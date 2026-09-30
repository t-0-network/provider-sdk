package main

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"connectrpc.com/connect"
	sdkcommon "github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

// test.v1.StreamTest (cross_test/stream_test.proto), built by hand on google.protobuf.StringValue.
const (
	streamTestPrefix       = "/test.v1.StreamTest/"
	streamTestClientStream = streamTestPrefix + "ClientStream"
	streamTestServerStream = streamTestPrefix + "ServerStream"
	serverStreamReplies    = 3
)

// newStreamTestHandler serves test.v1.StreamTest behind a verifier that checks the signature over
// the first envelope, as the network does. See cross_test/README.md.
func newStreamTestHandler(publicKeyHex string) (http.Handler, error) {
	publicKey, err := crypto.GetPublicKeyFromHex(publicKeyHex)
	if err != nil {
		return nil, fmt.Errorf("invalid public key: %w", err)
	}
	trustedKey := crypto.GetPublicKeyBytes(publicKey)

	mux := http.NewServeMux()
	mux.Handle(streamTestClientStream, connect.NewClientStreamHandler(streamTestClientStream,
		func(ctx context.Context, stream *connect.ClientStream[wrapperspb.StringValue]) (*connect.Response[wrapperspb.StringValue], error) {
			var got []string
			for stream.Receive() {
				got = append(got, stream.Msg().GetValue())
			}
			if err := stream.Err(); err != nil {
				return nil, err
			}
			log.Printf("ClientStream received %d messages", len(got))
			return connect.NewResponse(wrapperspb.String(framingOf(ctx) + ":" + strings.Join(got, ","))), nil
		}))
	mux.Handle(streamTestServerStream, connect.NewServerStreamHandler(streamTestServerStream,
		func(ctx context.Context, req *connect.Request[wrapperspb.StringValue], stream *connect.ServerStream[wrapperspb.StringValue]) error {
			for i := 0; i < serverStreamReplies; i++ {
				if err := stream.Send(wrapperspb.String(framingOf(ctx) + ":" + req.Msg.GetValue())); err != nil {
					return err
				}
			}
			return nil
		}))

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Every SDK's streaming cross tests wait for these log lines: keep their wording.
		framing, err := verifyFirstEnvelope(r, trustedKey)
		if err != nil {
			log.Printf("%s rejected: %v", r.URL.Path, err)
			// A proper RPC error, so the caller reads the reason from the call itself.
			_ = connect.NewErrorWriter().Write(w, r, connect.NewError(connect.CodeUnauthenticated, err))
			return
		}
		log.Printf("%s verified over the first %s", r.URL.Path, framing)
		mux.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), framingKey{}, framing)))
	}), nil
}

// framingKey carries what the signature was verified over ("envelope" or "payload") to the
// handlers, which put it in front of every reply.
type framingKey struct{}

func framingOf(ctx context.Context) string {
	framing, _ := ctx.Value(framingKey{}).(string)
	return framing
}

func verifyFirstEnvelope(r *http.Request, trustedKey []byte) (string, error) {
	signerKey, err := hex.DecodeString(strings.TrimPrefix(r.Header.Get(sdkcommon.PublicKeyHeader), "0x"))
	if err != nil || !bytes.Equal(signerKey, trustedKey) {
		return "", errors.New("unknown public key")
	}
	signature, err := hex.DecodeString(strings.TrimPrefix(r.Header.Get(sdkcommon.SignatureHeader), "0x"))
	if err != nil || (len(signature) != 64 && len(signature) != 65) {
		return "", errors.New("invalid signature header")
	}
	timestamp, err := strconv.ParseInt(r.Header.Get(sdkcommon.SignatureTimestampHeader), 10, 64)
	if err != nil {
		return "", errors.New("invalid timestamp header")
	}
	// Checked before the body is read, as the network does.
	if d := time.Since(time.UnixMilli(timestamp)); d > time.Minute || d < -time.Minute {
		return "", errors.New("timestamp is outside the allowed time window")
	}

	contentType := r.Header.Get("Content-Type")
	isGRPC := contentType == "application/grpc" || strings.HasPrefix(contentType, "application/grpc+")
	if !isGRPC && !strings.HasPrefix(contentType, "application/connect+") {
		return "", fmt.Errorf("content type %q is not a streaming content type", contentType)
	}

	var prefix [5]byte
	if _, err := io.ReadFull(r.Body, prefix[:]); err != nil {
		return "", fmt.Errorf("no first message: %w", err)
	}
	payload := make([]byte, binary.BigEndian.Uint32(prefix[1:]))
	if _, err := io.ReadFull(r.Body, payload); err != nil {
		return "", fmt.Errorf("truncated first message: %w", err)
	}
	envelope := append(prefix[:], payload...)
	r.Body = struct {
		io.Reader
		io.Closer
	}{io.MultiReader(bytes.NewReader(envelope), r.Body), r.Body}

	pubKey, err := crypto.GetPublicKeyFromBytes(signerKey)
	if err != nil {
		return "", err
	}
	var ts [8]byte
	binary.LittleEndian.PutUint64(ts[:], uint64(timestamp))
	verifies := func(signed []byte) bool {
		digest := crypto.LegacyKeccak256(append(append([]byte{}, signed...), ts[:]...))
		return crypto.VerifySignature(pubKey, digest, signature[:64])
	}
	switch {
	case verifies(envelope):
		return "envelope", nil
	case isGRPC && verifies(payload): // a signer above the gRPC framer (Java)
		return "payload", nil
	default:
		return "", errors.New("signature does not verify over the first message")
	}
}

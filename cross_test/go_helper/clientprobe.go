package main

import (
	"bytes"
	"context"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"
)

type clientCase struct {
	Name            string `json:"name"`
	ClientTimeoutMs *int64 `json:"client_timeout_ms"`
	CallTimeoutMs   *int64 `json:"call_timeout_ms"`
	Signer          string `json:"signer"`
	CustomSigner    *struct {
		Signature string `json:"signature"`
		PublicKey string `json:"public_key"`
		Error     string `json:"error"`
	} `json:"custom_signer"`
	Reply  string `json:"reply"`
	Expect struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Sent    *bool  `json:"sent"`
	} `json:"expect"`
}

const (
	// A deadline header may be this much below the timeout the case sets: the time between the
	// client starting the call and the request leaving it.
	deadlineSlackMs = 5000
	// Where a redirect case sends the client. It answers SERVING, so a client that follows the
	// redirect gets ok instead of the unknown the case expects.
	redirectTarget = "redirect-target"
)

// cmdClientProbe serves the client cases of the shared fixture until it is killed:
// go_helper client-probe --sdk <name> [--vectors <path>]. Its first line on stdout is
// "READY <base_url>"; a client calls a case at <base_url>/<case name>.
func cmdClientProbe() {
	var sdk, vectorsPath string
	args := os.Args[2:]
	for i := 0; i+1 < len(args); i += 2 {
		switch args[i] {
		case "--sdk":
			sdk = args[i+1]
		case "--vectors":
			vectorsPath = args[i+1]
		}
	}
	if sdk == "" || len(args)%2 != 0 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper client-probe --sdk <name> [--vectors <path>]")
		os.Exit(1)
	}
	v, err := loadVectors(vectorsPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading vectors: %v\n", err)
		os.Exit(1)
	}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error listening: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("READY http://%s\n", listener.Addr())
	os.Stdout.Sync()
	if err := newClientProbeServer(v, sdk, os.Stderr).Serve(listener); err != nil {
		fmt.Fprintf(os.Stderr, "Error serving: %v\n", err)
		os.Exit(1)
	}
}

// newClientProbeServer serves Connect over HTTP/1.1 and gRPC over h2c on one port.
func newClientProbeServer(v *vectors, sdk string, log io.Writer) *http.Server {
	protocols := new(http.Protocols)
	protocols.SetHTTP1(true)
	protocols.SetUnencryptedHTTP2(true)
	return &http.Server{
		Handler:           newClientProbeHandler(v, sdk, log),
		Protocols:         protocols,
		ReadHeaderTimeout: 10 * time.Second,
	}
}

// newClientProbeHandler checks each request against its client case and answers SERVING if it
// passes, or failed_precondition with the reason. The case is the first path segment.
func newClientProbeHandler(v *vectors, sdk string, log io.Writer) http.Handler {
	_, health := grpchealth.NewHandler(servingChecker{})
	errorWriter := connect.NewErrorWriter()
	cases := make(map[string]clientCase, len(v.ClientCases))
	for _, c := range v.ClientCases {
		cases[c.Name] = c
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		name, rest, _ := strings.Cut(strings.TrimPrefix(r.URL.Path, "/"), "/")
		body, err := io.ReadAll(r.Body)
		if err == nil && "/"+rest != healthCheckPath {
			err = fmt.Errorf("path %q is not /<case>%s", r.URL.Path, healthCheckPath)
		}
		c, ok := cases[name]
		if err == nil && !ok && name != redirectTarget {
			err = fmt.Errorf("no client case %q", name)
		}
		if err == nil && ok {
			err = checkClientRequest(v, sdk, c, r.Header, body, time.Now())
		}
		if err != nil {
			fmt.Fprintf(log, "FAIL %s: %v\n", name, err)
			_ = errorWriter.Write(w, r, connect.NewError(connect.CodeFailedPrecondition, fmt.Errorf("client probe: %s: %w", name, err)))
			return
		}
		fmt.Fprintf(log, "PASS %s\n", name)
		if c.Reply == "redirect" {
			w.Header().Set("Location", "/"+redirectTarget+healthCheckPath)
			w.WriteHeader(http.StatusTemporaryRedirect)
			return
		}
		served := r.Clone(r.Context())
		served.URL.Path = healthCheckPath
		served.URL.RawPath = ""
		served.Body = io.NopCloser(bytes.NewReader(body))
		health.ServeHTTP(w, served)
	})
}

type servingChecker struct{}

func (servingChecker) Check(context.Context, *grpchealth.CheckRequest) (*grpchealth.CheckResponse, error) {
	return &grpchealth.CheckResponse{Status: grpchealth.StatusServing}, nil
}

// checkClientRequest checks what a client sent for case c: its deadline header and its signature.
func checkClientRequest(v *vectors, sdk string, c clientCase, header http.Header, body []byte, now time.Time) error {
	if c.Expect.Sent != nil && !*c.Expect.Sent {
		return errors.New("the client sent a call that must fail before it is sent")
	}
	var protocol string
	switch header.Get("Content-Type") {
	case "application/proto":
		protocol = "connect"
	case "application/grpc", "application/grpc+proto":
		protocol = "grpc"
	default:
		return fmt.Errorf("content type %q is neither application/proto nor application/grpc", header.Get("Content-Type"))
	}
	if err := checkDeadline(v, c, protocol, header); err != nil {
		return err
	}
	// A custom signer signs with impostor_keys and may change the signature it returns.
	privateKey, publicKey, change := v.Keys.PrivateKey, v.Keys.PublicKey, ""
	if c.CustomSigner != nil {
		privateKey, publicKey, change = v.ImpostorKeys.PrivateKey, v.ImpostorKeys.PublicKey, c.CustomSigner.Signature
	}
	return checkSignature(v, sdk, protocol, privateKey, publicKey, change, header, body, now)
}

// checkDeadline: the deadline header is in (want - deadlineSlackMs, want], want being the case's
// per-call timeout, else its client timeout, else the default.
func checkDeadline(v *vectors, c clientCase, protocol string, header http.Header) error {
	want := v.Constants.DefaultTimeoutMs
	switch {
	case c.CallTimeoutMs != nil:
		want = *c.CallTimeoutMs
	case c.ClientTimeoutMs != nil:
		want = *c.ClientTimeoutMs
	}
	name := "Connect-Timeout-Ms"
	if protocol == "grpc" {
		name = "Grpc-Timeout"
	}
	value := header.Get(name)
	if value == "" {
		return fmt.Errorf("no %s header", name)
	}
	var got time.Duration
	var err error
	if protocol == "grpc" {
		got, err = parseGRPCTimeout(value)
	} else {
		var ms int64
		ms, err = strconv.ParseInt(value, 10, 64)
		got = time.Duration(ms) * time.Millisecond
		if err == nil && (ms < 0 || len(value) > 10) {
			err = errors.New("not 1 to 10 digits")
		}
	}
	if err != nil {
		return fmt.Errorf("%s %q: %v", name, value, err)
	}
	wantDuration := time.Duration(want) * time.Millisecond
	if got > wantDuration+time.Millisecond || got <= wantDuration-deadlineSlackMs*time.Millisecond {
		return fmt.Errorf("%s %q is %v, want at most %v and more than %v", name, value, got, wantDuration, wantDuration-deadlineSlackMs*time.Millisecond)
	}
	return nil
}

// parseGRPCTimeout reads a Grpc-Timeout value: 1 to 8 digits and a unit, H M S m u or n.
func parseGRPCTimeout(value string) (time.Duration, error) {
	units := map[byte]time.Duration{'H': time.Hour, 'M': time.Minute, 'S': time.Second, 'm': time.Millisecond, 'u': time.Microsecond, 'n': time.Nanosecond}
	if len(value) < 2 || len(value) > 9 {
		return 0, errors.New("not 1 to 8 digits and a unit")
	}
	unit, ok := units[value[len(value)-1]]
	digits := value[:len(value)-1]
	n, err := strconv.ParseInt(digits, 10, 64)
	if !ok || err != nil || n < 0 || strings.ContainsAny(digits, "+-") {
		return 0, errors.New("not 1 to 8 digits and a unit")
	}
	return time.Duration(n) * unit, nil
}

// checkSignature: the headers carry publicKey (uncompressed) as 0x and 130 lowercase hex digits,
// a timestamp within the window, and as X-Signature exactly the bytes the signer returned, in
// lowercase hex after 0x: the deterministic (RFC 6979) signature of privateKey over the digest of
// the body and the timestamp, changed as a custom signer changes it. Over gRPC, Java may sign the
// message without its 5-byte prefix (rule S3).
func checkSignature(v *vectors, sdk, protocol, privateKey, publicKey, change string, header http.Header, body []byte, now time.Time) error {
	wantKey := "0x" + publicKey
	if got := header.Get("X-Public-Key"); got != wantKey {
		return fmt.Errorf("X-Public-Key %q, want %q", got, wantKey)
	}

	timestampHeader := header.Get("X-Signature-Timestamp")
	timestamp, err := strconv.ParseInt(timestampHeader, 10, 64)
	if err != nil || strings.ContainsAny(timestampHeader, "+-") {
		return fmt.Errorf("X-Signature-Timestamp %q is not a decimal number of milliseconds", timestampHeader)
	}
	if skew := now.UnixMilli() - timestamp; skew > v.Constants.TimestampWindowMs || skew < -v.Constants.TimestampWindowMs {
		return fmt.Errorf("X-Signature-Timestamp %d is %d ms from the probe's clock", timestamp, skew)
	}

	candidates := [][]byte{body}
	if sdk == "java" && protocol == "grpc" && len(body) >= 5 {
		candidates = append(candidates, body[5:])
	}
	got := header.Get("X-Signature")
	var want string
	for _, signed := range candidates {
		sig, err := signDigest(privateKey, signed, timestamp)
		if err != nil {
			return err
		}
		switch change {
		case "r_s":
			sig = sig[:64]
		case "v_plus_27":
			sig[64] += 27
		}
		if want = "0x" + hex.EncodeToString(sig); got == want {
			return nil
		}
	}
	return fmt.Errorf("X-Signature %q is not the signer's signature of the body and X-Signature-Timestamp, %q", got, want)
}

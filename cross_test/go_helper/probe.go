package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/t-0-network/provider-sdk/go/api/ivms101/v1/ivms"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"golang.org/x/net/http2"
	"google.golang.org/protobuf/proto"
)

// The shared fixture: cross_test/test_vectors.json. probe reads its keys, impostor_keys and
// server_cases, client-probe its keys, impostor_keys, constants and client_cases.
type vectors struct {
	Keys struct {
		PrivateKey string `json:"private_key"`
		PublicKey  string `json:"public_key"`
	} `json:"keys"`
	ImpostorKeys struct {
		PrivateKey string `json:"private_key"`
		PublicKey  string `json:"public_key"`
	} `json:"impostor_keys"`
	Constants struct {
		TimestampWindowMs int64 `json:"timestamp_window_ms"`
		DefaultTimeoutMs  int64 `json:"default_timeout_ms"`
	} `json:"constants"`
	ServerCases []serverCase `json:"server_cases"`
	ClientCases []clientCase `json:"client_cases"`
}

type serverCase struct {
	Name              string   `json:"name"`
	Protocols         []string `json:"protocols"`
	PublicKey         string   `json:"public_key"`
	Signature         string   `json:"signature"`
	SignedBy          string   `json:"signed_by"`
	SignedOver        string   `json:"signed_over"`
	Timestamp         string   `json:"timestamp"`
	TimestampOffsetMs *int64   `json:"timestamp_offset_ms"`
	BodySize          int      `json:"body_size"`
	Body              string   `json:"body"`
	Before            string   `json:"before"`
	Call              string   `json:"call"`
	Service           string   `json:"service"`
	HTTPMethod        string   `json:"http_method"`
	Expect            struct {
		Code           string   `json:"code"`
		Message        string   `json:"message"`
		MessagePrefix  string   `json:"message_prefix"`
		LibraryMessage []string `json:"library_message"`
	} `json:"expect"`
}

// outcome is what a server answered: "ok" for a SERVING reply, else the error code and message;
// header holds the reply's headers and trailers.
type outcome struct {
	code    string
	message string
	header  http.Header
}

const (
	healthCheckPath          = "/grpc.health.v1.Health/Check"
	healthWatchPath          = "/grpc.health.v1.Health/Watch"
	approvePaymentQuotesPath = "/tzero.v1.payment.ProviderService/ApprovePaymentQuotes"
	payOutPath               = "/tzero.v1.payment.ProviderService/PayOut"
)

// cmdProbe sends every server case of the shared fixture to the provider server at base_url and
// checks each answer: go_helper probe <base_url> --sdk <name> [--protocol connect|grpc] [--vectors <path>].
func cmdProbe() {
	var baseURL, sdk, vectorsPath string
	protocols := []string{"connect", "grpc"}
	args := os.Args[2:]
	for i := 0; i < len(args); i++ {
		switch args[i] {
		case "--sdk":
			i++
			sdk = args[i]
		case "--protocol":
			i++
			protocols = []string{args[i]}
		case "--vectors":
			i++
			vectorsPath = args[i]
		default:
			baseURL = args[i]
		}
	}
	if baseURL == "" || sdk == "" {
		fmt.Fprintln(os.Stderr, "Usage: go_helper probe <base_url> --sdk <name> [--protocol connect|grpc] [--vectors <path>]")
		os.Exit(1)
	}
	v, err := loadVectors(vectorsPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading vectors: %v\n", err)
		os.Exit(1)
	}
	failures := runProbe(os.Stdout, v, baseURL, sdk, protocols)
	if failures > 0 {
		fmt.Printf("FAILED: %d case(s)\n", failures)
		os.Exit(1)
	}
	fmt.Println("ALL PASSED")
}

func loadVectors(path string) (*vectors, error) {
	if path == "" {
		exe, err := os.Executable()
		if err != nil {
			return nil, err
		}
		path = filepath.Join(filepath.Dir(exe), "..", "test_vectors.json")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var v vectors
	if err := json.Unmarshal(raw, &v); err != nil {
		return nil, err
	}
	return &v, nil
}

// runProbe runs every case over each protocol and writes one PASS or FAIL line per case. It returns
// the number of failures.
func runProbe(w io.Writer, v *vectors, baseURL, sdk string, protocols []string) int {
	failures := 0
	for _, c := range v.ServerCases {
		for _, protocol := range protocols {
			if len(c.Protocols) > 0 && !slices.Contains(c.Protocols, protocol) {
				continue
			}
			got, err := runCase(v, baseURL, protocol, c)
			label := fmt.Sprintf("%s [%s]", c.Name, protocol)
			if err != nil {
				failures++
				fmt.Fprintf(w, "FAIL %s: %v\n", label, err)
				continue
			}
			wantMessage := c.Expect.Message
			messageOK := got.message == wantMessage
			if c.Expect.MessagePrefix != "" {
				wantMessage = c.Expect.MessagePrefix + "…"
				messageOK = strings.HasPrefix(got.message, c.Expect.MessagePrefix)
			}
			checkMessage := c.Expect.Code != "ok" && !slices.Contains(c.Expect.LibraryMessage, sdk)
			if got.code != c.Expect.Code || (checkMessage && !messageOK) {
				failures++
				fmt.Fprintf(w, "FAIL %s: got %s %q, want %s %q\n", label, got.code, got.message, c.Expect.Code, wantMessage)
				continue
			}
			// A reply of the health service names the SDK that sent it.
			if c.Call == "" && (c.Expect.Code == "ok" || c.Service != "") {
				ecosystem, version := got.header.Get("T0-Sdk-Ecosystem"), got.header.Get("T0-Sdk-Version")
				if ecosystem != sdk || version == "" {
					failures++
					fmt.Fprintf(w, "FAIL %s: T0-Sdk-Ecosystem %q and T0-Sdk-Version %q, want %q and a version\n", label, ecosystem, version, sdk)
					continue
				}
			}
			fmt.Fprintf(w, "PASS %s\n", label)
		}
	}
	return failures
}

// The requests of the `call` cases: valid, so a server that validates requests reaches its handler.
var (
	approvePaymentQuotesRequest = &payment.ApprovePaymentQuoteRequest{
		PaymentId:        1,
		PayOutQuoteId:    1,
		PayOutRate:       &common.Decimal{Unscaled: 1},
		PayOutAmount:     &common.Decimal{Unscaled: 1},
		SettlementAmount: &common.Decimal{Unscaled: 1},
		PayOutFix:        &common.Decimal{},
	}
	payOutRequest = &payment.PayoutRequest{
		PaymentId:       1,
		Currency:        "EUR",
		ClientQuoteId:   "q",
		Amount:          &common.Decimal{Unscaled: 1},
		PayInProviderId: 1,
		TravelRuleData: &payment.PayoutRequest_TravelRuleData{
			Originator:  []*ivms.Person{person()},
			Beneficiary: []*ivms.Person{person()},
		},
	}
)

func person() *ivms.Person {
	return &ivms.Person{Person: &ivms.Person_NaturalPerson{NaturalPerson: &ivms.NaturalPerson{
		Name: &ivms.NaturalPersonName{NameIdentifiers: []*ivms.NaturalPersonNameId{{
			PrimaryIdentifier:  "Doe",
			NameIdentifierType: ivms.NaturalPersonNameTypeCode_NATURAL_PERSON_NAME_TYPE_CODE_LEGL,
		}}},
	}}}
}

func callRequest(protocol string, msg proto.Message) []byte {
	raw, err := proto.Marshal(msg)
	if err != nil {
		panic(err)
	}
	return frame(protocol, raw)
}

func frame(protocol string, msg []byte) []byte {
	if protocol != "grpc" {
		return msg
	}
	out := make([]byte, 5+len(msg))
	binary.BigEndian.PutUint32(out[1:5], uint32(len(msg)))
	copy(out[5:], msg)
	return out
}

// healthRequest is a grpc.health.v1.HealthCheckRequest for service "", padded with an unknown
// length-delimited field so that the whole HTTP body, gRPC prefix included, is bodySize bytes.
func healthRequest(protocol string, bodySize int, garbage bool) []byte {
	var msg []byte
	switch {
	case garbage:
		// A varint that never ends: not a HealthCheckRequest.
		msg = []byte{0x08, 0xff, 0xff, 0xff}
	case bodySize > 0:
		size := bodySize
		if protocol == "grpc" {
			size -= 5
		}
		msg = padding(size)
	}
	return frame(protocol, msg)
}

// padding is a message of exactly size bytes made of one unknown field (number 15, bytes).
func padding(size int) []byte {
	for n := size - 2; n >= 0; n-- {
		lenBytes := binary.AppendUvarint(nil, uint64(n))
		if 1+len(lenBytes)+n == size {
			out := make([]byte, 0, size)
			out = append(out, 15<<3|2)
			out = append(out, lenBytes...)
			return append(out, make([]byte, n)...)
		}
	}
	return nil
}

func runCase(v *vectors, baseURL, protocol string, c serverCase) (outcome, error) {
	if c.Before == "server_stream" {
		if err := serverStream(v, baseURL, protocol); err != nil {
			return outcome{}, err
		}
	}
	body, path := healthRequest(protocol, c.BodySize, c.Body == "garbage"), healthCheckPath
	switch c.Call {
	case "approve_payment_quotes":
		body, path = callRequest(protocol, approvePaymentQuotesRequest), approvePaymentQuotesPath
	case "pay_out":
		body, path = callRequest(protocol, payOutRequest), payOutPath
	}
	if c.Service != "" {
		body = frame(protocol, append([]byte{0x0a, byte(len(c.Service))}, c.Service...)) // HealthCheckRequest.service
	}
	method := http.MethodPost
	if c.HTTPMethod != "" {
		method = c.HTTPMethod
	}
	if method == http.MethodGet && protocol == "connect" {
		// A Connect GET: the message in the query, and an empty body, which the signature covers.
		path += "?" + url.Values{
			"connect":  {"v1"},
			"encoding": {"proto"},
			"base64":   {"1"},
			"message":  {base64.RawURLEncoding.EncodeToString(body)},
		}.Encode()
		body = nil
	}

	signer := v.Keys.PrivateKey
	if c.SignedBy == "impostor" {
		signer = v.ImpostorKeys.PrivateKey
	}
	now := time.Now().UnixMilli()
	timestamp := now
	if c.TimestampOffsetMs != nil {
		timestamp = now + *c.TimestampOffsetMs
	}
	signed := body
	if c.SignedOver == "payload" && protocol == "grpc" {
		signed = body[5:]
	}
	if c.Signature == "other_body" {
		signed = append(append([]byte{}, body...), 'x')
	}
	sig, err := signDigest(signer, signed, timestamp)
	if err != nil {
		return outcome{}, err
	}

	header := http.Header{}
	switch c.PublicKey {
	case "", "network":
		header.Set("X-Public-Key", "0x"+v.Keys.PublicKey)
	case "compressed":
		compressed, err := compressedKey(v.Keys.PublicKey)
		if err != nil {
			return outcome{}, err
		}
		header.Set("X-Public-Key", compressed)
	case "impostor":
		header.Set("X-Public-Key", "0x"+v.ImpostorKeys.PublicKey)
	case "absent":
	default:
		header.Set("X-Public-Key", c.PublicKey)
	}

	sigHex := hex.EncodeToString(sig)
	switch c.Signature {
	case "", "valid", "other_body":
		header.Set("X-Signature", "0x"+sigHex)
	case "valid_0X":
		header.Set("X-Signature", "0X"+sigHex)
	case "valid_no_prefix":
		header.Set("X-Signature", sigHex)
	case "valid_64":
		header.Set("X-Signature", "0x"+sigHex[:128])
	case "valid_high_s":
		// s replaced by n - s: a signature with a high s verifies too (rule V5).
		var sValue secp256k1.ModNScalar
		sValue.SetByteSlice(sig[32:64])
		sValue.Negate()
		highS := sValue.Bytes()
		header.Set("X-Signature", "0x"+hex.EncodeToString(sig[:32])+hex.EncodeToString(highS[:]))
	case "valid_v27":
		withV := append([]byte{}, sig...)
		withV[64] += 27
		header.Set("X-Signature", "0x"+hex.EncodeToString(withV))
	case "valid_trailing_junk":
		header.Set("X-Signature", "0x"+sigHex+"zz")
	case "valid_odd_length":
		header.Set("X-Signature", "0x"+sigHex+"1")
	case "absent":
	default:
		header.Set("X-Signature", c.Signature)
	}

	switch {
	case c.Timestamp == "absent":
	case c.Timestamp != "" && c.Timestamp != "now":
		header.Set("X-Signature-Timestamp", c.Timestamp)
	default:
		header.Set("X-Signature-Timestamp", strconv.FormatInt(timestamp, 10))
	}

	if protocol == "grpc" {
		return sendGRPC(baseURL, method, path, header, body)
	}
	return sendConnect(baseURL, method, path, header, body)
}

// serverStream sends a signed server-streaming call, Health/Watch, whose body arrives after its
// headers, and reads its first reply for at most two seconds. Whatever the server answers (rule V9:
// only the Go SDK serves streams), the case that follows must find it still serving.
func serverStream(v *vectors, baseURL, protocol string) error {
	msg := []byte{} // HealthCheckRequest{service: ""}
	envelope := make([]byte, 5+len(msg))
	binary.BigEndian.PutUint32(envelope[1:5], uint32(len(msg)))
	copy(envelope[5:], msg)
	timestamp := time.Now().UnixMilli()
	sig, err := signDigest(v.Keys.PrivateKey, envelope, timestamp)
	if err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	reader, writer := io.Pipe()
	go func() {
		time.Sleep(100 * time.Millisecond)
		_, _ = writer.Write(envelope)
		_ = writer.Close()
	}()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimSuffix(baseURL, "/")+healthWatchPath, reader)
	if err != nil {
		return err
	}
	req.Header.Set("X-Public-Key", "0x"+v.Keys.PublicKey)
	req.Header.Set("X-Signature", "0x"+hex.EncodeToString(sig))
	req.Header.Set("X-Signature-Timestamp", strconv.FormatInt(timestamp, 10))
	client := connectClient
	if protocol == "grpc" {
		client = grpcClient
		req.Header.Set("Content-Type", "application/grpc")
		req.Header.Set("Te", "trailers")
	} else {
		req.Header.Set("Content-Type", "application/connect+proto")
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil // a refused or reset stream is an answer too
	}
	defer resp.Body.Close()
	_, _ = io.ReadFull(resp.Body, make([]byte, 5))
	return nil
}

func signDigest(privateKeyHex string, signed []byte, timestamp int64) ([]byte, error) {
	key, err := crypto.GetPrivateKeyFromHex(privateKeyHex)
	if err != nil {
		return nil, err
	}
	var ts [8]byte
	binary.LittleEndian.PutUint64(ts[:], uint64(timestamp))
	sig, _, err := crypto.NewSigner(key)(crypto.LegacyKeccak256Concat(signed, ts[:]))
	return sig, err
}

func compressedKey(uncompressedHex string) (string, error) {
	raw, err := hex.DecodeString(uncompressedHex)
	if err != nil {
		return "", err
	}
	key, err := secp256k1.ParsePubKey(raw)
	if err != nil {
		return "", err
	}
	return "0x" + hex.EncodeToString(key.SerializeCompressed()), nil
}

var connectClient = &http.Client{
	Timeout:       60 * time.Second,
	CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	Transport:     &http.Transport{DisableCompression: true, ExpectContinueTimeout: 2 * time.Second},
}

// A body this large is sent with Expect: 100-continue over HTTP/1.1, so a server that rejects the
// request on its headers answers before the body is sent instead of closing the connection while
// the body is still being written.
const expectContinueOver = 1 << 20

var grpcClient = &http.Client{
	Timeout: 60 * time.Second,
	Transport: &http2.Transport{
		AllowHTTP: true,
		DialTLSContext: func(ctx context.Context, network, addr string, _ *tls.Config) (net.Conn, error) {
			return (&net.Dialer{}).DialContext(ctx, network, addr)
		},
	},
}

// sendConnect sends a unary Connect request with method; a GET has no body and no Content-Type.
func sendConnect(baseURL, method, path string, header http.Header, body []byte) (outcome, error) {
	req, err := http.NewRequest(method, strings.TrimSuffix(baseURL, "/")+path, bytes.NewReader(body))
	if err != nil {
		return outcome{}, err
	}
	req.Header = header
	if method != http.MethodGet {
		req.Header.Set("Content-Type", "application/proto")
	}
	if len(body) > expectContinueOver {
		req.Header.Set("Expect", "100-continue")
	}
	resp, err := connectClient.Do(req)
	if err != nil {
		return outcome{}, fmt.Errorf("transport error: %w", err)
	}
	defer resp.Body.Close()
	payload, err := io.ReadAll(resp.Body)
	if err != nil {
		return outcome{}, fmt.Errorf("reading response: %w", err)
	}
	if resp.StatusCode == http.StatusOK {
		got, err := healthOutcome(payload)
		got.header = resp.Header
		return got, err
	}
	var connectErr struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	}
	if err := json.Unmarshal(payload, &connectErr); err != nil || connectErr.Code == "" {
		return outcome{}, fmt.Errorf("HTTP %d with a body that is not a Connect error: %q", resp.StatusCode, truncate(payload))
	}
	return outcome{code: connectErr.Code, message: connectErr.Message, header: resp.Header}, nil
}

func sendGRPC(baseURL, method, path string, header http.Header, body []byte) (outcome, error) {
	req, err := http.NewRequest(method, strings.TrimSuffix(baseURL, "/")+path, bytes.NewReader(body))
	if err != nil {
		return outcome{}, err
	}
	req.Header = header
	req.Header.Set("Content-Type", "application/grpc")
	req.Header.Set("Te", "trailers")
	resp, err := grpcClient.Do(req)
	if err != nil {
		return outcome{}, fmt.Errorf("transport error: %w", err)
	}
	defer resp.Body.Close()
	payload, err := io.ReadAll(resp.Body)
	if err != nil {
		return outcome{}, fmt.Errorf("reading response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return outcome{}, fmt.Errorf("HTTP %d for a gRPC call: %q", resp.StatusCode, truncate(payload))
	}
	reply := resp.Header.Clone()
	for name, values := range resp.Trailer {
		reply[name] = append(reply[name], values...)
	}
	status := resp.Trailer.Get("Grpc-Status")
	message := resp.Trailer.Get("Grpc-Message")
	if status == "" {
		status = resp.Header.Get("Grpc-Status")
		message = resp.Header.Get("Grpc-Message")
	}
	if status == "" {
		return outcome{}, errors.New("gRPC reply without grpc-status")
	}
	if status == "0" {
		if len(payload) < 5 {
			return outcome{}, fmt.Errorf("gRPC OK without a message: %q", truncate(payload))
		}
		got, err := healthOutcome(payload[5:])
		got.header = reply
		return got, err
	}
	n, err := strconv.Atoi(status)
	if err != nil || n < 0 || n >= len(grpcCodeNames) {
		return outcome{}, fmt.Errorf("bad grpc-status %q", status)
	}
	decoded, err := url.PathUnescape(message)
	if err != nil {
		decoded = message
	}
	return outcome{code: grpcCodeNames[n], message: decoded, header: reply}, nil
}

// healthOutcome is "ok" for a HealthCheckResponse with status SERVING (field 1 = 1).
func healthOutcome(msg []byte) (outcome, error) {
	if bytes.Equal(msg, []byte{0x08, 0x01}) {
		return outcome{code: "ok"}, nil
	}
	return outcome{}, fmt.Errorf("reply is not a SERVING HealthCheckResponse: %x", truncate(msg))
}

func truncate(b []byte) []byte {
	if len(b) > 200 {
		return b[:200]
	}
	return b
}

var grpcCodeNames = []string{
	"ok", "canceled", "unknown", "invalid_argument", "deadline_exceeded", "not_found", "already_exists",
	"permission_denied", "resource_exhausted", "failed_precondition", "aborted", "out_of_range",
	"unimplemented", "internal", "unavailable", "data_loss", "unauthenticated",
}

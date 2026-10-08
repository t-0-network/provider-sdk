package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"testing"
	"time"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
)

// TestMain runs the helper's main instead of the tests when a test starts this binary as the
// helper command.
func TestMain(m *testing.M) {
	if os.Getenv("GO_HELPER_RUN_MAIN") == "1" {
		main()
		os.Exit(0)
	}
	os.Exit(m.Run())
}

// The Go column of the shared client behavior: every case of client_cases in
// cross_test/test_vectors.json, made by the SDK's own client over Connect and over gRPC.
func TestClientProbe_GoClient(t *testing.T) {
	v := testVectors(t)
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	log := new(helperLog)
	server := newClientProbeServer(v, "go", log)
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(func() { _ = server.Close() })
	baseURL := "http://" + listener.Addr().String()

	for _, c := range v.ClientCases {
		for _, protocol := range []network.Protocol{network.ProtocolConnect, network.ProtocolGRPC} {
			name := c.Name + "/connect"
			if protocol == network.ProtocolGRPC {
				name = c.Name + "/grpc"
			}
			t.Run(name, func(t *testing.T) {
				logged := len(log.lines())
				code, err := goClientCall(v, baseURL, c, protocol)
				if fails := failLines(log.lines()[logged:], c.Name); len(fails) > 0 {
					t.Errorf("client-probe logged:\n%s", strings.Join(fails, "\n"))
				}
				if code != c.Expect.Code {
					t.Fatalf("got %s (%v), want %s", code, err, c.Expect.Code)
				}
				if c.Expect.Message != "" {
					if message := connectMessage(err); message != c.Expect.Message {
						t.Errorf("got message %q, want %q", message, c.Expect.Message)
					}
				}
			})
		}
	}

	// A call that ends on its own deadline can end before the probe logs its request, so the whole
	// log is checked again once the server has finished every request it got.
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		t.Errorf("shutting down client-probe: %v", err)
	}
	if fails := failLines(log.lines(), ""); len(fails) > 0 {
		t.Errorf("client-probe logged FAIL lines:\n%s", strings.Join(fails, "\n"))
	}
}

// failLines returns the FAIL lines of case name ("FAIL <name>: <reason>"), or every FAIL line when
// name is empty.
func failLines(lines []string, name string) []string {
	prefix := "FAIL "
	if name != "" {
		prefix += name + ":"
	}
	var fails []string
	for _, line := range lines {
		if strings.HasPrefix(line, prefix) {
			fails = append(fails, line)
		}
	}
	return fails
}

// connectMessage is the message of a connect error, without the "<code>: " that Error() adds, or
// the whole text of any other error.
func connectMessage(err error) string {
	if connectErr, ok := errors.AsType[*connect.Error](err); ok {
		return connectErr.Message()
	}
	return err.Error()
}

// The command SDK tests start: its READY line gives a base URL that serves the cases.
func TestClientProbe_Command(t *testing.T) {
	v := testVectors(t)
	cmd := exec.Command(os.Args[0], "client-probe", "--sdk", "go", "--vectors", vectorsPath)
	cmd.Env = append(os.Environ(), "GO_HELPER_RUN_MAIN=1")
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
	})
	line, err := bufio.NewReader(stdout).ReadString('\n')
	if err != nil {
		t.Fatal(err)
	}
	baseURL, ok := strings.CutPrefix(strings.TrimSpace(line), "READY ")
	if !ok {
		t.Fatalf("first line %q is not READY <base_url>", line)
	}
	for _, c := range v.ClientCases {
		if c.Name == "default-timeout" {
			if code, err := goClientCall(v, baseURL, c, network.ProtocolConnect); code != "ok" {
				t.Fatalf("got %s (%v), want ok", code, err)
			}
			return
		}
	}
	t.Fatal("no default-timeout case")
}

// goClientCall makes the call of client case c with the Go SDK's client and returns how it ended:
// ok for a SERVING reply, else the error code.
func goClientCall(v *vectors, baseURL string, c clientCase, protocol network.Protocol) (string, error) {
	privateKey, opts, err := goSigner(v, c)
	if err != nil {
		return "", err
	}
	opts = append(opts, network.WithBaseURL(baseURL+"/"+c.Name), network.WithProtocol(protocol))
	if c.ClientTimeoutMs != nil {
		opts = append(opts, network.WithTimeout(time.Duration(*c.ClientTimeoutMs)*time.Millisecond))
	}
	client, err := network.NewServiceClient(privateKey, grpchealth.NewClient, opts...)
	if err != nil {
		return "", err
	}
	ctx := context.Background()
	if c.CallTimeoutMs != nil {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, time.Duration(*c.CallTimeoutMs)*time.Millisecond)
		defer cancel()
	}
	resp, err := client.Check(ctx, &grpchealth.CheckRequest{})
	if err != nil {
		return connect.CodeOf(err).String(), err
	}
	if resp.Status != grpchealth.StatusServing {
		return "", fmt.Errorf("status %v", resp.Status)
	}
	return "ok", nil
}

// The probe's checks refuse what a client must not send.
func TestClientProbe_Checks(t *testing.T) {
	v := testVectors(t)
	body := healthRequest("grpc", 0, false)
	now := time.Now()
	signed := func(privateKey string, signedBytes []byte, mutate func(sig []byte) string) http.Header {
		sig, err := signDigest(privateKey, signedBytes, now.UnixMilli())
		if err != nil {
			t.Fatal(err)
		}
		h := http.Header{}
		h.Set("Content-Type", "application/grpc")
		h.Set("Grpc-Timeout", "14999000u")
		h.Set("X-Public-Key", "0x"+v.Keys.PublicKey)
		if privateKey == v.ImpostorKeys.PrivateKey {
			h.Set("X-Public-Key", "0x"+v.ImpostorKeys.PublicKey)
		}
		h.Set("X-Signature-Timestamp", strconv.FormatInt(now.UnixMilli(), 10))
		h.Set("X-Signature", mutate(sig))
		return h
	}
	plain := func(sig []byte) string { return "0x" + hex.EncodeToString(sig) }
	custom := func(signature string) clientCase {
		var c clientCase
		c.CustomSigner = &struct {
			Signature string `json:"signature"`
			PublicKey string `json:"public_key"`
			Error     string `json:"error"`
		}{Signature: signature}
		return c
	}
	highS := func(sig []byte) string {
		var s secp256k1.ModNScalar
		s.SetByteSlice(sig[32:64])
		s.Negate()
		b := s.Bytes()
		return "0x" + hex.EncodeToString(sig[:32]) + hex.EncodeToString(b[:]) + hex.EncodeToString([]byte{sig[64] ^ 1})
	}
	notSent := false
	tests := []struct {
		name   string
		sdk    string
		header http.Header
		edit   func(http.Header)
		c      clientCase
		ok     bool
	}{
		{name: "valid", header: signed(v.Keys.PrivateKey, body, plain), ok: true},
		{name: "connect", header: signed(v.Keys.PrivateKey, body, plain), edit: func(h http.Header) {
			h.Set("Content-Type", "application/proto")
			h.Del("Grpc-Timeout")
			h.Set("Connect-Timeout-Ms", "15000")
		}, ok: true},
		{name: "json body", header: signed(v.Keys.PrivateKey, body, plain), edit: func(h http.Header) { h.Set("Content-Type", "application/json") }},
		{name: "no deadline", header: signed(v.Keys.PrivateKey, body, plain), edit: func(h http.Header) { h.Del("Grpc-Timeout") }},
		{name: "deadline over the timeout", header: signed(v.Keys.PrivateKey, body, plain), edit: func(h http.Header) { h.Set("Grpc-Timeout", "16S") }},
		{name: "deadline far under the timeout", header: signed(v.Keys.PrivateKey, body, plain), edit: func(h http.Header) { h.Set("Grpc-Timeout", "9000m") }},
		{name: "a call that must not be sent", header: signed(v.Keys.PrivateKey, body, plain), c: func() clientCase {
			var c clientCase
			c.Expect.Sent = &notSent
			return c
		}()},
		{name: "custom signer", header: signed(v.ImpostorKeys.PrivateKey, body, plain), c: custom(""), ok: true},
		{name: "custom signer, r_s", header: signed(v.ImpostorKeys.PrivateKey, body, func(sig []byte) string { return plain(sig[:64]) }), c: custom("r_s"), ok: true},
		{name: "custom signer, r_s with v", header: signed(v.ImpostorKeys.PrivateKey, body, plain), c: custom("r_s")},
		{name: "custom signer, keys", header: signed(v.Keys.PrivateKey, body, plain), c: custom("")},
		{name: "custom signer, v_plus_27", header: signed(v.ImpostorKeys.PrivateKey, body, func(sig []byte) string { return plain(append(sig[:64:64], sig[64]+27)) }), c: custom("v_plus_27"), ok: true},
		{name: "custom signer, v_plus_27 normalized", header: signed(v.ImpostorKeys.PrivateKey, body, plain), c: custom("v_plus_27")},
		{name: "other key", header: signed(v.ImpostorKeys.PrivateKey, body, plain)},
		{name: "64 bytes", header: signed(v.Keys.PrivateKey, body, func(sig []byte) string { return plain(sig[:64]) })},
		{name: "v 27", header: signed(v.Keys.PrivateKey, body, func(sig []byte) string { return plain(append(sig[:64:64], sig[64]+27)) })},
		{name: "wrong v", header: signed(v.Keys.PrivateKey, body, func(sig []byte) string { return plain(append(sig[:64:64], sig[64]^1)) })},
		{name: "high s", header: signed(v.Keys.PrivateKey, body, highS)},
		{name: "uppercase hex", header: signed(v.Keys.PrivateKey, body, func(sig []byte) string { return "0x" + strings.ToUpper(hex.EncodeToString(sig)) })},
		{name: "no 0x", header: signed(v.Keys.PrivateKey, body, func(sig []byte) string { return hex.EncodeToString(sig) })},
		{name: "stale timestamp", header: signed(v.Keys.PrivateKey, body, plain), edit: func(h http.Header) {
			h.Set("X-Signature-Timestamp", strconv.FormatInt(now.UnixMilli()-v.Constants.TimestampWindowMs-1000, 10))
		}},
		{name: "without the gRPC prefix", header: signed(v.Keys.PrivateKey, body[5:], plain)},
		{name: "without the gRPC prefix, java", sdk: "java", header: signed(v.Keys.PrivateKey, body[5:], plain), ok: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.edit != nil {
				tt.edit(tt.header)
			}
			sdk := tt.sdk
			if sdk == "" {
				sdk = "go"
			}
			err := checkClientRequest(v, sdk, tt.c, tt.header, body, now)
			if tt.ok && err != nil {
				t.Errorf("refused: %v", err)
			}
			if !tt.ok && err == nil {
				t.Error("accepted")
			}
		})
	}
}

// A client that follows the redirect case's 307 gets a SERVING reply, so it cannot end with the
// unknown the case expects.
func TestClientProbe_RedirectFollowerGetsOK(t *testing.T) {
	v := testVectors(t)
	server := httptest.NewServer(newClientProbeHandler(v, "go", io.Discard))
	t.Cleanup(server.Close)
	timestamp := time.Now().UnixMilli()
	sig, err := signDigest(v.Keys.PrivateKey, nil, timestamp)
	if err != nil {
		t.Fatal(err)
	}
	req, err := http.NewRequest(http.MethodPost, server.URL+"/redirect-not-followed"+healthCheckPath, bytes.NewReader(nil))
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Content-Type", "application/proto")
	req.Header.Set("Connect-Timeout-Ms", "15000")
	req.Header.Set("X-Public-Key", "0x"+v.Keys.PublicKey)
	req.Header.Set("X-Signature", "0x"+hex.EncodeToString(sig))
	req.Header.Set("X-Signature-Timestamp", strconv.FormatInt(timestamp, 10))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	payload, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK || !bytes.Equal(payload, []byte{0x08, 0x01}) {
		t.Fatalf("got HTTP %d %x, want 200 and a SERVING reply", resp.StatusCode, payload)
	}
}

// goSigner gives the Go client the signer of client case c: the hex key, the factory's signer, or a
// custom signer function that signs with impostor_keys and changes its output as the case says.
// It is the only place that depends on how the Go SDK takes a signer.
func goSigner(v *vectors, c clientCase) (network.PrivateKeyHexed, []network.ClientOption, error) {
	switch {
	case c.Signer == "factory":
		signer, err := crypto.NewSignerFromHex(v.Keys.PrivateKey)
		return "", []network.ClientOption{network.WithSignatureFunction(signer)}, err
	case c.CustomSigner != nil:
		impostor, err := crypto.NewSignerFromHex(v.ImpostorKeys.PrivateKey)
		if err != nil {
			return "", nil, err
		}
		custom := *c.CustomSigner
		signer := func(digest []byte) ([]byte, []byte, error) {
			if custom.Error != "" {
				return nil, nil, errors.New(custom.Error)
			}
			sig, publicKey, err := impostor(digest)
			if err != nil {
				return nil, nil, err
			}
			switch custom.Signature {
			case "r_s":
				sig = sig[:64]
			case "v_plus_27":
				sig = append(sig[:64:64], sig[64]+27)
			case "first_63_bytes":
				sig = sig[:63]
			}
			if custom.PublicKey != "" {
				if publicKey, err = hex.DecodeString(custom.PublicKey); err != nil {
					return nil, nil, err
				}
			}
			return sig, publicKey, nil
		}
		return "", []network.ClientOption{network.WithSignatureFunction(signer)}, nil
	default:
		return network.PrivateKeyHexed(v.Keys.PrivateKey), nil, nil
	}
}

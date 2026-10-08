// Unified cross-test helper: Go signs/verifies/hashes, runs a provider server,
// and makes signed client calls for cross-language interoperability testing.
//
// Usage:
//
//	go_helper hash <hex_data>
//	go_helper sign <hex_private_key> <hex_digest>
//	go_helper verify <hex_public_key> <hex_digest> <hex_signature>
//	go_helper pubkey <hex_private_key>
//	go_helper serve <port> <hex_network_public_key>
//	go_helper call-pay-out <base_url> <hex_private_key> [--grpc]
//	go_helper call-health <base_url> <hex_private_key> [--grpc]
//	go_helper probe <base_url> --sdk <name> [--protocol connect|grpc] [--vectors <path>]
//	go_helper client-probe --sdk <name> [--vectors <path>]
package main

import (
	"context"
	"encoding/hex"
	"fmt"
	"log"
	"net/http"
	"os"
	"slices"
	"strings"
	"time"

	"connectrpc.com/connect"
	"connectrpc.com/grpchealth"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/manage/kyc_sharing/kyc_sharingconnect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/provider"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper <command> [args...]")
		os.Exit(1)
	}

	switch os.Args[1] {
	case "hash":
		cmdHash()
	case "sign":
		cmdSign()
	case "verify":
		cmdVerify()
	case "pubkey":
		cmdPubkey()
	case "serve":
		cmdServe()
	case "call-pay-out":
		cmdCallPayOut()
	case "call-health":
		cmdCallHealth()
	case "probe":
		cmdProbe()
	case "client-probe":
		cmdClientProbe()
	default:
		fmt.Fprintf(os.Stderr, "Unknown command: %s\n", os.Args[1])
		os.Exit(1)
	}
}

func cmdHash() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper hash <hex_data>")
		os.Exit(1)
	}
	data := mustDecodeHex(os.Args[2])
	hash := crypto.LegacyKeccak256(data)
	fmt.Printf("0x%s\n", hex.EncodeToString(hash))
}

func cmdSign() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper sign <hex_private_key> <hex_digest>")
		os.Exit(1)
	}
	privateKey, err := crypto.GetPrivateKeyFromHex(os.Args[2])
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error parsing private key: %v\n", err)
		os.Exit(1)
	}
	digest := mustDecodeHex(os.Args[3])
	signFn := crypto.NewSigner(privateKey)
	sig, pubKey, err := signFn(digest)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error signing: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("signature=0x%s\n", hex.EncodeToString(sig))
	fmt.Printf("public_key=0x%s\n", hex.EncodeToString(pubKey))
}

func cmdVerify() {
	if len(os.Args) != 5 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper verify <hex_public_key> <hex_digest> <hex_signature>")
		os.Exit(1)
	}
	pubKey, err := crypto.GetPublicKeyFromHex(os.Args[2])
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error parsing public key: %v\n", err)
		os.Exit(1)
	}
	digest := mustDecodeHex(os.Args[3])
	signature := mustDecodeHex(os.Args[4])
	result := crypto.VerifySignature(pubKey, digest, signature)
	if result {
		fmt.Println("true")
	} else {
		fmt.Println("false")
	}
}

func cmdPubkey() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper pubkey <hex_private_key>")
		os.Exit(1)
	}
	privateKey, err := crypto.GetPrivateKeyFromHex(os.Args[2])
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error parsing private key: %v\n", err)
		os.Exit(1)
	}
	pubBytes := crypto.GetPublicKeyBytes(privateKey.PubKey())
	fmt.Printf("0x%s\n", hex.EncodeToString(pubBytes))
}

func cmdServe() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper serve <port> <hex_network_public_key>")
		os.Exit(1)
	}
	port := os.Args[2]

	handler, err := newServeHandler(os.Args[3])
	if err != nil {
		log.Fatalf("Failed to create handler: %v", err)
	}

	// The SDK's own server, so the cross tests that call this one test it too.
	shutdown, err := provider.StartServer(handler, provider.WithAddr(":"+port))
	if err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
	defer shutdown(context.Background())

	fmt.Printf("READY on :%s\n", port)
	os.Stdout.Sync()
	select {}
}

// newServeHandler serves the provider service and test.v1.StreamTest behind the SDK's signature
// verification; provider.StartServer adds h2c, so Connect (HTTP/1.1) and gRPC share the port.
func newServeHandler(networkPublicKeyHex string) (http.Handler, error) {
	httpHandler, err := provider.NewHttpHandler(
		provider.NetworkPublicKeyHexed(networkPublicKeyHex),
		provider.Handler(paymentconnect.NewProviderServiceHandler, paymentconnect.ProviderServiceHandler(&testProviderService{})),
		provider.Handler(newStreamTestHandler, streamTest{}),
		provider.Handler(kyc_sharingconnect.NewKycFileServiceHandler, kyc_sharingconnect.KycFileServiceHandler(newKycFileService())),
	)
	if err != nil {
		return nil, err
	}
	return logRejections(httpHandler), nil
}

func hasFlag(flag string) bool {
	return slices.Contains(os.Args[2:], flag)
}

func cmdCallPayOut() {
	grpcMode := hasFlag("--grpc")
	// Positional: call-pay-out <base_url> <hex_private_key> [--grpc]
	positional := make([]string, 0, 2)
	for _, arg := range os.Args[2:] {
		if !strings.HasPrefix(arg, "--") {
			positional = append(positional, arg)
		}
	}
	if len(positional) != 2 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper call-pay-out <base_url> <hex_private_key> [--grpc]")
		os.Exit(1)
	}
	baseURL := positional[0]
	privateKey := network.PrivateKeyHexed(positional[1])

	var clientOpts []network.ClientOption
	clientOpts = append(clientOpts, network.WithBaseURL(baseURL))
	if grpcMode {
		clientOpts = append(clientOpts, network.WithProtocol(network.ProtocolGRPC))
	}

	client, err := network.NewServiceClient(
		privateKey,
		paymentconnect.NewProviderServiceClient,
		clientOpts...,
	)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error creating client: %v\n", err)
		os.Exit(1)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	resp, err := client.PayOut(ctx, connect.NewRequest(&payment.PayoutRequest{
		PaymentId: 42,
		PayoutId:  1,
		Currency:  "EUR",
		Amount:    &common.Decimal{Unscaled: 100, Exponent: 0},
	}))
	if err != nil {
		fmt.Printf("ERROR: %v\n", err)
		os.Exit(1)
	}
	_ = resp
	fmt.Println("OK")
}

func cmdCallHealth() {
	grpcMode := hasFlag("--grpc")
	positional := make([]string, 0, 2)
	for _, arg := range os.Args[2:] {
		if !strings.HasPrefix(arg, "--") {
			positional = append(positional, arg)
		}
	}
	if len(positional) != 2 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper call-health <base_url> <hex_private_key> [--grpc]")
		os.Exit(1)
	}
	baseURL := positional[0]
	privateKey := network.PrivateKeyHexed(positional[1])

	var clientOpts []network.ClientOption
	clientOpts = append(clientOpts, network.WithBaseURL(baseURL))
	if grpcMode {
		clientOpts = append(clientOpts, network.WithProtocol(network.ProtocolGRPC))
	}

	client, err := network.NewServiceClient(
		privateKey,
		grpchealth.NewClient,
		clientOpts...,
	)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error creating client: %v\n", err)
		os.Exit(1)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	resp, err := client.Check(ctx, &grpchealth.CheckRequest{Service: grpchealth.HealthV1ServiceName})
	if err != nil {
		fmt.Printf("ERROR: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("status=%s\n", resp.Status)
}

type testProviderService struct{}

// PayOut answers a response whose failed.details is over its 1024-character limit, so the call
// fails response validation: the server case response-invalid-nested-field.
func (s *testProviderService) PayOut(ctx context.Context, req *connect.Request[payment.PayoutRequest]) (*connect.Response[payment.PayoutResponse], error) {
	log.Printf("PayOut called: payment_id=%d", req.Msg.PaymentId)
	details := strings.Repeat("x", 1025)
	return connect.NewResponse(&payment.PayoutResponse{
		Result: &payment.PayoutResponse_Failed_{Failed: &payment.PayoutResponse_Failed{Details: &details}},
	}), nil
}

func (s *testProviderService) UpdatePayment(ctx context.Context, req *connect.Request[payment.UpdatePaymentRequest]) (*connect.Response[payment.UpdatePaymentResponse], error) {
	return connect.NewResponse(&payment.UpdatePaymentResponse{}), nil
}

func (s *testProviderService) UpdateLimit(ctx context.Context, req *connect.Request[payment.UpdateLimitRequest]) (*connect.Response[payment.UpdateLimitResponse], error) {
	return connect.NewResponse(&payment.UpdateLimitResponse{}), nil
}

func (s *testProviderService) AppendLedgerEntries(ctx context.Context, req *connect.Request[payment.AppendLedgerEntriesRequest]) (*connect.Response[payment.AppendLedgerEntriesResponse], error) {
	return connect.NewResponse(&payment.AppendLedgerEntriesResponse{}), nil
}

func (s *testProviderService) ApprovePaymentQuotes(ctx context.Context, req *connect.Request[payment.ApprovePaymentQuoteRequest]) (*connect.Response[payment.ApprovePaymentQuoteResponse], error) {
	return connect.NewResponse(&payment.ApprovePaymentQuoteResponse{}), nil
}

func mustDecodeHex(s string) []byte {
	s = strings.TrimPrefix(strings.ToLower(s), "0x")
	data, err := hex.DecodeString(s)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error decoding hex '%s': %v\n", s, err)
		os.Exit(1)
	}
	return data
}

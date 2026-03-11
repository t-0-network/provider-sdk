// Cross-test helper: Go signs/verifies/hashes for Python to verify interop.
// Also runs a ConnectRPC server and makes client calls for end-to-end testing.
//
// Usage:
//   go_helper hash <hex_data>
//   go_helper sign <hex_private_key> <hex_digest>
//   go_helper verify <hex_public_key> <hex_digest> <hex_signature>
//   go_helper pubkey <hex_private_key>
//   go_helper serve <port> <hex_network_public_key>
//   go_helper call-update-quote <base_url> <hex_private_key>
//   go_helper call-pay-out <base_url> <hex_private_key> <hex_network_public_key>

package main

import (
	"context"
	"encoding/hex"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/provider"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper <command> [args...]")
		os.Exit(1)
	}

	cmd := os.Args[1]
	switch cmd {
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
	case "call-update-quote":
		cmdCallUpdateQuote()
	case "call-pay-out":
		cmdCallPayOut()
	default:
		fmt.Fprintf(os.Stderr, "Unknown command: %s\n", cmd)
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

// cmdServe starts a Go provider server.
// The server implements ProviderService (PayOut, UpdatePayment, etc.)
// and validates signatures using the given network public key.
func cmdServe() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper serve <port> <hex_network_public_key>")
		os.Exit(1)
	}
	port := os.Args[2]
	networkPubKey := provider.NetworkPublicKeyHexed(os.Args[3])

	service := &testProviderService{}

	httpHandler, err := provider.NewHttpHandler(
		networkPubKey,
		provider.Handler(paymentconnect.NewProviderServiceHandler, paymentconnect.ProviderServiceHandler(service)),
	)
	if err != nil {
		log.Fatalf("Failed to create handler: %v", err)
	}

	shutdownFunc, err := provider.StartServer(
		httpHandler,
		provider.WithAddr(":"+port),
	)
	if err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}

	fmt.Printf("READY on :%s\n", port)
	os.Stdout.Sync()

	// Wait forever (test will kill the process)
	select {}
	_ = shutdownFunc
}

// cmdCallUpdateQuote makes a signed UpdateQuote RPC call to a Python NetworkService server.
func cmdCallUpdateQuote() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper call-update-quote <base_url> <hex_private_key>")
		os.Exit(1)
	}
	baseURL := os.Args[2]
	privateKey := network.PrivateKeyHexed(os.Args[3])

	client, err := network.NewServiceClient(
		privateKey,
		paymentconnect.NewNetworkServiceClient,
		network.WithBaseURL(baseURL),
	)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error creating client: %v\n", err)
		os.Exit(1)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	_, err = client.UpdateQuote(ctx, connect.NewRequest(&payment.UpdateQuoteRequest{
		PayOut: []*payment.UpdateQuoteRequest_Quote{
			{
				Currency:      "EUR",
				QuoteType:     payment.QuoteType_QUOTE_TYPE_REALTIME,
				PaymentMethod: common.PaymentMethodType_PAYMENT_METHOD_TYPE_SEPA,
				Expiration:    timestamppb.New(time.Now().Add(30 * time.Second)),
				Timestamp:     timestamppb.New(time.Now()),
				Bands: []*payment.UpdateQuoteRequest_Quote_Band{
					{
						ClientQuoteId: "go-test-quote",
						MaxAmount:     &common.Decimal{Unscaled: 1000, Exponent: 0},
						Rate:          &common.Decimal{Unscaled: 86, Exponent: -2},
					},
				},
			},
		},
	}))
	if err != nil {
		fmt.Printf("ERROR: %v\n", err)
		os.Exit(1)
	}
	fmt.Println("OK")
}

// cmdCallPayOut makes a signed PayOut RPC call to a Python ProviderService server.
func cmdCallPayOut() {
	if len(os.Args) != 5 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper call-pay-out <base_url> <hex_private_key> <hex_network_public_key>")
		os.Exit(1)
	}
	baseURL := os.Args[2]
	privateKey := network.PrivateKeyHexed(os.Args[3])
	_ = os.Args[4] // network public key (used by server, not client)

	client, err := network.NewServiceClient(
		privateKey,
		paymentconnect.NewProviderServiceClient,
		network.WithBaseURL(baseURL),
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

// testProviderService is a minimal ProviderService for cross-testing.
type testProviderService struct{}

func (s *testProviderService) PayOut(ctx context.Context, req *connect.Request[payment.PayoutRequest]) (*connect.Response[payment.PayoutResponse], error) {
	log.Printf("PayOut called: payment_id=%d", req.Msg.PaymentId)
	return connect.NewResponse(&payment.PayoutResponse{}), nil
}

func (s *testProviderService) UpdatePayment(ctx context.Context, req *connect.Request[payment.UpdatePaymentRequest]) (*connect.Response[payment.UpdatePaymentResponse], error) {
	log.Printf("UpdatePayment called: payment_id=%d", req.Msg.PaymentId)
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

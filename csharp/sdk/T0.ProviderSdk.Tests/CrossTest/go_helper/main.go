// Cross-test helper: Go server and client for C# interoperability testing.
//
// Usage:
//
//	go_helper serve <port> <hex_network_public_key>
//	go_helper call-pay-out-grpc <base_url> <hex_private_key> <hex_network_public_key>
package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/common"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/provider"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper <command> [args...]")
		os.Exit(1)
	}

	switch os.Args[1] {
	case "serve":
		cmdServe()
	case "call-pay-out-grpc":
		cmdCallPayOutGRPC()
	default:
		fmt.Fprintf(os.Stderr, "Unknown command: %s\n", os.Args[1])
		os.Exit(1)
	}
}

// cmdServe starts a Go ConnectRPC provider server.
// The server handles both Connect and gRPC protocols.
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

// cmdCallPayOutGRPC makes a signed PayOut RPC using gRPC wire protocol.
// This is required for C# server compatibility (Grpc.Net only speaks gRPC, not Connect protocol).
func cmdCallPayOutGRPC() {
	if len(os.Args) != 5 {
		fmt.Fprintln(os.Stderr, "Usage: go_helper call-pay-out-grpc <base_url> <hex_private_key> <hex_network_public_key>")
		os.Exit(1)
	}
	baseURL := os.Args[2]
	privateKey := network.PrivateKeyHexed(os.Args[3])
	_ = os.Args[4] // network public key (used by server, not client)

	client, err := network.NewServiceClient(
		privateKey,
		paymentconnect.NewProviderServiceClient,
		network.WithBaseURL(baseURL),
		network.WithConnectOptions(connect.WithGRPC()), // C# gRPC server requires gRPC protocol
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


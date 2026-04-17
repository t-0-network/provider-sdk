package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/joho/godotenv"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/provider"
	"github.com/t-0-network/provider-sdk/go/starter/template/internal"
	"github.com/t-0-network/provider-sdk/go/starter/template/internal/handler"
)

type Config struct {
	NetworkPublicKey   provider.NetworkPublicKeyHexed
	ProviderPrivateKey network.PrivateKeyHexed
	TZeroEndpoint      string
	ServerAddr         string
}

func main() {
	config := loadConfig()

	networkClient := initNetworkClient(config)
	paymentIntentClient := initPaymentIntentClient(config)

	shutdownFunc := startProviderServer(config, networkClient, paymentIntentClient)
	defer shutdownFunc()

	// ✅ Step 1.1 is done. You successfully initialised starter template

	// TODO: Step 1.2 Share the generated public key from .env with t-0 team

	// TODO: Step 1.3 Replace publishQuotes with your own quote publishing logic

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go internal.PublishQuotes(ctx, networkClient)

	// TODO: Step 1.4 Verify that quotes for target currency are successfully received
	go internal.GetQuote(ctx, networkClient)

	// ──────────────────────────────────────────────────────────────
	// Payment Intent Flow — Phase 3
	//
	// Implement the role that applies to you. See the README for details.
	// ──────────────────────────────────────────────────────────────

	// Phase 3A — Pay-In Provider role. Comment out if you are only a beneficiary.
	// TODO: Step 3A.1 Replace with your own pay-in quote publishing logic
	go internal.PublishPaymentIntentQuotes(ctx, paymentIntentClient)

	// Phase 3B — Beneficiary Provider role. Comment out if you are only a pay-in provider.
	// TODO: Step 3B.1 Check that indicative quotes are being returned
	go internal.GetPaymentIntentQuote(ctx, paymentIntentClient)
	// TODO: Step 3B.2 Create a payment intent for a real end-user when they want to pay
	// internal.CreatePaymentIntent(ctx, paymentIntentClient)

	waitForShutdownSignal(cancel, shutdownFunc)

	// TODO: Step 2.2 Deploy your integration and provide t-0 team with the base URL
	// TODO: Step 2.3 Test payment submission
	// TODO: Step 2.5 Ask t-0 team to submit a payment to test your payOut endpoint
}

func loadConfig() Config {
	if err := godotenv.Load(".env"); err != nil {
		log.Fatalf("Failed to load .env file: %v", err)
	}

	return Config{
		NetworkPublicKey:   provider.NetworkPublicKeyHexed(os.Getenv("NETWORK_PUBLIC_KEY")),
		ProviderPrivateKey: network.PrivateKeyHexed(os.Getenv("PROVIDER_PRIVATE_KEY")),
		TZeroEndpoint:      os.Getenv("TZERO_ENDPOINT"),
		ServerAddr:         ":" + os.Getenv("PORT"),
	}
}

func initNetworkClient(config Config) paymentconnect.NetworkServiceClient {
	networkClient, err := network.NewServiceClient(
		config.ProviderPrivateKey,
		paymentconnect.NewNetworkServiceClient,
		network.WithBaseURL(config.TZeroEndpoint),
	)
	if err != nil {
		log.Fatalf("Failed to create network service client: %v", err)
	}
	return networkClient
}

func initPaymentIntentClient(config Config) payment_intentconnect.PaymentIntentServiceClient {
	paymentIntentClient, err := network.NewServiceClient(
		config.ProviderPrivateKey,
		payment_intentconnect.NewPaymentIntentServiceClient,
		network.WithBaseURL(config.TZeroEndpoint),
	)
	if err != nil {
		log.Fatalf("Failed to create payment intent service client: %v", err)
	}
	return paymentIntentClient
}

func startProviderServer(
	config Config,
	networkClient paymentconnect.NetworkServiceClient,
	paymentIntentClient payment_intentconnect.PaymentIntentServiceClient,
) func() {
	providerServiceHandler, err := provider.NewHttpHandler(
		config.NetworkPublicKey,
		provider.Handler(paymentconnect.NewProviderServiceHandler,
			paymentconnect.ProviderServiceHandler(handler.NewProviderServiceImplementation(networkClient))),
		// Phase 3A — Pay-In Provider role. Remove if you are only a beneficiary.
		provider.Handler(payment_intentconnect.NewPayInProviderServiceHandler,
			payment_intentconnect.PayInProviderServiceHandler(handler.NewPayInProviderServiceImplementation(paymentIntentClient))),
		// Phase 3B — Beneficiary Provider role. Remove if you are only a pay-in provider.
		provider.Handler(payment_intentconnect.NewBeneficiaryServiceHandler,
			payment_intentconnect.BeneficiaryServiceHandler(handler.NewBeneficiaryServiceImplementation())),
	)
	if err != nil {
		log.Fatalf("Failed to create provider service handler: %v", err)
	}

	shutdownFunc, err := provider.StartServer(
		providerServiceHandler,
		provider.WithAddr(config.ServerAddr),
	)
	if err != nil {
		log.Fatalf("Failed to start provider server: %v", err)
	}

	log.Printf("✅ Step 1.1: Provider server initialized on %s\n", config.ServerAddr)

	return func() {
		if err := shutdownFunc(context.Background()); err != nil {
			log.Fatalf("Failed to shutdown provider service: %v", err)
		}
	}
}

func waitForShutdownSignal(cancel context.CancelFunc, shutdownFunc func()) {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	<-ctx.Done()

	log.Println("Shutting down...")
	cancel()
	shutdownFunc()
}

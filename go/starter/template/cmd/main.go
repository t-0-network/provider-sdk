package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment/paymentconnect"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/payment_intent/payment_intentconnect"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/provider"
	"github.com/t-0-network/provider-sdk/go/starter/template/internal"
	"github.com/t-0-network/provider-sdk/go/starter/template/internal/handler"
)

func main() {
	config, err := loadConfig()
	if err != nil {
		exitFailure(err)
	}

	networkClient, err := initNetworkClient(config)
	if err != nil {
		exitFailure(err)
	}
	paymentIntentClient, err := initPaymentIntentClient(config)
	if err != nil {
		exitFailure(err)
	}

	shutdown, err := startProviderServer(config, networkClient, paymentIntentClient)
	if err != nil {
		exitFailure(err)
	}

	// ✅ Step 1.1 is done. You successfully initialised starter template

	// TODO: Step 1.2 Share the generated public key from .env with t-0 team

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	quoteTicker := time.NewTicker(config.QuotePublishingInterval)
	paymentIntentTicker := time.NewTicker(config.QuotePublishingInterval)

	// TODO: Step 1.3 Replace publishQuotes with your own quote publishing logic
	go internal.PublishQuotes(ctx, networkClient, quoteTicker)

	// TODO: Step 1.4 Verify that quotes for target currency are successfully received
	go internal.GetQuote(ctx, networkClient)

	// ──────────────────────────────────────────────────────────────
	// Payment Intent Flow — Phase 3
	//
	// Implement the role that applies to you. See the README for details.
	// ──────────────────────────────────────────────────────────────

	// Phase 3A — Pay-In Provider role. Comment out if you are only a beneficiary.
	// TODO: Step 3A.1 Replace with your own pay-in quote publishing logic
	go internal.PublishPaymentIntentQuotes(ctx, paymentIntentClient, paymentIntentTicker)

	// Phase 3B — Beneficiary Provider role. Comment out if you are only a pay-in provider.
	// TODO: Step 3B.1 Check that indicative quotes are being returned
	go internal.GetPaymentIntentQuote(ctx, paymentIntentClient)
	// TODO: Step 3B.2 Create a payment intent for a real end-user when they want to pay
	// internal.CreatePaymentIntent(ctx, paymentIntentClient)

	<-ctx.Done()

	log.Println("Shutting down...")
	quoteTicker.Stop()
	paymentIntentTicker.Stop()
	if err := shutdown(context.Background()); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to shutdown provider service: %s\n", err.Error())
	}

	// TODO: Step 2.2 Deploy your integration and provide t-0 team with the base URL
	// TODO: Step 2.3 Test payment submission
	// TODO: Step 2.5 Ask t-0 team to submit a payment to test your payOut endpoint
	// TODO: Step 2.6 (optional) If your PayOut handler returns manual_aml_check, report the
	// outcome of your AML check (see internal/complete_manual_aml_check.go)
	// internal.CompleteManualAmlCheck(ctx, networkClient, paymentId)
}

func initNetworkClient(config Config) (paymentconnect.NetworkServiceClient, error) {
	return network.NewServiceClient(
		config.ProviderPrivateKey,
		paymentconnect.NewNetworkServiceClient,
		network.WithBaseURL(config.TZeroEndpoint),
	)
}

func initPaymentIntentClient(config Config) (payment_intentconnect.PaymentIntentServiceClient, error) {
	return network.NewServiceClient(
		config.ProviderPrivateKey,
		payment_intentconnect.NewPaymentIntentServiceClient,
		network.WithBaseURL(config.TZeroEndpoint),
	)
}

func startProviderServer(
	config Config,
	networkClient paymentconnect.NetworkServiceClient,
	paymentIntentClient payment_intentconnect.PaymentIntentServiceClient,
) (provider.ServerShutdownFn, error) {
	// Replace slog.Default() with your own *slog.Logger (e.g. JSON to a file,
	// or an slog.Handler bridge to zap / zerolog) to route SDK diagnostics
	// such as response-validation failures into your log pipeline.
	providerServiceHandler, err := provider.NewHttpHandlerWithOptions(
		config.NetworkPublicKey,
		[]provider.HttpHandlerOption{
			provider.WithLogger(slog.Default()),
		},
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
		return nil, asConfigurationError(err)
	}

	shutdown, err := provider.StartServer(
		providerServiceHandler,
		provider.WithAddr(config.ServerAddr),
	)
	if err != nil {
		return nil, err
	}

	log.Printf("✅ Step 1.1: Provider server initialized on %s\n", config.ServerAddr)
	return shutdown, nil
}

func exitFailure(err error) {
	var ce *configurationError
	if errors.As(err, &ce) {
		fmt.Fprintf(os.Stderr, "ERROR: %s\n%s\n", ce.msg, ce.help)
	} else {
		fmt.Fprintf(os.Stderr, "Provider failed to start: %s\n", err.Error())
	}
	os.Exit(1)
}

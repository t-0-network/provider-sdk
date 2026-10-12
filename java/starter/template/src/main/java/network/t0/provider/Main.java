package network.t0.provider;

import network.t0.provider.handler.PaymentHandler;
import network.t0.provider.handler.PaymentIntentBeneficiaryHandler;
import network.t0.provider.handler.PaymentIntentPayInHandler;
import network.t0.provider.internal.GetPaymentIntentQuote;
import network.t0.provider.internal.GetQuote;
import network.t0.provider.internal.PublishPaymentIntentQuotes;
import network.t0.provider.internal.PublishQuotes;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.proto.tzero.v1.payment.NetworkServiceGrpc;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentServiceGrpc;
import network.t0.sdk.provider.ProviderServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import sun.misc.Signal;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for the T-0 Network Provider.
 *
 * <p>This is a starter template that guides you through the integration process.
 * Follow the TODO comments in order to complete your integration.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final AtomicBoolean DRAINING = new AtomicBoolean();

    public static void main(String[] args) {
        // grpc-java logs through java.util.logging. The bridge is what makes the
        // io.grpc level in logback.xml apply, and dropping the JDK handler keeps
        // those records from also printing in raw JUL form.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try {
            run();
        } catch (EnvConfig.ConfigurationException e) {
            if (e.missingEnvNotice() != null) {
                System.err.println(e.missingEnvNotice());
            }
            writeConfigError(e, System.err);
            System.exit(1);
        } catch (Exception e) {
            writeStartupError(e, System.err);
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        EnvConfig.Loaded loaded = EnvConfig.load(workingDirectory, System.getenv());
        if (loaded.missingEnvNotice() != null) {
            System.err.println(loaded.missingEnvNotice());
        }
        Config config = loaded.config();
        Signer signer = Signer.fromHex(config.providerPrivateKey());

        log.info("Provider public key: {}", signer.getPublicKeyHexPrefixed());
        log.info("Share this public key with T-0 team (Step 1.2)");

        var networkClient = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer, NetworkServiceGrpc::newBlockingStub);

        var paymentIntentClient = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer, PaymentIntentServiceGrpc::newBlockingStub);

        ProviderServer server = startProviderServer(config, networkClient, paymentIntentClient);

        // Step 1.1 is done. You successfully initialised starter template

        // TODO: Step 1.2 Share the generated public key from .env with t-0 team

        // TODO: Step 1.3 Replace publishQuotes with your own quote publishing logic

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        // A shutdown hook cannot change 130 or 143. INT and TERM call close() and exit 0.
        installSignals(server, scheduler, networkClient, paymentIntentClient);

        scheduler.scheduleAtFixedRate(
                () -> PublishQuotes.publish(networkClient.stub()),
                0, config.quotePublishingIntervalMs(), TimeUnit.MILLISECONDS);

        // TODO: Step 1.4 Verify that quotes for target currency are successfully received
        GetQuote.fetch(networkClient.stub());

        // ──────────────────────────────────────────────────────────────
        // Payment Intent Flow — Phase 3
        //
        // Implement the role that applies to you. See the README for details.
        // ──────────────────────────────────────────────────────────────

        // Phase 3A — Pay-In Provider role. Comment out if you are only a beneficiary.
        // TODO: Step 3A.1 Replace with your own pay-in quote publishing logic
        scheduler.scheduleAtFixedRate(
                () -> PublishPaymentIntentQuotes.publish(paymentIntentClient.stub()),
                0, config.quotePublishingIntervalMs(), TimeUnit.MILLISECONDS);

        // Phase 3B — Beneficiary Provider role. Comment out if you are only a pay-in provider.
        // TODO: Step 3B.1 Check that indicative quotes are being returned
        GetPaymentIntentQuote.fetch(paymentIntentClient.stub());
        // TODO: Step 3B.2 Create a payment intent for a real end-user when they want to pay
        // CreatePaymentIntent.create(paymentIntentClient.stub());

        // TODO: Step 2.2 Deploy your integration and provide t-0 team with the base URL
        // TODO: Step 2.3 Test payment submission (see SubmitPayment.java)
        // TODO: Step 2.5 Ask t-0 team to submit a payment to test your payOut endpoint
        // TODO: Step 2.6 (optional) Complete manual AML checks if your payOut returns manual_aml_check (see CompleteManualAmlCheck.java)

        // The signal handler exits the process. Park here so main does not return first.
        new CountDownLatch(1).await();
    }

    private static ProviderServer startProviderServer(
            Config config,
            BlockingNetworkClient<NetworkServiceGrpc.NetworkServiceBlockingStub> networkClient,
            BlockingNetworkClient<PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub> paymentIntentClient)
            throws IOException {
        PaymentHandler paymentHandler = new PaymentHandler(networkClient.stub());
        PaymentIntentPayInHandler payInHandler = new PaymentIntentPayInHandler(paymentIntentClient.stub());
        PaymentIntentBeneficiaryHandler beneficiaryHandler = new PaymentIntentBeneficiaryHandler();

        ProviderServer.Builder builder;
        try {
            builder = ProviderServer.create(config.port(), config.networkPublicKey());
        } catch (IllegalArgumentException e) {
            EnvConfig.rethrowNetworkKey(e);
            throw e;
        }

        ProviderServer server = builder
                .withService(paymentHandler)
                // Phase 3A — Pay-In Provider role. Remove if you are only a beneficiary.
                .withService(payInHandler)
                // Phase 3B — Beneficiary Provider role. Remove if you are only a pay-in provider.
                .withService(beneficiaryHandler)
                // SDK safety-net log line for response-validation failures routes through this
                // SLF4J logger. Swap for your own logger to integrate with your logging stack.
                .withLogger(LoggerFactory.getLogger("provider"))
                .start();

        log.info("Step 1.1: Provider server initialized on port {}", server.getPort());
        return server;
    }

    private static void installSignals(
            ProviderServer server,
            ScheduledExecutorService scheduler,
            BlockingNetworkClient<NetworkServiceGrpc.NetworkServiceBlockingStub> networkClient,
            BlockingNetworkClient<PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub> paymentIntentClient) {
        sun.misc.SignalHandler handler = signal ->
                drainAndExit(server, scheduler, networkClient, paymentIntentClient);
        Signal.handle(new Signal("INT"), handler);
        Signal.handle(new Signal("TERM"), handler);
    }

    private static void drainAndExit(
            ProviderServer server,
            ScheduledExecutorService scheduler,
            BlockingNetworkClient<NetworkServiceGrpc.NetworkServiceBlockingStub> networkClient,
            BlockingNetworkClient<PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub> paymentIntentClient) {
        if (!DRAINING.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.shutdown();
            // close() is the shutdown the server already owns: 5s graceful, then shutdownNow.
            server.close();
            networkClient.shutdown();
            paymentIntentClient.shutdown();
        } catch (Exception e) {
            System.err.println("Provider failed to shut down: " + causeText(e));
        }
        System.exit(0);
    }

    static void writeConfigError(EnvConfig.ConfigurationException error, PrintStream err) {
        err.println("ERROR: " + error.getMessage());
        err.println(error.getHelpMessage());
    }

    static void writeStartupError(Throwable error, PrintStream err) {
        err.println("Provider failed to start: " + causeText(error));
    }

    private static String causeText(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message.replace('\n', ' ').replace('\r', ' ').strip();
            }
            current = current.getCause();
        }
        return error.getClass().getSimpleName();
    }
}

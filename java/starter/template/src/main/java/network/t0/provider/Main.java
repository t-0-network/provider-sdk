package network.t0.provider;

import io.github.cdimascio.dotenv.Dotenv;
import network.t0.provider.handler.PaymentHandler;
import network.t0.provider.internal.GetQuote;
import network.t0.provider.internal.PublishQuotes;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.proto.tzero.v1.payment.NetworkServiceGrpc;
import network.t0.sdk.provider.ProviderServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the T-0 Network Provider.
 *
 * <p>This is a starter template that guides you through the integration process.
 * Follow the TODO comments in order to complete your integration.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            run();
        } catch (ConfigurationException e) {
            log.error(e.getMessage());
            log.error(e.getHelpMessage());
            System.exit(1);
        } catch (Exception e) {
            log.error("Failed to start provider", e);
            System.exit(1);
        }
    }

    private static void run() {
        Config config = loadConfig();
        Signer signer = Signer.fromHex(config.providerPrivateKey());

        log.info("Provider public key: {}", signer.getPublicKeyHexPrefixed());
        log.info("Share this public key with T-0 team (Step 1.2)");

        var networkClient = BlockingNetworkClient.create(
                config.tzeroEndpoint(), signer, NetworkServiceGrpc::newBlockingStub);

        ProviderServer server = startProviderServer(config, networkClient);

        // Step 1.1 is done. You successfully initialised starter template

        // TODO: Step 1.2 Share the generated public key from .env with t-0 team

        // TODO: Step 1.3 Replace publishQuotes with your own quote publishing logic

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
                () -> PublishQuotes.publish(networkClient.stub()),
                0, config.quotePublishingIntervalMs(), TimeUnit.MILLISECONDS);

        // TODO: Step 1.4 Verify that quotes for target currency are successfully received
        GetQuote.fetch(networkClient.stub());

        waitForShutdown(server, scheduler, networkClient);

        // TODO: Step 2.2 Deploy your integration and provide t-0 team with the base URL
        // TODO: Step 2.3 Test payment submission (see SubmitPayment.java)
        // TODO: Step 2.5 Ask t-0 team to submit a payment to test your payOut endpoint
    }

    private static Config loadConfig() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        String privateKey = dotenv.get("PROVIDER_PRIVATE_KEY");
        String networkPublicKey = dotenv.get("NETWORK_PUBLIC_KEY");
        String endpoint = dotenv.get("TZERO_ENDPOINT", "https://api-sandbox.t-0.network");
        int port = Integer.parseInt(dotenv.get("PORT", "8080"));
        long quoteInterval = Long.parseLong(dotenv.get("QUOTE_PUBLISHING_INTERVAL", "5000"));

        if (privateKey == null || privateKey.isEmpty()) {
            throw new ConfigurationException(
                    "PROVIDER_PRIVATE_KEY not set in .env file",
                    "Generate a keypair with: ./gradlew generateKeys");
        }

        if (networkPublicKey == null || networkPublicKey.isEmpty()) {
            throw new ConfigurationException(
                    "NETWORK_PUBLIC_KEY not set in .env file",
                    "Contact T-0 team to get the network public key");
        }

        return new Config(privateKey, networkPublicKey, endpoint, port, quoteInterval);
    }

    private static ProviderServer startProviderServer(
            Config config,
            BlockingNetworkClient<NetworkServiceGrpc.NetworkServiceBlockingStub> networkClient) {
        try {
            PaymentHandler handler = new PaymentHandler(networkClient.stub());

            ProviderServer server = ProviderServer.create(config.port(), config.networkPublicKey())
                    .withService(handler)
                    .start();

            log.info("Step 1.1: Provider server initialized on port {}", server.getPort());

            return server;

        } catch (IOException e) {
            throw new RuntimeException("Failed to start provider server", e);
        }
    }

    private static void waitForShutdown(
            ProviderServer server,
            ScheduledExecutorService scheduler,
            BlockingNetworkClient<NetworkServiceGrpc.NetworkServiceBlockingStub> networkClient) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            scheduler.shutdown();
            server.shutdown();
            networkClient.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.SECONDS);
                server.awaitTermination(10, TimeUnit.SECONDS);
                networkClient.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Exception thrown when configuration is invalid or missing.
     */
    private static class ConfigurationException extends RuntimeException {
        private final String helpMessage;

        ConfigurationException(String message, String helpMessage) {
            super(message);
            this.helpMessage = helpMessage;
        }

        String getHelpMessage() {
            return helpMessage;
        }
    }
}

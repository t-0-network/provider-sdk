package network.t0.provider;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads {@code .env} and the process environment. The process environment wins.
 * An empty value counts as unset. This does not start a server.
 */
final class EnvConfig {

    static final String SANDBOX_ENDPOINT = "https://api-sandbox.t-0.network";

    static final String NETWORK_PUBLIC_KEY_HELP =
            "Ask the t-0 team for the network public key and put it in .env.";

    private static final String PORT_HELP =
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";

    private EnvConfig() {
    }

    static Config load(Path workingDirectory) {
        Path envFile = workingDirectory.resolve(".env").toAbsolutePath().normalize();
        if (!Files.exists(envFile)) {
            System.err.println("No .env at " + envFile + " — taking configuration from the environment instead");
        }
        // get() returns the process environment's value when the variable is set, even to "".
        Dotenv dotenv = Dotenv.configure()
                .directory(workingDirectory.toString())
                .ignoreIfMissing()
                .load();

        String privateKey = dotenv.get("PROVIDER_PRIVATE_KEY", "").strip();
        String networkPublicKey = dotenv.get("NETWORK_PUBLIC_KEY", "").strip();

        if (privateKey.isEmpty()) {
            throw new ConfigurationException(
                    "PROVIDER_PRIVATE_KEY is not set",
                    ".env is read from the working directory, and we looked in " + envFile
                            + ". Run the app from the directory holding your .env, or set "
                            + "PROVIDER_PRIVATE_KEY in the environment. Only a project with no .env "
                            + "at all starts one from .env.example — an existing .env holds the key "
                            + "generated for you, and its private half is not recoverable.");
        }
        if (networkPublicKey.isEmpty()) {
            throw new ConfigurationException("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP);
        }

        String endpoint = dotenv.get("TZERO_ENDPOINT", "").strip();
        if (endpoint.isEmpty()) {
            endpoint = SANDBOX_ENDPOINT;
        }
        int port = parsePort(dotenv.get("PORT", ""));
        long quoteInterval = parseQuoteInterval(dotenv.get("QUOTE_PUBLISHING_INTERVAL"));

        return new Config(privateKey, networkPublicKey, endpoint, port, quoteInterval);
    }

    // Trimmed. Empty means 8080. Otherwise an integer from 1 to 65535.
    private static int parsePort(String raw) {
        String value = raw.strip();
        if (value.isEmpty()) {
            return 8080;
        }
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // Reported below, like a port out of range.
        }
        throw new ConfigurationException("PORT is not a valid port number: " + value, PORT_HELP);
    }

    // In milliseconds. Anything but a positive integer gives 5000.
    private static long parseQuoteInterval(String value) {
        try {
            long interval = Long.parseLong(value);
            if (interval >= 1) {
                return interval;
            }
        } catch (NumberFormatException e) {
            // Falls back below.
        }
        return 5000;
    }

    static final class ConfigurationException extends RuntimeException {
        private final String helpMessage;

        private ConfigurationException(String message, String helpMessage) {
            super(message);
            this.helpMessage = helpMessage;
        }

        String getHelpMessage() {
            return helpMessage;
        }
    }
}

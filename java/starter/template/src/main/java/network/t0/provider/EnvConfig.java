package network.t0.provider;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import network.t0.sdk.crypto.Signer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads {@code .env} and the process environment. The process environment wins.
 * An empty value counts as unset. This does not start a server.
 */
final class EnvConfig {

    static final String SANDBOX_ENDPOINT = "https://api-sandbox.t-0.network";

    static final String NETWORK_PUBLIC_KEY_HELP =
            "Ask the t-0 team for the network public key and put it in .env.";

    private static final String PRIVATE_KEY_UNUSABLE_HELP =
            "Any 32 random bytes will do: openssl rand -hex 32.";

    private static final String PORT_HELP =
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";

    private EnvConfig() {
    }

    record Loaded(Config config, String missingEnvNotice) {
    }

    static Loaded load(Path workingDirectory, Map<String, String> environment) {
        Path envFile = workingDirectory.resolve(".env").toAbsolutePath().normalize();
        String notice = null;
        Map<String, String> file = Map.of();
        if (!Files.exists(envFile)) {
            notice = "No .env at " + envFile + " — taking configuration from the environment instead";
        } else {
            file = readEnvFile(envFile);
        }

        String privateKey = orEmpty(lookup(file, environment, "PROVIDER_PRIVATE_KEY")).strip();
        String networkPublicKey = orEmpty(lookup(file, environment, "NETWORK_PUBLIC_KEY")).strip();

        if (privateKey.isEmpty()) {
            throw configError(
                    "PROVIDER_PRIVATE_KEY is not set",
                    ".env is read from the working directory, and we looked in " + envFile
                            + ". Run the app from the directory holding your .env, or set "
                            + "PROVIDER_PRIVATE_KEY in the environment. Only a project with no .env "
                            + "at all starts one from .env.example — an existing .env holds the key "
                            + "generated for you, and its private half is not recoverable.",
                    notice);
        }
        if (networkPublicKey.isEmpty()) {
            throw configError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP, notice);
        }
        try {
            Signer.fromHex(privateKey);
        } catch (IllegalArgumentException e) {
            throw configError(
                    "PROVIDER_PRIVATE_KEY is not usable: " + e.getMessage(),
                    PRIVATE_KEY_UNUSABLE_HELP,
                    notice);
        }

        String endpoint = orEmpty(lookup(file, environment, "TZERO_ENDPOINT")).strip();
        if (endpoint.isEmpty()) {
            endpoint = SANDBOX_ENDPOINT;
        }
        int port = parsePort(lookup(file, environment, "PORT"), notice);
        long quoteInterval = parseQuoteInterval(lookup(file, environment, "QUOTE_PUBLISHING_INTERVAL"));

        return new Loaded(
                new Config(privateKey, networkPublicKey, endpoint, port, quoteInterval),
                notice);
    }

    /**
     * Server setup rejected the network key. The SDK text stays when it is already
     * the {@code invalid network public key: } message. The missing-.env notice was
     * already printed, so this error does not carry it again.
     */
    static void rethrowNetworkKey(IllegalArgumentException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.startsWith("invalid network public key: ")) {
            throw configError(message, NETWORK_PUBLIC_KEY_HELP, null);
        }
        throw error;
    }

    private static Map<String, String> readEnvFile(Path envFile) {
        Dotenv dotenv = Dotenv.configure()
                .directory(envFile.getParent().toString())
                .filename(envFile.getFileName().toString())
                .load();
        Map<String, String> values = new HashMap<>();
        for (DotenvEntry entry : dotenv.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)) {
            values.put(entry.getKey(), entry.getValue());
        }
        return values;
    }

    /** Process environment wins, including when its value is empty. */
    private static String lookup(Map<String, String> file, Map<String, String> environment, String key) {
        if (environment.containsKey(key)) {
            return environment.get(key);
        }
        return file.get(key);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    // Trim first. Whitespace-only means 8080. What remains must be digits in 1..65535.
    private static int parsePort(String raw, String missingEnvNotice) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return 8080;
        }
        if (value.matches("[0-9]+")) {
            try {
                int port = Integer.parseInt(value);
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException e) {
                // More digits than an int. Same error as any other rejected port.
            }
        }
        throw configError("PORT is not a valid port number: " + value, PORT_HELP, missingEnvNotice);
    }

    // In milliseconds; anything but an integer from 1 to 2147483647 gives 5000.
    private static long parseQuoteInterval(String value) {
        try {
            long interval = Long.parseLong(value);
            if (interval >= 1 && interval <= Integer.MAX_VALUE) {
                return interval;
            }
        } catch (NumberFormatException e) {
            // falls back below
        }
        return 5000;
    }

    private static ConfigurationException configError(String message, String help, String missingEnvNotice) {
        return new ConfigurationException(message, help, missingEnvNotice);
    }

    static final class ConfigurationException extends RuntimeException {
        private final String helpMessage;
        private final String missingEnvNotice;

        private ConfigurationException(String message, String helpMessage, String missingEnvNotice) {
            super(message);
            this.helpMessage = helpMessage;
            this.missingEnvNotice = missingEnvNotice;
        }

        String getHelpMessage() {
            return helpMessage;
        }

        String missingEnvNotice() {
            return missingEnvNotice;
        }
    }
}

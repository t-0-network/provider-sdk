package network.t0.provider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Configuration only. This does not start a server. Each case writes a {@code .env}
 * to its own directory, so none of the five variables may be set in the environment.
 */
class ConfigTest {

    private static final String PRIVATE_KEY =
            "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String NETWORK_KEY =
            "0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b";

    private static final String NETWORK_HELP =
            "Ask the t-0 team for the network public key and put it in .env.";
    private static final String PORT_HELP =
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";

    private static final String KEYS =
            "PROVIDER_PRIVATE_KEY=" + PRIVATE_KEY + "\n" + "NETWORK_PUBLIC_KEY=" + NETWORK_KEY + "\n";

    @TempDir
    Path root;

    @BeforeAll
    static void environmentIsClean() {
        for (String name : List.of("PROVIDER_PRIVATE_KEY", "NETWORK_PUBLIC_KEY", "TZERO_ENDPOINT", "PORT",
                "QUOTE_PUBLISHING_INTERVAL")) {
            assumeTrue(System.getenv(name) == null, name + " is set in the environment");
        }
    }

    @Test
    void config() {
        List<org.junit.jupiter.api.function.Executable> cases = new ArrayList<>();
        cases.add(() -> trimsBothKeys());
        cases.add(() -> blankPrivateKeyIsNotSet());
        cases.add(() -> blankNetworkKeyIsNotSet());
        cases.add(() -> portAccepted("blank", "", 8080));
        cases.add(() -> portAccepted("9000", "9000", 9000));
        cases.add(() -> portAccepted("padded", "\" 9000 \"", 9000));
        cases.add(() -> portRejected("0"));
        cases.add(() -> portRejected("http"));
        cases.add(() -> portRejected("65536"));
        cases.add(() -> blankEndpointUsesSandbox("blank", ""));
        cases.add(() -> blankEndpointUsesSandbox("whitespace", "\"   \""));
        cases.add(() -> missingEnvIsNotAFailure());
        assertAll(cases);
    }

    private void trimsBothKeys() throws Exception {
        Path dir = withEnv("trim",
                "PROVIDER_PRIVATE_KEY=\"  " + PRIVATE_KEY + "  \"\n"
                        + "NETWORK_PUBLIC_KEY=\"  " + NETWORK_KEY + "  \"\n");
        Config config = EnvConfig.load(dir);
        assertEquals(PRIVATE_KEY, config.providerPrivateKey(), "trim private");
        assertEquals(NETWORK_KEY, config.networkPublicKey(), "trim network");
    }

    private void blankPrivateKeyIsNotSet() throws Exception {
        Path dir = withEnv("blank-private",
                "PROVIDER_PRIVATE_KEY=\"   \"\nNETWORK_PUBLIC_KEY=" + NETWORK_KEY + "\n");
        expectConfig(dir, "PROVIDER_PRIVATE_KEY is not set", privateKeyHelp(dir));
    }

    private void blankNetworkKeyIsNotSet() throws Exception {
        Path dir = withEnv("blank-network", "PROVIDER_PRIVATE_KEY=" + PRIVATE_KEY + "\nNETWORK_PUBLIC_KEY=\n");
        expectConfig(dir, "NETWORK_PUBLIC_KEY is not set", NETWORK_HELP);
    }

    private void portAccepted(String name, String raw, int expected) throws Exception {
        Path dir = withEnv("port-ok-" + name, KEYS + "PORT=" + raw + "\n");
        assertEquals(expected, EnvConfig.load(dir).port(), name);
    }

    private void portRejected(String raw) throws Exception {
        Path dir = withEnv("port-bad-" + raw, KEYS + "PORT=" + raw + "\n");
        expectConfig(dir, "PORT is not a valid port number: " + raw, PORT_HELP);
    }

    private void blankEndpointUsesSandbox(String name, String raw) throws Exception {
        Path dir = withEnv("endpoint-" + name, KEYS + "TZERO_ENDPOINT=" + raw + "\n");
        assertEquals(EnvConfig.SANDBOX_ENDPOINT, EnvConfig.load(dir).tzeroEndpoint(), "endpoint " + name);
    }

    // With no .env and none of the variables set, the only failure is the missing key.
    private void missingEnvIsNotAFailure() throws Exception {
        Path dir = Files.createDirectory(root.resolve("missing-env"));
        expectConfig(dir, "PROVIDER_PRIVATE_KEY is not set", privateKeyHelp(dir));
    }

    private void expectConfig(Path dir, String message, String help) {
        EnvConfig.ConfigurationException error = assertThrows(
                EnvConfig.ConfigurationException.class,
                () -> EnvConfig.load(dir));
        assertEquals(message, error.getMessage());
        assertEquals(help, error.getHelpMessage());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Main.writeConfigError(error, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertEquals(
                "ERROR: " + message + System.lineSeparator() + help + System.lineSeparator(),
                buffer.toString(StandardCharsets.UTF_8));
    }

    private Path withEnv(String name, String contents) throws Exception {
        Path dir = Files.createDirectory(root.resolve(name));
        Files.writeString(dir.resolve(".env"), contents);
        return dir;
    }

    private static String privateKeyHelp(Path dir) {
        String path = dir.resolve(".env").toAbsolutePath().normalize().toString();
        return ".env is read from the working directory, and we looked in " + path
                + ". Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY "
                + "in the environment. Only a project with no .env at all starts one from .env.example "
                + "— an existing .env holds the key generated for you, and its private half is not recoverable.";
    }
}

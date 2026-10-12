package network.t0.provider;

import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Configuration only. This does not start a server.
 */
class ConfigTest {

    private static final String PRIVATE_KEY =
            "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String OTHER_PRIVATE_KEY =
            "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String NETWORK_KEY =
            "0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b";

    private static final String NETWORK_HELP =
            "Ask the t-0 team for the network public key and put it in .env.";
    private static final String PORT_HELP =
            "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";
    private static final String UNUSABLE_HELP =
            "Any 32 random bytes will do: openssl rand -hex 32.";

    @TempDir
    Path root;

    @Test
    void config() {
        List<org.junit.jupiter.api.function.Executable> cases = new ArrayList<>();
        cases.add(() -> trimsBothKeysFromFile());
        cases.add(() -> trimsBothKeysFromEnvironment());
        cases.add(() -> environmentWinsOverFile());
        cases.add(() -> blankPrivateKeyIsNotSet());
        cases.add(() -> blankNetworkKeyIsNotSet());
        cases.add(() -> badPrivateKeyIsNotUsable());
        cases.add(() -> portAccepted("blank", "", 8080));
        cases.add(() -> portAccepted("8080", "8080", 8080));
        cases.add(() -> portAccepted("padded 8080", " 8080 ", 8080));
        cases.add(() -> portAccepted("whitespace", "   ", 8080));
        cases.add(() -> portRejected("0"));
        cases.add(() -> portRejected("http"));
        cases.add(() -> portRejected("0x1F90"));
        cases.add(() -> portRejected("1e3"));
        cases.add(() -> portRejected("+8080"));
        cases.add(() -> blankEndpointUsesSandbox(""));
        cases.add(() -> blankEndpointUsesSandbox("   "));
        cases.add(() -> missingEnvNotice());
        assertAll(cases);
    }

    private void trimsBothKeysFromFile() throws Exception {
        Path dir = caseDir("trim-file");
        Files.writeString(dir.resolve(".env"),
                "PROVIDER_PRIVATE_KEY=\"" + "  " + PRIVATE_KEY + "  " + "\"\n"
                        + "NETWORK_PUBLIC_KEY=\"" + "  " + NETWORK_KEY + "  " + "\"\n");
        EnvConfig.Loaded loaded = EnvConfig.load(dir, Map.of());
        assertEquals(PRIVATE_KEY, loaded.config().providerPrivateKey(), "trim-file private");
        assertEquals(NETWORK_KEY, loaded.config().networkPublicKey(), "trim-file network");
        assertNull(loaded.missingEnvNotice(), "trim-file notice");
    }

    private void trimsBothKeysFromEnvironment() throws Exception {
        Path dir = caseDir("trim-env");
        Files.writeString(dir.resolve(".env"), "PORT=8080\n");
        Map<String, String> environment = new HashMap<>();
        environment.put("PROVIDER_PRIVATE_KEY", "  " + PRIVATE_KEY + "  ");
        environment.put("NETWORK_PUBLIC_KEY", "\t" + NETWORK_KEY + "\t");
        EnvConfig.Loaded loaded = EnvConfig.load(dir, environment);
        assertEquals(PRIVATE_KEY, loaded.config().providerPrivateKey(), "trim-env private");
        assertEquals(NETWORK_KEY, loaded.config().networkPublicKey(), "trim-env network");
    }

    private void environmentWinsOverFile() throws Exception {
        Path dir = caseDir("env-wins");
        Files.writeString(dir.resolve(".env"),
                "PROVIDER_PRIVATE_KEY=" + OTHER_PRIVATE_KEY + "\n"
                        + "NETWORK_PUBLIC_KEY=" + NETWORK_KEY + "\n");
        Map<String, String> environment = keys();
        EnvConfig.Loaded loaded = EnvConfig.load(dir, environment);
        assertEquals(PRIVATE_KEY, loaded.config().providerPrivateKey(), "env wins");
    }

    private void blankPrivateKeyIsNotSet() throws Exception {
        Path dir = caseDir("blank-private");
        Files.writeString(dir.resolve(".env"), "NETWORK_PUBLIC_KEY=" + NETWORK_KEY + "\n");
        Map<String, String> environment = new HashMap<>();
        environment.put("PROVIDER_PRIVATE_KEY", "   ");
        expectConfig(dir, environment,
                "PROVIDER_PRIVATE_KEY is not set",
                privateKeyHelp(dir));
    }

    private void blankNetworkKeyIsNotSet() throws Exception {
        Path dir = caseDir("blank-network");
        Files.writeString(dir.resolve(".env"), "PROVIDER_PRIVATE_KEY=" + PRIVATE_KEY + "\n");
        Map<String, String> environment = new HashMap<>();
        environment.put("NETWORK_PUBLIC_KEY", "");
        expectConfig(dir, environment, "NETWORK_PUBLIC_KEY is not set", NETWORK_HELP);
    }

    private void badPrivateKeyIsNotUsable() throws Exception {
        Path dir = caseDir("bad-private");
        Files.writeString(dir.resolve(".env"), "");
        String sdkMessage;
        try {
            Signer.fromHex("zzzz");
            fail("the SDK parser should reject zzzz");
            return;
        } catch (IllegalArgumentException e) {
            sdkMessage = e.getMessage();
        }
        expectConfig(dir, Map.of("PROVIDER_PRIVATE_KEY", "zzzz", "NETWORK_PUBLIC_KEY", NETWORK_KEY),
                "PROVIDER_PRIVATE_KEY is not usable: " + sdkMessage,
                UNUSABLE_HELP);
    }

    private void portAccepted(String name, String raw, int expected) throws Exception {
        Path dir = caseDir("port-ok-" + name.replace(' ', '-'));
        Files.writeString(dir.resolve(".env"), "");
        Map<String, String> environment = keys();
        environment.put("PORT", raw);
        EnvConfig.Loaded loaded = EnvConfig.load(dir, environment);
        assertEquals(expected, loaded.config().port(), name);
    }

    private void portRejected(String raw) throws Exception {
        Path dir = caseDir("port-bad-" + raw.replace('+', 'p'));
        Files.writeString(dir.resolve(".env"), "");
        Map<String, String> environment = keys();
        environment.put("PORT", raw);
        expectConfig(dir, environment, "PORT is not a valid port number: " + raw.strip(), PORT_HELP);
    }

    private void blankEndpointUsesSandbox(String raw) throws Exception {
        Path dir = caseDir("endpoint-" + raw.length());
        Files.writeString(dir.resolve(".env"), "");
        Map<String, String> environment = keys();
        environment.put("TZERO_ENDPOINT", raw);
        EnvConfig.Loaded loaded = EnvConfig.load(dir, environment);
        assertEquals(EnvConfig.SANDBOX_ENDPOINT, loaded.config().tzeroEndpoint(), "endpoint " + raw.length());
    }

    private void missingEnvNotice() throws Exception {
        Path dir = caseDir("missing-env");
        EnvConfig.Loaded loaded = EnvConfig.load(dir, keys());
        String path = dir.resolve(".env").toAbsolutePath().normalize().toString();
        assertEquals(
                "No .env at " + path + " — taking configuration from the environment instead",
                loaded.missingEnvNotice());
        assertEquals(EnvConfig.SANDBOX_ENDPOINT, loaded.config().tzeroEndpoint());
        assertEquals(8080, loaded.config().port());
    }

    private void expectConfig(Path dir, Map<String, String> environment, String message, String help) {
        EnvConfig.ConfigurationException error = assertThrows(
                EnvConfig.ConfigurationException.class,
                () -> EnvConfig.load(dir, environment));
        assertEquals(message, error.getMessage());
        assertEquals(help, error.getHelpMessage());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Main.writeConfigError(error, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertEquals(
                "ERROR: " + message + System.lineSeparator() + help + System.lineSeparator(),
                printed);
    }

    private Map<String, String> keys() {
        Map<String, String> environment = new HashMap<>();
        environment.put("PROVIDER_PRIVATE_KEY", PRIVATE_KEY);
        environment.put("NETWORK_PUBLIC_KEY", NETWORK_KEY);
        return environment;
    }

    private Path caseDir(String name) throws Exception {
        return Files.createDirectory(root.resolve(name));
    }

    private static String privateKeyHelp(Path dir) {
        String path = dir.resolve(".env").toAbsolutePath().normalize().toString();
        return ".env is read from the working directory, and we looked in " + path
                + ". Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY "
                + "in the environment. Only a project with no .env at all starts one from .env.example "
                + "— an existing .env holds the key generated for you, and its private half is not recoverable.";
    }
}

package network.t0.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the .env file from .env.example with generated keys filled in.
 */
public final class EnvFileWriter {

    private EnvFileWriter() {
        // Utility class
    }

    /**
     * Reads .env.example from the target directory, fills in the generated keypair,
     * and writes the result as .env.
     *
     * @param targetDir the project directory (must already contain .env.example)
     * @param keyPair   the generated keypair
     * @throws IOException if reading or writing fails
     */
    public static void write(Path targetDir, KeyGenerator.KeyPair keyPair) throws IOException {
        Path envExample = targetDir.resolve(".env.example");
        String content = Files.readString(envExample);

        // Fill in the private key
        content = content.replace("PROVIDER_PRIVATE_KEY=", "PROVIDER_PRIVATE_KEY=" + keyPair.privateKeyHex());

        // Fill in the public key comment
        content = content.replace(
            "# your_public_key_here",
            "# 0x" + keyPair.publicKeyHex()
        );

        Files.writeString(targetDir.resolve(".env"), content);
    }
}

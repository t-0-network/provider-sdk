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

        // Add public key as a comment for reference
        content = content.replace(
            "# T-0 Network's public key",
            "# Your provider's public key (share with T-0 team)\n# Public key: 0x" + keyPair.publicKeyHex() + "\n\n# T-0 Network's public key"
        );

        Files.writeString(targetDir.resolve(".env"), content);
    }
}

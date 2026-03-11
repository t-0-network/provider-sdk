package network.t0.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Extracts template files from embedded resources to target directory.
 */
public final class TemplateExtractor {

    private static final String TEMPLATE_RESOURCE_PATH = "/template";
    // These match the real values in the template that get replaced during extraction
    private static final String SDK_VERSION_PLACEHOLDER = ":+\"";  // Matches ":+" at end of dependency version
    private static final String PROJECT_NAME_PLACEHOLDER = "my-provider";
    private static final String SDK_REPOSITORY_PATTERN = "val sdkRepository = \"jitpack\"";
    private static final String SDK_REPOSITORY_MAVEN = "val sdkRepository = \"maven-central\"";

    /**
     * Extracts template files to the target directory.
     *
     * @param targetDir   the directory to extract to
     * @param projectName the project name for placeholder substitution
     * @param repository  the SDK repository ("maven-central" or "jitpack")
     * @throws IOException if extraction fails
     */
    public void extractTo(Path targetDir, String projectName, String repository) throws IOException {
        String sdkVersion = Version.get();

        // Format version for the target repository
        String formattedVersion = sdkVersion;

        URI resourceUri;
        try {
            resourceUri = getClass().getResource(TEMPLATE_RESOURCE_PATH).toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to locate template resources", e);
        }

        // Handle both file system (development) and JAR (production) cases
        if (resourceUri.getScheme().equals("jar")) {
            extractFromJar(resourceUri, targetDir, projectName, formattedVersion, repository);
        } else {
            extractFromFileSystem(Paths.get(resourceUri), targetDir, projectName, formattedVersion, repository);
        }

        // Make gradlew executable
        makeExecutable(targetDir.resolve("gradlew"));
    }

    private void extractFromJar(URI jarUri, Path targetDir, String projectName, String sdkVersion, String repository)
            throws IOException {
        // Get the JAR file system
        String[] parts = jarUri.toString().split("!");
        URI jarFileUri = URI.create(parts[0]);

        try (FileSystem jarFs = FileSystems.newFileSystem(jarFileUri, Collections.emptyMap())) {
            Path templatePath = jarFs.getPath(TEMPLATE_RESOURCE_PATH);
            copyDirectory(templatePath, targetDir, projectName, sdkVersion, repository);
        }
    }

    private void extractFromFileSystem(Path templatePath, Path targetDir, String projectName, String sdkVersion, String repository)
            throws IOException {
        copyDirectory(templatePath, targetDir, projectName, sdkVersion, repository);
    }

    private void copyDirectory(Path source, Path target, String projectName, String sdkVersion, String repository)
            throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path relativePath = source.relativize(sourcePath);
                // Rename dot-gitignore to .gitignore (dotfiles are excluded by Gradle's default copy)
                String relStr = relativePath.toString().replace("dot-gitignore", ".gitignore");
                Path targetPath = target.resolve(relStr);

                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    copyFile(sourcePath, targetPath, projectName, sdkVersion, repository);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy: " + sourcePath, e);
            }
        });
    }

    private void copyFile(Path source, Path target, String projectName, String sdkVersion, String repository)
            throws IOException {
        String fileName = source.getFileName().toString();

        // Create parent directories
        Files.createDirectories(target.getParent());

        // Process text files for placeholder substitution
        if (isTextFile(fileName)) {
            String content = new String(readAllBytes(source));

            // Replace placeholders
            // SDK version: replace ":+" with ":actualVersion" (keeping the quote)
            content = content.replace(SDK_VERSION_PLACEHOLDER, ":" + sdkVersion + "\"");
            content = content.replace(PROJECT_NAME_PLACEHOLDER, projectName);
            if ("maven-central".equals(repository)) {
                content = content.replace(SDK_REPOSITORY_PATTERN, SDK_REPOSITORY_MAVEN);
            }

            Files.writeString(target, content);
        } else {
            // Binary file - copy directly
            try (InputStream is = Files.newInputStream(source)) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private byte[] readAllBytes(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return is.readAllBytes();
        }
    }

    private boolean isTextFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".java")
            || lower.endsWith(".kt")
            || lower.endsWith(".kts")
            || lower.endsWith(".gradle")
            || lower.endsWith(".properties")
            || lower.endsWith(".xml")
            || lower.endsWith(".md")
            || lower.endsWith(".txt")
            || lower.endsWith(".yaml")
            || lower.endsWith(".yml")
            || lower.endsWith(".json")
            || lower.endsWith(".toml")
            || lower.endsWith(".sh")
            || lower.endsWith(".bat")
            || lower.endsWith(".env")
            || lower.equals("gradlew")
            || lower.equals("dockerfile")
            || lower.equals("dot-gitignore");
    }

    private void makeExecutable(Path file) {
        if (!Files.exists(file)) {
            return;
        }

        try {
            // Try POSIX permissions first (Unix-like systems)
            Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE
            );
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            // Windows - executable bit not needed
        } catch (IOException e) {
            // Best effort - ignore permission errors
        }
    }
}

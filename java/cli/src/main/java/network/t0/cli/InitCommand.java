package network.t0.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI tool to initialize a new T-0 Network provider project.
 */
@Command(
    name = "t0-init",
    mixinStandardHelpOptions = true,
    versionProvider = InitCommand.VersionProvider.class,
    description = "Initialize a new T-0 Network provider project"
)
public class InitCommand implements Callable<Integer> {

    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    @Parameters(
        index = "0",
        description = "Project name (will be used as directory name)",
        defaultValue = ""
    )
    private String projectName;

    @Option(
        names = {"-d", "--directory"},
        description = "Target directory (defaults to current directory)"
    )
    private Path directory;

    @Option(
        names = {"--no-color"},
        description = "Disable colored output"
    )
    private boolean noColor;

    @Option(
        names = {"-r", "--repository"},
        description = "SDK repository: jitpack (default) or maven-central"
    )
    private String repository;

    /**
     * CLI entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new InitCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            printHeader();

            // Prompt for project name if not provided
            if (projectName == null || projectName.isEmpty()) {
                projectName = promptForProjectName();
            }

            // Sanitize project name
            projectName = sanitizeProjectName(projectName);
            if (projectName.isEmpty()) {
                printError("Invalid project name. Use only letters, numbers, and hyphens.");
                return 1;
            }

            // Determine repository choice
            String repo;
            if (repository != null) {
                String r = repository.trim().toLowerCase();
                if (r.equals("jitpack") || r.equals("maven-central")) {
                    repo = r;
                } else {
                    printError("Invalid repository '" + repository + "'. Use 'jitpack' or 'maven-central'.");
                    return 1;
                }
            } else {
                repo = promptForRepository();
            }

            // Determine target directory
            Path targetDir = directory != null ? directory.resolve(projectName) : Path.of(projectName);

            // Check if directory exists
            if (Files.exists(targetDir)) {
                printError("Directory '" + targetDir + "' already exists. Please choose a different name.");
                return 1;
            }

            printInfo("Creating project: " + projectName);

            // Create project directory
            Files.createDirectories(targetDir);

            // Extract template files
            printInfo("Extracting template files...");
            TemplateExtractor extractor = new TemplateExtractor();
            extractor.extractTo(targetDir, projectName, repo);
            printSuccess("Template files extracted");

            // Generate keypair
            printInfo("Generating secp256k1 keypair...");
            KeyGenerator.KeyPair keyPair = KeyGenerator.generate();
            printSuccess("Keypair generated");

            // Create .env file
            printInfo("Creating .env file...");
            EnvFileWriter.write(targetDir, keyPair);
            printSuccess("Environment configured");

            // Print success message
            printCompletionMessage(targetDir, keyPair.publicKeyHex(), repo);

            return 0;

        } catch (Exception e) {
            printError("Failed to initialize project: " + e.getMessage());
            if (System.getenv("DEBUG") != null) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private String promptForProjectName() throws IOException {
        System.out.print("Enter your project name: ");
        System.out.flush();
        return readLine();
    }

    private String promptForRepository() throws IOException {
        println("");
        println("Select SDK repository:");
        println("  " + color(BLUE, "1)") + " JitPack (default)");
        println("  " + color(BLUE, "2)") + " Maven Central");
        println("");
        System.out.print("Enter choice [1]: ");
        System.out.flush();

        String input = readLine();
        if (input == null || input.trim().isEmpty() || input.trim().equals("1")) {
            return "jitpack";
        } else if (input.trim().equals("2")) {
            return "maven-central";
        } else {
            printError("Invalid choice. Using JitPack.");
            return "jitpack";
        }
    }

    private java.io.BufferedReader stdinReader;

    private String readLine() throws IOException {
        java.io.Console console = System.console();
        if (console != null) {
            return console.readLine();
        } else {
            if (stdinReader == null) {
                stdinReader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in)
                );
            }
            return stdinReader.readLine();
        }
    }

    private String sanitizeProjectName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9-]", "");
    }

    private void printHeader() {
        println("");
        println(color(BLUE, "+-----------------------------------------------------------+"));
        println(color(BLUE, "|") + "       T-0 Network Provider SDK - Java Initializer        " + color(BLUE, "|"));
        println(color(BLUE, "+-----------------------------------------------------------+"));
        println("");
    }

    private void printCompletionMessage(Path targetDir, String publicKey, String repository) {
        println("");
        println(color(GREEN, "+-----------------------------------------------------------+"));
        println(color(GREEN, "|") + "                  Project Created Successfully!            " + color(GREEN, "|"));
        println(color(GREEN, "+-----------------------------------------------------------+"));
        println("");
        println("Your project is ready at: " + color(BLUE, targetDir.toAbsolutePath().toString()));
        println("");
        println(color(YELLOW, "SDK Repository:") + " " +
            (repository.equals("jitpack") ? "JitPack" : "Maven Central"));
        println("");
        println(color(YELLOW, "Your public key (share with T-0 team):"));
        println(color(BLUE, "0x" + publicKey));
        println("");
        println(color(YELLOW, "Next Steps:"));
        println("");
        println("  1. Navigate to your project:");
        println("     " + color(BLUE, "cd " + targetDir));
        println("");
        println("  2. Run the application:");
        println("     " + color(BLUE, "./gradlew run"));
        println("");
        println("  3. Edit your integration logic:");
        println("     - " + color(BLUE, "src/main/java/network/t0/provider/handler/PaymentHandler.java"));
        println("     - " + color(BLUE, "src/main/java/network/t0/provider/internal/PublishQuotes.java"));
        println("");
        println("For more information, see: " + color(BLUE, "https://github.com/t-0/provider-sdk-java"));
        println("");
    }

    private void printInfo(String message) {
        println(color(BLUE, "[INFO]") + " " + message);
    }

    private void printSuccess(String message) {
        println(color(GREEN, "[SUCCESS]") + " " + message);
    }

    private void printError(String message) {
        println(color(RED, "[ERROR]") + " " + message);
    }

    private void println(String message) {
        System.out.println(message);
    }

    private String color(String colorCode, String text) {
        if (noColor) {
            return text;
        }
        return colorCode + text + RESET;
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Version.get();
            return new String[] { "T-0 Provider Init CLI " + version };
        }
    }
}

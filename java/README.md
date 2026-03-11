# t-0 Network Provider SDK - Java

Java SDK for building provider integrations with the t-0 Network. This SDK provides a gRPC-based framework with automatic cryptographic signing and verification for secure cross-border payment network communication.

## Prerequisites

- **Java** 17 or later
- **Gradle** 8.x (or use the included Gradle wrapper)

## Quick Start

### One-liner Project Setup

Run the following command to create a new t-0 Network provider project:

```bash
curl -fsSL -L https://github.com/t-0-network/provider-java/releases/latest/download/provider-init.jar -o provider-init.jar && java -jar provider-init.jar && rm provider-init.jar
```

This will:
1. Prompt for your project name
2. Prompt for SDK repository (Maven Central or JitPack)
3. Download and set up the project template
4. Generate a secp256k1 keypair for your provider
5. Configure your environment

### CLI Options

The CLI supports several options:

```bash
java -jar provider-init.jar [OPTIONS] [PROJECT_NAME]
```

| Option | Description |
|--------|-------------|
| `-d, --directory` | Target directory (defaults to current directory) |
| `-r, --repository` | SDK repository: `jitpack` (default) or `maven-central` |
| `--no-color` | Disable colored output |
| `-h, --help` | Show help message |
| `-V, --version` | Show version information |

### Repository Selection

When running the CLI, you'll be prompted to choose the SDK repository:

```
Select SDK repository:
  1) JitPack (default)
  2) Maven Central

Enter choice [1]:
```

| Option | When to Use |
|--------|-------------|
| **JitPack** (default) | Recommended — fast publication |
| **Maven Central** | Alternative — publication can be slow |

**SDK Dependency Coordinates:**

| Repository | Artifact ID |
|------------|-------------|
| JitPack | `com.github.t-0-network:provider-java:<version>` |
| Maven Central | `network.t-0:provider-sdk-java:<version>` |

### Manual Setup (Alternative)

If you prefer manual setup:

```bash
# Clone the repository
git clone https://github.com/t-0-network/provider-sdk.git
cd provider-sdk/java/starter/template

# Copy to your project directory
cp -r . /path/to/your-project
cd /path/to/your-project

# Set up environment
cp .env.example .env

# Generate keypair
./gradlew generateKeys

# Add the generated private key to .env, then run
./gradlew run
```

## Environment Variables

Configure your `.env` file with the following variables:

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `PROVIDER_PRIVATE_KEY` | Your secp256k1 private key (64-char hex) | Yes | - |
| `NETWORK_PUBLIC_KEY` | t-0 Network's public key | Yes | Pre-configured |
| `TZERO_ENDPOINT` | t-0 Network API endpoint | No | `https://api-sandbox.t-0.network` |
| `PORT` | Provider server port | No | `8080` |
| `QUOTE_PUBLISHING_INTERVAL` | Quote publishing interval in milliseconds | No | `5000` |

## Integration Steps

Follow these steps to complete your integration:

### Phase 1: Initial Setup

- [ ] **Step 1.1** - Initialize your project using the quick start above
- [ ] **Step 1.2** - Share your public key with the t-0 team (displayed on first run)
- [ ] **Step 1.3** - Replace sample quote publishing logic in `PublishQuotes.java`
- [ ] **Step 1.4** - Verify quotes are received by checking application logs

### Phase 2: Payment Handling

- [ ] **Step 2.1** - Implement `updatePayment` handler in `PaymentHandler.java`
- [ ] **Step 2.2** - Deploy your integration and share the base URL with the t-0 team
- [ ] **Step 2.3** - Test payment submission using the included `SubmitPayment` utility
- [ ] **Step 2.4** - Implement `payOut` handler in `PaymentHandler.java`
- [ ] **Step 2.5** - Request a test payment from the t-0 team

## Project Structure

After initialization, your project will have the following structure:

```
your-project/
├── src/main/java/network/t0/provider/
│   ├── Main.java                    # Entry point
│   ├── Config.java                  # Configuration
│   ├── handler/
│   │   └── PaymentHandler.java      # ProviderService implementation (modify this)
│   └── internal/
│       ├── PublishQuotes.java       # Quote publishing logic (modify this)
│       ├── GetQuote.java            # Quote fetching utility
│       └── SubmitPayment.java       # Payment submission utility
├── build.gradle.kts                 # Build configuration (SDK from Maven Central)
├── .env                             # Your configuration (git-ignored)
└── Dockerfile                       # Docker deployment
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `PaymentHandler.java` | Implement your payment processing logic. Look for `TODO` comments. |
| `PublishQuotes.java` | Replace sample quotes with your FX rate source. |

## Available Commands

```bash
# Run the application
./gradlew run

# Generate a new keypair
./gradlew generateKeys

# Build the project
./gradlew build

# Run tests
./gradlew test
```

## Deployment

### Docker

Build and run with Docker:

```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

### Production Checklist

1. Generate a **new keypair** for production (never reuse development keys)
2. Set environment variables on your hosting platform
3. Share your production public key and base URL with the t-0 team
4. Ensure your server is accessible from the t-0 Network

## Security Considerations

- **Never commit `.env`** - It's included in the generated `.gitignore`
- **Keep your private key secure** - The `PROVIDER_PRIVATE_KEY` must remain confidential
- **Share only your public key** - Only the public key should be shared with the t-0 team
- **Use separate keys per environment** - Different keys for development, staging, and production

## Further Reading

- [SDK Technical Documentation](sdk/README.md) - Architecture, internals, and advanced usage
- [t-0 Network Documentation](https://t-0.network/docs) - Network integration guides

## Support

For issues or questions:
- Review the `TODO` comments in the generated code
- Check the [SDK documentation](sdk/README.md) for technical details
- Contact the t-0 team for integration support

---

## Maintainer Guide

This section is for SDK maintainers responsible for releases and infrastructure.

### Repository Structure

```
provider-sdk-java/
├── sdk/                    # Core SDK library (published to Maven Central)
├── cli/                    # Init CLI tool (published as GitHub Release asset)
├── starter/
│   ├── template/           # Template for new projects (embedded in CLI)
│   └── build.gradle.kts    # Starter module build
├── docs/
│   └── starter-architecture.md  # How the starter system works
├── gradle.properties       # Version management
└── build.gradle.kts        # Root build config
```

### Documentation

- **[GitHub Setup Guide](docs/github-setup.md)** - Complete CI/CD setup, secrets, and publishing configuration
- **[Starter Architecture](docs/starter-architecture.md)** - How the initialization system works
- **[SDK Technical Docs](sdk/README.md)** - SDK internals and architecture

### Version Management

Version is managed centrally in `gradle.properties`:

```properties
version=1.0.0-SNAPSHOT
group=network.t0
```

Both SDK and CLI artifacts use this version.

### Building

```bash
# Build everything
./gradlew build

# Build SDK only
./gradlew :sdk:build

# Build CLI (shadow JAR)
./gradlew :cli:shadowJar

# Verify template compiles
cd starter/template && ./gradlew build
```

### Testing the Init Flow

```bash
# Build CLI
./gradlew :cli:shadowJar

# Test locally
java -jar cli/build/libs/provider-init-*.jar my-test-project
# Verify generated project
cd my-test-project && ./gradlew build
```

### Publishing

| Artifact | Channel | Trigger |
|----------|---------|---------|
| **SDK** (`network.t-0:provider-sdk-java`) | Maven Central | Git tag push |
| **CLI** (`provider-init.jar`) | GitHub Releases | Git tag push |
| **SDK** (alternative) | JitPack | Automatic on-demand |

For complete setup instructions, see **[GitHub Setup Guide](docs/github-setup.md)**.

#### Quick Release Process

1. Run the **Release** workflow from GitHub Actions (manual dispatch)
2. Select version bump type (patch/minor/major)
3. The workflow will:
   - Calculate and set the release version
   - Build, commit, tag, and create GitHub Release
   - Update to next SNAPSHOT version
4. The **Publish** workflow (triggered by tag) will:
   - Publish SDK to Maven Central
   - Upload `provider-init.jar` to the GitHub Release

5. Verify on Maven Central (10-30 minutes): https://repo1.maven.org/maven2/network/t-0/

#### Required GitHub Secrets

| Secret | Purpose |
|--------|---------|
| `OSSRH_USERNAME` | Maven Central authentication |
| `OSSRH_PASSWORD` | Maven Central authentication |
| `GPG_PRIVATE_KEY` | Artifact signing |
| `CI_APP_PRIVATE_KEY` | Release workflow automation |

| Variable | Purpose |
|----------|---------|
| `CI_APP_ID` | GitHub App ID for releases |

See **[GitHub Setup Guide](docs/github-setup.md)** for detailed setup instructions.

### JitPack (Alternative Repository)

JitPack builds automatically from GitHub - no configuration needed beyond `jitpack.yml`.

**Using JitPack directly:**

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.t-0-network:provider-java:1.0.33")
}
```

**Using the CLI with JitPack:**

Select option 1 (default) when prompted for repository:

```
Select SDK repository:
  1) JitPack (default)
  2) Maven Central

Enter choice [1]: 1
```

**Build status:** https://jitpack.io/#t-0-network/provider-java

### Troubleshooting

See **[GitHub Setup Guide](docs/github-setup.md#troubleshooting)** for common issues and solutions

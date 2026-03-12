# T-0 Provider SDK -- Java

Java SDK and starter CLI for building T-0 Network payment provider integrations. The SDK provides a gRPC-based framework with automatic secp256k1 cryptographic signing and verification for secure cross-border payment network communication.

## Prerequisites

- **Java** 17 or later
- **Gradle** 8.x (or use the included Gradle wrapper)

## Quick Start

```bash
curl -fsSL -L https://github.com/t-0-network/provider-sdk/releases/latest/download/provider-init.jar -o provider-init.jar && java -jar provider-init.jar && rm provider-init.jar
```

This will prompt for your project name and SDK repository, then create a ready-to-run project with a secp256k1 keypair, environment config, provider service stubs, and a Dockerfile.

### CLI Options

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

| Option | When to Use |
|--------|-------------|
| **JitPack** (default) | Recommended -- fast publication |
| **Maven Central** | Alternative -- publication can be slow |

## Generated Project Structure

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
├── build.gradle.kts                 # Build configuration
├── .env                             # Your configuration (git-ignored)
└── Dockerfile                       # Docker deployment
```

## Key Files to Modify

| File | Purpose |
|------|---------|
| `PaymentHandler.java` | Implement your payment processing logic. Look for `TODO` comments. |
| `PublishQuotes.java` | Replace sample quotes with your FX rate source. |

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PROVIDER_PRIVATE_KEY` | Yes | Auto-generated | Your secp256k1 private key (64-char hex) |
| `NETWORK_PUBLIC_KEY` | Yes | Sandbox key | T-0 Network public key for signature verification |
| `TZERO_ENDPOINT` | No | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
| `PORT` | No | `8080` | Provider server port |
| `QUOTE_PUBLISHING_INTERVAL` | No | `5000` | Quote publishing interval in milliseconds |

## Getting Started

### Phase 1: Quoting

1. Initialize your project using the quick start above.
2. Share your public key with the T-0 team (displayed on first run).
3. Replace sample quote publishing logic in `PublishQuotes.java`.
4. Start the application: `./gradlew run`
5. Verify quotes are received by checking application logs.

### Phase 2: Payments

1. Implement `updatePayment` handler in `PaymentHandler.java`.
2. Deploy your service and share the base URL with the T-0 team.
3. Implement `payOut` handler in `PaymentHandler.java`.
4. Test payment submission using the included `SubmitPayment` utility.
5. Coordinate with the T-0 team to test end-to-end payment flows.

## Installation

To use the SDK directly without the starter CLI, add the dependency to your `build.gradle.kts`:

**JitPack** (recommended):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.t-0-network:provider-sdk:<version>")
}
```

**Maven Central:**

```kotlin
dependencies {
    implementation("network.t-0:provider-sdk-java:<version>")
}
```

## Available Commands

```bash
./gradlew run              # Run the application
./gradlew build            # Build the project
./gradlew test             # Run tests
./gradlew generateKeys     # Generate a new keypair
```

## Deployment

```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

## Troubleshooting

For common issues and solutions, see the [GitHub Setup Guide](../docs/java/GITHUB_SETUP.md).

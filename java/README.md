# T-0 Provider SDK -- Java

Java SDK for building T-0 Network payment provider integrations. The SDK provides a gRPC-based framework with automatic secp256k1 cryptographic signing and verification for secure cross-border payment network communication.

## Prerequisites

- **Java** 17 or later
- **Gradle** 8.x (or use the included Gradle wrapper)

## Quick Start

Scaffold a new provider project:

```bash
t0-init init --lang=java my-provider
```

`--repository maven-central` selects Maven Central; JitPack is the default. Installation and options: [cli/README.md](../cli/README.md). What `init` creates: [starter template README](starter/template/README.md).

## Installation

To use the SDK directly, add the dependency to your `build.gradle.kts`:

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
```

To generate a new keypair, run `t0-init keygen` and set `PROVIDER_PRIVATE_KEY` to the private key it prints (see [`cli/README.md`](../cli/README.md)).

## Deployment

```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

## Troubleshooting

For common issues and solutions, see the [GitHub Setup Guide](../docs/java/GITHUB_SETUP.md).

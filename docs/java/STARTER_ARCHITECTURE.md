# Starter System Architecture

This document describes how the project initialization system works for the T-0 Provider SDK.

## Overview

The starter system provides a one-liner experience for developers to bootstrap new provider projects:

```bash
curl -fsSL -L https://github.com/t-0-network/provider-java/releases/latest/download/provider-init.jar -o provider-init.jar && java -jar provider-init.jar && rm provider-init.jar
```

## Components

### 1. CLI Module (`cli/`)

A self-contained Java CLI tool distributed as a GitHub Release asset (`provider-init.jar`).

**Structure:**
```
cli/
├── build.gradle.kts           # Shadow JAR + publishing config
└── src/main/java/network/t0/cli/
    ├── InitCommand.java       # Main entry point (picocli)
    ├── TemplateExtractor.java # Extracts template from JAR resources
    ├── KeyGenerator.java      # Generates secp256k1 keypair
    ├── EnvFileWriter.java     # Creates .env file
    └── Version.java           # Reads embedded version
```

**Key Features:**
- **Shadow JAR**: All dependencies bundled (SDK, BouncyCastle, picocli)
- **Embedded Template**: Template files are packaged as JAR resources
- **Placeholder Substitution**: `${PROJECT_NAME}` and `${SDK_VERSION}` replaced during extraction

### 2. Template Source (`starter/template/`)

The template project that gets extracted for new providers. Contains:

- Complete Gradle project structure
- Sample handler implementations with TODO markers
- Configuration files (build.gradle.kts, Dockerfile, etc.)

**Important:** This directory is kept in the repo for:
1. CI verification (ensures template compiles)
2. Development/testing of template changes
3. Documentation reference

### 3. Starter Build (`starter/build.gradle.kts`)

A Gradle build file for the starter module that provides:
- `generateKeys` task for local development
- Compilation checks for the template

## Build Process

### Template Packaging

During CLI build, template files are copied to resources:

```kotlin
// cli/build.gradle.kts
tasks.register<Sync>("copyTemplateResources") {
    from("../starter/template")
    into(layout.buildDirectory.dir("resources/main/template"))
    exclude(".gradle", "build", ".env", "libs/")
}
```

### Version Embedding

The SDK version is embedded in `version.properties`:

```kotlin
tasks.register("generateVersionProperties") {
    doLast {
        File("version.properties").writeText("version=${project.version}")
    }
}
```

### Shadow JAR

The CLI is packaged as a fat JAR with all dependencies:

```kotlin
tasks.shadowJar {
    archiveBaseName.set("provider-init")
    minimize {
        exclude(dependency("org.bouncycastle:.*:.*"))
    }
}
```

## Initialization Flow

```
┌─────────────────────────────────────────────────────────────────┐
│          User downloads and runs provider-init.jar              │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    InitCommand.java (CLI)                        │
│  1. Prompt for project name (if not provided)                   │
│  2. Validate and sanitize project name                          │
│  3. Create project directory                                    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                 TemplateExtractor.java                           │
│  1. Read template files from JAR resources (/template/*)        │
│  2. Walk resource tree                                          │
│  3. Copy files, replacing placeholders:                         │
│     - ${PROJECT_NAME} → user's project name                     │
│     - ${SDK_VERSION}  → embedded SDK version                    │
│  4. Make gradlew executable                                     │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   KeyGenerator.java                              │
│  1. Generate 32 random bytes (SecureRandom)                     │
│  2. Create Signer from bytes (SDK)                              │
│  3. Derive public key                                           │
│  4. Return hex-encoded keypair                                  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   EnvFileWriter.java                             │
│  1. Create .env file with:                                      │
│     - PROVIDER_PRIVATE_KEY (generated)                          │
│     - NETWORK_PUBLIC_KEY (empty, user fills)                    │
│     - TZERO_ENDPOINT (default sandbox)                          │
│     - PORT, QUOTE_PUBLISHING_INTERVAL (defaults)                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Print Success Message                          │
│  - Show project path                                            │
│  - Display public key (for sharing with T-0 team)               │
│  - Show next steps                                              │
└─────────────────────────────────────────────────────────────────┘
```

## Placeholder Substitution

Template files with these extensions are processed for placeholders:
- `.java`, `.kt`, `.kts`, `.gradle`
- `.properties`, `.xml`, `.md`, `.txt`
- `.yaml`, `.yml`, `.json`, `.toml`
- `.sh`, `.bat`, `.env`
- `gradlew`, `Dockerfile`, `.gitignore`

**Placeholders:**

| Placeholder | Replaced With | Example |
|-------------|--------------|---------|
| `${PROJECT_NAME}` | User's project name | `my-provider` |
| `${SDK_VERSION}` | Current SDK version | `1.0.0` |

## Generated Project

The generated project uses the SDK from JitPack (default) or Maven Central:

```kotlin
// build.gradle.kts (generated) — JitPack example
dependencies {
    implementation("com.github.t-0-network:provider-java:1.0.33")
}

// Maven Central example
dependencies {
    implementation("network.t-0:provider-sdk-java:1.0.33")
}
```

The `sdkRepository` variable in `build.gradle.kts` controls which repository is used. The CLI sets this based on the user's choice (`-r` flag or interactive prompt).

This means:
- No local `libs/sdk.jar` in generated projects
- Users get SDK updates by changing the version number
- Transitive dependencies are resolved automatically

## CI Verification

The CI pipeline verifies the entire flow:

```yaml
jobs:
  build:
    steps:
      # 1. Build SDK
      - run: ./gradlew :sdk:build

      # 2. Verify template compiles (catches syntax errors)
      - run: cd starter/template && ./gradlew build

      # 3. Build CLI
      - run: ./gradlew :cli:shadowJar

      # 4. E2E test: generate project and compile it
      - run: |
          java -jar cli/build/libs/provider-init.jar test-project
          cd test-project
          ./gradlew compileJava
```

## Development Workflow

### Modifying the Template

1. Edit files in `starter/template/`
2. Test locally:
   ```bash
   ./gradlew :cli:shadowJar
   java -jar cli/build/libs/provider-init-*.jar test-project
   cd test-project && ./gradlew build
   ```
3. Commit changes

### Adding New Placeholders

1. Add placeholder in template file: `${NEW_PLACEHOLDER}`
2. Update `TemplateExtractor.java` to replace it
3. Document in this file

### Testing Changes

```bash
# Quick test
./gradlew :cli:shadowJar && \
  rm -rf /tmp/test-project && \
  java -jar cli/build/libs/provider-init-*.jar /tmp/test-project && \
  cd /tmp/test-project && ./gradlew build
```

## Why Java Has Separate `cli/` and `starter/` Directories

Go and Node SDKs each have a single `starter/` directory that serves as both CLI tool and template source. Java requires a different structure due to distribution constraints.

### Cross-SDK Comparison

| | Go | Node | Java |
|---|---|---|---|
| **Run command** | `go run ...@latest` | `npx @t-0/provider-starter-ts` | `curl ... provider-init.jar && java -jar` |
| **Distribution** | Source (fetched on demand) | npm package | Self-contained fat JAR (GitHub Release) |
| **Template access** | Filesystem copy from module cache | Filesystem copy from npm package | Extracted from embedded JAR resources |
| **Directory layout** | Single `starter/` | Single `starter/` | Separate `cli/` + `starter/template/` |

### Why the Separation Is Necessary

1. **No source-level execution.** Go has `go run` and Node has `npx` — both fetch and execute source code directly. Java has no equivalent; the CLI must be a pre-built JAR artifact.

2. **Shadow JAR requires its own module.** The fat JAR bundles all dependencies (SDK, BouncyCastle, picocli) via the Shadow Gradle plugin. This needs a dedicated `cli/` module with its own `build.gradle.kts` to configure the JAR build, resource embedding, and dependency minimization.

3. **Template must be embedded as resources.** Since the JAR is downloaded standalone (not from a package manager), template files must be packaged inside it. The `copyTemplateResources` build task copies `starter/template/` into `cli/build/resources/main/template/`, and `TemplateExtractor` reads them at runtime.

4. **SDK dependency for key generation.** The CLI depends on `:sdk` to use `Signer` for secp256k1 keypair generation. This is a compile-time module dependency, not just a template reference.

5. **Dual-mode template.** `starter/template/build.gradle.kts` detects whether it's running as a subproject (`rootProject.name == "provider-sdk-java"`) or standalone, switching between `project(":sdk")` and the published Maven/JitPack artifact accordingly. This allows CI to verify the template compiles without publishing the SDK first.

## Publishing

See [Maintainer Guide](../README.md#maintainer-guide) for release instructions.

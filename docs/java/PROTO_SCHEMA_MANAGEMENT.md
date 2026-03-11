# Proto Schema Management

This document explains how Protocol Buffer (protobuf) code generation works in the T-0 Provider SDK.

## Overview

The SDK uses protobuf to define the gRPC API contract between providers and the T-0 Network. Java code is **automatically generated** from `.proto` files on every build - no manual steps required.

## Proto Source Files

Location: `sdk/src/main/proto/`

```
sdk/src/main/proto/
├── tzero/v1/
│   ├── common/
│   │   ├── common.proto           # Shared types (Amount, Currency, etc.)
│   │   ├── payment_method.proto   # Payment method definitions
│   │   └── payment_receipt.proto  # Receipt structures
│   └── payment/
│       ├── network.proto          # NetworkService (client → T-0 Network)
│       └── provider.proto         # ProviderService (T-0 Network → provider)
└── ivms101/v1/
    └── ivms/
        ├── enum.proto             # IVMS101 enumerations
        └── ivms101.proto          # IVMS101 compliance data structures
```

### Key Proto Files

| File | Purpose |
|------|---------|
| `provider.proto` | Defines the ProviderService that providers implement |
| `network.proto` | Defines the NetworkService that providers call |
| `common.proto` | Shared message types used across services |
| `ivms101.proto` | Travel Rule compliance data structures |

## Generated Code

Location: `sdk/build/generated/sources/proto/main/`

```
sdk/build/generated/sources/proto/main/
├── java/           # Protobuf message classes
│   ├── network/t0/api/tzero/v1/...
│   └── network/t0/api/ivms101/v1/...
└── grpc/           # gRPC service stubs
    └── network/t0/api/tzero/v1/payment/
        ├── NetworkServiceGrpc.java
        └── ProviderServiceGrpc.java
```

**Note:** Generated code is NOT committed to git. It's regenerated on each build.

## Build Integration

Code generation happens automatically as part of the Gradle build:

```
./gradlew :sdk:build

# Task execution order:
:sdk:extractIncludeProto    # Extract proto dependencies
:sdk:extractProto           # Extract proto sources
:sdk:generateProto          # Generate Java code ← code generation
:sdk:compileJava            # Compile generated + hand-written code
```

The `generateProto` task is a dependency of `compileJava`, so you never need to run it manually.

### Gradle Configuration

From `sdk/build.gradle.kts`:

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.6"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.33.4"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.78.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}
```

## Updating Proto Schemas

### Adding/Modifying Messages

1. **Edit the proto file**:
   ```protobuf
   // sdk/src/main/proto/tzero/v1/common/common.proto
   message NewMessageType {
       string field1 = 1;
       int64 field2 = 2;
   }
   ```

2. **Build to regenerate code**:
   ```bash
   ./gradlew :sdk:build
   ```

3. **Use the new type in SDK code**:
   ```java
   import network.t0.api.tzero.v1.common.NewMessageType;

   NewMessageType msg = NewMessageType.newBuilder()
       .setField1("value")
       .setField2(123)
       .build();
   ```

4. **Run tests**:
   ```bash
   ./gradlew :sdk:test
   ```

### Adding New Proto Files

1. Create the file in the appropriate directory under `sdk/src/main/proto/`
2. Add proper package and imports
3. Build - the file will be automatically discovered

### Removing Proto Files

1. Delete the `.proto` file
2. Run `./gradlew clean :sdk:build` to remove stale generated code
3. Update SDK code to remove references to deleted types

## Proto Style Guide

Follow these conventions for consistency:

```protobuf
syntax = "proto3";

package tzero.v1.common;

option java_multiple_files = true;
option java_package = "network.t0.api.tzero.v1.common";

// Use PascalCase for message names
message PaymentRequest {
    // Use snake_case for field names
    string payment_id = 1;
    int64 amount_minor_units = 2;

    // Document non-obvious fields
    // Timestamp in milliseconds since Unix epoch
    int64 created_at = 3;
}

// Use PascalCase for enum names, SCREAMING_SNAKE_CASE for values
enum PaymentStatus {
    PAYMENT_STATUS_UNSPECIFIED = 0;
    PAYMENT_STATUS_PENDING = 1;
    PAYMENT_STATUS_COMPLETED = 2;
}
```

## Troubleshooting

### Generated Code Not Updating

```bash
# Clean and rebuild
./gradlew clean :sdk:build
```

### Import Errors in Generated Code

Check that all referenced proto files exist and have correct package declarations.

### IDE Not Recognizing Generated Code

Mark the generated sources directory as a source root:
- IntelliJ: Right-click `sdk/build/generated/sources/proto/main/java` → Mark Directory as → Generated Sources Root

Or let Gradle configure it:
```bash
./gradlew :sdk:idea  # For IntelliJ
```

### Proto Compilation Errors

```bash
# See detailed error output
./gradlew :sdk:generateProto --info
```

Common issues:
- Missing imports
- Circular dependencies
- Invalid field numbers (must be unique within message)
- Reserved field numbers being reused

## Useful Commands

```bash
# Generate proto code only (without full build)
./gradlew :sdk:generateProto

# Clean generated code
./gradlew :sdk:clean

# List all proto-related tasks
./gradlew :sdk:tasks --all | grep -i proto

# View generated code location
ls sdk/build/generated/sources/proto/main/
```

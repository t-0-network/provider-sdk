# my-provider

T-0 Network provider implementation generated from the official Java starter.

## Prerequisites

- Java 21 or later
- Gradle (or use the Gradle wrapper)

## Quick Start

1. **Review `.env`.** `t0-init` creates `.env` from `.env.example` and writes a new private key into it. The other values in that file are for the sandbox.

   | Variable | In `.env` after `t0-init` | If unset or empty | Description |
   |----------|---------------------------|-------------------|-------------|
   | `PROVIDER_PRIVATE_KEY` | A new key generated for this project | The provider does not start | Your secp256k1 private key (hex) |
   | `NETWORK_PUBLIC_KEY` | The sandbox network key | The provider does not start | T-0 Network public key for signature verification |
   | `TZERO_ENDPOINT` | `https://api-sandbox.t-0.network` | `https://api-sandbox.t-0.network` | T-0 Network API endpoint |
   | `PORT` | `8080` | `8080` | Server port |
   | `QUOTE_PUBLISHING_INTERVAL` | not set | `5000` | Quote publishing interval in milliseconds |

   The process reads `.env` from the working directory. A variable set in the process environment wins over `.env`, even when its value is empty, and an empty value counts as unset. For example, if `.env` has `PORT=9000` and the environment has `PORT=`, the server listens on 8080. To use the value from `.env`, unset the variable. If `.env` is missing, the process prints one notice on stderr and continues with the process environment.

   For production, set both `NETWORK_PUBLIC_KEY` and `TZERO_ENDPOINT` to the values the T-0 team gives you. If `TZERO_ENDPOINT` is unset, the provider connects to the sandbox. `.env` holds your private key, so keep it out of Git. Use a separate private key in production.

   If a key is missing, or `PORT` is not a port number, the process prints `ERROR: <message>` and one help line on stderr and exits with status 1. Any other startup failure, for example a malformed key or a port already in use, prints `Provider failed to start: <cause>` and exits with status 1. After startup, a failed call to the network is logged as one line, and the provider keeps running. On SIGINT or SIGTERM, the provider stops publishing quotes and waits for the calls in progress. Then it exits with status 0.

   The provider serves gRPC.

2. **Run the application:**
   ```bash
   ./gradlew run
   ```

## Integration Steps

Follow these steps in order to complete your integration:

### Step 1: Initial Setup

1. **Step 1.1** ✅ Initialize the starter template (done by the initializer that created this project and wrote `.env`)
2. **Step 1.2** Share your public key with the T-0 team (displayed on startup)
3. **Step 1.3** Replace the sample quote publishing logic in `PublishQuotes.java`
4. **Step 1.4** Verify quotes are received (check logs when running)

### Step 2: Implement Payment Handling

1. **Step 2.1** Implement `updatePayment` in `PaymentHandler.java`
2. **Step 2.2** Deploy and share your base URL with the T-0 team
3. **Step 2.3** Test payment submission: call `SubmitPayment.submit(...)` from `Main.java` (see `internal/SubmitPayment.java`)
4. **Step 2.4** Implement `payOut` in `PaymentHandler.java`
5. **Step 2.5** Ask T-0 team to submit a test payment
6. **Step 2.6** *(optional)* If your `payOut` responds with `manual_aml_check`, report the outcome via `internal/CompleteManualAmlCheck.java`

### Step 3: Payment Intent Flow

The payment intent flow is independent of Step 2. It is an asynchronous pay-in flow where an end-user pays a pay-in provider in fiat (bank transfer, mobile money, etc.) and a beneficiary provider receives settlement on the crypto side. Quotes are indicative until funds are received, settlement happens periodically, and a confirmation code links the end-user's payment back to a specific payment intent.

Implement **one** of the two sub-phases below depending on your role. If you participate on both sides, implement both.

**Phase 3A -- Pay-In Provider role** (skip if you're a beneficiary):

1. **Step 3A.1** Replace the sample pay-in quote publishing in `internal/PublishPaymentIntentQuotes.java` with your own.
2. **Step 3A.2** Implement `getPaymentDetails` in `handler/PaymentIntentPayInHandler.java` -- return bank account / mobile money details plus a payment reference the end-user will include in their transfer.
3. **Step 3A.3** When you detect the end-user's fiat payment, call `confirmFundsReceived` (see `internal/ConfirmFundsReceived.java`).

**Phase 3B -- Beneficiary Provider role** (skip if you're pay-in):

1. **Step 3B.1** Verify indicative quotes are returned (`internal/GetPaymentIntentQuote.java`).
2. **Step 3B.2** Create payment intents for your end-users via `CreatePaymentIntent.create(...)` (see `internal/CreatePaymentIntent.java`).
3. **Step 3B.3** Implement `paymentIntentUpdate` in `handler/PaymentIntentBeneficiaryHandler.java` to receive notifications when funds are received.

If you only play one role, delete the files for the other role. In `Main.java`, also remove that role's handler and its `.withService(...)` call, and that role's quote publishing and sample calls.

## Project Structure

```
src/main/java/network/t0/provider/
├── Main.java                                # Entry point with integration steps
├── Config.java                              # Configuration record
├── EnvConfig.java                           # Reads .env and the environment into Config
├── handler/
│   ├── PaymentHandler.java                  # Phase 2: ProviderService handler
│   ├── PaymentIntentPayInHandler.java       # Phase 3A: PayInProviderService handler
│   └── PaymentIntentBeneficiaryHandler.java # Phase 3B: BeneficiaryService handler
└── internal/
    ├── PublishQuotes.java                   # Phase 1: payout quote publishing
    ├── GetQuote.java                        # Phase 1: quote retrieval
    ├── SubmitPayment.java                   # Phase 2: payment submission
    ├── CompleteManualAmlCheck.java          # Phase 2: manual AML check completion (optional)
    ├── PublishPaymentIntentQuotes.java      # Phase 3A: pay-in quote publishing
    ├── GetPaymentIntentQuote.java           # Phase 3B: indicative quote retrieval
    ├── CreatePaymentIntent.java             # Phase 3B: create a payment intent
    └── ConfirmFundsReceived.java            # Phase 3A: confirm funds received
```

## Key Classes

### PaymentHandler

Implements the `ProviderService` gRPC interface:
- `updatePayment` - Receive updates for payments you initiated
- `payOut` - Process payout requests from counterparts
- `updateLimit` - Receive limit/credit updates
- `appendLedgerEntries` - Receive ledger transaction notifications
- `approvePaymentQuotes` - "Last Look" approval for quotes

### PublishQuotes

Publishes FX quotes to the network. Customize this to:
- Fetch rates from your pricing system
- Publish quotes for your supported currencies
- Adjust bands based on your liquidity

## Deployment

Build a Docker image:
```bash
docker build -t my-provider .
docker run -p 8080:8080 --env-file .env my-provider
```

Or build the application distribution (what the Dockerfile does):
```bash
./gradlew installDist
build/install/provider/bin/provider
```
The launcher reads `.env` from the current working directory.

## Configuring logging

The SDK uses SLF4J. By default it logs through the logger named
`network.t0.sdk.provider.ResponseValidationInterceptor`. The starter `Main.java`
overrides this by calling `.withLogger(LoggerFactory.getLogger("provider"))` on
`ProviderServer.Builder` — swap the logger argument for your own logging
configuration.

### What gets logged

The SDK writes a single `ERROR`-level event when one of its safety-net
interceptors triggers:

- **Response validation failure** — your handler returned a message that fails
  protovalidate. The event carries structured fields: `rpc_method`,
  `response_type`, `violations`, `sdk_version`. The wire response is still
  `Status.INTERNAL` with description `response validation failed: <details>`,
  so this log line is the only signal you get during development. Wrap your
  responses with `Validate.check(...)` (see `PaymentHandler.java`) to surface
  the failure in your own call frame instead.
- **Signature verification failure** — incoming requests with an invalid
  signature are rejected with `Status.UNAUTHENTICATED`. These currently log
  through `network.t0.sdk.provider.SignatureVerificationInterceptor` (a
  separate SLF4J logger name). Future SDK versions will route them through
  the same logger you pass via `withLogger(...)`, so it is worth configuring
  both logger names today if you want consistent routing.

### Routing logs to a file with JSON encoding (logback)

If you use logback, drop the following into `src/main/resources/logback.xml`
to send the SDK's logger to stdout in JSON, and your application's logs to
stderr in plain text:

```xml
<configuration>
    <appender name="STDOUT_JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.JsonEncoder"/>
    </appender>

    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- SDK safety-net log line (the logger name passed to withLogger above) -->
    <logger name="provider" level="ERROR" additivity="false">
        <appender-ref ref="STDOUT_JSON"/>
    </logger>

    <root level="INFO">
        <appender-ref ref="STDERR"/>
    </root>
</configuration>
```

The JSON encoder picks up the SLF4J `KeyValuePair`s emitted by the SDK
(`rpc_method`, `response_type`, `violations`, `sdk_version`) as top-level
fields, so they are directly indexable by your log aggregator.

The shipped `src/main/resources/logback.xml` writes plain text to stderr. The `network.t0` logger is at INFO, and `io.grpc` is at WARN. grpc-java logs with `java.util.logging`, so `Main` installs `SLF4JBridgeHandler` and the `io.grpc` level in that file applies.

## Testing

Run unit tests:
```bash
./gradlew test
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `ERROR: PROVIDER_PRIVATE_KEY is not set` | Run from the directory that holds your `.env`, or set `PROVIDER_PRIVATE_KEY` in the process environment. An existing `.env` holds the key generated for this project — do not overwrite its private half. |
| `ERROR: NETWORK_PUBLIC_KEY is not set` | Ask the t-0 team for the network public key of the environment you target and put it in `.env` (or set `NETWORK_PUBLIC_KEY`). |
| Port already in use | Free the port, or set `PORT` to another one. The process environment wins over `.env`, so change it where it is set. |
| `timestamp is outside the allowed time window` | The request signature timestamp is more than 60 seconds away from this server's clock. Sync both clocks with NTP (the allowed skew is ±60 seconds). The provider refuses the call with `INVALID_ARGUMENT`, the same in every SDK. |

## Support

Contact the T-0 team for:
- Sandbox credentials
- Integration support

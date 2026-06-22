# T-0 Network Provider Starter - Java

This is a starter template for building a provider integration with the T-0 Network.

## Prerequisites

- Java 17 or later
- Gradle (or use the Gradle wrapper)

## Quick Start

1. **Configure your environment:**
   ```bash
   cp .env.example .env
   ```

2. **Update `.env` with your configuration:**
   - `PROVIDER_PRIVATE_KEY` - Your provider's private key (generated during init)
   - `TZERO_ENDPOINT` - API endpoint (default: sandbox)
   - `PORT` - Port for your provider server

3. **Run the application:**
   ```bash
   ./gradlew run
   ```

## Integration Steps

Follow these steps in order to complete your integration:

### Step 1: Initial Setup

1. **Step 1.1** ✅ Initialize the starter template (done when you run the app)
2. **Step 1.2** Share your public key with the T-0 team (displayed on startup)
3. **Step 1.3** Replace the sample quote publishing logic in `PublishQuotes.java`
4. **Step 1.4** Verify quotes are received (check logs when running)

### Step 2: Implement Payment Handling

1. **Step 2.1** Implement `updatePayment` in `PaymentHandler.java`
2. **Step 2.2** Deploy and share your base URL with the T-0 team
3. **Step 2.3** Test payment submission (uncomment in `Main.java`)
4. **Step 2.4** Implement `payOut` in `PaymentHandler.java`
5. **Step 2.5** Ask T-0 team to submit a test payment

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

If you only play one role, delete the files for the other role and remove the corresponding `.withService(...)` call in `Main.java`.

## Project Structure

```
src/main/java/network/t0/provider/
├── Main.java                                # Entry point with integration steps
├── Config.java                              # Configuration record
├── handler/
│   ├── PaymentHandler.java                  # Phase 2: ProviderService handler
│   ├── PaymentIntentPayInHandler.java       # Phase 3A: PayInProviderService handler
│   └── PaymentIntentBeneficiaryHandler.java # Phase 3B: BeneficiaryService handler
└── internal/
    ├── PublishQuotes.java                   # Phase 1: payout quote publishing
    ├── GetQuote.java                        # Phase 1: quote retrieval
    ├── SubmitPayment.java                   # Phase 2: payment submission
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

Or build a JAR:
```bash
./gradlew build
java -jar build/libs/*.jar
```

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

If you do not configure SLF4J yourself, logback's default config writes plain
text to stderr — same as the rest of the application.

## Testing

Run unit tests:
```bash
./gradlew test
```

## Support

Contact the T-0 team for:
- Sandbox credentials
- Integration support

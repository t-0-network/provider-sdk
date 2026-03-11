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

## Project Structure

```
src/main/java/network/t0/provider/
├── Main.java                    # Entry point with integration steps
├── Config.java                  # Configuration record
├── handler/
│   └── PaymentHandler.java      # ProviderService implementation
└── internal/
    ├── PublishQuotes.java       # Quote publishing logic
    ├── GetQuote.java            # Quote fetching for testing
    └── SubmitPayment.java       # Payment submission for testing
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

## Testing

Run unit tests:
```bash
./gradlew test
```

## Support

Contact the T-0 team for:
- Sandbox credentials
- Integration support

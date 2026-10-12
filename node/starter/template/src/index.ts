import {
  createClient,
  createService,
  NetworkService,
  nodeAdapter,
  PaymentIntentBeneficiary,
  PaymentIntentNetwork,
  PaymentIntentPayInProvider,
  ProviderService,
  publicKeyFromPrivateKey,
  signatureValidation,
} from "@t-0/provider-sdk";
import { once } from "node:events";
import http from "node:http";
import { ConfigurationError, loadConfig } from "./config.js";
import publishQuotes from "./publish_quotes.js";
import CreateProviderService from "./service.js";
import getQuote from "./get_quote.js";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import submitPayment from "./submit_payment.js";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import completeManualAmlCheck from "./complete_manual_aml_check.js";
import CreatePayInProviderService from "./payment_intent_pay_in_service.js";
import CreateBeneficiaryService from "./payment_intent_beneficiary_service.js";
import publishPaymentIntentQuotes from "./publish_payment_intent_quotes.js";
import { quotePublishingInterval as parseQuotePublishingInterval, type Publishing } from "./lib.js";
import getPaymentIntentQuote from "./get_payment_intent_quote.js";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import createPaymentIntent from "./create_payment_intent.js";

function installShutdown(server: http.Server, publishing: Publishing): void {
  let draining = false;
  const shutdown = (): void => {
    if (draining) {
      return;
    }
    draining = true;
    publishing.shuttingDown = true;
    for (const timer of publishing.timers) {
      clearInterval(timer);
    }
    publishing.timers.length = 0;
    const force = setTimeout(() => {
      server.closeAllConnections();
      process.exit(0);
    }, 15_000);
    // A drain error is one stderr line. The process still exits 0.
    server.close((error) => {
      clearTimeout(force);
      if (error !== undefined) {
        console.error(`Provider failed to shut down: ${error.message}`);
      }
      process.exit(0);
    });
  };
  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

function reportStartupFailure(error: unknown): void {
  if (error instanceof ConfigurationError) {
    console.error(`ERROR: ${error.message}`);
    console.error(error.help);
  } else {
    console.error(`Provider failed to start: ${error instanceof Error ? error.message : String(error)}`);
  }
  process.exit(1);
}

async function main(): Promise<void> {
  const config = loadConfig();
  const quotePublishingInterval = parseQuotePublishingInterval(process.env.QUOTE_PUBLISHING_INTERVAL);

  console.log("🚀 Service starting...");
  console.log(`📡 Port: ${config.port}`);
  console.log(`🔑 Provider Public Key: ${publicKeyFromPrivateKey(config.privateKey)}`);
  console.log(`🔑 T-0 Network Verification Key: ${config.networkPublicKey}`);
  const networkClient = createClient(config.privateKey, config.endpoint, NetworkService);
  const paymentIntentClient = createClient(config.privateKey, config.endpoint, PaymentIntentNetwork.PaymentIntentService);

  const server = http.createServer(
    signatureValidation(
      nodeAdapter(
        createService(config.networkPublicKey, (r) => {
          r.service(ProviderService, CreateProviderService(networkClient));
          // Phase 3A — Pay-In Provider role. Remove if you are only a beneficiary.
          r.service(PaymentIntentPayInProvider.PayInProviderService, CreatePayInProviderService(paymentIntentClient));
          // Phase 3B — Beneficiary Provider role. Remove if you are only a pay-in provider.
          r.service(PaymentIntentBeneficiary.BeneficiaryService, CreateBeneficiaryService());
        }, {
          // SDK-wide logger. Used for response-validation failures (safety net).
          // Swap `console` for pino / winston
          // by adapting: { error: (msg, fields) => pino.error(fields, msg) }
          logger: {
            error: (msg, fields) => console.error(JSON.stringify({ msg, ...fields })),
          },
        })))
  );

  server.listen(config.port);
  await once(server, "listening");

  // Exists before either publisher's first await. A signal during that await
  // sets the flag, so the await cannot start a timer while the process drains.
  const publishing: Publishing = { shuttingDown: false, timers: [] };
  installShutdown(server, publishing);
  console.log("✅ Service ready and is listening at", server.address());

  await publishQuotes(networkClient, quotePublishingInterval, publishing);

  // Phase 3A — Pay-In Provider role. Comment out if you are only a beneficiary.
  await publishPaymentIntentQuotes(paymentIntentClient, quotePublishingInterval, publishing);

  // Step 1.1 is done. You successfully initialised starter template

  // TODO: Step 1.2 share the generated public key in the .env comment with the T-0 team

  // TODO: Step 1.3 implement publishing of quotes in the ./publish_quotes.ts

  // TODO: Step 1.4 check that quote for target currency is successfully received
  await getQuote(networkClient);

  // TODO: Step 2.2 deploy your integration and provide t-0 team base URL of your deployment

  // TODO: Step 2.3 check that you can submit payment by revisiting ./submit_payment.ts uncommenting following line
  // await submitPayment(networkClient)

  // TODO: Step 2.5 ask t-0 team to submit a payment which would trigger your payOut endpoint

  // TODO: Step 2.6 (optional) if your payOut returns manualAmlCheck, report the check result by revisiting ./complete_manual_aml_check.ts uncommenting following line
  // await completeManualAmlCheck(networkClient, paymentId)

  // ──────────────────────────────────────────────────────────────
  // Payment Intent Flow — Phase 3
  //
  // Implement the role that applies to you. See the README for details.
  // ──────────────────────────────────────────────────────────────

  // Phase 3B — Beneficiary Provider role. Comment out if you are only a pay-in provider.
  // TODO: Step 3B.1 check that indicative quotes are returned
  await getPaymentIntentQuote(paymentIntentClient);
  // TODO: Step 3B.2 create a payment intent for a real end-user when they want to pay
  // await createPaymentIntent(paymentIntentClient)
}

main().catch((error: unknown) => {
  reportStartupFailure(error);
});

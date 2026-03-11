import dotenv from 'dotenv';
import {
  createClient,
  createService,
  NetworkService,
  nodeAdapter,
  ProviderService,
  signatureValidation,
} from "@t-0/provider-sdk";
import invariant from 'tiny-invariant';
import http from "http";
import publishQuotes from "./publish_quotes";
import CreateProviderService from "./service";
import getQuote from "./get_quote";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import submitPayment from "./submit_payment";

dotenv.config();

const privateKeyHex = process.env.PROVIDER_PRIVATE_KEY;
const port = process.env.PORT || 3000;
const endpoint = process.env.TZERO_ENDPOINT || "https://api-sandbox.t-0.network";
const quotePublishingInterval: number = Number(process.env.QUOTE_PUBLISHING_INTERVAL || "5000");
const networkPublicKeyHex = process.env.NETWORK_PUBLIC_KEY;

invariant(privateKeyHex, 'Private key not set');
invariant(quotePublishingInterval > 0, 'Interval must be positive');
invariant(networkPublicKeyHex, 'Network public key is not set');

async function main() {
  console.log('🚀 Service starting...');
  console.log(`📡 Port: ${port}`);
  console.log(`🔑 Network Public Key: ${networkPublicKeyHex}`);
  const networkClient = createClient(privateKeyHex!, endpoint, NetworkService);

  await publishQuotes(networkClient, quotePublishingInterval)

  const server = http.createServer(
    signatureValidation(
      nodeAdapter(
        createService(networkPublicKeyHex!, (r) => {
          r.service(ProviderService, CreateProviderService(networkClient));
        })))
  ).listen(port);
  console.log("✅ Service ready and is listening at", server.address());

  // Step 1.1 is done. You successfully initialised starter template

  // TODO: Step 1.2 take you generated public key from .env and share it with t-0 team

  // TODO: Step 1.3 implement publishing of quotes in the ./publish_quotes.ts

  // TODO: Step 1.4 check that quote for target currency is successfully received
  await getQuote(networkClient)

  // TODO: Step 2.2 deploy your integration and provide t-0 team base URL of your deployment

  // TODO: Step 2.3 check that you can submit payment by revisiting ./submit_payment.ts uncommenting following line
  // await submitPayment(networkClient)

  // TODO: Step 2.5 ask t-0 team to submit a payment which would trigger your payOut endpoint
}

main().catch((error) => {
  console.error('❌ Error starting service:', error);
  process.exit(1);
});


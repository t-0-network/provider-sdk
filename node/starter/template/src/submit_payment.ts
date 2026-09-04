import {type Client, NetworkService, PaymentMethodType} from "@t-0/provider-sdk";
import {decimalFromString, decimalToString} from "./lib";
import {randomUUID} from "node:crypto";

export default async function submitPayment(networkClient: Client<typeof NetworkService>): Promise<void> {
  // TODO: Step 2.3 Test submitting payment to the system
  const result = await networkClient.createPayment({
    paymentClientId: randomUUID(),
    // quoteId: specify quote id if you want a reliable USD/GBP rate
    currency: 'GBP',
    amount: {
      amount: {
        case: "payOutAmount",
        value: decimalFromString("10"),
      },
    },
    paymentDetails: {
      details: {
        case: "sepa",
        value: {
          iban: 'GB12345567890',
          beneficiaryName: 'Max Mustermann',
        }
      }
    },
  })

  switch (result.result.case) {
      case 'accepted': {
        const settlementAmount = result.result.value.settlementAmount === undefined
          ? "not provided"
          : decimalToString(result.result.value.settlementAmount);
        console.log(`Payment accepted, ${settlementAmount} USD to settle`)
        break;
      }
      case 'failure':
        console.log(`Payment failed with '${result.result.value.reason}'`)
        break;
  }
}
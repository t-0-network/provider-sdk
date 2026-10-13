import {type Client, NetworkService} from "@t-0/provider-sdk";
import {decimalFromString, decimalToString} from "./lib.js";
import {randomUUID} from "node:crypto";

// ivms101.NaturalPersonNameTypeCode.NATURAL_PERSON_NAME_TYPE_CODE_LEGL (proto/ivms101/v1/ivms/enum.proto)
// — the SDK does not export the IVMS101 types, so the demo names the wire value of the
// legal-name enum here. Change it together with the tests if the proto enum is renumbered.
const NATURAL_PERSON_NAME_TYPE_CODE_LEGL = 4;

const legalName = (primaryIdentifier: string, secondaryIdentifier: string) => ({
  person: {
    case: "naturalPerson" as const,
    value: {
      name: {
        nameIdentifiers: [{primaryIdentifier, secondaryIdentifier, nameIdentifierType: NATURAL_PERSON_NAME_TYPE_CODE_LEGL}],
      },
    },
  },
});

export default async function submitPayment(networkClient: Client<typeof NetworkService>): Promise<void> {
  // TODO: Step 2.3 Test submitting payment to the system
  // An accepted result is not a completed payout — the PaymentUpdate notifications on the
  // ProviderService handler carry the outcome. A retry must resend the same payment client
  // id and request — the network deduplicates on it; running this demo again generates a
  // new id and therefore a new payment.
  try {
    const result = await networkClient.createPayment({
      paymentClientId: randomUUID(), // idempotency key — reuse to retry without duplicating the payment
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
            iban: 'GB29NWBK60161331926819',
            beneficiaryName: 'Max Mustermann',
          }
        }
      },
      travelRuleData: {
        // TODO: populate real IVMS101 information for your originating customer
        // and the beneficiary. These sample persons are schema-valid placeholders.
        originator: [legalName("Doe", "John")],
        beneficiary: [legalName("Mustermann", "Max")],
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
        default:
          console.error("unexpected result type")
    }
  } catch (error) {
    // A failed call is logged, and the provider keeps serving.
    console.error(`Error submitting payment: ${error instanceof Error ? error.message : String(error)}`)
  }
}
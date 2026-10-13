import assert from "node:assert/strict";
import test, {type TestContext} from "node:test";
import {type Client, type CreatePaymentRequest, type CreatePaymentResponse, NetworkService} from "@t-0/provider-sdk";
import submitPayment from "./submit_payment.js";

// The demo passes a plain request object to the single RPC it calls, so the
// fake records exactly what submitPayment built. Nothing talks to a server.
function fakeClient(
    t: TestContext,
    options: {response?: Record<string, unknown>; error?: Error} = {},
): { calls: CreatePaymentRequest[]; client: Client<typeof NetworkService>; logged: unknown[][] } {
  const calls: CreatePaymentRequest[] = [];
  const logged: unknown[][] = [];
  const client = {
    createPayment: async (request: CreatePaymentRequest): Promise<CreatePaymentResponse> => {
      calls.push(request);
      if (options.error !== undefined) {
        throw options.error;
      }
      return (options.response ?? {}) as CreatePaymentResponse;
    },
  } as unknown as Client<typeof NetworkService>;
  t.mock.method(console, "error", (...args: unknown[]) => logged.push(["error", ...args]));
  t.mock.method(console, "log", (...args: unknown[]) => logged.push(["log", ...args]));
  return {calls, client, logged};
}

const text = (logged: unknown[][]): string => logged.map((parts) => parts.join(" ")).join("\n");

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// ivms101.NaturalPersonNameTypeCode.NATURAL_PERSON_NAME_TYPE_CODE_LEGL
// (proto/ivms101/v1/ivms/enum.proto) — keep in step with the demo constant in submit_payment.ts.
const LEGL = 4;

type TravelRule = NonNullable<CreatePaymentRequest["travelRuleData"]>;
type Person = TravelRule["originator"][number];

function assertLegalName(person: Person, primary: string, secondary: string): void {
  assert.equal(person.person.case, "naturalPerson");
  if (person.person.case !== "naturalPerson") {
    return;
  }
  const identifiers = person.person.value.name?.nameIdentifiers;
  assert.equal(identifiers?.length, 1);
  assert.equal(identifiers[0]?.nameIdentifierType, LEGL);
  assert.equal(identifiers[0]?.primaryIdentifier, primary);
  assert.equal(identifiers[0]?.secondaryIdentifier, secondary);
}

test("the demo builds one schema-valid CreatePayment request", async (t) => {
  // The response a real accept carries: Accepted marks its amounts required.
  const {calls, client, logged} = fakeClient(t, {
    response: {
      result: {
        case: "accepted",
        value: {
          paymentId: 42n,
          settlementAmount: {unscaled: 1250n, exponent: -2},
          payoutAmount: {unscaled: 10n, exponent: 0},
          payoutProviderId: 1,
        },
      },
    },
  });

  await submitPayment(client);

  assert.equal(calls.length, 1);
  const request = calls[0];
  if (request === undefined) {
    return;
  }

  assert.match(request.paymentClientId, UUID_RE);

  const amount = request.amount?.amount;
  assert.equal(amount?.case, "payOutAmount");
  if (amount?.case === "payOutAmount") {
    assert.equal(amount.value.unscaled, 10n);
    assert.equal(amount.value.exponent, 0);
  }
  assert.equal(request.currency, "GBP");

  // The same checks the network's buf.validate rules apply to the SEPA demo data.
  const details = request.paymentDetails?.details;
  assert.equal(details?.case, "sepa");
  if (details?.case !== "sepa") {
    return;
  }
  const sepa = details.value;
  assert.match(sepa.iban, /^[A-Z]{2}[0-9]{2}[A-Z0-9]+$/);
  assert.ok(sepa.iban.length >= 15 && sepa.iban.length <= 34, `iban length: ${sepa.iban.length}`);
  assert.equal(sepa.iban, "GB29NWBK60161331926819"); // the sandbox IBAN the demo pins
  assert.equal(sepa.beneficiaryName, "Max Mustermann");

  const travel = request.travelRuleData;
  assert.ok(travel !== undefined, "travel_rule_data is required");
  assert.equal(travel.originator.length, 1);
  assert.equal(travel.beneficiary.length, 1);
  assertLegalName(travel.originator[0] as Person, "Doe", "John");
  assertLegalName(travel.beneficiary[0] as Person, "Mustermann", "Max");
  // The SEPA beneficiary name and the IVMS101 beneficiary describe the same person.
  const beneficiaryNameId = travel.beneficiary[0]?.person;
  if (beneficiaryNameId?.case === "naturalPerson") {
    const id = beneficiaryNameId.value.name?.nameIdentifiers[0];
    assert.equal(`${id?.secondaryIdentifier} ${id?.primaryIdentifier}`, sepa.beneficiaryName);
  }

  assert.match(text(logged), /Payment accepted/);
});

test("a failure result is logged", async (t) => {
  const {calls, client, logged} = fakeClient(t, {
    response: {result: {case: "failure", value: {reason: 10}}},
  });

  await submitPayment(client);

  assert.equal(calls.length, 1);
  assert.match(text(logged), /Payment failed with/);
});

test("an unexpected result case is reported, not swallowed", async (t) => {
  const {calls, client, logged} = fakeClient(t, {
    response: {result: {case: "settlementRequired", value: {}}},
  });

  await submitPayment(client);

  assert.equal(calls.length, 1);
  assert.match(text(logged), /unexpected result type/);
});

test("a failed call is one stderr line and does not propagate", async (t) => {
  const {calls, client, logged} = fakeClient(t, {error: new Error("connection refused")});

  await submitPayment(client);

  assert.equal(calls.length, 1, "no retry loop");
  const errors = logged.filter(([kind]) => kind === "error");
  assert.equal(errors.length, 1);
  assert.equal(errors[0]?.[1], "Error submitting payment: connection refused");
});

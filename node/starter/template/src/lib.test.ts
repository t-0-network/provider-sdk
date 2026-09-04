import assert from "node:assert/strict";
import test from "node:test";
import {create} from "@bufbuild/protobuf";
import {Decimal, DecimalSchema} from "@t-0/provider-sdk";
import {decimalFromString, decimalToString} from "./lib";

const MIN_INT64 = -(1n << 63n);
const MAX_INT64 = (1n << 63n) - 1n;
const decimal = (unscaled: bigint, exponent: number): Decimal =>
  create(DecimalSchema, {unscaled, exponent});

test("decimal helpers preserve signed-int64 boundaries exactly", () => {
  for (const [text, unscaled] of [
    ["9223372036854775807", MAX_INT64],
    ["-9223372036854775808", MIN_INT64],
    ["9007199254740993", 9007199254740993n],
  ] as const) {
    const value = decimalFromString(text);
    assert.equal(value.unscaled, unscaled);
    assert.equal(value.exponent, 0);
    assert.equal(decimalToString(value), text);
  }
});

test("decimal helpers format exponent bounds and point padding exactly", () => {
  assert.deepEqual(decimalFromString("0.00000001"), decimal(1n, -8));
  assert.equal(decimalToString(decimal(1n, -8)), "0.00000001");
  assert.equal(decimalToString(decimal(-12n, -4)), "-0.0012");
  assert.equal(decimalToString(decimal(123n, 0)), "123");
  assert.equal(decimalToString(decimal(123n, 8)), "12300000000");
});

test("decimalFromString uses positive exponents only when required for int64", () => {
  const onceShifted = decimalFromString("92233720368547758070");
  assert.deepEqual(onceShifted, decimal(MAX_INT64, 1));
  assert.equal(decimalToString(onceShifted), "92233720368547758070");

  const fullyShifted = decimalFromString("922337203685477580700000000");
  assert.deepEqual(fullyShifted, decimal(MAX_INT64, 8));
  assert.equal(decimalToString(fullyShifted), "922337203685477580700000000");
});

test("decimalFromString removes trailing zeros only when representability requires it", () => {
  const fractional = decimalFromString("1.230000000");
  assert.deepEqual(fractional, decimal(123000000n, -8));
  assert.equal(decimalToString(fractional), "1.23000000");

  const integral = decimalFromString("100.00");
  assert.deepEqual(integral, decimal(10000n, -2));
  assert.equal(decimalToString(integral), "100.00");
});

test("decimal helpers normalize signed zero without losing representable scale", () => {
  assert.deepEqual(decimalFromString("-0.00"), decimal(0n, -2));
  assert.equal(decimalToString(decimalFromString("-0.00")), "0.00");
  assert.equal(decimalToString(decimal(0n, 8)), "0");
});

test("decimal helpers round trip exact representable values", () => {
  for (const value of [
    "0",
    "1",
    "-1",
    "123.4500",
    "0.00000001",
    "9223372036854775807",
    "-9223372036854775808",
    "92233720368547758070",
  ]) {
    assert.equal(decimalToString(decimalFromString(value)), value);
  }
});

test("decimalFromString rejects malformed, scientific, and unrepresentable values", () => {
  for (const value of [
    "",
    "+1",
    ".1",
    "1.",
    " 1",
    "1 ",
    "1e3",
    "1E3",
    "1_000",
    "0.000000001",
    "9223372036854775808",
    "9223372036854775807000000000",
  ]) {
    assert.throws(() => decimalFromString(value));
  }
});

test("decimalToString validates incoming Decimal values", () => {
  for (const value of [
    decimal(MAX_INT64 + 1n, 0),
    decimal(MIN_INT64 - 1n, 0),
    decimal(1n, -9),
    decimal(1n, 9),
    {unscaled: 1n, exponent: 1.5} as Decimal,
    {unscaled: "1", exponent: 0} as unknown as Decimal,
  ]) {
    assert.throws(() => decimalToString(value));
  }
});

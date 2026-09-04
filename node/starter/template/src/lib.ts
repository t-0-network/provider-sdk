import {Decimal, DecimalSchema} from "@t-0/provider-sdk";
import {create} from "@bufbuild/protobuf";

const MIN_UNSCALED = -(1n << 63n);
const MAX_UNSCALED = (1n << 63n) - 1n;
const MIN_EXPONENT = -8;
const MAX_EXPONENT = 8;

const fitsInt64 = (value: bigint): boolean =>
  value >= MIN_UNSCALED && value <= MAX_UNSCALED;

export const decimalFromString = (value: string): Decimal => {
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value);
  if (match === null) {
    throw new Error("Decimal must use plain decimal syntax");
  }

  const [, sign, whole, fraction = ""] = match;
  let unscaled = BigInt(`${sign}${whole}${fraction}`);
  let exponent = fraction.length === 0 ? 0 : -fraction.length;

  // Preserve a caller's scale unless it cannot be encoded by the wire contract.
  while (exponent < MIN_EXPONENT && unscaled % 10n === 0n) {
    unscaled /= 10n;
    exponent += 1;
  }
  if (exponent < MIN_EXPONENT) {
    throw new Error("Decimal requires more than 8 fractional digits");
  }

  // Positive exponents are necessary for exact values whose unscaled form would
  // otherwise exceed int64. Only remove zeros when that representation is needed.
  while (!fitsInt64(unscaled) && exponent < MAX_EXPONENT && unscaled % 10n === 0n) {
    unscaled /= 10n;
    exponent += 1;
  }
  if (!fitsInt64(unscaled)) {
    throw new Error("Decimal unscaled value must fit signed int64");
  }

  return create(DecimalSchema, {unscaled, exponent});
};

const validateDecimal = (value: Decimal): void => {
  if (typeof value?.unscaled !== "bigint") {
    throw new Error("Decimal unscaled value must be a bigint");
  }
  if (!fitsInt64(value.unscaled)) {
    throw new Error("Decimal unscaled value must fit signed int64");
  }
  if (!Number.isInteger(value.exponent) || value.exponent < MIN_EXPONENT || value.exponent > MAX_EXPONENT) {
    throw new Error("Decimal exponent must be an integer between -8 and 8");
  }
};

export const decimalToString = (value: Decimal): string => {
  validateDecimal(value);

  const sign = value.unscaled < 0n ? "-" : "";
  const digits = (value.unscaled < 0n ? -value.unscaled : value.unscaled).toString();

  if (value.unscaled === 0n && value.exponent >= 0) {
    return "0";
  }
  if (value.exponent >= 0) {
    return `${sign}${digits}${"0".repeat(value.exponent)}`;
  }

  const decimalPoint = digits.length + value.exponent;
  if (decimalPoint > 0) {
    return `${sign}${digits.slice(0, decimalPoint)}.${digits.slice(decimalPoint)}`;
  }

  return `${sign}0.${"0".repeat(-decimalPoint)}${digits}`;
};

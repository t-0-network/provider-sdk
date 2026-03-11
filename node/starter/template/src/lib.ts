import {Decimal, DecimalSchema} from "@t-0/provider-sdk";
import {create} from "@bufbuild/protobuf";

export const toProtoDecimal = (unscaled: number, exponent: number): Decimal => {
  return create(DecimalSchema, {
    unscaled: BigInt(unscaled),
    exponent: exponent,
  });
}

export const fromProtoDecimal = (value: Decimal): number => {
  return Number(value.unscaled) * Math.pow(10, value.exponent)
}
/** How messages are encoded on the wire: binary Protobuf or JSON. */
export const WireFormat = {
    Binary: "proto",
    Json: "json",
} as const;

export type WireFormat = (typeof WireFormat)[keyof typeof WireFormat];

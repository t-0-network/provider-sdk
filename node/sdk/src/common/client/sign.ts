import {keccak_256} from "@noble/hashes/sha3.js";
import NetworkHeaders from "../headers.js";
import type {SignerFunction} from "./client.js";

/**
 * Signs bytes with the current timestamp and returns the signature headers to set on the request:
 * digest = Keccak256(bytes || uint64le(timestamp_ms)).
 */
export async function signatureHeaders(signer: SignerFunction, bytes: Uint8Array): Promise<[string, string][]> {
    const ts = Date.now();
    // 64‑bit little‑endian timestamp
    const tsBuf = Buffer.alloc(8);
    tsBuf.writeBigUInt64LE(BigInt(ts));

    const hash = keccak_256.create()
        .update(bytes)
        .update(tsBuf);
    const hashHex = Buffer.from(hash.digest())

    const sig = await signer(hashHex);

    return [
        [NetworkHeaders.Signature, "0x" + sig.signature.toString('hex')],
        [NetworkHeaders.PublicKey, "0x" + sig.publicKey.toString('hex')],
        [NetworkHeaders.SignatureTimestamp, ts.toString()],
    ];
}

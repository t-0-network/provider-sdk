import NetworkHeaders from "../headers.js";
import {computeDigest} from "../crypto/hash.js";
import type {SignerFunction} from "./client.js";

/** The signature headers for bytes signed now: digest = Keccak256(bytes || uint64le(timestamp_ms)). */
export async function signatureHeaders(signer: SignerFunction, bytes: Uint8Array): Promise<[string, string][]> {
    const ts = Date.now();
    const sig = await signer(computeDigest(bytes, ts));

    return [
        [NetworkHeaders.Signature, "0x" + sig.signature.toString('hex')],
        [NetworkHeaders.PublicKey, "0x" + sig.publicKey.toString('hex')],
        [NetworkHeaders.SignatureTimestamp, ts.toString()],
    ];
}

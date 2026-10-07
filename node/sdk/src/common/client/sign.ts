import {Code, ConnectError} from "@connectrpc/connect";
import NetworkHeaders from "../headers.js";
import {computeDigest} from "../crypto/hash.js";
import {SIGNER_PUBLIC_KEY_INVALID, SIGNER_SIGNATURE_INVALID, SIGNING_FAILED} from "../messages.js";
import type {Signature, SignerFunction} from "./client.js";

/** The signature headers for bytes signed now: digest = Keccak256(bytes || uint64le(timestamp_ms)). */
export async function signatureHeaders(signer: SignerFunction, bytes: Uint8Array): Promise<[string, string][]> {
    const ts = Date.now();
    let sig: Signature;
    try {
        sig = await signer(computeDigest(bytes, ts));
    } catch (e) {
        throw new ConnectError(SIGNING_FAILED(e instanceof Error ? e.message : String(e)), Code.Internal, undefined, undefined, e);
    }
    const {signature, publicKey} = checkSignerOutput(sig);

    return [
        [NetworkHeaders.Signature, "0x" + signature.toString("hex")],
        [NetworkHeaders.PublicKey, "0x" + publicKey.toString("hex")],
        [NetworkHeaders.SignatureTimestamp, ts.toString()],
    ];
}

/**
 * What a signer returns, checked before anything is sent, as in every SDK: a signature of 64 or 65
 * bytes, sent as it is, then the 65-byte uncompressed public key.
 */
function checkSignerOutput(sig: Signature | undefined): {signature: Buffer; publicKey: Buffer} {
    const signature = sig?.signature;
    if (!(signature instanceof Uint8Array) || !(signature.length === 64 || signature.length === 65)) {
        throw new ConnectError(SIGNING_FAILED(SIGNER_SIGNATURE_INVALID), Code.Internal);
    }
    const publicKey = sig?.publicKey;
    if (!(publicKey instanceof Uint8Array) || publicKey.length !== 65 || publicKey[0] !== 0x04) {
        throw new ConnectError(SIGNING_FAILED(SIGNER_PUBLIC_KEY_INVALID), Code.Internal);
    }
    return {signature: Buffer.from(signature), publicKey: Buffer.from(publicKey)};
}

import { secp256k1 } from '@noble/curves/secp256k1.js'
import type {Signature, SignerFunction} from "./client.js";
import { parsePrivateKey, uncompressedPublicKeyFromPrivateKey } from "../crypto/keys.js";
import { DIGEST_LENGTH } from "../messages.js";

export const CreateSigner = (privateKey: string | Buffer)=> {
    privateKey = parsePrivateKey(privateKey)
    const publicKey = uncompressedPublicKeyFromPrivateKey(privateKey);

    return async (data: Buffer): Promise<Signature> => {
        if (data.length !== 32) {
            throw new Error(DIGEST_LENGTH);
        }

        // 65 bytes r ‖ s ‖ v with v of 0 or 1, as every SDK signs. noble puts v first.
        const recovered = secp256k1.sign(data, privateKey, {prehash: false, format: 'recovered'});

        return {
            signature: Buffer.concat([recovered.subarray(1), recovered.subarray(0, 1)]),
            publicKey: publicKey,
        };
    }
}

/**
 * The signer of a private key given as hex (an optional 0x or 0X prefix and 64 hex characters), as
 * createClient takes it for its signer argument. It signs a 32-byte digest and returns the 65-byte
 * signature r ‖ s ‖ v (low s, v of 0 or 1) and the 65-byte uncompressed public key. Throws if the
 * key is not valid.
 */
export function newSignerFromHex(privateKey: string): SignerFunction {
    return CreateSigner(privateKey);
}

export default CreateSigner;

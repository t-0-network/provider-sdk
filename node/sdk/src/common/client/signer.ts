import { secp256k1 } from '@noble/curves/secp256k1.js'
import type {Signature} from "./client.js";
import { parsePrivateKey, uncompressedPublicKeyFromPrivateKey } from "../crypto/keys.js";

export const CreateSigner = (privateKey: string | Buffer)=> {
    privateKey = parsePrivateKey(privateKey)
    const publicKey = uncompressedPublicKeyFromPrivateKey(privateKey);

    return async (data: Buffer): Promise<Signature> => {
        // Ensure hash is 32 bytes
        if (data.length !== 32) {
            throw new Error('Message hash must be 32 bytes');
        }

        // Sign the hash
        const signature = secp256k1.sign(data, privateKey, {prehash: false});

        return {
            signature: Buffer.from(signature),
            publicKey: publicKey,
        };
    }
}

export default CreateSigner;

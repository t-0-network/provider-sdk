import { secp256k1 } from '@noble/curves/secp256k1.js'
import {Signature} from "./client.js";

export const CreateSigner = (privateKey: string | Buffer)=> {
    privateKey = parsePrivateKey(privateKey)
    const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, false));

    return async (data: Buffer): Promise<Signature> => {
        // Ensure hash is 32 bytes
        if (data.length !== 32) {
            throw new Error('Message hash must be 32 bytes');
        }

        // Sign the hash
        const signature = secp256k1.sign(data, privateKey);

        return {
            signature: Buffer.from(signature),
            publicKey: publicKey,
        };
    }
}

const parsePrivateKey = (privateKey: string | Buffer) => {
    if (typeof privateKey == 'string'){
        privateKey = privateKey.replace(/^0x/, '');
        if (!/^[0-9a-fA-F]{64}$/.test(privateKey)) {
            throw new Error('Private key must be 64 hex characters');
        }

        privateKey = Buffer.from(privateKey, 'hex');
    }

    // Validate private key
    if (!secp256k1.utils.isValidSecretKey(privateKey)) {
        throw new Error('Invalid private key');
    }

    return privateKey
}

export default CreateSigner;
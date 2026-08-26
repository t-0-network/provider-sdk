import { createClient as createClientCommon, type Signature, type SignerFunction } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";

export const DEFAULT_ENDPOINT = "https://api.t-0.network"

export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string | undefined, svc: T) {
    return createClientCommon(signer, endpoint || DEFAULT_ENDPOINT, svc);
}

export type { Signature, SignerFunction } from "../common/client/client.js";

export default createClient;

import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";

export const DEFAULT_ENDPOINT = "https://api.t-0.network"

export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string | undefined, svc: T, opts?: ClientOptions) {
    return createClientCommon(signer, endpoint || DEFAULT_ENDPOINT, svc, opts);
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";

export default createClient;

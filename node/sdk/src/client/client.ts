import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";

export const DEFAULT_ENDPOINT = "https://api.t-0.network"

/**
 * @param endpoint the network's base URL, `http://` or `https://`; undefined for DEFAULT_ENDPOINT.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string | undefined, svc: T, opts?: ClientOptions) {
    return createClientCommon(signer, baseUrl(endpoint), svc, opts);
}

function baseUrl(endpoint: string | undefined): string {
    if (endpoint === undefined) {
        return DEFAULT_ENDPOINT;
    }
    if (endpoint === null || endpoint === "") {
        throw new Error("base URL is not set");
    }
    let url: URL | undefined;
    try {
        url = new URL(endpoint);
    } catch {
        // not a URL
    }
    if (url === undefined || (url.protocol !== "http:" && url.protocol !== "https:") || url.hostname === "") {
        throw new Error("base URL is not valid");
    }
    return endpoint;
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";
export { WireFormat } from "../common/wire-format.js";

export default createClient;

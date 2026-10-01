import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";

export const DEFAULT_ENDPOINT = "https://api.t-0.network"

/**
 * @param endpoint the network's base URL; undefined for DEFAULT_ENDPOINT.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, endpoint: string | undefined, svc: T, opts?: ClientOptions) {
    return createClientCommon(signer, baseUrl(endpoint), svc, opts);
}

// The rule every SDK shares: docs/CROSS_SDK_RULES.md (C2). A path in the base URL prefixes every call.
function baseUrl(endpoint: string | undefined): string {
    if (endpoint === undefined) {
        return DEFAULT_ENDPOINT;
    }
    if (endpoint === null || endpoint === "") {
        throw new Error("base URL is not set");
    }
    // A value without "://" is read as https.
    const url = endpoint.includes("://") ? endpoint : "https://" + endpoint;
    const parsed = URL.canParse(url) ? new URL(url) : undefined;
    // "?" and "#" are checked on the value, as the parser leaves an empty query or fragment out of
    // search and hash. The parser refuses a port over 65535.
    if (parsed === undefined || /[?#]/.test(url) || (parsed.protocol !== "http:" && parsed.protocol !== "https:")
        || parsed.hostname === "" || parsed.username !== "" || parsed.password !== "" || parsed.port === "0") {
        throw new Error("base URL is not valid");
    }
    return parsed.href;
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";
export { WireFormat } from "../common/wire-format.js";

export default createClient;

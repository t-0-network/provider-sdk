import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";
import { BASE_URL_NOT_SET, BASE_URL_NOT_VALID } from "../common/messages.js";

/** The base URL a client uses when it is given none. */
export const DEFAULT_BASE_URL = "https://api.t-0.network";

/** @deprecated Use DEFAULT_BASE_URL. */
export const DEFAULT_ENDPOINT = DEFAULT_BASE_URL;

export { DEFAULT_STREAM_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, MAX_TIMEOUT_MS } from "../common/client/client.js";

/**
 * @param baseUrl the network's base URL; undefined or null for DEFAULT_BASE_URL.
 */
export function createClient<T extends DescService>(signer: string | Buffer | ((data: Buffer) => Promise<Signature>) | Buffer<ArrayBufferLike>, baseUrl: string | undefined, svc: T, opts?: ClientOptions) {
    return createClientCommon(signer, parseBaseUrl(baseUrl), svc, opts);
}

// A whitespace or control character: U+0000..U+0020, U+007F, or another Unicode White_Space
// character. The same set in every SDK.
const SPACE_OR_CONTROL = /[\x00-\x20\x7f\x85\xa0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]/;

// The rule every SDK shares: docs/CROSS_SDK_RULES.md (C2). A path in the base URL prefixes every call.
function parseBaseUrl(baseUrl: string | undefined | null): string {
    if (baseUrl === undefined || baseUrl === null) {
        return DEFAULT_BASE_URL;
    }
    if (baseUrl === "") {
        throw new Error(BASE_URL_NOT_SET);
    }
    // A value without "://" is read as https.
    const url = baseUrl.includes("://") ? baseUrl : "https://" + baseUrl;
    // The URL parser drops surrounding spaces and control characters, and tabs and line breaks
    // anywhere, and escapes the others in the path; every SDK refuses them all, anywhere.
    if (SPACE_OR_CONTROL.test(url)) {
        throw new Error(BASE_URL_NOT_VALID);
    }
    const parsed = URL.canParse(url) ? new URL(url) : undefined;
    // "?" and "#" are checked on the value, as the parser leaves an empty query or fragment out of
    // search and hash. The parser refuses a port over 65535.
    if (parsed === undefined || /[?#]/.test(url) || (parsed.protocol !== "http:" && parsed.protocol !== "https:")
        || parsed.hostname === "" || parsed.username !== "" || parsed.password !== "" || parsed.port === "0") {
        throw new Error(BASE_URL_NOT_VALID);
    }
    return parsed.href;
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";
export { newSignerFromHex } from "../common/client/signer.js";
export { WireFormat } from "../common/wire-format.js";

export default createClient;

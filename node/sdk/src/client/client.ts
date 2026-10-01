import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";
import { isIPv4 } from "node:net";

export const DEFAULT_ENDPOINT = "https://api.t-0.network"

/**
 * @param endpoint the network's base URL; undefined for DEFAULT_ENDPOINT.
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
    // A value without "://" is read as https.
    const url = typeof endpoint === "string" && !endpoint.includes("://") ? "https://" + endpoint : endpoint;
    if (!validBaseUrl(url)) {
        throw new Error("base URL is not valid");
    }
    return url;
}

// Checked as written: the URL parser rewrites what it reads ("http:foo" as http://foo/, "1.2.3" as
// 1.2.0.3, "h:080" as h, "/v1/.." as /) and accepts host names that some gRPC clients cannot connect
// to ("my_host", "a..b"). It is used only for what it checks strictly: an IPv6 address, and a port of
// at most 65535. A port has no leading zero. A path prefixes every call: segments of letters, digits
// and "-._~" (not "." or ".."), each after one "/", and an optional trailing "/". Other paths ("//",
// "/..", "/%41") would reach different URLs in different SDKs, whose HTTP clients normalize them
// differently. No query or fragment.
const BASE_URL = /^https?:\/\/(\[[0-9a-f:.]+\]|[a-z0-9.-]+)(?::[1-9]\d*)?((?:\/[a-z0-9._~-]+)*)\/?$/i;
const LABEL = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i;

function validBaseUrl(endpoint: string): boolean {
    const match = typeof endpoint === "string" ? BASE_URL.exec(endpoint) : null;
    return match !== null && URL.canParse(endpoint) && validHost(match[1])
        && !match[2].split("/").some((s) => s === "." || s === "..");
}

// A bracketed IPv6 (the URL parser checks it), an IPv4 without leading zeros, or a DNS name whose last
// label starts with a letter.
function validHost(host: string): boolean {
    if (host.startsWith("[") || isIPv4(host)) {
        return true;
    }
    const labels = host.split(".");
    return labels.every((label) => LABEL.test(label)) && /^[a-z]/i.test(labels[labels.length - 1]);
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";
export { WireFormat } from "../common/wire-format.js";

export default createClient;

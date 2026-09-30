import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";

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

// Checked as written: the URL parser would read "http:foo" as http://foo/ and accept host names
// that some gRPC clients cannot connect to ("my_host", "a..b"). A port has no leading zero, and
// nothing may follow the host and port but one "/".
const BASE_URL = /^https?:\/\/(\[[0-9a-f:.]+\]|[a-z0-9.-]+)(?::([1-9]\d*))?\/?$/i;
const IPV4 = /^(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/;
const LABEL = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i;

function validBaseUrl(endpoint: string): boolean {
    const match = typeof endpoint === "string" ? BASE_URL.exec(endpoint) : null;
    if (match === null || !validHost(match[1])) {
        return false;
    }
    try {
        new URL(endpoint);
    } catch {
        return false;
    }
    const port = match[2] === undefined ? undefined : Number(match[2]);
    return port === undefined || (port >= 1 && port <= 65535);
}

// A bracketed IPv6 (the URL parser checks it), an IPv4, or a DNS name whose last label starts with a letter.
function validHost(host: string): boolean {
    if (host.startsWith("[") || IPV4.test(host)) {
        return true;
    }
    const labels = host.split(".");
    return labels.every((label) => LABEL.test(label)) && /^[a-z]/i.test(labels[labels.length - 1]);
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";
export { WireFormat } from "../common/wire-format.js";

export default createClient;

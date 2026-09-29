import { createClient as createClientCommon, type ClientOptions, type Signature } from "../common/client/client.js";
import type { DescService } from "@bufbuild/protobuf";

export const DEFAULT_ENDPOINT = "https://api.t-0.network"

/**
 * @param endpoint the network's base URL, `http://` or `https://` with a host and an optional port,
 *     no path; undefined for DEFAULT_ENDPOINT.
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
    if (!validBaseUrl(endpoint)) {
        throw new Error("base URL is not valid");
    }
    return endpoint;
}

// Checked as written: the URL parser would read "http:foo" as http://foo/ and accept a host with
// "_", which some gRPC clients cannot connect to. A host is a DNS name, IPv4 or a bracketed IPv6;
// nothing may follow the host and port but one "/".
const BASE_URL = /^https?:\/\/(?:\[[0-9a-f:.]+\]|[a-z0-9.-]+)(?::(\d+))?\/?$/i;

function validBaseUrl(endpoint: string): boolean {
    const match = typeof endpoint === "string" ? BASE_URL.exec(endpoint) : null;
    if (match === null) {
        return false;
    }
    try {
        new URL(endpoint);
    } catch {
        return false;
    }
    const port = match[1] === undefined ? undefined : Number(match[1]);
    return port === undefined || (port >= 1 && port <= 65535);
}

export type { ClientOptions, Signature, SignerFunction } from "../common/client/client.js";
export { WireFormat } from "../common/wire-format.js";

export default createClient;

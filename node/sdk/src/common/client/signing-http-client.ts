import {Code, ConnectError} from "@connectrpc/connect";
import {universalClientResponseFromFetch, type UniversalClientFn, type UniversalClientResponse} from "@connectrpc/connect/protocol";
import {signatureHeaders} from "./sign.js";
import type {SignerFunction} from "./client.js";

/**
 * The transport's HTTP client: signs an enveloped body (Connect streaming, gRPC) over its first
 * envelope as sent and sends it at once, any other body whole. See docs/node/STREAMING.md.
 *
 * @param fetchFn defaults to the global fetch at the time of each call.
 */
export function createSigningFetchClient(signer: SignerFunction, fetchFn?: typeof globalThis.fetch): UniversalClientFn {
    return async (req) => {
        const it = (req.body ?? emptyBody)[Symbol.asyncIterator]();
        const enveloped = isEnveloped(req.header.get("Content-Type"));

        let first: Uint8Array | undefined;
        let whole: Uint8Array<ArrayBuffer> | undefined;
        let headers: [string, string][];
        try {
            if (enveloped) {
                // A client stream waits here for its first message; the call's deadline or signal ends that.
                const r = await untilAborted(it.next(), req.signal);
                if (r.done !== true) {
                    first = r.value;
                    requireOneEnvelope(first);
                }
                headers = await signatureHeaders(signer, first ?? new Uint8Array(0));
            } else {
                whole = await untilAborted(readAll(it), req.signal);
                headers = await signatureHeaders(signer, whole);
            }
        } catch (e) {
            it.return?.().catch(() => {});
            throw e;
        }
        for (const [name, value] of headers) {
            req.header.set(name, value);
        }

        const init: RequestInit & {duplex?: "half"} = {
            method: req.method,
            headers: req.header,
            redirect: "error",
            signal: req.signal,
        };
        if (enveloped) {
            init.body = bodyStream(first, it);
            init.duplex = "half";
        } else if (req.body !== undefined) {
            init.body = whole;
        }
        const res = await (fetchFn ?? globalThis.fetch)(req.url, init);
        return decodedResponse(res);
    };
}

// fetch decodes a compressed response but keeps its Content-Encoding and Content-Length. connect
// would take the former for a compression it never negotiated and fail the call.
function decodedResponse(res: Response): UniversalClientResponse {
    const uRes = universalClientResponseFromFetch(res);
    if (!res.headers.has("Content-Encoding")) {
        return uRes;
    }
    const header = new Headers(res.headers);
    header.delete("Content-Encoding");
    header.delete("Content-Length");
    return {...uRes, header};
}

// gRPC-Web does not match: it is signed whole.
function isEnveloped(contentType: string | null): boolean {
    const mediaType = (contentType ?? "").split(";")[0].trim().toLowerCase();
    return mediaType.startsWith("application/connect+")
        || mediaType === "application/grpc"
        || mediaType.startsWith("application/grpc+");
}

// connect-es yields one complete envelope per chunk (transformJoinEnvelopes). Checked, not trusted:
// if that ever changes, the call fails instead of signing bytes that are not the first envelope.
function requireOneEnvelope(chunk: Uint8Array): void {
    const size = chunk.byteLength >= 5
        ? 5 + new DataView(chunk.buffer, chunk.byteOffset, chunk.byteLength).getUint32(1)
        : -1;
    if (size !== chunk.byteLength) {
        throw new ConnectError("the first request chunk is not one complete envelope", Code.Internal);
    }
}

// connect-es sends a unary body as one chunk, but that is not relied on.
async function readAll(it: AsyncIterator<Uint8Array>): Promise<Uint8Array<ArrayBuffer>> {
    const chunks: Uint8Array[] = [];
    let size = 0;
    for (let r = await it.next(); r.done !== true; r = await it.next()) {
        chunks.push(r.value);
        size += r.value.byteLength;
    }
    const body = new Uint8Array(size);
    let at = 0;
    for (const chunk of chunks) {
        body.set(chunk, at);
        at += chunk.byteLength;
    }
    return body;
}

function bodyStream(first: Uint8Array | undefined, it: AsyncIterator<Uint8Array>): ReadableStream<Uint8Array> {
    let pending = first;
    let done = first === undefined;
    return new ReadableStream<Uint8Array>({
        async pull(controller) {
            if (pending !== undefined) {
                controller.enqueue(pending);
                pending = undefined;
                return;
            }
            const r = done ? undefined : await it.next();
            if (r === undefined || r.done === true) {
                done = true;
                controller.close();
                return;
            }
            controller.enqueue(r.value);
        },
        async cancel() {
            await it.return?.();
        },
    });
}

function untilAborted<T>(promise: Promise<T>, signal: AbortSignal | undefined): Promise<T> {
    if (signal === undefined) {
        return promise;
    }
    return new Promise<T>((resolve, reject) => {
        // connect aborts the signal with a ConnectError (deadline_exceeded on timeout).
        const onAbort = () => reject(signal.reason);
        if (signal.aborted) {
            onAbort();
        }
        signal.addEventListener("abort", onAbort, {once: true});
        promise.then(resolve, reject).finally(() => signal.removeEventListener("abort", onAbort));
    });
}

const emptyBody: AsyncIterable<Uint8Array> = {
    [Symbol.asyncIterator]: () => ({next: async () => ({done: true, value: undefined})}),
};

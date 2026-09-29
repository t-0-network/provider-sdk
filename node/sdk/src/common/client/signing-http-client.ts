import {Code, ConnectError} from "@connectrpc/connect";
import {universalClientResponseFromFetch, type UniversalClientFn, type UniversalClientResponse} from "@connectrpc/connect/protocol";
import {signatureHeaders} from "./sign.js";
import type {SignerFunction} from "./client.js";

/**
 * Creates the HTTP client of the client's transport. It signs every request and picks the signed
 * bytes from the request's content type:
 *
 * - An enveloped body (Connect streaming `application/connect+*`, gRPC `application/grpc` and
 *   `application/grpc+*`) is signed over its first envelope, exactly as sent: flags, uint32be
 *   length and payload (the compressed payload when the flags say so). The client waits for the
 *   first message, signs it and sends the request at once; later messages are streamed as they
 *   come and are not covered by the signature. A client stream closed before its first message is
 *   signed over empty bytes and sent. The network rejects it.
 * - Any other body (Connect unary: `application/proto`) is signed whole and sent as one buffer.
 *
 * An enveloped body goes out as a stream (`duplex: "half"`), so the whole request is sent before
 * the response is read: bidirectional streams are not supported.
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
                // For a client stream this waits for the caller's first message, which the call's
                // deadline or signal must be able to end.
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

// fetch asks for a compressed response itself (Accept-Encoding) and returns the body decoded, but
// with the headers of the encoded one. Connect would take that Content-Encoding for a unary
// compression it never asked for and fail the call, and Content-Length counts the encoded bytes.
// Both are dropped, so that connect reads the response as fetch decoded it.
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

// isEnveloped reports whether a body of this content type is a sequence of envelopes: Connect
// streaming, or gRPC (not gRPC-Web). The same rule as the other SDKs.
function isEnveloped(contentType: string | null): boolean {
    const mediaType = (contentType ?? "").split(";")[0].trim().toLowerCase();
    return mediaType.startsWith("application/connect+")
        || mediaType === "application/grpc"
        || mediaType.startsWith("application/grpc+");
}

// connect-es hands the HTTP client one complete envelope per chunk (transformJoinEnvelopes), so the
// first chunk is the first envelope. It is checked rather than trusted: a change in that framing
// must fail the call, never sign bytes that are not the first envelope.
function requireOneEnvelope(chunk: Uint8Array): void {
    const size = chunk.byteLength >= 5
        ? 5 + new DataView(chunk.buffer, chunk.byteOffset, chunk.byteLength).getUint32(1)
        : -1;
    if (size !== chunk.byteLength) {
        throw new ConnectError("the first request chunk is not one complete envelope", Code.Internal);
    }
}

// readAll joins a unary body. connect-es hands it over as one chunk, but it is not relied on: the
// signature covers every byte that is sent.
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

// bodyStream sends the first envelope, then pulls the rest of the body one chunk at a time, as
// the connection takes it.
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
        // connect aborts a call's signal with a ConnectError: deadline_exceeded on its timeout.
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

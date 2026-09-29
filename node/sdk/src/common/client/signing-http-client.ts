import {Code, ConnectError} from "@connectrpc/connect";
import {universalClientResponseFromFetch, type UniversalClientFn} from "@connectrpc/connect/protocol";
import {signatureHeaders} from "./sign.js";
import type {SignerFunction} from "./client.js";

/**
 * Creates the HTTP client of the streaming transport. It signs each request over its first
 * envelope, exactly as sent: flags, uint32be length and payload (the compressed payload when the
 * flags say so). It waits for the first message, signs it and sends the request at once; later
 * messages are streamed as they come and are not covered by the signature.
 *
 * A client stream closed before its first message is signed over empty bytes and sent. The
 * network rejects it.
 *
 * The body goes out as a stream (`duplex: "half"`), so the whole request is sent before the
 * response is read: bidirectional streams are not supported.
 *
 * @param fetchFn defaults to the global fetch at the time of each call.
 */
export function createSigningFetchClient(signer: SignerFunction, fetchFn?: typeof globalThis.fetch): UniversalClientFn {
    return async (req) => {
        const it = (req.body ?? emptyBody)[Symbol.asyncIterator]();

        let first: Uint8Array | undefined;
        let headers: [string, string][];
        try {
            // For a client stream this waits for the caller's first message, which the call's
            // deadline or signal must be able to end.
            const r = await untilAborted(it.next(), req.signal);
            if (r.done !== true) {
                first = r.value;
                requireOneEnvelope(first);
            }
            headers = await signatureHeaders(signer, first ?? new Uint8Array(0));
        } catch (e) {
            it.return?.().catch(() => {});
            throw e;
        }
        for (const [name, value] of headers) {
            req.header.set(name, value);
        }

        const init: RequestInit & {duplex: "half"} = {
            method: req.method,
            headers: req.header,
            body: bodyStream(first, it),
            duplex: "half",
            signal: req.signal,
        };
        const res = await (fetchFn ?? globalThis.fetch)(req.url, init);
        return universalClientResponseFromFetch(res);
    };
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

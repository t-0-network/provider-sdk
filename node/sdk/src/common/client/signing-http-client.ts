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

        let first: FirstEnvelope;
        let headers: [string, string][];
        try {
            // For a client stream this waits for the caller's first message, which the call's
            // deadline or signal must be able to end.
            first = await untilAborted(readFirstEnvelope(it), req.signal);
            headers = await signatureHeaders(signer, first.envelope);
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

interface FirstEnvelope {
    // The signed bytes: the first envelope, or nothing if the body has no messages.
    envelope: Uint8Array;
    // Bytes read past the first envelope. Connect yields one envelope per chunk, so this is
    // normally empty.
    rest: Uint8Array;
    // Whether the body has ended.
    done: boolean;
}

// readFirstEnvelope reads the body up to the end of its first envelope, as the length in its
// 5-byte prefix gives it, whatever the chunking.
async function readFirstEnvelope(it: AsyncIterator<Uint8Array>): Promise<FirstEnvelope> {
    const chunks: Uint8Array[] = [];
    let size = 0;
    let end = -1;
    for (;;) {
        if (end < 0 && size >= 5) {
            const head = joined(chunks, size);
            end = 5 + new DataView(head.buffer, head.byteOffset, head.byteLength).getUint32(1);
        }
        if (end >= 0 && size >= end) {
            const bytes = joined(chunks, size);
            return {envelope: bytes.subarray(0, end), rest: bytes.subarray(end), done: false};
        }

        const r = await it.next();
        if (r.done === true) {
            if (size === 0) {
                return {envelope: new Uint8Array(0), rest: new Uint8Array(0), done: true};
            }
            throw new ConnectError("streaming request ends inside its first message", Code.InvalidArgument);
        }
        chunks.push(r.value);
        size += r.value.byteLength;
    }
}

// joined concatenates chunks in place, so that they are copied at most once per call.
function joined(chunks: Uint8Array[], size: number): Uint8Array {
    if (chunks.length > 1) {
        chunks.splice(0, chunks.length, Buffer.concat(chunks, size));
    }
    return chunks[0];
}

// bodyStream sends the first envelope, then pulls the rest of the body one chunk at a time, as
// the connection takes it.
function bodyStream(first: FirstEnvelope, it: AsyncIterator<Uint8Array>): ReadableStream<Uint8Array> {
    const pending = [first.envelope, first.rest].filter((chunk) => chunk.byteLength > 0);
    let done = first.done;
    return new ReadableStream<Uint8Array>({
        async pull(controller) {
            const chunk = pending.shift();
            if (chunk !== undefined) {
                controller.enqueue(chunk);
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

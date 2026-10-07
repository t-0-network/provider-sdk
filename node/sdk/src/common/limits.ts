/** How far X-Signature-Timestamp may be from the server's clock, either way. Fixed: no option changes it. */
export const TIMESTAMP_WINDOW_MS = 60_000;

/**
 * The largest request body a provider server accepts unless `maxBodySize` sets another: the whole
 * HTTP body of a unary call, gRPC prefix included.
 */
export const DEFAULT_MAX_BODY_SIZE = 10 * 1024 * 1024; // 10 MiB

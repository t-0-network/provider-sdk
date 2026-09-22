/**
 * Minimal logger contract accepted by the SDK. Providers may pass `console`
 * directly, or adapt their preferred logger (e.g. pino) with:
 *
 *   { error: (msg, fields) => pinoInstance.error(fields, msg) }
 */
export interface Logger {
  error(msg: string, fields?: Record<string, unknown>): void;
}

export const defaultLogger: Logger = {
  error: (msg, fields) =>
    // eslint-disable-next-line no-console
    console.error(JSON.stringify({ msg, ...(fields ?? {}) })),
};

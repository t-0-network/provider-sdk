import { resolve } from "node:path";
import { config as loadDotenv } from "dotenv";

const SANDBOX_ENDPOINT = "https://api-sandbox.t-0.network";

const NETWORK_PUBLIC_KEY_HELP =
  "Ask the t-0 team for the network public key and put it in .env.";

const PORT_HELP = "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";

/** Configuration is missing or unusable. The process prints ERROR and this help, then exits 1. */
export class ConfigurationError extends Error {
  readonly help: string;

  constructor(message: string, help: string) {
    super(message);
    this.name = "ConfigurationError";
    this.help = help;
  }
}

export interface Config {
  privateKey: string;
  networkPublicKey: string;
  endpoint: string;
  port: number;
}

function privateKeyHelp(envPath: string): string {
  return ".env is read from the working directory, and we looked in " + envPath + ". " +
    "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
    "Only a project with no .env at all starts one from .env.example — an existing .env holds the key " +
    "generated for you, and its private half is not recoverable.";
}

function parsePort(raw: string | undefined): number {
  const trimmed = (raw ?? "").trim();
  if (trimmed === "") {
    return 8080;
  }
  const port = Number(trimmed);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ConfigurationError(`PORT is not a valid port number: ${trimmed}`, PORT_HELP);
  }
  return port;
}

export function loadConfig(): Config {
  // quiet: dotenv prints no line of its own. override: false keeps the process environment first.
  const loaded = loadDotenv({ quiet: true, override: false });
  let envPath = resolve(".env");
  if (loaded.error !== undefined) {
    if (loaded.error.code !== "ENOENT") {
      throw loaded.error;
    }
    envPath = resolve((loaded.error as NodeJS.ErrnoException).path ?? ".env");
    console.error(`No .env at ${envPath} — taking configuration from the environment instead`);
  }

  const privateKey = (process.env.PROVIDER_PRIVATE_KEY ?? "").trim();
  const networkPublicKey = (process.env.NETWORK_PUBLIC_KEY ?? "").trim();
  const endpointRaw = (process.env.TZERO_ENDPOINT ?? "").trim();
  const endpoint = endpointRaw === "" ? SANDBOX_ENDPOINT : endpointRaw;

  if (privateKey === "") {
    throw new ConfigurationError("PROVIDER_PRIVATE_KEY is not set", privateKeyHelp(envPath));
  }
  if (networkPublicKey === "") {
    throw new ConfigurationError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP);
  }
  const port = parsePort(process.env.PORT);

  return { privateKey, networkPublicKey, endpoint, port };
}

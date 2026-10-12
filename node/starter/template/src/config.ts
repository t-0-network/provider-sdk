import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";
import { config as loadDotenv } from "dotenv";
import { publicKeyFromPrivateKey } from "@t-0/provider-sdk";

const SANDBOX_ENDPOINT = "https://api-sandbox.t-0.network";

export const NETWORK_PUBLIC_KEY_HELP =
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
  /** Derived at load so a bad private key fails before any client or server is built. */
  publicKey: string;
}

// The same file dotenv will read: DOTENV_PATH, else DOTENV_CONFIG_PATH, else ./.env.
// A leading ~ is expanded the way dotenv expands it. The result is absolute.
function envFilePath(): string {
  const selected = process.env.DOTENV_PATH != null
    ? process.env.DOTENV_PATH
    : process.env.DOTENV_CONFIG_PATH != null
      ? process.env.DOTENV_CONFIG_PATH
      : undefined;
  if (selected == null) {
    return resolve(process.cwd(), ".env");
  }
  const expanded = selected.startsWith("~") ? join(homedir(), selected.slice(1)) : selected;
  return resolve(expanded);
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
  if (!/^[0-9]+$/.test(trimmed)) {
    throw new ConfigurationError(`PORT is not a valid port number: ${trimmed}`, PORT_HELP);
  }
  const port = Number(trimmed);
  if (port < 1 || port > 65535) {
    throw new ConfigurationError(`PORT is not a valid port number: ${trimmed}`, PORT_HELP);
  }
  return port;
}

export function loadConfig(): Config {
  const envPath = envFilePath();
  if (!existsSync(envPath)) {
    console.error(`No .env at ${envPath} — taking configuration from the environment instead`);
  }
  // quiet: the library must not print its own line. override: false states the rule
  // that the process environment wins over the file. Do not change dotenv itself.
  const loaded = loadDotenv({ quiet: true, override: false });
  if (loaded.error !== undefined && loaded.error.code !== "ENOENT") {
    throw loaded.error;
  }

  const privateKey = (process.env.PROVIDER_PRIVATE_KEY ?? "").trim();
  const networkPublicKey = (process.env.NETWORK_PUBLIC_KEY ?? "").trim();
  const endpointRaw = (process.env.TZERO_ENDPOINT ?? "").trim();
  const endpoint = endpointRaw === "" ? SANDBOX_ENDPOINT : endpointRaw;
  const port = parsePort(process.env.PORT);

  if (privateKey === "") {
    throw new ConfigurationError("PROVIDER_PRIVATE_KEY is not set", privateKeyHelp(envPath));
  }
  if (networkPublicKey === "") {
    throw new ConfigurationError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP);
  }

  let publicKey: string;
  try {
    publicKey = publicKeyFromPrivateKey(privateKey);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new ConfigurationError(
      `PROVIDER_PRIVATE_KEY is not usable: ${message}`,
      "Any 32 random bytes will do: openssl rand -hex 32.",
    );
  }

  return { privateKey, networkPublicKey, endpoint, port, publicKey };
}

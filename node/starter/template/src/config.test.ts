import assert from "node:assert/strict";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";
import { publicKeyFromPrivateKey } from "@t-0/provider-sdk";
import { ConfigurationError, loadConfig } from "./config.js";

const PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
const NETWORK_KEY =
  "0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b";
const SANDBOX = "https://api-sandbox.t-0.network";
const PORT_HELP = "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.";
const NETWORK_HELP = "Ask the t-0 team for the network public key and put it in .env.";
const PRIVATE_KEY_HELP_TAIL =
  "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
  "Only a project with no .env at all starts one from .env.example — an existing .env holds the key " +
  "generated for you, and its private half is not recoverable.";

const MANAGED = [
  "PROVIDER_PRIVATE_KEY",
  "NETWORK_PUBLIC_KEY",
  "TZERO_ENDPOINT",
  "PORT",
  "DOTENV_PATH",
  "DOTENV_CONFIG_PATH",
  "DOTENV_ENCODING",
  "DOTENV_CONFIG_ENCODING",
  "DOTENV_QUIET",
  "DOTENV_CONFIG_QUIET",
  "DOTENV_DEBUG",
  "DOTENV_CONFIG_DEBUG",
  "DOTENV_OVERRIDE",
  "DOTENV_CONFIG_OVERRIDE",
  "DOTENV_FAST",
  "DOTENV_CONFIG_FAST",
] as const;

interface Row {
  name: string;
  env: Record<string, string | undefined>;
  privateKey?: string;
  networkPublicKey?: string;
  port?: number;
  endpoint?: string;
  error?: string;
  help?: string;
  notice?: string;
}

test("configuration", () => {
  let sdkMessage = "";
  try {
    publicKeyFromPrivateKey("not-a-key");
  } catch (error) {
    sdkMessage = error instanceof Error ? error.message : String(error);
  }
  assert.notEqual(sdkMessage, "");

  const dir = mkdtempSync(join(tmpdir(), "provider-config-"));
  const keys = {
    PROVIDER_PRIVATE_KEY: PRIVATE_KEY,
    NETWORK_PUBLIC_KEY: NETWORK_KEY,
  };

  const rows: Row[] = [
    {
      name: "both keys trimmed",
      env: {
        ...keys,
        PROVIDER_PRIVATE_KEY: `  ${PRIVATE_KEY}  `,
        NETWORK_PUBLIC_KEY: `  ${NETWORK_KEY}  `,
      },
      privateKey: PRIVATE_KEY,
      networkPublicKey: NETWORK_KEY,
      port: 8080,
      endpoint: SANDBOX,
    },
    {
      name: "blank private key refused",
      env: { ...keys, PROVIDER_PRIVATE_KEY: "   " },
      error: "PROVIDER_PRIVATE_KEY is not set",
    },
    {
      name: "blank network key refused",
      env: { ...keys, NETWORK_PUBLIC_KEY: "   " },
      error: "NETWORK_PUBLIC_KEY is not set",
      help: NETWORK_HELP,
    },
    {
      name: "bad private key",
      env: { ...keys, PROVIDER_PRIVATE_KEY: "not-a-key" },
      error: `PROVIDER_PRIVATE_KEY is not usable: ${sdkMessage}`,
      help: "Any 32 random bytes will do: openssl rand -hex 32.",
    },
    {
      name: "PORT blank",
      env: { ...keys, PORT: "" },
      port: 8080,
    },
    {
      name: "PORT 8080",
      env: { ...keys, PORT: "8080" },
      port: 8080,
    },
    {
      name: "PORT surrounded by spaces",
      env: { ...keys, PORT: " 8080 " },
      port: 8080,
    },
    {
      name: "PORT 0",
      env: { ...keys, PORT: "0" },
      error: "PORT is not a valid port number: 0",
      help: PORT_HELP,
    },
    {
      name: "PORT http",
      env: { ...keys, PORT: "http" },
      error: "PORT is not a valid port number: http",
      help: PORT_HELP,
    },
    {
      name: "blank TZERO_ENDPOINT",
      env: { ...keys, TZERO_ENDPOINT: "   " },
      endpoint: SANDBOX,
    },
    {
      name: "missing .env notice",
      env: { ...keys },
      notice: "",
      port: 8080,
      endpoint: SANDBOX,
    },
  ];

  const previousCwd = process.cwd();
  const previousEnv = new Map<string, string | undefined>();
  for (const key of MANAGED) {
    previousEnv.set(key, process.env[key]);
  }
  const originalError = console.error;
  process.chdir(dir);
  const envPath = resolve(".env");
  const missingNotice = `No .env at ${envPath} — taking configuration from the environment instead`;
  const privateKeyHelp =
    `.env is read from the working directory, and we looked in ${envPath}. ${PRIVATE_KEY_HELP_TAIL}`;
  for (const row of rows) {
    if (row.error === "PROVIDER_PRIVATE_KEY is not set") {
      row.help = privateKeyHelp;
    }
    if (row.notice !== undefined) {
      row.notice = missingNotice;
    }
  }
  try {
    for (const row of rows) {
      for (const key of MANAGED) {
        delete process.env[key];
      }
      for (const [key, value] of Object.entries(row.env)) {
        if (value !== undefined) {
          process.env[key] = value;
        }
      }
      const lines: string[] = [];
      console.error = ((line?: unknown, ...rest: unknown[]) => {
        lines.push([line, ...rest].map((part) => String(part)).join(" "));
      }) as typeof console.error;
      try {
        if (row.error !== undefined) {
          assert.throws(
            () => loadConfig(),
            (error: unknown) => {
              assert.ok(error instanceof ConfigurationError, row.name);
              assert.equal(error.message, row.error, row.name);
              if (row.help !== undefined) {
                assert.equal(error.help, row.help, row.name);
              }
              return true;
            },
          );
        } else {
          const config = loadConfig();
          if (row.privateKey !== undefined) {
            assert.equal(config.privateKey, row.privateKey, row.name);
            assert.equal(config.publicKey, publicKeyFromPrivateKey(row.privateKey), row.name);
          }
          if (row.networkPublicKey !== undefined) {
            assert.equal(config.networkPublicKey, row.networkPublicKey, row.name);
          }
          if (row.port !== undefined) {
            assert.equal(config.port, row.port, row.name);
          }
          if (row.endpoint !== undefined) {
            assert.equal(config.endpoint, row.endpoint, row.name);
          }
        }
        if (row.notice !== undefined) {
          assert.deepEqual(lines, [row.notice], row.name);
        }
      } finally {
        console.error = originalError;
      }
    }
  } finally {
    console.error = originalError;
    process.chdir(previousCwd);
    for (const [key, value] of previousEnv) {
      if (value === undefined) {
        delete process.env[key];
      } else {
        process.env[key] = value;
      }
    }
  }
});

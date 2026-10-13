"""Configuration loading from environment variables."""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

NETWORK_PUBLIC_KEY_HELP = "Ask the t-0 team for the network public key and put it in .env."
PORT_HELP = "Set PORT to an integer between 1 and 65535, or leave it unset for 8080."
_SANDBOX_ENDPOINT = "https://api-sandbox.t-0.network"


class ConfigurationError(Exception):
    """Configuration is missing or unusable. ``main`` prints the message and the help line."""

    def __init__(self, message: str, help_text: str) -> None:
        super().__init__(message)
        self.help_text = help_text


@dataclass(frozen=True)
class Config:
    network_public_key: str
    provider_private_key: str
    tzero_endpoint: str
    port: int
    quote_publishing_interval_ms: int


def _parse_positive_int(value: str, *, default: int) -> int:
    """A positive integer, else the default."""
    try:
        parsed = int(value)
    except (ValueError, TypeError):
        return default
    return parsed if parsed > 0 else default


def _parse_port(value: str) -> int:
    """PORT: an empty value means 8080. Otherwise an integer from 1 to 65535."""
    trimmed = value.strip()
    if not trimmed:
        return 8080
    try:
        port = int(trimmed)
    except ValueError:
        port = 0
    if not 1 <= port <= 65535:
        raise ConfigurationError(f"PORT is not a valid port number: {trimmed}", PORT_HELP)
    return port


def _private_key_missing_help(env_path: Path) -> str:
    return (
        f".env is read from the working directory, and we looked in {env_path}. "
        "Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY "
        "in the environment. Only a project with no .env at all starts one from .env.example "
        "— an existing .env holds the key generated for you, and its private half is not recoverable."
    )


def load_config() -> Config:
    """Load configuration from .env and the environment.

    The process environment wins over the file. A missing .env is not a failure.
    """
    env_path = Path(".env").resolve()
    if env_path.exists():
        # override stays false: a variable already in the environment is kept.
        load_dotenv(env_path)
    else:
        print(
            f"No .env at {env_path} — taking configuration from the environment instead",
            file=sys.stderr,
        )

    provider_private_key = os.environ.get("PROVIDER_PRIVATE_KEY", "").strip()
    network_public_key = os.environ.get("NETWORK_PUBLIC_KEY", "").strip()

    if not provider_private_key:
        raise ConfigurationError("PROVIDER_PRIVATE_KEY is not set", _private_key_missing_help(env_path))

    if not network_public_key:
        raise ConfigurationError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP)

    # An empty value counts as unset. Whitespace is trimmed first.
    tzero_endpoint = os.getenv("TZERO_ENDPOINT", "").strip() or _SANDBOX_ENDPOINT

    return Config(
        network_public_key=network_public_key,
        provider_private_key=provider_private_key,
        tzero_endpoint=tzero_endpoint,
        port=_parse_port(os.getenv("PORT", "")),
        quote_publishing_interval_ms=_parse_positive_int(os.getenv("QUOTE_PUBLISHING_INTERVAL", "5000"), default=5000),
    )

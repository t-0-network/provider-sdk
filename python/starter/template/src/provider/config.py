"""Configuration loading from environment variables."""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass

from dotenv import load_dotenv


@dataclass(frozen=True)
class Config:
    network_public_key: str
    provider_private_key: str
    tzero_endpoint: str
    port: int
    quote_publishing_interval_ms: int


def _parse_positive_int(value: str, *, default: int) -> int:
    """An integer from 1 to 2147483647, else the default."""
    try:
        parsed = int(value)
    except (ValueError, TypeError):
        return default
    return parsed if 0 < parsed <= 2_147_483_647 else default


def _parse_port(value: str) -> int:
    """PORT: decimal digits, 1 to 65535. Unset or empty means 8080."""
    if not value:
        return 8080
    if not (value.isascii() and value.isdigit() and 1 <= int(value) <= 65535):
        print(f"Error: PORT must be an integer from 1 to 65535, got {value!r}", file=sys.stderr)
        sys.exit(1)
    return int(value)


def load_config() -> Config:
    """Load configuration from .env file and environment variables."""
    load_dotenv(".env")

    provider_private_key = os.getenv("PROVIDER_PRIVATE_KEY", "").strip()
    if not provider_private_key or provider_private_key == "your_private_key_here":
        print("Error: PROVIDER_PRIVATE_KEY is not set in .env", file=sys.stderr)
        sys.exit(1)

    network_public_key = os.getenv("NETWORK_PUBLIC_KEY", "").strip()
    if not network_public_key:
        print("Error: NETWORK_PUBLIC_KEY is not set in .env", file=sys.stderr)
        sys.exit(1)

    return Config(
        network_public_key=network_public_key,
        provider_private_key=provider_private_key,
        # An empty value counts as unset.
        tzero_endpoint=os.getenv("TZERO_ENDPOINT") or "https://api-sandbox.t-0.network",
        port=_parse_port(os.getenv("PORT", "")),
        quote_publishing_interval_ms=_parse_positive_int(os.getenv("QUOTE_PUBLISHING_INTERVAL", "5000"), default=5000),
    )

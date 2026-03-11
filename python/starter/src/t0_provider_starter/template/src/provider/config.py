"""Configuration loading from environment variables.

Go equivalent: cmd/main.go → Config struct + loadConfig()
"""

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


def load_config() -> Config:
    """Load configuration from .env file and environment variables."""
    load_dotenv(".env")

    provider_private_key = os.getenv("PROVIDER_PRIVATE_KEY", "")
    if not provider_private_key or provider_private_key == "your_private_key_here":
        print("Error: PROVIDER_PRIVATE_KEY is not set in .env", file=sys.stderr)
        sys.exit(1)

    return Config(
        network_public_key=os.getenv("NETWORK_PUBLIC_KEY", ""),
        provider_private_key=provider_private_key,
        tzero_endpoint=os.getenv("TZERO_ENDPOINT", "https://api-sandbox.t-0.network"),
        port=int(os.getenv("PORT", "8080")),
    )

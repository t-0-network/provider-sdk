"""Generic client factory for creating ConnectRPC clients with signing transport.

Go equivalent: network/client.go → NewServiceClient[T]

Proto-agnostic: works with ANY generated ConnectRPC client class.
"""

from __future__ import annotations

from typing import TypeVar

from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.network.options import DEFAULT_BASE_URL, DEFAULT_TIMEOUT
from t0_provider_sdk.network.signing import SigningClient, SigningSyncClient

T = TypeVar("T")


def new_service_client(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
) -> T:
    """Create an async ConnectRPC client with signing transport.

    Args:
        private_key: Hex-encoded secp256k1 private key (with or without 0x prefix).
        client_class: Generated ConnectRPC async client class (e.g. NetworkServiceClient).
        base_url: Base URL of the T-0 Network API.
        timeout: Request timeout in seconds.

    Returns:
        An instance of client_class configured with signing transport.
    """
    sign_fn = new_signer_from_hex(private_key)
    signing_client = SigningClient(sign_fn)
    return client_class(base_url, http_client=signing_client, timeout_ms=int(timeout * 1000))  # type: ignore[call-arg]


def new_service_client_sync(
    private_key: str,
    client_class: type[T],
    *,
    base_url: str = DEFAULT_BASE_URL,
    timeout: float = DEFAULT_TIMEOUT,
) -> T:
    """Create a sync ConnectRPC client with signing transport.

    Args:
        private_key: Hex-encoded secp256k1 private key (with or without 0x prefix).
        client_class: Generated ConnectRPC sync client class (e.g. NetworkServiceClientSync).
        base_url: Base URL of the T-0 Network API.
        timeout: Request timeout in seconds.

    Returns:
        An instance of client_class configured with signing transport.
    """
    sign_fn = new_signer_from_hex(private_key)
    signing_client = SigningSyncClient(sign_fn)
    return client_class(base_url, http_client=signing_client, timeout_ms=int(timeout * 1000))  # type: ignore[call-arg]

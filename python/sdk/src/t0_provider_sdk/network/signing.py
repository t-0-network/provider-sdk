"""Signing HTTP transport wrappers for pyqwest Client and SyncClient.

These wrappers intercept outgoing requests to add T-0 Network signature headers
before delegating to the underlying pyqwest client. ConnectRPC uses exactly
three methods on the client: get(), post(), and stream().

Go equivalent: network/signing_transport.go → SigningTransport.RoundTrip(req)
"""

from __future__ import annotations

import struct
import time
from typing import TYPE_CHECKING, Any

import pyqwest

from t0_provider_sdk.common.headers import (
    PUBLIC_KEY_HEADER,
    SIGNATURE_HEADER,
    SIGNATURE_TIMESTAMP_HEADER,
)
from t0_provider_sdk.crypto.hash import legacy_keccak256

if TYPE_CHECKING:
    from t0_provider_sdk.crypto.signer import SignFn


def _sign_request(
    sign_fn: SignFn,
    body: bytes,
    headers: pyqwest.Headers | None,
) -> pyqwest.Headers:
    """Compute signature and add signing headers.

    Protocol:
    1. timestamp_ms = current time in milliseconds
    2. timestamp_le = little-endian uint64 encoding of timestamp_ms (8 bytes)
    3. digest = Keccak256(body + timestamp_le)
    4. signature, public_key = sign(digest)
    5. Set X-Public-Key, X-Signature, X-Signature-Timestamp headers
    """
    timestamp_ms = int(time.time() * 1000)
    timestamp_bytes = struct.pack("<Q", timestamp_ms)
    digest = legacy_keccak256(body + timestamp_bytes)
    signature, pub_key = sign_fn(digest)

    if headers is None:
        headers = pyqwest.Headers()
    headers[PUBLIC_KEY_HEADER] = f"0x{pub_key.hex()}"
    headers[SIGNATURE_HEADER] = f"0x{signature.hex()}"
    headers[SIGNATURE_TIMESTAMP_HEADER] = str(timestamp_ms)
    return headers


class SigningClient:
    """Async signing wrapper for pyqwest.Client.

    Passed to ConnectRPC async client via http_client= parameter.
    Intercepts get(), post(), stream() to add signature headers.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.Client(transport=transport) if transport else pyqwest.Client()
        self._sign_fn = sign_fn

    async def get(self, url: str, headers: pyqwest.Headers | None = None) -> Any:
        headers = _sign_request(self._sign_fn, b"", headers)
        return await self._inner.get(url, headers=headers)

    async def post(
        self, url: str, headers: pyqwest.Headers | None = None, content: bytes | None = None
    ) -> Any:
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        return await self._inner.post(url, headers=headers, content=content)

    def stream(
        self, method: str, url: str, headers: pyqwest.Headers | None = None, content: bytes | None = None
    ) -> Any:
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        return self._inner.stream(method, url, headers=headers, content=content)


class SigningSyncClient:
    """Sync signing wrapper for pyqwest.SyncClient.

    Passed to ConnectRPC sync client via http_client= parameter.
    Intercepts get(), post(), stream() to add signature headers.
    """

    def __init__(self, sign_fn: SignFn, *, transport: Any | None = None) -> None:
        self._inner = pyqwest.SyncClient(transport=transport) if transport else pyqwest.SyncClient()
        self._sign_fn = sign_fn

    def get(self, url: str, headers: pyqwest.Headers | None = None, timeout: float | None = None) -> Any:
        headers = _sign_request(self._sign_fn, b"", headers)
        return self._inner.get(url, headers=headers, timeout=timeout)

    def post(
        self,
        url: str,
        headers: pyqwest.Headers | None = None,
        content: bytes | None = None,
        timeout: float | None = None,
    ) -> Any:
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        return self._inner.post(url, headers=headers, content=content, timeout=timeout)

    def stream(
        self,
        method: str,
        url: str,
        headers: pyqwest.Headers | None = None,
        content: bytes | None = None,
        timeout: float | None = None,
    ) -> Any:
        body = content or b""
        headers = _sign_request(self._sign_fn, body, headers)
        return self._inner.stream(method, url, headers=headers, content=content, timeout=timeout)

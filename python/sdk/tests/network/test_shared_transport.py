"""The pyqwest transports the signing clients build for themselves.

A transport built explicitly trusts no CA certificates unless told to, so without the system
certificates every https:// call fails with "invalid peer certificate: UnknownIssuer". The
certificates are loaded by the platform's verifier (the macOS keychain ignores SSL_CERT_FILE), so
a local test CA cannot stand in for them here; the test checks what the SDK asks pyqwest for.
"""

from __future__ import annotations

import pyqwest
import pytest
from t0_provider_sdk.network import signing


@pytest.mark.parametrize("http2", [False, True])
@pytest.mark.parametrize("sync", [False, True])
def test_transport_trusts_system_certs_and_follows_no_redirects(
    monkeypatch: pytest.MonkeyPatch, sync: bool, http2: bool
) -> None:
    built: list[dict] = []

    class Recording:
        def __init__(self, **options) -> None:
            built.append(options)

    monkeypatch.setattr(pyqwest, "SyncHTTPTransport" if sync else "HTTPTransport", Recording)
    signing._shared_transport.__wrapped__(sync=sync, http2=http2)  # not the cached one

    assert built == [
        {
            "http_version": pyqwest.HTTPVersion.HTTP2 if http2 else None,
            "follow_redirects": False,
            "tls_include_system_certs": True,
        }
    ]

"""Tests for the network public key check in new_asgi_app / new_wsgi_app.

Mirrors Go's handler_test.go: a missing or malformed key fails at startup.
"""

import pytest
from t0_provider_sdk.provider import NetworkPublicKeyRequiredError, new_asgi_app, new_wsgi_app

PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

BUILDERS = [pytest.param(new_asgi_app, id="asgi"), pytest.param(new_wsgi_app, id="wsgi")]


@pytest.mark.parametrize("build", BUILDERS)
@pytest.mark.parametrize("key", ["", "  \n"], ids=["empty", "whitespace only"])
def test_missing_key_is_rejected(build, key):
    with pytest.raises(NetworkPublicKeyRequiredError, match="network public key is not set"):
        build(key)


@pytest.mark.parametrize("build", BUILDERS)
@pytest.mark.parametrize(
    "key",
    ["0xnot-a-key", "0x", PUBLIC_KEY[:-2], "0x04" + "00" * 64],
    ids=["non-hex", "prefix only", "truncated", "off-curve"],
)
def test_malformed_key_is_rejected(build, key):
    with pytest.raises(ValueError, match="invalid network public key") as exc_info:
        build(key)
    assert not isinstance(exc_info.value, NetworkPublicKeyRequiredError)


@pytest.mark.parametrize("build", BUILDERS)
@pytest.mark.parametrize("key", [PUBLIC_KEY, f"  {PUBLIC_KEY}\n"], ids=["valid", "valid with surrounding whitespace"])
def test_valid_key_builds_an_app(build, key):
    assert callable(build(key))

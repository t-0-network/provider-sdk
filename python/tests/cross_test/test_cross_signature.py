"""Cross-language interoperability tests between Python and Go SDKs.

These tests verify that:
1. Both SDKs produce the same Keccak256 hash for the same input
2. Python can verify signatures produced by Go
3. Go can verify signatures produced by Python
4. Public key derivation is identical

Requires the Go helper binary to be built:
    cd tests/cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

import pytest

from t0_provider_sdk.crypto.hash import legacy_keccak256
from t0_provider_sdk.crypto.keys import private_key_from_hex, public_key_to_bytes
from t0_provider_sdk.crypto.signer import new_signer_from_hex
from t0_provider_sdk.crypto.verifier import verify_signature

GO_HELPER = Path(__file__).parent / "go_helper" / "go_helper"

# Shared test vectors (same as Go SDK tests)
PRIVATE_KEY = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"
EXPECTED_PUBLIC_KEY = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0"

SECOND_PRIVATE_KEY = "0x691db48202ca70d83cc7f5f3aa219536f9bb2dfe12ebb78a7bb634544858ee92"
SECOND_PUBLIC_KEY = "0x049bb924680bfba3f64d924bf9040c45dcc215b124b5b9ee73ca8e32c050d042c0bbd8dbb98e3929ed5bc2967f28c3a3b72dd5e24312404598bbf6c6cc47708dc7"


def _go_helper(*args: str) -> str:
    """Run the Go helper binary and return stdout."""
    result = subprocess.run(
        [str(GO_HELPER), *args],
        capture_output=True,
        text=True,
        timeout=10,
    )
    if result.returncode != 0:
        raise RuntimeError(f"Go helper failed: {result.stderr}")
    return result.stdout.strip()


def _go_available() -> bool:
    """Check if the Go helper binary is available."""
    return GO_HELPER.exists() and os.access(GO_HELPER, os.X_OK)


pytestmark = pytest.mark.skipif(
    not _go_available(),
    reason=f"Go helper binary not found at {GO_HELPER}. Build with: cd tests/cross_test/go_helper && go build -o go_helper .",
)


class TestCrossHash:
    """Verify Keccak256 produces identical results in Python and Go."""

    @pytest.mark.parametrize(
        "data_hex",
        [
            "68656c6c6f",  # "hello"
            "",  # empty
            "00",  # single zero byte
            "deadbeef",
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20",  # 32 bytes
        ],
    )
    def test_keccak256_matches(self, data_hex: str):
        """Both SDKs produce the same Keccak256 hash."""
        data = bytes.fromhex(data_hex)
        python_hash = legacy_keccak256(data)

        go_hash_hex = _go_helper("hash", f"0x{data_hex}")
        go_hash = bytes.fromhex(go_hash_hex.removeprefix("0x"))

        assert python_hash == go_hash, (
            f"Hash mismatch for input '{data_hex}':\n"
            f"  Python: 0x{python_hash.hex()}\n"
            f"  Go:     {go_hash_hex}"
        )


class TestCrossPublicKey:
    """Verify public key derivation is identical."""

    @pytest.mark.parametrize(
        "private_key,expected_public_key",
        [
            (PRIVATE_KEY, EXPECTED_PUBLIC_KEY),
            (SECOND_PRIVATE_KEY, SECOND_PUBLIC_KEY),
        ],
    )
    def test_public_key_derivation_matches(self, private_key: str, expected_public_key: str):
        """Both SDKs derive the same public key from a private key."""
        # Python
        pk = private_key_from_hex(private_key)
        python_pub = "0x" + public_key_to_bytes(pk.public_key).hex()

        # Go
        go_pub = _go_helper("pubkey", private_key)

        assert python_pub == go_pub
        assert python_pub == expected_public_key


class TestCrossSign:
    """Verify signatures are interoperable between Python and Go."""

    def test_python_sign_go_verify(self):
        """Python signs a message, Go verifies it."""
        message = b"cross-test message for Python -> Go"
        digest = legacy_keccak256(message)

        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        signature, pub_key = sign_fn(digest)

        # Go verifies Python's signature
        result = _go_helper(
            "verify",
            f"0x{pub_key.hex()}",
            f"0x{digest.hex()}",
            f"0x{signature.hex()}",
        )
        assert result == "true", "Go failed to verify Python's signature"

    def test_go_sign_python_verify(self):
        """Go signs a message, Python verifies it."""
        message = b"cross-test message for Go -> Python"
        digest = legacy_keccak256(message)

        # Go signs
        go_output = _go_helper("sign", PRIVATE_KEY, f"0x{digest.hex()}")
        lines = dict(line.split("=", 1) for line in go_output.splitlines())
        go_signature = bytes.fromhex(lines["signature"].removeprefix("0x"))
        go_pub_key = bytes.fromhex(lines["public_key"].removeprefix("0x"))

        # Python verifies Go's signature
        pk = private_key_from_hex(PRIVATE_KEY)
        result = verify_signature(pk.public_key, digest, go_signature)
        assert result, "Python failed to verify Go's signature"

        # Also verify using the Go-provided public key bytes
        from t0_provider_sdk.crypto.keys import public_key_from_bytes

        go_pub = public_key_from_bytes(go_pub_key)
        result2 = verify_signature(go_pub, digest, go_signature)
        assert result2, "Python failed to verify Go's signature (using Go pub key bytes)"

    def test_go_sign_python_verify_64_bytes(self):
        """Go signs, Python verifies using only r+s (64 bytes, no recovery ID)."""
        message = b"64-byte signature test"
        digest = legacy_keccak256(message)

        go_output = _go_helper("sign", PRIVATE_KEY, f"0x{digest.hex()}")
        lines = dict(line.split("=", 1) for line in go_output.splitlines())
        go_signature = bytes.fromhex(lines["signature"].removeprefix("0x"))

        # Strip recovery byte (use only r+s = 64 bytes)
        sig_64 = go_signature[:64]

        pk = private_key_from_hex(PRIVATE_KEY)
        result = verify_signature(pk.public_key, digest, sig_64)
        assert result, "Python failed to verify Go's 64-byte signature"

    def test_python_sign_go_verify_second_key(self):
        """Cross-verify with second key pair."""
        message = b"second key pair cross-test"
        digest = legacy_keccak256(message)

        sign_fn = new_signer_from_hex(SECOND_PRIVATE_KEY)
        signature, pub_key = sign_fn(digest)

        result = _go_helper(
            "verify",
            f"0x{pub_key.hex()}",
            f"0x{digest.hex()}",
            f"0x{signature.hex()}",
        )
        assert result == "true", "Go failed to verify Python's signature (second key)"

    def test_wrong_key_cross_verification_fails(self):
        """Signature from one key cannot be verified with another key's pub key across SDKs."""
        message = b"wrong key test"
        digest = legacy_keccak256(message)

        # Python signs with key 1
        sign_fn = new_signer_from_hex(PRIVATE_KEY)
        signature, _ = sign_fn(digest)

        # Go verifies with key 2's public key → should fail
        result = _go_helper(
            "verify",
            SECOND_PUBLIC_KEY,
            f"0x{digest.hex()}",
            f"0x{signature.hex()}",
        )
        assert result == "false", "Cross-key verification should fail"

"""Tests for Keccak256 hash function."""

import hashlib

from t0_provider_sdk.crypto.hash import legacy_keccak256


def test_keccak256_empty():
    """Empty input should produce a known Keccak256 hash."""
    result = legacy_keccak256(b"")
    # Known Keccak256 of empty string
    assert result.hex() == "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"


def test_keccak256_hello():
    """Known test vector for 'hello'."""
    result = legacy_keccak256(b"hello")
    assert result.hex() == "1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8"


def test_keccak256_is_not_sha3_256():
    """Keccak256 must NOT equal SHA3-256 (different padding)."""
    data = b"test data"
    keccak_result = legacy_keccak256(data)
    sha3_result = hashlib.sha3_256(data).digest()
    assert keccak_result != sha3_result


def test_keccak256_returns_32_bytes():
    """Output must always be 32 bytes."""
    assert len(legacy_keccak256(b"")) == 32
    assert len(legacy_keccak256(b"x" * 1000)) == 32


def test_keccak256_deterministic():
    """Same input must produce same output."""
    data = b"deterministic test"
    assert legacy_keccak256(data) == legacy_keccak256(data)

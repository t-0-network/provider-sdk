"""The Python column of the shared client behavior: every case of client_cases in
cross_test/test_vectors.json, called by new_service_client and new_service_client_sync, over Connect
and gRPC, against `go_helper client-probe`, which checks each request it gets.

Requires the Go helper binary to be built:
    cd cross_test/go_helper && go build -o go_helper .
"""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
from collections.abc import Iterator
from pathlib import Path

import pytest
from connectrpc.errors import ConnectError
from grpc_health.v1 import health_pb2
from t0_provider_sdk.crypto.signer import SignFn, new_signer_from_hex
from t0_provider_sdk.network import Protocol, new_service_client, new_service_client_sync
from t0_provider_sdk.provider.health import HealthClient, HealthClientSync

CROSS_TEST = Path(__file__).resolve().parents[3] / "cross_test"
GO_HELPER = CROSS_TEST / "go_helper" / "go_helper"
VECTORS = CROSS_TEST / "test_vectors.json"

if not (GO_HELPER.exists() and os.access(GO_HELPER, os.X_OK)) and os.environ.get("CI"):
    raise RuntimeError(f"Go helper binary required in CI but not found at {GO_HELPER}")

pytestmark = pytest.mark.skipif(not GO_HELPER.exists(), reason=f"Go helper not found at {GO_HELPER}")

_VECTORS = json.loads(VECTORS.read_text())
PRIVATE_KEY = _VECTORS["keys"]["private_key"]
IMPOSTOR_PRIVATE_KEY = _VECTORS["impostor_keys"]["private_key"]
CASES = _VECTORS["client_cases"]
PROTOCOLS = [pytest.param(Protocol.CONNECT, id="connect"), pytest.param(Protocol.GRPC, id="grpc")]


@pytest.fixture(scope="module")
def probe() -> Iterator[tuple[str, Path]]:
    """The probe's base URL, and the file its PASS and FAIL lines go to."""
    with tempfile.TemporaryDirectory() as tmp:
        log = Path(tmp) / "client-probe.log"
        with log.open("w") as stderr:
            process = subprocess.Popen(
                [str(GO_HELPER), "client-probe", "--sdk", "python", "--vectors", str(VECTORS)],
                stdout=subprocess.PIPE,
                stderr=stderr,
                text=True,
            )
        try:
            assert process.stdout is not None
            ready = process.stdout.readline().split()
            assert ready[:1] == ["READY"], f"client-probe did not start: {ready}"
            yield ready[1], log
        finally:
            process.kill()
            process.wait()


def _signer(case: dict) -> str | SignFn:
    """The client's signer argument: the hex key, the factory's signer, or a custom SignFn."""
    if case.get("signer") == "factory":
        return new_signer_from_hex(PRIVATE_KEY)
    if "custom_signer" not in case:
        return PRIVATE_KEY
    custom = case["custom_signer"]
    sign = new_signer_from_hex(IMPOSTOR_PRIVATE_KEY)

    def custom_signer(digest: bytes) -> tuple[bytes, bytes]:
        if "error" in custom:
            raise RuntimeError(custom["error"])
        signature, public_key = sign(digest)
        match custom.get("signature"):
            case "r_s":
                signature = signature[:64]
            case "v_plus_27":
                signature = signature[:64] + bytes([signature[64] + 27])
            case "first_63_bytes":
                signature = signature[:63]
        if "public_key" in custom:
            public_key = bytes.fromhex(custom["public_key"])
        return signature, public_key

    return custom_signer


def _options(case: dict, protocol: Protocol) -> dict:
    options: dict = {"protocol": protocol}
    if "client_timeout_ms" in case:
        options["timeout"] = case["client_timeout_ms"] / 1000  # seconds
    return options


def _call_options(case: dict) -> dict:
    return {"timeout_ms": case["call_timeout_ms"]} if "call_timeout_ms" in case else {}


def _outcome(response: health_pb2.HealthCheckResponse) -> tuple[str, str]:
    if response.status == health_pb2.HealthCheckResponse.SERVING:
        return "ok", ""
    return "not serving", str(response)


async def _call_async(base_url: str, case: dict, protocol: Protocol) -> tuple[str, str]:
    client = new_service_client(_signer(case), HealthClient, base_url=base_url, **_options(case, protocol))
    try:
        return _outcome(await client.check(health_pb2.HealthCheckRequest(), **_call_options(case)))
    except ConnectError as e:
        return e.code.value, e.message


def _call_sync(base_url: str, case: dict, protocol: Protocol) -> tuple[str, str]:
    client = new_service_client_sync(_signer(case), HealthClientSync, base_url=base_url, **_options(case, protocol))
    try:
        return _outcome(client.check(health_pb2.HealthCheckRequest(), **_call_options(case)))
    except ConnectError as e:
        return e.code.value, e.message


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("case", CASES, ids=lambda case: case["name"])
async def test_async_client(probe: tuple[str, Path], case: dict, protocol: Protocol) -> None:
    base_url, log = probe
    _check(case, await _call_async(f"{base_url}/{case['name']}", case, protocol), log)


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("case", CASES, ids=lambda case: case["name"])
def test_sync_client(probe: tuple[str, Path], case: dict, protocol: Protocol) -> None:
    base_url, log = probe
    _check(case, _call_sync(f"{base_url}/{case['name']}", case, protocol), log)


def _check(case: dict, outcome: tuple[str, str], log: Path) -> None:
    """The code, and the message where the case names one (an SDK-raised message)."""
    code, message = outcome
    assert code == case["expect"]["code"], f"{message}\n{log.read_text()}"
    if "message" in case["expect"]:
        assert message == case["expect"]["message"]

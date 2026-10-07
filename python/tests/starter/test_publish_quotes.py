"""The Python starter's quote publishers: a failed publish is logged and the next tick publishes
again, as in the other starters (row ST-timers.b)."""

from __future__ import annotations

import asyncio
import importlib.util
from pathlib import Path
from types import ModuleType

import pytest

STARTER = Path(__file__).resolve().parents[2] / "starter" / "template" / "src" / "provider"


def _load(name: str) -> ModuleType:
    """The starter module by its path: the starter is a project of its own, not a package here."""
    spec = importlib.util.spec_from_file_location(f"starter_{name}", STARTER / f"{name}.py")
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class _FailingOnceClient:
    """Fails the first update_quote, then counts the calls and stops the publisher at the third."""

    def __init__(self, shutdown: asyncio.Event) -> None:
        self.calls = 0
        self._shutdown = shutdown

    async def update_quote(self, request):
        self.calls += 1
        if self.calls == 1:
            raise RuntimeError("network unavailable")
        if self.calls == 3:
            self._shutdown.set()


@pytest.mark.parametrize(
    ("module", "function"),
    [("publish_quotes", "publish_quotes"), ("publish_payment_intent_quotes", "publish_payment_intent_quotes")],
)
async def test_publishing_goes_on_after_a_failure(module: str, function: str, caplog) -> None:
    publish = getattr(_load(module), function)
    shutdown = asyncio.Event()
    client = _FailingOnceClient(shutdown)

    await asyncio.wait_for(publish(client, shutdown, 0.01), timeout=5)

    assert client.calls == 3
    assert "network unavailable" in caplog.text

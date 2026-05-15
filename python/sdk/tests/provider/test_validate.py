"""Tests for the public ``validate()`` helper.

Mirrors the response-validation interceptor's error shape so providers can
call ``validate(resp)`` in their handlers and let the error propagate to the
interceptor without changing on-wire behavior.
"""

from __future__ import annotations

import pytest

# Import SDK first to ensure api/ is on sys.path (buf.validate stubs)
import t0_provider_sdk  # noqa: F401
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from t0_provider_sdk import validate as top_level_validate
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import PayoutResponse
from t0_provider_sdk.provider import validate as provider_validate
from t0_provider_sdk.provider.validate import validate


class TestValidateHelper:
    def test_returns_same_instance_on_valid(self) -> None:
        msg = Decimal(unscaled=100, exponent=2)
        assert validate(msg) is msg

    def test_returns_typed_message(self) -> None:
        msg = PayoutResponse()
        msg.accepted.CopyFrom(PayoutResponse.Accepted())
        result = validate(msg)
        assert isinstance(result, PayoutResponse)
        assert result is msg

    def test_raises_internal_on_invalid(self) -> None:
        msg = Decimal(exponent=100)  # outside [-8, 8]
        with pytest.raises(ConnectError) as exc_info:
            validate(msg)
        assert exc_info.value.code == Code.INTERNAL
        assert "response validation failed" in str(exc_info.value)

    def test_error_wording_matches_interceptor(self) -> None:
        # The on-wire error string must stay byte-identical with the
        # interceptor so propagating the error preserves wire behavior.
        msg = Decimal(exponent=100)
        with pytest.raises(ConnectError) as exc_info:
            validate(msg)
        # Same prefix as ValidationInterceptor; details follow ": ".
        assert str(exc_info.value).find("response validation failed: ") != -1

    def test_reexported_from_package(self) -> None:
        assert top_level_validate is validate
        assert provider_validate is validate


class TestHandlerPropagation:
    """Regression: a handler that calls ``validate(invalid)`` and lets the
    error propagate produces the same on-wire shape (``Code.INTERNAL`` with
    the ``"response validation failed: ..."`` wording) that the safety-net
    interceptor would have produced. Mirrors ``test_validation.py`` lines
    61-70 but exercises the propagation path through the helper instead of
    through the interceptor.
    """

    @pytest.mark.asyncio
    async def test_async_handler_propagates_internal(self) -> None:
        async def handler() -> Decimal:
            # Handler builds an invalid response and validates before
            # returning. ConnectError(Code.INTERNAL, ...) raised here
            # propagates through the call frame to the caller.
            return validate(Decimal(exponent=100))

        with pytest.raises(ConnectError) as exc_info:
            await handler()
        assert exc_info.value.code == Code.INTERNAL
        assert "response validation failed" in str(exc_info.value)

    def test_sync_handler_propagates_internal(self) -> None:
        def handler() -> Decimal:
            return validate(Decimal(exponent=100))

        with pytest.raises(ConnectError) as exc_info:
            handler()
        assert exc_info.value.code == Code.INTERNAL
        assert "response validation failed" in str(exc_info.value)

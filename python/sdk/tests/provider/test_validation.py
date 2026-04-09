"""Tests for protovalidate interceptors (server response + client request validation)."""

from __future__ import annotations

import protovalidate
import pytest

# Import SDK first to ensure api/ is on sys.path (needed for buf.validate stubs)
import t0_provider_sdk  # noqa: F401
from connectrpc.code import Code
from connectrpc.errors import ConnectError
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import (
    AppendLedgerEntriesRequest,
    PayoutResponse,
    UpdatePaymentResponse,
)
from t0_provider_sdk.network.validate_request import (
    RequestValidationInterceptor,
    RequestValidationInterceptorSync,
)
from t0_provider_sdk.provider.validate_response import (
    ValidationInterceptor,
    ValidationInterceptorSync,
)

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

class FakeContext:
    """Minimal RequestContext stub for interceptor tests."""
    pass


# ---------------------------------------------------------------------------
# Server-side: response validation
# ---------------------------------------------------------------------------

class TestResponseValidation:
    """Test that the server interceptor validates provider responses."""

    @pytest.fixture
    def interceptor(self):
        return ValidationInterceptor()

    @pytest.mark.asyncio
    async def test_valid_response_passes(self, interceptor):
        response = Decimal(unscaled=100, exponent=2)

        async def call_next(req, ctx):
            return response

        result = await interceptor.intercept_unary(call_next, None, FakeContext())
        assert result is response

    @pytest.mark.asyncio
    async def test_invalid_response_returns_internal(self, interceptor):
        response = Decimal(exponent=100)  # outside [-8, 8]

        async def call_next(req, ctx):
            return response

        with pytest.raises(ConnectError) as exc_info:
            await interceptor.intercept_unary(call_next, None, FakeContext())
        assert exc_info.value.code == Code.INTERNAL
        assert "response validation failed" in str(exc_info.value)

    @pytest.mark.asyncio
    async def test_invalid_response_exponent_too_low(self, interceptor):
        response = Decimal(exponent=-20)

        async def call_next(req, ctx):
            return response

        with pytest.raises(ConnectError) as exc_info:
            await interceptor.intercept_unary(call_next, None, FakeContext())
        assert exc_info.value.code == Code.INTERNAL

    @pytest.mark.asyncio
    async def test_empty_response_without_constraints_passes(self, interceptor):
        response = UpdatePaymentResponse()

        async def call_next(req, ctx):
            return response

        result = await interceptor.intercept_unary(call_next, None, FakeContext())
        assert result is response

    @pytest.mark.asyncio
    async def test_boundary_exponent_values_pass(self, interceptor):
        for exp in [-8, 0, 8]:
            response = Decimal(exponent=exp)

            async def call_next(req, ctx, r=response):
                return r

            result = await interceptor.intercept_unary(call_next, None, FakeContext())
            assert result.exponent == exp

    @pytest.mark.asyncio
    async def test_boundary_exponent_values_fail(self, interceptor):
        for exp in [-9, 9]:
            response = Decimal(exponent=exp)

            async def call_next(req, ctx, r=response):
                return r

            with pytest.raises(ConnectError) as exc_info:
                await interceptor.intercept_unary(call_next, None, FakeContext())
            assert exc_info.value.code == Code.INTERNAL


class TestResponseValidationSync:
    """Test the sync variant of the server interceptor."""

    @pytest.fixture
    def interceptor(self):
        return ValidationInterceptorSync()

    def test_valid_response_passes(self, interceptor):
        response = Decimal(unscaled=100, exponent=2)
        result = interceptor.intercept_unary_sync(lambda req, ctx: response, None, FakeContext())
        assert result is response

    def test_invalid_response_returns_internal(self, interceptor):
        response = Decimal(exponent=100)
        with pytest.raises(ConnectError) as exc_info:
            interceptor.intercept_unary_sync(lambda req, ctx: response, None, FakeContext())
        assert exc_info.value.code == Code.INTERNAL


# ---------------------------------------------------------------------------
# Client-side: request validation
# ---------------------------------------------------------------------------

class TestRequestValidation:
    """Test that the client interceptor validates outgoing requests."""

    @pytest.fixture
    def interceptor(self):
        return RequestValidationInterceptor()

    @pytest.mark.asyncio
    async def test_valid_request_passes(self, interceptor):
        request = Decimal(unscaled=100, exponent=2)
        called = False

        async def call_next(req, ctx):
            nonlocal called
            called = True
            return req

        await interceptor.intercept_unary(call_next, request, FakeContext())
        assert called

    @pytest.mark.asyncio
    async def test_invalid_request_returns_invalid_argument(self, interceptor):
        request = Decimal(exponent=100)

        async def call_next(req, ctx):
            return req

        with pytest.raises(ConnectError) as exc_info:
            await interceptor.intercept_unary(call_next, request, FakeContext())
        assert exc_info.value.code == Code.INVALID_ARGUMENT
        assert "request validation failed" in str(exc_info.value)

    @pytest.mark.asyncio
    async def test_invalid_request_empty_transactions(self, interceptor):
        request = AppendLedgerEntriesRequest()  # min_items: 1

        async def call_next(req, ctx):
            return req

        with pytest.raises(ConnectError) as exc_info:
            await interceptor.intercept_unary(call_next, request, FakeContext())
        assert exc_info.value.code == Code.INVALID_ARGUMENT

    @pytest.mark.asyncio
    async def test_invalid_request_does_not_call_next(self, interceptor):
        request = Decimal(exponent=100)
        called = False

        async def call_next(req, ctx):
            nonlocal called
            called = True
            return req

        with pytest.raises(ConnectError):
            await interceptor.intercept_unary(call_next, request, FakeContext())
        assert not called


class TestRequestValidationSync:
    """Test the sync variant of the client interceptor."""

    @pytest.fixture
    def interceptor(self):
        return RequestValidationInterceptorSync()

    def test_valid_request_passes(self, interceptor):
        request = Decimal(unscaled=100, exponent=2)
        result = interceptor.intercept_unary_sync(lambda req, ctx: req, request, FakeContext())
        assert result is request

    def test_invalid_request_returns_invalid_argument(self, interceptor):
        request = Decimal(exponent=100)
        with pytest.raises(ConnectError) as exc_info:
            interceptor.intercept_unary_sync(lambda req, ctx: req, request, FakeContext())
        assert exc_info.value.code == Code.INVALID_ARGUMENT


# ---------------------------------------------------------------------------
# Direct protovalidate sanity checks
# ---------------------------------------------------------------------------

class TestProtovalidateDirect:
    """Verify protovalidate catches violations on our proto messages."""

    def test_valid_decimal(self):
        protovalidate.validate(Decimal(exponent=2))

    def test_invalid_decimal(self):
        with pytest.raises(protovalidate.ValidationError):
            protovalidate.validate(Decimal(exponent=100))

    def test_valid_payout_response(self):
        msg = PayoutResponse()
        msg.accepted.CopyFrom(PayoutResponse.Accepted())
        protovalidate.validate(msg)

    def test_empty_append_ledger_request_fails(self):
        with pytest.raises(protovalidate.ValidationError):
            protovalidate.validate(AppendLedgerEntriesRequest())

"""The submit_payment demo against a recording fake client. No server, no sandbox."""

from __future__ import annotations

import asyncio
import logging
import re
import sys
import uuid
from pathlib import Path
from unittest.mock import AsyncMock

import pytest
import t0_provider_sdk  # noqa: F401  # puts the generated api/ dir on sys.path, like main.py's import chain

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from ivms101.v1.ivms.enum_pb2 import NATURAL_PERSON_NAME_TYPE_CODE_LEGL  # noqa: E402
from provider.submit_payment import submit_payment  # noqa: E402
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal  # noqa: E402
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import (  # noqa: E402
    CreatePaymentRequest,
    CreatePaymentResponse,
)


def make_client(response: CreatePaymentResponse | None = None, error: Exception | None = None) -> AsyncMock:
    client = AsyncMock()
    if error is not None:
        client.create_payment = AsyncMock(side_effect=error)
    else:
        client.create_payment = AsyncMock(return_value=response)
    return client


def run(client: AsyncMock) -> None:
    asyncio.run(submit_payment(client))


def legal_name(person) -> tuple[str, str]:
    """(primary, secondary) of the single LEGL name identifier, failing otherwise."""
    identifiers = person.natural_person.name.name_identifiers
    assert len(identifiers) == 1
    assert identifiers[0].name_identifier_type == NATURAL_PERSON_NAME_TYPE_CODE_LEGL
    return identifiers[0].primary_identifier, identifiers[0].secondary_identifier


@pytest.fixture(autouse=True)
def _capture_logs(caplog):
    caplog.set_level(logging.INFO, logger="provider.submit_payment")
    return caplog


def test_request_fields_and_one_rpc(_capture_logs):
    # The response a real accept carries: Accepted marks its amounts required.
    response = CreatePaymentResponse(
        accepted=CreatePaymentResponse.Accepted(
            payment_id=42,
            settlement_amount=Decimal(unscaled=1250, exponent=-2),  # 12.50 USD
            payout_amount=Decimal(unscaled=10, exponent=0),
            payout_provider_id=1,
        )
    )
    client = make_client(response)

    run(client)

    assert client.create_payment.await_count == 1
    request = client.create_payment.await_args.args[0]
    assert isinstance(request, CreatePaymentRequest)

    uuid.UUID(request.payment_client_id)  # a fresh UUID, raises otherwise

    assert request.amount.pay_out_amount.unscaled == 10
    assert request.amount.pay_out_amount.exponent == 0
    assert request.currency == "GBP"

    # The same checks the network's buf.validate rules apply to the SEPA demo data.
    iban = request.payment_details.sepa.iban
    assert 15 <= len(iban) <= 34
    assert re.fullmatch(r"[A-Z]{2}[0-9]{2}[A-Z0-9]+", iban)
    assert iban == "GB29NWBK60161331926819"  # the sandbox IBAN the demo pins
    assert request.payment_details.sepa.beneficiary_name == "Max Mustermann"

    assert request.HasField("travel_rule_data")  # required by the contract
    assert len(request.travel_rule_data.originator) == 1
    assert len(request.travel_rule_data.beneficiary) == 1
    assert legal_name(request.travel_rule_data.originator[0]) == ("Doe", "John")
    assert legal_name(request.travel_rule_data.beneficiary[0]) == ("Mustermann", "Max")
    # The SEPA beneficiary name and the IVMS101 beneficiary describe the same person.
    assert " ".join(reversed(legal_name(request.travel_rule_data.beneficiary[0]))) == (
        request.payment_details.sepa.beneficiary_name
    )

    assert "Payment accepted" in _capture_logs.text
    assert "id=42" in _capture_logs.text


def test_failure_result(_capture_logs):
    failure = CreatePaymentResponse.Failure(
        reason=CreatePaymentResponse.Failure.REASON_QUOTE_NOT_FOUND,
    )
    client = make_client(CreatePaymentResponse(failure=failure))

    run(client)

    assert client.create_payment.await_count == 1
    assert "Payment failed" in _capture_logs.text
    assert "REASON_QUOTE_NOT_FOUND" in _capture_logs.text


def test_unset_result_is_unknown(_capture_logs):
    # A response without a result (a deprecated or unset oneof arm) is unknown,
    # not a crash and not a silent success.
    client = make_client(CreatePaymentResponse())

    run(client)

    assert client.create_payment.await_count == 1
    assert "Unknown response type" in _capture_logs.text


def test_rpc_error_is_one_log_line(_capture_logs):
    client = make_client(error=RuntimeError("connection refused"))

    run(client)  # must not raise

    assert client.create_payment.await_count == 1  # no retry loop
    errors = [r for r in _capture_logs.records if r.levelno >= logging.ERROR]
    assert len(errors) == 1
    assert "Error submitting payment: connection refused" in errors[0].getMessage()
    assert errors[0].exc_info is None  # no stack trace

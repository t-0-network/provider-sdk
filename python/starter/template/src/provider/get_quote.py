"""Demonstrates getting a quote from the T-0 Network.

Go equivalent: internal/get_quote.go → GetQuote()
"""

from __future__ import annotations

import logging

from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.common.payment_method_pb2 import PAYMENT_METHOD_TYPE_SWIFT
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClient
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import (
    QUOTE_TYPE_REALTIME,
    GetQuoteRequest,
    PaymentAmount,
)

logger = logging.getLogger(__name__)


async def get_quote(network_client: NetworkServiceClient) -> None:
    """Request a sample quote from the network.

    TODO: Step 1.4 Verify that quotes for target currency are successfully received.
    """
    try:
        response = await network_client.get_quote(
            GetQuoteRequest(
                amount=PaymentAmount(
                    settlement_amount=Decimal(unscaled=500, exponent=0),  # amount in USD
                ),
                pay_out_currency="GBP",
                pay_out_method=PAYMENT_METHOD_TYPE_SWIFT,
                quote_type=QUOTE_TYPE_REALTIME,
            ),
        )

        if response.HasField("success"):
            logger.info(
                "Got success response with quote id: %d",
                response.success.quote_id.quote_id,
            )
        elif response.HasField("failure"):
            logger.info(
                "Got failure response with reason: %s",
                response.failure.reason,
            )
        else:
            logger.info("Unknown response type")

    except Exception:
        logger.exception("Error getting quote")

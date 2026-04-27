"""Payment intent indicative quote from the T-0 Network.

Go equivalent: internal/get_payment_intent_quote.go → GetPaymentIntentQuote()

Beneficiary Provider role — Step 3B.1. Use this to check available rates before
creating a payment intent. The actual settlement rate is determined when the
pay-in provider confirms funds received.
"""

from __future__ import annotations

import logging

from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import PaymentIntentServiceClient
from t0_provider_sdk.api.tzero.v1.payment_intent.network_pb2 import GetQuoteRequest

logger = logging.getLogger(__name__)


async def get_payment_intent_quote(payment_intent_client: PaymentIntentServiceClient) -> None:
    try:
        response = await payment_intent_client.get_quote(
            GetQuoteRequest(
                currency="EUR",
                amount=Decimal(unscaled=500, exponent=0),  # end-user pays 500 EUR
            ),
        )

        if response.HasField("success"):
            logger.info(
                "Got %d best pay-in quotes and %d total quotes",
                len(response.success.best_quotes),
                len(response.success.all_quotes),
            )
        elif response.HasField("quote_not_found"):
            logger.info("No pay-in quotes available for this currency/amount")
        else:
            logger.info("Unknown response type")
    except Exception:
        logger.exception("Error getting payment intent quote")

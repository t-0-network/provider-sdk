"""Payment intent quote publishing to the T-0 Network.

Go equivalent: internal/publish_payment_intent_quotes.go → PublishPaymentIntentQuotes()

Pay-In Provider role — Step 3A.1. Publishes sample pay-in quotes every 5 seconds.
Replace the hardcoded values with quotes from your own pricing systems.
"""

from __future__ import annotations

import asyncio
import logging
import uuid

from google.protobuf.timestamp_pb2 import Timestamp
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.common.payment_method_pb2 import PAYMENT_METHOD_TYPE_SEPA
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import PaymentIntentServiceClient
from t0_provider_sdk.api.tzero.v1.payment_intent.network_pb2 import UpdateQuoteRequest

logger = logging.getLogger(__name__)

PUBLISH_INTERVAL_SECONDS = 5


def _now_timestamp() -> Timestamp:
    ts = Timestamp()
    ts.GetCurrentTime()
    return ts


def _expiration_timestamp(seconds_from_now: int = 30) -> Timestamp:
    ts = _now_timestamp()
    ts.seconds += seconds_from_now
    return ts


async def publish_payment_intent_quotes(
    payment_intent_client: PaymentIntentServiceClient,
    shutdown_event: asyncio.Event,
) -> None:
    """Publish sample pay-in quotes on a regular interval.

    NOTE: Every UpdateQuote request discards all previous payment intent quotes.
    If you want to publish multiple quotes, combine them into a single request.
    """
    while not shutdown_event.is_set():
        try:
            await payment_intent_client.update_quote(
                UpdateQuoteRequest(
                    payment_intent_quotes=[
                        UpdateQuoteRequest.Quote(
                            currency="EUR",
                            payment_method=PAYMENT_METHOD_TYPE_SEPA,
                            expiration=_expiration_timestamp(30),
                            timestamp=_now_timestamp(),
                            bands=[
                                UpdateQuoteRequest.Quote.Band(
                                    client_quote_id=str(uuid.uuid4()),
                                    max_amount=Decimal(unscaled=1000, exponent=0),  # max 1000 USD
                                    # rate is always USD/XXX, so for EUR quote should be USD/EUR
                                    rate=Decimal(unscaled=92, exponent=-2),  # rate 0.92
                                ),
                            ],
                        ),
                    ],
                ),
            )
        except Exception:
            logger.exception("Error updating payment intent quote")
            return

        try:
            await asyncio.wait_for(shutdown_event.wait(), timeout=PUBLISH_INTERVAL_SECONDS)
            return  # shutdown requested
        except TimeoutError:
            pass  # interval elapsed, publish again

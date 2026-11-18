"""Payment intent quote publishing to the T-0 Network.

Pay-In Provider role — Step 3A.1. Publishes sample pay-in quotes every 5 seconds by default.
Replace the hardcoded values with quotes from your own pricing systems.
We recommend publishing at least once per 5 seconds, but not more than once per second.
Each quote expires at publish time + publishing interval + 35 s: a client that gets it just before
the next publish still has 30 s to use it; the 5 s cover network delay.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid

from google.protobuf.timestamp_pb2 import Timestamp
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.common.payment_method_pb2 import PAYMENT_METHOD_TYPE_SEPA
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import PaymentIntentServiceClient
from t0_provider_sdk.api.tzero.v1.payment_intent.network_pb2 import UpdateQuoteRequest

logger = logging.getLogger(__name__)


def _timestamp(ms: int) -> Timestamp:
    ts = Timestamp()
    ts.FromMilliseconds(ms)
    return ts


async def publish_payment_intent_quotes(
    payment_intent_client: PaymentIntentServiceClient,
    shutdown_event: asyncio.Event,
    interval_seconds: float,
) -> None:
    """Publish sample pay-in quotes on a regular interval.

    NOTE: Every UpdateQuote request discards all previous payment intent quotes.
    If you want to publish multiple quotes, combine them into a single request.
    """
    while not shutdown_event.is_set():
        try:
            now_ms = time.time_ns() // 1_000_000
            await payment_intent_client.update_quote(
                UpdateQuoteRequest(
                    payment_intent_quotes=[
                        UpdateQuoteRequest.Quote(
                            currency="EUR",
                            payment_method=PAYMENT_METHOD_TYPE_SEPA,
                            # publish time + publishing interval + 35 s
                            expiration=_timestamp(now_ms + round(interval_seconds * 1000) + 35_000),
                            timestamp=_timestamp(now_ms),
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
        except Exception as e:
            # Logged; the next tick publishes again.
            logger.error("Error updating payment intent quote: %s", e)

        try:
            await asyncio.wait_for(shutdown_event.wait(), timeout=interval_seconds)
            return  # shutdown requested
        except TimeoutError:
            pass  # interval elapsed, publish again

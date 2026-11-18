"""Quote publishing to the T-0 Network.

Publishes sample PayOut (off-ramp) quotes every 5 seconds by default.
TODO: Step 1.3 Replace this with fetching quotes from your systems and publishing them.
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
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClient
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import UpdateQuoteRequest

logger = logging.getLogger(__name__)


def _timestamp(ms: int) -> Timestamp:
    """Create a protobuf Timestamp from Unix milliseconds."""
    ts = Timestamp()
    ts.FromMilliseconds(ms)
    return ts


async def publish_quotes(
    network_client: NetworkServiceClient, shutdown_event: asyncio.Event, interval_seconds: float
) -> None:
    """Publish sample quotes on a regular interval.

    NOTE: Every UpdateQuote request discards all previous quotes.
    If you want to publish multiple quotes, combine them into a single request.
    Otherwise, only the quotes from the last request will be available.
    """
    while not shutdown_event.is_set():
        try:
            currency = "EUR"
            payment_method = PAYMENT_METHOD_TYPE_SEPA
            now_ms = time.time_ns() // 1_000_000
            # publish time + publishing interval + 35 s
            expiration = _timestamp(now_ms + round(interval_seconds * 1000) + 35_000)
            timestamp = _timestamp(now_ms)

            await network_client.update_quote(
                UpdateQuoteRequest(
                    pay_out=[
                        # The quote at which you want to take USDT and pay out local currency (off-ramp)
                        UpdateQuoteRequest.Quote(
                            currency=currency,
                            payment_method=payment_method,
                            expiration=expiration,
                            timestamp=timestamp,
                            bands=[
                                # One or more bands are allowed
                                UpdateQuoteRequest.Quote.Band(
                                    client_quote_id=str(uuid.uuid4()),
                                    max_amount=Decimal(
                                        unscaled=1000,  # maximum amount in USD
                                        exponent=0,
                                    ),
                                    # Note: rate is always USD/XXX, so for BRL quote should be USD/BRL
                                    rate=Decimal(
                                        unscaled=86,  # rate 0.86
                                        exponent=-2,
                                    ),
                                    # optional: set fix to charge a fixed fee per transfer (e.g. wire fees)
                                    # fix=Decimal(unscaled=5, exponent=0),  # $5 fixed charge
                                ),
                            ],
                        ),
                    ],
                ),
            )
        except Exception as e:
            # Logged; the next tick publishes again.
            logger.error("Error updating quote: %s", e)

        try:
            await asyncio.wait_for(shutdown_event.wait(), timeout=interval_seconds)
            return  # shutdown requested
        except TimeoutError:
            pass  # interval elapsed, publish again

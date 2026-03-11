"""Quote publishing to the T-0 Network.

Go equivalent: internal/publish_quotes.go → PublishQuotes()

Publishes sample PayOut (off-ramp) and PayIn (on-ramp) quotes every 5 seconds.
TODO: Step 1.3 Replace this with fetching quotes from your systems and publishing them.
We recommend publishing at least once per 5 seconds, but not more than once per second.
"""

from __future__ import annotations

import asyncio
import logging
import uuid

from google.protobuf.timestamp_pb2 import Timestamp
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.common.payment_method_pb2 import PAYMENT_METHOD_TYPE_SEPA
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClient
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import QUOTE_TYPE_REALTIME, UpdateQuoteRequest

logger = logging.getLogger(__name__)

PUBLISH_INTERVAL_SECONDS = 5


def _now_timestamp() -> Timestamp:
    """Create a protobuf Timestamp for the current time."""
    ts = Timestamp()
    ts.GetCurrentTime()
    return ts


def _expiration_timestamp(seconds_from_now: int = 30) -> Timestamp:
    """Create a protobuf Timestamp offset from now."""
    ts = _now_timestamp()
    ts.seconds += seconds_from_now
    return ts


async def publish_quotes(network_client: NetworkServiceClient, shutdown_event: asyncio.Event) -> None:
    """Publish sample quotes on a regular interval.

    NOTE: Every UpdateQuote request discards all previous quotes.
    If you want to publish multiple quotes, combine them into a single request.
    Otherwise, only the quotes from the last request will be available.
    """
    while not shutdown_event.is_set():
        try:
            currency = "EUR"
            payment_method = PAYMENT_METHOD_TYPE_SEPA
            expiration = _expiration_timestamp(30)
            timestamp = _now_timestamp()

            await network_client.update_quote(
                UpdateQuoteRequest(
                    pay_out=[
                        # The quote at which you want to take USDT and pay out local currency (off-ramp)
                        UpdateQuoteRequest.Quote(
                            currency=currency,
                            quote_type=QUOTE_TYPE_REALTIME,  # REALTIME is the only supported type
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
                                ),
                            ],
                        ),
                    ],
                    pay_in=[
                        # The quote at which you want to take local currency and settle with USDT (on-ramp)
                        UpdateQuoteRequest.Quote(
                            currency=currency,
                            quote_type=QUOTE_TYPE_REALTIME,
                            payment_method=payment_method,
                            expiration=expiration,
                            timestamp=timestamp,
                            bands=[
                                UpdateQuoteRequest.Quote.Band(
                                    client_quote_id=str(uuid.uuid4()),
                                    max_amount=Decimal(
                                        unscaled=1000,
                                        exponent=0,
                                    ),
                                    rate=Decimal(
                                        unscaled=88,  # rate 0.88
                                        exponent=-2,
                                    ),
                                ),
                            ],
                        ),
                    ],
                ),
            )
        except Exception:
            logger.exception("Error updating quote")
            return

        try:
            await asyncio.wait_for(shutdown_event.wait(), timeout=PUBLISH_INTERVAL_SECONDS)
            return  # shutdown requested
        except asyncio.TimeoutError:
            pass  # interval elapsed, publish again

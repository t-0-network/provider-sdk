"""Payment Intent — Pay-In Provider role (sync).

Parallel to payment_intent_pay_in.py but without async/await.
Use this with handler_sync() and new_wsgi_app() for synchronous WSGI servers.
"""

from __future__ import annotations

import logging

from connectrpc.request import RequestContext
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import (
    PaymentIntentServiceClientSync,
)
from t0_provider_sdk.api.tzero.v1.payment_intent.pay_in_provider_pb2 import (
    GetPaymentDetailsRequest,
    GetPaymentDetailsResponse,
)

logger = logging.getLogger(__name__)


class PayInProviderServiceSyncImplementation:
    """Implements the PayInProviderServiceSync protocol from the generated ConnectRPC code."""

    def __init__(self, payment_intent_client: PaymentIntentServiceClientSync) -> None:
        self._payment_intent_client = payment_intent_client

    # TODO: Step 3A.2 See the async variant (payment_intent_pay_in.py) for context.
    def get_payment_details(
        self, request: GetPaymentDetailsRequest, ctx: RequestContext
    ) -> GetPaymentDetailsResponse:
        logger.info(
            "GetPaymentDetails for payment intent %d, %d method(s)",
            request.payment_intent_id,
            len(request.payment_methods),
        )
        return GetPaymentDetailsResponse(
            details=GetPaymentDetailsResponse.Details(
                payment_details=[],  # TODO: populate one PaymentDetails per requested payment_method
            ),
        )

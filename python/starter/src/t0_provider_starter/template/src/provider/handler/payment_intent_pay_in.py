"""Payment Intent — Pay-In Provider role (async).

Go equivalent: internal/handler/payment_intent_pay_in.go

Implement this handler if you are a pay-in provider (you receive fiat from end-users).
Please refer to docs and proto definition comments to understand the full flow.
"""

from __future__ import annotations

import logging

from connectrpc.request import RequestContext
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import (
    PaymentIntentServiceClient,
)
from t0_provider_sdk.api.tzero.v1.payment_intent.pay_in_provider_pb2 import (
    GetPaymentDetailsRequest,
    GetPaymentDetailsResponse,
)

logger = logging.getLogger(__name__)


class PayInProviderServiceImplementation:
    """Implements the PayInProviderService protocol from the generated ConnectRPC code."""

    def __init__(self, payment_intent_client: PaymentIntentServiceClient) -> None:
        self._payment_intent_client = payment_intent_client

    # TODO: Step 3A.2 Implement how you return payment details for the end-user.
    #
    # The network calls this endpoint during CreatePaymentIntent processing. Return
    # payment details (bank account, mobile money number, etc.) for each requested
    # payment method, including a unique payment reference that will let you match
    # the incoming fiat payment back to this payment intent.
    #
    # Store (payment_intent_id, confirmation_code) so you can validate it later in
    # confirm_funds_received.
    async def get_payment_details(
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

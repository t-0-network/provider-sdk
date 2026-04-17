"""Payment Intent — Beneficiary Provider role (async).

Go equivalent: internal/handler/payment_intent_beneficiary.go

Implement this handler if you are a beneficiary provider (you receive settlement
for the crypto side). Please refer to docs and proto definition comments to
understand the full flow.
"""

from __future__ import annotations

import logging

from connectrpc.request import RequestContext
from t0_provider_sdk.api.tzero.v1.payment_intent.beneficiary_pb2 import (
    PaymentIntentUpdateRequest,
    PaymentIntentUpdateResponse,
)

logger = logging.getLogger(__name__)


class BeneficiaryServiceImplementation:
    """Implements the BeneficiaryService protocol from the generated ConnectRPC code."""

    # TODO: Step 3B.3 Implement how you handle notifications about your payment intents.
    #
    # The network calls this endpoint when the status of one of your payment intents
    # changes (e.g. funds received from the end-user). Correlate request.payment_intent_id
    # with the id you stored after calling create_payment_intent and update your
    # internal state accordingly.
    async def payment_intent_update(
        self, request: PaymentIntentUpdateRequest, ctx: RequestContext
    ) -> PaymentIntentUpdateResponse:
        if request.HasField("funds_received"):
            logger.info(
                "payment intent %d: funds received, payment_method=%s transaction_reference=%s",
                request.payment_intent_id,
                request.funds_received.payment_method,
                request.funds_received.transaction_reference,
            )
        else:
            logger.info("payment intent %d: unknown update variant", request.payment_intent_id)
        return PaymentIntentUpdateResponse()

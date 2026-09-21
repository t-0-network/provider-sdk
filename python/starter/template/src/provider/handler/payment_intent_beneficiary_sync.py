"""Payment Intent — Beneficiary Provider role (sync).

Parallel to payment_intent_beneficiary.py but without async/await.
Use this with handler_sync() and new_wsgi_app() for synchronous WSGI servers.
"""

from __future__ import annotations

import logging

from connectrpc.request import RequestContext
from t0_provider_sdk.api.tzero.v1.payment_intent.beneficiary_pb2 import (
    PaymentIntentUpdateRequest,
    PaymentIntentUpdateResponse,
)

logger = logging.getLogger(__name__)


class BeneficiaryServiceSyncImplementation:
    """Implements the BeneficiaryServiceSync protocol from the generated ConnectRPC code."""

    # TODO: Step 3B.3 See the async variant (payment_intent_beneficiary.py) for context.
    def payment_intent_update(
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

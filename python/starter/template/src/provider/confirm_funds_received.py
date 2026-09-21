"""Confirm funds received from an end-user for a payment intent.

Go equivalent: internal/confirm_funds_received.go → ConfirmFundsReceived()

Pay-In Provider role — Step 3A.3. Call this after you have matched an incoming
fiat payment to a payment intent (using the payment reference you returned from
get_payment_details). Settlement with the beneficiary provider will proceed once
this confirmation is accepted.
"""

from __future__ import annotations

import logging

from t0_provider_sdk.api.tzero.v1.common.payment_method_pb2 import PAYMENT_METHOD_TYPE_SEPA
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import PaymentIntentServiceClient
from t0_provider_sdk.api.tzero.v1.payment_intent.network_pb2 import ConfirmFundsReceivedRequest

logger = logging.getLogger(__name__)


async def confirm_funds_received(
    payment_intent_client: PaymentIntentServiceClient,
    payment_intent_id: int,
    confirmation_code: str,
    transaction_reference: str,
) -> None:
    try:
        response = await payment_intent_client.confirm_funds_received(
            ConfirmFundsReceivedRequest(
                payment_intent_id=payment_intent_id,
                confirmation_code=confirmation_code,
                payment_method=PAYMENT_METHOD_TYPE_SEPA,
                transaction_reference=transaction_reference,
                # optional: if your provider has multiple legal entities, set
                # originator_provider_legal_entity_id
            ),
        )

        if response.HasField("accept"):
            logger.info("Funds accepted for payment intent %d", payment_intent_id)
        elif response.HasField("reject"):
            logger.info(
                "Funds rejected for payment intent %d: %s",
                payment_intent_id,
                response.reject.reason,
            )
    except Exception:
        logger.exception("Error confirming funds received")

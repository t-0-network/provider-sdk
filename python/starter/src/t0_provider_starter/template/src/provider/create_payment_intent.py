"""Create a payment intent via the T-0 Network.

Go equivalent: internal/create_payment_intent.go → CreatePaymentIntent()

Beneficiary Provider role — Step 3B.2. Store the returned payment_intent_id to
correlate with the PaymentIntentUpdate notification you'll receive on your
BeneficiaryService handler once the end-user completes the pay-in.
"""

from __future__ import annotations

import logging
import uuid

from ivms101.v1.ivms.ivms101_pb2 import Person
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.payment_intent.network_connect import PaymentIntentServiceClient
from t0_provider_sdk.api.tzero.v1.payment_intent.network_pb2 import CreatePaymentIntentRequest

logger = logging.getLogger(__name__)


async def create_payment_intent(payment_intent_client: PaymentIntentServiceClient) -> None:
    try:
        response = await payment_intent_client.create_payment_intent(
            CreatePaymentIntentRequest(
                external_reference=str(uuid.uuid4()),  # idempotency key
                currency="EUR",
                amount=Decimal(unscaled=500, exponent=0),  # end-user pays 500 EUR
                travel_rule_data=CreatePaymentIntentRequest.TravelRuleData(
                    # TODO: populate real IVMS101 beneficiary information for your end-user.
                    beneficiary=[Person()],
                ),
            ),
        )

        if response.HasField("success"):
            logger.info(
                "Created payment intent id=%d with %d pay-in option(s)",
                response.success.payment_intent_id,
                len(response.success.pay_in_details),
            )
            # TODO: persist (payment_intent_id, external_reference) and present the
            # pay_in_details options to your end-user.
        elif response.HasField("failure"):
            logger.info("Failed to create payment intent: %s", response.failure.reason)
    except Exception:
        logger.exception("Error creating payment intent")

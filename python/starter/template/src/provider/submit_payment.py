"""Demonstrates submitting a test payment to the T-0 Network.

Payout Provider role — Step 2.3. An accepted result is not a completed payout —
follow the payment_update notifications on your ProviderService handler (see
src/provider/handler/payment.py) for the outcome. A retry must resend the same
payment client id and request — the network deduplicates on it. Running this
demo again creates a new id and therefore a new payment.
"""

from __future__ import annotations

import logging
import uuid

from ivms101.v1.ivms.enum_pb2 import NATURAL_PERSON_NAME_TYPE_CODE_LEGL
from ivms101.v1.ivms.ivms101_pb2 import NaturalPerson, NaturalPersonName, NaturalPersonNameId, Person
from t0_provider_sdk.api.tzero.v1.common.common_pb2 import Decimal
from t0_provider_sdk.api.tzero.v1.common.payment_method_pb2 import PaymentDetails
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClient
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import CreatePaymentRequest, CreatePaymentResponse, PaymentAmount

logger = logging.getLogger(__name__)


def _natural_person(primary_identifier: str, secondary_identifier: str) -> Person:
    """A schema-valid sample IVMS101 natural person."""
    return Person(
        natural_person=NaturalPerson(
            name=NaturalPersonName(
                name_identifiers=[
                    NaturalPersonNameId(
                        primary_identifier=primary_identifier,
                        secondary_identifier=secondary_identifier,
                        name_identifier_type=NATURAL_PERSON_NAME_TYPE_CODE_LEGL,
                    )
                ]
            )
        )
    )


async def submit_payment(network_client: NetworkServiceClient) -> None:
    """Submit a sample payment to the network.

    TODO: Step 2.3 Test submitting payment to the system
    """
    payment_client_id = str(uuid.uuid4())
    try:
        response = await network_client.create_payment(
            CreatePaymentRequest(
                payment_client_id=payment_client_id,  # idempotency key — reuse to retry
                amount=PaymentAmount(
                    pay_out_amount=Decimal(unscaled=10, exponent=0),  # payout 10 GBP
                ),
                currency="GBP",
                payment_details=PaymentDetails(
                    sepa=PaymentDetails.Sepa(
                        iban="GB29NWBK60161331926819",
                        beneficiary_name="Max Mustermann",
                    ),
                ),
                travel_rule_data=CreatePaymentRequest.TravelRuleData(
                    # TODO: populate real IVMS101 information for your originating
                    # customer and the beneficiary. These sample persons are
                    # schema-valid placeholders.
                    originator=[_natural_person("Doe", "John")],
                    beneficiary=[_natural_person("Mustermann", "Max")],
                ),
            ),
        )

        if response.HasField("accepted"):
            logger.info("Payment accepted, id=%d", response.accepted.payment_id)
            # TODO: an accepted result is not a completed payout — wait for the
            # payment_update notifications for the outcome.
        elif response.HasField("failure"):
            logger.info(
                "Payment failed with reason: %s",
                CreatePaymentResponse.Failure.Reason.Name(response.failure.reason),
            )
        else:
            logger.info("Unknown response type")

    except Exception as e:
        logger.error("Error submitting payment: %s", e)

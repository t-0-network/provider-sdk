"""Complete a manual AML check for a payout.

Go equivalent: internal/complete_manual_aml_check.go → CompleteManualAmlCheck()

Pay-Out Provider role — Step 2.6 (optional). If your pay_out handler returned
manual_aml_check instead of accepted (see handler/payment.py), run your AML
check out-of-band and report the outcome with this call. On approval the
response carries updated amounts and quote ids re-approved by the pay-in
provider; on rejection the payout must not proceed.
"""

from __future__ import annotations

import logging

from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClient
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import CompleteManualAmlCheckRequest

logger = logging.getLogger(__name__)


async def complete_manual_aml_check(
    network_client: NetworkServiceClient,
    payment_id: int,
) -> None:
    try:
        response = await network_client.complete_manual_aml_check(
            CompleteManualAmlCheckRequest(
                payment_id=payment_id,
                approved=CompleteManualAmlCheckRequest.Approved(),
                # If your check failed, report a rejection instead:
                # rejected=CompleteManualAmlCheckRequest.Rejected(reason="AML check failed"),
            ),
        )

        if response.HasField("approved"):
            # Pay-in provider approved the updated quotes — proceed with the payout using
            # the updated amounts, then report the outcome via finalize_payout.
            logger.info(
                "Manual AML check approved for payment %d: pay_out_amount=%s settlement_amount=%s "
                "pay_out_quote_id=%d pay_out_client_quote_id=%s",
                payment_id,
                response.approved.pay_out_amount,
                response.approved.settlement_amount,
                response.approved.pay_out_quote_id,
                response.approved.pay_out_client_quote_id,
            )
        elif response.HasField("rejected"):
            # Pay-in provider rejected the updated quotes — do NOT proceed with the payout.
            logger.info("Updated quotes rejected for payment %d, do not proceed with the payout", payment_id)
    except Exception:
        logger.exception("Error completing manual AML check")

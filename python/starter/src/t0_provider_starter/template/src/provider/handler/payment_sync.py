"""Synchronous provider service implementation.

Parallel to payment.py but implements ProviderServiceSync (no async/await).
Use this with handler_sync() and new_wsgi_app() for synchronous WSGI servers.

Please refer to docs, proto definition comments, or source code comments
to understand the purpose of each function.
"""

from __future__ import annotations

import logging

from connectrpc.request import RequestContext
from t0_provider_sdk.api.tzero.v1.common.payment_receipt_pb2 import PaymentReceipt
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClientSync
from t0_provider_sdk.api.tzero.v1.payment.network_pb2 import FinalizePayoutRequest
from t0_provider_sdk.api.tzero.v1.payment.provider_pb2 import (
    AppendLedgerEntriesRequest,
    AppendLedgerEntriesResponse,
    ApprovePaymentQuoteRequest,
    ApprovePaymentQuoteResponse,
    PayoutRequest,
    PayoutResponse,
    UpdateLimitRequest,
    UpdateLimitResponse,
    UpdatePaymentRequest,
    UpdatePaymentResponse,
)

logger = logging.getLogger(__name__)


class ProviderServiceSyncImplementation:
    """Implements the ProviderServiceSync protocol from the generated ConnectRPC code."""

    def __init__(self, network_client: NetworkServiceClientSync) -> None:
        self._network_client = network_client

    # TODO: Step 2.1 implement how you handle updates of payment initiated by you
    def update_payment(
        self, request: UpdatePaymentRequest, ctx: RequestContext
    ) -> UpdatePaymentResponse:
        return UpdatePaymentResponse()

    # TODO: Step 2.4 implement how you do payouts (payments initiated by your counterparts)
    def pay_out(
        self, request: PayoutRequest, ctx: RequestContext
    ) -> PayoutResponse:
        # TODO: FinalizePayout should be called when your system notifies
        # that payout has been made successfully
        self._network_client.finalize_payout(
            FinalizePayoutRequest(
                payment_id=request.payment_id,
                success=FinalizePayoutRequest.Success(
                    receipt=PaymentReceipt(
                        sepa=PaymentReceipt.Sepa(
                            banking_transaction_reference_id="123456",
                        ),
                    ),
                ),
            ),
        )

        return PayoutResponse()

    def update_limit(
        self, request: UpdateLimitRequest, ctx: RequestContext
    ) -> UpdateLimitResponse:
        # TODO: optionally implement handling of the notifications about
        # updates on your limits and limits usage
        return UpdateLimitResponse()

    def append_ledger_entries(
        self, request: AppendLedgerEntriesRequest, ctx: RequestContext
    ) -> AppendLedgerEntriesResponse:
        # TODO: optionally implement handling of the notifications about
        # new ledger transactions and new ledger entries
        return AppendLedgerEntriesResponse()

    def approve_payment_quotes(
        self, request: ApprovePaymentQuoteRequest, ctx: RequestContext
    ) -> ApprovePaymentQuoteResponse:
        # TODO: this is the endpoint to have a last look at quote
        # and approve after AML check is done
        return ApprovePaymentQuoteResponse()

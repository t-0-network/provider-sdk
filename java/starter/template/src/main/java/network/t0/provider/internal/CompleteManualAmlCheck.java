package network.t0.provider.internal;

import network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckRequest;
import network.t0.sdk.proto.tzero.v1.payment.CompleteManualAmlCheckResponse;
import network.t0.sdk.proto.tzero.v1.payment.NetworkServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pay-Out Provider role — Step 2.6 (optional).
 *
 * <p>If your payOut handler returned manual_aml_check instead of accepted (see
 * handler/PaymentHandler.java), run your AML check out-of-band and report the
 * outcome with this call. On approval the response carries the updated amounts
 * and quote ids (re-approved by the pay-in provider via the "last look"); on
 * rejection the payout must not proceed.
 */
public class CompleteManualAmlCheck {

    private static final Logger log = LoggerFactory.getLogger(CompleteManualAmlCheck.class);

    public static void complete(
            NetworkServiceGrpc.NetworkServiceBlockingStub networkClient,
            long paymentId) {
        try {
            CompleteManualAmlCheckResponse response = networkClient.completeManualAmlCheck(
                    CompleteManualAmlCheckRequest.newBuilder()
                            .setPaymentId(paymentId)
                            .setApproved(CompleteManualAmlCheckRequest.Approved.newBuilder().build())
                            // If your check failed, report a rejection instead:
                            // .setRejected(CompleteManualAmlCheckRequest.Rejected.newBuilder()
                            //         .setReason("AML check failed").build())
                            .build());

            switch (response.getResultCase()) {
                case APPROVED -> {
                    // Pay-in provider approved the updated quotes — proceed with the payout using
                    // the updated amounts, then report the outcome via finalizePayout.
                    CompleteManualAmlCheckResponse.Approved approved = response.getApproved();
                    log.info("Manual AML check approved for payment {}: payOutAmount={}, settlementAmount={}, quoteId={}, clientQuoteId={}",
                            paymentId, approved.getPayOutAmount(), approved.getSettlementAmount(),
                            approved.getPayOutQuoteId(), approved.getPayOutClientQuoteId());
                }
                case REJECTED ->
                        // Pay-in provider rejected the updated quotes — do NOT proceed with the payout.
                        log.warn("Updated quotes rejected for payment {}, do not proceed with the payout", paymentId);
                default -> log.warn("Unknown result type for payment {}", paymentId);
            }
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error completing manual AML check: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

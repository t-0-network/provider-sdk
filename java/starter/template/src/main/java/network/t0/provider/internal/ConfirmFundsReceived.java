package network.t0.provider.internal;

import network.t0.sdk.proto.tzero.v1.common.PaymentMethodType;
import network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedRequest;
import network.t0.sdk.proto.tzero.v1.payment_intent.ConfirmFundsReceivedResponse;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pay-In Provider role — Step 3A.3.
 *
 * <p>Notifies the t-0 Network that an end-user has paid. Call this after you have
 * matched an incoming fiat payment to a payment intent (using the payment reference
 * you returned from GetPaymentDetails). Settlement with the beneficiary provider
 * will proceed once this confirmation is accepted.
 */
public class ConfirmFundsReceived {

    private static final Logger log = LoggerFactory.getLogger(ConfirmFundsReceived.class);

    public static void confirm(
            PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub paymentIntentClient,
            long paymentIntentId,
            String confirmationCode,
            String transactionReference) {
        try {
            ConfirmFundsReceivedResponse response = paymentIntentClient.confirmFundsReceived(
                    ConfirmFundsReceivedRequest.newBuilder()
                            .setPaymentIntentId(paymentIntentId)
                            .setConfirmationCode(confirmationCode)
                            .setPaymentMethod(PaymentMethodType.PAYMENT_METHOD_TYPE_SEPA)
                            .setTransactionReference(transactionReference)
                            // optional: if your provider has multiple legal entities,
                            // set setOriginatorProviderLegalEntityId()
                            .build());

            switch (response.getResultCase()) {
                case ACCEPT -> log.info("Funds accepted for payment intent {}", paymentIntentId);
                case REJECT -> log.info("Funds rejected for payment intent {}: {}",
                        paymentIntentId, response.getReject().getReason());
                default -> log.info("Unknown response type");
            }
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error confirming funds received: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

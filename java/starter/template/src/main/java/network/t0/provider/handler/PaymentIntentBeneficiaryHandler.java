package network.t0.provider.handler;

import io.grpc.stub.StreamObserver;
import network.t0.sdk.proto.tzero.v1.payment_intent.BeneficiaryServiceGrpc;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateRequest;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentUpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payment Intent Flow — Beneficiary Provider role.
 *
 * <p>Implement this handler if you are a beneficiary provider (you receive settlement
 * for the crypto side). Please refer to docs and proto definition comments to
 * understand the full flow.
 */
public class PaymentIntentBeneficiaryHandler extends BeneficiaryServiceGrpc.BeneficiaryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntentBeneficiaryHandler.class);

    /**
     * TODO: Step 3B.3 Implement how you handle notifications about your payment intents.
     *
     * <p>The network calls this endpoint when the status of one of your payment intents
     * changes (e.g. funds received from the end-user). Correlate request.getPaymentIntentId()
     * with the id you stored after calling CreatePaymentIntent and update your
     * internal state accordingly.
     */
    @Override
    public void paymentIntentUpdate(PaymentIntentUpdateRequest request, StreamObserver<PaymentIntentUpdateResponse> responseObserver) {
        switch (request.getUpdateCase()) {
            case FUNDS_RECEIVED -> {
                var fundsReceived = request.getFundsReceived();
                log.info("payment intent {}: funds received, payment_method={} transaction_reference={}",
                        request.getPaymentIntentId(),
                        fundsReceived.getPaymentMethod(),
                        fundsReceived.getTransactionReference());
            }
            default -> log.info("payment intent {}: unknown update variant", request.getPaymentIntentId());
        }

        responseObserver.onNext(PaymentIntentUpdateResponse.newBuilder().build());
        responseObserver.onCompleted();
    }
}

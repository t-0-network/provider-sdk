package network.t0.provider.handler;

import io.grpc.stub.StreamObserver;
import network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsRequest;
import network.t0.sdk.proto.tzero.v1.payment_intent.GetPaymentDetailsResponse;
import network.t0.sdk.proto.tzero.v1.payment_intent.PayInProviderServiceGrpc;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payment Intent Flow — Pay-In Provider role.
 *
 * <p>Implement this handler if you are a pay-in provider (you receive fiat from end-users).
 * Please refer to docs and proto definition comments to understand the full flow.
 */
public class PaymentIntentPayInHandler extends PayInProviderServiceGrpc.PayInProviderServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntentPayInHandler.class);

    private final PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub paymentIntentClient;

    public PaymentIntentPayInHandler(PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub paymentIntentClient) {
        this.paymentIntentClient = paymentIntentClient;
    }

    /**
     * TODO: Step 3A.2 Implement how you return payment details for the end-user.
     *
     * <p>The network calls this endpoint during CreatePaymentIntent processing. Return
     * payment details (bank account, mobile money number, etc.) for each requested
     * payment method, including a unique payment reference that will let you match
     * the incoming fiat payment back to this payment intent.
     *
     * <p>Store (payment_intent_id, confirmation_code) so you can validate it later in
     * ConfirmFundsReceived.
     */
    @Override
    public void getPaymentDetails(GetPaymentDetailsRequest request, StreamObserver<GetPaymentDetailsResponse> responseObserver) {
        log.info("Received getPaymentDetails for payment_intent_id={}, {} method(s)",
                request.getPaymentIntentId(), request.getPaymentMethodsCount());

        // Example: return an empty Details response — replace with your real payment instructions.
        responseObserver.onNext(GetPaymentDetailsResponse.newBuilder()
                .setDetails(GetPaymentDetailsResponse.Details.newBuilder().build())
                .build());
        responseObserver.onCompleted();
    }
}

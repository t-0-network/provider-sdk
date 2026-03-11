package network.t0.provider.internal;

import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.common.PaymentDetails;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Payment submission logic for testing.
 *
 * <p>TODO: Step 2.3 Test payment submission by calling this method.
 * Uncomment the call in Main.java when you're ready to test.
 */
public class SubmitPayment {

    private static final Logger log = LoggerFactory.getLogger(SubmitPayment.class);

    /**
     * Submits a test payment to the T-0 Network.
     *
     * @param networkClient the network service client
     */
    public static void submit(NetworkServiceGrpc.NetworkServiceBlockingStub networkClient) {
        try {
            String paymentClientId = UUID.randomUUID().toString();

            log.info("Submitting test payment with client_id: {}", paymentClientId);

            CreatePaymentResponse response = networkClient.createPayment(CreatePaymentRequest.newBuilder()
                    .setPaymentClientId(paymentClientId)
                    .setAmount(PaymentAmount.newBuilder()
                            .setPayOutAmount(Decimal.newBuilder()
                                    .setUnscaled(10) // 10 GBP
                                    .setExponent(0)
                                    .build())
                            .build())
                    .setCurrency("GBP")
                    .setPaymentDetails(PaymentDetails.newBuilder()
                            .setSepa(PaymentDetails.Sepa.newBuilder()
                                    .setIban("GB12345567890")
                                    .setBeneficiaryName("Max Mustermann")
                                    .build())
                            .build())
                    .build());

            if (response.hasAccepted()) {
                CreatePaymentResponse.Accepted accepted = response.getAccepted();
                log.info("✅ Step 2.3: Payment submitted successfully!");
                log.info("Payment ID: {}", accepted.getPaymentId());
                log.info("Payout amount: {}", accepted.getPayoutAmount());
                log.info("Settlement amount: {}", accepted.getSettlementAmount());
            } else if (response.hasFailure()) {
                CreatePaymentResponse.Failure failure = response.getFailure();
                log.warn("Payment submission failed: {}", failure.getReason());
            }

        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error submitting payment: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

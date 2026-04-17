package network.t0.provider.internal;

import network.t0.sdk.proto.ivms101.Person;
import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentRequest;
import network.t0.sdk.proto.tzero.v1.payment_intent.CreatePaymentIntentResponse;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Beneficiary Provider role — Step 3B.2.
 *
 * <p>Initiates a new payment intent with the t-0 Network. Store the returned
 * payment_intent_id to correlate with the PaymentIntentUpdate notification you'll
 * receive on your BeneficiaryService handler once the end-user completes the pay-in.
 */
public class CreatePaymentIntent {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentIntent.class);

    public static void create(PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub paymentIntentClient) {
        try {
            CreatePaymentIntentResponse response = paymentIntentClient.createPaymentIntent(
                    CreatePaymentIntentRequest.newBuilder()
                            .setExternalReference(UUID.randomUUID().toString()) // idempotency key
                            .setCurrency("EUR")
                            .setAmount(Decimal.newBuilder().setUnscaled(500).setExponent(0).build()) // 500 EUR
                            .setTravelRuleData(CreatePaymentIntentRequest.TravelRuleData.newBuilder()
                                    // TODO: populate real IVMS101 beneficiary information for your end-user.
                                    .addBeneficiary(Person.newBuilder().build())
                                    .build())
                            .build());

            switch (response.getResultCase()) {
                case SUCCESS -> log.info("Created payment intent id={} with {} pay-in option(s)",
                        response.getSuccess().getPaymentIntentId(),
                        response.getSuccess().getPayInDetailsCount());
                case FAILURE -> log.info("Failed to create payment intent: {}",
                        response.getFailure().getReason());
                default -> log.info("Unknown response type");
            }
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error creating payment intent: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

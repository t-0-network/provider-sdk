package network.t0.provider.internal;

import network.t0.sdk.proto.ivms101.NaturalPerson;
import network.t0.sdk.proto.ivms101.NaturalPersonName;
import network.t0.sdk.proto.ivms101.NaturalPersonNameId;
import network.t0.sdk.proto.ivms101.NaturalPersonNameTypeCode;
import network.t0.sdk.proto.ivms101.Person;
import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.common.PaymentDetails;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Payment submission logic for testing.
 *
 * <p>TODO: Step 2.3 Test payment submission by calling this method from Main.java.
 *
 * <p>An accepted result is not a completed payout — the PaymentUpdate notifications on the
 * ProviderService handler carry the outcome. A retry must resend the same payment client id
 * and request — the network deduplicates on it; running this demo again generates a new id
 * and therefore a new payment.
 */
public class SubmitPayment {

    private static final Logger log = LoggerFactory.getLogger(SubmitPayment.class);

    /** A schema-valid sample IVMS101 natural person. */
    private static Person naturalPerson(String primaryIdentifier, String secondaryIdentifier) {
        return Person.newBuilder()
                .setNaturalPerson(NaturalPerson.newBuilder()
                        .setName(NaturalPersonName.newBuilder()
                                .addNameIdentifiers(NaturalPersonNameId.newBuilder()
                                        .setPrimaryIdentifier(primaryIdentifier)
                                        .setSecondaryIdentifier(secondaryIdentifier)
                                        .setNameIdentifierType(
                                                NaturalPersonNameTypeCode.NATURAL_PERSON_NAME_TYPE_CODE_LEGL))))
                .build();
    }

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
                    .setPaymentClientId(paymentClientId) // idempotency key — reuse to retry without duplicating the payment
                    .setAmount(PaymentAmount.newBuilder()
                            .setPayOutAmount(Decimal.newBuilder()
                                    .setUnscaled(10) // 10 GBP
                                    .setExponent(0)
                                    .build())
                            .build())
                    .setCurrency("GBP")
                    .setPaymentDetails(PaymentDetails.newBuilder()
                            .setSepa(PaymentDetails.Sepa.newBuilder()
                                    .setIban("GB29NWBK60161331926819")
                                    .setBeneficiaryName("Max Mustermann")
                                    .build())
                            .build())
                    // TODO: populate real IVMS101 information for your originating customer
                    // and the beneficiary. These sample persons are schema-valid placeholders.
                    .setTravelRuleData(CreatePaymentRequest.TravelRuleData.newBuilder()
                            .addOriginator(naturalPerson("Doe", "John"))
                            .addBeneficiary(naturalPerson("Mustermann", "Max"))
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
            } else {
                log.warn("Unknown result type");
            }

        } catch (RuntimeException e) {
            log.error("Error submitting payment: {}", e.getMessage());
        }
    }
}

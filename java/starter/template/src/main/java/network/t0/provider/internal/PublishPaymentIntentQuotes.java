package network.t0.provider.internal;

import com.google.protobuf.Timestamp;
import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.common.PaymentMethodType;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentServiceGrpc;
import network.t0.sdk.proto.tzero.v1.payment_intent.UpdateQuoteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Pay-In Provider role — Step 3A.1.
 *
 * <p>Publishes pay-in quotes for the payment intent flow. These quotes tell the
 * network what exchange rates you're willing to accept when an end-user pays via
 * one of your supported payment methods.
 *
 * <p>TODO: Step 3A.1 replace this with fetching pay-in quotes from your systems
 * and publishing them into t-0 Network. We recommend publishing at least once per
 * 5 seconds, but not more than once per second.
 */
public class PublishPaymentIntentQuotes {

    private static final Logger log = LoggerFactory.getLogger(PublishPaymentIntentQuotes.class);

    public static void publish(PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub paymentIntentClient) {
        try {
            Instant now = Instant.now();
            Timestamp expiration = Timestamp.newBuilder()
                    .setSeconds(now.plusSeconds(30).getEpochSecond())
                    .setNanos(now.plusSeconds(30).getNano())
                    .build();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();

            // NOTE: Every UpdateQuote request discards all previous payment intent quotes
            // that were published before. Combine multiple quotes into a single request.
            paymentIntentClient.updateQuote(UpdateQuoteRequest.newBuilder()
                    .addPaymentIntentQuotes(UpdateQuoteRequest.Quote.newBuilder()
                            .setCurrency("EUR")
                            .setPaymentMethod(PaymentMethodType.PAYMENT_METHOD_TYPE_SEPA)
                            .setExpiration(expiration)
                            .setTimestamp(timestamp)
                            .addBands(UpdateQuoteRequest.Quote.Band.newBuilder()
                                    .setClientQuoteId(UUID.randomUUID().toString())
                                    .setMaxAmount(Decimal.newBuilder()
                                            .setUnscaled(1000) // max 1000 USD for this band
                                            .setExponent(0)
                                            .build())
                                    // rate is always USD/XXX, so for EUR quote should be USD/EUR
                                    .setRate(Decimal.newBuilder()
                                            .setUnscaled(92) // rate 0.92
                                            .setExponent(-2)
                                            .build())
                                    .build())
                            .build())
                    .build());

            log.debug("✅ Payment intent quotes published successfully");

        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error updating payment intent quote: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

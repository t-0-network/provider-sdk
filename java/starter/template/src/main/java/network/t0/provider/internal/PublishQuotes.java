package network.t0.provider.internal;

import com.google.protobuf.Timestamp;
import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.common.PaymentMethodType;
import network.t0.sdk.proto.tzero.v1.payment.NetworkServiceGrpc;
import network.t0.sdk.proto.tzero.v1.payment.QuoteType;
import network.t0.sdk.proto.tzero.v1.payment.UpdateQuoteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Quote publishing logic.
 *
 * <p>TODO: Step 1.3 replace this with fetching quotes from your systems and publishing them into t-0 Network.
 * We recommend publishing at least once per 5 seconds, but not more than once per second.
 */
public class PublishQuotes {

    private static final Logger log = LoggerFactory.getLogger(PublishQuotes.class);

    /**
     * Publishes quotes to the T-0 Network.
     *
     * @param networkClient the network service client
     */
    public static void publish(NetworkServiceGrpc.NetworkServiceBlockingStub networkClient) {
        try {
            String currency = "EUR";
            PaymentMethodType paymentMethod = PaymentMethodType.PAYMENT_METHOD_TYPE_SEPA;

            Instant now = Instant.now();
            Timestamp expiration = Timestamp.newBuilder()
                    .setSeconds(now.plusSeconds(30).getEpochSecond())
                    .setNanos(now.plusSeconds(30).getNano())
                    .build();
            Timestamp timestamp = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();

            // NOTE: Every update quote request discards all previous quotes that were published before.
            // So if you want to publish multiple quotes, you need to combine them into a single request.
            // Otherwise, if you send multiple requests, only the quotes from the last one will be available.

            networkClient.updateQuote(UpdateQuoteRequest.newBuilder()
                    // The quote at which you want to take local currency and settle with USDT (on-ramp)
                    .addPayIn(UpdateQuoteRequest.Quote.newBuilder()
                            .setCurrency(currency)
                            .setQuoteType(QuoteType.QUOTE_TYPE_REALTIME) // REALTIME is only supported right now
                            .setPaymentMethod(paymentMethod)
                            .setExpiration(expiration)
                            .setTimestamp(timestamp)
                            .addBands(UpdateQuoteRequest.Quote.Band.newBuilder()
                                    .setClientQuoteId(UUID.randomUUID().toString())
                                    .setMaxAmount(Decimal.newBuilder()
                                            .setUnscaled(25000) // maximum amount in USD, could be 1000, 5000, 10000 or 25000
                                            .setExponent(0)
                                            .build())
                                    // note that rate is always USD/XXX, so that for EUR quote should be USD/EUR
                                    .setRate(Decimal.newBuilder()
                                            .setUnscaled(863) // rate 0.863
                                            .setExponent(-3)
                                            .build())
                                    .build())
                            .build())
                    // The quote at which you want to take USDT and pay out local currency (off-ramp)
                    .addPayOut(UpdateQuoteRequest.Quote.newBuilder()
                            .setCurrency(currency)
                            .setQuoteType(QuoteType.QUOTE_TYPE_REALTIME) // REALTIME is only supported right now
                            .setPaymentMethod(paymentMethod)
                            .setExpiration(expiration)
                            .setTimestamp(timestamp)
                            .addBands(UpdateQuoteRequest.Quote.Band.newBuilder()
                                    .setClientQuoteId(UUID.randomUUID().toString())
                                    .setMaxAmount(Decimal.newBuilder()
                                            .setUnscaled(25000) // maximum amount in USD, could be 1000, 5000, 10000 or 25000
                                            .setExponent(0)
                                            .build())
                                    // note that rate is always USD/XXX, so that for EUR quote should be USD/EUR
                                    .setRate(Decimal.newBuilder()
                                            .setUnscaled(873) // rate 0.873
                                            .setExponent(-3)
                                            .build())
                                    .build())
                            .build())
                    .build());

            log.debug("✅ Quotes published successfully");

        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error updating quote: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

package network.t0.provider.internal;

import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteRequest;
import network.t0.sdk.proto.tzero.v1.payment_intent.GetQuoteResponse;
import network.t0.sdk.proto.tzero.v1.payment_intent.PaymentIntentServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Beneficiary Provider role — Step 3B.1.
 *
 * <p>Fetches indicative quotes for a pay-in currency/amount. Use this to check
 * available rates before creating a payment intent. The actual settlement rate is
 * determined when the pay-in provider confirms funds received.
 */
public class GetPaymentIntentQuote {

    private static final Logger log = LoggerFactory.getLogger(GetPaymentIntentQuote.class);

    public static void fetch(PaymentIntentServiceGrpc.PaymentIntentServiceBlockingStub paymentIntentClient) {
        try {
            GetQuoteResponse response = paymentIntentClient.getQuote(GetQuoteRequest.newBuilder()
                    .setCurrency("EUR")
                    .setAmount(Decimal.newBuilder().setUnscaled(500).setExponent(0).build()) // 500 EUR
                    .build());

            switch (response.getResultCase()) {
                case SUCCESS -> log.info("Got {} best pay-in quotes and {} total quotes",
                        response.getSuccess().getBestQuotesCount(),
                        response.getSuccess().getAllQuotesCount());
                case QUOTE_NOT_FOUND -> log.info("No pay-in quotes available for this currency/amount");
                default -> log.info("Unknown response type");
            }
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error getting payment intent quote: {} - {}", e.getStatus().getCode(), e.getMessage());
        }
    }
}

package network.t0.provider.internal;

import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.common.PaymentMethodType;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Quote fetching logic for testing.
 *
 * <p>Use this to verify that quotes are successfully received (Step 1.4).
 */
public class GetQuote {

    private static final Logger log = LoggerFactory.getLogger(GetQuote.class);

    /**
     * Fetches a quote from the T-0 Network to verify connectivity.
     *
     * @param networkClient the network service client
     */
    public static void fetch(NetworkServiceGrpc.NetworkServiceBlockingStub networkClient) {
        try {
            GetQuoteResponse response = networkClient.getQuote(GetQuoteRequest.newBuilder()
                    .setPayOutCurrency("GBP")
                    .setPayOutMethod(PaymentMethodType.PAYMENT_METHOD_TYPE_SEPA)
                    .setQuoteType(QuoteType.QUOTE_TYPE_REALTIME)
                    .setAmount(PaymentAmount.newBuilder()
                            .setPayOutAmount(Decimal.newBuilder()
                                    .setUnscaled(50)
                                    .setExponent(0)
                                    .build())
                            .build())
                    .build());

            if (response.hasSuccess()) {
                GetQuoteResponse.Success success = response.getSuccess();
                log.info("✅ Step 1.4: Quote received successfully!");
                // A Decimal is unscaled × 10^exponent.
                log.info("Rate: {}", BigDecimal.valueOf(
                        success.getRate().getUnscaled(), -success.getRate().getExponent()).toPlainString());
                log.info("Quote ID: provider={}, quote={}",
                        success.getQuoteId().getProviderId(),
                        success.getQuoteId().getQuoteId());
            } else if (response.hasFailure()) {
                GetQuoteResponse.Failure failure = response.getFailure();
                log.warn("Quote request failed: {}", failure.getReason());
                log.info("This may be expected if no matching quotes are available yet");
            } else {
                log.warn("Unknown result type");
            }

        } catch (RuntimeException e) {
            log.error("Error getting quote: {}", e.getMessage());
        }
    }
}

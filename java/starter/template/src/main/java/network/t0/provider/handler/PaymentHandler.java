package network.t0.provider.handler;

import io.grpc.stub.StreamObserver;
import network.t0.sdk.proto.tzero.v1.common.PaymentReceipt;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the ProviderService.
 *
 * <p>Please refer to docs, proto definition comments or source code comments
 * to understand purpose of functions.
 */
public class PaymentHandler extends ProviderServiceGrpc.ProviderServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

    private final NetworkServiceGrpc.NetworkServiceBlockingStub networkClient;

    public PaymentHandler(NetworkServiceGrpc.NetworkServiceBlockingStub networkClient) {
        this.networkClient = networkClient;
    }

    // TODO: Step 2.1 implement how you handle updates of payment initiated by you
    @Override
    public void updatePayment(UpdatePaymentRequest request, StreamObserver<UpdatePaymentResponse> responseObserver) {
        log.info("Received updatePayment for payment_id={}, client_id={}",
                request.getPaymentId(), request.getPaymentClientId());

        // Handle the payment update based on the result type
        switch (request.getResultCase()) {
            case ACCEPTED -> {
                log.info("Payment {} was accepted", request.getPaymentId());
                // TODO: Update your system to reflect the accepted status
            }
            case FAILED -> {
                log.warn("Payment {} failed", request.getPaymentId());
                // TODO: Update your system to reflect the failed status
            }
            case CONFIRMED -> {
                log.info("Payment {} was confirmed/completed", request.getPaymentId());
                // TODO: Update your system to reflect the completed status
            }
            case MANUAL_AML_CHECK -> {
                log.info("Payment {} requires manual AML check", request.getPaymentId());
                // TODO: Handle manual AML check requirement
            }
            default -> log.warn("Unknown result type for payment {}", request.getPaymentId());
        }

        responseObserver.onNext(UpdatePaymentResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    // TODO: Step 2.4 implement how you do payouts (payments initiated by your counterparts)
    @Override
    public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> responseObserver) {
        log.info("Received payOut request: payment_id={}, currency={}, amount={}",
                request.getPaymentId(),
                request.getCurrency(),
                request.getAmount());

        // TODO: Implement your payout logic here
        // 1. Validate the payout request
        // 2. Initiate the payout in your system
        // 3. Return the appropriate response

        // Example: Accept the payout
        responseObserver.onNext(PayoutResponse.newBuilder()
                .setAccepted(PayoutResponse.Accepted.newBuilder().build())
                .build());
        responseObserver.onCompleted();

        // TODO: finalizePayout should be called when your system completes (or fails) the payout
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                networkClient.finalizePayout(FinalizePayoutRequest.newBuilder()
                        .setPaymentId(request.getPaymentId())
                        .setSuccess(FinalizePayoutRequest.Success.newBuilder()
                                .setReceipt(PaymentReceipt.newBuilder()
                                        .setSepa(PaymentReceipt.Sepa.newBuilder()
                                                .setBankingTransactionReferenceId("1234567890")
                                                .build())
                                        .build())
                                .build())
                        .build());
            } catch (Exception e) {
                log.error("Failed to finalize payout for payment {}", request.getPaymentId(), e);
            }
        }).start();
    }

    @Override
    public void updateLimit(UpdateLimitRequest request, StreamObserver<UpdateLimitResponse> responseObserver) {
        // TODO: optionally implement handling of the notifications about updates on your limits and limits usage
        log.info("Received updateLimit with {} limits", request.getLimitsCount());

        for (UpdateLimitRequest.Limit limit : request.getLimitsList()) {
            log.debug("Limit update: counterpart_id={}, payout_limit={}, credit_limit={}, credit_usage={}",
                    limit.getCounterpartId(),
                    limit.getPayoutLimit(),
                    limit.getCreditLimit(),
                    limit.getCreditUsage());
        }

        responseObserver.onNext(UpdateLimitResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void appendLedgerEntries(AppendLedgerEntriesRequest request, StreamObserver<AppendLedgerEntriesResponse> responseObserver) {
        // TODO: optionally implement handling of the notifications about new ledger transactions and new ledger entries
        log.info("Received appendLedgerEntries with {} transactions", request.getTransactionsCount());

        for (AppendLedgerEntriesRequest.Transaction transaction : request.getTransactionsList()) {
            log.debug("Transaction: id={}, entries_count={}",
                    transaction.getTransactionId(),
                    transaction.getEntriesCount());
        }

        responseObserver.onNext(AppendLedgerEntriesResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void approvePaymentQuotes(ApprovePaymentQuoteRequest request, StreamObserver<ApprovePaymentQuoteResponse> responseObserver) {
        log.info("Received approvePaymentQuotes for payment_id={}", request.getPaymentId());

        // TODO: Implement "Last Look" logic
        // Verify that the final rates and amounts are acceptable

        // Example: Accept the quotes
        responseObserver.onNext(ApprovePaymentQuoteResponse.newBuilder()
                .setAccepted(ApprovePaymentQuoteResponse.Accepted.newBuilder().build())
                .build());
        responseObserver.onCompleted();
    }
}

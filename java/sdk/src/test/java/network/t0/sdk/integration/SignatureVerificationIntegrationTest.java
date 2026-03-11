package network.t0.sdk.integration;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for signature verification using real ProviderServer and BlockingNetworkClient.
 *
 * <p>These tests verify the complete end-to-end flow:
 * <ul>
 *   <li>ProviderServer starts on a random port with SignatureVerificationInterceptor</li>
 *   <li>BlockingNetworkClient connects and calls ProviderService methods (simulating T-0 Network)</li>
 *   <li>The server verifies signatures and processes requests</li>
 * </ul>
 *
 * <p>Test scenarios include:
 * <ul>
 *   <li>Happy path with valid signatures</li>
 *   <li>Authentication failures (wrong key)</li>
 *   <li>Server configuration validation</li>
 *   <li>Various message sizes</li>
 * </ul>
 */
class SignatureVerificationIntegrationTest {

    // Test key pairs (same as SignerTest for consistency)
    private static final String NETWORK_PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String NETWORK_PUBLIC_KEY_HEX = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";

    // Alternative key for testing wrong key scenarios (secp256k1 private key = 1)
    private static final String OTHER_PRIVATE_KEY = "0000000000000000000000000000000000000000000000000000000000000001";

    private ProviderServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Starts a ProviderServer on a random port with the given network public key.
     */
    private void startServer(String networkPublicKey) throws Exception {
        server = ProviderServer.create(0, networkPublicKey) // Random port
                .withService(new TestProviderServiceImpl())
                .start();
    }

    /**
     * Creates a BlockingNetworkClient connected to the test server.
     * This simulates the T-0 Network calling the provider.
     */
    private BlockingNetworkClient<ProviderServiceGrpc.ProviderServiceBlockingStub> createClient(Signer signer) {
        return BlockingNetworkClient.create(
                "http://localhost:" + server.getPort(), signer, ProviderServiceGrpc::newBlockingStub);
    }

    // ==================== Test Service Implementation ====================

    /**
     * Simple test implementation of ProviderService that echoes success.
     */
    private static class TestProviderServiceImpl extends ProviderServiceGrpc.ProviderServiceImplBase {

        @Override
        public void payOut(PayoutRequest request, StreamObserver<PayoutResponse> responseObserver) {
            PayoutResponse response = PayoutResponse.newBuilder()
                    .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void updatePayment(UpdatePaymentRequest request, StreamObserver<UpdatePaymentResponse> responseObserver) {
            responseObserver.onNext(UpdatePaymentResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void updateLimit(UpdateLimitRequest request, StreamObserver<UpdateLimitResponse> responseObserver) {
            responseObserver.onNext(UpdateLimitResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void appendLedgerEntries(AppendLedgerEntriesRequest request,
                                        StreamObserver<AppendLedgerEntriesResponse> responseObserver) {
            responseObserver.onNext(AppendLedgerEntriesResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void approvePaymentQuotes(ApprovePaymentQuoteRequest request,
                                         StreamObserver<ApprovePaymentQuoteResponse> responseObserver) {
            ApprovePaymentQuoteResponse response = ApprovePaymentQuoteResponse.newBuilder()
                    .setAccepted(ApprovePaymentQuoteResponse.Accepted.getDefaultInstance())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // ==================== Happy Path Tests ====================

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Valid signature with correct key should succeed - PayOut")
        void validSignature_payOut_shouldSucceed() throws Exception {
            // Start server expecting NETWORK_PUBLIC_KEY
            startServer(NETWORK_PUBLIC_KEY_HEX);

            // Create NetworkClient that signs with NETWORK_PRIVATE_KEY
            Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);
            try (var client = createClient(networkSigner)) {
                PayoutRequest request = PayoutRequest.newBuilder()
                        .setPaymentId(12345)
                        .setPayoutId(67890)
                        .setCurrency("USD")
                        .setClientQuoteId("quote-abc-123")
                        .setPayInProviderId(42)
                        .build();

                PayoutResponse response = client.stub().payOut(request);

                assertThat(response.hasAccepted()).isTrue();
            }
        }

        @Test
        @DisplayName("Multiple sequential requests should succeed")
        void multipleSequentialRequests_shouldSucceed() throws Exception {
            startServer(NETWORK_PUBLIC_KEY_HEX);
            Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);

            try (var client = createClient(networkSigner)) {
                for (int i = 0; i < 5; i++) {
                    UpdateLimitRequest request = UpdateLimitRequest.newBuilder()
                            .addLimits(UpdateLimitRequest.Limit.newBuilder()
                                    .setVersion(i)
                                    .setCounterpartId(i * 100)
                                    .build())
                            .build();

                    UpdateLimitResponse response = client.stub().updateLimit(request);
                    assertThat(response).isNotNull();
                }
            }
        }

        @Test
        @DisplayName("All ProviderService methods should work with valid signatures")
        void allServiceMethods_shouldSucceed() throws Exception {
            startServer(NETWORK_PUBLIC_KEY_HEX);
            Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);

            try (var client = createClient(networkSigner)) {
                // PayOut
                PayoutResponse payoutResponse = client.stub().payOut(PayoutRequest.newBuilder()
                        .setPaymentId(1)
                        .setPayoutId(1)
                        .setCurrency("USD")
                        .build());
                assertThat(payoutResponse.hasAccepted()).isTrue();

                // UpdatePayment
                UpdatePaymentResponse updatePaymentResponse = client.stub().updatePayment(
                        UpdatePaymentRequest.newBuilder()
                                .setPaymentId(1)
                                .build());
                assertThat(updatePaymentResponse).isNotNull();

                // UpdateLimit
                UpdateLimitResponse updateLimitResponse = client.stub().updateLimit(
                        UpdateLimitRequest.getDefaultInstance());
                assertThat(updateLimitResponse).isNotNull();

                // AppendLedgerEntries
                AppendLedgerEntriesResponse ledgerResponse = client.stub().appendLedgerEntries(
                        AppendLedgerEntriesRequest.getDefaultInstance());
                assertThat(ledgerResponse).isNotNull();

                // ApprovePaymentQuotes
                ApprovePaymentQuoteResponse approveResponse = client.stub().approvePaymentQuotes(
                        ApprovePaymentQuoteRequest.newBuilder()
                                .setPaymentId(1)
                                .build());
                assertThat(approveResponse.hasAccepted()).isTrue();
            }
        }
    }

    // ==================== Authentication Failure Tests ====================

    @Nested
    @DisplayName("Authentication Failure Tests")
    class AuthenticationFailureTests {

        @Test
        @DisplayName("Wrong public key should return UNAUTHENTICATED")
        void wrongPublicKey_shouldReturnUnauthenticated() throws Exception {
            // Server expects NETWORK_PUBLIC_KEY
            startServer(NETWORK_PUBLIC_KEY_HEX);

            // Client signs with OTHER_PRIVATE_KEY (wrong key)
            Signer otherSigner = Signer.fromHex(OTHER_PRIVATE_KEY);

            try (var client = createClient(otherSigner)) {
                StatusRuntimeException exception = assertThrows(
                        StatusRuntimeException.class,
                        () -> client.stub().updateLimit(UpdateLimitRequest.getDefaultInstance()));

                assertThat(exception.getStatus().getCode())
                        .isEqualTo(io.grpc.Status.Code.UNAUTHENTICATED);
                assertThat(exception.getStatus().getDescription())
                        .contains("unknown public key");
            }
        }
    }

    // ==================== Server Configuration Tests ====================

    @Nested
    @DisplayName("Server Configuration Tests")
    class ServerConfigurationTests {

        @Test
        @DisplayName("Server with null networkPublicKey should throw")
        void nullNetworkPublicKey_shouldThrow() {
            assertThatThrownBy(() -> ProviderServer.create(0, null)
                    .withService(new TestProviderServiceImpl())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Server with empty networkPublicKey should throw")
        void emptyNetworkPublicKey_shouldThrow() {
            assertThatThrownBy(() -> ProviderServer.create(0, "")
                    .withService(new TestProviderServiceImpl())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Server without service should throw")
        void missingService_shouldThrow() {
            assertThatThrownBy(() -> ProviderServer.create(8080, NETWORK_PUBLIC_KEY_HEX)
                    .build())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Server with invalid port should throw")
        void invalidPort_shouldThrow() {
            assertThatThrownBy(() -> ProviderServer.create(-1, NETWORK_PUBLIC_KEY_HEX)
                    .withService(new TestProviderServiceImpl())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Server with port > 65535 should throw")
        void portTooHigh_shouldThrow() {
            assertThatThrownBy(() -> ProviderServer.create(65536, NETWORK_PUBLIC_KEY_HEX)
                    .withService(new TestProviderServiceImpl())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================== Message Size Tests ====================

    @Nested
    @DisplayName("Message Size Tests")
    class MessageSizeTests {

        @Test
        @DisplayName("Empty message body should succeed")
        void emptyMessageBody_shouldSucceed() throws Exception {
            startServer(NETWORK_PUBLIC_KEY_HEX);
            Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);

            try (var client = createClient(networkSigner)) {
                UpdateLimitResponse response = client.stub().updateLimit(UpdateLimitRequest.getDefaultInstance());
                assertThat(response).isNotNull();
            }
        }

        @Test
        @DisplayName("Large message with many entries should succeed")
        void largeMessage_shouldSucceed() throws Exception {
            startServer(NETWORK_PUBLIC_KEY_HEX);
            Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);

            try (var client = createClient(networkSigner)) {
                UpdateLimitRequest.Builder requestBuilder = UpdateLimitRequest.newBuilder();
                for (int i = 0; i < 100; i++) {
                    requestBuilder.addLimits(UpdateLimitRequest.Limit.newBuilder()
                            .setVersion(i)
                            .setCounterpartId(i * 1000)
                            .build());
                }
                UpdateLimitRequest request = requestBuilder.build();

                // Verify message is reasonably large
                assertThat(request.toByteArray().length).isGreaterThan(500);

                UpdateLimitResponse response = client.stub().updateLimit(request);
                assertThat(response).isNotNull();
            }
        }
    }
}

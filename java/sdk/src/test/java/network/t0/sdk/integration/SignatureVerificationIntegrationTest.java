package network.t0.sdk.integration;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import network.t0.sdk.common.Headers;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.network.ByteArrayMarshaller;
import network.t0.sdk.provider.ProviderServer;
import network.t0.sdk.proto.tzero.v1.payment.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.concurrent.TimeUnit;

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

    // ==================== Framed-Payload Verification Tests (path 2) ====================

    /**
     * Exercises path 2 of {@link network.t0.sdk.provider.SignatureVerificationInterceptor#verifySignature}:
     * signature computed over the gRPC-framed payload (5-byte frame prefix + protobuf bytes)
     * rather than the unframed protobuf payload.
     *
     * <p>The Java SDK's own {@code NetworkClient} signs the unframed payload (path 1).
     * Other callers — notably the T-0 Network when configured to call this provider via
     * gRPC protocol — sign the framed payload because their signing wiring sits below the
     * gRPC framer. Without these tests, removing path 2 would silently break those callers.
     *
     * <p>Implementation: a test-only client interceptor mirrors
     * {@code NetworkClient.SigningClientInterceptor} structurally, differing only in what
     * is hashed — the framed bytes instead of the unframed bytes.
     */
    @Nested
    @DisplayName("Framed-Payload Signature Verification (path 2)")
    class FramedPathTests {

        @Test
        @DisplayName("Signature over gRPC-framed payload should be accepted")
        void framedSignature_validKey_shouldSucceed() throws Exception {
            startServer(NETWORK_PUBLIC_KEY_HEX);
            Signer networkSigner = Signer.fromHex(NETWORK_PRIVATE_KEY);

            ManagedChannel channel = OkHttpChannelBuilder
                    .forAddress("localhost", server.getPort())
                    .usePlaintext()
                    .build();
            try {
                Channel intercepted = ClientInterceptors.intercept(
                        channel,
                        new FramedPayloadSigningInterceptor(networkSigner, Clock.systemUTC()));
                ProviderServiceGrpc.ProviderServiceBlockingStub stub =
                        ProviderServiceGrpc.newBlockingStub(intercepted);

                PayoutRequest request = PayoutRequest.newBuilder()
                        .setPaymentId(12345)
                        .setPayoutId(67890)
                        .setCurrency("USD")
                        .setClientQuoteId("framed-test")
                        .build();

                PayoutResponse response = stub.payOut(request);
                assertThat(response.hasAccepted()).isTrue();
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("Framed signature with wrong key should still be rejected")
        void framedSignature_wrongKey_shouldReturnUnauthenticated() throws Exception {
            startServer(NETWORK_PUBLIC_KEY_HEX);
            Signer wrongSigner = Signer.fromHex(OTHER_PRIVATE_KEY);

            ManagedChannel channel = OkHttpChannelBuilder
                    .forAddress("localhost", server.getPort())
                    .usePlaintext()
                    .build();
            try {
                Channel intercepted = ClientInterceptors.intercept(
                        channel,
                        new FramedPayloadSigningInterceptor(wrongSigner, Clock.systemUTC()));
                ProviderServiceGrpc.ProviderServiceBlockingStub stub =
                        ProviderServiceGrpc.newBlockingStub(intercepted);

                StatusRuntimeException exception = assertThrows(
                        StatusRuntimeException.class,
                        () -> stub.updateLimit(UpdateLimitRequest.getDefaultInstance()));

                assertThat(exception.getStatus().getCode())
                        .isEqualTo(io.grpc.Status.Code.UNAUTHENTICATED);
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Test-only client interceptor that signs the gRPC-framed payload (5-byte frame
     * prefix + protobuf bytes) instead of the unframed protobuf payload. Mirrors the
     * structure of {@code NetworkClient.SigningClientInterceptor}.
     */
    private static final class FramedPayloadSigningInterceptor implements ClientInterceptor {

        private static final int GRPC_FRAME_HEADER_SIZE = 5;

        private final Signer signer;
        private final Clock clock;

        FramedPayloadSigningInterceptor(Signer signer, Clock clock) {
            this.signer = signer;
            this.clock = clock;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next) {

            MethodDescriptor<byte[], RespT> rawMethod = method.toBuilder(
                    ByteArrayMarshaller.INSTANCE,
                    method.getResponseMarshaller()
            ).build();

            ClientCall<byte[], RespT> rawCall = next.newCall(rawMethod, callOptions);

            return new ClientCall<ReqT, RespT>() {

                private Listener<RespT> responseListener;
                private Metadata headers;
                private boolean started = false;
                private int pendingRequests = 0;

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    this.responseListener = responseListener;
                    this.headers = headers;
                }

                @Override
                public void sendMessage(ReqT message) {
                    byte[] messageBytes;
                    try (InputStream stream = method.getRequestMarshaller().stream(message)) {
                        messageBytes = stream.readAllBytes();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to serialize message for signing", e);
                    }

                    long timestampMs = clock.millis();
                    addFramedSignatureHeaders(messageBytes, timestampMs);

                    if (!started) {
                        rawCall.start(responseListener, headers);
                        started = true;
                        if (pendingRequests > 0) {
                            rawCall.request(pendingRequests);
                            pendingRequests = 0;
                        }
                    }

                    rawCall.sendMessage(messageBytes);
                }

                @Override
                public void halfClose() {
                    if (!started) {
                        long timestampMs = clock.millis();
                        addFramedSignatureHeaders(new byte[0], timestampMs);
                        rawCall.start(responseListener, headers);
                        started = true;
                        if (pendingRequests > 0) {
                            rawCall.request(pendingRequests);
                            pendingRequests = 0;
                        }
                    }
                    rawCall.halfClose();
                }

                @Override
                public void request(int numMessages) {
                    if (started) {
                        rawCall.request(numMessages);
                    } else {
                        pendingRequests += numMessages;
                    }
                }

                @Override
                public void cancel(String message, Throwable cause) {
                    rawCall.cancel(message, cause);
                }

                @Override
                public boolean isReady() {
                    return rawCall.isReady();
                }

                @Override
                public void setMessageCompression(boolean enabled) {
                    rawCall.setMessageCompression(enabled);
                }

                @Override
                public Attributes getAttributes() {
                    return rawCall.getAttributes();
                }

                private void addFramedSignatureHeaders(byte[] messageBytes, long timestampMs) {
                    byte[] timestampBytes = Headers.encodeTimestamp(timestampMs);
                    byte[] framedBytes = grpcFrame(messageBytes);
                    byte[] digest = Keccak256.hash(framedBytes, timestampBytes);
                    SignResult signResult = signer.sign(digest);

                    headers.put(
                            Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER),
                            signResult.getSignatureHex());
                    headers.put(
                            Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER),
                            signResult.getPublicKeyHex());
                    headers.put(
                            Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER),
                            String.valueOf(timestampMs));
                }
            };
        }

        /**
         * Builds {@code [0x00 (compression flag)] [4-byte length, big-endian] [messageBytes]}.
         * Matches {@code SignatureVerificationInterceptor.reconstructGrpcFrame}.
         */
        private static byte[] grpcFrame(byte[] messageBytes) {
            byte[] framed = new byte[GRPC_FRAME_HEADER_SIZE + messageBytes.length];
            framed[0] = 0;
            int len = messageBytes.length;
            framed[1] = (byte) ((len >> 24) & 0xFF);
            framed[2] = (byte) ((len >> 16) & 0xFF);
            framed[3] = (byte) ((len >> 8) & 0xFF);
            framed[4] = (byte) (len & 0xFF);
            System.arraycopy(messageBytes, 0, framed, GRPC_FRAME_HEADER_SIZE, messageBytes.length);
            return framed;
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

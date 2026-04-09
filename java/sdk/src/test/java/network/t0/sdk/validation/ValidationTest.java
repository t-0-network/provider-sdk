package network.t0.sdk.validation;

import build.buf.protovalidate.ValidationResult;
import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import build.buf.protovalidate.exceptions.ValidationException;
import io.grpc.*;
import network.t0.sdk.network.RequestValidationInterceptor;
import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.payment.AppendLedgerEntriesRequest;
import network.t0.sdk.proto.tzero.v1.payment.PayoutResponse;
import network.t0.sdk.proto.tzero.v1.payment.UpdatePaymentResponse;
import network.t0.sdk.provider.ResponseValidationInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = ValidatorFactory.newBuilder().build();
    }

    @Nested
    @DisplayName("Response Validation")
    class ResponseValidation {

        @Test
        @DisplayName("Valid Decimal response passes")
        void validDecimal() throws ValidationException {
            Decimal msg = Decimal.newBuilder().setUnscaled(12345).setExponent(2).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Invalid Decimal response fails - exponent too high")
        void invalidDecimalHigh() throws ValidationException {
            Decimal msg = Decimal.newBuilder().setExponent(100).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getViolations()).isNotEmpty();
        }

        @Test
        @DisplayName("Invalid Decimal response fails - exponent too low")
        void invalidDecimalLow() throws ValidationException {
            Decimal msg = Decimal.newBuilder().setExponent(-20).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(ints = {-8, 0, 8})
        @DisplayName("Boundary exponent values pass")
        void boundaryPass(int exponent) throws ValidationException {
            Decimal msg = Decimal.newBuilder().setExponent(exponent).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {-9, 9})
        @DisplayName("Boundary exponent values fail")
        void boundaryFail(int exponent) throws ValidationException {
            Decimal msg = Decimal.newBuilder().setExponent(exponent).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Valid PayoutResponse passes")
        void validPayoutResponse() throws ValidationException {
            PayoutResponse msg = PayoutResponse.newBuilder()
                    .setAccepted(PayoutResponse.Accepted.getDefaultInstance())
                    .build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Empty response without constraints passes")
        void emptyResponseNoConstraints() throws ValidationException {
            UpdatePaymentResponse msg = UpdatePaymentResponse.getDefaultInstance();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Request Validation")
    class RequestValidation {

        @Test
        @DisplayName("Valid AppendLedgerEntriesRequest passes")
        void validRequest() throws ValidationException {
            AppendLedgerEntriesRequest msg = AppendLedgerEntriesRequest.newBuilder()
                    .addTransactions(AppendLedgerEntriesRequest.Transaction.newBuilder()
                            .setTransactionId(1)
                            .addEntries(AppendLedgerEntriesRequest.LedgerEntry.getDefaultInstance())
                            .setPayout(AppendLedgerEntriesRequest.Transaction.Payout.newBuilder()
                                    .setPaymentId(1)
                                    .build())
                            .build())
                    .build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Invalid AppendLedgerEntriesRequest fails - empty transactions")
        void invalidRequestEmptyTransactions() throws ValidationException {
            AppendLedgerEntriesRequest msg = AppendLedgerEntriesRequest.getDefaultInstance();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getViolations()).isNotEmpty();
        }

        @Test
        @DisplayName("Invalid request fails - transaction_id is zero")
        void invalidRequestZeroTransactionId() throws ValidationException {
            AppendLedgerEntriesRequest msg = AppendLedgerEntriesRequest.newBuilder()
                    .addTransactions(AppendLedgerEntriesRequest.Transaction.newBuilder()
                            .setTransactionId(0)  // must be > 0
                            .addEntries(AppendLedgerEntriesRequest.LedgerEntry.getDefaultInstance())
                            .setPayout(AppendLedgerEntriesRequest.Transaction.Payout.newBuilder()
                                    .setPaymentId(1)
                                    .build())
                            .build())
                    .build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("Valid Decimal request passes")
        void validDecimalRequest() throws ValidationException {
            Decimal msg = Decimal.newBuilder().setUnscaled(100).setExponent(2).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Invalid Decimal request fails")
        void invalidDecimalRequest() throws ValidationException {
            Decimal msg = Decimal.newBuilder().setExponent(100).build();
            ValidationResult result = validator.validate(msg);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Interceptor Tests ====================

    /** Minimal ServerCall fake for testing interceptors without mocking libraries. */
    @SuppressWarnings("unchecked")
    static class FakeServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
        Status closedStatus;
        RespT sentMessage;

        @Override public void close(Status status, Metadata trailers) { closedStatus = status; }
        @Override public void sendMessage(RespT message) { sentMessage = message; }
        @Override public void request(int numMessages) {}
        @Override public void sendHeaders(Metadata headers) {}
        @Override public boolean isCancelled() { return false; }
        @Override public MethodDescriptor<ReqT, RespT> getMethodDescriptor() { return null; }
    }

    /** Minimal ClientCall fake for testing client interceptors. */
    @SuppressWarnings("unchecked")
    static class FakeClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
        ReqT sentMessage;

        @Override public void start(Listener<RespT> responseListener, Metadata headers) {}
        @Override public void request(int numMessages) {}
        @Override public void cancel(String message, Throwable cause) {}
        @Override public void halfClose() {}
        @Override public void sendMessage(ReqT message) { sentMessage = message; }
    }

    @Nested
    @DisplayName("Response Validation Interceptor")
    class ResponseInterceptorTest {

        @Test
        @DisplayName("Invalid response closes call with INTERNAL")
        void invalidResponseClosesWithInternal() {
            var interceptor = new ResponseValidationInterceptor();
            var fakeCall = new FakeServerCall<Decimal, Decimal>();

            ServerCallHandler<Decimal, Decimal> handler = (call, metadata) -> {
                call.sendMessage(Decimal.newBuilder().setExponent(100).build());
                return new ServerCall.Listener<>() {};
            };

            interceptor.interceptCall(fakeCall, new Metadata(), handler);

            assertThat(fakeCall.closedStatus).isNotNull();
            assertThat(fakeCall.closedStatus.getCode()).isEqualTo(Status.Code.INTERNAL);
            assertThat(fakeCall.closedStatus.getDescription()).contains("response validation failed");
            assertThat(fakeCall.sentMessage).isNull();
        }

        @Test
        @DisplayName("Valid response is sent through")
        void validResponsePassesThrough() {
            var interceptor = new ResponseValidationInterceptor();
            var fakeCall = new FakeServerCall<Decimal, Decimal>();

            Decimal validResponse = Decimal.newBuilder().setExponent(2).build();
            ServerCallHandler<Decimal, Decimal> handler = (call, metadata) -> {
                call.sendMessage(validResponse);
                return new ServerCall.Listener<>() {};
            };

            interceptor.interceptCall(fakeCall, new Metadata(), handler);

            assertThat(fakeCall.closedStatus).isNull();
            assertThat(fakeCall.sentMessage).isEqualTo(validResponse);
        }
    }

    @Nested
    @DisplayName("Request Validation Interceptor")
    class RequestInterceptorTest {

        @Test
        @DisplayName("Invalid request throws INVALID_ARGUMENT")
        void invalidRequestThrows() {
            var interceptor = new RequestValidationInterceptor();

            var fakeClientCall = new FakeClientCall<Decimal, Decimal>();
            @SuppressWarnings("unchecked")
            Channel fakeChannel = new Channel() {
                @Override public <RT, RST> ClientCall<RT, RST> newCall(MethodDescriptor<RT, RST> method, CallOptions options) {
                    return (ClientCall<RT, RST>) fakeClientCall;
                }
                @Override public String authority() { return "test"; }
            };

            ClientCall<Decimal, Decimal> wrappedCall = interceptor.interceptCall(
                    MethodDescriptor.<Decimal, Decimal>newBuilder()
                            .setType(MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName("test/Method")
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(Decimal.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(Decimal.getDefaultInstance()))
                            .build(),
                    CallOptions.DEFAULT,
                    fakeChannel);

            wrappedCall.start(new ClientCall.Listener<>() {}, new Metadata());

            assertThatThrownBy(() -> wrappedCall.sendMessage(Decimal.newBuilder().setExponent(100).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(((StatusRuntimeException) e).getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        }

        @Test
        @DisplayName("Valid request is sent through")
        void validRequestPassesThrough() {
            var interceptor = new RequestValidationInterceptor();

            var fakeClientCall = new FakeClientCall<Decimal, Decimal>();
            Channel fakeChannel = new Channel() {
                @Override public <RT, RST> ClientCall<RT, RST> newCall(MethodDescriptor<RT, RST> method, CallOptions options) {
                    return (ClientCall<RT, RST>) fakeClientCall;
                }
                @Override public String authority() { return "test"; }
            };

            ClientCall<Decimal, Decimal> wrappedCall = interceptor.interceptCall(
                    MethodDescriptor.<Decimal, Decimal>newBuilder()
                            .setType(MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName("test/Method")
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(Decimal.getDefaultInstance()))
                            .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(Decimal.getDefaultInstance()))
                            .build(),
                    CallOptions.DEFAULT,
                    fakeChannel);

            wrappedCall.start(new ClientCall.Listener<>() {}, new Metadata());

            Decimal validMsg = Decimal.newBuilder().setExponent(2).build();
            wrappedCall.sendMessage(validMsg);

            assertThat(fakeClientCall.sentMessage).isEqualTo(validMsg);
        }
    }
}

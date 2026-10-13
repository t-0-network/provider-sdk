package network.t0.provider.internal;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import network.t0.sdk.proto.ivms101.NaturalPersonNameTypeCode;
import network.t0.sdk.proto.ivms101.Person;
import network.t0.sdk.proto.tzero.v1.common.Decimal;
import network.t0.sdk.proto.tzero.v1.payment.CreatePaymentRequest;
import network.t0.sdk.proto.tzero.v1.payment.CreatePaymentResponse;
import network.t0.sdk.proto.tzero.v1.payment.NetworkServiceGrpc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The payment submission demo against a recording channel: the stub's transport
 * answers the unary call itself, so nothing talks to a server and no in-process
 * transport test dependency is needed.
 */
class SubmitPaymentTest {

    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]+");
    private static final String SandboxIban = "GB29NWBK60161331926819"; // the sandbox IBAN the demo pins

    /** The channel the demo's blocking stub runs on. It records requests and replies. */
    private static final class RecordingChannel extends Channel {

        final List<CreatePaymentRequest> requests = new ArrayList<>();
        CreatePaymentResponse response = CreatePaymentResponse.getDefaultInstance();
        Status failure;

        NetworkServiceGrpc.NetworkServiceBlockingStub stub() {
            return NetworkServiceGrpc.newBlockingStub(this);
        }

        @Override
        public String authority() {
            return "test-authority";
        }

        @Override
        @SuppressWarnings("unchecked")
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
            return new ClientCall<>() {
                private ClientCall.Listener<RespT> listener;

                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    this.listener = responseListener;
                    responseListener.onReady();
                }

                @Override
                public void request(int numMessages) {
                    // Unary: the reply is delivered at halfClose, demand is not gated.
                }

                @Override
                public void sendMessage(ReqT message) {
                    if (method == NetworkServiceGrpc.getCreatePaymentMethod()) {
                        requests.add((CreatePaymentRequest) message);
                    }
                }

                @Override
                public void halfClose() {
                    if (failure != null) {
                        listener.onClose(failure, new Metadata());
                        return;
                    }
                    listener.onHeaders(new Metadata());
                    listener.onMessage((RespT) response);
                    listener.onClose(Status.OK, new Metadata());
                }

                @Override
                public void cancel(String message, Throwable cause) {
                    // The demo never cancels a unary call.
                }
            };
        }
    }

    private static RecordingChannel channel(CreatePaymentResponse response) {
        var channel = new RecordingChannel();
        channel.response = response;
        return channel;
    }

    @Test
    void buildsOneSchemaValidRequestAndHandlesAccepted() {
        // The response a real accept carries: Accepted marks its amounts required.
        var channel = channel(CreatePaymentResponse.newBuilder()
                .setAccepted(CreatePaymentResponse.Accepted.newBuilder()
                        .setPaymentId(42)
                        .setSettlementAmount(Decimal.newBuilder().setUnscaled(1250).setExponent(-2)) // 12.50 USD
                        .setPayoutAmount(Decimal.newBuilder().setUnscaled(10).setExponent(0))
                        .setPayoutProviderId(1))
                .build());

        assertDoesNotThrow(() -> SubmitPayment.submit(channel.stub()));

        assertEquals(1, channel.requests.size(), "exactly one RPC, no retry loop");
        CreatePaymentRequest request = channel.requests.get(0);

        assertAll(
                () -> assertDoesNotThrow(() -> UUID.fromString(request.getPaymentClientId())),
                () -> assertEquals(10, request.getAmount().getPayOutAmount().getUnscaled()),
                () -> assertEquals(0, request.getAmount().getPayOutAmount().getExponent()),
                () -> assertEquals("GBP", request.getCurrency()),
                // The same checks the network's buf.validate rules apply to the SEPA demo data.
                () -> assertTrue(IBAN.matcher(request.getPaymentDetails().getSepa().getIban()).matches()),
                () -> assertTrue(request.getPaymentDetails().getSepa().getIban().length() >= 15
                                && request.getPaymentDetails().getSepa().getIban().length() <= 34,
                        "IBAN length is between 15 and 34"),
                () -> assertEquals(SandboxIban, request.getPaymentDetails().getSepa().getIban()),
                () -> assertEquals("Max Mustermann", request.getPaymentDetails().getSepa().getBeneficiaryName()),
                // travel_rule_data is required by the contract.
                () -> assertTrue(request.hasTravelRuleData()),
                () -> assertEquals(1, request.getTravelRuleData().getOriginatorCount()),
                () -> assertEquals(1, request.getTravelRuleData().getBeneficiaryCount()));

        assertLegalName(request.getTravelRuleData().getOriginator(0), "Doe", "John");
        assertLegalName(request.getTravelRuleData().getBeneficiary(0), "Mustermann", "Max");
        // The SEPA beneficiary name and the IVMS101 beneficiary describe the same person.
        var beneficiary = request.getTravelRuleData().getBeneficiary(0)
                .getNaturalPerson().getName().getNameIdentifiers(0);
        assertEquals(request.getPaymentDetails().getSepa().getBeneficiaryName(),
                beneficiary.getSecondaryIdentifier() + " " + beneficiary.getPrimaryIdentifier());
    }

    private static void assertLegalName(Person person, String primary, String secondary) {
        var identifiers = person.getNaturalPerson().getName().getNameIdentifiersList();
        assertEquals(1, identifiers.size());
        assertEquals(NaturalPersonNameTypeCode.NATURAL_PERSON_NAME_TYPE_CODE_LEGL,
                identifiers.get(0).getNameIdentifierType());
        assertEquals(primary, identifiers.get(0).getPrimaryIdentifier());
        assertEquals(secondary, identifiers.get(0).getSecondaryIdentifier());
    }

    @Test
    void failureAndUnknownResultsDoNotThrow() {
        var channel = channel(CreatePaymentResponse.newBuilder()
                .setFailure(CreatePaymentResponse.Failure.newBuilder()
                        .setReason(CreatePaymentResponse.Failure.Reason.REASON_QUOTE_NOT_FOUND))
                .build());
        assertDoesNotThrow(() -> SubmitPayment.submit(channel.stub()));

        // A response without a result (a deprecated or unset oneof arm) is unknown,
        // not a crash and not a silent success.
        assertDoesNotThrow(() -> SubmitPayment.submit(channel(CreatePaymentResponse.getDefaultInstance()).stub()));
    }

    @Test
    void rpcErrorIsCaughtAndNotRetried() {
        var channel = new RecordingChannel();
        channel.failure = Status.UNAVAILABLE.withDescription("connection refused");

        assertDoesNotThrow(() -> SubmitPayment.submit(channel.stub()));

        assertEquals(1, channel.requests.size(), "the demo logs the failure and returns, it does not retry");
    }
}

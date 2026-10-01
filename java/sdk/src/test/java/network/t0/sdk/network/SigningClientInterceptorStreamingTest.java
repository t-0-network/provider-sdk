package network.t0.sdk.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import network.t0.sdk.common.Headers;
import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.DigestSigner;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link NetworkClient.SigningClientInterceptor} over a recording fake of the underlying call.
 * See {@code docs/STREAMING.md}.
 */
class SigningClientInterceptorStreamingTest {

    private static final String PRIVATE_KEY_HEX = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final long FIXED_TIMESTAMP_MS = 1706000000000L;

    private static final Metadata.Key<String> SIGNATURE =
            Metadata.Key.of(Headers.SIGNATURE, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> PUBLIC_KEY =
            Metadata.Key.of(Headers.PUBLIC_KEY, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SIGNATURE_TIMESTAMP =
            Metadata.Key.of(Headers.SIGNATURE_TIMESTAMP, Metadata.ASCII_STRING_MARSHALLER);

    private static final MethodDescriptor<StringValue, StringValue> UNARY =
            stringMethod(MethodType.UNARY, "Unary");
    private static final MethodDescriptor<StringValue, StringValue> CLIENT_STREAM =
            stringMethod(MethodType.CLIENT_STREAMING, "ClientStream");
    private static final MethodDescriptor<StringValue, StringValue> SERVER_STREAM =
            stringMethod(MethodType.SERVER_STREAMING, "ServerStream");
    private static final MethodDescriptor<StringValue, StringValue> BIDI_STREAM =
            stringMethod(MethodType.BIDI_STREAMING, "BidiStream");

    private Signer signer;
    private FakeChannel channel;
    private Channel intercepted;
    // The calls' executor: its tasks (the listener's first onReady) run only when a test runs them.
    private final List<Runnable> callbackTasks = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        signer = Signer.fromHex(PRIVATE_KEY_HEX);
        channel = new FakeChannel();
        intercepted = interceptedAt(FIXED_TIMESTAMP_MS);
    }

    // ==================== Client streaming ====================

    @Test
    @DisplayName("Client stream: one signature, over the first message, and every message sent as-is")
    void clientStreamSignsOnlyTheFirstMessage() {
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        call.request(1);
        call.sendMessage(value("m1"));

        RecordingCall raw = channel.lastCall();
        // Assert before the next message exists: buffering until halfClose would fail here.
        assertThat(raw.events).containsExactly("start", "request:1", "send");
        assertThat(raw.sent).containsExactly(bytes("m1"));
        assertThat(verifies(raw.headers, bytes("m1"))).isTrue();

        call.sendMessage(value("m2"));
        call.sendMessage(value("m3"));
        call.halfClose();

        assertThat(raw.events).containsExactly("start", "request:1", "send", "send", "send", "halfClose");
        assertThat(raw.sent).containsExactly(bytes("m1"), bytes("m2"), bytes("m3"));

        assertThat(raw.headers.getAll(SIGNATURE)).hasSize(1);
        assertThat(raw.headers.get(SIGNATURE_TIMESTAMP)).isEqualTo(String.valueOf(FIXED_TIMESTAMP_MS));
        assertThat(raw.headers.get(PUBLIC_KEY)).isEqualTo("0x" + HexUtils.bytesToHex(signer.getPublicKey()));

        assertThat(verifies(raw.headers, bytes("m1"))).isTrue();
        assertThat(verifies(raw.headers, bytes("m2"))).isFalse();
        assertThat(verifies(raw.headers, concat(bytes("m1"), bytes("m2")))).isFalse();
        // Signed above the gRPC framer: not the framed first message.
        assertThat(verifies(raw.headers, frame(bytes("m1")))).isFalse();
    }

    @Test
    @DisplayName("A message sent from another thread while the first one goes out waits for it")
    void messageFromAnotherThreadWaitsForTheFirstMessage() throws Exception {
        channel.blockFirstSend();
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        RecordingCall raw = channel.lastCall();

        Thread first = new Thread(() -> call.sendMessage(value("m1")));
        Thread second = new Thread(() -> call.sendMessage(value("m2")));
        first.setDaemon(true); // a hang must not keep the test JVM alive
        second.setDaemon(true);
        try {
            first.start();
            raw.awaitFirstSendEntered();
            second.start();
            // m2 waits for the call's lock, which m1 holds until it is out.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (second.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(second.getState()).isEqualTo(Thread.State.BLOCKED);
            assertThat(raw.sent).isEmpty();
        } finally {
            raw.releaseSend();
        }
        first.join(5_000);
        second.join(5_000);

        assertThat(raw.sent).containsExactly(bytes("m1"), bytes("m2"));
        assertThat(verifies(raw.headers, bytes("m1"))).isTrue();
    }

    @Test
    @DisplayName("Client stream: headers are set once, before start, with no duplicate X-Signature")
    void clientStreamSetsHeadersOnce() {
        Metadata headers = new Metadata();
        headers.put(SIGNATURE, "0xstale"); // replaced, not appended to

        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(new RecordingListener<>(), headers);
        call.sendMessage(value("m1"));
        call.sendMessage(value("m2"));
        call.sendMessage(value("m3"));
        call.halfClose();

        RecordingCall raw = channel.lastCall();
        assertThat(raw.headers).isSameAs(headers);
        // The Metadata handed to start is not touched again: the transport may serialize it later.
        assertThat(raw.headers.toString()).isEqualTo(raw.headersAtStart);
        assertThat(raw.headers.getAll(SIGNATURE)).hasSize(1);
        assertThat(raw.headers.getAll(PUBLIC_KEY)).hasSize(1);
        assertThat(raw.headers.getAll(SIGNATURE_TIMESTAMP)).hasSize(1);
        assertThat(raw.headers.get(SIGNATURE)).isNotEqualTo("0xstale");
    }

    @Test
    @DisplayName("Empty client stream signs empty bytes and starts the call on halfClose")
    void emptyClientStreamSignsEmptyBytes() {
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        call.request(1);
        call.halfClose();

        RecordingCall raw = channel.lastCall();
        assertThat(raw.events).containsExactly("start", "request:1", "halfClose");
        assertThat(verifies(raw.headers, new byte[0])).isTrue();

        // Same key and timestamp as the empty-client-stream vector, so the same signature.
        JsonObject vec = streamSigningCase("empty-client-stream");
        assertThat(vec.get("timestamp_ms").getAsLong()).isEqualTo(FIXED_TIMESTAMP_MS);
        assertThat(signature64Hex(raw.headers)).isEqualTo(vec.get("expected_signature").getAsString());
    }

    @Test
    @DisplayName("A DigestSigner other than Signer signs the call: one digest, headers that verify")
    void anyDigestSignerSignsTheCall() {
        List<byte[]> digests = Collections.synchronizedList(new ArrayList<>());
        DigestSigner custom = new DigestSigner() {
            @Override
            public SignResult sign(byte[] digest) {
                digests.add(digest);
                return signer.sign(digest);
            }

            @Override
            public byte[] getPublicKey() {
                return signer.getPublicKey();
            }
        };
        Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIMESTAMP_MS), ZoneOffset.UTC);
        Channel withCustom = ClientInterceptors.intercept(channel, new NetworkClient.SigningClientInterceptor(custom, clock));

        ClientCall<StringValue, StringValue> call = withCustom.newCall(CLIENT_STREAM, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        call.sendMessage(value("m1"));
        call.sendMessage(value("m2"));

        assertThat(digests).hasSize(1);
        assertThat(verifies(channel.lastCall().headers, bytes("m1"))).isTrue();
    }

    // ==================== Server streaming ====================

    @Test
    @DisplayName("Server stream: one signature, over the request's unframed bytes")
    void serverStreamSignsTheRequest() {
        ClientCall<StringValue, StringValue> call = intercepted.newCall(SERVER_STREAM, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        call.request(1);
        call.sendMessage(value("hello"));
        call.halfClose();

        RecordingCall raw = channel.lastCall();
        assertThat(raw.method.getType()).isEqualTo(MethodType.SERVER_STREAMING);
        assertThat(raw.events).containsExactly("start", "request:1", "send", "halfClose");
        assertThat(raw.sent).containsExactly(bytes("hello"));
        assertThat(raw.headers.getAll(SIGNATURE)).hasSize(1);
        assertThat(raw.headers.getAll(PUBLIC_KEY)).hasSize(1);
        assertThat(raw.headers.getAll(SIGNATURE_TIMESTAMP)).hasSize(1);
        assertThat(verifies(raw.headers, bytes("hello"))).isTrue();
        assertThat(verifies(raw.headers, frame(bytes("hello")))).isFalse();
    }

    // ==================== Bidirectional streaming ====================

    @Test
    @DisplayName("Bidi stream: closed with UNIMPLEMENTED on start, no underlying call is created")
    void bidiStreamIsRefused() {
        RecordingListener<StringValue> listener = new RecordingListener<>();
        ClientCall<StringValue, StringValue> call = intercepted.newCall(BIDI_STREAM, callOptions());
        call.start(listener, new Metadata());

        // Delivered on the call's executor, not inside start().
        assertThat(listener.closeStatus).isNull();
        runCallbackTasks();
        assertThat(listener.closeStatus).isNotNull();
        assertThat(listener.closeStatus.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(listener.closeStatus.getDescription()).contains("bidirectional");
        assertThat(channel.lastCall()).isNull();

        assertThat(call.isReady()).isFalse();
        call.request(1);
        call.sendMessage(value("m1"));
        call.halfClose();
        call.cancel("done", null);
        assertThat(channel.lastCall()).isNull();
    }

    // ==================== Compression ====================

    @ParameterizedTest
    @EnumSource(value = MethodType.class, names = {"UNARY", "CLIENT_STREAMING", "SERVER_STREAMING"})
    @DisplayName("A call with a compressor is refused with UNIMPLEMENTED; no underlying call is created")
    void compressedCallIsRefused(MethodType type) {
        RecordingListener<StringValue> listener = new RecordingListener<>();
        ClientCall<StringValue, StringValue> call =
                intercepted.newCall(stringMethod(type, "Compressed"), callOptions().withCompression("gzip"));
        call.start(listener, new Metadata());
        call.request(1);
        call.sendMessage(value("m1"));
        call.halfClose();

        assertThat(listener.closeStatus).isNull();
        runCallbackTasks();
        assertThat(listener.closeStatus.getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(listener.closeStatus.getDescription()).isEqualTo("compressed requests are not supported");
        assertThat(channel.lastCall()).isNull();
    }

    @Test
    @DisplayName("The identity compressor sends the message as signed: the call goes ahead")
    void identityCompressionIsAllowed() {
        ClientCall<StringValue, StringValue> call =
                intercepted.newCall(CLIENT_STREAM, callOptions().withCompression("identity"));
        call.start(new RecordingListener<>(), new Metadata());
        call.sendMessage(value("m1"));

        RecordingCall raw = channel.lastCall();
        assertThat(raw.events).containsExactly("start", "send");
        assertThat(verifies(raw.headers, bytes("m1"))).isTrue();
    }

    // ==================== Before the first message ====================

    @Test
    @DisplayName("isReady() is true before the first message, then reflects the started call")
    void isReadyBeforeStartIsTrue() {
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(new RecordingListener<>(), new Metadata());

        // The fake throws on isReady() before start, like a real call.
        assertThat(call.isReady()).isTrue();
        assertThat(channel.lastCall().events).isEmpty();

        call.sendMessage(value("m1"));
        RecordingCall raw = channel.lastCall();
        raw.ready = false;
        assertThat(call.isReady()).isFalse();
        raw.ready = true;
        assertThat(call.isReady()).isTrue();
    }

    @Test
    @DisplayName("cancel() before the first message sends nothing and closes the listener once with CANCELLED")
    void cancelBeforeTheFirstMessageClosesTheListenerOnce() {
        RecordingListener<StringValue> listener = new RecordingListener<>();
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(listener, new Metadata());
        call.request(1);
        call.cancel("caller gave up", null);
        call.cancel("again", null);
        runCallbackTasks(); // the first onReady, which a cancelled call does not deliver

        RecordingCall raw = channel.lastCall();
        assertThat(raw.starts).isZero();
        assertThat(raw.events).containsExactly("cancel", "cancel");
        assertThat(listener.events).containsExactly("onClose");
        assertThat(listener.closeStatus.getCode()).isEqualTo(Status.Code.CANCELLED);
        assertThat(listener.closeStatus.getDescription()).isEqualTo("caller gave up");
    }

    @Test
    @DisplayName("cancel() before start() does not throw and starts nothing")
    void cancelBeforeStartStartsNothing() {
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.cancel("never started", null);

        // No listener to notify: the cancel just reaches the unstarted call, as ClientCall allows.
        RecordingCall raw = channel.lastCall();
        assertThat(raw.events).containsExactly("cancel");
        assertThat(raw.headers).isNull();
    }

    @Test
    @DisplayName("The listener gets onReady before the first message, on the call's executor")
    void onReadyBeforeTheFirstMessage() {
        RecordingListener<StringValue> listener = new RecordingListener<>();
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(listener, new Metadata());
        assertThat(listener.events).isEmpty();

        runCallbackTasks();
        assertThat(listener.events).containsExactly("onReady");
        assertThat(channel.lastCall().events).isEmpty();
    }

    @Test
    @DisplayName("A sender that sends only on onReady gets its first onReady, and its first message starts the call")
    void onReadyDrivenSenderStartsTheCall() {
        RecordingListener<StringValue> listener = new RecordingListener<>();
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        listener.onReadyAction = () -> {
            while (call.isReady() && listener.sent < 2) {
                call.sendMessage(value("m" + ++listener.sent));
                channel.lastCall().ready = listener.sent < 2;
            }
        };
        call.start(listener, new Metadata());
        call.request(1);

        runCallbackTasks();
        RecordingCall raw = channel.lastCall();
        assertThat(raw.events).containsExactly("start", "request:1", "send", "send");
        assertThat(verifies(raw.headers, bytes("m1"))).isTrue();
    }

    @Test
    @DisplayName("No onReady before the first message once it was sent: the started call's onReady follows")
    void noEarlyOnReadyAfterTheFirstMessage() {
        RecordingListener<StringValue> listener = new RecordingListener<>();
        ClientCall<StringValue, StringValue> call = intercepted.newCall(CLIENT_STREAM, callOptions());
        call.start(listener, new Metadata());
        call.sendMessage(value("m1"));

        runCallbackTasks();
        assertThat(listener.events).isEmpty();
    }

    // ==================== Unary ====================

    @Test
    @DisplayName("Unary: signed over the request, started on sendMessage, earlier request() calls added up and passed on")
    void unaryIsUnchanged() {
        ClientCall<StringValue, StringValue> call = intercepted.newCall(UNARY, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        call.request(1);
        call.request(1);
        call.sendMessage(value("request"));
        call.halfClose();

        RecordingCall raw = channel.lastCall();
        assertThat(raw.method.getType()).isEqualTo(MethodType.UNARY);
        assertThat(raw.events).containsExactly("start", "request:2", "send", "halfClose");
        assertThat(raw.sent).containsExactly(bytes("request"));
        assertThat(raw.headers.getAll(SIGNATURE)).hasSize(1);
        assertThat(verifies(raw.headers, bytes("request"))).isTrue();
    }

    // ==================== Cross-language vector ====================

    /** The vector's payloads, sent at its timestamp, give its signature and, framed, its body. */
    @Test
    @DisplayName("Emits the grpc-client-stream-unframed vector's signature")
    void emitsUnframedStreamVectorSignature() {
        JsonObject vec = streamSigningCase("grpc-client-stream-unframed");
        assertThat(vec.get("covers").getAsString()).isEqualTo("first_payload");
        byte[] body = HexUtils.hexToBytes(vec.get("body_hex").getAsString());
        List<byte[]> payloads = splitFrames(body);
        assertThat(payloads).hasSize(3);

        MethodDescriptor<byte[], byte[]> rawBytesMethod = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodType.CLIENT_STREAMING)
                .setFullMethodName("test.v1.StreamTest/ClientStream")
                .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
                .setResponseMarshaller(ByteArrayMarshaller.INSTANCE)
                .build();
        ClientCall<byte[], byte[]> call = interceptedAt(vec.get("timestamp_ms").getAsLong())
                .newCall(rawBytesMethod, callOptions());
        call.start(new RecordingListener<>(), new Metadata());
        payloads.forEach(call::sendMessage);
        call.halfClose();

        RecordingCall raw = channel.lastCall();
        assertThat(signature64Hex(raw.headers)).isEqualTo(vec.get("expected_signature").getAsString());
        assertThat(raw.headers.get(SIGNATURE_TIMESTAMP)).isEqualTo(vec.get("timestamp_ms").getAsString());
        assertThat(HexUtils.bytesToHex((byte[]) raw.sent.get(0))).isEqualTo(vec.get("signed_hex").getAsString());

        ByteArrayOutputStream onTheWire = new ByteArrayOutputStream();
        raw.sent.forEach(m -> onTheWire.writeBytes(frame((byte[]) m)));
        assertThat(onTheWire.toByteArray()).isEqualTo(body);
    }

    // ==================== Helpers ====================

    private CallOptions callOptions() {
        return CallOptions.DEFAULT.withExecutor(callbackTasks::add);
    }

    private void runCallbackTasks() {
        List<Runnable> tasks;
        synchronized (callbackTasks) {
            tasks = new ArrayList<>(callbackTasks);
            callbackTasks.clear();
        }
        tasks.forEach(Runnable::run);
    }

    private Channel interceptedAt(long timestampMs) {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(timestampMs), ZoneOffset.UTC);
        return ClientInterceptors.intercept(channel, new NetworkClient.SigningClientInterceptor(signer, clock));
    }

    private static MethodDescriptor<StringValue, StringValue> stringMethod(MethodType type, String name) {
        return MethodDescriptor.<StringValue, StringValue>newBuilder()
                .setType(type)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("test.v1.StreamTest", name))
                .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                .build();
    }

    private static StringValue value(String s) {
        return StringValue.of(s);
    }

    private static byte[] bytes(String s) {
        return StringValue.of(s).toByteArray();
    }

    private boolean verifies(Metadata headers, byte[] signed) {
        byte[] signature = HexUtils.hexToBytes(HexUtils.stripHexPrefix(headers.get(SIGNATURE)));
        long timestampMs = Long.parseLong(headers.get(SIGNATURE_TIMESTAMP));
        byte[] digest = Keccak256.hash(signed, Headers.encodeTimestamp(timestampMs));
        return SignatureVerifier.verify(signer.getPublicKey(), digest, signature);
    }

    private static String signature64Hex(Metadata headers) {
        byte[] signature = HexUtils.hexToBytes(HexUtils.stripHexPrefix(headers.get(SIGNATURE)));
        return HexUtils.bytesToHex(Arrays.copyOf(signature, 64));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        return ByteBuffer.allocate(a.length + b.length).put(a).put(b).array();
    }

    /** The 5-byte gRPC prefix (uncompressed, big-endian length) followed by the message. */
    private static byte[] frame(byte[] message) {
        return ByteBuffer.allocate(5 + message.length).put((byte) 0).putInt(message.length).put(message).array();
    }

    private static List<byte[]> splitFrames(byte[] body) {
        List<byte[]> payloads = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(body);
        while (buf.hasRemaining()) {
            buf.get(); // flags
            byte[] payload = new byte[buf.getInt()];
            buf.get(payload);
            payloads.add(payload);
        }
        return payloads;
    }

    private static JsonObject streamSigningCase(String name) {
        try {
            // Gradle runs tests with CWD = project root (java/sdk/)
            String json = Files.readString(Path.of("../../cross_test/test_vectors.json"));
            for (var element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("stream_signing_cases")) {
                if (element.getAsJsonObject().get("name").getAsString().equals(name)) {
                    return element.getAsJsonObject();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        throw new AssertionError("no stream_signing_cases entry named " + name);
    }

    // ==================== Fakes ====================

    /** Hands out {@link RecordingCall}s and keeps the last one. */
    static final class FakeChannel extends Channel {
        private volatile RecordingCall lastCall;
        private boolean blockFirstSend;

        RecordingCall lastCall() {
            return lastCall;
        }

        /** Calls handed out from now on block in their first sendMessage() until {@link RecordingCall#releaseSend()}. */
        void blockFirstSend() {
            blockFirstSend = true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
            lastCall = new RecordingCall(method, blockFirstSend);
            return (ClientCall<ReqT, RespT>) lastCall;
        }

        @Override
        public String authority() {
            return "fake";
        }
    }

    /**
     * Minimal ClientCall fake that records what the interceptor does to the underlying call. Like
     * a real call, it refuses a second start().
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class RecordingCall extends ClientCall<Object, Object> {
        final MethodDescriptor<?, ?> method;
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<Object> sent = Collections.synchronizedList(new ArrayList<>());
        volatile Listener listener;
        volatile Metadata headers;
        volatile String headersAtStart;
        volatile int starts;
        volatile boolean ready;
        private final CountDownLatch firstSendEntered = new CountDownLatch(1);
        private final CountDownLatch firstSendReleased;

        RecordingCall(MethodDescriptor<?, ?> method, boolean blockFirstSend) {
            this.method = method;
            this.firstSendReleased = new CountDownLatch(blockFirstSend ? 1 : 0);
        }

        @Override
        public void start(Listener responseListener, Metadata headers) {
            if (++starts > 1) {
                throw new IllegalStateException("Already started");
            }
            events.add("start");
            this.listener = responseListener;
            this.headers = headers;
            this.headersAtStart = headers.toString();
        }

        @Override
        public void request(int numMessages) {
            events.add("request:" + numMessages);
        }

        @Override
        public void cancel(String message, Throwable cause) {
            events.add("cancel");
        }

        @Override
        public void halfClose() {
            events.add("halfClose");
        }

        void awaitFirstSendEntered() throws InterruptedException {
            assertThat(firstSendEntered.await(5, TimeUnit.SECONDS)).as("sendMessage() entered").isTrue();
        }

        void releaseSend() {
            firstSendReleased.countDown();
        }

        @Override
        public void sendMessage(Object message) {
            if (firstSendEntered.getCount() > 0) {
                firstSendEntered.countDown();
                try {
                    firstSendReleased.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            events.add("send");
            sent.add(message);
        }

        @Override
        public boolean isReady() {
            // A real call throws here before start.
            if (listener == null) {
                throw new IllegalStateException("isReady() called before start()");
            }
            return ready;
        }
    }

    static final class RecordingListener<T> extends ClientCall.Listener<T> {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        volatile Status closeStatus;
        Runnable onReadyAction = () -> { };
        int sent;

        @Override
        public void onHeaders(Metadata headers) {
            events.add("onHeaders");
        }

        @Override
        public void onMessage(T message) {
            events.add("onMessage");
        }

        @Override
        public void onReady() {
            events.add("onReady");
            onReadyAction.run();
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            events.add("onClose");
            closeStatus = status;
        }
    }
}

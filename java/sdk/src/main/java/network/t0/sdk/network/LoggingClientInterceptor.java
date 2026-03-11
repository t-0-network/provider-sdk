package network.t0.sdk.network;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client interceptor that logs request and response information.
 *
 * <p>This interceptor is useful for debugging and monitoring gRPC calls.
 * It logs method names, timing information, and status codes.
 *
 * <p>Log levels:
 * <ul>
 *   <li>INFO: Request start and completion with timing</li>
 *   <li>DEBUG: Headers and detailed status information</li>
 *   <li>WARN: Request failures and errors</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // The interceptor is automatically included when logging is enabled
 * // Logs will appear at DEBUG level by default
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Each call creates independent state.
 */
public final class LoggingClientInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingClientInterceptor.class);

    /**
     * Creates a new logging interceptor with default settings.
     */
    public LoggingClientInterceptor() {
        // Default constructor
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        final long startTime = System.nanoTime();
        final String methodName = method.getFullMethodName();

        log.debug("Starting gRPC call: {}", methodName);

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                log.debug("Request headers for {}: {}", methodName, headers.keys());

                Listener<RespT> loggingListener = new ForwardingClientCallListener
                        .SimpleForwardingClientCallListener<RespT>(responseListener) {

                    @Override
                    public void onHeaders(Metadata headers) {
                        log.debug("Response headers for {}: {}", methodName, headers.keys());
                        super.onHeaders(headers);
                    }

                    @Override
                    public void onMessage(RespT message) {
                        log.debug("Received response for {}", methodName);
                        super.onMessage(message);
                    }

                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long durationNanos = System.nanoTime() - startTime;
                        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);

                        if (status.isOk()) {
                            log.debug("gRPC call completed: {} ({}ms)", methodName, durationMs);
                        } else {
                            log.warn("gRPC call failed: {} - {} ({}ms)",
                                    methodName, status, durationMs);
                        }
                        super.onClose(status, trailers);
                    }
                };

                super.start(loggingListener, headers);
            }

            @Override
            public void sendMessage(ReqT message) {
                log.debug("Sending message for {}", methodName);
                super.sendMessage(message);
            }
        };
    }
}

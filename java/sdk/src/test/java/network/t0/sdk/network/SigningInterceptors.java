package network.t0.sdk.network;

import io.grpc.ClientInterceptor;
import network.t0.sdk.crypto.Signer;

import java.time.Clock;

/**
 * Hands tests outside this package the SDK's signing interceptor with a clock of their choice,
 * without widening the interceptor's visibility in the published API.
 */
public final class SigningInterceptors {

    private SigningInterceptors() {
    }

    public static ClientInterceptor withClock(Signer signer, Clock clock) {
        return new NetworkClient.SigningClientInterceptor(signer, clock);
    }
}

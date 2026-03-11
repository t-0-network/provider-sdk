package network.t0.provider;

/**
 * Configuration record for the T-0 Network Provider.
 */
public record Config(
        String providerPrivateKey,
        String networkPublicKey,
        String tzeroEndpoint,
        int port,
        long quotePublishingIntervalMs
) {
}

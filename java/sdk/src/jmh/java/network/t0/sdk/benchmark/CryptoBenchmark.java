package network.t0.sdk.benchmark;

import network.t0.sdk.common.HexUtils;
import network.t0.sdk.crypto.Keccak256;
import network.t0.sdk.crypto.SignResult;
import network.t0.sdk.crypto.SignatureVerifier;
import network.t0.sdk.crypto.Signer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for cryptographic operations in the T-0 SDK.
 *
 * <p>Run with: ./gradlew jmh
 *
 * <p>These benchmarks measure:
 * <ul>
 *   <li>Keccak-256 hashing throughput</li>
 *   <li>ECDSA signing throughput</li>
 *   <li>ECDSA verification throughput</li>
 *   <li>Hex encoding/decoding throughput</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CryptoBenchmark {

    private static final String TEST_PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";

    private Signer signer;
    private byte[] smallMessage;      // 100 bytes
    private byte[] mediumMessage;     // 1KB
    private byte[] largeMessage;      // 100KB
    private byte[] digest;
    private byte[] signature;
    private byte[] publicKey;
    private String hexString;

    @Setup
    public void setup() {
        signer = Signer.fromHex(TEST_PRIVATE_KEY);

        Random random = new Random(42);  // Fixed seed for reproducibility

        smallMessage = new byte[100];
        random.nextBytes(smallMessage);

        mediumMessage = new byte[1024];
        random.nextBytes(mediumMessage);

        largeMessage = new byte[100 * 1024];
        random.nextBytes(largeMessage);

        digest = Keccak256.hash(mediumMessage);
        SignResult signResult = signer.sign(digest);
        signature = signResult.getSignature();
        publicKey = signResult.getPublicKey();

        hexString = HexUtils.bytesToHex(mediumMessage);
    }

    // ==================== Keccak-256 Hashing ====================

    @Benchmark
    public byte[] hashSmall() {
        return Keccak256.hash(smallMessage);
    }

    @Benchmark
    public byte[] hashMedium() {
        return Keccak256.hash(mediumMessage);
    }

    @Benchmark
    public byte[] hashLarge() {
        return Keccak256.hash(largeMessage);
    }

    // ==================== ECDSA Signing ====================

    @Benchmark
    public SignResult sign() {
        return signer.sign(digest);
    }

    // ==================== ECDSA Verification ====================

    @Benchmark
    public boolean verify() {
        return SignatureVerifier.verify(publicKey, digest, signature);
    }

    // ==================== Hex Encoding/Decoding ====================

    @Benchmark
    public String hexEncode() {
        return HexUtils.bytesToHex(mediumMessage);
    }

    @Benchmark
    public byte[] hexDecode() {
        return HexUtils.hexToBytes(hexString);
    }

    // ==================== Combined Operations ====================

    @Benchmark
    public SignResult hashAndSign() {
        byte[] hash = Keccak256.hash(mediumMessage);
        return signer.sign(hash);
    }

    @Benchmark
    public boolean hashAndVerify() {
        byte[] hash = Keccak256.hash(mediumMessage);
        return SignatureVerifier.verify(publicKey, hash, signature);
    }
}

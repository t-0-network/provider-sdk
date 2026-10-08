package network.t0.sdk.kycsharing;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import network.t0.sdk.crypto.Signer;
import network.t0.sdk.network.BlockingNetworkClient;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.DownloadFileRequest;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.KycFileServiceGrpc;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.UploadFileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The KYC file helpers against {@code go_helper serve}. One process per test. gRPC only:
 * the Java client has no Connect transport.
 */
@Timeout(30)
class CrossServerTests {

    private static final String PRIVATE_KEY = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private static final String PUBLIC_KEY = "044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private static final String SHAPE = "download stream must be one metadata message followed by chunks";
    private static final String GO_HELPER = findGoHelper();

    @Test
    void uploadAndDownload() throws Exception {
        withServer(files -> {
            byte[] data = patterned(2621440);
            long fileId = KycFiles.uploadFile(files, UploadFileRequest.Metadata.newBuilder()
                    .setPayoutProviderId(7)
                    .setClientId("applicant-1")
                    .setFileName("passport.pdf")
                    .setDeclaredContentType("application/pdf")
                    .setUploadId("upload-1")
                    .build(), data);
            assertThat(fileId).isGreaterThan(0);
            var downloaded = KycFiles.downloadFile(files, download(fileId));
            assertThat(downloaded.metadata().getContentType()).isEqualTo("application/pdf");
            assertThat(downloaded.metadata().getFileName()).isEqualTo("passport.pdf");
            assertThat(downloaded.data()).isEqualTo(data);
        });
    }

    @Test
    void unknownFileIsNotFound() throws Exception {
        withServer(files -> {
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class,
                    () -> KycFiles.downloadFile(files, download(42)));
            assertThat(err.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        });
    }

    @Test
    void downloadShapeIsInvalidArgument() throws Exception {
        withServer(files -> {
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class,
                    () -> KycFiles.downloadFile(files, download(9223372036854775807L)));
            assertThat(err.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(err.getStatus().getDescription()).isEqualTo(SHAPE);
        });
    }

    @Test
    void deniedUploadIsPermissionDenied() throws Exception {
        withServer(files -> {
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class, () -> KycFiles.uploadFile(files,
                    UploadFileRequest.Metadata.newBuilder()
                            .setPayoutProviderId(7)
                            .setClientId("kyc-file-permission-denied")
                            .build(),
                    patterned(8388608)));
            assertThat(err.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        });
    }

    @FunctionalInterface
    private interface Call {
        void run(KycFileServiceGrpc.KycFileServiceBlockingV2Stub files) throws Exception;
    }

    private static void withServer(Call call) throws Exception {
        if (GO_HELPER == null) {
            if (System.getenv("CI") != null) {
                throw new AssertionError("Go helper binary required in CI but not found");
            }
            assumeTrue(false, "Go helper not found — skipping cross-language test");
        }
        int port = findFreePort();
        Process process = new ProcessBuilder(GO_HELPER, "serve", String.valueOf(port), "0x" + PUBLIC_KEY)
                .redirectErrorStream(true)
                .start();
        Thread drain = new Thread(() -> {
            try {
                process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // the process is being stopped
            }
        }, "go-helper-log");
        drain.setDaemon(true);
        drain.start();
        try {
            waitForPort(port);
            try (var client = BlockingNetworkClient.create(
                    "http://127.0.0.1:" + port, Signer.fromHex(PRIVATE_KEY), KycFileServiceGrpc::newBlockingV2Stub)) {
                call.run(client.stub().withDeadlineAfter(30, TimeUnit.SECONDS));
            }
        } finally {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static DownloadFileRequest download(long fileId) {
        return DownloadFileRequest.newBuilder()
                .setFileId(fileId)
                .setPayoutRequesterId(3)
                .setPayoutProviderId(7)
                .setClientId("applicant-1")
                .build();
    }

    private static byte[] patterned(int n) {
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    private static String findGoHelper() {
        File dir = new File(System.getProperty("user.dir"));
        File repoRoot = dir.getParentFile().getParentFile();
        File helper = new File(repoRoot, "cross_test/go_helper/go_helper");
        if (!helper.exists()) {
            helper = new File(dir.getParentFile(), "cross_test/go_helper/go_helper");
        }
        if (!helper.exists()) {
            helper = new File(dir, "../../cross_test/go_helper/go_helper");
        }
        return helper.exists() ? helper.getAbsolutePath() : null;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForPort(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try (var sock = new java.net.Socket()) {
                sock.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new RuntimeException("Port " + port + " not ready");
    }
}

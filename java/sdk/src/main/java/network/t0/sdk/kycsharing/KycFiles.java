package network.t0.sdk.kycsharing;

import com.google.protobuf.UnsafeByteOperations;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.BlockingClientCall;
import network.t0.sdk.common.Messages;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.DownloadFileRequest;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.DownloadFileResponse;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.KycFileServiceGrpc.KycFileServiceBlockingV2Stub;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.UploadFileRequest;
import network.t0.sdk.proto.tzero.v1.manage.kyc_sharing.UploadFileResponse;

import java.io.ByteArrayOutputStream;

/**
 * Uploads and downloads a KYC file through {@code KycFileService}. The caller builds the
 * {@link KycFileServiceBlockingV2Stub}. These methods only run the stream.
 */
public final class KycFiles {

    /** The largest {@code Chunk.data} a helper sends: the {@code max_len} of that field. */
    public static final int KYC_FILE_CHUNK_MAX_BYTES = 1048576;

    private KycFiles() {
    }

    /**
     * Sends metadata, then {@code data} in chunks, and returns the stored file id.
     *
     * <p>The stream is half-closed only after every write. When {@code write} returns false the
     * server has already answered, so the call is read and its status is thrown. The call is
     * cancelled on any other failure. Checked {@link StatusException}s become
     * {@link StatusRuntimeException}s with the same status. An empty file sends no chunk.
     */
    public static long uploadFile(KycFileServiceBlockingV2Stub stub, UploadFileRequest.Metadata metadata, byte[] data) {
        BlockingClientCall<UploadFileRequest, UploadFileResponse> call = stub.uploadFile();
        try {
            if (!call.write(UploadFileRequest.newBuilder().setMetadata(metadata).build())) {
                return fileId(call.read());
            }
            for (int offset = 0; offset < data.length; ) {
                int length = Math.min(KYC_FILE_CHUNK_MAX_BYTES, data.length - offset);
                UploadFileRequest chunk = UploadFileRequest.newBuilder()
                        .setChunk(UploadFileRequest.Chunk.newBuilder()
                                .setData(UnsafeByteOperations.unsafeWrap(data, offset, length)))
                        .build();
                if (!call.write(chunk)) {
                    return fileId(call.read());
                }
                offset += length;
            }
            call.halfClose();
            return fileId(call.read());
        } catch (StatusException e) {
            throw new StatusRuntimeException(e.getStatus(), e.getTrailers());
        } catch (InterruptedException e) {
            call.cancel(null, e);
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.asRuntimeException();
        } catch (RuntimeException e) {
            call.cancel(null, e);
            throw e;
        }
    }

    /**
     * Returns the file metadata and every byte.
     *
     * <p>A stream that is not one metadata message followed by chunks is cancelled and fails with
     * {@link Messages#KYC_DOWNLOAD_SHAPE}. No bytes are returned. A server status is rethrown with
     * the same code and description.
     */
    public static DownloadedFile downloadFile(KycFileServiceBlockingV2Stub stub, DownloadFileRequest request) {
        BlockingClientCall<?, DownloadFileResponse> call = stub.downloadFile(request);
        try {
            DownloadFileResponse.Metadata metadata = null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                DownloadFileResponse message = call.read();
                if (message == null) {
                    break;
                }
                switch (message.getPayloadCase()) {
                    case METADATA -> {
                        if (metadata != null) {
                            call.cancel(null, null);
                            throw shapeError();
                        }
                        metadata = message.getMetadata();
                    }
                    case CHUNK -> {
                        if (metadata == null) {
                            call.cancel(null, null);
                            throw shapeError();
                        }
                        byte[] chunk = message.getChunk().getData().toByteArray();
                        bytes.write(chunk, 0, chunk.length);
                    }
                    default -> {
                        call.cancel(null, null);
                        throw shapeError();
                    }
                }
            }
            if (metadata == null) {
                call.cancel(null, null);
                throw shapeError();
            }
            return new DownloadedFile(metadata, bytes.toByteArray());
        } catch (StatusException e) {
            throw new StatusRuntimeException(e.getStatus(), e.getTrailers());
        } catch (InterruptedException e) {
            call.cancel(null, e);
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.asRuntimeException();
        }
    }

    private static long fileId(UploadFileResponse response) throws StatusException {
        if (response == null) {
            throw Status.UNKNOWN.asException();
        }
        return response.getFileId();
    }

    private static StatusRuntimeException shapeError() {
        return Status.INVALID_ARGUMENT.withDescription(Messages.KYC_DOWNLOAD_SHAPE).asRuntimeException();
    }

    /**
     * A downloaded file. Java has no tuples, so this holds the generated metadata and the bytes.
     * It has no fields of its own.
     */
    public record DownloadedFile(DownloadFileResponse.Metadata metadata, byte[] data) {
    }
}

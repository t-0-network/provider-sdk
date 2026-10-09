import { create, type MessageInitShape } from "@bufbuild/protobuf";
import { Code, ConnectError, type CallOptions, type Client } from "@connectrpc/connect";
import { KYC_DOWNLOAD_SHAPE } from "../common/messages.js";
import {
    DownloadFileRequestSchema,
    KycFileService,
    UploadFileRequest_MetadataSchema,
    type DownloadFileResponse_Metadata,
} from "../common/gen/tzero/v1/manage/kyc_sharing/file_pb.js";

/** The largest Chunk.data a helper sends: the max_len of that field. */
export const KYC_FILE_CHUNK_MAX_BYTES = 1048576;

/**
 * Sends metadata, then data in chunks of at most {@link KYC_FILE_CHUNK_MAX_BYTES},
 * and returns the stored file id. The generator ends only after the last chunk.
 * A throw aborts the call. An empty file sends no chunk. Chunks are views of data.
 */
export async function uploadFile(
    client: Client<typeof KycFileService>,
    metadata: MessageInitShape<typeof UploadFileRequest_MetadataSchema>,
    data: Uint8Array,
    options?: CallOptions,
): Promise<bigint> {
    const response = await client.uploadFile((async function* () {
        yield { payload: { case: "metadata", value: metadata } };
        for (let offset = 0; offset < data.byteLength; offset += KYC_FILE_CHUNK_MAX_BYTES) {
            const end = Math.min(offset + KYC_FILE_CHUNK_MAX_BYTES, data.byteLength);
            // subarray is a view of data, not a copy.
            yield { payload: { case: "chunk", value: { data: data.subarray(offset, end) } } };
        }
    })(), options);
    return response.fileId;
}

/**
 * Returns the file metadata and every byte. A stream that is not one metadata
 * message followed by chunks throws {@link KYC_DOWNLOAD_SHAPE} (invalid_argument)
 * from inside the loop, so the call is cancelled, and no bytes are returned.
 * A server error is the call's error.
 */
export async function downloadFile(
    client: Client<typeof KycFileService>,
    request: MessageInitShape<typeof DownloadFileRequestSchema>,
    options?: CallOptions,
): Promise<{ metadata: DownloadFileResponse_Metadata; data: Uint8Array }> {
    const parts: Uint8Array[] = [];
    let metadata: DownloadFileResponse_Metadata | undefined;
    for await (const message of client.downloadFile(create(DownloadFileRequestSchema, request), options)) {
        switch (message.payload.case) {
            case "metadata":
                if (metadata !== undefined) {
                    throw new ConnectError(KYC_DOWNLOAD_SHAPE, Code.InvalidArgument);
                }
                metadata = message.payload.value;
                break;
            case "chunk":
                if (metadata === undefined) {
                    throw new ConnectError(KYC_DOWNLOAD_SHAPE, Code.InvalidArgument);
                }
                parts.push(message.payload.value.data);
                break;
            default:
                throw new ConnectError(KYC_DOWNLOAD_SHAPE, Code.InvalidArgument);
        }
    }
    if (metadata === undefined) {
        throw new ConnectError(KYC_DOWNLOAD_SHAPE, Code.InvalidArgument);
    }
    let size = 0;
    for (const part of parts) {
        size += part.byteLength;
    }
    const data = new Uint8Array(size);
    let offset = 0;
    for (const part of parts) {
        data.set(part, offset);
        offset += part.byteLength;
    }
    return { metadata, data };
}

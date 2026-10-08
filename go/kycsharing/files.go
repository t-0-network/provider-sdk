// Package kycsharing uploads and downloads a KYC file through KycFileService.
// The caller builds the client. These helpers only run the stream.
package kycsharing

import (
	"context"
	"errors"
	"io"

	"connectrpc.com/connect"

	kyc_sharing "github.com/t-0-network/provider-sdk/go/api/tzero/v1/manage/kyc_sharing"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/manage/kyc_sharing/kyc_sharingconnect"
	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// KycFileChunkMaxBytes is the largest Chunk.data a helper sends. It is the
// max_len of that field. A file is split into views of this size, never copied,
// and an empty file sends no chunk.
const KycFileChunkMaxBytes = 1048576

// UploadFile sends metadata, then data in chunks, and returns the stored file id.
// The stream ends normally only after the last chunk. Any earlier send error
// cancels the call, except io.EOF, which means the server already answered:
// the returned error is that status.
func UploadFile(ctx context.Context, client kyc_sharingconnect.KycFileServiceClient, metadata *kyc_sharing.UploadFileRequest_Metadata, data []byte) (int64, error) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	stream := client.UploadFile(ctx)
	err := stream.Send(&kyc_sharing.UploadFileRequest{
		Payload: &kyc_sharing.UploadFileRequest_Metadata_{Metadata: metadata},
	})
	if err != nil {
		return uploadStopped(stream, cancel, err)
	}
	for offset := 0; offset < len(data); offset += KycFileChunkMaxBytes {
		end := offset + KycFileChunkMaxBytes
		if end > len(data) {
			end = len(data)
		}
		err = stream.Send(&kyc_sharing.UploadFileRequest{
			Payload: &kyc_sharing.UploadFileRequest_Chunk_{
				Chunk: &kyc_sharing.UploadFileRequest_Chunk{Data: data[offset:end]},
			},
		})
		if err != nil {
			return uploadStopped(stream, cancel, err)
		}
	}
	resp, err := stream.CloseAndReceive()
	if err != nil {
		return 0, err
	}
	return resp.Msg.GetFileId(), nil
}

// uploadStopped leaves a partial upload unfinished. io.EOF means the server
// already closed the stream, so the status comes from CloseAndReceive. Any
// other send error cancels, and the stream is not closed normally.
func uploadStopped(stream *connect.ClientStreamForClient[kyc_sharing.UploadFileRequest, kyc_sharing.UploadFileResponse], cancel context.CancelFunc, err error) (int64, error) {
	if errors.Is(err, io.EOF) {
		resp, err := stream.CloseAndReceive()
		if err != nil {
			return 0, err
		}
		return resp.Msg.GetFileId(), nil
	}
	cancel()
	return 0, err
}

// DownloadFile returns the file metadata and every byte. A stream that is not
// one metadata message followed by chunks is cancelled and fails with
// InvalidArgument, and no bytes are returned. A server status is returned as it is.
func DownloadFile(ctx context.Context, client kyc_sharingconnect.KycFileServiceClient, req *kyc_sharing.DownloadFileRequest) (*kyc_sharing.DownloadFileResponse_Metadata, []byte, error) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	stream, err := client.DownloadFile(ctx, connect.NewRequest(req))
	if err != nil {
		return nil, nil, err
	}
	var metadata *kyc_sharing.DownloadFileResponse_Metadata
	var data []byte
	for stream.Receive() {
		msg := stream.Msg()
		switch payload := msg.GetPayload().(type) {
		case *kyc_sharing.DownloadFileResponse_Metadata_:
			if metadata != nil || payload.Metadata == nil {
				cancel()
				return nil, nil, shapeError()
			}
			metadata = payload.Metadata
		case *kyc_sharing.DownloadFileResponse_Chunk_:
			if metadata == nil || payload.Chunk == nil {
				cancel()
				return nil, nil, shapeError()
			}
			data = append(data, payload.Chunk.GetData()...)
		default:
			cancel()
			return nil, nil, shapeError()
		}
	}
	if err := stream.Err(); err != nil {
		return nil, nil, err
	}
	if metadata == nil {
		cancel()
		return nil, nil, shapeError()
	}
	return metadata, data, nil
}

func shapeError() error {
	return connect.NewError(connect.CodeInvalidArgument, errors.New(contract.KycDownloadShape))
}

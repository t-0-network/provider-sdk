package main

import (
	"context"
	"errors"
	"sync"

	"connectrpc.com/connect"
	kyc "github.com/t-0-network/provider-sdk/go/api/tzero/v1/manage/kyc_sharing"
	"github.com/t-0-network/provider-sdk/go/kycsharing"
)

// Sentinels the cross tests share, as literals, so a helper that breaks the
// upload or download shape fails here without reading a log.
const (
	kycShapeFileID    int64 = 9223372036854775807
	kycDeniedClientID       = "kyc-file-permission-denied"
)

type storedFile struct {
	contentType string
	fileName    *string
	data        []byte
}

// kycFileService keeps uploaded files in memory. It rejects an upload that does
// not start with metadata, an empty chunk, and a chunk over the shared maximum.
// It does not look at the bytes.
type kycFileService struct {
	mu     sync.Mutex
	nextID int64
	files  map[int64]storedFile
}

func newKycFileService() *kycFileService {
	return &kycFileService{files: map[int64]storedFile{}}
}

func (s *kycFileService) UploadFile(_ context.Context, stream *connect.ClientStream[kyc.UploadFileRequest]) (*connect.Response[kyc.UploadFileResponse], error) {
	if !stream.Receive() {
		if err := stream.Err(); err != nil {
			return nil, err
		}
		return nil, connect.NewError(connect.CodeInvalidArgument, errors.New("upload must start with metadata"))
	}
	metadata := stream.Msg().GetMetadata()
	if metadata == nil {
		return nil, connect.NewError(connect.CodeInvalidArgument, errors.New("upload must start with metadata"))
	}
	// Answer without reading the chunks, so the client is still writing.
	if metadata.GetClientId() == kycDeniedClientID {
		return nil, connect.NewError(connect.CodePermissionDenied, errors.New("permission denied"))
	}

	var data []byte
	for stream.Receive() {
		chunk := stream.Msg().GetChunk()
		if chunk == nil || len(chunk.GetData()) == 0 || len(chunk.GetData()) > kycsharing.KycFileChunkMaxBytes {
			return nil, connect.NewError(connect.CodeInvalidArgument, errors.New("upload chunk is empty or too large"))
		}
		data = append(data, chunk.GetData()...)
	}
	if err := stream.Err(); err != nil {
		return nil, err
	}

	contentType := "application/octet-stream"
	if metadata.DeclaredContentType != nil {
		contentType = *metadata.DeclaredContentType
	}
	var fileName *string
	if metadata.FileName != nil {
		name := *metadata.FileName
		fileName = &name
	}

	s.mu.Lock()
	s.nextID++
	id := s.nextID
	s.files[id] = storedFile{contentType: contentType, fileName: fileName, data: data}
	s.mu.Unlock()
	return connect.NewResponse(&kyc.UploadFileResponse{FileId: id}), nil
}

func (s *kycFileService) DownloadFile(_ context.Context, req *connect.Request[kyc.DownloadFileRequest], stream *connect.ServerStream[kyc.DownloadFileResponse]) error {
	if req.Msg.GetFileId() == kycShapeFileID {
		return stream.Send(&kyc.DownloadFileResponse{
			Payload: &kyc.DownloadFileResponse_Chunk_{
				Chunk: &kyc.DownloadFileResponse_Chunk{Data: []byte{0x01}},
			},
		})
	}

	s.mu.Lock()
	file, ok := s.files[req.Msg.GetFileId()]
	s.mu.Unlock()
	if !ok {
		return connect.NewError(connect.CodeNotFound, errors.New("file not found"))
	}

	metadata := &kyc.DownloadFileResponse_Metadata{ContentType: file.contentType}
	if file.fileName != nil {
		name := *file.fileName
		metadata.FileName = &name
	}
	if err := stream.Send(&kyc.DownloadFileResponse{
		Payload: &kyc.DownloadFileResponse_Metadata_{Metadata: metadata},
	}); err != nil {
		return err
	}
	for offset := 0; offset < len(file.data); offset += kycsharing.KycFileChunkMaxBytes {
		end := offset + kycsharing.KycFileChunkMaxBytes
		if end > len(file.data) {
			end = len(file.data)
		}
		if err := stream.Send(&kyc.DownloadFileResponse{
			Payload: &kyc.DownloadFileResponse_Chunk_{
				Chunk: &kyc.DownloadFileResponse_Chunk{Data: file.data[offset:end]},
			},
		}); err != nil {
			return err
		}
	}
	return nil
}

package main

import (
	"bytes"
	"context"
	"errors"
	"testing"
	"time"

	"connectrpc.com/connect"
	"google.golang.org/protobuf/proto"

	kyc "github.com/t-0-network/provider-sdk/go/api/tzero/v1/manage/kyc_sharing"
	"github.com/t-0-network/provider-sdk/go/api/tzero/v1/manage/kyc_sharing/kyc_sharingconnect"
	"github.com/t-0-network/provider-sdk/go/kycsharing"
	"github.com/t-0-network/provider-sdk/go/network"
)

func TestKycFiles(t *testing.T) {
	for _, protocol := range []struct {
		name string
		opts []network.ClientOption
	}{
		{name: "connect"},
		{name: "grpc", opts: []network.ClientOption{network.WithProtocol(network.ProtocolGRPC)}},
	} {
		t.Run(protocol.name, func(t *testing.T) {
			client := newKycClient(t, serveHelper(t), protocol.opts)
			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			defer cancel()

			t.Run("upload and download", func(t *testing.T) {
				data := patterned(2621440)
				metadata := &kyc.UploadFileRequest_Metadata{
					PayoutProviderId:    7,
					ClientId:            "applicant-1",
					FileName:            proto.String("passport.pdf"),
					DeclaredContentType: proto.String("application/pdf"),
					UploadId:            proto.String("upload-1"),
				}
				fileID, err := kycsharing.UploadFile(ctx, client, metadata, data)
				if err != nil {
					t.Fatal(err)
				}
				if fileID < 1 {
					t.Fatalf("file id %d", fileID)
				}
				gotMeta, got, err := kycsharing.DownloadFile(ctx, client, &kyc.DownloadFileRequest{
					FileId: fileID, PayoutRequesterId: 3, PayoutProviderId: 7, ClientId: "applicant-1",
				})
				if err != nil {
					t.Fatal(err)
				}
				if gotMeta.GetContentType() != "application/pdf" || gotMeta.GetFileName() != "passport.pdf" {
					t.Fatalf("metadata content_type=%q file_name=%q", gotMeta.GetContentType(), gotMeta.GetFileName())
				}
				if !bytes.Equal(got, data) {
					t.Fatalf("downloaded %d bytes, want %d", len(got), len(data))
				}
			})

			t.Run("unknown file", func(t *testing.T) {
				_, got, err := kycsharing.DownloadFile(ctx, client, &kyc.DownloadFileRequest{
					FileId: 42, PayoutRequesterId: 3, PayoutProviderId: 7, ClientId: "applicant-1",
				})
				if connect.CodeOf(err) != connect.CodeNotFound {
					t.Fatalf("got %v, want not found", err)
				}
				if got != nil {
					t.Fatalf("returned %d bytes", len(got))
				}
			})

			t.Run("download shape", func(t *testing.T) {
				_, got, err := kycsharing.DownloadFile(ctx, client, &kyc.DownloadFileRequest{
					FileId: 9223372036854775807, PayoutRequesterId: 3, PayoutProviderId: 7, ClientId: "applicant-1",
				})
				var connectErr *connect.Error
				if !errors.As(err, &connectErr) || connectErr.Code() != connect.CodeInvalidArgument ||
					connectErr.Message() != "download stream must be one metadata message followed by chunks" {
					t.Fatalf("got %v", err)
				}
				if got != nil {
					t.Fatalf("returned %d bytes", len(got))
				}
			})

			t.Run("permission denied mid upload", func(t *testing.T) {
				_, err := kycsharing.UploadFile(ctx, client, &kyc.UploadFileRequest_Metadata{
					PayoutProviderId: 7,
					ClientId:         "kyc-file-permission-denied",
				}, patterned(8388608))
				if connect.CodeOf(err) != connect.CodePermissionDenied {
					t.Fatalf("got %v, want permission denied", err)
				}
			})
		})
	}
}

func newKycClient(t *testing.T, url string, opts []network.ClientOption) kyc_sharingconnect.KycFileServiceClient {
	t.Helper()
	client, err := network.NewServiceClient(network.PrivateKeyHexed(clientPrivateKey), kyc_sharingconnect.NewKycFileServiceClient,
		append([]network.ClientOption{network.WithBaseURL(url)}, opts...)...)
	if err != nil {
		t.Fatal(err)
	}
	return client
}

func patterned(n int) []byte {
	data := make([]byte, n)
	for i := range data {
		data[i] = byte(i)
	}
	return data
}

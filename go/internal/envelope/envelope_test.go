package envelope

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestIsEnveloped(t *testing.T) {
	for contentType, want := range map[string]bool{
		"application/connect+proto":      true,
		"application/connect+json":       true,
		"application/grpc":               true,
		"application/grpc+proto":         true,
		"Application/GRPC+proto; x=y":    true,
		"APPLICATION/CONNECT+proto; x=y": true,
		"application/proto":              false,
		"application/json; charset=utf8": false,
		"":                               false,
	} {
		require.Equal(t, want, IsEnveloped(contentType), contentType)
	}
}

func TestIsGRPC(t *testing.T) {
	for mediaType, want := range map[string]bool{
		"application/grpc":          true,
		"application/grpc+proto":    true,
		"application/grpc+json":     true,
		"application/grpcx":         false,
		"application/connect+proto": false,
		"application/proto":         false,
		"":                          false,
	} {
		require.Equal(t, want, IsGRPC(mediaType), mediaType)
	}
}

// Package envelope holds the content type rule that the network client and the provider server
// share: which request bodies are a sequence of envelopes, of which only the first is signed.
// See docs/STREAMING.md.
package envelope

import "strings"

// mediaType returns the media type of a Content-Type value: lower case, without its parameters
// and surrounding spaces.
func mediaType(contentType string) string {
	if i := strings.IndexByte(contentType, ';'); i >= 0 {
		contentType = contentType[:i]
	}
	return strings.ToLower(strings.TrimSpace(contentType))
}

func isGRPC(mediaType string) bool {
	return mediaType == "application/grpc" || strings.HasPrefix(mediaType, "application/grpc+")
}

// IsGRPC reports whether a Content-Type is gRPC: application/grpc or application/grpc+<codec>.
func IsGRPC(contentType string) bool {
	return isGRPC(mediaType(contentType))
}

// IsEnveloped reports whether a body of this Content-Type is a sequence of envelopes,
// flags (1) || uint32be(length) || payload: application/connect+<codec> or gRPC.
func IsEnveloped(contentType string) bool {
	mt := mediaType(contentType)
	return strings.HasPrefix(mt, "application/connect+") || isGRPC(mt)
}

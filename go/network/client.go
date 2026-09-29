// Package network provides a client for interacting with the TZero service.
package network

import (
	"net/http"
	"net/url"
	"sync"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

type PrivateKeyHexed string

type ClientFactory[T any] func(httpClient connect.HTTPClient, baseURL string, opts ...connect.ClientOption) T

// NewServiceClient builds a client for a T-0 Network service that signs every request: unary calls
// over the whole body, client- and server-streaming calls over their first request message.
// Bidirectional-streaming calls fail with connect.CodeUnimplemented. See docs/STREAMING.md.
func NewServiceClient[T any](
	privateKey PrivateKeyHexed, clientFactory ClientFactory[T], opts ...ClientOption,
) (T, error) {
	options := defaultClientOptions
	for _, opt := range opts {
		opt(&options)
	}

	var t T

	if err := options.validate(); err != nil {
		return t, err
	}

	if options.signFn == nil {
		if privateKey == "" {
			return t, ErrEmptyPrivateKey
		}

		defaultSignFn, err := crypto.NewSignerFromHex(string(privateKey))
		if err != nil {
			return t, err
		}

		options.signFn = defaultSignFn
	}

	transport := options.transport
	if baseURL, _ := url.Parse(options.baseURL); transport == nil && options.protocol == ProtocolGRPC && baseURL.Scheme == "http" {
		transport = cleartextHTTP2()
	}

	// No http.Client.Timeout: it would cap whole streams. callTimeouts sets per-call deadlines.
	client := http.Client{
		Transport: NewSigningTransport(options.signFn, time.Now, WithTransport(transport)),
		// A redirect is returned, not followed: following it would send the signed request to
		// another URL, or turn it into a GET.
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}

	connectOptions := []connect.ClientOption{
		connect.WithInterceptors(rejectBidi{}, callTimeouts{unary: options.timeout, stream: options.streamTimeout}),
	}
	if options.wireFormat == WireFormatJSON {
		connectOptions = append(connectOptions, connect.WithProtoJSON())
	}
	if options.protocol == ProtocolGRPC {
		connectOptions = append(connectOptions, connect.WithGRPC())
	}

	return clientFactory(&client, options.baseURL, connectOptions...), nil
}

// cleartextHTTP2 carries gRPC to an http:// base URL: HTTP/2 without TLS, with prior knowledge,
// because gRPC needs HTTP/2 and without TLS there is no handshake in which to agree on it. Every
// such client shares this one transport and its connections.
var cleartextHTTP2 = sync.OnceValue(func() *http.Transport {
	transport := &http.Transport{}
	if base, ok := http.DefaultTransport.(*http.Transport); ok {
		transport = base.Clone() // keeps its dial and idle timeouts
	}
	transport.Proxy = nil
	transport.Protocols = new(http.Protocols)
	transport.Protocols.SetUnencryptedHTTP2(true)
	return transport
})

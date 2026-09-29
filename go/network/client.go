// Package network provides a client for interacting with the TZero service.
package network

import (
	"fmt"
	"net/http"
	"slices"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

type PrivateKeyHexed string

type ClientFactory[T any] func(httpClient connect.HTTPClient, baseURL string, opts ...connect.ClientOption) T

// NewServiceClient builds a client for a T-0 Network service that signs every request.
//
// Unary calls are signed over the whole request body. Client-streaming and server-streaming calls
// are signed over their first request message only, and the request goes out as soon as that
// message is sent: see SigningTransport. Unary calls time out after WithTimeout (15 seconds by
// default); streaming calls have no timeout unless WithStreamTimeout is set.
func NewServiceClient[T any](
	privateKey PrivateKeyHexed, clientFactory ClientFactory[T], opts ...ClientOption,
) (T, error) {
	options := defaultClientOptions
	for _, opt := range opts {
		opt(&options)
	}

	var t T

	if err := options.validate(); err != nil {
		return t, fmt.Errorf("validating client options: %w", err)
	}

	if options.signFn == nil {
		if privateKey == "" {
			return t, ErrEmptyPrivateKey
		}

		defaultSignFn, err := crypto.NewSignerFromHex(string(privateKey))
		if err != nil {
			return t, fmt.Errorf("creating signer from hexed private key: %w", err)
		}

		options.signFn = defaultSignFn
	}

	// No http.Client.Timeout: it would cover a whole upload or download. The transport applies the
	// unary and the stream timeout instead.
	client := http.Client{
		Transport: NewSigningTransport(
			options.signFn, time.Now,
			WithTransport(options.transport),
			WithCallTimeouts(options.timeout, options.streamTimeout),
		),
	}

	// The interceptor tells the transport each call's stream type.
	connectOptions := append(slices.Clone(options.connectOptions), connect.WithInterceptors(streamTypeInterceptor{}))

	return clientFactory(&client, options.baseURL, connectOptions...), nil
}

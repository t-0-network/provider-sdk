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

	// No http.Client.Timeout: it would cap whole streams. callTimeouts sets per-call deadlines.
	client := http.Client{
		Transport: NewSigningTransport(
			options.signFn, time.Now, WithTransport(options.transport),
		),
	}

	connectOptions := append(slices.Clone(options.connectOptions),
		connect.WithInterceptors(rejectBidi{}, callTimeouts{unary: options.timeout, stream: options.streamTimeout}))

	return clientFactory(&client, options.baseURL, connectOptions...), nil
}

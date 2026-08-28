package network

import (
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

const (
	defaultBaseURL = "https://api.t-0.network"
	defaultTimeout = 15 * time.Second
)

var (
	ErrEmptyBaseURL    = errors.New("base URL is not set")
	ErrInvalidBaseURL  = errors.New("base URL is not valid")
	ErrEmptyPrivateKey = errors.New("provider private key is not set")
	ErrInvalidTimeOut  = errors.New("timeout must be greater than zero")
)

type clientOptions struct {
	baseURL        string
	signFn         crypto.SignFn
	timeout        time.Duration
	transport      http.RoundTripper
	connectOptions []connect.ClientOption
}

func (c *clientOptions) validate() error {
	if c.baseURL == "" {
		return ErrEmptyBaseURL
	}

	if _, err := url.Parse(c.baseURL); err != nil {
		return fmt.Errorf("%w: %s", ErrInvalidBaseURL, err)
	}

	if c.timeout <= 0 {
		return ErrInvalidTimeOut
	}

	return nil
}

var defaultClientOptions = clientOptions{
	baseURL: defaultBaseURL,
	signFn:  nil,
	timeout: defaultTimeout,
}

type ClientOption func(*clientOptions)

func WithBaseURL(url string) ClientOption {
	return func(c *clientOptions) {
		c.baseURL = url
	}
}

func WithSignatureFunction(fn crypto.SignFn) ClientOption {
	return func(c *clientOptions) {
		c.signFn = fn
	}
}

func WithTimeout(t time.Duration) ClientOption {
	return func(c *clientOptions) {
		c.timeout = t
	}
}

func WithConnectOptions(options ...connect.ClientOption) ClientOption {
	return func(c *clientOptions) {
		c.connectOptions = options
	}
}

// WithHTTPTransport sets the underlying http.RoundTripper that carries requests.
// The SDK wraps it in a SigningTransport — requests are signed regardless of the
// transport supplied. Pass a plain transport (instrumentation, proxying, TLS config,
// in-memory test transport); do not pass one that already signs.
//
// Retries below the signing layer replay the same timestamp. Keep retry budgets
// well under the 60-second signature tolerance, or retry above the SDK client.
//
// Default: http.DefaultTransport. A nil value is ignored.
func WithHTTPTransport(rt http.RoundTripper) ClientOption {
	return func(c *clientOptions) {
		c.transport = rt
	}
}

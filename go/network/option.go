package network

import (
	"errors"
	"fmt"
	"math"
	"net/http"
	"net/url"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

const (
	defaultBaseURL       = "https://api.t-0.network"
	defaultTimeout       = 15 * time.Second
	defaultStreamTimeout = 5 * time.Minute
	// maxTimeout is the largest timeout every SDK accepts: 2^31-1 ms.
	maxTimeout = math.MaxInt32 * time.Millisecond
)

var (
	ErrEmptyBaseURL    = errors.New("base URL is not set")
	ErrInvalidBaseURL  = errors.New("base URL is not valid")
	ErrEmptyPrivateKey = errors.New("provider private key is not set")
	ErrInvalidTimeOut  = errors.New("WithTimeout must be a positive duration of at most 2147483647 ms")

	ErrInvalidStreamTimeout = errors.New("WithStreamTimeout must be a positive duration of at most 2147483647 ms")
)

type clientOptions struct {
	baseURL        string
	signFn         crypto.SignFn
	timeout        time.Duration
	streamTimeout  time.Duration
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

	if c.timeout <= 0 || c.timeout > maxTimeout {
		return ErrInvalidTimeOut
	}

	if c.streamTimeout <= 0 || c.streamTimeout > maxTimeout {
		return ErrInvalidStreamTimeout
	}

	return nil
}

var defaultClientOptions = clientOptions{
	baseURL:       defaultBaseURL,
	signFn:        nil,
	timeout:       defaultTimeout,
	streamTimeout: defaultStreamTimeout,
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

// WithTimeout sets the deadline of each unary call whose context has none; streams use
// WithStreamTimeout. A deadline on the call's context replaces it, shorter or longer.
// It must be positive and at most 2147483647 ms.
//
// Default: 15 seconds.
func WithTimeout(t time.Duration) ClientOption {
	return func(c *clientOptions) {
		c.timeout = t
	}
}

// WithStreamTimeout sets the deadline of each client- and server-streaming call whose context has
// none, including the wait for its first message. A deadline on the call's context replaces it,
// shorter or longer. It must be positive and at most 2147483647 ms. See docs/STREAMING.md.
//
// Default: 5 minutes.
func WithStreamTimeout(t time.Duration) ClientOption {
	return func(c *clientOptions) {
		c.streamTimeout = t
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

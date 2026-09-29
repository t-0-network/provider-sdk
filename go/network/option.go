package network

import (
	"errors"
	"fmt"
	"math"
	"net/http"
	"net/url"
	"time"

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
	ErrEmptyPrivateKey = errors.New("private key must not be null or empty")
	ErrInvalidTimeOut  = errors.New("WithTimeout must be a positive duration of at most 2147483647 ms")

	ErrInvalidStreamTimeout = errors.New("WithStreamTimeout must be a positive duration of at most 2147483647 ms")
)

type clientOptions struct {
	baseURL       string
	signFn        crypto.SignFn
	timeout       time.Duration
	streamTimeout time.Duration
	wireFormat    WireFormat
	protocol      Protocol
	transport     http.RoundTripper
}

func (c *clientOptions) validate() error {
	if c.baseURL == "" {
		return ErrEmptyBaseURL
	}

	if u, err := url.Parse(c.baseURL); err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Host == "" {
		return ErrInvalidBaseURL
	}

	if c.timeout <= 0 || c.timeout > maxTimeout {
		return ErrInvalidTimeOut
	}

	if c.streamTimeout <= 0 || c.streamTimeout > maxTimeout {
		return ErrInvalidStreamTimeout
	}

	if c.wireFormat != WireFormatBinary && c.wireFormat != WireFormatJSON {
		return fmt.Errorf("unknown wire format %d", c.wireFormat)
	}

	if c.protocol != ProtocolConnect && c.protocol != ProtocolGRPC {
		return fmt.Errorf("unknown protocol %d", c.protocol)
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

// WireFormat is how a client encodes its messages.
type WireFormat int

const (
	// WireFormatBinary encodes messages as binary protobuf.
	WireFormatBinary WireFormat = iota
	// WireFormatJSON encodes messages as protobuf JSON.
	WireFormatJSON
)

// WithWireFormat sets how messages are encoded.
//
// Default: WireFormatBinary.
func WithWireFormat(f WireFormat) ClientOption {
	return func(c *clientOptions) {
		c.wireFormat = f
	}
}

// Protocol is the RPC protocol a client speaks.
type Protocol int

const (
	// ProtocolConnect is the Connect protocol.
	ProtocolConnect Protocol = iota
	// ProtocolGRPC is gRPC. On an http:// base URL it runs over HTTP/2 without TLS.
	ProtocolGRPC
)

// WithProtocol sets the RPC protocol.
//
// Default: ProtocolConnect.
func WithProtocol(p Protocol) ClientOption {
	return func(c *clientOptions) {
		c.protocol = p
	}
}

// withHTTPTransport sets the http.RoundTripper under the signing transport; tests use it to reach
// their own servers. A nil value is ignored.
func withHTTPTransport(rt http.RoundTripper) ClientOption {
	return func(c *clientOptions) {
		c.transport = rt
	}
}

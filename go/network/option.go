package network

import (
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// What a client uses when it is given no base URL, unary timeout or stream timeout, and the
// largest timeout and stream timeout it accepts. The same values in every SDK.
const (
	DefaultBaseURL       = "https://api.t-0.network"
	DefaultTimeout       = 15 * time.Second
	DefaultStreamTimeout = 5 * time.Minute
	MaxTimeout           = 2147483647 * time.Millisecond
)

var (
	ErrEmptyBaseURL    = errors.New(contract.BaseURLNotSet)
	ErrInvalidBaseURL  = errors.New(contract.BaseURLNotValid)
	ErrEmptyPrivateKey = errors.New(contract.PrivateKeyEmpty)
	ErrInvalidTimeOut  = errors.New(contract.TimeoutNotValid)

	ErrInvalidStreamTimeout = errors.New(contract.StreamTimeoutNotValid)
)

type clientOptions struct {
	baseURL       string
	signFn        crypto.SignFn
	signFnGiven   bool
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

	if !strings.Contains(c.baseURL, "://") {
		c.baseURL = "https://" + c.baseURL // a base URL without a scheme is read as https
	}
	if !validBaseURL(c.baseURL) {
		return ErrInvalidBaseURL
	}

	if c.timeout <= 0 || c.timeout > MaxTimeout {
		return ErrInvalidTimeOut
	}

	if c.streamTimeout <= 0 || c.streamTimeout > MaxTimeout {
		return ErrInvalidStreamTimeout
	}

	return nil
}

// validBaseURL accepts an http or https URL with a host, without user info, query or fragment, and
// with a port, if given, in 1..65535.
func validBaseURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.Hostname() == "" || u.User != nil ||
		strings.ContainsAny(raw, "?#") { // url.Parse keeps no trace of an empty "#"
		return false
	}
	if port := u.Port(); port != "" {
		n, err := strconv.ParseUint(port, 10, 16)
		return err == nil && n > 0
	}
	return true
}

var defaultClientOptions = clientOptions{
	baseURL:       DefaultBaseURL,
	signFn:        nil,
	timeout:       DefaultTimeout,
	streamTimeout: DefaultStreamTimeout,
}

type ClientOption func(*clientOptions)

func WithBaseURL(url string) ClientOption {
	return func(c *clientOptions) {
		c.baseURL = url
	}
}

// WithSignatureFunction makes fn the client's signer, in place of a private key: NewServiceClient
// then takes an empty key, and refuses a non-empty one. fn is a custom signer, or the signer
// crypto.NewSignerFromHex or crypto.NewSigner builds. It must not be nil.
func WithSignatureFunction(fn crypto.SignFn) ClientOption {
	return func(c *clientOptions) {
		c.signFn = fn
		c.signFnGiven = true
	}
}

// WithTimeout sets the deadline of each unary call whose context has none; streams use
// WithStreamTimeout. A deadline on the call's context replaces it, shorter or longer.
// It must be positive and at most MaxTimeout (2147483647 ms).
//
// Default: DefaultTimeout (15 seconds).
func WithTimeout(t time.Duration) ClientOption {
	return func(c *clientOptions) {
		c.timeout = t
	}
}

// WithStreamTimeout sets the deadline of each client- and server-streaming call whose context has
// none, including the wait for its first message. A deadline on the call's context replaces it,
// shorter or longer. It must be positive and at most MaxTimeout (2147483647 ms).
//
// Default: DefaultStreamTimeout (5 minutes).
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

// WithHTTPTransport sets the http.RoundTripper under the signing transport. It is meant for tests:
// it sends the client's requests, still signed, through a mock RoundTripper or the transport of a
// test server, such as httptest.Server.Client().Transport. Production code normally does not need it.
//
// It replaces the transport the client picks itself: http.DefaultTransport, or for ProtocolGRPC on
// an http:// base URL an HTTP/2 transport without TLS. A gRPC test over http:// therefore passes a
// transport that speaks HTTP/2. A nil value is ignored.
func WithHTTPTransport(rt http.RoundTripper) ClientOption {
	return func(c *clientOptions) {
		c.transport = rt
	}
}

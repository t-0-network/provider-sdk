package network

import (
	"errors"
	"math"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/t-0-network/provider-sdk/go/crypto"
)

const (
	defaultBaseURL       = "https://api.t-0.network"
	defaultTimeout       = 15 * time.Second
	defaultStreamTimeout = 5 * time.Minute
	// maxTimeout is the largest timeout accepted: 2^31-1 ms.
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

	if !strings.Contains(c.baseURL, "://") {
		c.baseURL = "https://" + c.baseURL // a base URL without a scheme is read as https
	}
	if !validBaseURL(c.baseURL) {
		return ErrInvalidBaseURL
	}

	if c.timeout <= 0 || c.timeout > maxTimeout {
		return ErrInvalidTimeOut
	}

	if c.streamTimeout <= 0 || c.streamTimeout > maxTimeout {
		return ErrInvalidStreamTimeout
	}

	return nil
}

// validBaseURL accepts http:// or https://, a host without user info, if given a port in 1..65535
// without leading zeros, and a path (see validPath): no query or fragment.
func validBaseURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || (u.Scheme != "http" && u.Scheme != "https") || u.User != nil || !validHost(u) {
		return false
	}
	// The path as written: u.Path is decoded ("%2F" becomes "/").
	authorityAndPath := raw[strings.Index(raw, "://")+len("://"):]
	path := ""
	if i := strings.IndexByte(authorityAndPath, '/'); i >= 0 {
		path = authorityAndPath[i:]
	}
	if !validPath(path) || strings.ContainsAny(raw, "?#") {
		return false
	}
	if port := u.Port(); port != "" || strings.HasSuffix(u.Host, ":") {
		n, err := strconv.Atoi(port)
		return err == nil && port[0] != '0' && n >= 1 && n <= 65535
	}
	return true
}

// validPath accepts "", "/", or segments of ASCII letters, digits and "-._~" (not "." or ".."), each
// after one "/", with an optional trailing "/". Calls go to <path>/<service>/<method>. Other paths
// ("//", "/..", "/%41") would reach different URLs in different SDKs, whose HTTP clients normalize
// them differently.
func validPath(path string) bool {
	path = strings.TrimSuffix(path, "/")
	if path == "" {
		return true
	}
	for _, segment := range strings.Split(path, "/")[1:] {
		if segment == "" || segment == "." || segment == ".." {
			return false
		}
		for i := 0; i < len(segment); i++ {
			if c := segment[i]; !isASCIILetter(c) && (c < '0' || c > '9') && !strings.ContainsRune("-._~", rune(c)) {
				return false
			}
		}
	}
	return true
}

// validHost accepts an IPv4 address, an IPv6 address in brackets, or a name of labels made of
// ASCII letters, digits and inner '-', whose last label starts with a letter. gRPC clients cannot
// connect to other names, such as ones with '_' or an empty label.
func validHost(u *url.URL) bool {
	host := u.Hostname()
	if strings.HasPrefix(u.Host, "[") {
		return strings.Contains(host, ":") && net.ParseIP(host) != nil
	}
	if ip := net.ParseIP(host); ip != nil {
		return ip.To4() != nil
	}
	labels := strings.Split(host, ".")
	for _, label := range labels {
		if !validLabel(label) {
			return false
		}
	}
	return isASCIILetter(labels[len(labels)-1][0])
}

func validLabel(label string) bool {
	if label == "" || label[0] == '-' || label[len(label)-1] == '-' {
		return false
	}
	for i := 0; i < len(label); i++ {
		if c := label[i]; !isASCIILetter(c) && (c < '0' || c > '9') && c != '-' {
			return false
		}
	}
	return true
}

func isASCIILetter(c byte) bool {
	return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')
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
// shorter or longer. It must be positive and at most 2147483647 ms.
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
// their own servers. It replaces the one the client picks itself: http.DefaultTransport, or for
// ProtocolGRPC on an http:// base URL an HTTP/2 transport without TLS. A nil value is ignored.
func withHTTPTransport(rt http.RoundTripper) ClientOption {
	return func(c *clientOptions) {
		c.transport = rt
	}
}

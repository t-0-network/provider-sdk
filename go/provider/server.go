package provider

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"net"
	"net/http"
	"strconv"
	"sync"
	"time"

	"golang.org/x/net/http/httpguts"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"

	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// Default timeout values
const (
	DefaultAddr              = ":8080"
	DefaultReadTimeout       = 10 * time.Second
	DefaultWriteTimeout      = 10 * time.Second
	DefaultReadHeaderTimeout = 10 * time.Second
	DefaultShutdownTimeout   = 15 * time.Second

	// ServerStartupTimeout was how long StartServer waited for the server to be
	// ready. StartServer returns once the address is bound.
	//
	// Deprecated: Not used by the SDK; will be removed in a future release.
	ServerStartupTimeout = 5 * time.Second
)

// ServerOption configures server options using the functional options pattern
type ServerOption func(*serverOptions)

type serverOptions struct {
	addr              string
	readTimeout       time.Duration
	writeTimeout      time.Duration
	readHeaderTimeout time.Duration
	tlsConfig         *tls.Config
	shutdownTimeout   time.Duration // applies only to started server
	http2Config       *http2.Server
}

// WithAddr sets the server's address to listen on (host:port format)
// If an empty string is provided, the default ":8080" will be used.
// StartServer refuses a numeric port outside 0..65535.
func WithAddr(addr string) ServerOption {
	return func(opts *serverOptions) {
		if addr != "" {
			opts.addr = addr
		}
	}
}

// WithReadTimeout sets the maximum duration for reading the entire request
// including the body. A timeout of 0 means no timeout.
// Negative timeouts are ignored and the default will be used.
func WithReadTimeout(timeout time.Duration) ServerOption {
	return func(opts *serverOptions) {
		if timeout < 0 {
			// Negative timeouts don't make sense, ignore and keep default
			return
		}
		opts.readTimeout = timeout
	}
}

// WithWriteTimeout sets the maximum duration before timing out writes of the response
// A timeout of 0 means no timeout.
// Negative timeouts are ignored and the default will be used.
func WithWriteTimeout(timeout time.Duration) ServerOption {
	return func(opts *serverOptions) {
		if timeout < 0 {
			// Negative timeouts don't make sense, ignore and keep default
			return
		}
		opts.writeTimeout = timeout
	}
}

// WithReadHeaderTimeout sets the amount of time allowed to read request headers
// A timeout of 0 means no timeout.
// Negative timeouts are ignored and the default will be used.
func WithReadHeaderTimeout(timeout time.Duration) ServerOption {
	return func(opts *serverOptions) {
		if timeout < 0 {
			// Negative timeouts don't make sense, ignore and keep default
			return
		}
		opts.readHeaderTimeout = timeout
	}
}

// WithTLSConfig sets the TLS configuration for the server
func WithTLSConfig(tlsConfig *tls.Config) ServerOption {
	return func(opts *serverOptions) {
		opts.tlsConfig = tlsConfig
	}
}

// WithShutdownTimeout sets the maximum duration to wait for the server to shutdown gracefully
// If the timeout is <= 0, the default timeout will be used.
func WithShutdownTimeout(timeout time.Duration) ServerOption {
	return func(opts *serverOptions) {
		if timeout > 0 {
			opts.shutdownTimeout = timeout
		}
	}
}

// WithHTTP2Config sets custom HTTP/2 server configuration
func WithHTTP2Config(config *http2.Server) ServerOption {
	return func(opts *serverOptions) {
		if config != nil {
			opts.http2Config = config
		}
	}
}

var defaultServerOptions = serverOptions{
	addr:              DefaultAddr,
	readTimeout:       DefaultReadTimeout,
	writeTimeout:      DefaultWriteTimeout,
	readHeaderTimeout: DefaultReadHeaderTimeout,
	tlsConfig:         nil,
	shutdownTimeout:   DefaultShutdownTimeout,
	http2Config:       &http2.Server{},
}

// ServerShutdownFn is a function that gracefully shuts down the server.
// It blocks until the server is shut down or the context is cancelled.
// It is safe to call concurrently, but only the first call is guaranteed to succeed.
type ServerShutdownFn func(ctx context.Context) error

// NewServer returns a ready-to-use *http.Server with the provided handler registered.
// The server is not started - you need to call ListenAndServe or similar methods.
func NewServer(handler http.Handler, serverOptions ...ServerOption) *http.Server {
	if handler == nil {
		panic(contract.ServiceNull)
	}

	server, _ := createServer(handler, serverOptions)
	return server
}

// StartServer creates and starts a new HTTP server with the provided handler.
//
// It binds the address, starts serving in the background and returns. An error
// that ends serving later is returned by the shutdown function.
//
// Example:
//
//	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
//	    w.WriteHeader(http.StatusOK)
//	})
//
//	shutdown, err := StartServer(handler,
//	    WithAddr(":8080"),
//	    WithReadTimeout(30*time.Second),
//	)
//	if err != nil {
//	    log.Fatal(err)
//	}
//	defer shutdown(context.Background())
//
// Returns:
//   - ServerShutdownFn: Safe for concurrent use, only first call performs shutdown
//   - error: Non-nil if the address could not be bound: the error of net.Listen
func StartServer(handler http.Handler, serverOptions ...ServerOption) (ServerShutdownFn, error) {
	if handler == nil {
		return nil, errors.New(contract.ServiceNull)
	}

	server, opts := createServer(handler, serverOptions)
	if err := opts.validate(); err != nil {
		return nil, err
	}
	listener, err := net.Listen("tcp", server.Addr)
	if err != nil {
		return nil, err
	}

	// The error serving ended with, if it was not ended by Shutdown. Buffered, so
	// the server goroutine never blocks on it; the shutdown function reads it.
	serveErr := make(chan error, 1)

	// Wait group for graceful shutdown
	var wg sync.WaitGroup
	// Once to ensure server shutdown is only executed once
	var shutdownOnce sync.Once

	wg.Add(1)

	go func() {
		defer wg.Done()

		var err error
		if opts.tlsConfig != nil {
			err = server.ServeTLS(listener, "", "")
		} else {
			err = server.Serve(listener)
		}
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
		}
	}()

	// Create a reusable shutdown function that can be called concurrently
	serverShutdown := func(ctx context.Context) error {
		// Check if context is already cancelled
		if err := ctx.Err(); err != nil {
			return fmt.Errorf(contract.ShutdownContextDone, err)
		}

		// Variable to collect shutdown errors
		var shutdownErr error

		// Ensure shutdown only happens once
		shutdownOnce.Do(func() {
			// The shutdown timeout, or the caller's deadline if that comes first.
			timeoutCtx, cancel := context.WithTimeout(ctx, opts.shutdownTimeout)
			defer cancel()

			// Shutdown the server gracefully
			if err := server.Shutdown(timeoutCtx); err != nil {
				shutdownErr = fmt.Errorf(contract.ServerShutdownFailed, err)
			}

			// Always ensure listener is closed
			listener.Close()

			// Wait for the server goroutine to finish with timeout
			done := make(chan struct{})
			go func() {
				wg.Wait()
				close(done)
			}()

			select {
			case <-done:
				// Server goroutine finished normally
			case <-timeoutCtx.Done():
				// Timeout occurred while waiting for server goroutine
				// We can't force kill the goroutine, but we can continue with shutdown
				// The goroutine will eventually finish when the server stops
			}

			// An error that ended serving before the shutdown.
			select {
			case err := <-serveErr:
				shutdownErr = errors.Join(shutdownErr, err)
			default:
			}
		})

		return shutdownErr
	}

	return serverShutdown, nil
}

// validate refuses a numeric port outside 0..65535. Any other address is left to net.Listen, which
// also takes a service name such as ":http".
func (o *serverOptions) validate() error {
	if _, port, err := net.SplitHostPort(o.addr); err == nil {
		n, err := strconv.ParseInt(port, 10, 64)
		if errors.Is(err, strconv.ErrRange) || err == nil && (n < 0 || n > 65535) {
			return errors.New(contract.PortNotValid)
		}
	}
	return nil
}

// createServer creates a new http.Server with the provided handler and options
// This is an internal helper to avoid code duplication
func createServer(handler http.Handler, options []ServerOption) (*http.Server, *serverOptions) {
	// Process options once and store them for later use
	opts := defaultServerOptions
	for _, opt := range options {
		opt(&opts)
	}

	server := &http.Server{
		Addr:              opts.addr,
		ReadTimeout:       opts.readTimeout,
		ReadHeaderTimeout: opts.readHeaderTimeout,
		WriteTimeout:      opts.writeTimeout,
		TLSConfig:         opts.tlsConfig,
	}

	// An h2c connection gets the idle timeout of an HTTP/1.1 one, as
	// http2.ConfigureServer gives a TLS one: IdleTimeout, else ReadTimeout. x/net's
	// h2c leaves it at none. A copy, so that neither the default nor the
	// caller's configuration changes.
	http2Config := *opts.http2Config
	if http2Config.IdleTimeout == 0 {
		http2Config.IdleTimeout = server.IdleTimeout
		if http2Config.IdleTimeout == 0 {
			http2Config.IdleTimeout = server.ReadTimeout
		}
	}
	server.Handler = withoutH2CUpgrade(h2c.NewHandler(handler, &http2Config))
	return server, &opts
}

// withoutH2CUpgrade serves an HTTP/1.1 request that asks to upgrade to h2c as
// the HTTP/1.1 request it is, by dropping its Upgrade header. x/net's h2c reads
// the whole body of an upgrade request into memory before any handler runs, so
// no body limit could apply to it. HTTP/2 without TLS is still served to a client
// that starts with it (prior knowledge), as gRPC clients do.
func withoutH2CUpgrade(handler http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if httpguts.HeaderValuesContainsToken(r.Header["Upgrade"], "h2c") {
			r.Header.Del("Upgrade")
			r.Header.Del("Http2-Settings")
		}
		handler.ServeHTTP(w, r)
	})
}

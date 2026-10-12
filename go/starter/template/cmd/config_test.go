package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"testing"
)

const (
	testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2"
	testNetworkKey = "0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b"
)

func TestLoadConfig(t *testing.T) {
	dir := t.TempDir()
	t.Chdir(dir)

	envPath, err := filepath.Abs(".env")
	if err != nil {
		t.Fatalf("abs .env: %v", err)
	}
	notice := fmt.Sprintf("No .env at %s — taking configuration from the environment instead\n", envPath)
	privateKeyHelp := ".env is read from the working directory, and we looked in " + envPath + ". " +
		"Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
		"Only a project with no .env at all starts one from .env.example — an existing .env holds the key generated for you, and its private half is not recoverable."

	tests := []struct {
		name         string
		privateKey   string
		networkKey   string
		port         string
		endpoint     string
		wantErr      string
		wantHelp     string
		wantAddr     string
		wantEndpoint string
		wantPrivate  string
		wantNetwork  string
	}{
		{
			name:         "both keys trimmed",
			privateKey:   "  " + testPrivateKey + "\n",
			networkKey:   "\t" + testNetworkKey + "  ",
			port:         " 8080 ",
			endpoint:     "  https://example.test  ",
			wantAddr:     ":8080",
			wantEndpoint: "https://example.test",
			wantPrivate:  testPrivateKey,
			wantNetwork:  testNetworkKey,
		},
		{
			name:       "blank private key",
			privateKey: "   ",
			networkKey: testNetworkKey,
			wantErr:    "PROVIDER_PRIVATE_KEY is not set",
			wantHelp:   privateKeyHelp,
		},
		{
			name:       "blank network key",
			privateKey: testPrivateKey,
			networkKey: " \t ",
			wantErr:    "NETWORK_PUBLIC_KEY is not set",
			wantHelp:   "Ask the t-0 team for the network public key and put it in .env.",
		},
		{
			name:         "port blank and blank endpoint",
			privateKey:   testPrivateKey,
			networkKey:   testNetworkKey,
			port:         "",
			endpoint:     "",
			wantAddr:     ":8080",
			wantEndpoint: "https://api-sandbox.t-0.network",
			wantPrivate:  testPrivateKey,
			wantNetwork:  testNetworkKey,
		},
		{
			name:         "port 8080",
			privateKey:   testPrivateKey,
			networkKey:   testNetworkKey,
			port:         "8080",
			endpoint:     "https://example.test",
			wantAddr:     ":8080",
			wantEndpoint: "https://example.test",
			wantPrivate:  testPrivateKey,
			wantNetwork:  testNetworkKey,
		},
		{
			name:         "port surrounded by spaces",
			privateKey:   testPrivateKey,
			networkKey:   testNetworkKey,
			port:         " 9000 ",
			endpoint:     "",
			wantAddr:     ":9000",
			wantEndpoint: "https://api-sandbox.t-0.network",
			wantPrivate:  testPrivateKey,
			wantNetwork:  testNetworkKey,
		},
		{
			name:         "port whitespace only",
			privateKey:   testPrivateKey,
			networkKey:   testNetworkKey,
			port:         "   ",
			endpoint:     "   ",
			wantAddr:     ":8080",
			wantEndpoint: "https://api-sandbox.t-0.network",
			wantPrivate:  testPrivateKey,
			wantNetwork:  testNetworkKey,
		},
		{
			name:       "port 0",
			privateKey: testPrivateKey,
			networkKey: testNetworkKey,
			port:       "0",
			wantErr:    "PORT is not a valid port number: 0",
			wantHelp:   "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
		},
		{
			name:       "port http",
			privateKey: testPrivateKey,
			networkKey: testNetworkKey,
			port:       "http",
			wantErr:    "PORT is not a valid port number: http",
			wantHelp:   "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
		},
		{
			name:       "port 65536",
			privateKey: testPrivateKey,
			networkKey: testNetworkKey,
			port:       "65536",
			wantErr:    "PORT is not a valid port number: 65536",
			wantHelp:   "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Setenv("PROVIDER_PRIVATE_KEY", tt.privateKey)
			t.Setenv("NETWORK_PUBLIC_KEY", tt.networkKey)
			t.Setenv("PORT", tt.port)
			t.Setenv("TZERO_ENDPOINT", tt.endpoint)

			var cfg Config
			var loadErr error
			stderr := captureStderr(t, func() {
				cfg, loadErr = loadConfig()
			})
			if stderr != notice {
				t.Errorf("missing .env notice = %q, want %q", stderr, notice)
			}

			if tt.wantErr != "" {
				var ce *configurationError
				if !errors.As(loadErr, &ce) {
					t.Fatalf("error = %v, want configuration error %q", loadErr, tt.wantErr)
				}
				if ce.msg != tt.wantErr {
					t.Errorf("message = %q, want %q", ce.msg, tt.wantErr)
				}
				if ce.help != tt.wantHelp {
					t.Errorf("help = %q, want %q", ce.help, tt.wantHelp)
				}
				return
			}
			if loadErr != nil {
				t.Fatalf("loadConfig: %v", loadErr)
			}
			if string(cfg.ProviderPrivateKey) != tt.wantPrivate {
				t.Errorf("private key = %q, want %q", cfg.ProviderPrivateKey, tt.wantPrivate)
			}
			if string(cfg.NetworkPublicKey) != tt.wantNetwork {
				t.Errorf("network key = %q, want %q", cfg.NetworkPublicKey, tt.wantNetwork)
			}
			if cfg.ServerAddr != tt.wantAddr {
				t.Errorf("addr = %q, want %q", cfg.ServerAddr, tt.wantAddr)
			}
			if cfg.TZeroEndpoint != tt.wantEndpoint {
				t.Errorf("endpoint = %q, want %q", cfg.TZeroEndpoint, tt.wantEndpoint)
			}
		})
	}

}

func captureStderr(t *testing.T, fn func()) string {
	t.Helper()
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatalf("pipe: %v", err)
	}
	orig := os.Stderr
	os.Stderr = w
	defer func() { os.Stderr = orig }()

	fn()

	os.Stderr = orig
	if err := w.Close(); err != nil {
		t.Fatalf("close stderr pipe: %v", err)
	}
	out, err := io.ReadAll(r)
	if err != nil {
		t.Fatalf("read stderr: %v", err)
	}
	return string(out)
}

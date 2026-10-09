package main

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
	"github.com/t-0-network/provider-sdk/go/crypto"
	"github.com/t-0-network/provider-sdk/go/network"
	"github.com/t-0-network/provider-sdk/go/provider"
)

const (
	sandboxEndpoint = "https://api-sandbox.t-0.network"

	// invalidNetworkPublicKeyPrefix is the text the SDK puts in front of a rejected network key.
	invalidNetworkPublicKeyPrefix = "invalid network public key: "

	networkPublicKeyHelp   = "Ask the t-0 team for the network public key and put it in .env."
	privateKeyUnusableHelp = "Any 32 random bytes will do: openssl rand -hex 32."
	portHelp               = "Set PORT to an integer between 1 and 65535, or leave it unset for 8080."
)

var portPattern = regexp.MustCompile(`^[0-9]+$`)

type Config struct {
	NetworkPublicKey        provider.NetworkPublicKeyHexed
	ProviderPrivateKey      network.PrivateKeyHexed
	TZeroEndpoint           string
	ServerAddr              string
	QuotePublishingInterval time.Duration
}

type configurationError struct {
	msg  string
	help string
}

func (e *configurationError) Error() string { return e.msg }

func loadConfig() (Config, error) {
	envPath, err := filepath.Abs(".env")
	if err != nil {
		return Config{}, err
	}

	if _, err := os.Stat(envPath); err != nil {
		if !os.IsNotExist(err) {
			return Config{}, err
		}
		fmt.Fprintf(os.Stderr, "No .env at %s — taking configuration from the environment instead\n", envPath)
	} else if err := godotenv.Load(envPath); err != nil && !os.IsNotExist(err) {
		// A missing file was handled above. godotenv does not override variables
		// already set in the process environment.
		return Config{}, err
	}

	// Anything but an integer in 1..2147483647 gives the default of 5000 ms.
	intervalMs, err := strconv.ParseInt(os.Getenv("QUOTE_PUBLISHING_INTERVAL"), 10, 32)
	if err != nil || intervalMs <= 0 {
		intervalMs = 5000
	}

	privateKey := strings.TrimSpace(os.Getenv("PROVIDER_PRIVATE_KEY"))
	networkKey := strings.TrimSpace(os.Getenv("NETWORK_PUBLIC_KEY"))
	endpoint := strings.TrimSpace(os.Getenv("TZERO_ENDPOINT"))
	if endpoint == "" {
		endpoint = sandboxEndpoint
	}

	if privateKey == "" {
		return Config{}, &configurationError{
			msg:  "PROVIDER_PRIVATE_KEY is not set",
			help: privateKeyMissingHelp(envPath),
		}
	}
	if _, err := crypto.GetPrivateKeyFromHex(privateKey); err != nil {
		return Config{}, &configurationError{
			msg:  "PROVIDER_PRIVATE_KEY is not usable: " + err.Error(),
			help: privateKeyUnusableHelp,
		}
	}
	if networkKey == "" {
		return Config{}, &configurationError{
			msg:  "NETWORK_PUBLIC_KEY is not set",
			help: networkPublicKeyHelp,
		}
	}

	port, err := parsePort(os.Getenv("PORT"))
	if err != nil {
		return Config{}, err
	}

	return Config{
		NetworkPublicKey:        provider.NetworkPublicKeyHexed(networkKey),
		ProviderPrivateKey:      network.PrivateKeyHexed(privateKey),
		TZeroEndpoint:           endpoint,
		ServerAddr:              fmt.Sprintf(":%d", port),
		QuotePublishingInterval: time.Duration(intervalMs) * time.Millisecond,
	}, nil
}

func privateKeyMissingHelp(envPath string) string {
	return ".env is read from the working directory, and we looked in " + envPath + ". " +
		"Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
		"Only a project with no .env at all starts one from .env.example — an existing .env holds the key generated for you, and its private half is not recoverable."
}

func parsePort(raw string) (int, error) {
	portStr := strings.TrimSpace(raw)
	if portStr == "" {
		return 8080, nil
	}
	port, err := strconv.Atoi(portStr)
	if err != nil || !portPattern.MatchString(portStr) || port < 1 || port > 65535 {
		return 0, &configurationError{
			msg:  "PORT is not a valid port number: " + portStr,
			help: portHelp,
		}
	}
	return port, nil
}

// asConfigurationError maps a server-setup failure whose text is the SDK's
// invalid-network-key error onto the configuration help. Any other error is
// returned unchanged.
func asConfigurationError(err error) error {
	if err != nil && strings.HasPrefix(err.Error(), invalidNetworkPublicKeyPrefix) {
		return &configurationError{msg: err.Error(), help: networkPublicKeyHelp}
	}
	return err
}

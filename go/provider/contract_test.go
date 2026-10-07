package provider

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"regexp"
	"slices"
	"sort"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/t-0-network/provider-sdk/go/common"
	"github.com/t-0-network/provider-sdk/go/internal/contract"
	"github.com/t-0-network/provider-sdk/go/network"
)

type contractVectors struct {
	Constants     map[string]any      `json:"constants"`
	Messages      map[string]string   `json:"messages"`
	MessagesScope map[string][]string `json:"messages_scope"`
}

func loadContract(t *testing.T) contractVectors {
	t.Helper()
	data, err := os.ReadFile("../../cross_test/test_vectors.json")
	require.NoError(t, err)
	var v contractVectors
	require.NoError(t, json.Unmarshal(data, &v))
	return v
}

// The named constants of the SDK against `constants` in cross_test/test_vectors.json, the file
// every SDK compares its constants with.
func TestContract_Constants(t *testing.T) {
	v := loadContract(t)

	sdk := map[string]any{
		"default_max_body_size":      float64(DefaultMaxBodySize),
		"timestamp_window_ms":        float64(TimestampWindow.Milliseconds()),
		"public_key_header":          common.PublicKeyHeader,
		"signature_header":           common.SignatureHeader,
		"signature_timestamp_header": common.SignatureTimestampHeader,
		"default_base_url":           network.DefaultBaseURL,
		"default_timeout_ms":         float64(network.DefaultTimeout.Milliseconds()),
		"default_stream_timeout_ms":  float64(network.DefaultStreamTimeout.Milliseconds()),
		"max_timeout_ms":             float64(network.MaxTimeout.Milliseconds()),
	}
	require.Equal(t, keys(v.Constants), keys(sdk), "every shared constant is defined")
	for name, value := range v.Constants {
		require.Equal(t, value, sdk[name], name)
	}
}

// Every message the SDK raises itself against `messages` in cross_test/test_vectors.json: the Go
// SDK defines exactly the messages whose messages_scope lists go, or that have no scope. A
// message with placeholders is filled with sample values on both sides.
func TestContract_Messages(t *testing.T) {
	v := loadContract(t)

	sdk := map[string]string{
		"private_key_empty":             contract.PrivateKeyEmpty,
		"private_key_malformed":         contract.PrivateKeyMalformed,
		"private_key_out_of_range":      contract.PrivateKeyOutOfRange,
		"public_key_not_hex":            contract.PublicKeyNotHex,
		"public_key_not_a_point":        contract.PublicKeyNotAPoint,
		"network_public_key_not_set":    contract.NetworkPublicKeyNotSet,
		"network_public_key_invalid":    contract.NetworkPublicKeyInvalid,
		"signer_null":                   contract.SignerNull,
		"key_and_signer":                contract.KeyAndSigner,
		"digest_length":                 contract.DigestLength,
		"signer_signature_invalid":      contract.SignerSignatureInvalid,
		"signer_public_key_invalid":     contract.SignerPublicKeyInvalid,
		"base_url_not_set":              contract.BaseURLNotSet,
		"base_url_not_valid":            contract.BaseURLNotValid,
		"timeout_not_valid":             contract.TimeoutNotValid,
		"stream_timeout_not_valid":      contract.StreamTimeoutNotValid,
		"signing_failed":                contract.SigningFailed,
		"get_not_supported":             contract.GetNotSupported,
		"bidi_not_supported":            contract.BidiNotSupported,
		"first_message_incomplete":      contract.FirstMessageIncomplete,
		"first_message_read_failed":     contract.FirstMessageReadFailed,
		"service_null":                  contract.ServiceNull,
		"port_not_valid":                contract.PortNotValid,
		"shutdown_context_done":         contract.ShutdownContextDone,
		"server_shutdown_failed":        contract.ServerShutdownFailed,
		"missing_header":                contract.MissingHeader,
		"invalid_header_encoding":       contract.InvalidHeaderEncoding,
		"timestamp_not_decimal":         contract.TimestampNotDecimal,
		"timestamp_out_of_range":        contract.TimestampOutOfRange,
		"timestamp_outside_window":      contract.TimestampOutsideWindow,
		"unknown_public_key":            contract.UnknownPublicKey,
		"signature_verification_failed": contract.SignatureVerificationFailed,
		"body_too_large":                contract.BodyTooLarge,
		"no_signature_result":           contract.NoSignatureResult,
		"no_first_message":              contract.NoFirstMessage,
		"truncated_first_message":       contract.TruncatedFirstMessage,
		"unknown_service":               contract.UnknownService,
		"response_invalid":              contract.ResponseInvalid,
		"response_validation_error":     contract.ResponseValidationError,
		"request_body_read_failed":      contract.RequestBodyReadFailed,
	}

	var goNames []string
	for name := range v.Messages {
		if scope, scoped := v.MessagesScope[name]; !scoped || slices.Contains(scope, "go") {
			goNames = append(goNames, name)
		}
	}
	sort.Strings(goNames)
	require.Equal(t, goNames, keys(sdk), "exactly the messages the Go SDK raises are defined")

	placeholder := regexp.MustCompile(`\{([a-z]+)\}`)
	verb := regexp.MustCompile(`%[sdw]`)
	for _, name := range goNames {
		want := v.Messages[name]
		// Each placeholder, in order, filled with a sample: a number for %d, an error for %w.
		var args []any
		var samples []string
		for i, match := range placeholder.FindAllStringSubmatch(want, -1) {
			sample := "sample " + match[1]
			verbs := verb.FindAllString(sdk[name], -1)
			require.Greater(t, len(verbs), i, "%s: the Go format has a verb for {%s}", name, match[1])
			switch verbs[i] {
			case "%d":
				sample = "1234"
				args = append(args, 1234)
			case "%w":
				args = append(args, errors.New(sample))
			default:
				args = append(args, sample)
			}
			samples = append(samples, sample)
		}
		i := 0
		want = placeholder.ReplaceAllStringFunc(want, func(string) string { i++; return samples[i-1] })

		got := sdk[name]
		if len(args) > 0 {
			got = fmt.Errorf(got, args...).Error()
		}
		require.False(t, strings.Contains(got, "%!"), "%s: %q has a verb with no value", name, got)
		require.Equal(t, want, got, name)
	}
}

// The body limit WithMaxBodySize gives a handler, against max_body_size_cases: 0 or less keeps
// DefaultMaxBodySize.
func TestContract_MaxBodySizeCases(t *testing.T) {
	data, err := os.ReadFile("../../cross_test/test_vectors.json")
	require.NoError(t, err)
	var v struct {
		Cases []struct {
			Name  string `json:"name"`
			Input int64  `json:"input"`
			Limit int64  `json:"limit"`
		} `json:"max_body_size_cases"`
	}
	require.NoError(t, json.Unmarshal(data, &v))
	require.NotEmpty(t, v.Cases)

	for _, c := range v.Cases {
		t.Run(c.Name, func(t *testing.T) {
			opts, err := newDefaultHandlerOptions(nil, nil, "")
			require.NoError(t, err)
			WithMaxBodySize(c.Input)(&opts)
			require.Equal(t, c.Limit, opts.verifySignatureMaxBodySize)
		})
	}
}

func keys[V any](m map[string]V) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

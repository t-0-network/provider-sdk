// Package pubkey holds the one public key rule of the SDK (rule V2 of
// docs/CROSS_SDK_RULES.md): hex with an optional 0x or 0X prefix of a compressed
// (33 bytes, 02 or 03) or uncompressed (65 bytes, 04) point on secp256k1.
package pubkey

import (
	"encoding/hex"
	"errors"
	"strings"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"

	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

// The two reasons a key is refused, the same text in every SDK (cross_test/test_vectors.json,
// public_key_parsing).
var (
	ErrNotHex   = errors.New(contract.PublicKeyNotHex)
	ErrNotPoint = errors.New(contract.PublicKeyNotAPoint)
)

// ParseHex decodes hex with an optional 0x or 0X prefix and parses the bytes
// with ParseBytes. hex.DecodeString accepts nothing but pairs of hex digits, so
// whitespace and junk are errors.
func ParseHex(publicKeyHexed string) (*secp256k1.PublicKey, error) {
	cleanHex := publicKeyHexed
	if strings.HasPrefix(cleanHex, "0x") || strings.HasPrefix(cleanHex, "0X") {
		cleanHex = cleanHex[2:]
	}
	if cleanHex == "" {
		return nil, ErrNotHex
	}

	publicKeyBytes, err := hex.DecodeString(cleanHex)
	if err != nil {
		return nil, ErrNotHex
	}

	return ParseBytes(publicKeyBytes)
}

// ParseBytes parses a compressed (33 bytes, 02 or 03) or uncompressed (65 bytes,
// 04) point on secp256k1. secp256k1.ParsePubKey also accepts the hybrid forms
// (65 bytes, 06 or 07), which rule V2 does not, so the form is checked first.
func ParseBytes(publicKeyBytes []byte) (*secp256k1.PublicKey, error) {
	switch {
	case len(publicKeyBytes) == secp256k1.PubKeyBytesLenCompressed &&
		(publicKeyBytes[0] == 0x02 || publicKeyBytes[0] == 0x03):
	case len(publicKeyBytes) == secp256k1.PubKeyBytesLenUncompressed && publicKeyBytes[0] == 0x04:
	default:
		return nil, ErrNotPoint
	}
	publicKey, err := secp256k1.ParsePubKey(publicKeyBytes)
	if err != nil {
		return nil, ErrNotPoint
	}

	return publicKey, nil
}

// Package pubkey holds the one public key rule of the SDK: hex with an optional
// 0x or 0X prefix, and bytes that secp256k1.ParsePubKey accepts.
package pubkey

import (
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
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
		return nil, errors.New("public key hex is empty")
	}

	publicKeyBytes, err := hex.DecodeString(cleanHex)
	if err != nil {
		return nil, fmt.Errorf("decoding public key hex: %w", err)
	}

	return ParseBytes(publicKeyBytes)
}

// ParseBytes parses a public key with secp256k1.ParsePubKey: 33 bytes starting
// with 02 or 03, or 65 bytes starting with 04, 06 or 07, that name a point on
// the curve.
func ParseBytes(publicKeyBytes []byte) (*secp256k1.PublicKey, error) {
	publicKey, err := secp256k1.ParsePubKey(publicKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("parsing public key bytes: %w", err)
	}

	return publicKey, nil
}

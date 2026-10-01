package provider

import (
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// parsePublicKeyHex parses the configured network public key and, in two steps
// (decodePublicKeyHex in the middleware, parsePublicKeyBytes in the verifier),
// the X-Public-Key header. Unlike crypto.GetPublicKeyFromHex it rejects the
// hybrid encodings (06/07).
func parsePublicKeyHex(publicKeyHexed string) (*secp256k1.PublicKey, error) {
	publicKeyBytes, err := decodePublicKeyHex(publicKeyHexed)
	if err != nil {
		return nil, err
	}

	return parsePublicKeyBytes(publicKeyBytes)
}

// decodePublicKeyHex decodes hex with an optional 0x or 0X prefix. hex.DecodeString
// accepts nothing but pairs of hex digits, so whitespace and junk are errors.
func decodePublicKeyHex(publicKeyHexed string) ([]byte, error) {
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

	return publicKeyBytes, nil
}

// parsePublicKeyBytes accepts 33 bytes starting with 02 or 03 (compressed) or 65
// bytes starting with 04 (uncompressed) that name a point on secp256k1.
func parsePublicKeyBytes(publicKeyBytes []byte) (*secp256k1.PublicKey, error) {
	switch {
	case len(publicKeyBytes) == secp256k1.PubKeyBytesLenCompressed &&
		(publicKeyBytes[0] == secp256k1.PubKeyFormatCompressedEven || publicKeyBytes[0] == secp256k1.PubKeyFormatCompressedOdd):
	case len(publicKeyBytes) == secp256k1.PubKeyBytesLenUncompressed &&
		publicKeyBytes[0] == secp256k1.PubKeyFormatUncompressed:
	default:
		return nil, errors.New("public key must be 33 bytes starting with 02 or 03, or 65 bytes starting with 04")
	}

	publicKey, err := secp256k1.ParsePubKey(publicKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("parsing public key bytes: %w", err)
	}

	return publicKey, nil
}

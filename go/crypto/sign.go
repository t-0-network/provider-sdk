package crypto

import (
	"errors"

	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/t-0-network/provider-sdk/go/internal/contract"
)

const (
	ethereumSignatureLength = 65 // 32 bytes r + 32 bytes s + 1 byte recovery ID
)

// SignFn is a signer: it signs a 32-byte digest and returns the signature, 64 bytes r‖s or 65
// bytes r‖s‖v, and the 65-byte uncompressed public key it verifies against. A client takes one
// as its custom signer (network.WithSignatureFunction), and checks its output before it sends
// anything. NewSignerFromHex and NewSigner build one from a private key.
type SignFn func(digest []byte) (sig []byte, publicKey []byte, err error)

// NewSigner returns the signer of privateKey: 65 bytes r‖s‖v with a low s (RFC 6979) and v the
// recovery id, 0 or 1, and the 65-byte uncompressed public key. A digest that is not 32 bytes is
// refused.
func NewSigner(privateKey *secp256k1.PrivateKey) SignFn {
	return func(digest []byte) ([]byte, []byte, error) {
		if len(digest) != 32 {
			return nil, nil, errors.New(contract.DigestLength)
		}
		return sign(digest, privateKey),
			GetPublicKeyBytes(privateKey.PubKey()), nil
	}
}

// NewSignerFromHex returns the signer of a private key that GetPrivateKeyFromHex accepts (see
// NewSigner), or its error.
func NewSignerFromHex(hexedPrivateKey string) (SignFn, error) {
	privateKey, err := GetPrivateKeyFromHex(hexedPrivateKey)
	if err != nil {
		return nil, err
	}

	return NewSigner(privateKey), nil
}

func sign(digest []byte, privateKey *secp256k1.PrivateKey) []byte {
	// Use SignCompact which returns recovery ID in the first byte
	compactSig := ecdsa.SignCompact(privateKey, digest, false)

	// compactSig is [recovery_id + 27][r][s] (65 bytes)
	// We need to adjust the recovery ID format for Ethereum
	signature := make([]byte, ethereumSignatureLength)
	copy(signature[:32], compactSig[1:33])  // R
	copy(signature[32:64], compactSig[33:]) // S
	signature[64] = compactSig[0] - 27      // V (recovery ID, subtract 27)

	return signature
}

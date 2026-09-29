package crypto

import (
	"encoding/hex"
	"errors"
	"fmt"
	"strings"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

func GetPrivateKeyBytes(privateKey *secp256k1.PrivateKey) []byte {
	return privateKey.Serialize()
}

// GetPrivateKeyFromHex parses a private key written as 64 hex characters, with or without a 0x
// prefix. Its value must be in [1, n-1], n being the secp256k1 order; it is never reduced mod n.
func GetPrivateKeyFromHex(privateKeyHexed string) (*secp256k1.PrivateKey, error) {
	if privateKeyHexed == "" {
		return nil, errors.New("private key must not be null or empty")
	}

	cleanHex := privateKeyHexed
	if strings.HasPrefix(cleanHex, "0x") || strings.HasPrefix(cleanHex, "0X") {
		cleanHex = cleanHex[2:]
	}
	privateKeyBytes, err := hex.DecodeString(cleanHex)
	if err != nil || len(privateKeyBytes) != secp256k1.PrivKeyBytesLen {
		return nil, errors.New("private key must be 32 bytes (64 hex characters)")
	}

	var key secp256k1.ModNScalar
	if overflow := key.SetByteSlice(privateKeyBytes); overflow || key.IsZero() {
		return nil, errors.New("private key must be in range [1, n-1]")
	}
	return secp256k1.NewPrivateKey(&key), nil
}

func HexPrivateKey(privateKey *secp256k1.PrivateKey) string {
	return "0x" + hex.EncodeToString(GetPrivateKeyBytes(privateKey))
}

func GetPublicKeyBytes(publicKey *secp256k1.PublicKey) []byte {
	return publicKey.SerializeUncompressed()
}

func GetPublicKeyFromBytes(pubKeyBytes []byte) (*secp256k1.PublicKey, error) {
	publicKey, err := secp256k1.ParsePubKey(pubKeyBytes)
	if err != nil {
		return nil, fmt.Errorf("parsing public key bytes: %w", err)
	}

	return publicKey, nil
}

func GetPublicKeyFromHex(publicKeyHexed string) (*secp256k1.PublicKey, error) {
	pubKeyBytes, err := hex.DecodeString(strings.TrimPrefix(strings.ToLower(publicKeyHexed), "0x"))
	if err != nil {
		return nil, fmt.Errorf("decoding public key hex: %w", err)
	}

	return GetPublicKeyFromBytes(pubKeyBytes)
}

func HexPublicKey(publicKey *secp256k1.PublicKey) string {
	return "0x" + hex.EncodeToString(GetPublicKeyBytes(publicKey))
}

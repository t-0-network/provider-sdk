package crypto

import "golang.org/x/crypto/sha3"

func LegacyKeccak256(b []byte) []byte {
	return LegacyKeccak256Concat(b)
}

// LegacyKeccak256Concat hashes the concatenation of parts without joining them first.
func LegacyKeccak256Concat(parts ...[]byte) []byte {
	hash := sha3.NewLegacyKeccak256()
	for _, part := range parts {
		hash.Write(part)
	}

	return hash.Sum(make([]byte, 0, 32))
}

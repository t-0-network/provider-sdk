package crypto

import (
	"bytes"
	"crypto/rand"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// Below the curve order n, which is every digest in practice, reducing the digest mod n before
// signing changes nothing: the signature is SignCompact's over the digest as given.
func TestSign_DigestBelowOrderUnchanged(t *testing.T) {
	key, err := GetPrivateKeyFromHex("6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8")
	if err != nil {
		t.Fatal(err)
	}
	digest := make([]byte, 32)
	for range 1000 {
		if _, err := rand.Read(digest); err != nil {
			t.Fatal(err)
		}
		var e secp256k1.ModNScalar
		if e.SetByteSlice(digest) {
			continue // at or above n: the vectors in signer_cases cover these
		}
		compact := ecdsa.SignCompact(key, digest, false)
		want := append(append([]byte{}, compact[1:]...), compact[0]-27)
		if got := sign(digest, key); !bytes.Equal(got, want) {
			t.Fatalf("digest %x: got %x, want %x", digest, got, want)
		}
	}
}

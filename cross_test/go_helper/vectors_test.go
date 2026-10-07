package main

import (
	"encoding/hex"
	"encoding/json"
	"os"
	"regexp"
	"testing"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/decred/dcrd/dcrec/secp256k1/v4/ecdsa"
	"github.com/t-0-network/provider-sdk/go/crypto"
)

// Every message another section of the fixture expects is one of the named messages, so an SDK
// that holds those as constants raises the same text.
func TestVectors_MessagesNamed(t *testing.T) {
	raw, err := os.ReadFile("../test_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var v struct {
		Messages      map[string]string   `json:"messages"`
		MessagesScope map[string][]string `json:"messages_scope"`
		ServerCases   []struct {
			Name   string `json:"name"`
			Expect struct {
				Message string `json:"message"`
			} `json:"expect"`
		} `json:"server_cases"`
		ClientCases []clientCase `json:"client_cases"`
	}
	if err := json.Unmarshal(raw, &v); err != nil {
		t.Fatal(err)
	}
	var sections map[string]json.RawMessage
	if err := json.Unmarshal(raw, &sections); err != nil {
		t.Fatal(err)
	}
	var patterns []*regexp.Regexp
	for _, text := range v.Messages {
		pattern := regexp.MustCompile(`\\\{[a-z]+\\\}`).ReplaceAllString(regexp.QuoteMeta(text), ".+")
		patterns = append(patterns, regexp.MustCompile("^"+pattern+"$"))
	}
	named := func(message string) bool {
		for _, p := range patterns {
			if p.MatchString(message) {
				return true
			}
		}
		return false
	}

	for _, c := range v.ServerCases {
		if c.Expect.Message != "" && !named(c.Expect.Message) {
			t.Errorf("server case %s: %q is not in messages", c.Name, c.Expect.Message)
		}
	}
	for _, c := range v.ClientCases {
		if c.Expect.Message != "" && !named(c.Expect.Message) {
			t.Errorf("client case %s: %q is not in messages", c.Name, c.Expect.Message)
		}
	}
	for _, section := range []string{"signer_cases", "public_key_parsing", "timestamp_parsing", "base_url_parsing", "private_key_parsing"} {
		var rows []struct {
			Name  string `json:"name"`
			Error string `json:"error"`
		}
		if err := json.Unmarshal(sections[section], &rows); err != nil {
			t.Fatalf("%s: %v", section, err)
		}
		for _, r := range rows {
			if r.Error != "" && !named(r.Error) {
				t.Errorf("%s %s: %q is not in messages", section, r.Name, r.Error)
			}
		}
	}

	sdks := map[string]bool{"go": true, "node": true, "python": true, "java": true, "csharp": true}
	for name, list := range v.MessagesScope {
		if _, ok := v.Messages[name]; !ok {
			t.Errorf("messages_scope names %s, which is not in messages", name)
		}
		for _, sdk := range list {
			if !sdks[sdk] {
				t.Errorf("messages_scope %s: unknown SDK %q", name, sdk)
			}
		}
	}
}

// Each signer case's signature is r‖s‖v with a low s and the v that recovers its public key from
// its digest, and that key is its private key's.
func TestVectors_SignerCases(t *testing.T) {
	raw, err := os.ReadFile("../test_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var v struct {
		SignerCases []struct {
			Name       string `json:"name"`
			PrivateKey string `json:"private_key"`
			Digest     string `json:"digest"`
			Signature  string `json:"signature"`
			PublicKey  string `json:"public_key"`
			Error      string `json:"error"`
		} `json:"signer_cases"`
	}
	if err := json.Unmarshal(raw, &v); err != nil {
		t.Fatal(err)
	}
	for _, c := range v.SignerCases {
		t.Run(c.Name, func(t *testing.T) {
			digest, err := hex.DecodeString(c.Digest)
			if err != nil {
				t.Fatal(err)
			}
			if c.Error != "" {
				if len(digest) == 32 {
					t.Errorf("a 32-byte digest with an error")
				}
				return
			}
			key, err := crypto.GetPrivateKeyFromHex(c.PrivateKey)
			if err != nil {
				t.Fatal(err)
			}
			if got := hex.EncodeToString(key.PubKey().SerializeUncompressed()); got != c.PublicKey {
				t.Errorf("public_key is not the private key's: %s", got)
			}
			sig, err := hex.DecodeString(c.Signature)
			if err != nil || len(sig) != 65 || sig[64] > 1 {
				t.Fatalf("signature is not 65 bytes ending in 0 or 1: %s", c.Signature)
			}
			var s secp256k1.ModNScalar
			if s.SetByteSlice(sig[32:64]); s.IsOverHalfOrder() {
				t.Error("high s")
			}
			recovered, _, err := ecdsa.RecoverCompact(append([]byte{27 + sig[64]}, sig[:64]...), digest)
			if err != nil || hex.EncodeToString(recovered.SerializeUncompressed()) != c.PublicKey {
				t.Errorf("v does not recover public_key (%v)", err)
			}
		})
	}
}

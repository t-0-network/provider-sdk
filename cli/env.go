package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func writeEnvFile(projectDir string, kp KeyPair) error {
	envExample := filepath.Join(projectDir, ".env.example")
	data, err := os.ReadFile(envExample)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("reading .env.example: %w", err)
	}

	content := string(data)

	// Replace private key placeholder — templates use various patterns,
	// most specific first (PRIVATE_KEY= is a substring of PROVIDER_PRIVATE_KEY=):
	//   PROVIDER_PRIVATE_KEY=your_private_key_here  (provider-sdk default)
	//   PROVIDER_PRIVATE_KEY=                        (provider-sdk default)
	//   PRIVATE_KEY=your_private_key_here
	//   PRIVATE_KEY=
	var keyLine string
	for _, pattern := range []string{
		"PROVIDER_PRIVATE_KEY=your_private_key_here",
		"PROVIDER_PRIVATE_KEY=",
		"PRIVATE_KEY=your_private_key_here",
		"PRIVATE_KEY=",
	} {
		if strings.Contains(content, pattern) {
			varName := pattern[:strings.Index(pattern, "=")]
			keyLine = varName + "=" + kp.PrivateKey
			content = strings.Replace(content, pattern, keyLine, 1)
			break
		}
	}

	// Record the matching public key next to the private key, so it can be
	// found later without re-running init: in the template's marker when it
	// has one, otherwise on a comment line right under the key.
	switch {
	case strings.Contains(content, "# your_public_key_here"):
		content = strings.Replace(content, "# your_public_key_here", "# "+kp.PublicKey, 1)
	case keyLine != "":
		content = strings.Replace(content, keyLine, keyLine+"\n# Public key for the line above (share it with t-0): "+kp.PublicKey, 1)
	}

	envPath := filepath.Join(projectDir, ".env")
	if err := os.WriteFile(envPath, []byte(content), 0600); err != nil {
		return fmt.Errorf("writing .env: %w", err)
	}
	return nil
}

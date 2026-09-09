package main

import (
	"encoding/hex"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
	"sort"
	"strings"
	"testing"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

func starterLangs(t *testing.T) []string {
	t.Helper()
	var langs []string
	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range entries {
		if info, err := os.Stat(filepath.Join("..", e.Name(), "starter")); err == nil && info.IsDir() {
			langs = append(langs, e.Name())
		}
	}
	sort.Strings(langs)
	configured := slices.Clone(Config.Languages)
	sort.Strings(configured)
	if !slices.Equal(langs, configured) {
		t.Fatalf("languages with a starter in the tree %v, Config.Languages %v — wire the missing one into cli/config.go", langs, configured)
	}
	return langs
}

var entryFiles = map[string][]string{
	"go":     {"go.mod", "go.sum", "cmd/main.go"},
	"node":   {"package.json", "tsconfig.json", "src/index.ts"},
	"python": {"pyproject.toml", "src/provider/main.py"},
	"java":   {"build.gradle.kts", "settings.gradle.kts", "gradlew", "src/main/java"},
	"csharp": {"test-project.csproj", "Program.cs", "appsettings.json"},
}

func requireEntryFiles(t *testing.T, lang, projectDir string) {
	t.Helper()
	files, ok := entryFiles[lang]
	if !ok {
		t.Fatalf("no entry files listed for lang=%s — add them to entryFiles", lang)
	}
	for _, f := range files {
		if _, err := os.Stat(filepath.Join(projectDir, filepath.FromSlash(f))); err != nil {
			t.Errorf("%s missing from the scaffolded project: %v", f, err)
		}
	}
	if lang == "java" {
		sources := 0
		filepath.WalkDir(filepath.Join(projectDir, "src", "main", "java"), func(p string, d fs.DirEntry, err error) error {
			if err == nil && !d.IsDir() && strings.HasSuffix(p, ".java") {
				sources++
			}
			return nil
		})
		if sources == 0 {
			t.Error("no .java sources under src/main/java — the scaffold would compile to nothing")
		}
	}
}

func parseDotenv(t *testing.T, p string) map[string]string {
	t.Helper()
	data, err := os.ReadFile(p)
	if err != nil {
		t.Fatalf("reading %s: %v", p, err)
	}
	vars := map[string]string{}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if k, v, ok := strings.Cut(line, "="); ok {
			vars[k] = v
		}
	}
	return vars
}

var (
	privateKeyRe       = regexp.MustCompile(`^0x[0-9a-f]{64}$`)
	printedPublicKeyRe = regexp.MustCompile(`0x04[0-9a-f]{128}`)
)

func instantiate(t *testing.T, lang string) (string, string) {
	t.Helper()
	projectDir := filepath.Join(t.TempDir(), "test-project")
	opts := ScaffoldOpts{
		Lang:        lang,
		ProjectName: "test-project",
		ProjectDir:  projectDir,
		Version:     "dev",
	}
	if lang == "go" {
		opts.ModulePath = "github.com/test/test-project"
	}
	var runErr error
	out := captureOutput(t, func() { runErr = run(opts) })
	if runErr != nil {
		t.Fatalf("run(%s): %v\n%s", lang, runErr, out)
	}
	return projectDir, out
}

func TestStarters_HaveDockerfiles(t *testing.T) {
	for _, lang := range starterLangs(t) {
		t.Run(lang, func(t *testing.T) {
			dir := "internal/embed/" + lang
			entries, err := embeddedTemplates.ReadDir(dir)
			if err != nil {
				t.Fatalf("ReadDir(%q): %v", dir, err)
			}
			var names []string
			for _, e := range entries {
				names = append(names, e.Name())
			}

			if !slices.Contains(names, "Dockerfile") {
				t.Errorf("%s: Dockerfile missing from the embedded template", lang)
			}
			hasDot := slices.Contains(names, "dot-dockerignore")
			hasPlain := slices.Contains(names, ".dockerignore")
			if !hasDot && !hasPlain {
				t.Errorf("%s: neither .dockerignore nor dot-dockerignore in the embedded template", lang)
			}
			if hasDot && hasPlain {
				t.Errorf("%s: both .dockerignore and dot-dockerignore — dot-dockerignore overwrites .dockerignore on scaffold (fs.WalkDir is lexical); delete the .dockerignore", lang)
			}
		})
	}
}

func TestRun_InstantiatesEveryStarter(t *testing.T) {
	for _, lang := range starterLangs(t) {
		t.Run(lang, func(t *testing.T) {
			projectDir, _ := instantiate(t, lang)

			for _, name := range []string{".gitignore", ".env", "Dockerfile", ".dockerignore"} {
				if _, err := os.Stat(filepath.Join(projectDir, name)); err != nil {
					t.Errorf("%s missing after run(): %v", name, err)
				}
			}
			for _, name := range []string{"dot-gitignore", "dot-dockerignore"} {
				if _, err := os.Stat(filepath.Join(projectDir, name)); err == nil {
					t.Errorf("%s still present — the template rename did not happen", name)
				}
			}
			requireEntryFiles(t, lang, projectDir)
		})
	}
}

func TestRun_WritesFreshPrivateKey(t *testing.T) {
	seenPriv := map[string]string{}
	seenPub := map[string]string{}

	langs := starterLangs(t)
	for _, lang := range langs {
		for i := 0; i < 2; i++ {
			t.Run(lang, func(t *testing.T) {
				projectDir, printed := instantiate(t, lang)
				envPath := filepath.Join(projectDir, ".env")

				env := parseDotenv(t, envPath)
				priv, ok := env["PROVIDER_PRIVATE_KEY"]
				if !ok {
					t.Fatalf(".env has no PROVIDER_PRIVATE_KEY line:\n%v", env)
				}
				if !privateKeyRe.MatchString(priv) {
					t.Fatalf("PROVIDER_PRIVATE_KEY = %q, want 0x + 64 lowercase hex", priv)
				}

				raw, err := hex.DecodeString(priv[2:])
				if err != nil {
					t.Fatal(err)
				}
				var scalar secp256k1.ModNScalar
				if overflow := scalar.SetByteSlice(raw); overflow {
					t.Fatalf("PROVIDER_PRIVATE_KEY %s is >= the secp256k1 group order", priv)
				}
				if scalar.IsZero() {
					t.Fatalf("PROVIDER_PRIVATE_KEY is zero")
				}
				derivedPub := "0x" + hex.EncodeToString(secp256k1.NewPrivateKey(&scalar).PubKey().SerializeUncompressed())

				printedPub := printedPublicKeyRe.FindString(printed)
				if printedPub == "" {
					t.Fatalf("run() did not print an uncompressed public key:\n%s", printed)
				}
				if printedPub != derivedPub {
					t.Errorf("public key shown to the user %s does not derive from the .env private key (derives %s)", printedPub, derivedPub)
				}

				envRaw, err := os.ReadFile(envPath)
				if err != nil {
					t.Fatal(err)
				}
				switch recorded := printedPublicKeyRe.FindString(string(envRaw)); {
				case recorded == "":
					t.Errorf(".env does not record the public key under PROVIDER_PRIVATE_KEY:\n%s", envRaw)
				case recorded != derivedPub:
					t.Errorf("public key recorded in .env %s does not derive from the private key on the line above (derives %s)", recorded, derivedPub)
				}

				if _, dup := seenPriv[priv]; dup {
					t.Errorf("private key %s repeated across instantiations (first in %s) — not random", priv, seenPriv[priv])
				}
				seenPriv[priv] = lang
				if _, dup := seenPub[derivedPub]; dup {
					t.Errorf("public key %s repeated across instantiations", derivedPub)
				}
				seenPub[derivedPub] = lang

				example := parseDotenv(t, filepath.Join(projectDir, ".env.example"))
				if v := example["PROVIDER_PRIVATE_KEY"]; v != "" {
					t.Errorf(".env.example PROVIDER_PRIVATE_KEY = %q, want empty placeholder", v)
				}

				envNPK, envHas := env["NETWORK_PUBLIC_KEY"]
				exNPK, exHas := example["NETWORK_PUBLIC_KEY"]
				if !envHas {
					t.Fatal("NETWORK_PUBLIC_KEY missing from .env")
				}
				if !exHas {
					t.Fatal("NETWORK_PUBLIC_KEY missing from .env.example")
				}
				if envNPK != exNPK {
					t.Errorf("NETWORK_PUBLIC_KEY in .env = %q, in .env.example = %q — the scaffolder must not rewrite it", envNPK, exNPK)
				}

				if runtime.GOOS != "windows" {
					info, err := os.Stat(envPath)
					if err != nil {
						t.Fatal(err)
					}
					if perm := info.Mode().Perm(); perm != 0o600 {
						t.Errorf(".env mode = %o, want 0600 (it holds the private key)", perm)
					}
				}
			})
		}
	}

	if want := 2 * len(langs); len(seenPriv) != want {
		t.Errorf("%d distinct private keys across %d instantiations", len(seenPriv), want)
	}
}

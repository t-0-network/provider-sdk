// Sync tool: copies templates from their source directories into cli/internal/embed/
// for go:embed. Invoked via go:generate.
//
// Usage: go run ./internal/sync <lang1> [lang2...] [lang=path override...]
//
// Convention: templates live at <lang>/starter/template/ relative to repo root.
// Override with lang=path for non-standard locations.
//
// Special handling:
//   - Filters out build artifacts (node_modules, dist, __pycache__, build, .venv, etc.)
//   - Renames .go/.mod/.sum → .tmpl for Go templates (prevents go:embed compilation)
//   - For Go templates: replaces the literal module path with {{MODULE_PATH}}
//   - Skips .git directories, OS metadata files, and .env / .env.* except .env.example
package main

import (
	"bufio"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

var skipDirs = map[string]bool{
	"node_modules":  true,
	"dist":          true,
	"build":         true,
	"__pycache__":   true,
	".venv":         true,
	".git":          true,
	".gradle":       true,
	".idea":         true,
	".vs":           true,
	".DS_Store":     true,
	"obj":           true,
	"bin":           true,
	".pytest_cache": true,
	".ruff_cache":   true,
}

var skipFiles = map[string]bool{
	".DS_Store": true,
	"Thumbs.db": true,
}

func main() {
	if len(os.Args) < 2 {
		fatalf("usage: sync <lang1> [lang2...] [lang=path ...]")
	}

	repoRoot, err := findRepoRoot()
	if err != nil {
		fatalf("finding repo root: %v", err)
	}

	embedDir := filepath.Join(repoRoot, "cli", "internal", "embed")

	// Parse args: plain "go" uses convention, "python=some/path" overrides
	overrides := map[string]string{}
	var langs []string
	for _, arg := range os.Args[1:] {
		if k, v, ok := strings.Cut(arg, "="); ok {
			overrides[k] = v
			langs = append(langs, k)
		} else {
			langs = append(langs, arg)
		}
	}

	for _, lang := range langs {
		src := lang + "/starter/template"
		if override, ok := overrides[lang]; ok {
			src = override
		}

		srcDir := filepath.Join(repoRoot, src)
		destDir := filepath.Join(embedDir, lang)

		if _, err := os.Stat(srcDir); os.IsNotExist(err) {
			fmt.Printf("skipping %s: %s not found\n", lang, src)
			continue
		}

		fmt.Printf("syncing %s: %s → %s\n", lang, src, destDir)

		os.RemoveAll(destDir)
		if err := os.MkdirAll(destDir, 0777); err != nil {
			fatalf("creating %s: %v", destDir, err)
		}

		if err := copyTree(srcDir, destDir, lang); err != nil {
			fatalf("copying %s: %v", lang, err)
		}
	}

	fmt.Println("done")
}

// baseLang returns the first path component of a lang key: "go/acquirer" → "go",
// "node" → "node". Role-based starters use "lang/role" keys while the Go-specific
// handling (.go→.tmpl rename, module-path replacement) applies to any Go starter.
func baseLang(lang string) string {
	if i := strings.IndexByte(lang, '/'); i >= 0 {
		return lang[:i]
	}
	return lang
}

// goModulePath reads the module directive from go.mod in dir, or "" if absent.
func goModulePath(dir string) string {
	f, err := os.Open(filepath.Join(dir, "go.mod"))
	if err != nil {
		return ""
	}
	defer f.Close()
	s := bufio.NewScanner(f)
	for s.Scan() {
		line := strings.TrimSpace(s.Text())
		if after, ok := strings.CutPrefix(line, "module "); ok {
			return strings.TrimSpace(after)
		}
	}
	return ""
}

func copyTree(srcDir, destDir, lang string) error {
	isGo := baseLang(lang) == "go"
	goModPath := ""
	if isGo {
		goModPath = goModulePath(srcDir)
	}

	return filepath.WalkDir(srcDir, func(src string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		rel, err := filepath.Rel(srcDir, src)
		if err != nil {
			return err
		}
		if rel == "." {
			return nil
		}

		base := filepath.Base(rel)

		if d.IsDir() {
			if skipDirs[base] {
				return filepath.SkipDir
			}
			return os.MkdirAll(filepath.Join(destDir, rel), 0777)
		}

		if skipFiles[base] {
			return nil
		}
		// A developer's local .env (with a real private key) must never become
		// part of the template; only the example ships.
		if base == ".env" || (strings.HasPrefix(base, ".env.") && base != ".env.example") {
			return nil
		}

		destRel := rel
		if isGo {
			switch {
			case strings.HasSuffix(base, ".go"):
				destRel = destRel + ".tmpl"
			case base == "go.mod":
				destRel = destRel + ".tmpl"
			case base == "go.sum":
				destRel = destRel + ".tmpl"
			}
		}

		destPath := filepath.Join(destDir, destRel)
		if err := os.MkdirAll(filepath.Dir(destPath), 0777); err != nil {
			return err
		}

		data, err := os.ReadFile(src)
		if err != nil {
			return fmt.Errorf("reading %s: %w", src, err)
		}

		if isGo && goModPath != "" && isTextFile(base) {
			content := string(data)
			content = strings.ReplaceAll(content, goModPath, "{{MODULE_PATH}}")
			data = []byte(content)
		}

		info, err := d.Info()
		if err != nil {
			return err
		}
		mode := info.Mode().Perm()
		if mode&0111 == 0 {
			mode = 0666
		}

		return os.WriteFile(destPath, data, mode)
	})
}

func isTextFile(name string) bool {
	textExts := map[string]bool{
		".go": true, ".mod": true, ".sum": true, ".tmpl": true,
		".java": true, ".kt": true, ".kts": true, ".gradle": true,
		".ts": true, ".js": true, ".json": true, ".mjs": true, ".cjs": true,
		".py": true, ".toml": true, ".cfg": true, ".ini": true,
		".yaml": true, ".yml": true, ".xml": true, ".properties": true,
		".md": true, ".txt": true, ".rst": true,
		".sh": true, ".bat": true, ".ps1": true, ".cmd": true,
		".cs": true, ".csproj": true, ".sln": true, ".slnx": true,
		".env": true, ".example": true, ".template": true,
		".html": true, ".css": true, ".scss": true,
	}
	ext := strings.ToLower(filepath.Ext(name))
	if textExts[ext] {
		return true
	}
	lower := strings.ToLower(name)
	return lower == "dockerfile" || lower == "gradlew" || lower == "dot-gitignore" || lower == "dot-dockerignore" || lower == "makefile"
}

func findRepoRoot() (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", err
	}
	for {
		if _, err := os.Stat(filepath.Join(dir, ".git")); err == nil {
			return dir, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", fmt.Errorf("no .git found above %s", dir)
		}
		dir = parent
	}
}

func fatalf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "sync: "+format+"\n", args...)
	os.Exit(1)
}

// Sync tool: copies templates from their source directories into cli/internal/embed/
// for go:embed. Invoked via go:generate.
//
// Reads templates.yaml from the cli/ directory (repo root / cli/).
// Each entry maps an ecosystem name to a source path relative to the repo root.
//
// Special handling:
//   - Filters out build artifacts (node_modules, dist, __pycache__, build, .venv, etc.)
//   - Renames go.mod → go.mod.tmpl (prevents nested module exclusion by go:embed)
//   - For Go templates: replaces the literal module path with {{MODULE_PATH}}
//   - Skips .git directories and OS metadata files
package main

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

// Go template module path that gets replaced with {{MODULE_PATH}} placeholder
const goTemplateModulePath = "github.com/t-0-network/provider-sdk/go/starter/template"

var skipDirs = map[string]bool{
	"node_modules": true,
	"dist":         true,
	"__pycache__":  true,
	".venv":        true,
	".git":         true,
	".idea":        true,
	".vs":          true,
	".DS_Store":    true,
	"obj":          true,
	"bin":          true,
	".pytest_cache": true,
	".ruff_cache":  true,
}

var skipFiles = map[string]bool{
	".DS_Store":  true,
	"Thumbs.db":  true,
}

// Manifest entry: ecosystem → source path relative to repo root
type manifestEntry struct {
	lang string
	src  string
}

func main() {
	// Find repo root: walk up from cli/internal/sync/ to find .git
	repoRoot, err := findRepoRoot()
	if err != nil {
		fatalf("finding repo root: %v", err)
	}

	cliDir := filepath.Join(repoRoot, "cli")
	manifestPath := filepath.Join(cliDir, "templates.yaml")
	embedDir := filepath.Join(cliDir, "internal", "embed")

	entries, err := parseManifest(manifestPath)
	if err != nil {
		fatalf("parsing manifest: %v", err)
	}

	for _, entry := range entries {
		srcDir := filepath.Join(repoRoot, entry.src)
		destDir := filepath.Join(embedDir, entry.lang)

		fmt.Printf("syncing %s: %s → %s\n", entry.lang, entry.src, destDir)

		// Clean destination
		os.RemoveAll(destDir)
		if err := os.MkdirAll(destDir, 0777); err != nil {
			fatalf("creating %s: %v", destDir, err)
		}

		if err := copyTree(srcDir, destDir, entry.lang); err != nil {
			fatalf("copying %s: %v", entry.lang, err)
		}
	}

	fmt.Println("done")
}

func copyTree(srcDir, destDir, lang string) error {
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

		// Destination filename transforms
		destRel := rel
		if lang == "go" {
			// Rename Go source files to .tmpl so the Go toolchain doesn't
			// try to compile template code inside internal/embed/go/.
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

		// Read source
		data, err := os.ReadFile(src)
		if err != nil {
			return fmt.Errorf("reading %s: %w", src, err)
		}

		// For Go templates: inject {{MODULE_PATH}} placeholder
		if lang == "go" && isTextFile(base) {
			content := string(data)
			content = strings.ReplaceAll(content, goTemplateModulePath, "{{MODULE_PATH}}")
			data = []byte(content)
		}

		// Preserve executable bit
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
	// Known text files without extensions
	lower := strings.ToLower(name)
	return lower == "dockerfile" || lower == "gradlew" || lower == "dot-gitignore" || lower == "makefile"
}

func parseManifest(path string) ([]manifestEntry, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("reading %s: %w", path, err)
	}

	var entries []manifestEntry
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		// Simple "key: value" YAML parsing (no external dep)
		parts := strings.SplitN(line, ":", 2)
		if len(parts) != 2 {
			continue
		}
		lang := strings.TrimSpace(parts[0])
		src := strings.TrimSpace(parts[1])
		if lang != "" && src != "" {
			entries = append(entries, manifestEntry{lang: lang, src: src})
		}
	}

	if len(entries) == 0 {
		return nil, fmt.Errorf("no entries found in %s", path)
	}
	return entries, nil
}

func findRepoRoot() (string, error) {
	// Start from the directory of this tool and walk up
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

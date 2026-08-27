package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestCopyTreeSkipsDirs(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	writeFile(t, filepath.Join(src, "normal.txt"), "hello")
	mkdirAll(t, filepath.Join(src, "build"))
	writeFile(t, filepath.Join(src, "build", "output.jar"), "jar")
	mkdirAll(t, filepath.Join(src, ".gradle"))
	writeFile(t, filepath.Join(src, ".gradle", "cache.bin"), "cache")
	mkdirAll(t, filepath.Join(src, "node_modules"))
	writeFile(t, filepath.Join(src, "node_modules", "pkg.js"), "module")
	writeFile(t, filepath.Join(src, ".DS_Store"), "store")

	if err := copyTree(src, dest, "java"); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	assertExists(t, filepath.Join(dest, "normal.txt"))
	assertNotExists(t, filepath.Join(dest, "build"))
	assertNotExists(t, filepath.Join(dest, ".gradle"))
	assertNotExists(t, filepath.Join(dest, "node_modules"))
	assertNotExists(t, filepath.Join(dest, ".DS_Store"))
}

func TestCopyTreeGoTmplRenaming(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	writeFile(t, filepath.Join(src, "main.go"), "package main")
	writeFile(t, filepath.Join(src, "go.mod"), "module example")
	writeFile(t, filepath.Join(src, "go.sum"), "h1:abc")
	writeFile(t, filepath.Join(src, "README.md"), "readme")

	if err := copyTree(src, dest, "go"); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	assertExists(t, filepath.Join(dest, "main.go.tmpl"))
	assertNotExists(t, filepath.Join(dest, "main.go"))
	assertExists(t, filepath.Join(dest, "go.mod.tmpl"))
	assertNotExists(t, filepath.Join(dest, "go.mod"))
	assertExists(t, filepath.Join(dest, "go.sum.tmpl"))
	assertNotExists(t, filepath.Join(dest, "go.sum"))
	assertExists(t, filepath.Join(dest, "README.md"))
}

func TestCopyTreeGoModulePathReplacement(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	content := `package main

import "github.com/t-0-network/provider-sdk/go/starter/template/pkg"
`
	writeFile(t, filepath.Join(src, "main.go"), content)

	if err := copyTree(src, dest, "go"); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	data, err := os.ReadFile(filepath.Join(dest, "main.go.tmpl"))
	if err != nil {
		t.Fatalf("reading output: %v", err)
	}

	got := string(data)
	if expected := `import "{{MODULE_PATH}}/pkg"`; !contains(got, expected) {
		t.Errorf("expected module path replacement\ngot: %s", got)
	}
	if contains(got, goTemplateModulePath) {
		t.Error("original module path should have been replaced")
	}
}

func TestCopyTreeNonGoSkipsRenaming(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	writeFile(t, filepath.Join(src, "main.go"), "package main")

	if err := copyTree(src, dest, "node"); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	assertExists(t, filepath.Join(dest, "main.go"))
	assertNotExists(t, filepath.Join(dest, "main.go.tmpl"))
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0777); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0666); err != nil {
		t.Fatal(err)
	}
}

func mkdirAll(t *testing.T, path string) {
	t.Helper()
	if err := os.MkdirAll(path, 0777); err != nil {
		t.Fatal(err)
	}
}

func assertExists(t *testing.T, path string) {
	t.Helper()
	if _, err := os.Stat(path); os.IsNotExist(err) {
		t.Errorf("expected %s to exist", filepath.Base(path))
	}
}

func assertNotExists(t *testing.T, path string) {
	t.Helper()
	if _, err := os.Stat(path); err == nil {
		t.Errorf("expected %s to not exist", filepath.Base(path))
	}
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && searchString(s, substr)
}

func searchString(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}

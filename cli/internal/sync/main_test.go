package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
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

	if err := copyTree(src, dest, false); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	assertExists(t, filepath.Join(dest, "normal.txt"))
	if got := readFile(t, filepath.Join(dest, "normal.txt")); got != "hello" {
		t.Errorf("normal.txt = %q, want %q", got, "hello")
	}
	assertNotExists(t, filepath.Join(dest, "build"))
	assertNotExists(t, filepath.Join(dest, ".gradle"))
	assertNotExists(t, filepath.Join(dest, "node_modules"))
	assertNotExists(t, filepath.Join(dest, ".DS_Store"))
}

func TestCopyTreeGoTmplRenaming(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	writeFile(t, filepath.Join(src, "main.go"), "package main")
	writeFile(t, filepath.Join(src, "go.mod"), "module example.com/my-provider")
	writeFile(t, filepath.Join(src, "go.sum"), "h1:abc")
	writeFile(t, filepath.Join(src, "README.md"), "readme")

	if err := copyTree(src, dest, true); err != nil {
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

func TestCopyTreeGoModulePathPreserved(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	const modPath = "example.com/my-provider"
	writeFile(t, filepath.Join(src, "go.mod"), "module "+modPath+"\n\ngo 1.27.0\n")
	writeFile(t, filepath.Join(src, "main.go"), "package main\n\nimport \""+modPath+"/pkg\"\n")

	if err := copyTree(src, dest, true); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	got := readFile(t, filepath.Join(dest, "main.go.tmpl"))
	if !strings.Contains(got, `"`+modPath+`/pkg"`) {
		t.Errorf("expected real module path preserved\ngot: %s", got)
	}

	gomod := readFile(t, filepath.Join(dest, "go.mod.tmpl"))
	if !strings.HasPrefix(gomod, "module "+modPath) {
		t.Errorf("go.mod.tmpl should start with module %s\ngot: %s", modPath, gomod)
	}
}

func TestCopyTreeNonGoSkipsRenaming(t *testing.T) {
	src := t.TempDir()
	dest := t.TempDir()

	writeFile(t, filepath.Join(src, "main.go"), "package main")

	if err := copyTree(src, dest, false); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	assertExists(t, filepath.Join(dest, "main.go"))
	assertNotExists(t, filepath.Join(dest, "main.go.tmpl"))
}

func TestMainRoleKey(t *testing.T) {
	name := "sync"
	if runtime.GOOS == "windows" {
		name += ".exe"
	}
	bin := filepath.Join(t.TempDir(), name)
	out, err := exec.Command("go", "build", "-o", bin, ".").CombinedOutput()
	if err != nil {
		t.Fatalf("go build: %v\n%s", err, out)
	}

	repo := t.TempDir()
	mkdirAll(t, filepath.Join(repo, ".git"))

	const modPath = "example.com/my-product/go/starter/acquirer"
	starterDir := filepath.Join(repo, "go", "starter", "acquirer")
	writeFile(t, filepath.Join(starterDir, "go.mod"), "module "+modPath+"\n\ngo 1.27.0\n")
	writeFile(t, filepath.Join(starterDir, "go.sum"), "h1:abc")
	writeFile(t, filepath.Join(starterDir, "cmd", "main.go"),
		"package main\n\nimport \""+modPath+"/pkg\"\n")

	cmd := exec.Command(bin, "go/acquirer=go/starter/acquirer")
	cmd.Dir = repo
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("sync go/acquirer: %v\n%s", err, out)
	}

	embedDir := filepath.Join(repo, "cli", "internal", "embed", "go", "acquirer")
	mainTmpl := readFile(t, filepath.Join(embedDir, "cmd", "main.go.tmpl"))
	if !strings.Contains(mainTmpl, `"`+modPath+`/pkg"`) {
		t.Errorf("main.go.tmpl should contain the real module path %s/pkg\ngot: %s", modPath, mainTmpl)
	}
	assertExists(t, filepath.Join(embedDir, "go.mod.tmpl"))

	// A Go key without go.mod should fail before wiping the destination.
	brokenDir := filepath.Join(repo, "go", "starter", "broken")
	writeFile(t, filepath.Join(brokenDir, "main.go"), "package main")
	sentinel := filepath.Join(repo, "cli", "internal", "embed", "go", "broken", "sentinel.txt")
	writeFile(t, sentinel, "survive")

	cmd2 := exec.Command(bin, "go/broken=go/starter/broken")
	cmd2.Dir = repo
	if err := cmd2.Run(); err == nil {
		t.Fatal("expected nonzero exit for Go key without go.mod")
	}
	assertExists(t, sentinel)

	// Overlapping keys should be rejected before any syncing.
	cmd3 := exec.Command(bin, "go", "go/acquirer=go/starter/acquirer")
	cmd3.Dir = repo
	if out, err := cmd3.CombinedOutput(); err == nil {
		t.Fatal("expected nonzero exit for overlapping keys")
	} else if !strings.Contains(string(out), "overlapping") {
		t.Errorf("expected overlapping keys error, got: %s", out)
	}

	// Duplicate keys should also be rejected.
	cmd4 := exec.Command(bin, "node", "node")
	cmd4.Dir = repo
	if out, err := cmd4.CombinedOutput(); err == nil {
		t.Fatal("expected nonzero exit for duplicate keys")
	} else if !strings.Contains(string(out), "overlapping") {
		t.Errorf("expected overlapping keys error for duplicates, got: %s", out)
	}

	// Sibling keys (go/acquirer + go/acquirer-x) must be accepted.
	siblingDir := filepath.Join(repo, "go", "starter", "acquirer-x")
	writeFile(t, filepath.Join(siblingDir, "go.mod"), "module example.com/acquirer-x\n\ngo 1.27.0\n")
	writeFile(t, filepath.Join(siblingDir, "main.go"), "package main\n")
	cmd5 := exec.Command(bin, "go/acquirer=go/starter/acquirer", "go/acquirer-x=go/starter/acquirer-x")
	cmd5.Dir = repo
	if out, err := cmd5.CombinedOutput(); err != nil {
		t.Fatalf("sibling keys should succeed: %v\n%s", err, out)
	}
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
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

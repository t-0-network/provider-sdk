package main

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"testing/fstest"
)

func TestEmbedFS_RejectsBackslashPaths(t *testing.T) {
	// embed.FS requires forward-slash paths. On Windows, filepath.Join
	// produces backslash-separated paths that silently fail to match.
	// This test verifies the bug exists at the embed.FS level so the
	// path.Join fix in scaffold() remains guarded against regression.
	backslashPath := "internal\\embed\\go"
	_, err := embeddedTemplates.ReadDir(backslashPath)
	if err == nil {
		t.Fatalf("ReadDir(%q) succeeded; embed.FS should reject backslash paths", backslashPath)
	}
}

func TestEmbedFS_ForwardSlashWorks(t *testing.T) {
	for _, lang := range Config.Languages {
		t.Run(lang, func(t *testing.T) {
			dir := "internal/embed/" + lang
			entries, err := embeddedTemplates.ReadDir(dir)
			if err != nil {
				t.Fatalf("ReadDir(%q): %v", dir, err)
			}
			if len(entries) == 0 {
				t.Errorf("ReadDir(%q) returned 0 entries", dir)
			}
		})
	}
}

func TestScaffold_AllLanguages(t *testing.T) {
	for _, lang := range Config.Languages {
		t.Run(lang, func(t *testing.T) {
			dir := t.TempDir()
			projectDir := filepath.Join(dir, "test-project")

			opts := ScaffoldOpts{
				Lang:        lang,
				ProjectName: "test-project",
				ProjectDir:  projectDir,
				Version:     "dev",
			}
			if lang == "go" {
				opts.ModulePath = "github.com/test/test-project"
			}

			if err := os.MkdirAll(projectDir, 0777); err != nil {
				t.Fatal(err)
			}
			if err := scaffold(opts); err != nil {
				t.Fatalf("scaffold(%s): %v", lang, err)
			}

			// .gitignore must exist (dot-gitignore renamed)
			if _, err := os.Stat(filepath.Join(projectDir, ".gitignore")); err != nil {
				t.Errorf(".gitignore missing in %s scaffold", lang)
			}
			// dot-gitignore must NOT exist
			if _, err := os.Stat(filepath.Join(projectDir, "dot-gitignore")); err == nil {
				t.Errorf("dot-gitignore still present in %s scaffold", lang)
			}

			// .dockerignore must exist for languages with Dockerfile-based workflows
			if lang == "java" || lang == "csharp" || lang == "python" {
				if _, err := os.Stat(filepath.Join(projectDir, ".dockerignore")); err != nil {
					t.Errorf(".dockerignore missing in %s scaffold", lang)
				}
				if _, err := os.Stat(filepath.Join(projectDir, "dot-dockerignore")); err == nil {
					t.Errorf("dot-dockerignore still present in %s scaffold", lang)
				}
			}
		})
	}
}

func TestToPascalCase_EdgeCases(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"my-provider", "MyProvider"},
		{"3rd-provider", "3rdProvider"},
		{"---", ""},
		{"hello", "Hello"},
		{"a-b-c", "ABC"},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got := toPascalCase(tt.input)
			if got != tt.want {
				t.Errorf("toPascalCase(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}

func TestSanitizeProjectName(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"My Provider", "my-provider"},
		{"hello_world", "hello_world"},
		{"test@123!", "test123"},
		{"---", "---"},
		{"  ", ""},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got := sanitizeProjectName(tt.input)
			if got != tt.want {
				t.Errorf("sanitizeProjectName(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}

// scaffoldInto runs the real template scaffold for lang/role into a fresh
// temp project dir and returns the dir.
func scaffoldInto(t *testing.T, lang, role string) (string, ScaffoldOpts) {
	t.Helper()
	projectDir := filepath.Join(t.TempDir(), "test-project")
	opts := ScaffoldOpts{
		Lang:        lang,
		Role:        role,
		ProjectName: "test-project",
		ProjectDir:  projectDir,
		Version:     "dev",
	}
	if lang == "go" {
		opts.ModulePath = "github.com/test/test-project"
	}
	if err := os.MkdirAll(projectDir, 0777); err != nil {
		t.Fatal(err)
	}
	if err := scaffold(opts); err != nil {
		t.Fatalf("scaffold(%s): %v", lang, err)
	}
	return projectDir, opts
}

func readFile(t *testing.T, p string) string {
	t.Helper()
	data, err := os.ReadFile(p)
	if err != nil {
		t.Fatalf("reading %s: %v", p, err)
	}
	return string(data)
}

func TestApplyOverlay(t *testing.T) {
	overlay := fstest.MapFS{
		"overlay/node/Dockerfile":         {Data: []byte("FROM overlay\n")},
		"overlay/node/docker/extra.txt":   {Data: []byte("nested\n")},
		"overlay/node/run.sh":             {Data: []byte("#!/bin/sh\n")},
		"overlay/java/acquirer/README.md": {Data: []byte("# acquirer overlay\n")},
	}

	t.Run("overwrites and adds files, leaves others untouched", func(t *testing.T) {
		projectDir, opts := scaffoldInto(t, "node", "")
		before := readFile(t, filepath.Join(projectDir, "package.json"))

		if err := applyOverlay(overlay, opts); err != nil {
			t.Fatalf("applyOverlay: %v", err)
		}

		if got := readFile(t, filepath.Join(projectDir, "Dockerfile")); got != "FROM overlay\n" {
			t.Errorf("Dockerfile = %q, want overlay content", got)
		}
		if got := readFile(t, filepath.Join(projectDir, "docker", "extra.txt")); got != "nested\n" {
			t.Errorf("docker/extra.txt = %q, want nested overlay content", got)
		}
		if after := readFile(t, filepath.Join(projectDir, "package.json")); after != before {
			t.Error("package.json changed although it is not in the overlay")
		}
		info, err := os.Stat(filepath.Join(projectDir, "run.sh"))
		if err != nil {
			t.Fatal(err)
		}
		// Windows has no exec bit; os.Stat reports 0666 regardless.
		if runtime.GOOS != "windows" && info.Mode().Perm()&0111 == 0 {
			t.Errorf("run.sh mode = %v, want executable", info.Mode())
		}
	})

	t.Run("role lookup uses overlay/<lang>/<role>", func(t *testing.T) {
		projectDir, opts := scaffoldInto(t, "java", "")
		opts.Role = "acquirer" // scaffold has no role dirs here; only the overlay path is role-scoped
		if err := applyOverlay(overlay, opts); err != nil {
			t.Fatalf("applyOverlay: %v", err)
		}
		if got := readFile(t, filepath.Join(projectDir, "README.md")); got != "# acquirer overlay\n" {
			t.Errorf("README.md = %q, want role overlay content", got)
		}
	})

	t.Run("missing overlay root is a no-op", func(t *testing.T) {
		projectDir, opts := scaffoldInto(t, "python", "")
		before := readFile(t, filepath.Join(projectDir, "Dockerfile"))
		if err := applyOverlay(overlay, opts); err != nil {
			t.Fatalf("applyOverlay with no overlay for python: %v", err)
		}
		if after := readFile(t, filepath.Join(projectDir, "Dockerfile")); after != before {
			t.Error("Dockerfile changed although python has no overlay")
		}
	})

	t.Run("empty overlay FS is a no-op", func(t *testing.T) {
		_, opts := scaffoldInto(t, "node", "")
		if err := applyOverlay(fstest.MapFS{}, opts); err != nil {
			t.Fatalf("applyOverlay on empty FS: %v", err)
		}
	})
}

// The run() tests below mutate the package-level Config. No test in this
// package uses t.Parallel(), so restoring via t.Cleanup is sufficient.

func TestRun_AppliesOverlay(t *testing.T) {
	prev := Config.OverlayFS
	Config.OverlayFS = fstest.MapFS{
		"overlay/node/Dockerfile": {Data: []byte("FROM run-overlay\n")},
	}
	t.Cleanup(func() { Config.OverlayFS = prev })

	projectDir := filepath.Join(t.TempDir(), "test-project")
	err := run(ScaffoldOpts{
		Lang:        "node",
		ProjectName: "test-project",
		ProjectDir:  projectDir,
		Version:     "dev",
	})
	if err != nil {
		t.Fatalf("run: %v", err)
	}
	if got := readFile(t, filepath.Join(projectDir, "Dockerfile")); got != "FROM run-overlay\n" {
		t.Errorf("Dockerfile = %q — run() did not apply Config.OverlayFS", got)
	}
}

func TestRun_PostScaffoldErrorKeepsExistingDir(t *testing.T) {
	prev := Config.PostScaffold
	Config.PostScaffold = func(ScaffoldOpts) error { return errors.New("boom") }
	t.Cleanup(func() { Config.PostScaffold = prev })

	// Pre-existing (empty) directory owned by the user, as with --dir.
	projectDir := t.TempDir()
	err := run(ScaffoldOpts{
		Lang:        "node",
		ProjectName: "test-project",
		ProjectDir:  projectDir,
		Version:     "dev",
	})
	if err == nil {
		t.Fatal("run succeeded; want post-scaffold error")
	}
	if _, statErr := os.Stat(projectDir); statErr != nil {
		t.Fatalf("pre-existing project dir was removed on hook error: %v", statErr)
	}
}

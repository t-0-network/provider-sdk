// Command sumtool emits a file:// GOPROXY layout for the Go SDK module at a
// version that has no git tag yet, so the starter template's go.sum can be
// generated in the same release commit that bumps its require line.
//
// A module's h1: hash is a function of its source tree, not of the tag, so the
// checksums recorded against this layout are exactly the ones proxy.golang.org
// serves once publish.yaml pushes go/vX.Y.Z.
//
// Usage: sumtool <module-dir> <vX.Y.Z> <proxy-dir>
package main

import (
	"fmt"
	"log"
	"os"
	"path/filepath"

	"golang.org/x/mod/module"
	"golang.org/x/mod/zip"
)

const modPath = "github.com/t-0-network/provider-sdk/go"

func main() {
	if len(os.Args) != 4 {
		log.Fatalf("usage: %s <module-dir> <vX.Y.Z> <proxy-dir>", os.Args[0])
	}
	srcDir, version, proxyDir := os.Args[1], os.Args[2], os.Args[3]

	// cmd/go synthesizes the repo-root LICENSE into the zip when the module
	// directory has none; x/mod/zip does not. Without go/LICENSE the locally
	// computed hash would silently diverge from the proxy's.
	if _, err := os.Stat(filepath.Join(srcDir, "LICENSE")); err != nil {
		log.Fatalf("%s/LICENSE is missing: the computed hash would not match proxy.golang.org", srcDir)
	}

	dir := filepath.Join(proxyDir, filepath.FromSlash(modPath), "@v")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		log.Fatal(err)
	}

	goMod, err := os.ReadFile(filepath.Join(srcDir, "go.mod"))
	if err != nil {
		log.Fatal(err)
	}
	write := func(name string, data []byte) {
		if err := os.WriteFile(filepath.Join(dir, name), data, 0o644); err != nil {
			log.Fatal(err)
		}
	}
	write("list", []byte(version+"\n"))
	write(version+".info", []byte(fmt.Sprintf("{%q:%q}\n", "Version", version)))
	write(version+".mod", goMod)

	f, err := os.Create(filepath.Join(dir, version+".zip"))
	if err != nil {
		log.Fatal(err)
	}
	if err := zip.CreateFromDir(f, module.Version{Path: modPath, Version: version}, srcDir); err != nil {
		log.Fatal(err)
	}
	if err := f.Close(); err != nil {
		log.Fatal(err)
	}
	fmt.Printf("wrote %s@%s to %s\n", modPath, version, proxyDir)
}

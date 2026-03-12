// Copyright 2023 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// Gonew starts a new Go module by copying a template module.
//
// Usage:
//
//	go run github.com/t-0-network/provider-sdk/go/starter@latest you_package_name
package main

import (
	"bytes"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"go/parser"
	"go/token"
	"io/fs"
	"log"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"

	"github.com/ethereum/go-ethereum/crypto"
	"github.com/joho/godotenv"
	"github.com/t-0-network/provider-sdk/go/starter/internal"
	"github.com/t-0-network/provider-sdk/go/starter/internal/edit"
	"golang.org/x/mod/modfile"
)

func usage() {
	fmt.Fprintf(os.Stderr, "usage: go run github.com/t-0-network/provider-sdk/go/starter@latest dstmod\n")
	os.Exit(2)
}

func main() {
	log.SetPrefix("new: ")
	log.SetFlags(0)
	flag.Usage = usage
	flag.Parse()
	args := flag.Args()

	if len(args) < 1 || len(args) > 2 {
		usage()
	}

	dstMod := args[0]
	dir := "." + string(filepath.Separator) + path.Base(dstMod)

	// Dir must not exist or must be an empty directory.
	de, err := os.ReadDir(dir)
	if err == nil && len(de) > 0 {
		log.Fatalf("target directory %s exists and is non-empty", dir)
	}
	needMkdir := err != nil

	srcMod, _ := strings.CutSuffix(reflect.TypeOf(internal.Dummy{}).PkgPath(), "/internal")
	srcMod += "/template"
	srcModVers := srcMod + "@latest"
	var stdout, stderr bytes.Buffer
	cmd := exec.Command("go", "mod", "download", "-json", srcModVers)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		log.Fatalf("go mod download -json %s: %v\n%s%s", srcModVers, err, stderr.Bytes(), stdout.Bytes())
	}

	var info struct {
		Dir string
	}
	if err := json.Unmarshal(stdout.Bytes(), &info); err != nil {
		log.Fatalf("go mod download -json %s: invalid JSON output: %v\n%s%s", srcMod, err, stderr.Bytes(), stdout.Bytes())
	}

	if needMkdir {
		if err := os.MkdirAll(dir, 0777); err != nil {
			log.Fatal(err)
		}
	}

	// Copy from module cache into new directory, making edits as needed.
	filepath.WalkDir(info.Dir, func(src string, d fs.DirEntry, err error) error {
		if err != nil {
			log.Fatal(err)
		}
		rel, err := filepath.Rel(info.Dir, src)
		if err != nil {
			log.Fatal(err)
		}
		dst := filepath.Join(dir, rel)
		if d.IsDir() {
			if err := os.MkdirAll(dst, 0777); err != nil {
				log.Fatal(err)
			}
			return nil
		}

		data, err := os.ReadFile(src)
		if err != nil {
			log.Fatal(err)
		}

		isRoot := !strings.Contains(rel, string(filepath.Separator))
		if strings.HasSuffix(rel, ".go") {
			data = fixGo(data, rel, srcMod, dstMod, isRoot)
		}
		if rel == "go.mod" {
			data = fixGoMod(data, dstMod)
		}

		if err := os.WriteFile(dst, data, 0666); err != nil {
			log.Fatal(err)
		}
		return nil
	})

	// Ensure go.sum is in sync with go.mod (the template's go.sum may reference
	// a different SDK version than go.mod if the release workflow updated go.mod
	// but couldn't run go mod tidy because the new version wasn't published yet).
	tidy := exec.Command("go", "mod", "tidy")
	tidy.Dir = dir
	tidy.Stdout = os.Stdout
	tidy.Stderr = os.Stderr
	if err := tidy.Run(); err != nil {
		log.Fatalf("go mod tidy: %v", err)
	}

	// TODO: extract into function
	key, err := crypto.GenerateKey()
	if err != nil {
		log.Fatal(err)
	}
	values, err := godotenv.Read(path.Join(dir, ".env.example"))
	if err != nil {
		log.Fatal(err)
	}

	values["PROVIDER_PRIVATE_KEY"] = fmt.Sprintf("0x%s", hex.EncodeToString(crypto.FromECDSA(key)))
	values["PROVIDER_PUBLIC_KEY"] = fmt.Sprintf("0x%s", hex.EncodeToString(crypto.FromECDSAPub(&key.PublicKey)))
	err = godotenv.Write(values, path.Join(dir, ".env"))
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("initialized %s in %s", dstMod, dir)
}

// fixGo rewrites the Go source in data to replace srcMod with dstMod.
// isRoot indicates whether the file is in the root directory of the module,
// in which case we also update the package name.
func fixGo(data []byte, file string, srcMod, dstMod string, isRoot bool) []byte {
	fset := token.NewFileSet()
	f, err := parser.ParseFile(fset, file, data, parser.ImportsOnly)
	if err != nil {
		log.Fatalf("parsing source module:\n%s", err)
	}

	buf := edit.NewBuffer(data)
	at := func(p token.Pos) int {
		return fset.File(p).Offset(p)
	}

	srcName := path.Base(srcMod)
	dstName := path.Base(dstMod)
	if isRoot {
		if name := f.Name.Name; name == srcName || name == srcName+"_test" {
			dname := dstName + strings.TrimPrefix(name, srcName)
			if !token.IsIdentifier(dname) {
				log.Fatalf("%s: cannot rename package %s to package %s: invalid package name", file, name, dname)
			}
			buf.Replace(at(f.Name.Pos()), at(f.Name.End()), dname)
		}
	}

	for _, spec := range f.Imports {
		path, err := strconv.Unquote(spec.Path.Value)
		if err != nil {
			continue
		}
		if path == srcMod {
			if srcName != dstName && spec.Name == nil {
				// Add package rename because source code uses original name.
				// The renaming looks strange, but template authors are unlikely to
				// create a template where the root package is imported by packages
				// in subdirectories, and the renaming at least keeps the code working.
				// A more sophisticated approach would be to rename the uses of
				// the package identifier in the file too, but then you have to worry about
				// name collisions, and given how unlikely this is, it doesn't seem worth
				// trying to clean up the file that way.
				buf.Insert(at(spec.Path.Pos()), srcName+" ")
			}
			// Change import path to dstMod
			buf.Replace(at(spec.Path.Pos()), at(spec.Path.End()), strconv.Quote(dstMod))
		}
		if strings.HasPrefix(path, srcMod+"/") {
			// Change import path to begin with dstMod
			buf.Replace(at(spec.Path.Pos()), at(spec.Path.End()), strconv.Quote(strings.Replace(path, srcMod, dstMod, 1)))
		}
	}
	return buf.Bytes()
}

// fixGoMod rewrites the go.mod content in data to add a module
// statement for dstMod.
func fixGoMod(data []byte, dstMod string) []byte {
	f, err := modfile.ParseLax("go.mod", data, nil)
	if err != nil {
		log.Fatalf("parsing source module:\n%s", err)
	}
	f.AddModuleStmt(dstMod)
	new, err := f.Format()
	if err != nil {
		return data
	}
	return new
}

package provider_sdk_go

import (
	"bytes"
	"os/exec"
	"strings"
	"testing"

	"github.com/stretchr/testify/require"
)

// No library package may link the generated code in api/: its init registers the proto files, and a
// binary that also links a caller's own stubs of those files panics ("already registered").
// kycsharing is the exception: its functions take the generated types.
func TestLibraryPackagesDoNotLinkGeneratedCode(t *testing.T) {
	const module = "github.com/t-0-network/provider-sdk/go"
	var stdout, stderr bytes.Buffer
	cmd := exec.Command("go", "list", "-f", `{{.ImportPath}} {{join .Deps " "}}`, "./...")
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	require.NoError(t, cmd.Run(), stderr.String())

	var links []string
	for _, line := range strings.Split(strings.TrimSpace(stdout.String()), "\n") {
		pkg, deps, _ := strings.Cut(line, " ")
		if strings.HasPrefix(pkg, module+"/api/") || pkg == module+"/kycsharing" {
			continue
		}
		for _, dep := range strings.Fields(deps) {
			if strings.HasPrefix(dep, module+"/api/") {
				links = append(links, pkg+" -> "+dep)
			}
		}
	}
	require.Empty(t, links)
}

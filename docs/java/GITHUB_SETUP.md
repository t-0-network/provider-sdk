# GitHub Repository Setup Guide

This guide covers the complete setup required for CI/CD, including Maven Central publishing and JitPack integration.

## Overview

The SDK uses two publishing channels:

| Channel | Purpose | Trigger |
|---------|---------|---------|
| **Maven Central** | Production releases (can be slow to propagate) | Git tag push |
| **JitPack** | Default — fast, builds on demand from GitHub | Automatic on-demand |

## Required GitHub Secrets

Navigate to: **Repository → Settings → Secrets and variables → Actions → Secrets**

### Maven Central Secrets

| Secret | Description | How to Obtain |
|--------|-------------|---------------|
| `OSSRH_PASSWORD` | Password of a Maven Central (Central Portal) user token | See [Maven Central Setup](#maven-central-setup) below |
| `GPG_PRIVATE_KEY` | Full armored GPG private key | See [GPG Key Generation](#gpg-key-generation) below |

### Release Workflow Secrets

| Secret | Description | How to Obtain |
|--------|-------------|---------------|
| `CI_APP_PRIVATE_KEY` | GitHub App private key (PEM format) | See [GitHub App Setup](#github-app-setup) below |

## Required GitHub Variables

Navigate to: **Repository → Settings → Secrets and variables → Actions → Variables**

| Variable | Description | Example |
|----------|-------------|---------|
| `CI_APP_CLIENT_ID` | Client ID of the GitHub App | `Iv23li…` |
| `OSSRH_USERNAME` | Username of the Maven Central user token | See [Maven Central Setup](#maven-central-setup) below |

The `OSSRH_` names date from Sonatype's old OSSRH service; the values are a Central Portal user token. Every secret and variable the workflows use, and which job uses it: [`../RELEASE_AND_PUBLISH.md`](../RELEASE_AND_PUBLISH.md#secrets-variables-and-environments).

## GPG Key Generation

Maven Central requires all artifacts to be GPG signed. Generate a dedicated signing key for CI:

```bash
# Generate a new GPG key without passphrase (for CI automation)
gpg --batch --gen-key <<EOF
Key-Type: RSA
Key-Length: 4096
Name-Real: T-0 Release Signing
Name-Email: release@t-0.network
Expire-Date: 0
%no-protection
EOF

# List keys to find the key ID
gpg --list-secret-keys --keyid-format LONG
# Output example:
# sec   rsa4096/ABCD1234EFGH5678 2024-01-01 [SC]
#       Full fingerprint here
# The KEY_ID is: ABCD1234EFGH5678

# Export the private key for GitHub Secrets
gpg --armor --export-secret-keys ABCD1234EFGH5678
# Copy the ENTIRE output including:
# -----BEGIN PGP PRIVATE KEY BLOCK-----
# ... (key content)
# -----END PGP PRIVATE KEY BLOCK-----
```

### Upload Public Key to Keyservers

Maven Central verifies signatures against public keyservers:

```bash
# Upload to multiple keyservers for redundancy
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EFGH5678
gpg --keyserver keys.openpgp.org --send-keys ABCD1234EFGH5678

# Verify upload (may take a few minutes to propagate)
gpg --keyserver keyserver.ubuntu.com --recv-keys ABCD1234EFGH5678
```

### Security Notes

- Store the private key securely; it's needed for all releases
- Key without passphrase is acceptable for CI since it's stored as a GitHub secret
- Consider key rotation annually or after team changes
- Back up the key securely; losing it requires generating a new one

## GitHub App Setup

The release workflow uses a GitHub App to bypass branch protection rules and trigger downstream workflows.

### Why a GitHub App?

- `GITHUB_TOKEN` cannot trigger other workflows (e.g., tag push → publish workflow)
- Personal Access Tokens are tied to individual accounts
- GitHub Apps provide organization-level automation

### Create the GitHub App

1. Go to **Organization Settings → Developer settings → GitHub Apps → New GitHub App**
   (Or for personal repos: **Settings → Developer settings → GitHub Apps**)

2. Configure the app:
   - **Name**: `T-0 SDK Release Bot` (or similar)
   - **Homepage URL**: Your repository URL
   - **Webhook**: Uncheck "Active" (not needed)

3. Set permissions:
   - **Repository permissions**:
     - Contents: Read & Write
     - Metadata: Read-only
   - **No organization permissions needed**

4. Create the app and note the **Client ID** (shown on the app's settings page)

5. Generate a private key:
   - Scroll to "Private keys" section
   - Click "Generate a private key"
   - Save the downloaded `.pem` file securely

6. Install the app:
   - Go to the app's settings → Install App
   - Install on your organization/account
   - Select the repository

### Add to GitHub Secrets/Variables

1. **Secret**: `CI_APP_PRIVATE_KEY`
   - Copy the entire contents of the `.pem` file
   - Include `-----BEGIN RSA PRIVATE KEY-----` and `-----END RSA PRIVATE KEY-----`

2. **Variable**: `CI_APP_CLIENT_ID`
   - The Client ID from the app's settings page

## Maven Central Setup

The SDK is published through the Sonatype Central Portal: `./gradlew publishAggregationToCentralPortal` (the NMCP plugin, configured in `java/build.gradle.kts`) uploads it with a user token.

### First-Time Setup

1. **Sign in** at https://central.sonatype.com.
2. **Verify the namespace** `network.t-0`, the SDK's Maven group (`group` in `java/gradle.properties`), following the Portal's instructions.
3. **Generate a user token**: Account → Generate User Token. Store its username as the variable `OSSRH_USERNAME` and its password as the secret `OSSRH_PASSWORD`.

### Verify Setup

After a release, the upload appears under Publishing → Deployments at https://central.sonatype.com.

## JitPack Setup

JitPack requires **no GitHub secrets or configuration**. It works automatically:

1. JitPack reads `jitpack.yml` from the repository root
2. When a user requests a dependency, JitPack:
   - Clones the repository
   - Runs the build commands from `jitpack.yml`
   - Caches and serves the artifacts

### JitPack Configuration

The `jitpack.yml` file in this repository:

```yaml
jdk:
  - openjdk17

before_install:
  - chmod +x gradlew

install:
  - ./gradlew :sdk:publishToMavenLocal --no-daemon

env:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"
```

> **Note:** Only the SDK is published via JitPack. The CLI is distributed as a GitHub Release asset.

### Verify JitPack

1. Push a tag or commit to GitHub
2. Visit https://jitpack.io/#t-0-network/provider-sdk
3. Click "Get it" on your version to trigger a build
4. Check the build log for any issues

### JitPack Artifact Coordinates

| Module | JitPack Coordinates |
|--------|---------------------|
| SDK | `com.github.t-0-network:provider-sdk:TAG` |

Where `TAG` can be:
- Release version: `1.0.33` (the bare version; the git tag is `v1.0.33`)
- Branch: `master-SNAPSHOT`
- Commit hash: `abc1234`

> **Note:** The CLI is not published to JitPack. It is distributed as a GitHub Release asset.

## Complete Setup Checklist

### One-Time Setup

- [ ] Sign in to the Central Portal and verify the `network.t-0` namespace
- [ ] Generate a Central Portal user token
- [ ] Generate GPG key (4096-bit RSA, no passphrase)
- [ ] Upload GPG public key to keyservers
- [ ] Create GitHub App for releases
- [ ] Install GitHub App on repository

### GitHub Configuration

- [ ] Add variable: `OSSRH_USERNAME`
- [ ] Add secret: `OSSRH_PASSWORD`
- [ ] Add secret: `GPG_PRIVATE_KEY`
- [ ] Add secret: `CI_APP_PRIVATE_KEY`
- [ ] Add variable: `CI_APP_CLIENT_ID`

### Verification

- [ ] After the first release, check the deployment in the Central Portal
- [ ] Verify JitPack can build: https://jitpack.io/#t-0-network/provider-sdk
- [ ] Test CLI generates projects with both repository options

## Workflow Reference

| Workflow | File | Trigger | Purpose |
|----------|------|---------|---------|
| CI | `.github/workflows/ci-{go,node,python,java,csharp,cli}.yaml` | Push to master, PRs (path-filtered) | Build and test per ecosystem |
| Release | `.github/workflows/release.yaml` | Manual dispatch | Create release, tag, update versions |
| Publish | `.github/workflows/publish.yaml` | Tag push | Publish every SDK (Maven Central, npm, PyPI, NuGet, the Go module tag) and upload the CLI to the GitHub Release; see [`../RELEASE_AND_PUBLISH.md`](../RELEASE_AND_PUBLISH.md) |

### Release Process Flow

```
1. Manual: Run "Release" workflow
   ↓
2. Automatic: Updates version, creates tag, commits
   ↓
3. Automatic: Tag triggers "Publish" workflow
   ↓
4. Automatic: Artifacts published to Maven Central
   ↓
5. Automatic: JitPack builds on first user request
```

## Troubleshooting

### GPG Signing Fails

```
Could not find signing key
```

- Ensure `GPG_PRIVATE_KEY` contains the full armored key (including BEGIN/END lines)
- Verify the key was generated without a passphrase
- Check no extra whitespace was added when copying

### Maven Central Rejects Artifacts

```
Invalid POM / Missing required elements
```

- Verify POM has: name, description, url, license, developer, scm
- Check `sdk/build.gradle.kts` publishing blocks

### Release Workflow Can't Push

```
refusing to allow a GitHub App to create or update workflow
```

- Ensure GitHub App has "Contents: Read & Write" permission
- Verify the app is installed on the repository

### JitPack Build Fails

Check the build log at: `https://jitpack.io/#t-0-network/provider-sdk/TAG`

Common issues:
- Missing `chmod +x gradlew` in `jitpack.yml`
- JDK version mismatch
- Test failures (JitPack runs full build by default)

### Maven Central Sync Delayed

- Initial sync can take up to 2 hours
- Subsequent syncs usually 10-30 minutes
- Check status: https://repo1.maven.org/maven2/network/t-0/provider-sdk-java/

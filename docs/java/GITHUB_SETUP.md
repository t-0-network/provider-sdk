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

### Maven Central (OSSRH) Secrets

| Secret | Description | How to Obtain |
|--------|-------------|---------------|
| `OSSRH_USERNAME` | Sonatype OSSRH username | Your JIRA username from [Sonatype JIRA](https://issues.sonatype.org) |
| `OSSRH_PASSWORD` | Sonatype OSSRH password | Your JIRA password |
| `GPG_PRIVATE_KEY` | Full armored GPG private key | See [GPG Key Generation](#gpg-key-generation) below |

### Release Workflow Secrets

| Secret | Description | How to Obtain |
|--------|-------------|---------------|
| `CI_APP_PRIVATE_KEY` | GitHub App private key (PEM format) | See [GitHub App Setup](#github-app-setup) below |

## Required GitHub Variables

Navigate to: **Repository → Settings → Secrets and variables → Actions → Variables**

| Variable | Description | Example |
|----------|-------------|---------|
| `CI_APP_ID` | GitHub App ID | `123456` |

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

4. Create the app and note the **App ID** (shown on the app's settings page)

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

2. **Variable**: `CI_APP_ID`
   - The numeric App ID from the app's settings page

## Sonatype OSSRH Setup

### First-Time Setup

1. **Create Sonatype JIRA Account**
   - Register at https://issues.sonatype.org

2. **Request Group ID**
   - Create a new issue: Project = "Community Support - Open Source Project Repository Hosting"
   - Issue Type = "New Project"
   - Group Id = `network.t0`
   - Provide proof of domain ownership or GitHub organization

3. **Wait for Approval**
   - Sonatype team will verify and approve (usually 1-2 business days)
   - You'll receive email confirmation

### Verify Setup

After approval, verify you can access:
- Staging: https://s01.oss.sonatype.org
- Login with your JIRA credentials

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
2. Visit https://jitpack.io/#t-0-network/provider-java
3. Click "Get it" on your version to trigger a build
4. Check the build log for any issues

### JitPack Artifact Coordinates

| Module | JitPack Coordinates |
|--------|---------------------|
| SDK | `com.github.t-0-network:provider-java:TAG` |

Where `TAG` can be:
- Release tag: `1.0.33` (no `v` prefix — tags are bare version numbers)
- Branch: `master-SNAPSHOT`
- Commit hash: `abc1234`

> **Note:** The CLI is not published to JitPack. It is distributed as a GitHub Release asset.

## Complete Setup Checklist

### One-Time Setup

- [ ] Create Sonatype JIRA account
- [ ] Request `network.t0` group ID approval
- [ ] Generate GPG key (4096-bit RSA, no passphrase)
- [ ] Upload GPG public key to keyservers
- [ ] Create GitHub App for releases
- [ ] Install GitHub App on repository

### GitHub Configuration

- [ ] Add secret: `OSSRH_USERNAME`
- [ ] Add secret: `OSSRH_PASSWORD`
- [ ] Add secret: `GPG_PRIVATE_KEY`
- [ ] Add secret: `CI_APP_PRIVATE_KEY`
- [ ] Add variable: `CI_APP_ID`

### Verification

- [ ] Trigger a test release to staging (don't release)
- [ ] Verify JitPack can build: https://jitpack.io/#t-0-network/provider-java
- [ ] Test CLI generates projects with both repository options

## Workflow Reference

| Workflow | File | Trigger | Purpose |
|----------|------|---------|---------|
| CI | `.github/workflows/ci.yaml` | Push to master, PRs | Build and test |
| Release | `.github/workflows/release.yaml` | Manual dispatch | Create release, tag, update versions |
| Publish | `.github/workflows/publish.yaml` | Tag push | Publish to Maven Central, upload CLI to GitHub Release |

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
- Check `sdk/build.gradle.kts` and `cli/build.gradle.kts` publishing blocks

### Release Workflow Can't Push

```
refusing to allow a GitHub App to create or update workflow
```

- Ensure GitHub App has "Contents: Read & Write" permission
- Verify the app is installed on the repository

### JitPack Build Fails

Check the build log at: `https://jitpack.io/#t-0-network/provider-java/TAG`

Common issues:
- Missing `chmod +x gradlew` in `jitpack.yml`
- JDK version mismatch
- Test failures (JitPack runs full build by default)

### Maven Central Sync Delayed

- Initial sync can take up to 2 hours
- Subsequent syncs usually 10-30 minutes
- Check status: https://repo1.maven.org/maven2/network/t0/

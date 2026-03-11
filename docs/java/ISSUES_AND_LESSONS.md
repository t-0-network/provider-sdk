# Issues & Lessons Learned

Historical issues encountered during development, extracted from git history. Organized chronologically (newest first).

## JitPack Dependency Coordinates (v1.0.31–1.0.33)

**Problem:** Template used wrong JitPack coordinates `com.github.t-0.provider-sdk-java:sdk` — wrong GitHub org separator (`.` vs `-`), wrong repo name, and included a nonexistent submodule path.

**Root cause:** The GitHub org `t-0-network` contains a hyphen, and JitPack uses `-` as separator between `com.github` and org. The repo was renamed to `provider-java` but coordinates weren't updated. JitPack doesn't need `:sdk` submodule since `jitpack.yml` only publishes the SDK.

**Fix:** Use `com.github.t-0-network:provider-java:<version>` — no submodule, correct org name.

**Also:** Tags don't use `v`-prefix (they're `1.0.33`, not `v1.0.33`), so the CLI must not prepend `v` when constructing the dependency version.

## BufferedReader Stdin Bug (v1.0.33)

**Problem:** When piping input to the CLI (`echo "name\n1" | java -jar provider-init.jar`), the repository prompt always received empty input regardless of what was piped.

**Root cause:** `readLine()` created a new `BufferedReader(new InputStreamReader(System.in))` on every call. The first BufferedReader buffered all available stdin. The second call created a new BufferedReader over the now-empty stream.

**Fix:** Store a single `BufferedReader` instance as a field and reuse it across all `readLine()` calls.

## Docker Build — Project Name in Install Path (v1.0.29)

**Problem:** `docker build` failed with `COPY --from=build /app/build/install/provider/ ./` because the install directory was named after `rootProject.name` (the user's project name), not `provider`.

**Root cause:** Gradle's `application` plugin uses `rootProject.name` as the default install directory name. Different project names produce different paths.

**Fix:** Set `applicationName = "provider"` in `build.gradle.kts` so `installDist` always outputs to `build/install/provider/`.

## Docker Build — installDist vs build (v1.0.27)

**Problem:** Dockerfile used `./gradlew build` and tried to copy a thin JAR, but the application needs all dependency JARs to run.

**Fix:** Use `./gradlew installDist` which creates a full distribution with `bin/` scripts and `lib/` directory containing all JARs. Copy the entire install directory.

## Dockerfile Base Image — Alpine vs Noble (v1.0.25–1.0.27)

**Problem:** Alpine-based JRE images don't have ARM64 builds for `eclipse-temurin`, causing failures on Apple Silicon and ARM servers.

**Fix:** Use `eclipse-temurin:17-jre-noble` (Ubuntu-based) instead of `eclipse-temurin:17-jre-alpine`.

## Maven Central Group ID (v1.0.25)

**Problem:** Template's `build.gradle.kts` used dependency group `network.t0` but the actual Maven Central group is `network.t-0` (with hyphen).

**Fix:** Use `network.t-0:provider-sdk-java:<version>` for Maven Central dependencies.

## CLI Distribution — GitHub Releases (v1.0.23)

**Problem:** Publishing the CLI to Maven Central was unnecessary complexity. Users don't resolve the CLI as a Gradle dependency — they download and run the JAR directly.

**Fix:** Removed CLI from Maven Central/JitPack publishing. CLI JAR is now uploaded as a GitHub Release asset by the Publish workflow. Users download via: `curl -fsSL -L https://github.com/t-0-network/provider-java/releases/latest/download/provider-init.jar -o provider-init.jar`

## Maven Central Publishing — JReleaser to NMCP Migration (v1.0.19)

**Problem:** JReleaser was heavyweight and had configuration issues (deployment ID extraction, `skipPublicationCheck` requiring external YAML config).

**Fix:** Migrated to NMCP (GradleUp/nmcp) plugin which directly uses the Central Portal API. Simpler configuration, more reliable. JReleaser config preserved as comments for potential re-enablement.

## JReleaser Configuration Issues (v1.0.11–1.0.17)

Multiple iterations to get JReleaser working:

- **v1.0.11:** Initial migration to Maven Central Portal with JReleaser
- **v1.0.13:** JReleaser project name had to match expectations (`provider-java`)
- **v1.0.13:** Split publish workflow into build + verify jobs to allow async Maven Central propagation checking
- **v1.0.15:** `skipPublicationCheck` needed `jreleaser.yml` file, couldn't be set in Gradle DSL alone; later fixed with JReleaser 1.22.0
- **v1.0.17:** Verify job simplified to directly poll Maven Central by version URL instead of parsing deployment IDs

## Template .env Generation (v1.0.25–1.0.27)

**Problem:** `EnvFileWriter` hardcoded environment variables instead of reading from `.env.example`, causing drift between the two.

**Fix:** Refactored to read `.env.example` from the extracted template and substitute actual values. Also ensured `TemplateExtractor` doesn't skip `.env.example`.

## Build.gradle.kts Repository Replacement (v1.0.33)

**Problem:** CLI used regex replacement to switch between JitPack and Maven Central in the template's `build.gradle.kts`. The regex was too broad and clobbered `if`-conditions in the file.

**Fix:** Use exact string match replacement (`val sdkRepository = "jitpack"` → `val sdkRepository = "maven-central"`) instead of regex.

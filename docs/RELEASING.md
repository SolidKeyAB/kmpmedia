# Releasing KMPMedia

This is the project's **official release process** and the decision behind it.

## Decision: publishing is manual and local

We publish `se.solidkey:kmpmedia-lib` to **Maven Central by hand, from a maintainer's
machine** — not from CI. The GPG signing key stays in the maintainer's local keyring and is
**never stored in GitHub** (no signing key in Actions secrets).

### Why

- **Maven Central requires PGP-signed artifacts.** A GitHub-hosted runner could only sign if
  the private key were reachable by the runner (i.e. stored as an Actions secret). Keyless
  signing (Sigstore / GitHub OIDC) is **not accepted by Maven Central**, so there is no
  "CI signs but the key lives nowhere" option.
- We release **infrequently**, so a one-command local publish is barely any friction.
- Keeping the key off GitHub is the **simplest and most secure** choice: the key never leaves
  the maintainer's machine, so there is nothing in the repo to leak, scope, or rotate.

CI's job is therefore **build + test only** — see `.github/workflows/build.yml`, which runs
the tests on every push to `main`.

## How to cut a release

1. **Land the change on `main`** with tests green, and bump the version in `gradle.properties`
   (`LIBRARY_VERSION_FALLBACK=<x.y.z>`) plus a `CHANGELOG.md` entry.

2. **Publish to Maven Central (local, signed):**

   ```bash
   ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys <KEY_ID>)" \
     ./gradlew publishAndReleaseToMavenCentral -Pversion=<x.y.z> --no-configuration-cache
   ```

   - `--no-configuration-cache` is **required** — the publish plugin cannot release to Central
     with Gradle's configuration cache enabled (see gradle/gradle#22779).
   - The Portal token lives in `~/.gradle/gradle.properties`
     (`mavenCentralUsername` / `mavenCentralPassword`).
   - Central's CDN (`repo1.maven.org`) syncs roughly 10–15 min after a successful upload.

3. **Cut the GitHub Release _without_ triggering the publish workflow.** `release.yml` fires on
   a `v*.*.*` tag push and runs on a **macOS runner**, so we disable it while tagging to avoid a
   pointless run:

   ```bash
   gh workflow disable "Publish KMP Library Release" -R SolidKeyAB/kmpmedia
   gh release create v<x.y.z> --target main --title "…" --notes-file NOTES.md --latest
   gh workflow enable  "Publish KMP Library Release" -R SolidKeyAB/kmpmedia
   ```

   `gh release --target` must be a **branch name** (e.g. `main`), not a commit SHA — a SHA is
   rejected with HTTP 422.

## The `release.yml` workflow (dormant by design)

`release.yml` is kept but **intentionally dormant**: both of its publish steps are gated on repo
secrets we deliberately do **not** set (`MAVEN_CENTRAL_*`, `SIGNING_IN_MEMORY_KEY`, `GPR_TOKEN`).
With no secrets present, a run is a harmless no-op — nothing publishes and nothing fails.

If we ever decide to automate publishing, adding those secrets switches it on with no further
changes. If we do, prefer a **dedicated, revocable signing subkey** (not the primary GPG key), so
a leak can be revoked without touching the maintainer's identity.

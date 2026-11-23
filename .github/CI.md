# Self-hosted runner CI

These workflows run on a Forgejo Actions runner inside WSL on a Windows host, with
no access to `github.com`. Every `uses:` that pointed at a marketplace action has
been replaced with a plain `run:` step.

## Where the CI files live

Everything under `.github/` is executed by the CI itself. The two exceptions are
`image/` and the host scripts, whose headers say where to run them.

```
.github/
├── workflows/       one stage per file, each dispatchable on its own
├── image/           the CI image definitions (docker build on the runner host)
├── scripts/ci/      shell libraries sourced inside a job
│                    common.sh, cnb.sh, cnb-upload.sh, parallel.sh
│                    plus three scripts a single step calls:
│                    ci-checkout.sh, telegram-trigger.sh, forgejo-release.sh
├── scripts/         upload-telegram.sh (inlined as base64 by publish/release),
│                    setup-rust-build.sh
├── riscv64-cargo.toml
├── CI.md            this file
└── CI-TEMPLATE.md   template for writing a new workflow (Chinese)
```

Tools that are never run by CI live outside, in `scripts/`: `scripts/dev/` for the
developer machine, `scripts/host/` for the runner host. Two of them relate to CI
and both run on the host, not in a job: `seed-ci-tools.sh` (installs
`/ci/tools/bin/ci-checkout`) and `seed-cargo-cache.sh` (prewarms the cargo
registry, see "Things this network cannot reach").

### Checkout lives on the host, not in the repository

Every job starts with `run: /ci/tools/bin/ci-checkout`, and that file is not in
this repository: a job has to clone before it can read anything the repository
ships, so a checkout helper cannot be sourced from the tree it is fetching.

The source of truth is `.github/scripts/ci/ci-checkout.sh`; the copy on the host
is installed from it:

```bash
# Docker runner with a named volume (`forgejo-ci:/ci`, the layout this repo uses)
CI_TOOLS_VOLUME=forgejo-ci bash scripts/host/seed-ci-tools.sh

# Runner jobs that bind-mount a host directory instead
bash scripts/host/seed-ci-tools.sh
```

`/ci/tools/bin` already holds `forgejo-runner` for the same reason, so a host is
expected to be seeded. A host without this file fails at checkout with
`/ci/tools/bin/ci-checkout: No such file or directory` (exit 127) — every job,
in two seconds, before anything is built.

## Workflow layout

Each stage is its own workflow, so any of them can be dispatched alone:

| File | Builds | Notes |
| --- | --- | --- |
| `build-lkm.yml` | `*_kernelsu.ko` for 8 KMIs | matrix, calls `ddk-lkm.yml`, uses the `ddk-min` images |
| `ksud.yml` | `ksud` | all four targets by default, subset via `targets=` |
| `ksuinit.yml` | `ksuinit` | |
| `manager.yml` | the manager APK | build + repack/sign (no spoofed variant) |
| `publish.yml` | — | rolling `manager-ci` (main) / `manager-dev` (dev) build and Telegram upload, one job |
| `release.yml` | — | orchestrates the stages, then assembles `/ci/runs/<id>/release` |
| `shellcheck.yml`, `clippy.yml`, `clang-format.yml`, `rustfmt.yml` | — | lint jobs |
| `validate.yml` | — | `forgejo-runner validate` on the workflow files, a check that every helper the workflows source still exists, and one that every script they call by path is still on disk |

### Showing the job graph

Forgejo lists a run's jobs but draws no dependencies between them
(forgejo#12717 is still open), so the shape is written where Forgejo does render
it: `determine-version` appends a Mermaid graph to its job summary, built from
the same `needs` the workflow uses. Forgejo 17 renders Mermaid in job summaries
(runner 13.2 or newer); GitHub renders it natively.

It is structure only. Durations, statuses and the click-through to a log stay in
the job list, which owns them — a static graph cannot show what a finished run
did anyway.

`manager.yml` and `publish.yml` take `artifacts_run=<id>`, which seeds this run's
artifact directory from an earlier run. That makes repackaging take minutes
instead of rebuilding the native chain.

## One image for everything

`.github/image/Dockerfile` builds `sukisu-ci:<tag>` from `catthehacker/ubuntu` and
bakes in JDK 21, the Android SDK (`platforms;android-37.0`,
`build-tools;37.0.0`, `cmake;3.22.1`, `cmake;3.31.6`), NDK `29.0.14206865`,
`libclang-21-dev`, Gradle 9.7.1, stable Rust with clippy/rustfmt plus the Android
targets, `nightly`/`nightly-2026-09-23` with `rust-src`, the KPM tools, and
git/curl/python3/zip/openssl/shellcheck/clang-format.

Installing that per job cost several gigabytes of downloads per run and left the
runner idle while a 1.5 GB NDK unpacked. A job now starts in seconds and its only
work is the build itself.

```bash
# on the runner host (WSL), from the repository root
bash .github/image/build-image.sh 6
```

Then point the runner at it:

```ini
# /etc/forgejo-runner/config.yml
runner:
  labels:
    - "ubuntu-latest:docker://sukisu-ci:6"
  capacity: 4
container:
  options: "--add-host=host.docker.internal:host-gateway --volume /srv/forgejo-ci:/ci"
  valid_volumes:
    - /srv/forgejo-ci
```

`capacity: 4` with `MAKEFLAGS=-j6` / `CARGO_BUILD_JOBS=6` per job keeps four
concurrent jobs inside a 32-core host. Build a new tag instead of overwriting
one, so a running job keeps the image it started with.

## What replaces what

| Was | Now |
| --- | --- |
| `actions/checkout` | `/ci/tools/bin/ci-checkout`, which clones from a shared bare mirror in `/ci/repo-cache` with `--shared` |
| `actions/setup-java`, `android-actions/setup-android`, `nttld/setup-ndk`, `Swatinem/rust-cache` | baked into the image; each job runs a `Check toolchain` step |
| `actions/upload-artifact`, `actions/download-artifact` | `ci_artifact_put` / `ci_download_artifact` from `.github/scripts/ci/common.sh`; CNB is the storage and the cross-runner transport, and summaries link to it |
| `softprops/action-gh-release` | files collected under `${CI_ARTIFACT_ROOT}/release` |
| `gh release` publishing | split APKs copied to `${CI_ARTIFACT_ROOT}/manager-<channel>/<sha>` and uploaded to the matching CNB release |

## Things this network cannot reach

`github.com` and `raw.githubusercontent.com` resolve to `::1` here (the tunnel
only proxies a fixed domain list), and `services.gradle.org` redirects to
`github.com`. Nothing in the pipeline may depend on them:

- **Rust git dependencies** — eight crates come from git on `github.com`.
  `.github/image/mirror-git-deps.sh` mirrors them into `/srv/forgejo-ci/git-mirrors`
  with the revisions `Cargo.lock` pins tagged, the image bakes them in, and
  `git url.<mirror>.insteadOf` plus `cargo net.git-fetch-with-cli` redirect the
  fetches.
- **Gradle** — `gradle-wrapper.properties` would download its distribution from
  `github.com`. The image ships Gradle 9.7.1 and the workflows call `gradle`
  instead of `./gradlew`.
- **KPM tools** — `:app:downloadKpmTools` fetches `kptools-android` and `kpimg`
  from `raw.githubusercontent.com`. The workflow sets `KPM_TOOLS_BASE_URL` to the
  release assets of the mirrored KernelPatch repo, and the image also carries a
  copy at `/opt/kpm-tools` that the job seeds into `app/build/generated/kpmTools`
  after `gradle clean`.
- **crates.io** — `static.crates.io` accepts the TCP connection and then drops
  the TLS handshake (`unexpected eof while reading`), so a cargo download is a
  coin flip and one `hashbrown` download took a builtzygisk run down.
  `scripts/host/seed-cargo-cache.sh` fills the persistent `/ci/tools/cargo`
  registry cache from a container that has access (it clones BuiltZygisk's
  `zygiskd`, runs `cargo fetch` for the job's nightly, and the crates and the
  mirrored git dependencies land in the cache the job already mounts), and
  `builtzygisk.yml` then runs with `CARGO_NET_OFFLINE=1`. Re-run the script when
  `zygiskd` gains a dependency or floats to a version the cache does not have;
  nothing in the image needs rebuilding.

## Artifact transport

Jobs may land on different runners, and a re-run may need another run's output,
so artifacts move through these places, tried in this order by
`ci_download_artifact`:

1. **`/ci` volume** — `/ci/runs/<run_id>/<artifact>/`, written by
   `ci_artifact_put`. Free, but only exists on the host that produced the file.
2. **EasyTier peer** — each runner serves `/ci/runs` on port 18080
   (`scripts/host/artifact-server.sh`), so the other host can fetch the same tree.
   Flat files only: the manifest fetch rejects any entry containing `/`.
3. **CNB** — every artifact is also archived as `<name>.tar.gz` on the CNB
   release `ci-<run_id>` of `CNB_ARTIFACT_REPO` (default
   `shirkneko/sukisu-artifacts`). This is the path that works across hosts *and*
   across runs, and it carries the directory layout the peer cannot. Downloads
   are anonymous. Those releases are created as prereleases so the manager's
   stable channel never picks one up.

   The per-run releases live in their own repository, separate from the release
   repository (`CNB_REPO`, default `shirkneko/sukisu-release`), and only the newest
   `CI_ARTIFACT_KEEP_RUNS` (default 5) are kept: `ci_artifact_cnb_prune` deletes
   the older release *and* its git tag once per run per runner. The run doing the
   pruning is never a candidate, so a slow run cannot delete its own release
   halfway through. Without that cap the repository collects a release per run
   forever, which is what got the old `sukisu-result` repository removed for
   release abuse. Older runs therefore cannot be fetched from CNB any more; they
   stay on `/ci` only where they were produced.
4. **Forgejo Artifacts API** — last resort (zip download); needs a token and is
   the slow endpoint.

CNB is where the artifacts live on purpose: the tunnel to the Forgejo server is
the bottleneck, so no job copies them back into a Forgejo artifact or release.
Job summaries and the release bodies link straight at the CNB download URLs; the
only thing Forgejo still stores is release metadata (title and body) the manager
parses.

Summary links percent-encode one character of the CNB account name
(`cnb.cool/%73hirkneko/sukisu-artifacts/...`). The runner masks every occurrence
of a secret value in job output, and on this instance that value is the account
name, so a plain link arrives as `cnb.cool/***/...`. CNB decodes the escape, so
the link still works, and the masker no longer matches.

```
/ci/repo-cache/                bare mirror per repository, shared by all jobs
/ci/runs/<run_id>/<artifact>/  per-run artifacts (+ manifest.json)
/ci/runs/<run_id>/release/     packaged release files
/ci/runs/<run_id>/manager-ci/   rolling main APKs
/ci/runs/<run_id>/manager-dev/  rolling dev APKs
/ci/tools/cargo/               cargo registry + git cache
/ci/tools/gradle/              Gradle user home
```

`/ci` is a Docker named volume (`forgejo-ci`), not a host directory: 
`container.options` mounts it as `--volume forgejo-ci:/ci`, and `valid_volumes`
has to allow the same name. There is no `/srv/forgejo-ci` on the host, so read
and write through a container:

```bash
docker run --rm -v forgejo-ci:/ci alpine ls /ci/tools/bin
```

`valid_volumes` matters: act silently drops a `--volume` it has not been told to
allow, and the job then writes to a throwaway `/ci` inside the container.

Without `CNB_TOKEN` the puts stay on `/ci` (and warn), which only works when
every job of the run landed on the same host; nothing fails, so a PR build still
runs.

## Secrets and variables

Signing uses repository **variables** (`KEYSTORE` as base64, `KEY_ALIAS`,
`KEYSTORE_PASSWORD`, `KEY_PASSWORD`) with the same names accepted as secrets.
Telegram upload reads `BOT_TOKEN` and `CHAT_ID`, plus `MESSAGE_THREAD_ID` for
normal and dev builds (`MESSAGE_DEV_THREAD_ID` overrides it on dev) and
`MESSAGE_RELEASES_THREAD_ID` for releases; it is skipped when `BOT_TOKEN` or
`CHAT_ID` is empty. Only the single Manager variant is published.

`TG_API_BASE` has to be set too. CNB cannot reach `api.telegram.org`, so the
delivery runs through a Cloudflare Worker proxy: the variable is the Bot API base
**including the worker's secret path**, e.g.
`https://tg.oddnexus.top/<secret>`, and `upload-telegram.sh` appends
`/bot<token>/<method>` to it. The worker only accepts `/<secret>/bot...` and
answers 404 for everything else, so the secret is what keeps the proxy from being
used by anyone else — keep it in the repository variable, never in this file or
in a committed `.cnb.yml`. `telegram-trigger.sh` fails the publishing step when
the variable is still the default, rather than letting the CNB build time out.

Rolling channels: main publishes `manager-ci` (the tag the manager's CI updater
reads) and dev publishes `manager-dev`. Both are CNB prereleases, so a dev push
never replaces the build main was tested with and neither enters the stable
update channel; a dispatch from any other ref signs nothing and publishes
nothing. Both are deleted and recreated on every build (`CNB_RECREATE=true`):
CNB keeps the original `published_at` when a release is updated, and its release
pages only embed the newest ten, so a patched-in-place release would eventually
be pushed off the page the updater reads.

CNB needs `CNB_TOKEN` (release read/write; the per-run pruning also needs
`repo-code:r` to list releases). Two optional variables pick the repositories:
`CNB_REPO` (default `shirkneko/sukisu-release`) for stable releases and the rolling
channels, and `CNB_ARTIFACT_REPO` (default `shirkneko/sukisu-artifacts`) for the
`ci-<run_id>` archives. Publishing back to Forgejo uses `RELEASE_API_TOKEN` when
set, and the built-in `GITHUB_TOKEN` otherwise.

Telegram delivery is a CNB pipeline in the release repository, not a workflow
step: `.github/scripts/ci/telegram-trigger.sh` posts to
`{repo}/-/build/start` with the `api_trigger_telegram` event, and the pipeline
downloads the release's APKs and runs the inline copy of
`.github/scripts/upload-telegram.sh`. The pipeline source is versioned here as
`.github/cnb/sukisu-release.cnb.yml` and has to be committed to the release
repository as `.cnb.yml` on its default branch — without it the trigger fails and
nothing reaches Telegram.

## Disabled workflows

Renamed to `*.yml.disabled`; Forgejo and GitHub both ignore those names.

- `npm.yml` publishes to the npm registry with provenance — needs an OIDC token
  from GitHub that does not exist here.
- `ksud-extra.yml` builds with `cross`, installed from a pinned `github.com`
  commit, and needs a Docker socket inside the job container. Nothing calls it.

`builtzygisk.yml` is *not* disabled: `external/BuiltZygisk` is mirrored onto this
instance, and `ci.yml` / `release.yml` both call it.

## Commit signing

Commits are signed with GPG (`commit.gpgsign=true`, key
`1C7DD4CE810618C3`). Forgejo only shows the *Verified* badge when the public key
is registered on the account, which also requires proving ownership: the key's
email address is not on the account, so the key was added with a signature over
the challenge token that the API returns.

```bash
# register (or replace) the key on the account
gpg --armor --export 1C7DD4CE810618C3 > pubkey.asc
curl -X POST -H "Authorization: token $FORGEJO_TOKEN"   -H 'Content-Type: application/json'   -d "$(jq -Rs '{armored_public_key: .}' pubkey.asc)"   https://git.zako.shirkneko.top:20004/api/v1/user/gpg_keys
# if it answers "None of the emails ... could be found", sign the token it prints
printf '%s' '<token>' | gpg --armor --detach-sign > token.asc
# and add "armored_signature": "<token.asc contents>" to the JSON body
```

#!/usr/bin/env bash
# Prewarm the persistent cargo registry at /ci/tools/cargo with everything the
# builtzygisk job needs, so that job never downloads from static.crates.io.
#
# Why: on this network static.crates.io accepts the connection and then drops
# the TLS handshake ("unexpected eof while reading"). The registry cache is the
# only thing a job needs from the network, so it is filled here, once, from a
# container that does have access. builtzygisk.yml then runs with
# CARGO_NET_OFFLINE=1 and reads this cache.
#
# Run it on the runner host whenever BuiltZygisk's zygiskd dependencies change
# (a new crate, or a version newer than what is cached); it is additive, so
# re-running it is safe.
#
#   # /ci is a directory on the Windows host (Git Bash; drive letters allowed;
#   # E: is the default, use CI_ROOT when the runner mounted another drive)
#   CI_ROOT='X:/other/forgejo-ci' bash scripts/host/seed-cargo-cache.sh
#
#   # /ci is a WSL bind mount
#   bash scripts/host/seed-cargo-cache.sh /srv/forgejo-ci
#
#   # /ci is a docker named volume
#   CI_TOOLS_VOLUME=forgejo-ci bash scripts/host/seed-cargo-cache.sh
#
# A BuiltZygisk checkout can be passed as the second argument (or
# CI_BUILTZYGISK_SRC); otherwise the container clones it from the Forgejo
# mirror, which it can reach.
#
#   bash scripts/host/seed-cargo-cache.sh /srv/forgejo-ci ~/src/BuiltZygisk
set -euo pipefail

# Git Bash rewrites /ci in a -v argument to C:/Program Files/Git/ci; the same
# switch build-images.sh uses.
export MSYS2_ARG_CONV_EXCL='*'

# The Windows host keeps the runner's /ci at E:\other\forgejo-ci (the drive
# letter is not stable, so CI_ROOT overrides it); WSL bind-mounts /srv.
ci_root="${1:-${CI_ROOT:-}}"
if [ -z "$ci_root" ]; then
	case "$(uname -s)" in
	MINGW* | MSYS* | CYGWIN*) ci_root='E:/other/forgejo-ci' ;;
	*) ci_root='/srv/forgejo-ci' ;;
	esac
fi
bz_src="${2:-${CI_BUILTZYGISK_SRC:-}}"
image="${CI_TOOLS_IMAGE:-forgejo-ci/full:1}"
url="${CI_BUILTZYGISK_URL:-https://git.zako.shirkneko.top:20004/NkBe/BuiltNyaZygisk.git}"
# Keep in sync with RUSTUP_TOOLCHAIN in .github/workflows/builtzygisk.yml: the
# cache is resolved by the same cargo the job uses.
toolchain="${CI_RUST_TOOLCHAIN:-nightly-2026-09-23}"

# Git Bash needs Windows paths in -v; WSL and plain Linux accept them as-is.
topath() {
	if command -v cygpath >/dev/null 2>&1; then
		cygpath -m "$1"
	else
		printf '%s' "$1"
	fi
}

mounts=()
if [ -n "${CI_TOOLS_VOLUME:-}" ]; then
	mounts+=(-v "${CI_TOOLS_VOLUME}:/ci")
else
	if [ ! -d "$ci_root" ]; then
		echo "missing CI root: ${ci_root}" >&2
		echo "pass the directory mounted at /ci, or set CI_TOOLS_VOLUME" >&2
		exit 1
	fi
	mounts+=(-v "$(topath "$ci_root"):/ci")
fi

if [ -n "$bz_src" ]; then
	if [ ! -s "${bz_src}/zygiskd/Cargo.toml" ]; then
		echo "not a BuiltZygisk checkout: ${bz_src}" >&2
		exit 1
	fi
	mounts+=(-v "$(topath "$bz_src"):/src:ro")
fi

docker run --rm "${mounts[@]}" \
	-e "CI_RUST_TOOLCHAIN=${toolchain}" \
	-e "CI_BUILTZYGISK_URL=${url}" \
	-e CARGO_HOME=/ci/tools/cargo \
	-e CARGO_NET_GIT_FETCH_WITH_CLI=true \
	-e CARGO_NET_RETRY=10 \
	-e CARGO_HTTP_TIMEOUT=60 \
	"$image" bash -c '
	set -eux
	mkdir -p "$CARGO_HOME"
	work="$(mktemp -d)"
	trap "rm -rf \"$work\"" EXIT
	if [ -f /src/zygiskd/Cargo.toml ]; then
		cp -a /src/. "$work/"
	else
		git clone -q --depth 1 "$CI_BUILTZYGISK_URL" "$work"
	fi
	cd "$work/zygiskd"
	# git-fetch-with-cli (env above) is what makes the insteadOf rewrites in the
	# image /etc/gitconfig apply, so the two git dependencies come from
	# /opt/git-mirrors instead of github.com, which is unreachable.
	cargo "+${CI_RUST_TOOLCHAIN}" fetch
	test -d "${CARGO_HOME}/registry/cache"
	test -d "${CARGO_HOME}/git/db"
	echo "crates in cache: $(find "${CARGO_HOME}/registry/cache" -name "*.crate" | wc -l)"
	du -sh "${CARGO_HOME}/registry" "${CARGO_HOME}/git"
'

#!/usr/bin/env bash
# Install the job-side helper that must exist before a checkout can run.
#
# How to run it depends on how the runner is deployed:
#
#   # Docker: /ci is a named volume (docker-compose with `forgejo-ci:/ci`)
#   CI_TOOLS_VOLUME=forgejo-ci bash scripts/host/seed-ci-tools.sh
#
#   # Host bind mount: /srv/forgejo-ci on the host, /ci inside a job container
#   bash scripts/host/seed-ci-tools.sh                    # auto-detect
#   CI_TOOLS_BIN=/srv/forgejo-ci/tools/bin bash scripts/host/seed-ci-tools.sh
#
# Every job calls /ci/tools/bin/ci-checkout as its first step, so a host without
# this file fails at checkout with exit code 127 before any build starts.
set -euo pipefail

root="${1:-$(pwd)}"
src="${root}/.github/scripts/ci/ci-checkout.sh"

[ -f "$src" ] || {
	echo "not found: ${src}" >&2
	echo "pass the repository root: bash scripts/host/seed-ci-tools.sh /path/to/repo" >&2
	exit 1
}

# Docker deployment: /ci is a named volume, so there is no host directory to copy
# into. Write through a throwaway container that mounts the same volume; it stays
# readable by every later job container without restarting the runner.
if [ -n "${CI_TOOLS_VOLUME:-}" ]; then
	image="${CI_TOOLS_IMAGE:-forgejo-ci/base:1}"
	if ! docker volume inspect "$CI_TOOLS_VOLUME" >/dev/null 2>&1; then
		echo "no such docker volume: ${CI_TOOLS_VOLUME}" >&2
		docker volume ls >&2
		exit 1
	fi
	# MSYS_NO_PATHCONV keeps Git Bash from rewriting /ci in the -v argument.
	MSYS_NO_PATHCONV=1 docker run --rm -i -v "${CI_TOOLS_VOLUME}:/ci:rw" "$image" \
		bash -c 'mkdir -p /ci/tools/bin && cat > /ci/tools/bin/ci-checkout && chmod 755 /ci/tools/bin/ci-checkout' <"$src"
	MSYS_NO_PATHCONV=1 docker run --rm -v "${CI_TOOLS_VOLUME}:/ci" "$image" \
		sh -c 'ls -l /ci/tools/bin/ci-checkout'
	echo "seeded volume ${CI_TOOLS_VOLUME} at /ci/tools/bin/ci-checkout"
	exit 0
fi

# Git Bash has its own /srv (under the Git installation), so a run there would
# create a tree the runner never looks at and look like success until the next
# job fails at checkout. Refuse early instead.
case "$(uname -s)" in
MINGW* | MSYS* | CYGWIN*)
	# /c/Users/... is a Git Bash path; WSL mounts the same tree at /mnt/c/...
	wsl_path="$(pwd | sed 's|^/\([a-zA-Z]\)/|/mnt/\1/|')"
	echo "error: this is Windows' shell, not the runner host." >&2
	echo "  With Docker, name the volume instead:" >&2
	echo "    CI_TOOLS_VOLUME=forgejo-ci bash scripts/host/seed-ci-tools.sh" >&2
	echo "  With a host bind mount, run this inside WSL:" >&2
	echo "    sudo install -Dm755 ${wsl_path}/.github/scripts/ci/ci-checkout.sh \\" >&2
	echo "      /srv/forgejo-ci/tools/bin/ci-checkout" >&2
	exit 1
	;;
esac

bin="${CI_TOOLS_BIN:-}"
if [ -z "$bin" ]; then
	for candidate in /srv/forgejo-ci/tools/bin /ci/tools/bin; do
		parent="${candidate%/tools/bin}"
		if [ -d "$parent" ] && [ -w "$parent" ]; then
			bin="$candidate"
			break
		fi
	done
fi
bin="${bin:-/srv/forgejo-ci/tools/bin}"

install -d "$bin"
# install(1), not cp: the mode is part of the contract, a job executes this file.
install -m 0755 "$src" "${bin}/ci-checkout"
bash -n "${bin}/ci-checkout"

echo "seeded ${bin}/ci-checkout from ${src}"
case "$bin" in
/ci/*) echo "container path — a job will find it at ${bin}/ci-checkout" ;;
*) echo "host path — matches the runner's /ci volume mount" ;;
esac

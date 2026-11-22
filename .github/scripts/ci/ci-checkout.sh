#!/usr/bin/env bash
# Clone the repository into the job workspace from the per-repository bare
# mirror on /ci.
#
# This script lives on the runner host at /ci/tools/bin/ci-checkout rather than
# in the repository, because a job has to clone before it can read anything the
# repository ships: a checkout helper cannot be sourced from the tree it is
# fetching. Install it once per host with scripts/host/seed-ci-tools.sh; every
# job then calls it as `run: /ci/tools/bin/ci-checkout`.
#
# Reads GITHUB_REPOSITORY and GITHUB_SHA, and TOKEN (falls back to GITHUB_TOKEN).
set -euxo pipefail

remote="${CI_REMOTE:-https://git.zako.shirkneko.top:20004}"
mirror="/ci/repo-cache/${GITHUB_REPOSITORY}.git"
token="${TOKEN:-${GITHUB_TOKEN:-}}"

mkdir -p "$(dirname "$mirror")"
if [ ! -d "$mirror" ]; then
	# One bare mirror per repository lives on the runner host, shared by every
	# job through the /ci volume. Re-downloading the whole history per job made
	# parallel jobs stall on the same fetch.
	#
	# The token must not appear in the trace of `set -x`.
	set +x
	if [ -n "$token" ]; then
		git -c "http.${remote}/.extraHeader=Authorization: token ${token}" \
			clone -q --mirror "${remote}/${GITHUB_REPOSITORY}.git" "$mirror"
	else
		git clone -q --mirror "${remote}/${GITHUB_REPOSITORY}.git" "$mirror"
	fi
	set -x
elif command -v flock >/dev/null 2>&1; then
	# Concurrent jobs share one mirror, so the fetch has to be serialised or
	# git fails with "cannot lock ref".
	(
		flock 9
		git --git-dir="$mirror" fetch -q --prune origin \
			"+refs/heads/*:refs/heads/*" "+refs/tags/*:refs/tags/*" "+refs/pull/*:refs/pull/*"
	) 9>"${mirror}.lock"
else
	git --git-dir="$mirror" fetch -q --prune origin \
		"+refs/heads/*:refs/heads/*" "+refs/tags/*:refs/tags/*" "+refs/pull/*:refs/pull/*"
fi

git clone -q --shared --no-checkout "$mirror" .
git checkout -q "${GITHUB_SHA}"

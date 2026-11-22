#!/usr/bin/env bash
# Shared helpers for the self-hosted Forgejo runner.
#
# The workflows were originally written for github.com hosted runners and used
# marketplace actions for checkout, toolchain setup and artifact passing. This
# runner has no access to github.com, so every one of those actions is replaced
# with a plain script. Artifacts are exchanged through a host directory that the
# runner mounts into each job container (see CI_ARTIFACT_ROOT), because the
# runner has no artifact service of its own in this setup.

set -eu
# The job shell differs per image (bash on the hosted-like images, dash in
# the ddk-min container), so only enable pipefail where it exists.
if [ -n "${BASH_VERSION:-}" ]; then
	set -o pipefail
fi

CI_REMOTE="${CI_REMOTE:-https://git.zako.shirkneko.top:20004}"
CI_ARTIFACT_ROOT="${CI_ARTIFACT_ROOT:-/ci/runs/${GITHUB_RUN_ID:-local}}"
# CNB carries artifacts between runners. /ci only exists on the host that wrote
# the file, and the Forgejo artifact API is the slow endpoint, so every artifact
# is archived on CNB (release `ci-<run_id>`) as `<name>.tar.gz`.
CI_CNB_TAG_PREFIX="${CI_CNB_TAG_PREFIX:-ci-}"

# The CNB API calls live in cnb.sh, shared with cnb-upload.sh. Prefer the
# directory this file lives in (BASH_SOURCE), then the workspace, so sourcing
# works from any cwd and from a step script that was written to a temp path.
CI_SCRIPTS_DIR=""
if [ -n "${BASH_VERSION:-}" ]; then
	eval 'CI_SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"'
fi
if [ -n "$CI_SCRIPTS_DIR" ] && [ -f "${CI_SCRIPTS_DIR}/cnb.sh" ]; then
	# shellcheck disable=SC1091  # computed path; cnb.sh is checked on its own
	. "${CI_SCRIPTS_DIR}/cnb.sh"
elif [ -f "${GITHUB_WORKSPACE:-.}/.github/scripts/ci/cnb.sh" ]; then
	CI_SCRIPTS_DIR="${GITHUB_WORKSPACE:-.}/.github/scripts/ci"
	# shellcheck disable=SC1091
	. "${CI_SCRIPTS_DIR}/cnb.sh"
else
	echo "common.sh: cnb.sh not found next to the script or in \$GITHUB_WORKSPACE" >&2
	exit 1
fi

# ci_provide_node
#   act runs JS actions with `node` from the container PATH, and it would
#   otherwise clone every `uses:` action from data.forgejo.org. Both are
#   pre-seeded in the shared /ci volume, so a job needs no network.
#
#   Appends to $GITHUB_PATH, so it has to run before the step that uses
#   `uses:`; a later step in the same job still picks it up.
#
#   Checkout is not here on purpose: a job must clone before it can read this
#   file. The runner host provides /ci/tools/bin/ci-checkout instead, seeded by
#   scripts/host/seed-ci-tools.sh.
ci_provide_node() {
	local found="" dir action

	for dir in /opt/acttoolcache/node/*/x64/bin /ci/tools/node/bin; do
		if [ -x "$dir/node" ]; then
			echo "$dir" >>"$GITHUB_PATH"
			found=1
			break
		fi
	done
	[ -n "$found" ] || {
		echo "node not found (tool cache and /ci/tools/node)" >&2
		return 1
	}

	mkdir -p "$GITHUB_WORKSPACE/.ci-actions"
	for action in upload-artifact download-artifact; do
		rm -rf "$GITHUB_WORKSPACE/.ci-actions/$action"
		cp -a "/ci/tools/actions/$action" "$GITHUB_WORKSPACE/.ci-actions/$action"
	done
}

# ci_artifact_path NAME -> absolute directory for one artifact
ci_artifact_path() {
	printf '%s/%s\n' "$CI_ARTIFACT_ROOT" "$1"
}

# ci_artifact_manifest DIR
#   Writes the file list consumed by the LAN peer downloader.
ci_artifact_manifest() {
	local dir="$1"
	local files escaped first
	files="$(cd "$dir" && find . -type f ! -name manifest.json | sed 's#^\./##' | sort)"
	{
		printf '{"files":['
		first=1
		printf '%s\n' "$files" | while IFS= read -r file; do
			[ -n "$file" ] || continue
			if [ "$first" = 1 ]; then
				first=0
			else
				printf ','
			fi
			escaped="$(printf '%s' "$file" | sed 's/\\/\\\\/g; s/"/\\"/g')"
			printf '"%s"' "$escaped"
		done
		printf ']}'
	} > "$dir/manifest.json"
}

# ci_artifact_put NAME PATH...
#   Copies files or directories into the artifact directory. Missing sources are
#   an error: a silent skip would hide a broken build.
ci_artifact_put() {
	local name="$1"
	shift
	local dest
	dest="$(ci_artifact_path "$name")"
	mkdir -p "$dest"
	local src
	for src in "$@"; do
		if [ ! -e "$src" ]; then
			echo "artifact source missing: $src" >&2
			return 1
		fi
		# Match upload-artifact: a directory contributes its contents, not
		# the directory itself.
		if [ -d "$src" ]; then
			cp -a "$src"/. "$dest"/
		else
			cp -a "$src" "$dest"/
		fi
	done
	ci_artifact_manifest "$dest"
	echo "stored artifact ${name} in ${dest}"
	ci_artifact_cnb_put "$name" "$dest"
}

# ci_artifact_cnb_tag RUN_ID -> release tag that holds that run's artifacts
ci_artifact_cnb_tag() {
	printf '%s%s\n' "$CI_CNB_TAG_PREFIX" "${1:-${GITHUB_RUN_ID:-local}}"
}

# ci_artifact_cnb_put NAME DIR
#   Archives DIR (without manifest.json, which is a /ci-local detail) and stores
#   it as `<name>.tar.gz` on CNB. A missing token or a CNB outage only warns:
#   this host still has the /ci copy, and the workflows fall back to the Forgejo
#   artifact API.
ci_artifact_cnb_put() {
	local name="$1" dir="$2" tag id archive archived attempt canonical
	if ! cnb_enabled; then
		echo "artifact ${name}: CNB_TOKEN is not set, keeping it on /ci only" >&2
		return 0
	fi
	tag="$(ci_artifact_cnb_tag "${GITHUB_RUN_ID:-local}")"
	archive="$(mktemp)"
	if ! tar -czf "$archive" --exclude=manifest.json -C "$dir" .; then
		echo "artifact ${name}: cannot archive ${dir}" >&2
		rm -f "$archive"
		return 0
	fi
	# The release lives in CNB_REPO, which does not contain this build's commit,
	# so target_commitish has to be a ref of that repository: CNB rejects a
	# foreign SHA with HTTP 400. prerelease=true keeps the dump out of the
	# manager's stable channel, which picks the newest non-prerelease release.
	if ! id="$(cnb_release_ensure "$tag" "$tag" "${CNB_TARGET_COMMITISH:-main}" "" true false)"; then
		echo "artifact ${name}: cannot reach CNB, keeping the /ci copy only" >&2
		rm -f "$archive"
		return 0
	fi
	# Upload, then prove the copy is visible through the tag our consumers list.
	# A racing create can leave a second release behind the same tag, and an asset
	# that went there would be silently invisible downstream.
	archived=0
	attempt=1
	while [ "$attempt" -le 2 ]; do
		attempt=$((attempt + 1))
		if ! cnb_asset_put "$archive" "${name}.tar.gz" "$id"; then
			break
		fi
		if cnb_release_assets "$tag" | grep -qxF "${name}.tar.gz"; then
			archived=1
			break
		fi
		echo "artifact ${name}: release ${id} does not list it; retrying on the tag's release" >&2
		canonical="$(cnb_release_id "$tag")"
		if [ -z "$canonical" ] || [ "$canonical" = "$id" ]; then
			break
		fi
		id="$canonical"
	done
	if [ "$archived" = 1 ]; then
		echo "artifact ${name} archived on CNB (${tag})"
	else
		echo "artifact ${name}: CNB upload failed, keeping the /ci copy only" >&2
	fi
	rm -f "$archive"
	return 0
}

# ci_artifact_names RUN_ID
#   One artifact name per line: everything CNB holds for the run, merged with
#   this host's /ci tree. Both are needed -- a producer that could not reach CNB
#   leaves its artifact only on its own host, and a consumer on another host only
#   sees the CNB copy. Only /ci entries with a manifest.json count, which excludes
#   the `manager-ci/<sha>` tree. The Forgejo API is the last resort.
ci_artifact_names() {
	local run_id="${1:-${GITHUB_RUN_ID:-0}}" names="" local_dir entry
	if cnb_enabled; then
		names="$(cnb_release_assets "$(ci_artifact_cnb_tag "$run_id")" |
			sed -n 's/\.tar\.gz$//p' | sort -u)"
	fi
	local_dir="/ci/runs/${run_id}"
	if [ -d "$local_dir" ]; then
		for entry in "$local_dir"/*/; do
			[ -f "${entry%/}/manifest.json" ] || continue
			names="$(printf '%s\n%s\n' "$names" "$(basename "$entry")" |
				sed '/^$/d' | sort -u)"
		done
	fi
	if [ -n "$names" ]; then
		printf '%s\n' "$names"
		return 0
	fi

	local token="${TOKEN:-${GITHUB_TOKEN:-}}"
	[ -n "$token" ] || return 1
	local site="${CI_ARTIFACT_BASE:-http://100.0.0.11:3000}"
	local api="${site}/api/v1/repos/${GITHUB_REPOSITORY}"
	local json
	if ! curl -fsS --max-time 4 "${site}/api/v1/version" >/dev/null 2>&1; then
		site="${FORGEJO_PUBLIC_URL:-https://git.zako.shirkneko.top:20004}"
		api="${site}/api/v1/repos/${GITHUB_REPOSITORY}"
	fi
	json="$(curl -fsS --max-time 30 -H "Authorization: token ${token}" \
		"${api}/actions/artifacts?limit=100")" || return 1
	printf '%s' "$json" | jq -r --argjson rid "${run_id:-0}" \
		'.[] | select(.expired == false) | select($rid == 0 or .run_id == $rid) | .name' |
		sort -u
}

# ci_download_artifact NAME DEST [RUN_ID]
#   Fetch one artifact into DEST, cheapest source first: this host's /ci copy,
#   the EasyTier peer, CNB (works across hosts *and* runs), then the Forgejo
#   artifact API.
ci_download_artifact() {
	local name="$1"
	local dest="$2"
	local run_id="${3:-${GITHUB_RUN_ID:-0}}"
	mkdir -p "$dest"

	local local_root="/ci/runs/${run_id}/${name}"
	if [ -d "$local_root" ]; then
		cp -a "$local_root"/. "$dest"/
		echo "artifact ${name} copied from ${local_root}"
		return 0
	fi

	# Runner-to-runner transfer over the LAN. Each runner serves /ci/runs on
	# port 18080 (scripts/host/artifact-server.sh).
	local peers="${CI_ARTIFACT_PEERS:-192.168.11.2 192.168.11.3}"
	local host manifest files file ok
	manifest="$(mktemp)"
	for host in $peers; do
		if ! curl -fsS --max-time 3 -o "$manifest" \
			"http://${host}:18080/${run_id}/${name}/manifest.json"; then
			continue
		fi
		files="$(jq -r '.files[]' "$manifest")"
		[ -n "$files" ] || continue
		ok=1
		for file in $files; do
			case "$file" in
			'' | */* | ..*)
				echo "artifact ${name}: refusing unsafe file name ${file}" >&2
				ok=0
				break
				;;
			esac
			mkdir -p "$(dirname "${dest}/${file}")"
			if ! curl -fL --retry 3 --progress-bar \
				"http://${host}:18080/${run_id}/${name}/${file}" \
				-o "${dest}/${file}"; then
				ok=0
				break
			fi
		done
		if [ "$ok" = 1 ]; then
			rm -f "$manifest"
			echo "artifact ${name} copied from EasyTier peer ${host}"
			return 0
		fi
	done
	rm -f "$manifest"

	# CNB: the path that works across hosts and across runs. Anonymous download,
	# and the archive keeps the directory layout the peer transfer cannot.
	local cnb_tag archive
	cnb_tag="$(ci_artifact_cnb_tag "$run_id")"
	archive="$(mktemp)"
	if cnb_asset_get "${name}.tar.gz" "$archive" "$cnb_tag" 2>/dev/null &&
		tar -xzf "$archive" -C "$dest" 2>/dev/null; then
		rm -f "$archive"
		echo "artifact ${name} downloaded from CNB (${cnb_tag})"
		return 0
	fi
	rm -f "$archive"

	local token="${TOKEN:-${GITHUB_TOKEN:-}}"
	[ -n "$token" ] || {
		echo "artifact ${name}: missing GITHUB_TOKEN for API download" >&2
		return 1
	}
	# Artifact traffic goes over EasyTier when the runner can reach the mesh;
	# fall back to the public endpoint for hosts outside it.
	local site="${CI_ARTIFACT_BASE:-http://100.0.0.11:3000}"
	local api="${site}/api/v1/repos/${GITHUB_REPOSITORY}"
	local json
	if ! curl -fsS --max-time 4 "${site}/api/v1/version" >/dev/null 2>&1; then
		site="${FORGEJO_PUBLIC_URL:-https://git.zako.shirkneko.top:20004}"
		api="${site}/api/v1/repos/${GITHUB_REPOSITORY}"
	fi
	json="$(curl -fsS --max-time 30 -H "Authorization: token ${token}" \
		"${api}/actions/artifacts?name=${name}&limit=100")"
	local id
	id="$(printf '%s' "$json" | jq -r --argjson rid "${run_id:-0}" \
		'[.[] | select(.expired == false) | select($rid == 0 or .run_id == $rid)] | sort_by(.id) | last | .id // empty')"
	[ -n "$id" ] || {
		echo "artifact ${name} not found for run ${run_id}" >&2
		return 1
	}
	# Build the zip URL on the same base: archive_download_url points at the
	# public host and would bypass EasyTier.
	local url="${api}/actions/artifacts/${id}/zip"
	local zip
	zip="$(mktemp)"
	curl -fL --retry 5 --retry-all-errors --progress-bar \
		-H "Authorization: token ${token}" -o "$zip" "$url"
	unzip -q -o "$zip" -d "$dest"
	rm -f "$zip"
	echo "artifact ${name} downloaded"
}

# ci_download_all_artifacts DEST [RUN_ID]
#   One directory per artifact. The names come from ci_artifact_names, so they are
#   visible across hosts and across runs even though nothing is uploaded to
#   Forgejo any more.
ci_download_all_artifacts() {
	local dest="$1"
	local run_id="${2:-${GITHUB_RUN_ID:-0}}"
	local names name
	names="$(ci_artifact_names "$run_id")" || true
	[ -n "$names" ] || {
		echo "no artifacts found for run ${run_id}" >&2
		return 1
	}
	mkdir -p "$dest"
	printf '%s\n' "$names" | while IFS= read -r name; do
		[ -n "$name" ] || continue
		ci_download_artifact "$name" "$dest/$name" "$run_id"
	done
}

# ci_cnb_link_base
#   The CNB download prefix for this run, with the account name percent-encoded
#   by one character.
#
#   The runner masks every occurrence of a secret value in job output -- on this
#   instance that value is the account name, so a plain
#   `cnb.cool/<owner>/...` link reaches the summary as `cnb.cool/***/...`.
#   Encoding one character of the owner keeps the URL valid (CNB decodes it,
#   verified) and no longer matches the masker.
ci_cnb_link_base() {
	local owner repo first rest hex
	owner="${CNB_REPO:-shirkneko/sukisu-result}"
	owner="${owner%%/*}"
	repo="${CNB_REPO:-shirkneko/sukisu-result}"
	repo="${repo#*/}"
	first="${owner%"${owner#?}"}"
	rest="${owner#?}"
	hex="$(printf '%s' "$first" | od -An -tx1 | tr -d ' \n')"
	[ -n "$hex" ] || {
		printf '%s/%s\n' "${CNB_WEB:-https://cnb.cool}" "$owner/$repo"
		return 0
	}
	printf '%s/%%%s%s/%s\n' "${CNB_WEB:-https://cnb.cool}" "$hex" "$rest" "$repo"
}

# ci_summary_artifacts NAME...
#   Job Summary: one row per artifact with its CNB download URL. CNB is where the
#   artifacts actually live (release `ci-<run_id>`), so the link resolves as soon
#   as the producer archived the file; nothing is copied back to Forgejo.
ci_summary_artifacts() {
	local summary="${FORGEJO_STEP_SUMMARY:-${GITHUB_STEP_SUMMARY:-}}"
	[ -n "$summary" ] || return 0
	local name base=""
	if cnb_enabled; then
		base="$(ci_cnb_link_base)/-/releases/download/$(ci_artifact_cnb_tag "${GITHUB_RUN_ID:-local}")"
	fi
	{
		echo "## 构建产物"
		echo
		if [ -n "$base" ]; then
			echo "| Artifact | 下载 |"
			echo "| --- | --- |"
			for name in "$@"; do
				echo "| \`${name}\` | [下载](${base}/${name}.tar.gz) |"
			done
		else
			echo "| Artifact |"
			echo "| --- |"
			for name in "$@"; do
				echo "| \`${name}\` |"
			done
			echo
			echo "> 本次运行的产物只归档在 \`/ci\` 上（没有 CNB token）。"
		fi
	} >> "$summary"
}

# ci_summary_release TITLE TAG PATH...
#   Job Summary for a rolling channel: one row per file with its CNB download URL
#   on release TAG. cnb-upload.sh writes the same TAG, so the links resolve as
#   soon as the upload step finished. Without a token the table stays and the note
#   says where the files really are.
ci_summary_release() {
	local title="$1" tag="$2"
	shift 2
	local summary="${FORGEJO_STEP_SUMMARY:-${GITHUB_STEP_SUMMARY:-}}"
	[ -n "$summary" ] || return 0
	local base="" file name size
	if cnb_enabled; then
		base="$(ci_cnb_link_base)/-/releases/download/${tag}"
	fi
	{
		echo "## ${title}"
		echo
		if [ -n "$base" ]; then
			echo "| 文件 | 大小 | 下载 |"
			echo "| --- | --- | --- |"
			for file in "$@"; do
				name="$(basename "$file")"
				size="$(du -h "$file" 2>/dev/null | cut -f1)"
				echo "| \`${name}\` | ${size:-?} | [下载](${base}/${name}) |"
			done
			echo
			echo "> 分支 \`${GITHUB_REF_NAME:-unknown}\` · 提交 \`${GITHUB_SHA:-unknown}\` · 已签名，可直接覆盖安装。"
		else
			echo "| 文件 |"
			echo "| --- |"
			for file in "$@"; do
				echo "| \`$(basename "$file")\` |"
			done
			echo
			echo "> 没有 CNB token，产物只留在 runner 的 \`/ci\` 上。"
		fi
	} >> "$summary"
}

# ci_summary_graph TITLE EDGE...
#   Job Summary: a Mermaid graph of the job dependencies, so the pipeline shape
#   is visible in the run page instead of being implied by the job list.
#
#   Forgejo 17 renders Mermaid in job summaries (runner 13.2+), GitHub renders
#   it natively, so one fragment works on both. It shows structure only: job
#   durations and status stay in the job list, which owns them.
#
#   EDGE is "from:to", one per argument. Job names may contain dashes; the
#   Mermaid node ids cannot, so they are substituted.
ci_summary_graph() {
	local title="$1"
	shift
	local summary="${FORGEJO_STEP_SUMMARY:-${GITHUB_STEP_SUMMARY:-}}"
	[ -n "$summary" ] || return 0
	local nodes=" " edge name from to
	{
		echo "## ${title}"
		echo
		echo '```mermaid'
		echo 'graph LR'
		for edge in "$@"; do
			for name in "${edge%%:*}" "${edge#*:}"; do
				case "$nodes" in
				*" ${name} "*) ;;
				*)
					nodes="${nodes}${name} "
					echo "  ${name//-/_}[\"${name}\"]"
					;;
				esac
			done
		done
		for edge in "$@"; do
			from="${edge%%:*}"
			to="${edge#*:}"
			echo "  ${from//-/_} --> ${to//-/_}"
		done
		echo '```'
		echo
		echo "> 分支 \`${GITHUB_REF_NAME:-unknown}\` · 提交 \`${GITHUB_SHA:-unknown}\`。"
		echo "> 图由各 job 的 \`needs\` 生成；耗时与状态见左侧 job 列表。"
	} >>"$summary"
}

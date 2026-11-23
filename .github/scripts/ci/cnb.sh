#!/usr/bin/env bash
# CNB (cnb.cool) release API helpers.
#
# Sourced by common.sh and by cnb-upload.sh, so the artifact transport and the
# release publishing share one implementation. POSIX sh on purpose: common.sh
# also runs under dash inside the ddk-min image.
#
# Environment:
#   CNB_TOKEN   bearer token; required for anything that writes
#   CNB_REPO    default shirkneko/sukisu-release (stable releases and rolling channels)
#   CNB_API     default https://api.cnb.cool
#   CNB_WEB     default https://cnb.cool (public download host)
#
# The helpers that touch a release take an optional trailing REPO so the
# artifact transport can keep its per-run releases in a separate repository
# (see ci_artifact_cnb_repo in common.sh) while publishing still targets
# CNB_REPO. Omitting REPO keeps the release repository.
#
# API surface used:
#   GET    /-/releases                          -> api.Release[] (paged)
#   GET    /-/releases/tags/<tag>
#   POST   /-/releases                          {tag_name,target_commitish,name,body,draft,prerelease,make_latest}
#   PATCH  /-/releases/<id>                     {name,body,prerelease,make_latest}
#   DELETE /-/releases/<id>
#   DELETE /-/git/tags/<tag>
#   GET    /-/releases/<id>                     -> .assets[]
#   DELETE /-/releases/<id>/assets/<asset_id>
#   POST   /-/releases/<id>/asset-upload-url    {asset_name,size,overwrite}
#   PUT    <upload_url>                         pre-signed object storage, no auth
#   POST   <verify_url>                         marks the upload committed

# cnb_repo
#   The repository releases are published to. Artifact helpers pass their own
#   repository explicitly instead of overwriting CNB_REPO, so a caller can
#   archive a build and publish a release in the same step.
cnb_repo() {
	printf '%s\n' "${CNB_REPO:-shirkneko/sukisu-release}"
}

# cnb_api_base [REPO]
cnb_api_base() {
	printf '%s/%s\n' "${CNB_API:-https://api.cnb.cool}" "${1:-$(cnb_repo)}"
}

# cnb_web_base [REPO]
cnb_web_base() {
	printf '%s/%s\n' "${CNB_WEB:-https://cnb.cool}" "${1:-$(cnb_repo)}"
}

# True when a token is configured; every write path checks this.
cnb_enabled() {
	[ -n "${CNB_TOKEN:-}" ]
}

# cnb_curl ARGS...
#   Authenticated API call. --fail is deliberately absent: the callers inspect
#   the status code instead of losing the response body. The token rides in a
#   header, so `set -x` (the workflows trace their steps) is muted around it.
cnb_curl() {
	local trace='' status=''
	case $- in
	*x*)
		trace=1
		set +x
		;;
	esac
	curl -sS --connect-timeout 10 --max-time 120 \
		-H "Authorization: Bearer ${CNB_TOKEN}" \
		-H 'Accept: application/vnd.cnb.api+json' "$@"
	status=$?
	[ -z "$trace" ] || set -x
	return "$status"
}

# cnb_release_id TAG [REPO]
#   Prints the release id for TAG, empty when the release does not exist.
cnb_release_id() {
	local tag="$1" repo json
	repo="${2:-$(cnb_repo)}"
	json="$(mktemp)"
	cnb_curl -o "$json" "$(cnb_api_base "$repo")/-/releases/tags/${tag}" >/dev/null 2>&1 || true
	jq -r '.id // empty' "$json" 2>/dev/null || true
	rm -f "$json"
}

# cnb_release_ensure TAG NAME TARGET BODY PRERELEASE MAKE_LATEST [REPO]
#   Prints the release id, creating the release when it is missing. Re-reading
#   after a failed create covers the case where the response was lost but the
#   release was written anyway (and the concurrent-run race).
cnb_release_ensure() {
	local tag="$1" name="$2" target="$3" body="$4" pre="$5" latest="$6"
	local repo="${7:-$(cnb_repo)}"
	local id json payload code
	id="$(cnb_release_id "$tag" "$repo")"
	if [ -n "$id" ]; then
		printf '%s\n' "$id"
		return 0
	fi
	json="$(mktemp)"
	# make_latest is a string on the wire ("true"/"false"); CNB rejects a JSON
	# boolean with errcode 2000006. prerelease is a boolean.
	payload="$(jq -nc --arg tag "$tag" --arg target "$target" --arg name "$name" \
		--arg body "$body" --arg latest "$latest" --argjson pre "$pre" \
		'{tag_name:$tag,target_commitish:$target,name:$name,body:$body,
		  draft:false,prerelease:$pre,make_latest:$latest}')"
	code="$(cnb_curl -o "$json" -w '%{http_code}' -H 'Content-Type: application/json' \
		-X POST -d "$payload" "$(cnb_api_base "$repo")/-/releases" || true)"
	id="$(jq -r '.id // empty' "$json" 2>/dev/null || true)"
	if [ -z "$id" ]; then
		echo "cnb: release create returned HTTP ${code}; reading it back" >&2
		# The API explains a rejection in the body; surface it instead of guessing.
		head -c 500 "$json" >&2 || true
		echo >&2
		id="$(cnb_release_id "$tag" "$repo")"
	fi
	rm -f "$json"
	# Producers archive in parallel, and CNB can accept two creates for the same
	# tag while the lookup then returns only one of them. Prefer whatever the tag
	# resolves to, so every job converges on the release the consumers will read.
	if [ -n "$id" ]; then
		local canonical
		canonical="$(cnb_release_id "$tag" "$repo")"
		[ -z "$canonical" ] || id="$canonical"
	fi
	[ -n "$id" ] || {
		echo "cnb: could not resolve the release id for ${tag}" >&2
		return 1
	}
	printf '%s\n' "$id"
}

# cnb_release_patch TAG NAME BODY PRERELEASE MAKE_LATEST
cnb_release_patch() {
	local tag="$1" name="$2" body="$3" pre="$4" latest="$5"
	local id payload code out
	id="$(cnb_release_id "$tag")"
	[ -n "$id" ] || {
		echo "cnb: release ${tag} not found for update" >&2
		return 1
	}
	payload="$(jq -nc --arg name "$name" --arg body "$body" \
		--arg latest "$latest" --argjson pre "$pre" \
		'{name:$name,body:$body,prerelease:$pre,make_latest:$latest}')"
	out="$(mktemp)"
	code="$(cnb_curl -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
		-X PATCH -d "$payload" "$(cnb_api_base)/-/releases/${id}" || true)"
	[ "$code" = "200" ] || {
		echo "cnb: release ${tag} update failed: HTTP ${code}" >&2
		head -c 500 "$out" >&2 || true
		rm -f "$out"
		return 1
	}
	rm -f "$out"
}

# cnb_release_delete TAG [REPO]
#   Deletes the release for TAG. Rolling channels use this instead of patching,
#   because a release keeps the published_at it was created with: the manager
#   reads CNB's release pages, which only embed the newest ten, so an
#   ever-patched release slowly sinks out of view. Idempotent: a missing release
#   is not an error.
cnb_release_delete() {
	local tag="$1" repo id code
	repo="${2:-$(cnb_repo)}"
	id="$(cnb_release_id "$tag" "$repo")"
	[ -n "$id" ] || return 0
	code="$(cnb_curl -o /dev/null -w '%{http_code}' \
		-X DELETE "$(cnb_api_base "$repo")/-/releases/${id}" || true)"
	case "$code" in
	200 | 202 | 204 | 404) return 0 ;;
	*)
		echo "cnb: failed to delete release ${tag}: HTTP ${code}" >&2
		return 1
		;;
	esac
}

# cnb_tag_delete TAG [REPO]
#   Removes the git tag. Deleting a release does not always remove its tag, and
#   recreating a release needs a free tag name. A missing tag is not an error.
cnb_tag_delete() {
	local tag="$1" repo code
	repo="${2:-$(cnb_repo)}"
	code="$(cnb_curl -o /dev/null -w '%{http_code}' \
		-X DELETE "$(cnb_api_base "$repo")/-/git/tags/${tag}" || true)"
	case "$code" in
	200 | 202 | 204 | 404) return 0 ;;
	*)
		echo "cnb: failed to delete tag ${tag}: HTTP ${code}" >&2
		return 1
		;;
	esac
}

# cnb_release_assets TAG [REPO]
#   One asset name per line. Empty when the release has no assets.
cnb_release_assets() {
	local tag="$1" repo json
	repo="${2:-$(cnb_repo)}"
	json="$(mktemp)"
	cnb_curl -o "$json" "$(cnb_api_base "$repo")/-/releases/tags/${tag}" >/dev/null 2>&1 || true
	jq -r '.assets[]?.name // empty' "$json" 2>/dev/null || true
	rm -f "$json"
}

# cnb_release_list [REPO]
#   One "TAG<TAB>ID<TAB>PUBLISHED_AT" per release, newest first as CNB returns
#   them. Paginates with page_size=100 (the API's ceiling); a short page ends the
#   walk. Returns 1 when the API answered with something that is not a JSON
#   array, so a token without repo-code:r looks like a failure instead of an
#   empty repository.
cnb_release_list() {
	local repo="${1:-$(cnb_repo)}" page=1 size=100 out count
	while [ "$page" -le 10 ]; do
		out="$(mktemp)"
		cnb_curl -o "$out" \
			"$(cnb_api_base "$repo")/-/releases?page=${page}&page_size=${size}" 2>/dev/null || true
		if ! jq -e 'type == "array"' "$out" >/dev/null 2>&1; then
			rm -f "$out"
			echo "cnb: cannot list releases of ${repo} (token needs repo-code:r)" >&2
			return 1
		fi
		jq -r '.[]? | [.tag_name, (.id | tostring), (.published_at // "")] | @tsv' "$out"
		count="$(jq 'length' "$out")"
		rm -f "$out"
		[ "$count" -ge "$size" ] || break
		page=$((page + 1))
	done
}


# cnb_asset_delete RELEASE_ID ASSET_ID
cnb_asset_delete() {
	local release_id="$1" asset_id="$2" code
	code="$(cnb_curl -o /dev/null -w '%{http_code}' \
		-X DELETE "$(cnb_api_base)/-/releases/${release_id}/assets/${asset_id}" || true)"
	case "$code" in
	200 | 204 | 404) return 0 ;;
	*)
		echo "cnb: failed to delete asset ${asset_id}: HTTP ${code}" >&2
		return 1
		;;
	esac
}

# cnb_clear_assets TAG
cnb_clear_assets() {
	local tag="$1" id ids
	id="$(cnb_release_id "$tag")"
	[ -n "$id" ] || return 0
	echo "cnb: clearing assets of ${tag}" >&2
	ids="$(mktemp)"
	cnb_curl "$(cnb_api_base)/-/releases/${id}" 2>/dev/null |
		jq -r '.assets[]?.id // empty' 2>/dev/null >"$ids" || true
	while IFS= read -r asset_id; do
		[ -n "$asset_id" ] || continue
		cnb_asset_delete "$id" "$asset_id" || true
	done <"$ids"
	rm -f "$ids"
}

# cnb_asset_put FILE ASSET_NAME RELEASE_ID [REPO]
#   Uploads through the pre-signed URL and then verifies, which is what makes the
#   asset readable from the public download host.
cnb_asset_put() {
	local file="$1" name="$2" release_id="$3"
	local repo="${4:-$(cnb_repo)}"
	local size url_json upload_url verify_url code
	size="$(wc -c <"$file" | tr -d ' ')"
	url_json="$(mktemp)"
	code="$(cnb_curl -o "$url_json" -w '%{http_code}' -H 'Content-Type: application/json' \
		-X POST -d "$(jq -nc --arg name "$name" --argjson size "$size" \
			'{asset_name:$name,size:$size,overwrite:true}')" \
		"$(cnb_api_base "$repo")/-/releases/${release_id}/asset-upload-url" || true)"
	case "$code" in
	200 | 201) ;;
	*)
		echo "cnb: upload-url failed for ${name}: HTTP ${code}" >&2
		head -c 500 "$url_json" >&2 || true
		rm -f "$url_json"
		return 1
		;;
	esac
	upload_url="$(jq -r '.upload_url // empty' "$url_json")"
	verify_url="$(jq -r '.verify_url // empty' "$url_json")"
	rm -f "$url_json"
	[ -n "$upload_url" ] && [ -n "$verify_url" ] || {
		echo "cnb: upload URL missing for ${name}" >&2
		return 1
	}
	# A big asset is a long single transfer: abort on a stall, not on elapsed time.
	curl -fsS --retry 4 --retry-all-errors --retry-delay 2 \
		--connect-timeout 20 --max-time 3600 \
		--speed-limit 1024 --speed-time 60 \
		-X PUT -H 'Content-Type: application/octet-stream' \
		--data-binary @"$file" "$upload_url" >/dev/null
	case "$verify_url" in
	http://* | https://*) ;;
	*) verify_url="${CNB_API:-https://api.cnb.cool}${verify_url}" ;;
	esac
	code="$(cnb_curl -o /dev/null -w '%{http_code}' -X POST "$verify_url" || true)"
	case "$code" in
	200 | 201) ;;
	*)
		echo "cnb: verify failed for ${name}: HTTP ${code}" >&2
		return 1
		;;
	esac
}

# cnb_asset_get ASSET_NAME DEST [TAG] [REPO]
#   Anonymous download from the public host (CNB answers with a 302 to
#   asset.cnb.cool, hence -L). Returns 1 when the asset is not there.
cnb_asset_get() {
	local name="$1" dest="$2" tag="${3:-${CNB_RELEASE_TAG:-}}"
	local repo="${4:-$(cnb_repo)}"
	[ -n "$tag" ] || return 1
	curl -fsS -L --retry 3 --retry-all-errors --retry-delay 2 \
		--connect-timeout 10 --max-time 900 \
		--speed-limit 1024 --speed-time 60 \
		-o "$dest" "$(cnb_web_base "$repo")/-/releases/download/${tag}/${name}"
}

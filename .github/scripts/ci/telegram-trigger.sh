#!/usr/bin/env bash
# Ask CNB to deliver a build to Telegram.
#
# No APK travels from here: CNB downloads it from the release and runs
# .github/scripts/upload-telegram.sh, which is handed over inline as base64
# with the caption logic included. Delegating keeps a local network failure from
# failing the publishing job.
#
# Env:
#   CNB_TOKEN CNB_REPO CNB_TAG CNB_KIND
#   THREAD_ID DEV_THREAD_ID            (DEV_THREAD_ID wins on branch dev)
#   BOT_TOKEN CHAT_ID TG_API_BASE TITLE BRANCH RUN_URL EVENT
set -euo pipefail

if [ -z "${CNB_TOKEN:-}" ]; then
	echo "CNB_TOKEN is not configured; skipping CNB Telegram trigger"
	exit 0
fi

thread="${THREAD_ID:-}"
if [ "${BRANCH:-}" = "dev" ] && [ -n "${DEV_THREAD_ID:-}" ]; then
	thread="$DEV_THREAD_ID"
fi

version="$(git rev-list --count HEAD 2>/dev/null || true)"
commit_text="$(git log -1 --format='%s by %an' 2>/dev/null | cut -c1-400)"
changes_url="$(printf '%s' "${EVENT:-}" | jq -r '.compare // .head_commit.url // empty' 2>/dev/null || true)"
if [ -z "$changes_url" ]; then
	changes_url="https://git.zako.shirkneko.top:20004/${GITHUB_REPOSITORY}/commit/${GITHUB_SHA}"
fi

# The caption script is inlined so CNB needs no access to this repository.
telegram_script_b64="$(base64 -w0 .github/scripts/upload-telegram.sh)"
payload="$(jq -nc --arg tag "$CNB_TAG" --arg kind "$CNB_KIND" \
	--arg thread "$thread" --arg bot "$BOT_TOKEN" --arg chat "$CHAT_ID" \
	--arg api "${TG_API_BASE:-https://api.telegram.org}" \
	--arg title "$TITLE" --arg branch "$BRANCH" \
	--arg version "$version" --arg commit "$commit_text" \
	--arg run "$RUN_URL" --arg changes "$changes_url" \
	--arg script "$telegram_script_b64" \
	'{event:"api_trigger_telegram",env:{TAG:$tag,KIND:$kind,THREAD_ID:$thread,TG_BOT_TOKEN:$bot,TG_CHAT_ID:$chat,TG_API_BASE:$api,TITLE:$title,BRANCH:$branch,VERSION:$version,COMMIT_TEXT:$commit,RUN_URL:$run,CHANGES_URL:$changes,TELEGRAM_SCRIPT_B64:$script}}')"

curl -fsS --retry 3 --retry-all-errors -X POST \
	-H "Authorization: Bearer ${CNB_TOKEN}" \
	-H 'Accept: application/json' \
	-H 'Content-Type: application/json' \
	-d "$payload" \
	"https://api.cnb.cool/${CNB_REPO}/-/build/start" >/dev/null

echo "triggered CNB Telegram delivery for ${CNB_TAG} (thread ${thread:-default})"

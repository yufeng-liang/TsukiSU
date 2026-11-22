#!/bin/sh
# Run this on each CI runner host (not inside a job).
# Serves the shared /ci/runs volume on the LAN through Docker's published port:
#
#   windows runner: 192.168.11.2:18080
#   laptop runner:  192.168.11.3:18080
#
# The directory is read-only; the container has no access to anything else.
set -eu

docker rm -f ci-artifacts >/dev/null 2>&1 || true
# Git Bash rewrites /ci/runs into a Windows path; keep container paths intact.
MSYS_NO_PATHCONV=1 docker run -d \
	--name ci-artifacts \
	--restart unless-stopped \
	-p 18080:18080 \
	-u 0:0 \
	-v forgejo-ci:/ci:ro \
	--entrypoint python3 \
	forgejo-ci/full:1 \
	-m http.server 18080 --directory /ci/runs

echo "serving /ci/runs on port 18080"

#!/usr/bin/env bash
# Split the old sukisu-ci image into smaller, reusable images WITHOUT network
# access, keeping the same OS: everything is derived from the image that is
# already on the host.
#
#   bash .github/image/build-images.sh [source-image] [tag]
#
#   forgejo-ci/base:<tag>     the old rootfs minus the big toolchain dirs
#   forgejo-ci/android:<tag>  base + Android SDK, NDK 27/29, cmake, Gradle, kpm
#   forgejo-ci/rust:<tag>     base + rustup/Cargo, cargo-ndk, git mirrors
#   forgejo-ci/full:<tag>     android + Rust (jobs that need both)
#
# ddk-min and the forgejo runner images are untouched.
set -euo pipefail

src="${1:-sukisu-ci:7}"
tag="${2:-1}"
here="$(cd "$(dirname "$0")" && pwd)"
win() { cygpath -m "$1"; }

# Keep the extracted trees: rebuilding or copying them to the other runner is
# then a local operation. Override with CTX_DIR=... to use another disk.
export MSYS2_ARG_CONV_EXCL='*'
ctx="${CTX_DIR:-E:/other/forgejo-ci/imgctx}"
mkdir -p "$ctx"
echo "== context: $ctx"

# ---------------------------------------------------------------- extraction
if [ ! -s "$ctx/rootfs.tar" ]; then
  echo "== exporting $src (several GB, one-off)"
  docker rm -f forgejo-ci-export >/dev/null 2>&1 || true
  docker create --name forgejo-ci-export "$src" >/dev/null
  docker export -o "$(win "$ctx")/rootfs.tar" forgejo-ci-export
  docker rm -f forgejo-ci-export >/dev/null
fi

if [ ! -s "$ctx/base.tar" ]; then
  # Carve inside a container: the Windows bind mount cannot hold Linux
  # symlinks, and the scratch copy has to be a Linux filesystem.
  echo "== carving the base rootfs (in a container)"
  docker run --rm -v "$(win "$ctx"):/out" --entrypoint bash "$src" -c '
    set -e
    rm -rf /scratch /out/base.tar
    mkdir -p /scratch
    tar -xf /out/rootfs.tar -C /scratch       --exclude=opt/android-sdk       --exclude=opt/gradle       --exclude=opt/gradle-9.7.1       --exclude=opt/kpm-tools       --exclude=opt/git-mirrors       --exclude=usr/local/rustup       --exclude=usr/local/cargo       --exclude=etc/gitconfig
    tar -C /scratch -cpf /out/base.tar .
    rm -rf /scratch
    du -sh /out/base.tar
  '
fi

if [ ! -s "$ctx/android.tar" ]; then
  echo "== extracting the Android/Gradle/kpm tree"
  docker run --rm -v "$(win "$ctx"):/out" --entrypoint sh "$src" -c '
    set -e
    tar -C / -cpf /out/android.tar \
      opt/android-sdk opt/gradle-9.7.1 opt/gradle opt/kpm-tools
    du -sh /out/android.tar
  '
fi

if [ ! -s "$ctx/rust.tar" ]; then
  echo "== extracting the Rust tree"
  docker run --rm -v "$(win "$ctx"):/out" --entrypoint sh "$src" -c '
    set -e
    tar -C / -cpf /out/rust.tar \
      usr/local/rustup usr/local/cargo opt/git-mirrors etc/gitconfig
    du -sh /out/rust.tar
  '
fi

# ---------------------------------------------------------------- base image
echo "== importing forgejo-ci/base:$tag"
docker import \
  --change 'ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' \
  --change 'ENV LANG=C.UTF-8' \
  --change 'ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' \
  --change 'CMD ["/bin/bash"]' \
  "$(win "$ctx")/base.tar" "forgejo-ci/base:$tag"

# ---------------------------------------------------------------- derived
echo "== building forgejo-ci/android:$tag"
docker build -f "$(win "$here")/Dockerfile.android" -t "forgejo-ci/android:$tag" "$(win "$ctx")"

echo "== building forgejo-ci/rust:$tag"
docker build -f "$(win "$here")/Dockerfile.rust" -t "forgejo-ci/rust:$tag" "$(win "$ctx")"

echo "== building forgejo-ci/full:$tag"
docker build -f "$(win "$here")/Dockerfile.full" -t "forgejo-ci/full:$tag" "$(win "$ctx")"

docker images --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep forgejo-ci

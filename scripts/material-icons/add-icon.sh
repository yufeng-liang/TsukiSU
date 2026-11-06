#!/usr/bin/env bash
# 用法: add-icon.sh <style> <IconName> [aar路径]
# 例:   add-icon.sh rounded CloudDownload
set -euo pipefail
STYLE="${1:?style, e.g. rounded}"
NAME="${2:?icon name, e.g. CloudDownload}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$ROOT/manager/app/libs/material-icons-custom.jar"
AAR="${3:-}"
if [ -z "$AAR" ]; then
  AAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/androidx.compose.material/material-icons-extended-android" -name 'material-icons-extended-release.aar' | head -1)"
fi
[ -n "$AAR" ] && [ -f "$AAR" ] || { echo "找不到 material-icons-extended AAR" >&2; exit 1; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
unzip -p "$AAR" classes.jar > "$TMP/extended.jar"
mkdir -p "$TMP/out"
(cd "$TMP/out" && unzip -o "$TMP/extended.jar" "androidx/compose/material/icons/$STYLE/${NAME}Kt.class" >/dev/null)
(cd "$TMP/out" && jar uf "$JAR" "androidx/compose/material/icons/$STYLE/${NAME}Kt.class" 2>/dev/null) || \
  (cp "$JAR" "$TMP/custom.jar" && cd "$TMP" && jar uf custom.jar -C out "androidx/compose/material/icons/$STYLE/${NAME}Kt.class" && cp custom.jar "$JAR")
echo "已加入 $STYLE/$NAME"

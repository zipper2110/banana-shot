#!/usr/bin/env bash
# Renders src/main/resources/icons/app-icon.svg into distribution/macos/assets/BananaShot.icns (B-13).
# The SVG is the single source of the icon. Run this script again after you change the SVG.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SVG="$ROOT/src/main/resources/icons/app-icon.svg"
OUT="$ROOT/distribution/macos/assets/BananaShot.icns"
WORK="$ROOT/target/macos-icon"

rm -rf "$WORK"
mkdir -p "$WORK" "$(dirname "$OUT")"
(cd "$ROOT" && mvn -q dependency:build-classpath -Dmdep.includeArtifactIds=jsvg "-Dmdep.outputFile=$WORK/classpath.txt")
java -Djava.awt.headless=true -cp "$(cat "$WORK/classpath.txt")" \
  "$ROOT/distribution/macos/MacIconRenderer.java" "$SVG" "$WORK/BananaShot.iconset"
iconutil -c icns "$WORK/BananaShot.iconset" -o "$OUT"
echo "The icon is in $OUT"

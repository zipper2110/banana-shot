#!/usr/bin/env bash
# Downloads the pinned macOS native files (libmpv and FFmpeg) of native-dependencies.json, checks
# their SHA-256, and puts them in target/native/macos-arm64 (B-13). It is the macOS copy of
# distribution/windows/Get-NativeDependencies.ps1.
#
# The files come from the natives release of this repository. build-natives.sh makes them.
# Usage: distribution/macos/get-native-dependencies.sh [manifest]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MANIFEST="${1:-$ROOT/distribution/macos/native-dependencies.json}"
OUT="$ROOT/target/native/macos-arm64"
CACHE="$ROOT/target/downloads"

# plutil is part of macOS and reads JSON.
value() { plutil -extract "$1" raw -o - "$MANIFEST"; }

if [[ "$(value architecture)" != "macos-arm64" ]]; then
  echo "The manifest is not for macos-arm64: $MANIFEST" >&2
  exit 1
fi

get_dependency() {
  local name="$1" archive url sha directory file
  archive="$(value "$name.archiveName")"
  url="$(value "$name.url")"
  sha="$(value "$name.sha256")"
  directory="$(value "$name.extractedDirectory")"
  [[ "$archive" != */* && "$directory" != */* && -n "$directory" ]] || {
    echo "Bad file name in the manifest: $name" >&2; exit 1
  }

  file="$CACHE/$archive"
  if [[ ! -f "$file" ]]; then
    echo "Downloading $archive"
    curl -fL --retry 3 --silent --show-error -o "$file.part" "$url"
    mv "$file.part" "$file"
  fi
  if ! echo "$sha  $file" | shasum -a 256 -c - >/dev/null; then
    echo "SHA-256 mismatch for $file. Expected $sha, got $(shasum -a 256 "$file" | cut -d' ' -f1)." >&2
    exit 1
  fi

  rm -rf "${OUT:?}/$directory"
  tar -xJf "$file" -C "$OUT" "$directory"
}

mkdir -p "$CACHE" "$OUT"
get_dependency mpv
get_dependency ffmpeg

for path in mpv/libmpv.dylib mpv/libmpv.2.dylib mpv/libMoltenVK.dylib ffmpeg/bin/ffmpeg ffmpeg/bin/ffprobe; do
  [[ -e "$OUT/$path" ]] || { echo "The native files are incomplete: $OUT/$path" >&2; exit 1; }
done
# The files have an ad-hoc signature. An Apple Silicon Mac does not run unsigned arm64 code.
codesign --verify "$OUT/mpv/libmpv.2.dylib" "$OUT/mpv/libMoltenVK.dylib" "$OUT/ffmpeg/bin/ffmpeg" \
  "$OUT/ffmpeg/bin/ffprobe"

cp "$MANIFEST" "$OUT/native-dependencies.json"
echo "The native files are in $OUT"

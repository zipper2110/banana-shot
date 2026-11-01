#!/usr/bin/env bash
# Builds FFmpeg for source runs on an Apple Silicon Mac (B-13).
#
# DEVELOPMENT ONLY. Do not ship this build. It links the libraries of Homebrew
# (libass, x264), so it runs only on a Mac with these Homebrew packages. The
# release needs a pinned, self-contained build ("2. Native files" in
# docs/macos/b-13-macos.md).
#
# Homebrew's own ffmpeg has no libass, so it has no "subtitles" filter. The
# export draws the scoreboard with that filter.
#
# Needs: brew install pkgconf libass x264
# Output: target/native/macos-arm64/ffmpeg/bin/ffmpeg and ffprobe. Source runs
# use this folder before the ffmpeg on the PATH.
set -euo pipefail

VERSION="9.0.2"
# The SHA-256 of https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz, read on
# 2026-11-01. ffmpeg.org publishes no SHA-256 file.
SHA256="8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/target/native/macos-arm64/ffmpeg"
WORK="$ROOT/target/native-build"
ARCHIVE="$WORK/ffmpeg-$VERSION.tar.xz"
SOURCE="$WORK/ffmpeg-$VERSION"

if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "arm64" ]]; then
  echo "This script is for an Apple Silicon Mac." >&2
  exit 1
fi
for package in pkgconf libass x264; do
  if ! brew list --versions "$package" >/dev/null 2>&1; then
    echo "Install the Homebrew package first: brew install $package" >&2
    exit 1
  fi
done

mkdir -p "$WORK"
if [[ ! -f "$ARCHIVE" ]]; then
  curl -fsSL -o "$ARCHIVE" "https://ffmpeg.org/releases/ffmpeg-$VERSION.tar.xz"
fi
echo "$SHA256  $ARCHIVE" | shasum -a 256 -c -

rm -rf "$SOURCE"
tar -xJf "$ARCHIVE" -C "$WORK"
cd "$SOURCE"

./configure \
  --prefix="$OUT" \
  --enable-gpl \
  --enable-version3 \
  --enable-libx264 \
  --enable-libass \
  --enable-videotoolbox \
  --enable-audiotoolbox \
  --disable-ffplay \
  --disable-doc \
  --disable-debug

make -j"$(sysctl -n hw.ncpu)"
rm -rf "$OUT"
make install

# The checks of the features that the export uses. Read the lists into variables first: with pipefail,
# "ffmpeg | grep -q" fails when grep stops early and ffmpeg gets SIGPIPE.
filters="$("$OUT/bin/ffmpeg" -hide_banner -filters)"
encoders="$("$OUT/bin/ffmpeg" -hide_banner -encoders)"
grep -qw "subtitles" <<<"$filters" || { echo "No subtitles filter." >&2; exit 1; }
grep -qw "libx264" <<<"$encoders" || { echo "No libx264 encoder." >&2; exit 1; }
grep -qw "h264_videotoolbox" <<<"$encoders" || { echo "No VideoToolbox encoder." >&2; exit 1; }
echo "FFmpeg $VERSION is in $OUT"

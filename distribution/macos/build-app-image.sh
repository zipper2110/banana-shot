#!/usr/bin/env bash
# Builds the macOS app bundle BananaShot.app with jpackage (B-13, step 8). It is the macOS copy of
# distribution/windows/Build-AppImage.ps1.
#
# Output: target/package/app-image/BananaShot.app
#   Contents/MacOS/BananaShot          the launcher of jpackage
#   Contents/app/                      the JAR files and BananaShot.cfg (the Java options)
#   Contents/runtime/                  the Java runtime (jlink), with bin/java for validate-app-image.sh
#   Contents/natives/macos-arm64/      mpv, ffmpeg (native-dependencies.json), and java/ (the native files of JNA
#                                      and FlatLaf)
#   Contents/legal/                    LICENSE, LICENSE-NOTICE, THIRD-PARTY-NOTICES
#
# Usage: distribution/macos/build-app-image.sh [options]
#   --version <x.y.z>                  The app version. The default is the version of pom.xml.
#   --skip-build                       Use target/distribution/input of an earlier build.
#   --sign <identity>                  The code signing identity. The default "-" is an ad-hoc signature. A bundle
#                                      with an ad-hoc signature opens only on the Mac that built it.
#   --feedback-endpoint <url>          The feedback Worker (B-8), https://<host>/v1/feedback.
#   --analytics-endpoint <url>         The analytics Worker (B-9), https://<host>/v1/session. Give the three
#   --analytics-privacy-url <url>      analytics options or none.
#   --analytics-notice-version <n>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET="$ROOT/target"
INPUT="$TARGET/distribution/input"
NATIVE="$TARGET/native/macos-arm64"
APP_IMAGE_ROOT="$TARGET/package/app-image"
APP="$APP_IMAGE_ROOT/BananaShot.app"
ICON="$ROOT/distribution/macos/assets/BananaShot.icns"
ENTITLEMENTS="$ROOT/distribution/macos/entitlements.plist"
BUNDLE_ID="app.banana-shot-editor.bananashot"
MINIMUM_MACOS="15.0"

VERSION=""
SKIP_BUILD=false
IDENTITY="-"
FEEDBACK_ENDPOINT=""
ANALYTICS_ENDPOINT=""
ANALYTICS_PRIVACY_URL=""
ANALYTICS_NOTICE_VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --sign) IDENTITY="$2"; shift 2 ;;
    --feedback-endpoint) FEEDBACK_ENDPOINT="$2"; shift 2 ;;
    --analytics-endpoint) ANALYTICS_ENDPOINT="$2"; shift 2 ;;
    --analytics-privacy-url) ANALYTICS_PRIVACY_URL="$2"; shift 2 ;;
    --analytics-notice-version) ANALYTICS_NOTICE_VERSION="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

fail() { echo "$*" >&2; exit 1; }

# --- Checks ------------------------------------------------------------------------------------
[[ "$(uname -s)" == "Darwin" && "$(uname -m)" == "arm64" ]] || fail "The macOS app bundle must be built on an Apple Silicon Mac."
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || true)}"
[[ -x "$JAVA_HOME/bin/jpackage" ]] || fail "jpackage was not found. Install JDK 25 and set JAVA_HOME."
JAVA_FEATURE="$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$JAVA_HOME/release")"
[[ -n "$JAVA_FEATURE" && "$JAVA_FEATURE" -ge 25 ]] || fail "JAVA_HOME must point to JDK 25 or later; found $JAVA_FEATURE."
if [[ "$JAVA_FEATURE" != "25" ]]; then
  echo "WARNING: JDK $JAVA_FEATURE. The release uses JDK 25." >&2
fi

if [[ -z "$VERSION" ]]; then
  VERSION="$(cd "$ROOT" && mvn -q -Dexpression=project.version -DforceStdout help:evaluate | tail -1)"
fi
PACKAGE_VERSION="${VERSION#v}"
PACKAGE_VERSION="${PACKAGE_VERSION%%-*}"
[[ "$PACKAGE_VERSION" =~ ^[1-9][0-9]*(\.[0-9]+){0,2}$ ]] \
  || fail "jpackage on macOS needs a version 1.0 or later with up to three numbers; got '$PACKAGE_VERSION'."

# The same rules as FeedbackBuildConfig.endpoint and AnalyticsBuildConfig.fromProperties in the app.
# validate-app-image.sh has the same values.
EXPECTED_NOTICE_VERSION="3"
if [[ -n "$FEEDBACK_ENDPOINT" && ! "$FEEDBACK_ENDPOINT" =~ ^https://[^/@?#[:space:]]+(/[^?#[:space:]]*)?/v1/feedback$ ]]; then
  fail "--feedback-endpoint must be an https URL that ends in /v1/feedback; got '$FEEDBACK_ENDPOINT'."
fi
ANALYTICS_COUNT=0
for value in "$ANALYTICS_ENDPOINT" "$ANALYTICS_PRIVACY_URL" "$ANALYTICS_NOTICE_VERSION"; do
  [[ -n "$value" ]] && ANALYTICS_COUNT=$((ANALYTICS_COUNT + 1))
done
if [[ "$ANALYTICS_COUNT" -ne 0 && "$ANALYTICS_COUNT" -ne 3 ]]; then
  fail "Give all three analytics options, or none."
fi
if [[ "$ANALYTICS_COUNT" -eq 3 ]]; then
  [[ "$ANALYTICS_ENDPOINT" =~ ^https://[^/@?#[:space:]]+/v1/session$ ]] \
    || fail "--analytics-endpoint must be an https URL with the path /v1/session; got '$ANALYTICS_ENDPOINT'."
  [[ "$ANALYTICS_PRIVACY_URL" =~ ^https://[^/@?#[:space:]]+(/[^?#[:space:]]*)?$ ]] \
    || fail "--analytics-privacy-url must be an https URL with no query; got '$ANALYTICS_PRIVACY_URL'."
  [[ "$ANALYTICS_NOTICE_VERSION" == "$EXPECTED_NOTICE_VERSION" ]] \
    || fail "--analytics-notice-version must be $EXPECTED_NOTICE_VERSION, the notice version of the app."
fi

[[ -e "$NATIVE/mpv/libmpv.dylib" && -x "$NATIVE/ffmpeg/bin/ffmpeg" ]] \
  || fail "The native files are missing. Run distribution/macos/get-native-dependencies.sh."
[[ -f "$ICON" ]] || fail "The icon is missing: $ICON. Run distribution/macos/make-icon.sh."

# --- Maven build -------------------------------------------------------------------------------
if ! $SKIP_BUILD; then
  rm -rf "$INPUT"
  (cd "$ROOT" && mvn -B -Pdistribution -DskipTests "-Drevision=$PACKAGE_VERSION" package)
fi
MAIN_JAR="$(cd "$INPUT" && ls -t bananashot-*.jar 2>/dev/null | grep -v sources | head -1 || true)"
[[ -n "$MAIN_JAR" ]] || fail "The application JAR was not found in $INPUT."

# --- jpackage ----------------------------------------------------------------------------------
# The modules of the bundled runtime: the list of Build-AppImage.ps1 without jdk.crypto.mscapi (Windows only).
# On macOS, the KeychainStore of "Trust for the HTTPS connection" is in java.base.
RUNTIME_MODULES="java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.sql,java.xml,\
jdk.accessibility,jdk.charsets,jdk.localedata"

rm -rf "$APP_IMAGE_ROOT"
mkdir -p "$APP_IMAGE_ROOT"

# $APPDIR is Contents/app. JNA and FlatLaf load their native files from natives/macos-arm64/java, not from a
# copy in a temporary folder: the hardened runtime refuses a library that our certificate did not sign.
JAVA_NATIVES='$APPDIR/../natives/macos-arm64/java'
ARGS=(
  --type app-image
  --name BananaShot
  --dest "$APP_IMAGE_ROOT"
  --input "$INPUT"
  --main-jar "$MAIN_JAR"
  --main-class org.litvin.SwingMainApp
  --app-version "$PACKAGE_VERSION"
  --vendor BananaShot
  --description "Turn tennis match recordings into compact scored videos."
  --copyright "Copyright (c) 2026 BananaShot"
  --icon "$ICON"
  --mac-package-identifier "$BUNDLE_ID"
  --mac-package-name BananaShot
  --mac-app-category public.app-category.video
  --add-modules "$RUNTIME_MODULES"
  --java-options -Dfile.encoding=UTF-8
  --java-options --enable-native-access=ALL-UNNAMED
  --java-options "-Djna.boot.library.path=$JAVA_NATIVES"
  --java-options -Djna.nounpack=true
  --java-options "-Dflatlaf.nativeLibraryPath=$JAVA_NATIVES"
)
if [[ -n "$FEEDBACK_ENDPOINT" ]]; then
  ARGS+=(--java-options "-Dbananashot.feedback.endpoint=$FEEDBACK_ENDPOINT")
else
  echo "WARNING: no --feedback-endpoint. The feedback form of this build cannot send reports." >&2
fi
if [[ "$ANALYTICS_COUNT" -eq 3 ]]; then
  ARGS+=(
    --java-options "-Dbananashot.analytics.endpoint=$ANALYTICS_ENDPOINT"
    --java-options "-Dbananashot.analytics.privacyUrl=$ANALYTICS_PRIVACY_URL"
    --java-options "-Dbananashot.analytics.noticeVersion=$ANALYTICS_NOTICE_VERSION"
  )
else
  echo "WARNING: no analytics options. This build cannot send usage analytics." >&2
fi
"$JAVA_HOME/bin/jpackage" "${ARGS[@]}"
[[ -d "$APP" ]] || fail "jpackage made no app bundle."
CONTENTS="$APP/Contents"

# --- Files of the bundle -----------------------------------------------------------------------
# validate-app-image.sh runs the build information with the bundled runtime, as on Windows.
cp "$JAVA_HOME/bin/java" "$CONTENTS/runtime/Contents/Home/bin/java" 2>/dev/null || {
  mkdir -p "$CONTENTS/runtime/Contents/Home/bin"
  cp "$JAVA_HOME/bin/java" "$CONTENTS/runtime/Contents/Home/bin/java"
}

mkdir -p "$CONTENTS/natives"
# ditto keeps the libmpv.dylib link.
ditto "$NATIVE" "$CONTENTS/natives/macos-arm64"
rm -f "$CONTENTS/natives/macos-arm64/native-dependencies.json"

# The arm64 native files of JNA and FlatLaf. The JAR files in the bundle then have no macOS native files:
# notarization refuses a JAR with a native file that our certificate did not sign.
JAVA_NATIVE_DIR="$CONTENTS/natives/macos-arm64/java"
mkdir -p "$JAVA_NATIVE_DIR"
JNA_JAR="$(ls "$CONTENTS/app"/jna-[0-9]*.jar)"
FLATLAF_JAR="$(ls "$CONTENTS/app"/flatlaf-[0-9]*.jar)"
unzip -q -j -o "$JNA_JAR" com/sun/jna/darwin-aarch64/libjnidispatch.jnilib -d "$JAVA_NATIVE_DIR"
unzip -q -j -o "$FLATLAF_JAR" com/formdev/flatlaf/natives/libflatlaf-macos-arm64.dylib -d "$JAVA_NATIVE_DIR"
for jar in "$CONTENTS/app"/*.jar; do
  entries="$(unzip -Z1 "$jar" | grep -E '\.(dylib|jnilib)$' || true)"
  if [[ -n "$entries" ]]; then
    # shellcheck disable=SC2086
    zip -q -d "$jar" $entries
  fi
done

mkdir -p "$CONTENTS/legal"
cp "$ROOT/LICENSE" "$CONTENTS/legal/LICENSE.txt"
cp "$ROOT/LICENSE-NOTICE" "$CONTENTS/legal/LICENSE-NOTICE.txt"
cp "$ROOT/distribution/THIRD-PARTY-NOTICES.txt" "$CONTENTS/legal/THIRD-PARTY-NOTICES.txt"

# --- Info.plist --------------------------------------------------------------------------------
PLIST="$CONTENTS/Info.plist"
plist_set() {
  plutil -remove "$1" "$PLIST" 2>/dev/null || true
  plutil -insert "$1" "$2" "$3" "$PLIST"
}
plist_set LSMinimumSystemVersion -string "$MINIMUM_MACOS"
plist_set LSArchitecturePriority -json '["arm64"]'
# One entry for each AppLanguage. Without it, the native dialogs of macOS stay in English (B-11 E4-T13).
plist_set CFBundleLocalizations -json '["en","es","es-419","ru","pt-BR","pt-PT","fr","de","ja","zh-Hans","zh-Hant"]'
plist_set CFBundleDevelopmentRegion -string en
plist_set NSHighResolutionCapable -bool true
plutil -lint "$PLIST" >/dev/null

# --- Code signature ----------------------------------------------------------------------------
# Sign from the inside to the outside. The hardened runtime is for a Developer ID signature. With an ad-hoc
# signature, the hardened runtime refuses the libraries, because they have no Team ID.
SIGN_OPTIONS=(--force --timestamp=none --sign "$IDENTITY")
if [[ "$IDENTITY" != "-" ]]; then
  SIGN_OPTIONS=(--force --timestamp --options runtime --sign "$IDENTITY")
fi
is_macho() { file -b "$1" | grep -q "Mach-O"; }

# 1. Each Mach-O file except the launcher: the runtime, the native files, ffmpeg, and ffprobe.
while IFS= read -r -d '' file; do
  [[ "$file" == "$CONTENTS/MacOS/BananaShot" ]] && continue
  if is_macho "$file"; then
    codesign "${SIGN_OPTIONS[@]}" "$file"
  fi
done < <(find "$CONTENTS" -type f -print0)
# 2. The runtime is a bundle of its own.
codesign "${SIGN_OPTIONS[@]}" "$CONTENTS/runtime"
# 3. The app bundle and its launcher, with the entitlements of the JVM.
codesign "${SIGN_OPTIONS[@]}" --entitlements "$ENTITLEMENTS" "$APP"
codesign --verify --deep --strict "$APP"

echo "The app bundle is ready: $APP"
echo "Signature: $( [[ "$IDENTITY" == "-" ]] && echo "ad-hoc (opens only on this Mac)" || echo "$IDENTITY")"

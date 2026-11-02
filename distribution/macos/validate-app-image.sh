#!/usr/bin/env bash
# Checks the macOS app bundle of build-app-image.sh (B-13, step 8). It is the macOS copy of
# distribution/windows/Validate-AppImage.ps1, with the macOS checks of the bundle.
#
# The checks of the build information are the same as on Windows (see "Build expiry" in
# docs/licensing/build-expiry-spec.md). Do not put them in the packaged diagnostics: users also run the
# diagnostics, and the check of the build date fails for them 7 days after the build.
#
# Usage: distribution/macos/validate-app-image.sh --version <x.y.z> [--app <BananaShot.app>]
#          [--allow-no-feedback-endpoint] [--allow-no-analytics]
# The two --allow options are only for a local build. The release workflow never uses them.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/target/package/app-image/BananaShot.app"
VERSION=""
ALLOW_NO_FEEDBACK=false
ALLOW_NO_ANALYTICS=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --app) APP="$2"; shift 2 ;;
    --allow-no-feedback-endpoint) ALLOW_NO_FEEDBACK=true; shift ;;
    --allow-no-analytics) ALLOW_NO_ANALYTICS=true; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done
fail() { echo "$*" >&2; exit 1; }
[[ -n "$VERSION" ]] || fail "Give --version."

PACKAGE_VERSION="${VERSION#v}"
PACKAGE_VERSION="${PACKAGE_VERSION%%-*}"
CONTENTS="$APP/Contents"
HOME_DIR="$CONTENTS/runtime/Contents/Home"
JAVA="$HOME_DIR/bin/java"
[[ -x "$JAVA" ]] || fail "The bundled java was not found: $JAVA"

# --- Build information -------------------------------------------------------------------------
OUTPUT="$("$JAVA" -cp "$CONTENTS/app/*" org.litvin.license.BuildInfoPrinter 2>&1)" \
  || fail "BuildInfoPrinter did not run:"$'\n'"$OUTPUT"
value() { sed -n "s/^$1=//p" <<<"$OUTPUT" | head -1 | tr -d '[:space:]'; }
BUILD_DATE="$(value buildDate)"
EXPIRY_DATE="$(value expiryDate)"
APP_VERSION="$(value version)"
for pair in "buildDate:$BUILD_DATE" "expiryDate:$EXPIRY_DATE" "version:$APP_VERSION"; do
  [[ -n "${pair#*:}" ]] || fail "BuildInfoPrinter printed no ${pair%%:*}. Output:"$'\n'"$OUTPUT"
done

# The dates with Python: the same calendar rules as LocalDate.plusMonths (the last day of the month when the day
# does not exist).
python3 -I - "$BUILD_DATE" "$EXPIRY_DATE" <<'PY' || exit 1
import calendar, datetime, sys
build = datetime.date.fromisoformat(sys.argv[1])
expiry = datetime.date.fromisoformat(sys.argv[2])
month = build.month - 1 + 6
year, month = build.year + month // 12, month % 12 + 1
expected = datetime.date(year, month, min(build.day, calendar.monthrange(year, month)[1]))
today = datetime.datetime.now(datetime.timezone.utc).date()
if expiry != expected:
    sys.exit(f"The expiry date {expiry} is not 6 calendar months after the build date {build} (expected {expected}).")
if build < today - datetime.timedelta(days=7):
    sys.exit(f"The build date {build} is more than 7 days before today ({today} UTC). Build the app again.")
# The 1 day covers the time zones.
if build > today + datetime.timedelta(days=1):
    sys.exit(f"The build date {build} is later than today ({today} UTC) plus 1 day.")
PY
[[ "$APP_VERSION" == "$PACKAGE_VERSION" ]] \
  || fail "The app version $APP_VERSION is not the jpackage --app-version $PACKAGE_VERSION."

# --- Runtime -----------------------------------------------------------------------------------
JAVA_FEATURE="$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$HOME_DIR/release")"
[[ -n "$JAVA_FEATURE" ]] || fail "The runtime release file has no valid JAVA_VERSION."
MODULES="$("$JAVA" --list-modules | sed 's/@.*//')"
# java.net.http: the request of the rules file. On macOS, the KeychainStore is in java.base.
for module in java.base java.net.http java.desktop; do
  grep -qx "$module" <<<"$MODULES" || fail "The bundled runtime (JDK $JAVA_FEATURE) has no module $module."
done
if grep -qx "jdk.crypto.mscapi" <<<"$MODULES"; then
  fail "The macOS runtime must not contain jdk.crypto.mscapi."
fi

# --- Launcher options --------------------------------------------------------------------------
CFG="$CONTENTS/app/BananaShot.cfg"
[[ -f "$CFG" ]] || fail "The launcher configuration was not found: $CFG"
property() { sed -n "s/^java-options=-D$1=//p" "$CFG" | head -1; }
for option in "jna.nounpack=true" 'jna.boot.library.path=$APPDIR/../natives/macos-arm64/java' \
  'flatlaf.nativeLibraryPath=$APPDIR/../natives/macos-arm64/java'; do
  grep -qxF "java-options=-D$option" "$CFG" || fail "The launcher has no option -D$option."
done
FEEDBACK="$(property bananashot.feedback.endpoint)"
if [[ -n "$FEEDBACK" ]]; then
  [[ "$FEEDBACK" =~ ^https://[^/@?#[:space:]]+(/[^?#[:space:]]*)?/v1/feedback$ ]] \
    || fail "The feedback endpoint '$FEEDBACK' is not an https URL that ends in /v1/feedback."
elif ! $ALLOW_NO_FEEDBACK; then
  fail "The app bundle has no feedback endpoint. Give --feedback-endpoint to build-app-image.sh."
fi
EXPECTED_NOTICE_VERSION="3"
ANALYTICS_ENDPOINT="$(property bananashot.analytics.endpoint)"
ANALYTICS_PRIVACY_URL="$(property bananashot.analytics.privacyUrl)"
ANALYTICS_NOTICE="$(property bananashot.analytics.noticeVersion)"
if [[ -z "$ANALYTICS_ENDPOINT$ANALYTICS_PRIVACY_URL$ANALYTICS_NOTICE" ]]; then
  $ALLOW_NO_ANALYTICS || fail "The app bundle has no analytics properties. Give the analytics options to build-app-image.sh."
else
  [[ "$ANALYTICS_ENDPOINT" =~ ^https://[^/@?#[:space:]]+/v1/session$ ]] \
    || fail "The analytics endpoint '$ANALYTICS_ENDPOINT' is not an https URL with the path /v1/session."
  [[ "$ANALYTICS_PRIVACY_URL" =~ ^https://[^/@?#[:space:]]+(/[^?#[:space:]]*)?$ ]] \
    || fail "The analytics privacy URL '$ANALYTICS_PRIVACY_URL' is not an https URL with no query."
  [[ "$ANALYTICS_NOTICE" == "$EXPECTED_NOTICE_VERSION" ]] \
    || fail "The analytics notice version '$ANALYTICS_NOTICE' is not $EXPECTED_NOTICE_VERSION."
fi

# --- Info.plist --------------------------------------------------------------------------------
PLIST="$CONTENTS/Info.plist"
plist() { plutil -extract "$1" raw -o - "$PLIST" 2>/dev/null || true; }
[[ "$(plist CFBundleIdentifier)" == "app.banana-shot-editor.bananashot" ]] || fail "Wrong CFBundleIdentifier."
[[ "$(plist CFBundleShortVersionString)" == "$PACKAGE_VERSION" ]] || fail "Wrong CFBundleShortVersionString."
[[ "$(plist LSMinimumSystemVersion)" == "15.0" ]] || fail "LSMinimumSystemVersion must be 15.0."
[[ "$(plist LSArchitecturePriority.0)" == "arm64" ]] || fail "LSArchitecturePriority must be arm64."
LOCALIZATIONS="$(plutil -extract CFBundleLocalizations json -o - "$PLIST")"
for language in en es es-419 ru pt-BR pt-PT fr de ja zh-Hans zh-Hant; do
  grep -q "\"$language\"" <<<"$LOCALIZATIONS" || fail "CFBundleLocalizations has no $language."
done

# --- Native files and JAR files ----------------------------------------------------------------
NATIVES="$CONTENTS/natives/macos-arm64"
for path in mpv/libmpv.dylib mpv/libmpv.2.dylib mpv/libMoltenVK.dylib ffmpeg/bin/ffmpeg ffmpeg/bin/ffprobe \
  java/libjnidispatch.jnilib java/libflatlaf-macos-arm64.dylib; do
  [[ -e "$NATIVES/$path" ]] || fail "The native file is missing: $NATIVES/$path"
done
for jar in "$CONTENTS/app"/*.jar; do
  if unzip -Z1 "$jar" | grep -qE '\.(dylib|jnilib)$'; then
    fail "The JAR has a macOS native file. Notarization refuses it: $jar"
  fi
done
for file in LICENSE.txt LICENSE-NOTICE.txt THIRD-PARTY-NOTICES.txt; do
  [[ -f "$CONTENTS/legal/$file" ]] || fail "The legal file is missing: $file"
done

# --- Code signature ----------------------------------------------------------------------------
UNSIGNED=""
while IFS= read -r -d '' file; do
  if file -b "$file" | grep -q "Mach-O"; then
    [[ "$(lipo -archs "$file")" == "arm64" ]] || fail "Not arm64 only: $file"
    codesign --verify --strict "$file" 2>/dev/null || UNSIGNED+="$file"$'\n'
  fi
done < <(find "$CONTENTS" -type f -print0)
[[ -z "$UNSIGNED" ]] || fail "These files have no valid signature:"$'\n'"$UNSIGNED"
codesign --verify --deep --strict "$APP" || fail "The signature of the app bundle is not valid."
SIGNATURE="$(codesign -dvv "$APP" 2>&1 | sed -n 's/^Authority=//p' | head -1)"

echo "Build date: $BUILD_DATE UTC"
echo "Expiry date: $EXPIRY_DATE"
echo "Version: $APP_VERSION"
echo "Runtime: JDK $JAVA_FEATURE"
echo "Feedback endpoint: ${FEEDBACK:-none (local build)}"
echo "Analytics: ${ANALYTICS_ENDPOINT:-none (local build)}"
echo "Signature: ${SIGNATURE:-ad-hoc}"
echo "The app bundle is valid."

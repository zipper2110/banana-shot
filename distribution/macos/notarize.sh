#!/usr/bin/env bash
# Notarizes and staples the macOS app bundle (B-13, step 11). Sign the bundle with a Developer ID first:
#   distribution/macos/build-app-image.sh --sign <identity>
#
# The login for the notarization service comes from one of these:
# - A keychain profile on a development Mac. Make it one time; the command asks for nothing secret in a file:
#     xcrun notarytool store-credentials bananashot-notary --key <AuthKey_ID.p8> --key-id <key ID> --issuer <issuer ID>
#   Then run: distribution/macos/notarize.sh --keychain-profile bananashot-notary
# - The environment variables of the release workflow: APPLE_API_KEY_P8 (the text of the .p8 file),
#   APPLE_API_KEY_ID, and APPLE_API_ISSUER_ID. The script writes the key to a temporary file and deletes it.
#
# Usage: distribution/macos/notarize.sh [--keychain-profile <name>] [--app <BananaShot.app>]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/target/package/app-image/BananaShot.app"
PROFILE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keychain-profile) PROFILE="$2"; shift 2 ;;
    --app) APP="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done
fail() { echo "$*" >&2; exit 1; }

[[ -d "$APP" ]] || fail "The app bundle was not found: $APP"
codesign --verify --deep --strict "$APP" || fail "The signature of the app bundle is not valid."
# Read the text first: with pipefail, "codesign | grep -q" fails when grep stops early and codesign gets SIGPIPE.
SIGNATURE="$(codesign -dvv "$APP" 2>&1)"
grep -q "^Authority=Developer ID Application" <<<"$SIGNATURE" \
  || fail "The app bundle has no Developer ID signature. Build it with build-app-image.sh --sign <identity>."

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

if [[ -n "$PROFILE" ]]; then
  AUTH=(--keychain-profile "$PROFILE")
else
  [[ -n "${APPLE_API_KEY_P8:-}" && -n "${APPLE_API_KEY_ID:-}" && -n "${APPLE_API_ISSUER_ID:-}" ]] \
    || fail "Give --keychain-profile, or set APPLE_API_KEY_P8, APPLE_API_KEY_ID, and APPLE_API_ISSUER_ID."
  KEY_FILE="$WORK/AuthKey_$APPLE_API_KEY_ID.p8"
  (umask 077 && printf '%s\n' "$APPLE_API_KEY_P8" >"$KEY_FILE")
  AUTH=(--key "$KEY_FILE" --key-id "$APPLE_API_KEY_ID" --issuer "$APPLE_API_ISSUER_ID")
fi

# notarytool takes a zip, a dmg, or a pkg. ditto keeps the links and the extended attributes of the bundle.
ZIP="$WORK/BananaShot.zip"
ditto -c -k --keepParent "$APP" "$ZIP"

echo "Sending $(basename "$APP") to the Apple notary service. This usually takes some minutes."
RESULT="$WORK/result.json"
xcrun notarytool submit "$ZIP" "${AUTH[@]}" --wait --timeout 1h --output-format json >"$RESULT" || true
STATUS="$(plutil -extract status raw -o - "$RESULT" 2>/dev/null || true)"
ID="$(plutil -extract id raw -o - "$RESULT" 2>/dev/null || true)"
echo "Notarization $ID: ${STATUS:-no status}"
if [[ -z "$STATUS" && -n "$ID" ]] && grep -q "Timeout" "$RESULT"; then
  # The first submission of a new team can take hours. The check continues at Apple.
  echo "Apple is still checking the app bundle. Wait for the result with:" >&2
  echo "  xcrun notarytool wait $ID ${AUTH[*]}" >&2
  echo "Then staple the ticket: xcrun stapler staple \"$APP\"" >&2
  exit 2
fi
if [[ "$STATUS" != "Accepted" ]]; then
  cat "$RESULT" >&2
  if [[ -n "$ID" ]]; then
    echo "The log of the notary service:" >&2
    xcrun notarytool log "$ID" "${AUTH[@]}" >&2 || true
  fi
  fail "Apple did not accept the app bundle."
fi

# The ticket in the bundle lets Gatekeeper accept the app also with no network connection.
xcrun stapler staple "$APP"
xcrun stapler validate "$APP"
spctl --assess --type execute -vv "$APP"
echo "The app bundle is notarized and stapled: $APP"

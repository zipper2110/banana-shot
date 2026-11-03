#!/usr/bin/env bash
# Notarizes and staples the macOS app bundle or the installer (B-13, step 11). Sign the file with a Developer ID
# first: build-app-image.sh --sign <identity> for the bundle, build-velopack-release.sh for the .pkg.
#
# The login for the notarization service comes from one of these:
# - A keychain profile. Make it one time on a development Mac; the command asks for nothing secret in a file:
#     xcrun notarytool store-credentials bananashot-notary --key <AuthKey_ID.p8> --key-id <key ID> --issuer <issuer ID>
#   Then run: distribution/macos/notarize.sh --keychain-profile bananashot-notary
#   The release workflow keeps the profile in a temporary keychain: give it with --keychain.
# - The environment variables APPLE_API_KEY_P8 (the text of the .p8 file), APPLE_API_KEY_ID, and
#   APPLE_API_ISSUER_ID. The script writes the key to a temporary file and deletes it.
#
# The staple puts the ticket into the file, so that Gatekeeper accepts it also with no network connection. It asks
# the ticket service of Apple (CloudKit). On the GitHub macOS runner, that request failed each time with "The network
# connection was lost" (2026-11-03). Without the staple, Gatekeeper gets the ticket online at the first start.
#
# Usage: distribution/macos/notarize.sh [--keychain-profile <name>] [--keychain <path>]
#          [--app <BananaShot.app> | --pkg <BananaShot-osx-Setup.pkg>] [--staple-tries <n>] [--allow-unstapled]
# --allow-unstapled: when all staple tries fail, warn and continue if Gatekeeper accepts the file online.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/target/package/app-image/BananaShot.app"
PKG=""
PROFILE=""
KEYCHAIN=""
STAPLE_TRIES=1
ALLOW_UNSTAPLED=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keychain-profile) PROFILE="$2"; shift 2 ;;
    --keychain) KEYCHAIN="$2"; shift 2 ;;
    --app) APP="$2"; shift 2 ;;
    --pkg) PKG="$2"; shift 2 ;;
    --staple-tries) STAPLE_TRIES="$2"; shift 2 ;;
    --allow-unstapled) ALLOW_UNSTAPLED=true; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done
fail() { echo "$*" >&2; exit 1; }

if [[ -n "$PKG" ]]; then
  TARGET="$PKG"
  [[ -f "$PKG" ]] || fail "The installer was not found: $PKG"
  # Read the text first: with pipefail, "cmd | grep -q" fails when grep stops early and cmd gets SIGPIPE.
  PKG_SIGNATURE="$(pkgutil --check-signature "$PKG" 2>&1)" || fail "The installer has no valid signature."
  grep -q "Developer ID Installer" <<<"$PKG_SIGNATURE" || fail "The installer has no Developer ID Installer signature."
  SPCTL_TYPE=install
else
  TARGET="$APP"
  [[ -d "$APP" ]] || fail "The app bundle was not found: $APP"
  codesign --verify --deep --strict "$APP" || fail "The signature of the app bundle is not valid."
  SIGNATURE="$(codesign -dvv "$APP" 2>&1)"
  grep -q "^Authority=Developer ID Application" <<<"$SIGNATURE" \
    || fail "The app bundle has no Developer ID signature. Build it with build-app-image.sh --sign <identity>."
  SPCTL_TYPE=execute
fi

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

if [[ -n "$PROFILE" ]]; then
  AUTH=(--keychain-profile "$PROFILE")
  [[ -n "$KEYCHAIN" ]] && AUTH+=(--keychain "$KEYCHAIN")
else
  [[ -n "${APPLE_API_KEY_P8:-}" && -n "${APPLE_API_KEY_ID:-}" && -n "${APPLE_API_ISSUER_ID:-}" ]] \
    || fail "Give --keychain-profile, or set APPLE_API_KEY_P8, APPLE_API_KEY_ID, and APPLE_API_ISSUER_ID."
  KEY_FILE="$WORK/AuthKey_$APPLE_API_KEY_ID.p8"
  (umask 077 && printf '%s\n' "$APPLE_API_KEY_P8" >"$KEY_FILE")
  AUTH=(--key "$KEY_FILE" --key-id "$APPLE_API_KEY_ID" --issuer "$APPLE_API_ISSUER_ID")
fi

# notarytool takes a zip, a dmg, or a pkg. ditto keeps the links and the extended attributes of the bundle.
if [[ -n "$PKG" ]]; then
  UPLOAD="$PKG"
else
  UPLOAD="$WORK/BananaShot.zip"
  ditto -c -k --keepParent "$APP" "$UPLOAD"
fi

echo "Sending $(basename "$TARGET") to the Apple notary service. This usually takes some minutes."
RESULT="$WORK/result.json"
xcrun notarytool submit "$UPLOAD" "${AUTH[@]}" --wait --timeout 1h --output-format json >"$RESULT" || true
STATUS="$(plutil -extract status raw -o - "$RESULT" 2>/dev/null || true)"
ID="$(plutil -extract id raw -o - "$RESULT" 2>/dev/null || true)"
echo "Notarization $ID: ${STATUS:-no status}"
if [[ -z "$STATUS" && -n "$ID" ]] && grep -q "Timeout" "$RESULT"; then
  # The first submission of a new team can take hours. The check continues at Apple.
  echo "Apple is still checking $(basename "$TARGET"). Wait for the result with:" >&2
  echo "  xcrun notarytool wait $ID ${AUTH[*]}" >&2
  echo "Then staple the ticket: xcrun stapler staple \"$TARGET\"" >&2
  exit 2
fi
if [[ "$STATUS" != "Accepted" ]]; then
  cat "$RESULT" >&2
  if [[ -n "$ID" ]]; then
    echo "The log of the notary service:" >&2
    xcrun notarytool log "$ID" "${AUTH[@]}" >&2 || true
  fi
  fail "Apple did not accept $(basename "$TARGET")."
fi

stapled=false
for ((try = 1; try <= STAPLE_TRIES; try++)); do
  if xcrun stapler staple "$TARGET"; then
    stapled=true
    break
  fi
  echo "The staple failed (try $try of $STAPLE_TRIES)." >&2
  [[ $try -lt $STAPLE_TRIES ]] && sleep 30
done
if $stapled; then
  xcrun stapler validate "$TARGET"
elif $ALLOW_UNSTAPLED; then
  echo "WARNING: $(basename "$TARGET") is notarized but not stapled. Gatekeeper gets the ticket online." >&2
else
  fail "The staple failed. Try again later with: xcrun stapler staple \"$TARGET\""
fi
# Without a staple, spctl asks Apple online for the ticket.
spctl --assess --type "$SPCTL_TYPE" -vv "$TARGET"
echo "$(basename "$TARGET") is notarized$($stapled && echo " and stapled" || echo " (not stapled)")."

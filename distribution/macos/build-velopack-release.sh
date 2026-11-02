#!/usr/bin/env bash
# Makes the macOS installer and the update package from the app bundle of build-app-image.sh with Velopack
# (B-13, step 11). It is the macOS copy of distribution/windows/Build-VelopackRelease.ps1.
#
# Build the bundle with a Developer ID first: build-app-image.sh --sign <identity>. vpk keeps our signatures
# (--signDisableDeep). It adds its update program (Contents/MacOS/UpdateMac), signs it and the bundle again with
# the Developer ID Application certificate, and signs the .pkg with the Developer ID Installer certificate.
#
# Output in target/package/velopack:
# - BananaShot-osx-Setup.pkg: the installer. The name is the same in each release, because "Update and restart"
#   downloads releases/latest/download/BananaShot-osx-Setup.pkg. The user can install for all users
#   (/Applications) or only for the current user (~/Applications, no administrator rights).
# - BananaShot-osx-Portable.zip: the app bundle as a zip.
# - BananaShot-<version>-osx-full.nupkg: the update package. releases.osx.json: the release feed.
#
# Usage: distribution/macos/build-velopack-release.sh --version <x.y.z> --team-id <ID>
#          [--notary-profile <name>] [--keychain <path>]
# With --notary-profile, vpk notarizes and staples the bundle and the .pkg (see notarize.sh for the profile).
# --keychain is the keychain with the certificates (the release workflow uses a temporary keychain).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$ROOT/target/package/app-image/BananaShot.app"
OUT="$ROOT/target/package/velopack"
VPK="${VPK:-vpk}"
VERSION=""
TEAM_ID=""
NOTARY_PROFILE=""
KEYCHAIN=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --team-id) TEAM_ID="$2"; shift 2 ;;
    --notary-profile) NOTARY_PROFILE="$2"; shift 2 ;;
    --keychain) KEYCHAIN="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done
fail() { echo "$*" >&2; exit 1; }

PACKAGE_VERSION="${VERSION#v}"
PACKAGE_VERSION="${PACKAGE_VERSION%%-*}"
[[ "$PACKAGE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Velopack needs a MAJOR.MINOR.PATCH version; got '$VERSION'."
[[ "$TEAM_ID" =~ ^[A-Z0-9]{10}$ ]] || fail "Give --team-id, the 10 characters of the Apple Team ID."
[[ -d "$APP" ]] || fail "The app bundle is missing: $APP. Run build-app-image.sh first."
command -v "$VPK" >/dev/null || fail "vpk was not found. Install the version of windows-release.yml: dotnet tool install --global vpk --version <version>"
SIGNATURE="$(codesign -dvv "$APP" 2>&1)"
grep -q "^TeamIdentifier=$TEAM_ID$" <<<"$SIGNATURE" \
  || fail "The app bundle has no signature of the team $TEAM_ID. Build it with build-app-image.sh --sign <identity>."
[[ "$(plutil -extract CFBundleShortVersionString raw -o - "$APP/Contents/Info.plist")" == "$PACKAGE_VERSION" ]] \
  || fail "The app bundle is not version $PACKAGE_VERSION."

# The subject names of the two certificates. vpk gives them to codesign and productsign.
IDENTITY_NAME="$(sed -n "s/^Authority=\(Developer ID Application: .*($TEAM_ID)\)$/\1/p" <<<"$SIGNATURE" | head -1)"
[[ -n "$IDENTITY_NAME" ]] || fail "The app bundle has no Developer ID Application signature."
INSTALLER_NAME="${IDENTITY_NAME/Developer ID Application/Developer ID Installer}"

rm -rf "$OUT"
ARGS=(
  pack
  --packId BananaShot
  --packVersion "$PACKAGE_VERSION"
  --packDir "$APP"
  --mainExe BananaShot
  --packTitle BananaShot
  --packAuthors BananaShot
  --icon "$ROOT/distribution/macos/assets/BananaShot.icns"
  --bundleId app.banana-shot-editor.bananashot
  --channel osx
  # The .pkg then installs only on Apple Silicon.
  --runtime osx-arm64
  --signAppIdentity "$IDENTITY_NAME"
  --signInstallIdentity "$INSTALLER_NAME"
  --signEntitlements "$ROOT/distribution/macos/BananaShot.entitlements"
  # build-app-image.sh already signed each file of the bundle, with the entitlements of the JVM where necessary.
  --signDisableDeep
  --outputDir "$OUT"
)
[[ -n "$NOTARY_PROFILE" ]] && ARGS+=(--notaryProfile "$NOTARY_PROFILE")
[[ -n "$KEYCHAIN" ]] && ARGS+=(--keychain "$KEYCHAIN")
"$VPK" "${ARGS[@]}"

SETUP="$OUT/BananaShot-osx-Setup.pkg"
[[ -f "$SETUP" ]] || fail "vpk did not make $(basename "$SETUP"). The app needs this fixed name."
[[ -f "$OUT/BananaShot-$PACKAGE_VERSION-osx-full.nupkg" ]] || fail "vpk did not make the update package."
[[ -f "$OUT/releases.osx.json" ]] || fail "vpk did not make the release feed releases.osx.json."
pkgutil --check-signature "$SETUP" >/dev/null || fail "The installer has no valid signature."
echo "The Velopack installer is ready: $SETUP"

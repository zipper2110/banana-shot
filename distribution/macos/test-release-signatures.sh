#!/usr/bin/env bash
# Checks the code signatures of a signed macOS release (B-13, step 11). It is the macOS copy of
# distribution/windows/Test-ReleaseSignatures.ps1.
#
# Gatekeeper and the hardened runtime refuse a file with no valid signature of our team. One such file is enough
# to break the preview or the export. So the script checks each Mach-O file of the app in the .pkg, in the portable
# zip, and in the update package: a valid signature of the team, a timestamp, and the hardened runtime. It also
# checks that no JAR file contains a macOS native file (notarization refuses it).
#
# Usage: distribution/macos/test-release-signatures.sh --team-id <ID> [--velopack-dir <dir>] [--require-notarization]
# With --require-notarization, the .pkg and the app must have a stapled ticket, and Gatekeeper must accept them.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/target/package/velopack"
TEAM_ID=""
REQUIRE_NOTARIZATION=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --team-id) TEAM_ID="$2"; shift 2 ;;
    --velopack-dir) DIR="$2"; shift 2 ;;
    --require-notarization) REQUIRE_NOTARIZATION=true; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done
fail() { echo "$*" >&2; exit 1; }
[[ "$TEAM_ID" =~ ^[A-Z0-9]{10}$ ]] || fail "Give --team-id, the 10 characters of the Apple Team ID."

SETUP="$DIR/BananaShot-osx-Setup.pkg"
PORTABLE="$DIR/BananaShot-osx-Portable.zip"
[[ -f "$SETUP" ]] || fail "The installer is missing: $SETUP"
[[ -f "$PORTABLE" ]] || fail "The portable zip is missing: $PORTABLE"
PACKAGES=("$DIR"/*-osx-full.nupkg)
[[ -f "${PACKAGES[0]}" ]] || fail "No update package in $DIR."

WORK="$ROOT/target/signature-check-macos"
rm -rf "$WORK"
mkdir -p "$WORK"
FAILURES=()

# --- The installer -----------------------------------------------------------------------------
PKG_SIGNATURE="$(pkgutil --check-signature "$SETUP" 2>&1)" || FAILURES+=("BananaShot-osx-Setup.pkg: no valid signature")
grep -q "Developer ID Installer: .*($TEAM_ID)" <<<"$PKG_SIGNATURE" \
  || FAILURES+=("BananaShot-osx-Setup.pkg: not signed with the Developer ID Installer certificate of $TEAM_ID")
grep -q "Signed with a trusted timestamp" <<<"$PKG_SIGNATURE" \
  || FAILURES+=("BananaShot-osx-Setup.pkg: no trusted timestamp")
if $REQUIRE_NOTARIZATION; then
  xcrun stapler validate "$SETUP" >/dev/null 2>&1 || FAILURES+=("BananaShot-osx-Setup.pkg: no stapled notarization ticket")
  spctl --assess --type install "$SETUP" >/dev/null 2>&1 || FAILURES+=("BananaShot-osx-Setup.pkg: Gatekeeper refuses it")
fi

# --- The app in each file ----------------------------------------------------------------------
# check_app <label> <bundle folder> [files-only]. With files-only, the script checks each Mach-O file but not the
# seal of the bundle: the update package stores the links as ".__symlink" files, and Velopack makes the links
# again when it installs the update.
check_app() {
  local label="$1" app="$2" mode="${3:-bundle}" file details checked=0
  if [[ ! -d "$app/Contents" ]]; then
    FAILURES+=("$label: no app bundle"); return
  fi
  if [[ "$mode" == "bundle" ]]; then
    codesign --verify --deep --strict "$app" 2>/dev/null || FAILURES+=("$label: the bundle signature is not valid")
  fi
  while IFS= read -r -d '' file; do
    [[ "$(file -b "$file")" == *Mach-O* ]] || continue
    checked=$((checked + 1))
    local name="$label: ${file#"$app"/}"
    # The main program of a bundle (the launcher, libjli of the runtime) is checked together with the seal of its
    # bundle. In files-only mode, the script reads only the signature details of these files.
    if [[ "$mode" == "files-only" && "$file" == */Contents/MacOS/* ]]; then
      codesign -dvv "$file" >/dev/null 2>&1 || { FAILURES+=("$name: no signature"); continue; }
    elif ! codesign --verify --strict "$file" 2>/dev/null; then
      FAILURES+=("$name: no valid signature"); continue
    fi
    details="$(codesign -dvv "$file" 2>&1)"
    grep -q "^TeamIdentifier=$TEAM_ID$" <<<"$details" || FAILURES+=("$name: not signed by the team $TEAM_ID")
    # Without a timestamp, notarization refuses the file.
    grep -q "^Timestamp=" <<<"$details" || FAILURES+=("$name: no timestamp")
    grep -q "flags=.*(runtime)" <<<"$details" || FAILURES+=("$name: no hardened runtime")
  done < <(find "$app" -type f -print0)
  [[ "$checked" -gt 0 ]] || FAILURES+=("$label: no Mach-O file found")
  for jar in "$app"/Contents/app/*.jar; do
    if grep -qE '\.(dylib|jnilib)$' <<<"$(unzip -Z1 "$jar")"; then
      FAILURES+=("$label: $(basename "$jar") contains a macOS native file")
    fi
  done
  if $REQUIRE_NOTARIZATION && [[ "$mode" == "bundle" ]]; then
    xcrun stapler validate "$app" >/dev/null 2>&1 || FAILURES+=("$label: no stapled notarization ticket")
    spctl --assess --type execute "$app" >/dev/null 2>&1 || FAILURES+=("$label: Gatekeeper refuses the app")
  fi
  echo "$label: $checked Mach-O files checked"
}

# The .pkg: pkgutil extracts the payload with the bundle.
pkgutil --expand-full "$SETUP" "$WORK/pkg"
check_app "BananaShot-osx-Setup.pkg" "$(find "$WORK/pkg" -maxdepth 3 -type d -name BananaShot.app | head -1)"

# ditto keeps the links and the extended attributes, as the update of Velopack does.
mkdir -p "$WORK/portable"
ditto -x -k "$PORTABLE" "$WORK/portable"
check_app "BananaShot-osx-Portable.zip" "$WORK/portable/BananaShot.app"

for package in "${PACKAGES[@]}"; do
  target="$WORK/$(basename "$package" .nupkg)"
  mkdir -p "$target"
  ditto -x -k "$package" "$target"
  # The update package has the bundle in lib/app.
  check_app "$(basename "$package")" "$target/lib/app" files-only
done

if [[ ${#FAILURES[@]} -gt 0 ]]; then
  printf '%s\n' "${FAILURES[@]}" >&2
  fail "${#FAILURES[@]} signature problems."
fi
echo "All signatures of the release are valid."

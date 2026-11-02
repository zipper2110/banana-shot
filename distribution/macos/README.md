# macOS distribution

The macOS version is in progress (B-13). The plan and its status are in
[docs/macos/b-13-macos.md](../../docs/macos/b-13-macos.md). This folder has
the native files (libmpv and FFmpeg) for Apple Silicon (arm64) and the
scripts of the app bundle. The notarization and the installer are not ready
yet.

## Native files

`native-dependencies.json` pins the native files of the macOS app:

- `mpv/libmpv.2.dylib`: LGPL libmpv. FFmpeg (LGPL), libplacebo, libass, and
  their dependencies are static libraries in it. `mpv/libmpv.dylib` is a link
  to it. JNA loads libmpv with the name `mpv`.
- `mpv/libMoltenVK.dylib`: the Vulkan driver of the preview.
- `ffmpeg/bin/ffmpeg` and `ffprobe`: GPL FFmpeg with libx264, libass, and
  VideoToolbox. The export runs them as separate processes.

The app is not GPL. libmpv runs in the process of the app, so libmpv must
not contain GPL code. Only the FFmpeg programs contain GPL code (x264).

### Get the pinned files

```bash
distribution/macos/get-native-dependencies.sh
```

The script downloads the files of the natives release, checks the SHA-256,
and puts them in `target/native/macos-arm64`. Source runs use this folder.

### Build the files from source

```bash
brew install meson ninja cmake pkgconf
distribution/macos/build-natives.sh
```

The script needs an Apple Silicon Mac and the Xcode Command Line Tools. It
downloads the pinned sources, builds them for macOS 15 and later, checks the
result, and puts it in `target/native/macos-arm64`. A full build takes about
10 minutes on an M1 Mac. The build tools of Homebrew are not in the output.
The script refuses a file that links a library of Homebrew.

`--clean` builds all libraries again. `--package` also makes the files of a
natives release in `target/native-build/macos/release`.

To change a version, change its URL and SHA-256 (or its Git commit) in
`build-natives.sh`. Then make a new natives release.

## Natives release

The natives release keeps the native files and their corresponding source in
this repository. Make a new natives release only when a pin in
`build-natives.sh` changes.

1. On GitHub, open Actions → "macOS natives" → "Run workflow". The workflow
   builds the files on a macOS 15 runner (about 15 minutes). If the run
   stops with "The job was not acquired by Runner", GitHub had no free macOS
   runner. Run it again later.
2. When the run is complete, download the artifact `macos-natives-<number>`
   and extract it. It contains the three archives, `SHA256SUMS.txt`, and
   `README.txt`.
3. On GitHub, open the releases page and click "Draft a new release".
4. In "Choose a tag", type a new tag (for example `natives-2026-11`) and
   select "Create new tag". Keep the target branch `master`.
5. Type the tag as the release title. Paste the text of `README.txt` as the
   description.
6. Drag the three archives and `SHA256SUMS.txt` into the assets area. Wait
   until each upload is complete.
7. Select "Set as a pre-release". Clear "Set as the latest release". The app
   releases must stay the latest release.
8. Click "Publish release".
9. In `native-dependencies.json`, set `nativesRelease`, `buildRun`, and the
   file names, URLs, and SHA-256 values from `SHA256SUMS.txt`.
10. Run `get-native-dependencies.sh` to check the new pins.

## App bundle

```bash
distribution/macos/get-native-dependencies.sh
distribution/macos/build-app-image.sh
distribution/macos/validate-app-image.sh --version 1.4.0 --allow-no-feedback-endpoint --allow-no-analytics
target/package/app-image/BananaShot.app/Contents/MacOS/BananaShot --diagnostics
open target/package/app-image/BananaShot.app
```

`build-app-image.sh` needs JDK 25 (a later JDK works with a warning). It runs
the Maven distribution build and jpackage, copies the native files and the
legal files into the bundle, sets the `Info.plist` keys, and signs the bundle
from the inside to the outside.

- `--sign <identity>` signs with a Developer ID, the hardened runtime, and
  `entitlements.plist`. The default `-` is an ad-hoc signature: the bundle
  then opens only on the Mac that built it. Give the SHA-1 hash of the
  certificate (`security find-identity -v -p codesigning`) when the keychain
  has two copies of it.
- `--feedback-endpoint` and the three `--analytics-*` options are the same
  as the options of `Build-AppImage.ps1` on Windows.

`validate-app-image.sh` checks the build and expiry dates, the version, the
runtime modules, the launcher options, the feedback and analytics
properties, the `Info.plist` keys, the native files, the JAR files (no macOS
native file in them), and the signature of each file. The two `--allow-*`
options are only for a local build.

The icon is `assets/BananaShot.icns`. After a change of
`src/main/resources/icons/app-icon.svg`, run `make-icon.sh`.

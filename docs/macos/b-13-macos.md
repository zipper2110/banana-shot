# B-13 macOS: Changes for macOS Support

This file tells what we must change to support macOS at the same level as
Windows. The backlog item is B-13 in `docs/backlog.md`.

The list comes from a review of the code on 2026-10-08 (version
`1.0.1-SNAPSHOT`). Items with "Verify" are not tested yet. Do a test of each
of them on a Mac before you start the related work.

## Decisions

The author made these decisions on 2026-10-08:

| Subject | Decision |
|---|---|
| CPU type | Apple Silicon (arm64) only. No Intel Mac build |
| Minimum macOS version | The version that covers 95% of Mac users. Now this is macOS 15 (see "Minimum macOS version") |
| Scoreboard fonts | Replace the fonts on all platforms with bundled open fonts (see "5. Fonts") |
| Installer and update | Velopack, if it works well on macOS. If it does not, use a DMG (see "Installer and update") |
| Start date | Not in the scope of this document |

## Goal

A Mac user can do all the steps that a Windows user can do:

- Download a signed and notarized app from the site and install it.
- Open the app with no Gatekeeper warning.
- Create a project, mark the points, score them, and change the colors and
  the transform with the live video preview.
- Export a video with the scoreboard. The export can use hardware encoding.
- Send feedback and usage analytics.
- Get the update notice and use "Update and restart".
- Get the same build expiry, rules file, and expired mode behavior.

## Summary

Most of the app is Kotlin on the JVM with Swing and FlatLaf. That part runs on
macOS with no change or with small changes. The Windows-only parts are at the
edges of the app:

1. **The video preview.** The preview puts a libmpv window into a Swing
   `Canvas` with the `wid` option and Direct3D 11. This method is specific to
   Windows. It is the largest risk. A spike must find a method that works on
   macOS before the other work starts.
2. **The native files.** The app bundles libmpv and FFmpeg builds for Windows
   only. We need pinned macOS builds, their source code, and a new natives
   release.
3. **The packaging and the update.** jpackage, Velopack, and Certum signing
   make a Windows setup EXE. macOS needs an `.app` bundle, Apple Developer ID
   signing, notarization, and a different installer.
4. **The hardware encoders.** The export knows only NVENC, AMF, and QSV. A Mac
   needs VideoToolbox.
5. **The fonts.** The UI and 4 scoreboard fonts are Windows fonts (Segoe UI,
   Segoe UI Black, Consolas, Ink Free). A Mac does not have them, so the
   scoreboard changes its look. We replace all scoreboard fonts with bundled
   open fonts on all platforms.
6. **Small Windows services.** The registry, DXGI, `GetTickCount64`,
   `CreateFile`, the `Windows-ROOT` trust store, and the Windows error codes.
   Each one needs a macOS replacement or a "not used on macOS" path.
7. **CI, release, site, and texts.** All are for Windows now.

Rough size: 6 to 10 weeks of work for one developer, plus the time to get the
Apple Developer account. The preview spike can change this number.

## The Windows-only parts in the code

| Area | Windows now | macOS need | Files |
|---|---|---|---|
| Preview window | `wid` from `Native.getComponentID(canvas)`, `gpu-api=d3d11`, `hwdec=d3d11va` | A different embed method (see "1. Video preview") | `media/mpv/MpvSwingMediaPlayerAdapter.kt` |
| Mouse input over the preview | `EnumChildWindows` and `EnableWindow` (user32) | Depends on the embed method | `MpvSwingMediaPlayerAdapter.kt` |
| libmpv load | Library name `libmpv-2` (`libmpv-2.dll`), `LoadLibraryEx` error codes | Library name `mpv` (`libmpv.2.dylib`), a macOS error text | `media/mpv/LibMpv.kt` |
| Native folder | `natives/windows-x64`, `ffmpeg.exe`, `ffprobe.exe` | `natives/macos-arm64`, no `.exe` | `ApplicationLayout.kt` |
| Packaged launcher | `BananaShot.exe` | `BananaShot.app/Contents/MacOS/BananaShot` | `ApplicationLayout.kt` |
| Data folder | `%APPDATA%\BananaShot` | `~/Library/Application Support/BananaShot` (now the fallback is `~/.bananashot`) | `ApplicationLayout.kt` |
| GPU choice | Registry key `UserGpuPreferences`, DXGI GPU count | Not necessary. macOS selects the GPU. Skip on macOS | `WindowsGpuPreference.kt`, `WindowsGpuCount.kt` |
| Encoder choice | `powershell.exe Get-CimInstance Win32_VideoController` | VideoToolbox is always the hardware encoder on a Mac | `FFmpegCapabilities.kt` |
| Hardware encoders | `h264_nvenc`, `h264_amf`, `h264_qsv` | `h264_videotoolbox` | `export/EncoderCapabilities.kt`, `FFmpegCommandBuilder.kt` |
| Run time counter | `Kernel32.GetTickCount64` (counts sleep) | A clock that counts sleep (see "6. Platform services") | `license/time/RunTimeCounter.kt` |
| File in use | `Kernel32.CreateFile` sharing check | No equivalent. It returns false now. Keep it | `media/FileWriteCheck.kt` |
| Trust store | `Windows-ROOT`, module `jdk.crypto.mscapi` | `KeychainStore` (Verify the root store type) | `license/online/UpdateTrust.kt`, `DistributionDiagnostics.kt` |
| Blocked files | Error codes 225, 226, 1260, 4550-4559 (Smart App Control) | Gatekeeper and quarantine messages | `WindowsSecurityBlock.kt`, `media/VideoProblem.kt`, `export/ExportFailureAdvice.kt` |
| Show in folder | `Desktop.open`, then `explorer.exe` | `Desktop.open`, then `open -R <file>` | `ui/tabs/export/ExportsTable.kt` |
| Update | Download `BananaShot-win-Setup.exe` and start it | Download the macOS installer and open it | `license/update/UpdateAndRestart.kt`, `UpdateOptions.kt` |
| Velopack hooks | `--veloapp-*` arguments | Verify which hooks Velopack runs on macOS | `VelopackHooks.kt` |
| UI font | `Segoe UI` set in `SwingMainApp` | The macOS system font | `SwingMainApp.kt`, `ui/commons/UiKit.kt`, `ExportUi.kt`, `ProjectsUi.kt`, `VideoFocusChip.kt` |
| Scoreboard fonts | Windows system fonts | Bundled open fonts on all platforms (see "5. Fonts") | `export/scoreboard/ScoreboardLayoutParts.kt`, `export/comments/CommentAss.kt`, `AssOverlayWriter.kt` |
| Text anti-aliasing | `lcd_hrgb` on Windows | `on` (already the code path for other systems) | `SwingMainApp.kt` |
| Diagnostics | `BananaShot Diagnostics.cmd`, check `libmpv-2.dll` | A shell script or `--diagnostics` from the bundle, check the dylib | `distribution/windows/`, `DistributionDiagnostics.kt` |
| Build and CI | `windows-2022` runners, PowerShell scripts | macOS runners, shell scripts | `.github/workflows/`, `distribution/windows/` |

These parts already work on macOS or need no change:

- `FlatLaf`, Swing, the themes, and `SystemFileChooser` (it shows the Finder
  panel on macOS).
- The project files (Jackson JSON), the export queue, and the instance lock
  (`FileChannel.lock` works on macOS).
- `java.util.prefs` (on macOS it uses a plist file).
- The analytics `os_family`: the app and the Worker already accept `macos`.
- The feedback report: it sends `os_name` and `os_version` from Java.
- The rules file parser reads only the fields that it knows. Thus a new field
  for a macOS installer URL does not break version 1.0.0 (see "8. Update and
  restart").
- `SafeFileName` removes the characters that Windows does not permit. These
  rules are stricter than the macOS rules, so they are safe on macOS.
- The ffmpeg filter path escape in `FFmpegCommandBuilder.kt` also works for
  POSIX paths.

## 1. Video preview (largest risk)

### The problem

`MpvSwingMediaPlayerAdapter` makes a heavyweight `java.awt.Canvas`, reads its
native window handle with `Native.getComponentID(canvas)`, and gives it to
mpv as `wid`. mpv then makes a child window in the canvas and renders with
`vo=gpu-next`, `gpu-api=d3d11`, and `hwdec=d3d11va`.

On macOS this method does not work in the same way:

- AWT on macOS draws all components of a window into one layer-backed view.
  A `Canvas` has no `NSView` of its own. Verify what
  `Native.getComponentID` returns on macOS with JDK 25.
- The mpv manual tells that on macOS `wid` is an `NSView` pointer and that
  it works only with libmpv. Verify that it works with `vo=gpu-next` and a
  view that is not a real child view of the Java window.
- `gpu-api=d3d11` and `hwdec=d3d11va` do not exist on macOS. Use
  `hwdec=videotoolbox` and a macOS GPU API (Vulkan through MoltenVK, or
  OpenGL).

### The candidate methods

Do a spike with each method in this order. Stop at the first method that
passes all the checks below. The spike code can start from
`src/test/kotlin/org/litvin/media/mpv/MpvPreviewSpikeMain.kt`.

1. **The libmpv render API with OpenGL.** The app makes an OpenGL surface in
   Swing (for example with LWJGL and `lwjgl3-awt`) and calls
   `mpv_render_context_create` and `mpv_render_context_render` on each
   frame. mpv decodes with VideoToolbox. This is the method that most
   embedded mpv players on macOS use. It adds JNA mappings for
   `mpv/render.h` and `mpv/render_gl.h` and one new library. Apple marks
   OpenGL as deprecated, but it still works on Apple Silicon. Verify that
   the custom shader `tr-adjust.hook` and the `osd-overlay` scoreboard work
   with the render API.
2. **`wid` with an `NSView`.** The app makes a child `NSView` in the content
   view of the Java window with JNA calls to the Objective-C runtime, keeps
   its frame equal to the canvas bounds, and gives it to mpv as `wid`. This
   keeps the most code of the current adapter. The risk is the z-order with
   Swing popups and the resize and move behavior.
3. **The libmpv render API with software output (`MPV_RENDER_API_TYPE_SW`).**
   mpv renders into a memory buffer. The app paints the buffer as a
   `BufferedImage`. This method is simple and safe. It uses much CPU at 4K
   and can drop frames. Use it only as a fallback.

### The checks of the spike

- 4K 60 fps H.264 and HEVC files play with no dropped frames on an M1 Mac
  with macOS 15.
- Seek, frame step, and the playback rate work as on Windows.
- The color and the tone controls give the same result as the export
  (`FfmpegToneFilterParityTest` and `ExportPreviewParityMain`).
- The scoreboard overlay and the crop editor overlay show in the correct
  place on a Retina display and on a normal display.
- Mouse clicks and drags on the preview go to the Swing listeners. The
  keyboard focus stays in Java.
- Swing dialogs, menus, and the hint balloons show over the preview.
- A tab change, a project change, and a window resize do not crash the app
  and do not leak mpv instances.

### Other work in the preview code

- `LibMpv.LIBRARY_NAME`: JNA maps `libmpv-2` to `liblibmpv-2.dylib` on macOS.
  Use the name `mpv` on macOS. JNA then finds `libmpv.dylib` (add a
  `libmpv.dylib` link to `libmpv.2.dylib` in the bundle, or give the full
  file name).
- `LibMpv.windowsLoadError` and `VideoProblem.previewFailure`: on macOS,
  show the `UnsatisfiedLinkError` text and a macOS hint ("macOS blocked a
  file of the app. Install the app again from the site.").
- `disableNativeInput`: it already returns at once on systems that are not
  Windows. Keep it for Windows. The macOS input path depends on the method
  that the spike selects.

## 2. Native files (libmpv and FFmpeg)

### Builds

We need pinned macOS builds of libmpv and FFmpeg, as we have for Windows in
`distribution/windows/native-dependencies.json`:

- **libmpv:** a shared `libmpv.2.dylib` with all its dependencies (FFmpeg
  libraries, libplacebo, libass, MoltenVK if we use Vulkan). Each dependency
  must be in the bundle and must use `@rpath` or `@loader_path` names, not
  Homebrew paths. Build for arm64 only, with the deployment target macOS 15.
  Candidate sources: a build of our own in CI, or a third-party build such as
  `media-kit/libmpv-darwin-build`. Verify the
  license (LGPL or GPL) and the available source of each candidate.
- **FFmpeg:** `ffmpeg` and `ffprobe` with `libx264`, `h264_videotoolbox`,
  `libass`, and `zscale` (the export uses the same filters as on Windows).
  BtbN does not make macOS builds. Candidate sources: a build of our own in
  CI, or a pinned third-party build. Verify that the build has all the
  filters that `FFmpegCommandBuilder` uses.

Recommendation: build both in a GitHub Actions job of our own, from pinned
source commits. Then the corresponding source for the GPL and the LGPL is
the input of the build, and we do not depend on a third party.

### Manifest and natives release

- Add `distribution/macos/native-dependencies.json` with the same schema:
  version, URL, SHA-256, source URL, and source SHA-256 for each file. Set
  `architecture` to `macos-arm64`.
- Add the macOS files and their source archives to a new natives release
  (for example `natives-2026-11`). Follow the steps of "Natives release" in
  `distribution/windows/README.md`. Do not delete `natives-2026-09`.
- Add a script `distribution/macos/get-native-dependencies.sh`. It
  downloads the files, checks the SHA-256, and puts them in
  `target/native/macos-arm64`. It is the macOS copy of
  `Get-NativeDependencies.ps1`.
- Add the macOS builds to `distribution/THIRD-PARTY-NOTICES.txt`.

## 3. Runtime layout and data folders

`ApplicationLayoutResolver` needs a platform value. Add an internal enum, for
example `Platform { WINDOWS_X64, MACOS_ARM64 }`, and read it from `os.name`
and `os.arch`. An Intel Mac cannot run the app, because the bundle has only
arm64 files. Then:

- `nativeRoot`: `natives/<platform>` in place of `natives/windows-x64`.
- The bundled FFmpeg: `ffmpeg/bin/ffmpeg` and `ffmpeg/bin/ffprobe` with no
  `.exe` on macOS.
- The development folder: `target/native/<platform>/mpv`.
- `packagedLauncher`: `Contents/MacOS/BananaShot` in the `.app` bundle.
  jpackage on macOS puts the JARs in `BananaShot.app/Contents/app`. The
  current `resolveAppHome` then returns `Contents`. Put `natives` in
  `Contents` (or in `Contents/Frameworks` and change `nativeRoot`). Verify
  that code signing accepts the files in the selected folder.
- The data folder: `~/Library/Application Support/BananaShot` on macOS, in
  place of `~/.bananashot`. The logs can stay in the `logs` folder of the
  data folder.
- `ApplicationLayoutResolverTest`: add the macOS cases.

## 4. Export

- **Encoder:** add `VIDEOTOOLBOX("h264_videotoolbox", "Apple VideoToolbox",
  "H.264 (VideoToolbox)", ...)` to `ExportEncoder`.
- **Encoder choice:** `FFmpegCapabilities.videoAdapters` returns an empty
  list on macOS. On macOS, select `h264_videotoolbox` when the encoder test
  passes. Keep the real test encode, as for the other encoders.
- **Quality:** `FFmpegCommandBuilder` maps the CRF only for NVENC. Add a map
  for VideoToolbox. On Apple Silicon, VideoToolbox accepts a constant
  quality (`-q:v`). Find the `-q:v` values that give the same quality as the
  CRF values of the presets. Add tests to `FFmpegCommandBuilderTest`.
- **Texts:** the encoder descriptions say "PC", "NVIDIA", "AMD", and
  "Intel". Add a description for VideoToolbox and change "PC" to "computer".
- **Analytics:** the counter keys are a closed list with one key for each
  encoder (`export_started_nvenc`, `export_run_s_nvenc`, and so on). Add the
  `videotoolbox` keys to `analytics-contract/v1/counter-keys.json`, the
  analytics Worker, and the cockpit before a macOS release. Otherwise the
  Worker refuses the summaries of Mac users or drops the keys. Verify the
  current behavior for an unknown key.
- **Hardware decode in the export:** the export uses no Windows hardware
  decoder now. No change is necessary.

## 5. Fonts

### UI font

`SwingMainApp` sets `Segoe UI` as the default font, and `UiKit`, `ExportUi`,
`ProjectsUi`, and `VideoFocusChip` ask for `Segoe UI` or `Segoe UI Semibold`.
On macOS, Java then uses a fallback font. Use the FlatLaf default font on
macOS (the system font) and keep `Segoe UI` on Windows. Check each screen for
text that does not fit, because the system font of macOS is wider.

### Scoreboard, statistics card, and comment fonts

The scoreboard styles, the statistics card, and the comments use these
fonts in the export (libass) and in the preview (mpv `osd-overlay`). Java
measures the text with the same fonts (`ScoreboardFonts`). The font names
are in `export/scoreboard/ScoreboardLayoutParts.kt`,
`export/comments/CommentAss.kt` (Arial), and `AssOverlayWriter.kt` (Arial).

When a font is missing, libass and Java select different fallback fonts.
Then the text of the scoreboard does not fit its box. A Mac does not have
Segoe UI, Segoe UI Black, Consolas, and Ink Free. We cannot bundle the
Microsoft fonts, because their license does not permit it.

**Decision:** replace all these fonts on all platforms with bundled open
fonts. Then the export and the preview do not depend on the fonts of the
computer, and the scoreboards look the same on Windows and on macOS.

This step does not need macOS. Do it on Windows first and release it in a
Windows version. Then the macOS work starts with fonts that are known to
work.

#### Candidate fonts

Select the fonts in a design review with screenshots of all 16 styles
before and after the change. Each candidate has the SIL Open Font License
(OFL). Verify the license and the weights of each font before you select it.

| Current font | Candidate | Note |
|---|---|---|
| Segoe UI | Selawik | Microsoft made it as an open fallback with the metrics of Segoe UI. It has no Black weight |
| Segoe UI Black | Inter (Black weight) | Not metric-compatible. Alternative: use Inter for Segoe UI too, so that one family has all weights |
| Arial | Liberation Sans | Metric-compatible with Arial |
| Arial Black | Archivo Black | Not metric-compatible |
| Georgia | Gelasio | Metric-compatible with Georgia |
| Trebuchet MS | Fira Sans | Not metric-compatible |
| Tahoma | Open Sans or Noto Sans | Not metric-compatible |
| Consolas | Cascadia Mono or Inconsolata | Not metric-compatible |
| Ink Free | Patrick Hand or Caveat | Handwriting fonts. Not metric-compatible |

A metric-compatible font keeps the text widths, so the boxes of a style need
no change. For the other fonts, check the box sizes of each style.

#### Tasks

- Put the font files in the app as resources (for example
  `src/main/resources/fonts`). At the start, copy them to a folder in the
  app data folder or the app folder, because libass and mpv need a folder
  on the disk.
- Change the font names in `ScoreboardLayoutParts.kt`, `CommentAss.kt`, and
  `AssOverlayWriter.kt` to the family names of the bundled fonts. Use the
  family names that are in the font files. libass finds a font by this
  name.
- Give the folder to the ffmpeg `subtitles` filter with `fontsdir=`.
- Give the folder to mpv with `sub-fonts-dir` and `osd-fonts-dir`. Verify
  that the `osd-overlay` text uses `osd-fonts-dir`.
- Register the fonts in Java with `Font.createFont` and
  `GraphicsEnvironment.registerFont`, so that `ScoreboardFonts` and the
  statistics card measure the same font. Register them before the first
  layout.
- Make sure that a font that is also installed on the computer does not
  replace the bundled font. Give the bundled fonts unique names if
  necessary.
- Update the expected values of `ScoreboardLayoutsTest`,
  `ScoreboardAssTest`, `CommentAssTest`, `StatsCardTest`, and
  `ScoreboardComponentTest`. Add a test that each font name of a style is
  a bundled font.
- Run the export and the preview parity checks on Windows.
- Add the font licenses to `THIRD-PARTY-NOTICES.txt` and copy them into
  the `legal` folder of the app.
- Tell the users in the changelog that the scoreboard fonts changed.
  Existing projects keep their style, but a new export looks a little
  different.
- Update the screenshots on the site (`site/public/assets/frames`) and in
  `design/` if they show the old fonts.

## 6. Platform services

- **GPU preference and GPU count (B-35):** call
  `WindowsGpuPreference.ensureHighPerformancePreference` and
  `WindowsGpuCount.count` only on Windows (they return at once on other
  systems now). Do not show the GPU restart message on macOS. Apple Silicon
  Macs have one GPU, so no GPU choice is necessary.
- **Run time counter (expiry):** `RunTimeCounter.production` calls
  `Kernel32.GetTickCount64`. On macOS this call fails, and the app uses
  `System.nanoTime`. On macOS, `System.nanoTime` does not count the time
  while the Mac sleeps. Then a sleeping Mac does not add run time. Add a
  macOS primary counter that counts sleep, for example
  `clock_gettime_nsec_np(CLOCK_MONOTONIC)` or `mach_continuous_time` through
  JNA. Verify with the sleep check of B-42 on a Mac.
- **Trust store:** `UpdateTrust` and `DistributionDiagnostics` load
  `Windows-ROOT`. On macOS, load the bundled `cacerts` and the macOS
  keychain store. JDK 25 has `KeychainStore` and, from JDK 23,
  `KeychainStore-ROOT` for the system roots. Verify which type gives the
  roots that an office TLS proxy adds. Remove `jdk.crypto.mscapi` from the
  macOS runtime modules. Rename the diagnostics check to "System trust
  store". Update "Trust for the HTTPS connection" in
  `docs/licensing/build-expiry-spec.md`.
- **Blocked files:** Smart App Control error codes do not exist on macOS. On
  macOS, a blocked file usually shows as a signature or quarantine failure
  when the process starts or the dylib loads. Add macOS texts to
  `VideoProblem` and `ExportFailureAdvice`. The texts must tell the user to
  install the app again from the site. Keep the Windows codes on Windows.
- **Proxy:** `java.net.useSystemProxies` also reads the macOS proxy
  settings. Change the comments that say "the proxy of Windows". Do the
  proxy check of B-42 on a Mac.
- **File in use:** `FileWriteCheck` returns false on macOS. macOS has no
  sharing lock of this type. Keep it.
- **Show in folder:** in `ExportsTable.openFolder`, use `open -R <file>` on
  macOS as the fallback, in place of `explorer.exe`. `open -R` also selects
  the file in Finder.

## 7. macOS app behavior

A Swing app does not get the macOS behavior automatically. Add these items in
a small `MacIntegration` class that runs only on macOS:

- **Quit:** Cmd+Q and the Quit item of the app menu call `System.exit(0)` by
  default. Then the app does not save the open project and the export queue.
  Set a quit handler with `Desktop.setQuitHandler`. The handler must run the
  same close sequence as the main window `windowClosing` in
  `SwingApplicationFactory`.
- **About:** set `Desktop.setAboutHandler` to open the About page of More.
- **App name and Dock icon:** set the app name in `Info.plist` and the Dock
  icon with `Taskbar.setIconImage` for source runs.
- **Help key:** F1 needs the Fn key on most Mac keyboards. Add a second key
  for Help, for example Cmd+? or Cmd+/. Show the macOS key names in the help
  texts (`AppShortcuts`).
- **Full screen and window buttons:** test the green window button and full
  screen with the mpv preview.
- **Retina:** test all custom painting (timeline, scoreboard preview, crop
  overlay, momentum chart) at the 2x scale.
- **"Already running" text:** `SwingMainApp` says "look in the taskbar". On
  macOS, say "look in the Dock".

## 8. Packaging, signing, and update

### App bundle

- Add `distribution/macos/build-app-image.sh`. It is the macOS copy of
  `Build-AppImage.ps1`: it runs `mvn -Pdistribution package` and
  `jpackage --type app-image` on macOS. jpackage makes `BananaShot.app`.
- Give jpackage `--mac-package-identifier` (for example
  `app.banana-shot-editor.bananashot`), `--mac-package-name`, and an `.icns`
  icon. Make the icon from `src/main/resources/icons/app-icon.svg` (see
  `New-AppIcon.ps1` and `AppIconRenderer.java`).
- Use the same runtime modules as Windows, without `jdk.crypto.mscapi`.
  Check with `jdeps` on macOS.
- Set `LSMinimumSystemVersion` in `Info.plist` to the minimum macOS version
  (see "Minimum macOS version"). Build the libmpv and FFmpeg files with the
  same deployment target.
- Copy the natives, the fonts, and the legal files into the bundle.
- Add `distribution/macos/validate-app-image.sh` with the same checks as
  `Validate-AppImage.ps1` (version, build and expiry dates, runtime
  modules, feedback and analytics properties).

### Architecture

The app is for Apple Silicon (arm64) only. The bundled JDK runtime and the
native files are arm64 files. Apple sells only Apple Silicon Macs since
2023, and macOS 26 is the last macOS for Intel Macs.

- Build on an arm64 GitHub runner with an arm64 JDK 25.
- Set `LSArchitecturePriority` to `arm64` in `Info.plist`.
- On the download page, tell that the app needs a Mac with Apple Silicon
  (M1 or later).

### Minimum macOS version

Rule: select the newest minimum version that still runs on 95% or more of
Macs. "Runs on" means that the Mac has this version or a newer version.

TelemetryDeck data of July 2026 shows macOS 26 on 86.0% of Macs and macOS 15
on 12.2%. Thus macOS 15 and later cover about 98%, and macOS 26 alone covers
less than 95%. This data comes from the apps that use TelemetryDeck. It can
contain more new Macs than the average.

- Start with **macOS 15**.
- Check the share again before each macOS release. The next macOS version
  came out in September 2026. When macOS 26 and later cover 95%, change the
  minimum to macOS 26.
- When the app sends analytics, use the `os_version` of our own users in
  place of the public data. The analytics send only `os_family` now. Add
  the major macOS version to the analytics only with a change of the
  contract and the privacy notice.

### Code signing and notarization

Without signing and notarization, Gatekeeper blocks the app.

- Join the Apple Developer Program (99 USD for each year). Make a
  "Developer ID Application" certificate. Make a "Developer ID Installer"
  certificate if we ship a `.pkg`.
- Sign each Mach-O file in the bundle: each dylib, `ffmpeg`, `ffprobe`, the
  JDK runtime files, and the app launcher. Sign from the inside to the
  outside. Use the hardened runtime (`--options runtime`).
- Give the JVM these entitlements:
  `com.apple.security.cs.allow-jit` and
  `com.apple.security.cs.allow-unsigned-executable-memory`. Verify if the
  JVM and libmpv need more.
- **JNA:** by default, JNA extracts its `libjnidispatch` file to a temporary
  folder. The hardened runtime refuses that file, because our certificate
  did not sign it. Put a signed copy of `libjnidispatch.jnilib` in the
  bundle and set `-Djna.boot.library.path=<folder>` and
  `-Djna.nounpack=true` in the jpackage options. Also set
  `-Djna.library.path` to the libmpv folder.
- Send the result to Apple with `xcrun notarytool submit --wait`. Then
  attach the ticket with `xcrun stapler staple`.
- Store the certificate (`.p12`), its password, and the App Store Connect
  API key as secrets of a new `macos-release` GitHub environment. Only the
  macOS build job gets them, as in the Windows workflow (B-27).
- Add `distribution/macos/test-release-signatures.sh` with
  `codesign --verify --deep --strict` and `spctl --assess`.

### Installer and update

**Decision:** use Velopack if it works well on macOS. Do the checks of
option 1 first. If one check fails and we cannot fix it, use option 2.

1. **Velopack for macOS.** `vpk pack` on macOS makes a `.pkg` installer, a
   portable `.zip`, the update package, and a release feed. It can sign and
   notarize with the given identities. It keeps one tool for both platforms.
   Verify that the `.pkg` replaces a running app and starts the new version,
   as the Windows setup does. Verify which `--veloapp-*` hooks run on macOS.
   Pin the same `vpk` version as Windows (B-26).
2. **A notarized DMG.** The user drags the app to Applications. It is the
   usual Mac method. "Update and restart" must then download the DMG, mount
   it, copy the app, and start it again. This needs more own code.

The checks for Velopack on macOS:

- The `.pkg` installs the app with no Gatekeeper warning on a clean Mac
  with macOS 15.
- "Update and restart" from the previous version installs the new version
  and starts it. The project and the export queue stay.
- The update works when the app is open, when the user moved the app to
  another folder, and when the user has no administrator rights.
- The uninstall removes the app and keeps the data folder.

### Changes for "Update and restart"

- `UpdateOptions.SETUP_EXE_NAME` is `BananaShot-win-Setup.exe`. Add the
  macOS file name (for example `BananaShot-osx-Setup.pkg`) and select the
  name by platform. The stable URL also changes by platform.
- `latest.installerUrl` in the rules file is the Windows installer. Add a
  new optional field, for example `latest.macInstallerUrl` (or an
  `installers` object with one URL for each platform). Version 1.0.0
  ignores fields that it does not know, so this is safe. Keep
  `installerUrl` for Windows. Update the parser, `release/version-policy.json`,
  `build-expiry-spec.md`, and the tests.
- `ProcessSetupLauncher` starts the setup file directly. On macOS, start
  `open <file>.pkg`.
- The app must exit after it starts the installer, as on Windows. Verify
  that the macOS installer waits for the app to exit.

## 9. CI and release workflow

- **CI:** add a `macos-15` (arm64) job to `.github/workflows/ci.yml` that
  runs `mvn -B test`. Some tests use Windows paths (12 places use `C:\`).
  Make them platform-neutral, or limit them with `@EnabledOnOs(OS.WINDOWS)`.
  Some tests already use `Assumptions`. Check each one.
- **UI tests:** the Swing tests need a display. GitHub macOS runners have a
  display. The AssertJ Swing robot (the `ui-flow` and `ui-smoke` profiles)
  needs the Accessibility permission on macOS. Verify if the hosted runner
  gives it. If not, run only the headless tests in CI and run the UI flow
  tests on a Mac by hand.
- **Release:** add a `macos-installer` job to the release workflow, or a new
  `macos-release.yml`. It runs the tests, gets the natives, builds the app
  image, validates it, runs the diagnostics, signs, notarizes, staples,
  makes the installer, and uploads a workflow artifact. The `publish` job
  then collects the Windows and the macOS artifacts into one draft release.
- **Smoke test:** add `qa/macos/ui-smoke.md` with the packaged checks of
  `qa/windows/ui-smoke.md` that apply to macOS.
- **Release checklist:** add a macOS section to `docs/release-checklist.md`
  (signing secrets, notarization, the Gatekeeper check on a clean Mac, the
  update test from the previous version).

## 10. Site, texts, and documentation

- **Download page:** add "Download for Mac", the system needs (macOS
  version, Apple Silicon), the install steps, and the uninstall steps
  (`site/public/download/index.html`). Change the title "Download
  BananaShot for Windows". The home page, the FAQ, and the pricing page also
  say "Windows". Update them.
- **Cockpit:** `cockpit-worker/src/downloads.ts` counts only
  `BananaShot-win-Setup.exe` as a setup. Add the macOS installer.
- **Privacy texts:** `PrivacyPage.kt` and the site privacy page say "the
  version of Windows". Change to "the version of the operating system".
  Keep the two texts in agreement (`docs/feedback/privacy-notice.md`).
- **App texts:** `ContactPage.kt` says "the version of Windows". The help
  texts and the export texts say "PC". Change to platform-neutral words.
- **Windows-only messages:** the GPU restart message in
  `SwingApplicationFactory` names Windows Graphics Settings and `javaw.exe`.
  It does not show on macOS now, because `WindowsGpuPreference` makes no
  change there. Add a test that keeps this behavior.
- **README:** change "The app is for Windows x64 only" and add the macOS
  development steps (the natives script and the data folder).
- **Licensing documents:** `build-expiry-spec.md` and `threat-model.md`
  name Windows-only parts (`Windows-ROOT`, `GetTickCount64`, the setup EXE).
  Add the macOS parts.

## Order of the work

1. **Bundled fonts** (about 1 week). Section 5. Do this on Windows and
   release it in a Windows version. It does not need a Mac.
2. **Preview spike** (1 to 2 weeks). Select the embed method. If no method
   passes the checks, stop and tell the author before more work.
3. **Apple Developer account.** Start early. The approval can take days.
4. **Native files** (1 to 2 weeks). Build or select libmpv and FFmpeg for
   macOS arm64. Make the manifest, the script, and the natives release.
5. **Runtime layout and platform services** (about 1 week). Sections 3 and
   6. The app starts from source on a Mac with export and the preview.
6. **Preview implementation** (1 to 2 weeks). The method of the spike, with
   all overlays and input.
7. **Export** (a few days). Section 4.
8. **macOS app behavior** (a few days). Section 7.
9. **Packaging, signing, update** (1 to 2 weeks). Section 8. Do the
   Velopack checks first.
10. **CI, release, site, and texts** (about 1 week). Sections 9 and 10.
11. **Beta.** Give a build to a few Mac users before the public release.

## Open questions

- The price and the license on macOS: the same as on Windows?

## Costs

- Apple Developer Program: 99 USD for each year.
- An Apple Silicon Mac with macOS 15 for development and tests.
- GitHub macOS runners cost more minutes than Linux and Windows runners on a
  private repository.

## Done when

- A signed and notarized macOS build is in a GitHub release, next to the
  Windows build.
- A clean Apple Silicon Mac with macOS 15 installs and opens it with no
  Gatekeeper warning.
- The packaged smoke test of `qa/macos/ui-smoke.md` passes.
- "Update and restart" updates from the previous macOS version.
- The B-42 checks (sleep, proxy, TLS inspection) pass on a Mac.
- CI runs the tests on macOS and on Windows.

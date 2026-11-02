# B-13 macOS: Changes for macOS Support

This file tells what we must change to support macOS at the same level as
Windows. The backlog item is B-13 in `docs/backlog.md`.

The list comes from a review of the code on 2026-10-08 (version
`1.0.1-SNAPSHOT`). Items with "Verify" are not tested yet. Do a test of each
of them on a Mac before you start the related work.

The status of each step is in "Order of the work" at the end of this file.
Read it at the start of a work session. Update it when you finish or start a
step, in the same commit as the work.

## Decisions

The author made these decisions on 2026-10-08:

| Subject | Decision |
|---|---|
| CPU type | Apple Silicon (arm64) only. No Intel Mac build |
| Minimum macOS version | The version that covers 95% of Mac users. Now this is macOS 15 (see "Minimum macOS version") |
| Scoreboard fonts | Replace the fonts on all platforms with bundled open fonts (see "5. Fonts") |
| Installer and update | Velopack, if it works well on macOS. If it does not, use a DMG (see "Installer and update") |
| Price and license | The same as on Windows |
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
| GPU choice | Registry key `UserGpuPreferences` | Not necessary. macOS selects the GPU. Skip on macOS | `WindowsGpuPreference.kt` |
| Encoder choice | `powershell.exe Get-CimInstance Win32_VideoController` | VideoToolbox is always the hardware encoder on a Mac | `FFmpegCapabilities.kt` |
| Hardware encoders | `h264_nvenc`, `h264_amf`, `h264_qsv` | `h264_videotoolbox` | `export/EncoderCapabilities.kt`, `FFmpegCommandBuilder.kt` |
| Run time counter | `Kernel32.GetTickCount64` (counts sleep) | A clock that counts sleep (see "6. Platform services") | `license/time/RunTimeCounter.kt` |
| File in use | `Kernel32.CreateFile` sharing check | No equivalent. It returns false now. Keep it | `media/FileWriteCheck.kt` |
| Trust store | `Windows-ROOT`, module `jdk.crypto.mscapi` | `KeychainStore` (Verify the root store type) | `license/online/UpdateTrust.kt`, `DistributionDiagnostics.kt` |
| Blocked files | Error codes 225, 226, 1260, 4550-4559 (Smart App Control) | Gatekeeper and quarantine messages | `WindowsSecurityBlock.kt`, `media/VideoProblem.kt`, `export/ExportFailureAdvice.kt` |
| Show in folder | `Desktop.open`, then `explorer.exe` | `Desktop.open`, then `open -R <file>` | `ui/tabs/export/ExportsTable.kt` |
| Update | Download `BananaShot-win-Setup.exe` and start it | Download the macOS installer and open it | `license/update/UpdateAndRestart.kt`, `UpdateOptions.kt` |
| Velopack hooks | `--veloapp-*` arguments | Verify which hooks Velopack runs on macOS | `VelopackHooks.kt` |
| UI font | `Segoe UI` set in `SwingMainApp` | The macOS system font | `SwingMainApp.kt`, `ui/commons/UiKit.kt`, `ExportUi.kt`, `ProjectsUi.kt` |
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

### Result of the first spike (2026-11-01)

Test Mac: MacBook Air M1, macOS 27.2, JDK 27, Homebrew mpv 0.41.0.

- `Native.getComponentID` and `Native.getWindowID` give 0 on macOS. JNA
  gives no `NSView` and no `NSWindow` for AWT components.
- mpv 0.41 has no `wid` on macOS. The `macvk` context of `gpu-next`
  always makes its own `NSWindow`. Thus method 2 does not work.
- The render API (method 1 and method 3) uses the `gpu` renderer, not
  `gpu-next`. `glsl-shader-opts` works only with `gpu-next`, so the color
  and geometry controls of the preview do not work with the render API.
- **Selected method: a child window.** mpv makes its own borderless window
  (`force-window=yes`, `border=no`, `input-cursor-passthrough=yes`,
  `focus-on=never`, `auto-window-resize=no`). The `window-id` property
  gives the `NSWindow` pointer. `MacVideoWindow` attaches it as a child
  window of the Java window and keeps its frame equal to the screen bounds
  of the canvas. The app finds the `NSWindow` of the Java window in
  `[NSApp windows]` by its frame and title, because the JDK has no public
  API for it. All AppKit calls run on the main thread with
  `dispatch_async_f`.
- The test harness showed: `hwdec-current=videotoolbox`,
  `current-vo=gpu-next`, the OSD size follows the canvas at the Retina
  scale (2000x1166 pixels for 1000x583 points), the Java window keeps the
  keyboard focus, and a tab change and a close do not block the app.
- Not verified yet (the test shell has no Screen Recording and no
  Accessibility permission): the picture on the screen, the mouse input
  through the mpv window, Swing popups and dialogs over the video, full
  screen, and two displays. For these checks, give the Screen Recording and
  the Accessibility permission to the app that runs the test (for example
  the terminal or the Claude app) in System Settings → Privacy & Security.
  Then restart that app.
- Risk: mpv calls `DispatchQueue.main.sync` when it makes and closes its
  window. The app closes mpv on a separate thread. `mpv_initialize` still
  runs on the event dispatch thread. If the main thread waits for the event
  dispatch thread at that time (for example, VoiceOver), the app stops.
- In a source run (not a bundle), mpv sets its own Dock icon.

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

### Result of the build script (2026-11-02)

`distribution/macos/build-natives.sh` builds the native files from pinned
sources on an Apple Silicon Mac. Each tarball has a SHA-256 in the script.
libplacebo and x264 have a pinned Git commit. The output is in
`target/native/macos-arm64`:

| File | License | Content |
|---|---|---|
| `mpv/libmpv.2.dylib` (30 MB) | LGPL-2.1-or-later | mpv 0.41.0 with `-Dgpl=false`. FFmpeg 9.0.2 (no `--enable-gpl`), libplacebo 7.387.0, libass 0.17.5, FreeType, FriBidi, HarfBuzz, Little CMS, and glslang are static libraries in it |
| `mpv/libmpv.dylib` | — | A link to `libmpv.2.dylib`. JNA loads the name `mpv` |
| `mpv/libMoltenVK.dylib` (5 MB) | Apache-2.0 | MoltenVK 1.4.2, the KhronosGroup release file, arm64 only. A MoltenVK build needs Xcode |
| `ffmpeg/bin/ffmpeg`, `ffprobe` (25 MB each) | GPL-2.0-or-later | FFmpeg 9.0.2, static, with libx264, libass, and VideoToolbox |
| `LICENSES/`, `BUILD-INFO.txt` | — | The license texts, the sources, and the FFmpeg configuration |

Decisions:

- **Two FFmpeg builds.** The app has the Elastic License 2.0, not the GPL.
  libmpv runs in the process of the app, so libmpv and all the code in it
  must be LGPL or more permissive, as on Windows. Only the `ffmpeg` and
  `ffprobe` programs contain GPL code (x264). The app runs them as separate
  processes.
- **Static libraries in libmpv.** Only two dylibs, and no Homebrew paths to
  fix. A user can still replace `libmpv.2.dylib` with an own build (LGPL).
- **No Vulkan loader.** libmpv links MoltenVK directly. Thus no ICD file is
  necessary in the bundle.
- **No Lua and no JavaScript.** The app uses only the client API. mpv
  rejects the options `ytdl` and `osc` of the adapter with a warning, because
  these options come from the Lua scripts. The warning is harmless.
- **The only build tools from Homebrew** are meson, ninja, cmake, and
  pkgconf. The script hides the `.pc` files, the CMake packages, and
  `sdl2-config` of Homebrew.

The script checks each Mach-O file: arm64 only, minimum macOS 15.0, a valid
ad-hoc signature, and only libraries of macOS (`/usr/lib`, `/System`) or of
the same folder. It also checks the `subtitles` filter, `libx264`, and
`h264_videotoolbox`, that libmpv has `-Dgpl=false` and an LGPL FFmpeg with no
x264 encoder, and that libmpv starts.

Test on the M1 (macOS 27.2) with the new files:

- The diagnostics (`--diagnostics`) pass. They now check arm64 and
  `libmpv.dylib` on macOS.
- The FFmpeg tests pass: `FfmpegToneFilterParityTest`, `UserTextExportTest`,
  `NonSquarePixelExportTest`, `VideoToolboxExportTest`, and
  `SourceVideoEndProbeTest`. An ASS text with libass and the macOS fonts
  encodes with x264 and with VideoToolbox.
- `MpvCropTabSpikeMain` shows the video in the preview with the child window,
  the rotation, the zoom, and the crop frame. MoltenVK writes the warning
  "Metal does not support disabling primitive restart" to stderr. The
  Homebrew build does not show it. The picture is correct.

The workflow "macOS natives" (`.github/workflows/macos-natives.yml`) runs
the script on a `macos-15` runner and uploads the release files as an
artifact. The first run on 2026-11-02 took 14 minutes on macOS 15.8.2
(Apple clang 17, Swift 6.1.2). All checks passed, and libmpv started. Thus
macOS 15 has all the system libraries that libmpv links (also the Swift
libraries). The files of that run also pass the diagnostics and the export
tests on the M1 with macOS 27.2.

Not verified yet: the live preview on a Mac with macOS 15.

### Manifest and natives release

Done on 2026-11-02. The natives release `natives-2026-11` has the files of
the workflow run 41495949661 and their source. The macOS release has its own
tag. The Windows files stay in `natives-2026-09`. The steps for a new natives
release are in "Natives release" in `distribution/macos/README.md`.

- Add `distribution/macos/native-dependencies.json` with the same schema:
  version, URL, SHA-256, source URL, and source SHA-256 for each file. Set
  `architecture` to `macos-arm64`. The steps are in "Natives release" in
  `distribution/macos/README.md`.
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
  data folder. Done on 2026-11-02. The app does not move an old
  `~/.bananashot` folder, because only development runs used it.
- `ApplicationLayoutResolverTest`: add the macOS cases.

## 4. Export

Done on 2026-11-01, except the Worker deployment.

- **Encoder:** `ExportEncoder.VIDEOTOOLBOX` (`h264_videotoolbox`, job label
  "H.264 (VideoToolbox)"). The encoder detection finds it in the encoder
  list of ffmpeg, keeps the real test encode, and prefers it when it works.
  Only an ffmpeg for macOS has it, so no GPU check is necessary.
- **Measurement** (M1, a 30 s 1080p clip, VMAF against the source):

  | Encoder | Settings | Time | Size | VMAF |
  |---|---|---|---|---|
  | x264 | `slow`, 20 Mbit/s | 20.4 s | 67.8 MB | 98.1 |
  | VideoToolbox | 20 Mbit/s, default settings | 4.7 s | 18.4 MB | 96.9 |
  | VideoToolbox | 20 Mbit/s, `-profile:v high -prio_speed 0` | 4.7 s | 58.8 MB | 97.6 |
  | VideoToolbox | constant bitrate 20 Mbit/s | 4.7 s | 73.8 MB | 96.9 |
  | VideoToolbox | `-q:v` 65 / 75 / 85 | 4.6 s | 4.2 / 8.4 / 21.5 MB | 94.4 / 96.4 / 97.5 |
  | x264 | `medium`, CRF 21 | 6.7 s | 4.3 MB | 96.4 |

  With the default settings, VideoToolbox uses much less than the bitrate.
  Constant bitrate makes larger files with no better quality.
- **Arguments:** `-profile:v high -prio_speed 0` and the bitrate of the
  export. A job with no bitrate gets `-q:v` from the CRF: 75 for CRF 21,
  2.5 steps for each CRF step (`FFmpegCommandBuilder.videoToolboxQuality`).
  Verify the quality with fast tennis and padel footage.
- **Texts:** a description for VideoToolbox in all languages. "PC" is now
  "computer" in the texts that a Mac user sees ("Best for this computer",
  no hardware encoder, the software encoder). The NVIDIA, AMD, and Intel
  texts keep "PC".
- **Analytics:** the `videotoolbox` keys are in
  `analytics-contract/v1/counter-keys.json`, the analytics Worker, the
  cockpit, `docs/analytics/design.md`, and the privacy page of the site. The
  Worker refuses a summary with an unknown key. Thus **deploy the analytics
  Worker and the cockpit before a macOS release**, and also before a test
  export with VideoToolbox sends a summary to the production Worker.
- **Hardware decode in the export:** the export uses no hardware decoder.
  No change is necessary.

## 5. Fonts

### UI font

`SwingMainApp` sets `Segoe UI` as the default font, and `UiKit`, `ExportUi`, and
`ProjectsUi` ask for `Segoe UI` or `Segoe UI Semibold`.
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

Select the fonts in a design review with screenshots of all 17 styles
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

- **GPU preference (B-35):** call
  `WindowsGpuPreference.ensureHighPerformancePreference` only on Windows
  (it returns at once on other systems now). Apple Silicon Macs have one
  GPU, so no GPU choice is necessary. The app shows no GPU message on any
  system.
- **Run time counter (expiry):** done on 2026-11-01. `RunTimeCounter.production`
  uses `clock_gettime_nsec_np(CLOCK_MONOTONIC)` on macOS. This clock counts
  the time while the Mac sleeps. The counter tries the macOS clock and
  `GetTickCount64`, and keeps the first one that works. It does not read
  `os.name`, because the expiry reads no property. Verify with the sleep
  check of B-42 on a Mac.
- **Trust store:** done on 2026-11-01. `UpdateTrust` loads the bundled
  `cacerts` and each system store type that the runtime has:
  `Windows-ROOT`, `KeychainStore-ROOT`, and `KeychainStore`. It skips an
  empty store. On the M1 test Mac (macOS 27.2, JDK 27), `KeychainStore-ROOT`
  has 150 roots and `KeychainStore` has 0 certificates. The diagnostics check
  is now "System trust store". It checks `KeychainStore-ROOT` on macOS.
  Still to do: verify with an office TLS proxy that its root shows in
  `KeychainStore` (B-42). Remove `jdk.crypto.mscapi` from the macOS runtime
  modules.
- **Blocked files:** done on 2026-11-02. A test on the M1 (macOS 27.2)
  showed the two cases. A libmpv with no valid signature does not load. The
  dyld text has "missing code signature" (or "code signature ... not valid",
  "disallowed by system policy", "different Team IDs"). An ffmpeg with no
  valid signature starts, but macOS stops it at once with SIGKILL: exit code
  137 and no output. A quarantine attribute alone also blocks a file with
  only an ad-hoc signature (no notarization): the same exit code 137 for
  ffmpeg, "library load disallowed by system policy" for libmpv, and a
  Gatekeeper dialog for each file. `MacSecurityBlock` finds both cases. `VideoProblem`
  and `ExportFailureAdvice` then show macOS texts in all languages. The texts
  tell the user to install the app again from the site. The export shows the
  ffmpeg text only when ffmpeg wrote nothing: with `-v info`, an ffmpeg that
  runs always writes text. The Windows codes stay on Windows.
- **Proxy:** `java.net.useSystemProxies` also reads the macOS proxy
  settings. The comments now say "the proxy of the system" (done on
  2026-11-02). Still to do: the proxy check of B-42 on a Mac.
- **File in use:** `FileWriteCheck` returns false on macOS. macOS has no
  sharing lock of this type. Keep it.
- **Show in folder:** done on 2026-11-02. `ShowInFolder` opens the folder
  and selects the file: `open -R <file>` on macOS, `explorer.exe /select,`
  on Windows. The export table and the share panel use it. On Windows, the
  export table now also selects the file, as the share panel did before.

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
- Declare the languages of the app in `Info.plist`
  (`CFBundleLocalizations`, one entry for each `AppLanguage`). Without it,
  the native dialogs of macOS (for example the file dialogs) stay in English
  (B-11 E4-T13).
- Set `LSMinimumSystemVersion` in `Info.plist` to the minimum macOS version
  (see "Minimum macOS version"). Build the libmpv and FFmpeg files with the
  same deployment target.
- Copy the natives, the fonts, and the legal files into the bundle.
- Add `distribution/macos/validate-app-image.sh` with the same checks as
  `Validate-AppImage.ps1` (version, build and expiry dates, runtime
  modules, feedback and analytics properties).
- Until the Developer ID certificate exists, sign the bundle with an ad-hoc
  signature (`codesign -s -`), from the inside to the outside. An Apple
  Silicon Mac does not run arm64 code with no signature. An ad-hoc bundle
  opens on the development Mac. Gatekeeper blocks it on other Macs. Make the
  signing identity a parameter of the script, so that step 11 only gives
  the Developer ID.

### Result of the app bundle (2026-11-02)

`distribution/macos/build-app-image.sh` makes
`target/package/app-image/BananaShot.app` (223 MB: runtime 122 MB, native
files 81 MB, JAR files 19 MB). `validate-app-image.sh` checks it. The steps
are in `distribution/macos/README.md`.

- **Layout.** jpackage puts the JAR files in `Contents/app`, so the app home
  is `Contents`. The native files are in `Contents/natives/macos-arm64`, the
  legal files in `Contents/legal`. `codesign --verify --deep --strict`
  accepts the bundle with the native files in this folder.
  `ApplicationLayout` finds the launcher in `Contents/MacOS/BananaShot`.
- **Bundle ID** `app.banana-shot-editor.bananashot`. Do not change it after
  a release: macOS keys the permissions and the settings of the app to it.
- **Icon.** `MacIconRenderer.java` renders the SVG with the margin of the
  Apple grid (an 824-pixel tile on 1024 pixels). `make-icon.sh` makes
  `assets/BananaShot.icns` with `iconutil`.
- **Runtime.** The modules of Windows without `jdk.crypto.mscapi`. `jdeps` on
  macOS (2026-11-02) gives the same list as on Windows. The script copies
  `bin/java` into the runtime for `validate-app-image.sh`, as on Windows.
- **JNA and FlatLaf.** Both JAR files contain macOS native files, and both
  libraries copy them to a temporary folder at run time. The hardened
  runtime refuses such a file, and notarization refuses a JAR with an
  unsigned native file. The script copies the arm64 files to
  `natives/macos-arm64/java`, removes all macOS native files from the JAR
  files of the bundle, and sets `jna.boot.library.path`, `jna.nounpack`,
  and `flatlaf.nativeLibraryPath`. A run of the bundle loaded both files
  from the bundle and none from a temporary folder.
- **Info.plist.** `LSMinimumSystemVersion` 15.0, `LSArchitecturePriority`
  arm64, `CFBundleLocalizations` with the 11 languages.
- **Signature.** `--sign <identity>`. The default `-` is ad-hoc, with no
  hardened runtime: with the hardened runtime, an ad-hoc process refuses
  libraries with no Team ID. With a Developer ID, the script uses the
  hardened runtime, a timestamp, and `entitlements.plist` (`allow-jit` and
  `allow-unsigned-executable-memory`). Give the SHA-1 hash of the
  certificate as the identity when the keychain has two copies of it.
- **Test on the M1 (macOS 27.2, JDK 27, ad-hoc).** `validate-app-image.sh`
  passes. `BananaShot.app/Contents/MacOS/BananaShot --diagnostics` passes:
  libmpv, FFmpeg, and FFprobe from the bundle. `open BananaShot.app` starts
  the app with no warning in the log.

- **Locale.** The first test by hand showed "The video player cannot start"
  in the bundle: `mpv_create` returned null. mpv refuses to start when the C
  locale for numbers (`LC_NUMERIC`) is not "C". An app that the Dock starts
  gets the locale of the user (here `ru_RU.UTF-8`). A start from a terminal
  has "C", so the source runs did not show it. `CNumericLocale` now sets
  `LC_NUMERIC` to "C" before each `mpv_create` (not on Windows).
  `MpvLocaleStartTest` starts mpv with a Russian locale.

After the locale fix, the author opened a project in the bundle from the
Dock on 2026-11-02: the video plays in the preview, and an export works.

Not verified yet: a build with JDK 25.

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

### Apple Developer account

Signing and notarization need a paid Apple Developer Program membership
(99 USD for each year). A free Apple Account cannot make a Developer ID
certificate and cannot notarize. If the membership stops, the versions that
are already notarized still open. A new version cannot be signed until the
membership is renewed.

Status: the enrollment was sent on 2026-11-02. Apple approved it on
2026-11-02. Steps 1 to 6 are done (2026-11-02). The six secrets are in the
`macos-release` environment. The development Mac has a valid Developer ID
Application identity and a valid Developer ID Installer identity. The Developer
ID Application certificate expires on 2027-12-02. Make a new one before that
date, and update the two `MACOS_SIGNING_P12_*` secrets.

The author does these steps. Do not put the certificate, the key, or the
passwords in a chat, a file of the repository, or a log.

1. Enroll on developer.apple.com/programs/enroll, or in the Apple Developer
   app from the App Store. Pay the fee. Wait for the e-mail "Welcome to the
   Apple Developer Program". The Team ID is on the Membership page.
2. Make two certificate requests (CSR), one for each certificate. Apple
   refuses a CSR that already made a certificate ("An attribute specified
   more than once"). In Keychain Access, select Certificate Assistant →
   Request a Certificate From a Certificate Authority. Select "Saved to
   disk". Do this two times, and give the files different names (for example
   `developer-id-application.certSigningRequest` and
   `developer-id-installer.certSigningRequest`). The private keys stay in the
   login keychain. The development Mac has no Xcode, so do not use the Xcode
   steps.
3. On developer.apple.com, go to Certificates, IDs & Profiles →
   Certificates → +. Make a "Developer ID Application" certificate with the
   first CSR. Then make a "Developer ID Installer" certificate with the
   second CSR. Select the G2 Sub-CA for both. The Velopack `.pkg` needs the
   Installer certificate. Install the two `.cer` files with a double-click.
4. In Keychain Access, open login → My Certificates. Export the two
   Developer ID items as one `.p12` file with a strong password. Keep the
   file and the password in a password manager.
5. In App Store Connect, go to Users and Access → Integrations → App Store
   Connect API → Team Keys. Make a key with the "Developer" access for
   notarization. Download the `.p8` file at once. Apple lets you download it
   only one time. Write down the Key ID and the Issuer ID.
6. Put the values in the secrets of a new `macos-release` GitHub
   environment. The release workflow (step 12) will use these names:

   | Secret | Value |
   |---|---|
   | `MACOS_SIGNING_P12_BASE64` | The `.p12` file as base64 (`base64 -i file.p12`) |
   | `MACOS_SIGNING_P12_PASSWORD` | The password of the `.p12` file |
   | `APPLE_API_KEY_P8` | The text of the `.p8` file |
   | `APPLE_API_KEY_ID` | The Key ID |
   | `APPLE_API_ISSUER_ID` | The Issuer ID |
   | `APPLE_TEAM_ID` | The Team ID |

To check that the certificates are installed on the development Mac, run
`security find-identity -v -p codesigning`. It must show "Developer ID
Application" as a valid identity.

- If `security find-identity -p codesigning` (without `-v`) shows the
  identity, but `-v` shows 0 valid identities, the keychain does not have
  the Apple intermediate certificate "Developer ID Certification Authority"
  (G2). Get "Developer ID - G2" (`DeveloperIDG2CA.cer`) from
  apple.com/certificateauthority and install it in the login keychain with
  a double-click. The intermediate was installed on 2026-11-02.
- On the development Mac, select the identity by its SHA-1 hash, not by its
  name (`security find-identity -v -p codesigning` shows the hash). Then a
  copy of the certificate in a second keychain cannot make the name
  ambiguous.
- Keep the Developer ID certificates only in the login keychain. On
  2026-11-02, a copy of each certificate was also in the System keychain.
  If `codesign` tells that the identity is "ambiguous", delete the copies in
  the System keychain. The private keys are in the login keychain.
- The CI does not use the keychain of the development Mac. It imports the
  `.p12` file into a temporary keychain.

### Code signing and notarization

Without signing and notarization, Gatekeeper blocks the app. Since macOS 15,
a user cannot open a blocked app with right-click → Open. The user must go to
System Settings → Privacy & Security → "Open Anyway".

- Get the certificates and the API key (see "Apple Developer account").
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
- Read the certificate, its password, and the App Store Connect API key
  from the `macos-release` GitHub environment (see "Apple Developer
  account"). Only the macOS build job gets them, as in the Windows workflow
  (B-27).
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
  say "Windows". Update them. The pricing page must tell that the price and
  the license are the same on Windows and on macOS.
- **Cockpit:** `cockpit-worker/src/downloads.ts` counts only
  `BananaShot-win-Setup.exe` as a setup. Add the macOS installer.
- **Privacy texts:** `PrivacyPage.kt` and the site privacy page say "the
  version of Windows". Change to "the version of the operating system".
  Keep the two texts in agreement (`docs/feedback/privacy-notice.md`).
- **App texts:** `ContactPage.kt` says "the version of Windows". The help
  texts and the export texts say "PC". Change to platform-neutral words.
- **README:** change "The app is for Windows x64 only" and add the macOS
  development steps (the natives script and the data folder).
- **Licensing documents:** `build-expiry-spec.md` and `threat-model.md`
  name Windows-only parts (`Windows-ROOT`, `GetTickCount64`, the setup EXE).
  Add the macOS parts.

## Order of the work

Status on 2026-11-02. Update this table when a step changes.

The column "Apple account" tells if the step needs the approved Apple
Developer account. Do the steps with "No" while Apple reviews the enrollment.

| # | Step | Size | Apple account | Status |
|---|---|---|---|---|
| 1 | **Preview spike.** Section 1 | 1 to 2 weeks | No | Done on 2026-11-01. Method: a child window (see "Result of the first spike") |
| 2 | **Export.** Section 4 | A few days | No | Done on 2026-11-01. Still to do: deploy the analytics Worker and the cockpit (step 12) |
| 3 | **Apple Developer account.** Section 8, "Apple Developer account" | Days of waiting | — | Done on 2026-11-02. The certificates, the API key, and the six secrets of the `macos-release` environment are in place. The Application certificate expires on 2027-12-02. Not tested yet: a real signature and a notarization (step 11) |
| 4 | **Native files.** Section 2. Pinned, self-contained libmpv and FFmpeg for macOS arm64, the manifest, the script, and the natives release | 1 to 2 weeks | No | Done on 2026-11-02. `build-natives.sh`, the workflow "macOS natives" (it passes on macOS 15), the natives release `natives-2026-11`, `native-dependencies.json`, `get-native-dependencies.sh`, and the macOS part of `THIRD-PARTY-NOTICES.txt`. Not verified yet: the live preview on a Mac with macOS 15 |
| 5 | **Runtime layout and platform services.** Sections 3 and 6 | About 1 week | No | Done on 2026-11-02, except the manual B-42 checks on a Mac (sleep, proxy, TLS inspection). Done: the native folders, `LibMpv` on macOS, the run time counter, the trust store, the diagnostics checks, the data folder in `~/Library/Application Support`, the macOS texts for blocked files, "Show in folder" with `open -R`, and the proxy comments |
| 6 | **Preview implementation.** Section 1 | 1 to 2 weeks | No | Partly done. The child window works in a source run. To do: the checks of the spike that are "Not verified yet" |
| 7 | **macOS app behavior.** Section 7 | A few days | No | Not started |
| 8 | **Local app bundle.** Section 8, "App bundle" and "Architecture". Use an ad-hoc signature until the certificates exist | About 1 week | No | Done on 2026-11-02 (see "Result of the app bundle"). The preview and an export work in the bundle |
| 9 | **CI test job on macOS.** Section 9, "CI" and "UI tests" | A few days | No | Not started |
| 10 | **Bundled fonts.** Section 5. Release it in a Windows version before the macOS release. It does not need a Mac | About 1 week | No | Not started |
| 11 | **Signing, notarization, installer, and update.** Section 8, "Code signing and notarization", "Installer and update", and "Changes for Update and restart" | 1 to 2 weeks | Yes | Not started. Needs steps 3 and 8. Do the Velopack checks first |
| 12 | **Release workflow, site, and texts.** Sections 9 and 10. Deploy the analytics Worker and the cockpit before the release | About 1 week | Yes, for the release workflow | Not started |
| 13 | **Beta.** Give a build to a few Mac users before the public release | — | Yes | Not started |

Notes on the order:

- Step 4 is done. The app bundle (step 8), the signing (step 11), and the
  release workflow (step 12) use its pinned native files.
- The first order of 2026-10-08 had the fonts first. The fonts can start at
  any time. They must only be in a Windows release before the macOS release.
- If the preview implementation (step 6) finds a problem that the child
  window cannot solve, stop and tell the author before more work.

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

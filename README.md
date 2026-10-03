# BananaShot (Kotlin Desktop)

A desktop application that helps tennis players turn full‑match recordings into compact, watchable videos. You can remove dead time between points, track the score, and export a final video with a scoreboard overlay. Future versions add zoom/crop/reposition and color adjustments.

> Detailed architecture and workflows live in the Solution Outline:
> - 📄 [docs/solution-outline.md](docs/solution-outline.md)
> Roadmap, milestones, and changelog:
> - 🗺️ [docs/roadmap.md](docs/roadmap.md)

## Key features (scope)
- Manual trimming of empty time between points (mark in/out, ripple delete)
- Score entry during editing; live scoreboard overlay in preview
- Final render: cuts applied + overlayed scoreboard
- Future: zoom/crop/reposition, brightness/contrast, presets, hardware‑accelerated exports

## Tech stack (selected)
- UI: Compose Multiplatform Desktop (Kotlin/JVM)
- Preview: libmpv (JNA binding), GPU rendering with a custom FFmpeg-parity shader
- Export/render: FFmpeg (CLI initially), optional hardware encoding (NVENC/Quick Sync/Videotoolbox)
- Models & storage: Kotlin + kotlinx.serialization (EDL JSON)
- Packaging: jpackage (Windows/macOS/Linux)

Rationale, trade‑offs, and module plan are explained in the [solution outline](docs/solution-outline.md).

## Project status
- This repository currently contains a Kotlin/Maven skeleton.
- Implementation will proceed in phases; see the Roadmap below.

## Getting started (development)
### Prerequisites
- JDK 25 (the build and the bundled runtime use JDK 25; the bytecode target is 17)
- Maven 3.9+
- FFmpeg (ffmpeg/ffprobe) available in PATH (for export stage)
- Bundled libmpv (for preview at runtime)

On Windows, provision the pinned native runtime before launching from IntelliJ
or another source-run configuration:

```powershell
.\distribution\windows\Get-NativeDependencies.ps1
```

This places libmpv under `target/native/windows-x64/mpv` and FFmpeg under
`target/native/windows-x64/ffmpeg`, which source runs prefer over machine-wide
installations. Packaged Windows builds already include the same pinned runtime.

### Data folder of a development run

Only one instance of the app runs for each Windows account. The app locks
`instance.lock` in its data folder. By default, a development run uses the
same data folder as the installed app (`%APPDATA%\BananaShot`). Then an open
installed app blocks the development run: the run shows "BananaShot is
already running" and quits.

To use a separate data folder, set the system property
`-Dbananashot.appDataDir=<folder>` or the environment variable
`BANANASHOT_APP_DATA_DIR` in the run configuration.

### Usage analytics in a development run

A development run has no analytics properties. Thus it sends no analytics
and does not show the consent dialog. To see the dialog:

1. Add these VM options to the run configuration. Use a test Worker, or a
   local `wrangler dev` with an https tunnel. The app accepts only https.
   - `-Dbananashot.analytics.endpoint=https://<host>/v1/session`
   - `-Dbananashot.analytics.privacyUrl=https://<host>/privacy`
   - `-Dbananashot.analytics.noticeVersion=1`
2. Delete the values `analytics.choice` and `analytics.noticeVersion` in the
   preferences node `org/litvin` (on Windows, the registry key
   `HKCU\Software\JavaSoft\Prefs\org\litvin`). The app then asks again.

A development run that sends to the production Worker adds rows to the
production data. Do not do this.

### Build information and the build expiry

The Maven phase `generate-sources` makes
`target/generated-sources/kotlin-templates/org/litvin/license/BuildInfo.kt`
from `src/main/kotlin-templates`. It contains the build date (UTC) and the
version from `pom.xml`. Each build expires 6 calendar months after its build
date.

IntelliJ does not run `generate-sources`. An IDE run uses the `BuildInfo.kt`
of the last Maven build, with its old build date. If no Maven build ran for 6
months, an IDE run opens in expired mode. To fix this, run one Maven build:

```bash
mvn -DskipTests package
```

Warning: a development run and the installed app use the same preferences
(`HKCU\Software\JavaSoft\Prefs`), also with a different data folder. Thus,
they share the saved time and the expired-mode flag. A manual clock test with
a development build changes the installed app. For example, a clock set to
2028 makes the installed app open in expired mode until it gets a server time.

### Build
```bash
mvn -DskipTests package
```

### Tests
- Run full test suite:
  ```bash
  mvn test
  ```
- Quick run for architecture dependency hygiene only:
  ```bash
  mvn -q -Dtest=ArchitectureDependencyHygieneTest test
  ```

## Testing UI flows

UI changes should use the narrowest test lane that gives useful confidence. Run
these commands from the repository root with JDK 25 selected.

| Lane | Command | Use it for |
| --- | --- | --- |
| Fast checks | `mvn -B test` | General development and non-UI regressions. |
| Deterministic Swing flows | `mvn -B -Pui-flow verify` | Every UI-affecting change: import/restart, editing across tabs, scoring, export configuration, and recovery flows. It uses fake media/export services so it is repeatable and fast. |
| Packaged Windows smoke | `pwsh -File qa/windows/Run-UiSmoke.ps1 -KeepArtifacts` | Before a release or after changing mpv, FFmpeg, packaging, or native UI integration. It drives the real packaged application against the checked-in sample clip. |

The UI-flow suite creates isolated app data and retains diagnostics under
`target/ui-test-artifacts` only when a test fails. The packaged smoke runner
prints its isolated app-data directory, report, and artifacts paths under
`target/ui-smoke`; these outputs are intentionally ignored by Git.

For the exact packaged-smoke checklist and report requirements, see
[qa/windows/ui-smoke.md](qa/windows/ui-smoke.md). The runner first runs
`NativeSmokeIT` (Maven profile `ui-smoke`) with the natives of the packaged
app. This test does a real FFmpeg export and checks that the adjustment
controls change the mpv preview and the exported video. Then you do the
manual checklist in the packaged app.

### Run (temporary)
A proper desktop entrypoint (Compose Desktop) will be added with dependencies and packaging. For now, the skeleton app is minimal and only for verifying the toolchain.

## CI
- GitHub Actions workflow runs `mvn test` on pushes and pull requests to `main`/`master`.
- The architecture dependency hygiene test (`org.litvin.ArchitectureDependencyHygieneTest`) is part of the suite and will fail the build on violations.

## High‑level architecture
See: [docs/solution-outline.md](docs/solution-outline.md)

## Roadmap (high level)
- v0.1 (MVP): Open video, mark segments, simple scoreboard, save/load project, basic export with overlay.
- v0.2: Better scrubbing, thumbnails, undo/redo, presets, HW accel paths.
- v0.3: Proxy media, color adjustments, richer themes, in‑process FFmpeg.

## Contributing
- File issues and proposals referencing sections of the [solution outline](docs/solution-outline.md).
- Keep EDL/score models versioned and migration‑ready.

## Privacy

### Usage analytics
- A build can send optional usage analytics: anonymous counts of the tabs
  and features in use, export results, and session length. The app asks
  first, and you can turn the analytics off in More → Privacy. See
  [docs/analytics/privacy-notice.md](docs/analytics/privacy-notice.md) and
  [docs/analytics/design.md](docs/analytics/design.md).

### Version check and updates
- This is separate from the usage analytics. It is necessary for the license
  (the build expiry), so you cannot turn it off.
- The app reads a small file from GitHub when it starts and from time to time
  while it runs: `release/version-policy.json` in this repository, on
  `raw.githubusercontent.com`. The file tells if this version still works and
  if a new version is available.
- "Update and restart" downloads the setup EXE of the new version from GitHub.
- These requests send no user ID and no analytics data. GitHub receives the IP
  address and the standard HTTP headers.
- Details: "Privacy" in
  [docs/licensing/build-expiry-spec.md](docs/licensing/build-expiry-spec.md).

## Licensing & third‑party components
- BananaShot is source-available software under the Elastic License 2.0
  (ELv2). See [LICENSE](LICENSE) and [LICENSE-NOTICE](LICENSE-NOTICE). ELv2 is
  not an OSI open source license. You must not bypass the license key
  functionality that LICENSE-NOTICE describes.
- Versions up to the `last-gpl` tag were released under the GNU General Public
  License version 3 or later.
- Binary releases include the application source, an SBOM, native dependency
  provenance, and third-party notices. The bundled FFmpeg and libmpv builds
  and their source are in the
  [natives release](https://github.com/zipper2110/banana-shot/releases/tag/natives-2026-09).
- libmpv (LGPL build, `-Dgpl=false`) — loaded in the app process, shipped
  unmodified, and replaceable by users.
- FFmpeg (GPL build) — runs only as a separate `ffmpeg.exe` process. An
  in-process FFmpeg must be an LGPL build.
- Fonts — ensure redistribution rights (e.g., OFL fonts like Roboto).

## Acknowledgements
- FFmpeg, mpv, Kotlin, and JetBrains Compose teams for awesome tooling.

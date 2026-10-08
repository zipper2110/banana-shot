# BananaShot

A desktop application that helps tennis players turn full‑match recordings into compact, watchable videos. You can remove dead time between points, track the score, and export a final video with a scoreboard overlay.

- Site and download: <https://banana-shot-editor.app/>
- Releases: [GitHub releases](https://github.com/zipper2110/banana-shot/releases)

## Documentation

- Open work and the order of the work: [docs/backlog.md](docs/backlog.md)
- Package and dependency rules: [docs/architecture-rules.md](docs/architecture-rules.md)
- Steps of a release: [docs/release-checklist.md](docs/release-checklist.md)
- Windows packaging: [distribution/windows/README.md](distribution/windows/README.md)
- UI test lanes and the packaged smoke checklist: [qa/windows/ui-smoke.md](qa/windows/ui-smoke.md)
- Landing site: [site/README.md](site/README.md)
- Usage statistics server: [analytics-worker/README.md](analytics-worker/README.md)
- Feedback server: [feedback-worker/README.md](feedback-worker/README.md)
- Private dashboard of the numbers (cockpit): [cockpit-worker/README.md](cockpit-worker/README.md)
- Marketing strategy and the plan for the first users: [docs/marketing/strategy.md](docs/marketing/strategy.md)
- Changes for padel support (B-14): [docs/padel/b-14-padel.md](docs/padel/b-14-padel.md)

## Key features

- Projects: create a project from a video. Open, rename, and delete projects.
- Points: mark the start and the end of each point. Edit points, delete
  points, and mark favorites. Add comments.
- Scoring: give each point a winner. The app computes the games, sets, and
  tiebreaks for the selected match format.
- Scoreboard: 16 scoreboard styles, with a position, player colors, and
  other settings.
- Statistics: statistics of the match and of each set.
- Colors and Transform: color, crop, and rotation of the video.
- Export: the full video, only the points, or only the favorites. The export
  can show the scoreboard, the comments, a statistics card, and set
  summaries. A queue keeps the exports after a restart. The export can use
  hardware encoding.
- Help: an overview page and a help page for each tab.
- Feedback: send a problem, an idea, or a question to the author from the
  app.
- Updates: the app shows when a new version is available. "Update and
  restart" installs it.

## Tech stack

- UI: Swing with FlatLaf (Kotlin/JVM)
- Preview: libmpv (JNA binding), GPU rendering with a custom FFmpeg-parity shader
- Export: FFmpeg as a separate process, optional hardware encoding
- Project files: JSON (Jackson)
- Runtime: a bundled JDK 25 runtime
- Packaging: Velopack setup EXE for Windows, signed with a Certum code
  signing certificate
- Servers: Cloudflare Workers with D1 for the usage statistics, the feedback
  reports, and the cockpit. The landing site is static HTML on Cloudflare.

## Project status

- Version 1.0.0, the first public release, came out on 2026-10-06. The
  development version is `1.0.1-SNAPSHOT`.
- The app is for Windows x64 only. macOS is a post-release item (B-13).
- The open work is in [docs/backlog.md](docs/backlog.md).

## Getting started (development)
### Prerequisites
- JDK 25 (the build and the bundled runtime use JDK 25; the bytecode target is 17)
- Maven 3.9+
- FFmpeg (ffmpeg/ffprobe) and libmpv. The script below gets the pinned
  versions.

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

A development run has no analytics properties. Thus it sends no usage
statistics and does not show the consent dialog. With the properties, the run
sends the essential statistics at once. To see the dialog:

1. Add these VM options to the run configuration. You can use the
   production Worker. A development run sends the version `<x>-SNAPSHOT`,
   so you can find its rows (B-9 decision 18). The app accepts only https.
   - `-Dbananashot.analytics.endpoint=https://<host>/v1/session`
   - `-Dbananashot.analytics.privacyUrl=https://<host>/privacy`
   - `-Dbananashot.analytics.noticeVersion=1`
2. Delete the values `analytics.choice` and `analytics.noticeVersion` in the
   preferences node `org/litvin`. On Windows, they are in the registry key
   `HKCU\Software\JavaSoft\Prefs\org\litvin`. Java writes a `/` before each
   capital letter, so the second name is `analytics.notice/Version`. The
   installed app uses the same values. The app then asks again:

   ```powershell
   Remove-ItemProperty -Path 'HKCU:\Software\JavaSoft\Prefs\org\litvin' -Name 'analytics.choice','analytics.notice/Version' -ErrorAction SilentlyContinue
   ```

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

### Run

The main class is `org.litvin.SwingMainApp`. To start the app from Maven:

```bash
mvn compile exec:java
```

`exec:java` ignores `-Dexec.mainClass`. To start a different main class, use
`-Dapp.mainClass=<class>`.

## CI
- `ci.yml` runs `mvn test` on pushes and pull requests to `main`/`master`.
- `windows-release.yml` builds, tests, signs, and publishes a Windows release.
  A tag push makes a draft GitHub release. See
  [docs/release-checklist.md](docs/release-checklist.md).
- `release-age-reminder.yml` runs each Monday. It opens an issue when the
  newest release is older than 4 months, because each build expires 6 months
  after its build date.
- The architecture dependency hygiene test (`org.litvin.ArchitectureDependencyHygieneTest`) is part of the suite and will fail the build on violations.

## High‑level architecture
See: [docs/architecture-rules.md](docs/architecture-rules.md)

## Roadmap
The pre-release and post-release work is in [docs/backlog.md](docs/backlog.md).

## Contributing
- Read [CONTRIBUTING.md](CONTRIBUTING.md). The project does not accept
  outside code contributions now.
- Keep EDL/score models versioned and migration‑ready.

## Privacy

### Usage analytics
- A release build sends anonymous usage statistics at two levels. The
  essential statistics are on by default: app version, OS family, session
  length, error counts, and a range for the number of sessions. The extended
  statistics add counts of the tabs and features in use and the export
  results. The app asks at the first start. In More → Privacy, you can turn
  the extended statistics on or off, or turn off all statistics. See the
  [privacy notice](https://banana-shot-editor.app/privacy/) (source:
  [site/public/privacy/index.html](site/public/privacy/index.html)) and
  [docs/analytics/design.md](docs/analytics/design.md).

### Feedback reports
- The app sends a feedback report only when you click Send in the feedback
  form. The usage statistics choice does not change this.
- The log files are in the report only if you select "Attach the log files".
  "Show the data" shows the exact data before you send.
- The server keeps the report for 90 days and sends it to the author with a
  Telegram bot. See "Feedback reports" in the
  [privacy notice](https://banana-shot-editor.app/privacy/#feedback) and
  [feedback-worker/README.md](feedback-worker/README.md).

### Version check and updates
- This is separate from the usage statistics. It is necessary for the license
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
- [distribution/THIRD-PARTY-NOTICES.txt](distribution/THIRD-PARTY-NOTICES.txt)
  lists all components and their licenses, also the icon fonts.

## Acknowledgements
- FFmpeg, mpv, Kotlin, and FlatLaf teams for awesome tooling.

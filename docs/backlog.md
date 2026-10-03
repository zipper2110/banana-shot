# Backlog

This file lists the open work. For the steps of a release, see
`release-checklist.md`.

Remove an item when it is done. To change the order of the work, move an item
to a different section.

## Pre-release

Do these items before the first public release.

### B-4 License restriction

- `docs/licensing/build-expiry-spec.md` has the design (build expiry, version
  rules, update check). The spec was approved on 2026-10-02.
  `docs/licensing/l-5.2-epics.md` tracks the work.
- This must be in the first release. A build without an expiry stays free
  forever.
- Status 2026-10-03: E0 to E7 are done. E8 (the expiry user interface)
  is done. E9 waits for a manual check
  with a real release. The "Next work" list in `l-5.2-epics.md` has
  the stories that can start now.

### B-8 Requests for bug reports and features

- Ask users to send bug reports and feature requests.
- Decided on 2026-10-03: an in-app feedback form sends the report to a
  Cloudflare Worker, and the Worker sends it to the author with a Telegram
  bot. `docs/feedback/b-8-tasks.md` has the decisions and the tasks.
- Status 2026-10-03: the code of T1 to T5 is done. Open: deploy the Worker,
  make the bot, set the GitHub variable `FEEDBACK_ENDPOINT`, and do the
  manual checks of T5. The site text waits for B-10.
- Replies inside the app are B-33 (post-release).

### B-9 Analytics

- The app code is in `org.litvin.analytics`. The Worker is in
  `analytics-worker`. The Worker does not accept events until
  `ANALYTICS_INGESTION_ENABLED` is `true`.
- Finish the analytics work and turn it on.
- No build sets the three JVM properties that turn on analytics:
  `bananashot.analytics.endpoint`, `bananashot.analytics.privacyUrl`, and
  `bananashot.analytics.noticeVersion`. Without them, the app never shows
  the consent dialog and never sends analytics (found on 2026-10-03).
- Add `-AnalyticsEndpoint`, `-AnalyticsPrivacyUrl`, and
  `-AnalyticsNoticeVersion` to `Build-AppImage.ps1`, as
  `docs/analytics/design.md` says. Add the three values to
  `windows-release.yml` from GitHub variables. A shelved IntelliJ patch has
  this code with the old `tennis.record.` prefix. Do not use that prefix.
- The endpoint must be an `https` URL that ends in `/v1/events/batch`. The
  notice version must be `AnalyticsEventRegistry.NOTICE_VERSION` (now 1).
- To see the consent dialog in a dev run, add the three properties to the VM
  options. Also clear `analytics.choice` and `analytics.noticeVersion` in the
  application preferences.
- At the first start, the Overview help opens after the consent dialog
  closes (`AnalyticsConsentDialog.show(onClosed)`). Check this order on a
  fresh install with analytics turned on.

### B-10 Landing site

- Make a landing site for the app.

### B-21 First dry run of the release workflow

- The "Windows release" workflow has never run. Start a dry run now with
  the current installer. It can find problems before B-23 and B-24.
- B-26 does the install checks with the installer of the dry run.
- In the app image, run `runtime\bin\java --list-modules`. The JDK 17
  jpackage default can omit `jdk.crypto.ec` and `jdk.crypto.mscapi`, but
  `build-expiry-spec.md` requires them. Write the result in B-23.
- Done when: the dry run passes, and each problem that it finds is fixed or
  is an item in this file. Do the dry run again after B-23 and B-24.
- Status 2026-10-03:
  - The first dry run (version 0.0.1, commit 17bc4b8, Temurin 25.0.4) passed
    in CI. `mvn -B test`: 706 tests, 0 failures, 2 skipped.
    `Validate-AppImage.ps1` passed: the runtime has `jdk.crypto.mscapi` and
    `java.net.http`, and the expiry date is 2027-04-03. All packaged
    diagnostics passed (libmpv load, FFmpeg, `Windows-ROOT` with 564
    certificates, HTTPS). `vpk pack` made `BananaShot-win-Setup.exe` and the
    `.nupkg` package. The workflow artifact has 10 files (336 MB).
  - Expected warnings: the open L-5.2 stories (a real release fails until
    B-4 is done) and the unsigned files (B-22).
  - Not a problem: at the end of the job, the runner stopped an orphan
    `java` process (probably the Kotlin compile daemon). The Maven cache was
    not saved because a different job saved the same key.
  - Step 5 review of the artifact: the SHA-256 file agrees with the setup
    EXE. The SBOM has 20 Maven components. They are the same as the 20
    library JARs in `lib/app/app` of the `.nupkg`. All are `required`, and
    no test library is in the SBOM. The licenses are Apache-2.0, MIT,
    EPL-1.0 or LGPL (Logback), and LGPL-2.1+ or Apache-2.0 (JNA). All
    are acceptable. The `THIRD-PARTY-NOTICES.txt` of the artifact is the
    same as the file in the repository.
  - Problem: `THIRD-PARTY-NOTICES.txt` says that the license texts are in
    the JAR files. These 5 JARs have no license file:
    `kotlin-stdlib-2.2.20`, `kotlin-reflect-2.2.20`, `annotations-13.0`
    (Apache-2.0), `logback-classic-1.5.18`, and `logback-core-1.5.18`
    (EPL-1.0 or LGPL-2.1). Apache-2.0 and EPL-1.0 require a copy of the
    license with the binaries. The notices also do not name `jsvg` (MIT)
    and the JetBrains `annotations`.
  - Fixed 2026-10-03: "Java libraries" in `THIRD-PARTY-NOTICES.txt` now
    lists each library, its version, and its license. The end of the file
    has the full texts of the Apache License 2.0, the EPL-1.0, and the MIT
    License (with the SLF4J, JSVG, and Feather copyright lines). The file
    tells that BananaShot uses Logback under the EPL-1.0 and JNA under the
    Apache License 2.0. Step 6 of `release-checklist.md` now has a check
    that this list agrees with the SBOM.
  - Still to do: a new dry run with the fixed notices. Its installer is
    version N-1 for B-26.

### B-23 Bundle the JDK 25 runtime

- Build with JDK 25 (LTS) and bundle its runtime. Keep the bytecode target
  17 unless a reason to change it comes up.
- Update `ci.yml`, `windows-release.yml`, and the JDK 17 check in
  `Build-AppImage.ps1`. Update the JDK version in the docs.
- Add `--enable-native-access=ALL-UNNAMED` to the jpackage java options.
  JNA calls native code, and JDK 24 and later warn about it.
- Give jpackage an explicit `--add-modules` list. Use `jdeps` to find the
  modules. Add `jdk.crypto.mscapi` (the `Windows-ROOT` key store). In JDK 22
  and later, the elliptic-curve code is in `java.base`.
- Add checks to `DistributionDiagnostics`: the `Windows-ROOT` key store
  loads, and an HTTPS request to GitHub works.
- Check that Kotlin, JNA, FlatLaf, and assertj-swing work with JDK 25.
- Done when: `mvn -B test` and the ui-flow tests pass with JDK 25, and the
  packaged diagnostics pass.
- Status 2026-10-03:
  - Done: `ci.yml`, `windows-release.yml`, `Build-AppImage.ps1`, the docs,
    and the ui-flow spike test use JDK 25. The bytecode target stays 17.
    `--enable-native-access=ALL-UNNAMED` is in the jpackage java options and
    in `BananaShot Diagnostics.cmd`.
  - `jdeps` (JDK 25) gives java.base, java.desktop, java.naming,
    java.net.http, java.prefs, and java.sql. `Build-AppImage.ps1` gives
    `--add-modules` with these modules, java.logging, java.xml,
    jdk.crypto.mscapi, jdk.localedata, jdk.charsets, and jdk.accessibility.
  - `DistributionDiagnostics` checks that `Windows-ROOT` loads and that an
    HTTPS request to `raw.githubusercontent.com` gets an HTTP status.
  - `mvn -B test` passes with Temurin 25.0.2 (local, 2026-10-03). A test app
    image with the module list passed `Validate-AppImage.ps1` up to the
    version check (a local SNAPSHOT build) and the packaged diagnostics.
  - Kotlin 2.2.20, JNA 5.18.1, FlatLaf 3.7.2: compile and tests pass.
  - Found and fixed: in JDK 25, `File.getCanonicalPath` opens the file on
    Windows. `JsonFileIO` made its lock key with it before it took the lock.
    Thus, a reader blocked the move of a writer ("Access is denied"), and
    `JsonFileIOTest` failed. The key now comes from the normalized absolute
    path (`fileLockKey`).
  - `mvn -B -Pui-flow verify` passes with Temurin 25.0.2 (local,
    2026-10-03): 706 unit tests and 17 ui-flow tests, 1 skipped by design
    (`ValidationRecoveryUiFlowIT`). `AssertJSwingCompatibilityUiFlowIT`
    passes, so assertj-swing works with JDK 25.
  - The CI run of the release workflow passed with Temurin 25.0.4
    (2026-10-03, B-21): tests, `Validate-AppImage.ps1`, and the packaged
    diagnostics.
- All "Done when" conditions are true.

### B-24 Velopack installer

- Replace the jpackage EXE installer (WiX 3) with Velopack. WiX 3 is at its
  end of life. JDK 17 jpackage supports only WiX 3.
- Velopack installs for each user with no administrator rights. It also
  gives the update functions of B-30 and B-25. The installer of the first
  release sets how users update later. Thus, do this item before the first
  release.
- jpackage continues to make the app image. `vpk pack` makes the installer
  and the update packages from the app image.
- Velopack starts the app with hook arguments (`--veloapp-install`,
  `--veloapp-updated`, `--veloapp-uninstall`, and others) when it installs,
  updates, or removes the app. The app does not use a Velopack SDK. Thus,
  `SwingMainApp` must exit at once for these arguments and must not open
  the main window. Check the full list in the Velopack docs.
- The order in `main` is in "Velopack hook processes" in
  `build-expiry-spec.md`: the proxy property, then the hook check, then the
  lock of B-19.
- `vpk pack` checks that the app uses the Velopack SDK. The app does not use
  it, so the pack command probably needs `--skipVeloAppCheck` (a hidden
  option).
- The release workflow uploads the Velopack files (the setup EXE, the
  packages, and the release feed) to the GitHub release. Remove the WiX
  step and `Build-Installer.ps1`.
- Decided 2026-10-03: the first release has no installer license page. The
  files in `legal/` and More → About replace it. A license dialog at the
  first start is B-31.
- The uninstall must not delete the app data (`%APPDATA%\BananaShot`) or the
  preferences in `HKCU\Software\JavaSoft\Prefs`. `build-expiry-spec.md`
  requires this.
- Update `distribution/windows/README.md`, `release-checklist.md`,
  `ui-smoke.md`, and the paths in `Run-UiSmoke.ps1`.
- Done when: a dry run makes a Velopack installer. The installer
  installs the app, makes the Start menu shortcut, and the app starts
  (check 1 of B-26).
- Status 2026-10-03:
  - Done: `Build-VelopackRelease.ps1` runs `vpk pack` (vpk 1.2.161, pinned in
    `windows-release.yml`) with `--skipVeloAppCheck`, `--runtime win-x64`,
    and `--noPortable`. The setup EXE is `BananaShot-win-Setup.exe` (the
    default name of vpk for the pack ID `BananaShot` and the channel `win`).
    The workflow uploads it, the `.nupkg` package, and `releases.win.json`.
    The WiX step and `Build-Installer.ps1` are removed. Checked locally with
    Windows PowerShell 5.1 on a test app image.
  - `main` sets the proxy property, then exits with code 0 for each argument
    that starts with `--veloapp-`, then takes the lock. The Velopack docs
    (checked 2026-10-03) list `--veloapp-install`, `--veloapp-obsolete`,
    `--veloapp-updated`, and `--veloapp-uninstall`. The first start after the
    install and a restart are environment variables (`VELOPACK_FIRSTRUN`,
    `VELOPACK_RESTART`), so the app starts as usual for them. Tests:
    `VelopackHooksTest`, `StartOrderSourceTest`.
  - Velopack installs in `%LocalAppData%\BananaShot`. The app data
    (`%APPDATA%\BananaShot`) and the preferences are in other places.
  - `distribution/windows/README.md`, `release-checklist.md`, `ui-smoke.md`,
    and `Run-UiSmoke.ps1` (`-Installed`) are updated.
- License page, decided on 2026-10-03: the Velopack setup EXE is a one-click
  installer with no license page, and the first release has no license page.
  The license files stay in `legal/` of the app, and More → About opens them.
  No Velopack MSI. A license dialog at the first start is B-31 (after the
  first release).
  - The dry run of 2026-10-03 (B-21) made the Velopack installer in CI.
- Still to do: the install checks of B-26.

### B-30 Simple update from the app

- This is the simple form of the automatic update. B-25 is the full form,
  after the first release.
- In the update notice and the expiry dialog of `build-expiry-spec.md`,
  replace "Download update" with "Update and restart".
- When the user clicks it, the app downloads the setup EXE of the newest
  release to a temporary folder. Then it starts the setup EXE and closes.
  The Velopack setup installs the new version over the old version and
  starts it.
- The app must know the URL of the setup EXE. Add a field for it to the
  rules file, for example `latest.installerUrl`. The app reads it from the
  first release, so the field must be in the first release (see "Changes to
  the file format" in `build-expiry-spec.md`). If the field is missing, use
  the stable URL `releases/latest/download/<setup EXE name>`. Thus, the
  release workflow must give the setup EXE the same name in each release.
- The app does not check the URL or the file (decided on 2026-10-02, see
  "Accepted risks" in `build-expiry-spec.md`).
- The spec has the details: "Update and restart" in
  `build-expiry-spec.md`. The spec wins if this item and the spec do not
  agree.
- If an export runs, ask the user before the app closes. B-18 saves the
  queue, so the queued exports continue after the update. The running
  export starts again from the beginning.
- Show the download progress. If the download fails, show the error and
  keep "Download update" (open the download page in the browser) as the
  second option.
- Checked on 2026-10-02 in the Velopack source: over an existing install,
  the setup asks "Update" or "Cancel", kills each process that runs from the
  install folder, installs, and starts the new version. The spec has the
  rules that follow from this. Check 3 of B-26 confirms the behavior with
  the pinned Velopack version.
- Do this after B-4 and B-24.
- Done when: version N-1 shows the update notice for version N. The user
  clicks "Update and restart", and version N starts with the same projects
  and the same export queue (check 4 of B-26).
- Status 2026-10-03: the logic is done, the user interface is not.
  - `UpdateOptions` (in `org.litvin.license.update`) decides if "Update and
    restart" shows and gives the installer URL and the download page (E9-S1).
  - `UpdateAndRestart` asks first when an export runs, downloads the setup
    EXE to a temporary folder with progress (redirects followed), does the
    close sequence, starts the setup with no `--silent` as the last step,
    and exits. A failed download deletes the partial file and keeps the app
    open. A second click during the download has no effect (E9-S2).
  - Tests: `UpdateAndRestartTest` (fake download and setup start, and a local
    HTTP server for the real download).
- Still to do: the buttons in the update notice, the expiry warning, and
  expired mode (E8), and the wiring of the close sequence and the saved time
  (E7). The download uses the trust of E5-S4 (2026-10-03).

### B-26 Pre-release install testing

- This item has all the checks that need the app installed with
  `BananaShot-win-Setup.exe`. Other items refer to it: B-21, B-24, B-30,
  and E11 of `l-5.2-epics.md`. Do the checks together, on a clean Windows
  account.
- No automatic test installs a new version over an old version.
- You need two dry-run builds with different versions: version N-1 and
  version N.
- Checks with one build (version N-1):
  1. Fresh install: do the checks of step 7 of `release-checklist.md`
     (B-21, B-24). The setup installs with no administrator rights, makes
     the Start menu shortcut, and starts the app.
  2. Proxy: E11-S2 of `l-5.2-epics.md`.
- Checks with two builds:
  3. Install over a running app: start version N-1 and make a project. Then
     run the setup of version N. The app closes or the setup asks the user
     to close it. Version N starts. The projects, the preferences, and the
     export history stay. Confirm the Velopack behavior of B-30 with the
     pinned vpk version.
  4. "Update and restart" (B-30, E11-S3, and "Tests" in
     `build-expiry-spec.md`): in version N-1, click "Update and restart" for
     version N. The Velopack dialog shows. Version N starts with the same
     projects, preferences, and export queue. Also check that "Cancel" in
     the Velopack dialog installs nothing and leaves the app closed.
  5. Uninstall: the app data (`%APPDATA%\BananaShot`) and
     `HKCU\Software\JavaSoft\Prefs` stay.
- Before you start:
  - Checks 1 and 2 need the new dry run of B-21 (with the fixed notices).
  - Check 4 needs the user interface of B-30.
- Add checks 3 to 5 to step 7 of `release-checklist.md`. Automate them in
  the release workflow if it is possible.
- Done when: all checks pass with two dry-run builds. Write the result of
  each check in this item.

### B-27 Release workflow hardening

- Pin each third-party action to a commit SHA, mainly
  `softprops/action-gh-release` and the Azure actions. Keep the version as a
  comment.
- Now `mvn -B test` runs in the job that has the `contents: write` and
  `id-token: write` permissions. Run the tests in a separate job with
  read-only permissions. Give the write permissions only to the job that
  publishes.
- Done when: a dry run passes with the new jobs.

## Post-release

Do these items after the first public release.

### B-33 Replies to feedback inside the app

- Option C of B-8, decided on 2026-10-03. The author can reply to a report
  in the app. The user does not need to give an email address.
- The app keeps the `report_id` of each sent report. It checks the Worker
  for replies and shows a badge when a reply is available.
- The author can mark a report as fixed in a version. The app shows "Fixed
  in version X" after the update.
- Each report needs a secret that only the app knows. Without it, a person
  who knows a `report_id` must not read the replies.
- Do this after B-8 (`docs/feedback/b-8-tasks.md`).

### B-34 Social media accounts

- Decided on 2026-10-03: after the first release. Show the accounts in the
  app (the Contact and About pages in More) and on the landing site (B-10).
- Make the accounts only when the app name is final. Use the same name on
  all platforms.
- Most users are in Europe and the USA. Some are in Latin America and Asia.
  The analysis of 2026-10-03 (not decided):
  - YouTube: yes. It is the main place for tennis video in all regions. Use
    it for how-to videos, release videos, and match highlights.
  - Instagram: yes. It is strong in Europe, the USA, Latin America, India,
    and Southeast Asia. Use it for short clips made with the app and for
    direct messages.
  - Facebook page: optional. Meta Business Suite can copy the Instagram
    posts to it. It reaches older club players and Latin America.
  - TikTok: later, when there is time to make clips often. It is blocked in
    India.
  - WhatsApp channel: later, if many users are in Latin America. A channel
    is one-way, so use it for announcements such as "Fixed in 1.2".
  - Discord: later, at about 100 active users. It needs moderation.
  - Reddit (r/10s): take part, but do not show it as an account.
  - Not useful: X, Threads, Bluesky, LINE, KakaoTalk, WeChat.
- Each account that the app shows is a promise to answer messages there.
  Start with few accounts.
- The in-app feedback form of B-8 stays the main way to report a problem.
  Direct messages cannot attach the log.
- The icon pack of the app (Material2) has no brand logos. Use SVG files
  (JSVG) or an icon pack with brand icons.

### B-11 Localization

- Translate the user interface into more languages.

### B-12 Themes

- Let the user select a theme for the user interface.

### B-13 macOS

- Build, package, and test the app on macOS.

### B-14 Padel

- Support padel matches.

### B-15 Intro and outro videos

- Let the user add an intro video and an outro video to the exported video.

### B-16 Social media features

- Add features for social media use.

### B-17 Audio noise reduction

- Remove background noise from the audio in the exported video. Examples are
  rain, wind, and traffic noise.
- The sounds of the game (ball hits, calls) must stay clear.

### B-20 Build libmpv in our own CI

- The app now uses the LGPL libmpv build of `lpbborges/grid`. One person
  keeps that project, so it can stop.
- Copy its MIT-licensed MSYS2 workflow into this project or into a separate
  repository. CI must make a pinned LGPL libmpv and its source archive.

### B-22 Code signing

- The first release is unsigned. SmartScreen shows a warning for the
  installer. The user must click "More info", then "Run anyway".
- On a computer with Smart App Control on, Windows can block the unsigned
  installer or the unsigned natives. The user cannot select "Run anyway".
- Make sure that the author can use Azure Artifact Signing. It accepts
  individual developers only from some countries. If the author cannot use
  it, buy an OV certificate on a cloud HSM (for example Certum or SSL.com).
- Sign each EXE and DLL in the app image, not only `BananaShot.exe`. This
  includes `ffmpeg.exe`, `ffprobe.exe`, `libmpv-2.dll`, and the FFmpeg DLLs.
- Sign the installer and the update packages with the signing options of
  `vpk` (see B-24).
- Check if JNA extracts an unsigned `jnidispatch.dll` at run time. If it
  does, find a solution for Smart App Control.
- The release workflow must check the signature of each signed file.
- Done when: the installer of a dry run starts on a clean Windows 11
  computer with Smart App Control on, and the preview and the export work.

### B-25 Full automatic update with Velopack

- B-30 downloads the full setup EXE and shows the setup window. Replace it
  with the Velopack updater: delta packages, no setup window, and a restart
  into the new version.
- Velopack has no Java SDK. Bind its C library (`velopack_libc`) with JNA:
  check, download, and apply. Do a short test of the binding first.
- The release workflow downloads the previous release, makes a delta
  package, and uploads the feed file and the packages.
- Done when: version N-1 updates to version N with a delta package and no
  setup window, and version N starts with the same projects.

### B-28 winget package

- Add a winget manifest for each release. Users can then install with
  `winget install` and update with `winget upgrade`.
- Do this after B-22.

### B-29 Smaller installer

- libmpv contains its own FFmpeg libraries. Thus, the installer has two
  copies of FFmpeg. The FFmpeg archive is 81 MB compressed.
- Build FFmpeg in our own CI with only the codecs and filters that the app
  uses. Do this together with B-20.
- Measure the installer size before and after the change.

### B-31 License dialog at the first start

- The Velopack setup EXE shows no license page (B-24). Show the ELv2 text,
  `LICENSE-NOTICE`, and the third-party notices in a dialog at the first
  start of the app.
- Keep a flag in the preferences, so that the dialog shows only one time.
- Done when: the first start with empty preferences shows the dialog. The
  next start does not show it.

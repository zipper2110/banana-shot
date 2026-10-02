# Backlog

This file lists the open work. For the steps of a release, see
`release-checklist.md`.

Remove an item when it is done. To change the order of the work, move an item
to a different section.

## Pre-release

Do these items before the first public release.

### B-4 License restriction

- `docs/licensing/build-expiry-spec.md` has the design (build expiry, version
  rules, update check). Review the draft, then do L-5.2 of
  `docs/licensing/elv2-migration-plan.md`.
- This must be in the first release. A build without an expiry stays free
  forever.
- Phase 6 of the plan has the other license items for the release.

### B-5 First-time hints for new users

- The proposal has 23 one-time hints, with an anchor, a trigger, a text, and
  a priority for each hint:
  [First-time hints for new users](https://claude.ai/code/artifact/6ed337ac-c2da-470d-b120-487ad46e118c).
  The review of the proposal is not finished.
- Build the 8 high-priority hints first: 1, 2, 8, 10, 14, 15, 18, 23.
- First replace `PreferencesScoreSettingsHint` with a registry that keeps one
  flag for each hint. More → Settings → "Show all hints again" must reset
  all hints.
- Done when: the high-priority hints show once. After the user closes a
  hint, it does not show again. "Show all hints again" shows the hints again.

### B-6 Better tooltips for new users

- Add more information to the tooltips, so that new users can learn the app.
  B-5 adds one-time hints. This item is about the usual tooltips.

### B-7 Popup window redesign

- Redesign the popup windows.

### B-8 Requests for bug reports and features

- Ask users to send bug reports and feature requests.
- The Contact page already asks for the log files with a bug report.

### B-9 Analytics

- The app code is in `org.litvin.analytics`. The Worker is in
  `analytics-worker`. The Worker does not accept events until
  `ANALYTICS_INGESTION_ENABLED` is `true`.
- Finish the analytics work and turn it on.

### B-10 Landing site

- Make a landing site for the app.

### B-19 Only one instance for each Windows account

- Now the user can start the app more than one time. Two instances can open
  the same project and overwrite the changes of the other. Each instance has
  its own export queue, so two ffmpeg exports can run at the same time.
- In `main` (`SwingMainApp`), before the main window opens, lock a file in
  the data folder (`AppDataPaths`, for example `instance.lock`) with
  `FileChannel.tryLock`. Keep the lock until the process ends.
- Windows releases the lock when the process ends, also after a crash or End
  task. Thus, a lock from an old process never blocks the start. Do not use a
  file with a process ID and no lock.
- If the lock is held by another process, show a message and quit: "<app
  name> is already running. Use the open window. If you cannot see it, look
  in the taskbar."
- The lock is in the data folder of the Windows account. Thus, each account
  can run one instance. A lock for the whole device is not necessary: the
  projects, the saved queue, the preferences, and the installation are all
  per account, so two accounts share no state. A lock for the whole device
  would also block user B while user A keeps the app open in another
  session, and B cannot close it without administrator rights.
- Fail open: if the app cannot create or lock the file (for example, the data
  folder is read-only), it starts, and it writes a line to the log.
- Take the lock in `main`, not in `AppServices`. The UI-flow tests build the
  app in the same process with temporary folders, so they must not take it.
- A development run uses the same data folder as the installed app
  (`%APPDATA%\BananaShot`), unless `bananashot.appDataDir` or
  `BANANASHOT_APP_DATA_DIR` is set. Then an open installed app blocks the
  development run. Set the property for development runs.
- Do this before B-18. B-18 then needs no rules for a queue that two
  instances share.
- Done when: a second start shows the message and quits; after End task on
  the first instance, the next start works; two Windows accounts can each
  run one instance.

### B-18 Keep the export queue after the app closes

- Now the export queue is only in memory (`RenderQueueManager` in
  `RenderQueue.kt`). When the app closes, the queued exports and the running
  export are lost. Only completed exports are saved
  (`CompletedRendersRepository`).
- Save each queued `RenderJob` to a file in `AppDataPaths`. Update the file
  when a job is added, starts, completes, fails, or is canceled.
- B-19 makes sure that only one instance runs. Thus, only one instance reads
  and writes the file. If the lock of B-19 fails (fail open), two instances
  can run. Then the instance that starts first owns the file, and the other
  keeps its queue in memory only.
- At startup, load the file and put the jobs back in the queue in the same
  order. Start the restored queue automatically. Do not ask the user first.
  A running export that was stopped by
  the close starts again from the beginning. Delete its partial output file
  first.
- Each `RenderJob` holds all its data. This includes a copy of the color,
  crop and rotate adjustments from the time that the user queued the export
  (`RenderJob.adjustments`). Thus a restored job does not need its project.
  Save all the fields of the job. If the source video is not available, the
  worker shows the job as failed with the reason "Source file missing".
- Done when: the user adds 3 exports, closes the app during the first export,
  and opens the app again. The Exports table shows the 3 exports, and they
  complete. A canceled export does not come back.

### B-21 First dry run of the release workflow

- The "Windows release" workflow has never run. Start a dry run now with
  the current installer. It can find problems before B-23 and B-24.
- Install the dry-run installer on a clean Windows account. Do the checks of
  step 6 of `release-checklist.md`.
- In the app image, run `runtime\bin\java --list-modules`. The JDK 17
  jpackage default can omit `jdk.crypto.ec` and `jdk.crypto.mscapi`, but
  `build-expiry-spec.md` requires them. Write the result in B-23.
- Done when: the dry run passes, and each problem that it finds is fixed or
  is an item in this file. Do the dry run again after B-23 and B-24.

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
- The release workflow uploads the Velopack files (the setup EXE, the
  packages, and the release feed) to the GitHub release. Remove the WiX
  step and `Build-Installer.ps1`.
- Find how Velopack shows the license. L-3.8 of `elv2-migration-plan.md`
  requires the ELv2 text and the third-party notices in the installer.
- The uninstall must not delete the app data (`%APPDATA%\BananaShot`) or the
  preferences in `HKCU\Software\JavaSoft\Prefs`. `build-expiry-spec.md`
  requires this.
- Update `distribution/windows/README.md`, `release-checklist.md`,
  `ui-smoke.md`, and the paths in `Run-UiSmoke.ps1`.
- Done when: a dry run makes a Velopack installer. The installer
  installs the app, makes the Start menu shortcut, and the app starts.

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
- Check how the Velopack setup acts when the app is already installed.
  The setup must update the app, keep the app data, and start the new
  version.
- Do this after B-4 and B-24.
- Done when: version N-1 shows the update notice for version N. The user
  clicks "Update and restart", and version N starts with the same projects
  and the same export queue.

### B-26 Test install, update, and uninstall

- No test installs a new version over an old version.
- Install version N-1, start it, and make a project. Then install version N
  while the app runs.
- Check: the app closes or the installer asks the user to close it. Version
  N starts. The projects, the preferences, and the export history stay.
- Uninstall. Check that the app data and `HKCU\Software\JavaSoft\Prefs`
  stay.
- Add these checks to step 6 of `release-checklist.md`. Automate them in the
  release workflow if it is possible.
- Done when: the checks pass with two dry-run builds.

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
- This is item L-1.4 of `docs/licensing/elv2-migration-plan.md`.

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

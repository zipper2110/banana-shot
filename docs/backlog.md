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
- Status 2026-10-03: E0 to E8 and E10 are done. E9 waits for a manual
  check with a real release. E11 (the manual checks before the first
  release) waits for E9 and B-26. The "Next work" list in `l-5.2-epics.md`
  has the stories that can start now.

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
- Status 2026-10-03: the logic and the user interface are done.
  - `UpdateOptions` (in `org.litvin.license.update`) decides if "Update and
    restart" shows and gives the installer URL and the download page (E9-S1).
  - `UpdateAndRestart` asks first when an export runs, downloads the setup
    EXE to a temporary folder with progress (redirects followed), does the
    close sequence, starts the setup with no `--silent` as the last step,
    and exits. A failed download deletes the partial file and keeps the app
    open. A second click during the download has no effect (E9-S2).
  - Tests: `UpdateAndRestartTest` (fake download and setup start, and a local
    HTTP server for the real download).
  - Done (2026-10-03, with E8 and E7): the update notice, the expiry
    warning, and expired mode have the buttons (`UpdateButtons`).
    `UpdateRunner` shows the progress and the errors. The close sequence is
    `SwingApplicationHandle.close`. The download uses the trust of E5-S4.
- Still to do: the manual check of the update (B-26, check 4).

### B-26 Pre-release install testing

- This item has all the checks that need the app installed with
  `BananaShot-win-Setup.exe`. Other items refer to it: B-24, B-30,
  and E11 of `l-5.2-epics.md`. Do the checks together, on a clean Windows
  account.
- No automatic test installs a new version over an old version.
- You need two dry-run builds with different versions: version N-1 and
  version N.
- Checks with one build (version N-1):
  1. Fresh install: do the checks of step 7 of `release-checklist.md`
     (B-24). The setup installs with no administrator rights, makes
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
  - Checks 1 and 2 need a dry-run build that has the fixed
    `THIRD-PARTY-NOTICES.txt` (commit c1ea279 or later). The builds 0.9.0
    and 0.9.1 of check 4 have it.
  - The user interface of B-30 for check 4 is done (E8 of
    `l-5.2-epics.md`, 2026-10-03).
- Done (2026-10-03): checks 3 and 5 are in step 7 of `release-checklist.md`,
  and check 4 is in step 9 (after the publish, because the app downloads
  only a public setup file). For the first release, do them here with two
  test builds. No automation: the checks need the Velopack dialog and a real
  install.
- Setup for check 4 (decided on 2026-10-03). The app downloads the setup
  from the `installerUrl` of `release/version-policy.json` on `master`. A
  workflow artifact needs a GitHub login, so version N must be in a public
  release. A tag push cannot make it: `Validate-Release.ps1` (E0-S3) stops
  the tag build while a story of `l-5.2-epics.md` is open. Thus:
  1. Start the "Windows release" workflow by hand two times, with the
     versions `0.9.0` (N-1) and `0.9.1` (N). Download both artifacts. The
     versions are lower than the first release, so the test builds never
     replace it.
  2. Make a public pre-release for N by hand. Its tag does not start with
     `v`, so the release workflow does not run, and the release age reminder
     ignores it:
     `gh release create update-test-0.9.1 --prerelease --title "Update test 0.9.1 (delete after B-26)" --notes "Test build for B-26. Do not use." <folder of N>/BananaShot-win-Setup.exe`
  3. On `master`, set `latest` in `release/version-policy.json` to
     `"version": "0.9.1"`, `"downloadUrl": "https://github.com/zipper2110/banana-shot/releases/tag/update-test-0.9.1"`,
     and `"installerUrl": "https://github.com/zipper2110/banana-shot/releases/download/update-test-0.9.1/BananaShot-win-Setup.exe"`.
     Push. GitHub can need about 5 minutes to show the new file.
  4. Install N-1 from its artifact, and start it. The update notice shows
     version 0.9.1. Do check 4: first "Cancel" in the Velopack dialog, then
     "Update".
  5. After the checks: set `latest` back to the values before step 3, and
     push. Delete the pre-release and its tag:
     `gh release delete update-test-0.9.1 --cleanup-tag --yes`.
- Builds (2026-10-06): the dry runs of commit `db219df` (after the change
  of `ANALYTICS_PRIVACY_URL`): `0.9.0` is run 37461914426, and `0.9.1` is
  run 37461925236. Both passed. The two `BananaShot.cfg` files have the
  correct version, the privacy URL `https://banana-shot-editor.app/privacy/`,
  and the feedback and analytics endpoints. Both setup EXEs have a valid
  signature (`CN=Dmitrii Litvin`), and the SHA-256 files agree.
  - The first try of `0.9.1` failed at the Certum login ("No matching
    certificate was found in Cert:\CurrentUser\My within 60 seconds"). The
    two runs started 5 seconds apart, so they probably used the same TOTP
    code, and SimplySign refused the second login. A rerun of the failed
    job passed. Do not start two signed runs at the same time.
- Done when: all checks pass with two dry-run builds. Write the result of
  each check in this item.

## Post-release

Do these items after the first public release.

### B-40 Contact address on the domain

- The first release uses the personal Gmail of the author
  (`leetvin@gmail.com`) as the contact address (B-10 decision 26). Change
  it to an address on `banana-shot-editor.app`.
- Select the local part of the address, for example `hello@` or
  `support@`.
- Set up Cloudflare Email Routing: forward the address to the Gmail. The
  author replies from Gmail. To reply from the domain address, set up
  "Send mail as" in Gmail with an SMTP service. Without it, the replies come
  from the Gmail address.
- Change the address in three places: `CONTACT_EMAIL` in
  `SwingApplicationFactory`, "Contact and your rights" of
  `site/public/privacy/index.html`, and "Help and contact" of
  `site/public/faq/index.html`. On the site, the address is reversed in
  the `data-email` attribute (`assets/email.js`).
- Released apps keep the Gmail address until the users update. Thus, keep
  the Gmail address in use.
- Done when: an email to the new address gets to the Gmail, and the app and
  the site show the new address.

### B-41 Admin dashboard for the numbers

- Make a private dashboard for the author. It shows on one page:
  - The downloads of the setup file, with a daily and a weekly history.
  - The numbers of the site (Cloudflare Web Analytics): visits, page views,
    the pages, and the referrers.
  - The numbers of the analytics Worker (B-9): sessions, app versions, the
    use of the features, and the exports. The SQL is in
    `analytics-worker/queries/`.
- Downloads: GitHub gives only the total `download_count` of each release
  file, with no history. Thus, a scheduled job must save the totals each
  day (for example a cron trigger of a Worker that writes to D1). The
  history starts on the day that the job starts, so start it soon after
  the first release.
- The count of `BananaShot-win-Setup.exe` includes the downloads of
  "Update and restart" (B-30). Show the number of new installs from the
  analytics Worker next to it.
- Site numbers: read them with the Cloudflare GraphQL Analytics API. This
  needs an API token with read access to the account analytics.
- Only the author can open the dashboard. For example, use Cloudflare Access
  in front of the page. The page must not show the data of a single user.
- Open questions: a new Worker or a part of the analytics Worker; the
  hosting of the page; the time periods and the charts.
- Done when: the author opens the dashboard and sees the three groups of
  numbers, with the daily and weekly download history.

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

### B-37 Locate a moved source video

- Finding F-02 of the feature audit of 2026-10-05. Decided on 2026-10-05:
  after the first release.
- Problem: when the source video is moved, renamed, or on a USB drive with a
  different drive letter, the project does not open. The app tells the user
  to put the video back at the old path.
- The parts for the fix are in the code. `FileProjectsRepository.openProject`
  writes a new `sourceVideo` path into the manifest. The intent
  `MissingSourceVideoSelected` opens the project with a new video. Now the
  app uses them only when the manifest has no video path.
- Tasks:
  1. In `DefaultProjectsPresenter.openProject`, when the video is not on the
     disk, send a new effect instead of `ShowError`. The effect gives the old
     path and the project name.
  2. In `SwingProjectsPanel`, show a dialog with the old path and two
     buttons: "Locate video..." and "Cancel". "Locate video..." opens the
     file chooser in the folder of the old path. If that folder does not
     exist, the chooser opens in the last video folder.
  3. Send `MissingSourceVideoSelected` with the selected file. The project
     then opens, and the manifest keeps the new path.
  4. Check the selected video before the project opens. Probe its duration
     with the existing duration probe. If the last point ends after the end
     of the video, show a warning: "This video is shorter than the points of
     the project." The user can continue or select a different file.
  5. On a project row that shows "The video is not on the disk anymore", add
     the same "Locate video..." action.
  6. Update the Projects help text in `HelpCatalog`.
  7. Add presenter tests: a missing video sends the new effect; a selected
     video opens the project and changes the manifest; Cancel keeps the
     project closed; a video that is too short gives the warning.
- Not in scope: exports in the queue keep the old `sourcePath`. They fail
  with "Source file missing". The user can start these exports again.
- Done when: a project whose video was moved to a different folder opens
  after the user selects the video. The next start opens it with no dialog.

### B-38 Minor findings of the feature audit

- The minor and super-minor findings (F-04 to F-17) of the feature audit of
  2026-10-05. Decided on 2026-10-05: after the first release.
- The tasks and the open questions are in `docs/audit/b-38-tasks.md`.
- Done when: each task of the file is done, or it has a decision to keep the
  current behavior.

### B-39 Silent errors

- Found by the feature audit of 2026-10-05. Decided on 2026-10-05: after the
  first release.
- Problem: many code paths catch all errors and continue with no log and no
  message. Some of them can hide data loss from the user. Two known cases
  are in B-38: a skipped overlay or statistics card in an export (T2), and a
  failed save of `adjustments.json` (T6).
- On 2026-10-05, `src/main/kotlin` had 90 blocks
  `catch (_: Throwable)` or `catch (_: Exception)`. Most are in
  `RenderQueue.kt` (15), `SwingExportPanel.kt` (10), `SwingScoringPanel.kt`
  (6), `AdjustmentsStore.kt` (6), and `FFmpegCapabilities.kt` (5). Also look
  for `catch (e: ...) { }` with an empty body.
- Tasks:
  1. Make a list of each block: file, line, and the operation that can fail.
     Put the list in this item or in a separate file.
  2. Put each block in one class:
     - Expected: the error is a normal case (for example, an optional probe
       or a UI cleanup). Keep the catch. Add a short comment that tells why.
     - Log: the error does not change the user data or the result. Write it
       to the log with the context.
     - Show: the error can lose user data or change the result (a save, an
       export, the queue file). Show it to the user, for example with the
       "Autosave failed" status or an export warning.
  3. Fix the "Show" blocks first, then the "Log" blocks.
  4. Catch `Throwable` only where the code must survive an `Error` (for
     example, a thread loop). In other places, catch `Exception` or a
     narrower type.
  5. Add tests for each "Show" block: a failing store or writer gives a
     message to the user.
- Optional: add a guard test that refuses a new empty catch block without a
  comment.
- Done when: each block of the list has a class, and each "Show" and "Log"
  block is fixed.

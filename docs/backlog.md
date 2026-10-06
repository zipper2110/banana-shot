# Backlog

This file lists the open work. For the steps of a release, see
`release-checklist.md`.

Remove an item when it is done. To change the order of the work, move an item
to a different section.

## Pre-release

Do these items before the first public release.

No open items (2026-10-06). The next step is the first release with
`release-checklist.md`. The manual checks that moved to after the release
are in B-42.

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

### B-42 Manual checks after the first release

- Moved from B-26 check 2 and E11-S2 (proxy) and from E11-S1 (sleep) on
  2026-10-06. The author postponed them to after the first release.
- Sleep (E11-S1): on a laptop, start the app, sleep for 10 minutes, and
  wake it. The log shows a run time that includes the 10 minutes.
- Set a manual proxy in Windows (for example a local Fiddler or mitmproxy),
  with analytics on. Start the installed app. The proxy shows the request
  for the rules file and the analytics request. Remove the proxy setting
  after the check.
- TLS inspection (S-12 of `docs/licensing/expiry-scenarios.md`): use
  mitmproxy with its root certificate only in the Windows store. The app
  gets the rules file through the proxy. Remove the proxy setting and the
  certificate after the check.
- Done when: the three checks pass with a released build.

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

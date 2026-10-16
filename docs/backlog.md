# Backlog

This file lists the open work. For the steps of a release, see
`release-checklist.md`.

When item is done, move it under 
`docs/version-scope/<upcoming-release-version-number>.md` file.

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
- The plan is in `docs/localization/b-11-localization.md`.
- The work has 8 epics (E1 to E8). Each epic has its own file with its
  tasks in `docs/localization/` (see "Epics" in the plan).

### B-13 macOS

- Build, package, and test the app on macOS.
- The list of changes is in `docs/macos/b-13-macos.md`.

### B-45 Padel improvements

- The baseline is the padel support of the first release with padel
  (B-14, done on 2026-10-10). `padel.md` describes the baseline.
- The tasks P-1 to P-7 are in `padel.md`, section "Improvements".
- Do P-7 (site and FAQ) after the release with padel support, then P-6
  (Spanish, with B-11).

### B-43 Padel Americano events

- Decided on 2026-10-08: a separate item. The author does not know yet what
  users need.
- The app has one Americano match for each project (structure
  `TOTAL_POINTS`, see `padel.md`). Each team serves 2 or 4 points in a row.
- Open questions:
  - Serve turn: organizers use 2 or 4 serves before the serve goes to the
    other team. Some events rotate by player, not by team. Are 2 or 4
    points for each team sufficient?
  - Events: an Americano or Mexicano event has many short matches with
    different partners. The points belong to each player, and a
    leaderboard shows the winner. Do users need one video with many
    rounds, with the points of each player?
  - Draw: a draw shows the equal points. Nothing on the scoreboard or in
    Stats says "Draw".

### B-15 Intro and outro videos

- Let the user add an intro video and an outro video to the exported video.

### B-16 Social media features

- Add features for social media use.
- Decided on 2026-10-15: these features go on the Share tab (B-46). The
  vertical clips for Shorts, Reels, and TikTok come there later.

### B-46 Share tab: YouTube thumbnail

- Decided on 2026-10-15. A new tab "Share" makes a YouTube thumbnail for
  the open project. The thumbnail is the only product of B-46. The
  vertical clips of B-16 come later on the same tab.
- Decided on 2026-10-15: the video description ("Copy description" of the
  marketing strategy) is not part of B-46. Its place is not decided.
- Why a separate tab and not a part of Export:
  - The sidebar shows the tab all the time. A button inside Export is easy
    to miss.
  - Export makes the video file, and its queue is for all projects (B-32).
    The thumbnail needs one project: a frame and the match data.
  - The thumbnail does not need a finished export. The user can make it
    before the export or while the export runs.
- User value: YouTube uses a random frame as the default thumbnail. This
  frame often shows the time between points. A frame of a point with large
  words is better. Today the user needs ffmpeg and an image editor for
  this.
- The tab:
  - Its sidebar item is "Share", after Export. It shows only when a
    project is open, the same as the Match and Video groups.
  - Layout of the tab (option A of `design/share-redesign/`): the preview
    of the thumbnail at the left, the edit fields at the right.
  - Decided on 2026-10-15: the right side has two tabs, "Texts" and
    "Colors". The preview and the Save block are always visible.
  - It has a Help page "Share", and a step in the workflow list of the
    Projects tab.
- The thumbnail:
  - A frame of the video. The user selects a point and a moment in the
    point. The crop, rotation, and color adjustments apply to the frame,
    as in the export.
  - Decided on 2026-10-15: no scoreboard on the thumbnail.
  - Decided on 2026-10-15: the user selects one of several layout styles.
    A layout style sets the placement and the colors of the texts. The
    texts can show the players, the league, the court, and other match
    data.
  - Decided on 2026-10-15: the video type ("Full match" or "Best points")
    sets the preset of the large words. The app fills as many fields as
    possible with preset values, so that the user saves time and sees the
    look of each layout at once.
  - Decided on 2026-10-15: the user can change the colors of the layout
    style. Each color of the style can change, in one system of color
    roles for all styles.
  - Nothing goes in the bottom-right corner, because YouTube shows the
    video length there.
  - "Save" writes a PNG (1280 × 720, less than 2 MB) to the export folder.
    "Open folder" shows the file.
- Out of scope: free placement of the texts, user fonts;
  vertical covers for Shorts and Reels (with the vertical clips of B-16);
  upload to YouTube; automatic selection of the "best" frame; translated
  preset words (later, with B-11 E5).
- Parts to use:
  - `VideoFrameLoader` reads a frame with the project adjustments. It is
    already 1280 px wide.
  - The texts use Segoe UI Black and Segoe UI, the same fonts as the
    scoreboard layouts (`SEGOE_BLACK`, `SEGOE`).
- The layout styles and the fields are in `design/share-redesign/`.
- Done when: the Share tab shows for an open project, and Help has a
  "Share" page. The user makes a thumbnail in each layout style for a
  project with and without favorites. The PNG is 1280 × 720 and less than
  2 MB. It shows the selected frame with the adjustments and the texts of
  the layout.

### B-47 Change points for the crop and rotation

- Decided on 2026-10-16. During a recording, wind can move the phone. Then
  one angle is not correct for the full video. The user can add change
  points on the Transform tab.
- A change point has a time in the video, and its own angle, zoom, and
  pan. The user can add a change point at any time, also during a point.
- Before the first change point, the video uses the usual Transform values.
- Decided on 2026-10-16: the change is a blend of 1 second. The blend ends
  at the change point. Reason: the user sets the values while the editor
  shows the frame at the change point, so this frame must get the exact
  values.
- Editing: the Transform controls edit the last change point at or before
  the playhead (or the usual values before the first change point). While
  the user edits, the editor shows these values without the blend.
- The preview, the export, and the still frames (statistics card, Share
  thumbnail) use the same values for each time.
- Storage: a list `geometryKeys` in `adjustments.json`. An older app
  ignores the list and uses the usual values.
- Done when: a project with two change points exports with the correct
  angle before, during, and after each blend, and the preview shows the
  same angles.
- Decided on 2026-10-16: the Colors tab gets change points too, for light
  that changes during a match (clouds, sunset). The color change points
  are a separate list `colorKeys`. Reason: the wind and the light change
  at different times.
- A color change point has all six color values. The blend is also 1
  second and ends at the change point. The color blend is linear: the
  export fades from the old colors to the new colors, because FFmpeg
  cannot change the tone curve at each frame. The preview uses the same
  linear blend.
- The Colors tab edits the last color change point at or before the
  playhead. While the user edits, the preview shows these colors without
  the blend.
- Decided on 2026-10-16 (replaces the separate lists above): one "Colors &
  Transform" tab replaces the Colors tab and the Transform tab. It stays
  in the Video group of the sidebar. The right panel has two sub-tabs, Colors and Transform. Each sub-tab has its own
  "Reset all" button. The change points do not depend on the sub-tab: a
  change point has the colors and the transform. Add, Remove, Previous,
  and Next work on both. The file keeps the two lists `colorKeys` and
  `geometryKeys`, and the app keeps the same times in the two lists.
  Reason: one list is easier to understand than two lists in one tab.
- The site (`site/public`: the demos and the changelog) and the tutorial
  video show the Colors tab and the Transform tab. Update them at the
  release of the Colors & Transform tab, as in B-48.

### B-48 Site: the Points tab replaces the Scoring tab

- Version 1.2.0 removes the Scoring tab. The Points tab has the scoring
  controls now.
- Decided on 2026-10-16: update the site at the 1.2.0 release, not before.
  Reason: until then, the site describes version 1.1.0, which has the
  Scoring tab.
- Change these parts of `site/public`:
  - `index.html`: the scoring demo and its `aria-label` show the Scoring
    tab. Make the demo a copy of the Points tab with the scoring controls.
  - `assets/demos/scoring.js`, `assets/demos.css`, and the tab list in
    `assets/demos/core.js`.
  - `privacy/index.html`: the list of counted tabs names Scoring, and the
    text says "Point outcomes recorded in Scoring". Older versions still
    send the Scoring tab keys. Check if the change needs a new notice
    version (`AnalyticsSchema.NOTICE_VERSION`).
- Done when: the deployed site shows no Scoring tab, except in the
  changelog of older versions.

### B-17 Audio noise reduction

- Remove background noise from the audio in the exported video. Examples are
  rain, wind, and traffic noise.
- The sounds of the game (ball hits, calls) must stay clear.
- Rejected (2026-10-07): the ffmpeg `highpass` + `afftdn` chain. The author
  tested 3 strengths on a rain video. All of them made the sound muffled.
  With heavy rain, only the strongest level removed enough rain, but it
  distorted the game sounds too much.
- Rejected (2026-10-07): a sidechain gate that opens on ball hits. The rain
  pumps between hits, and the rain comes back on each hit.
- Rejected (2026-10-07): Meta SAM Audio. It needs a 2.4 GB or larger model
  and a GPU with 6-8 GB of video memory or more.
- Do not use speech-only models (RNNoise, DeepFilterNet and similar). They
  remove ball hits as noise.

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

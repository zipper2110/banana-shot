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

### B-49 A match from several video files

- Let a project use several video files of one match, one after the other.
  Action cameras and some phones split a long recording into several
  files. Now a project uses one video file, so the user must join the files
  first with another tool.
- The site does not tell about this limit now (decided on 2026-10-18). When
  the feature is done, add it to the FAQ answer "Can I use a video from any
  phone or camera?" (`site/public/faq/index.html`, `#any-camera`).

### B-52 Add comment during a replay

- Postponed from B-50 on 2026-10-26. Do it after B-50.
- "Add comment" should work during a replay. It needs a concept first.

### B-55 Lightweight scoreboard style

- Add a new scoreboard style with no background. The texts have an outline,
  so that they stay clear on a light or a dark video.
- Keep the other parts to a minimum. The existing "Minimal" style still has
  a background panel, so the new style is a different style.
- Done when: the user can select the style on the Points tab, and the export
  shows it with no background.

### B-56 Show the scoreboard only after each point

- Add a scoreboard option: show the scoreboard only after each point, not
  during the whole video.
- The scoreboard comes in with an ease-in animation (for example a fade)
  after the point ends. It stays for a short time with the new score. Then
  it goes out with an ease-out animation.
- Open questions: how long the scoreboard stays, and whether the user can
  change this time.
- Done when: the user can turn on the option, and the export shows the
  scoreboard only after each point, with the two animations.

### B-57 Emoji in comments

- Let the user put emoji in a comment. The preview and the exported video
  show the emoji correctly.
- The export draws comments with ASS subtitles (`CommentAss.kt`). Find out
  first how the emoji look there now (color, black and white, or missing).
- Done when: a comment with emoji shows the same emoji in the preview and in
  the export.

### B-62 Trim the start and the end of the video

- Let the user cut off the start and the end of the video, for example the
  warm-up before the match and the time after it.
- Decided on 2026-10-30:
  - The trim is on its own sub-tab "Trim" of the Colors & Transform tab. It
    is not on the Transform sub-tab, because it changes the time, not the
    frame.
  - The trim has a start time and an end time. They apply to the whole
    video and have no change points. They are in `adjustments.json`.
  - The Points tab timeline shows only the time between the start and the
    end. So the timeline shows the match larger.
  - The "Full video" export uses only the time between the start and the
    end. The scoreboard and the comments move with the video.
  - The "Only points" and "Only favorites" exports do not use the trim.
    They keep all their points.
  - The score does not change. The scoreboard at the start shows the score
    of the points before the start.
  - The trim sub-tab sends no analytics event for its tab, because the
    analytics contract has no key for it.
- Points outside the trim, decided on 2026-10-30:
  - The user can set a trim that cuts off points. The user decides.
  - The app keeps these points. It deletes no data.
  - The Trim sub-tab shows the points as marks on its timeline, so the user
    sees where they are.
  - While points are outside the trim, a warning banner shows. It goes away
    when no point is outside the trim.
  - The first time that a trim cuts off points in a project, a warning
    dialog also shows. The project remembers that the dialog showed.
- Sub-tabs, decided on 2026-10-31:
  - The sub-tabs are large tabs in a strip across the top of the Colors &
    Transform tab, not a small control in the right panel.
  - The order is Trim, Colors, Transform. The tab opens on Trim.
  - Each sub-tab has an icon, a title and a short subtitle. The open tab
    has a lime icon.
- Trim controls, decided on 2026-10-31 (from the user's mock):
  - The right panel shows a card with the length that the video keeps, the
    time cut off and the points. A card for each end shows its time, a
    large "Start at the current position" or "End at the current position"
    button, and a "Go" button. The texts do not use the word "playhead".
  - Under the video, a trim timeline shows the whole video. The user drags
    two handles to set the start and the end. Pills above the handles show
    their times. The parts outside the trim are hatched. The points show
    on the track, and the points outside the trim are red.
  - The trim timeline replaces the seek bar on the Trim sub-tab. A click on
    it seeks. The drag of a handle seeks the video with it, so the user
    sees the frame. The trim timeline shows the playhead as the Points
    tab does: a thin line with a small triangle. It shows no "Kept" text, because the
    right panel shows the kept length.
  - While the user drags a handle, the right panel shows the new length,
    the points and the warning at once.
- Done when: the Points tab timeline and the "Full video" export use the
  trim.

### B-61 Zoom the points timeline with the mouse wheel and the touchpad

- Let the user zoom the Points tab timeline in and out with the mouse wheel
  and with touchpad gestures (pinch, and two-finger scroll).
- The zoom keeps the time under the mouse pointer at the same position.
- Open question: which input zooms and which input scrolls the timeline
  (for example, the wheel with Ctrl zooms, and the wheel alone scrolls).
- Done when: the user can zoom the timeline with the mouse wheel and with
  the touchpad.

### B-15 Intro and outro videos

- Let the user add an intro video and an outro video to the exported video.
- Postponed on 2026-10-30. The work has 3 epics (E1 to E3). The concept,
  the open questions and the epics are in
  `docs/intro-outro/b-15-intro-outro.md`.

### B-16 Social media features

- Add features for social media use.
- Decided on 2026-10-15: these features go on the Share tab (B-46). The
  vertical clips for Shorts, Reels, and TikTok come there later.

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

### B-58 Analyze the 404 requests of the site

- Get the list of the requests to the landing site that got a 404 page. The
  404 page sends its path to the counter (`data-path` in
  `site/public/assets/count.js`), and the site Worker reports the HTML
  requests to the analytics Worker. Also look at the Cloudflare analytics of
  the zone.
- Put the paths in groups: old or wrong links to our pages, links from other
  sites, bots and scanners (for example `/wp-admin`), and other.
- For each group, find out if an action is useful. Examples: a redirect to
  the correct page, a fix of a wrong link, a new page, or no action.
- Done when: the groups and the decision for each group are in this item, and
  the useful actions are new tasks or new backlog items.

### B-60 Feature demos with different sports

- Added on 2026-10-29.
- The feature demos on the landing site (`site/public/index.html`) show
  only tennis singles. The scripts are in `site/public/assets/demos/`.
- Update the demos, so that they use different sports as examples: tennis,
  padel, and beach tennis. Padel and beach tennis are usually doubles, so
  their demos show two teams.
- Update the `aria-label` text of each demo to match its sport.
- Open question: which demo shows which sport.

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

### B-18 Keep the export queue after the app closes

- Now the export queue is only in memory (`RenderQueueManager` in
  `RenderQueue.kt`). When the app closes, the queued exports and the running
  export are lost. Only completed exports are saved
  (`CompletedRendersRepository`).
- Save each queued `RenderJob` to a file in `AppDataPaths`. Update the file
  when a job is added, starts, completes, fails, or is canceled.
- At startup, load the file and put the jobs back in the queue in the same
  order. Start the restored queue again. A running export that was stopped by
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

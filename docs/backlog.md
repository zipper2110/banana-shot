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

# Backlog

This file lists the open work. For the steps of a release, see
`release-checklist.md`.

Remove an item when it is done. To change the order of the work, move an item
to a different section.

## Pre-release

Do these items before the first public release.

### B-1 Log files for bug reports

- Problem: the app writes logs only to the console (`src/main/resources/logback.xml`
  has only a `ConsoleAppender`). A packaged app has no console, so a user
  cannot send logs with a bug report. `AppDataPaths.logs` exists, but no code
  uses it.
- Add a rolling file appender that writes to the `logs` folder in the app
  data folder. Limit the size and the number of files.
- Add an "Open log folder" button to More → Contact, under "Report a
  problem". Change the text so that it asks for the log files.
- Make sure that the logs do not contain more than necessary. They can contain
  file paths. Tell the user this on the Contact page.
- Done when: a packaged build writes log files, and the Contact page opens
  their folder.

### B-2 Automate the native checks

- The packaged smoke test does not yet do a real FFmpeg export or check the
  adjustment controls (see `README.md`, "Testing UI flows"). Until it does,
  the release checklist has these checks as manual steps.
- Add these checks to the smoke test. Then remove the manual steps from the
  release checklist.

### B-3 Unstable UI-flow tests

- On Windows, the Robot sometimes loses the first click on the Projects tab
  (Import Match or Open Project). Then a test fails, for example with "Timed
  out waiting for new-project-create to be visible". A different test fails
  in each run. The same test passes when you run it again.
- Find the cause (for example, focus or window activation before the first
  click), and make the driver wait or click again.
- Done when: `mvn -B -Pui-flow verify` passes three times in sequence.

### B-4 License restriction

- `docs/licensing/elv2-migration-plan.md` has the open license items
  (phase 5, license key functionality, and phase 6, release).
- The license restriction (phase 5) must be in the first release. Check which
  other items must also be in it.

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
- B-1 also changes the Contact page.

### B-9 Analytics

- The app code is in `org.litvin.analytics`. The Worker is in
  `analytics-worker`. The Worker does not accept events until
  `ANALYTICS_INGESTION_ENABLED` is `true`.
- Finish the analytics work and turn it on.

### B-10 Landing site

- Make a landing site for the app.

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

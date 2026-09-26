# Release checklist

Use this list for each Windows release. The details of each step are in the
linked documents. Do not copy them here.

## Open items for the next release

Close each item or move it to a later release on purpose. Remove an item when
it is done.

### R-1 Log files for bug reports

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

### R-2 Native checks that are not automated

- The packaged smoke test does not yet do a real FFmpeg export or check the
  adjustment controls (see `README.md`, "Testing UI flows"). Do these checks
  by hand on the packaged app until the smoke test covers them.

### R-3 Unstable UI-flow tests

- On Windows, the Robot sometimes loses the first click on the Projects tab
  (Import Match or Open Project). Then a test fails, for example with "Timed
  out waiting for new-project-create to be visible". A different test fails
  in each run. The same test passes when you run it again.
- Find the cause (for example, focus or window activation before the first
  click), and make the driver wait or click again.
- Done when: `mvn -B -Pui-flow verify` passes three times in sequence.

### R-4 License work

- `docs/licensing/elv2-migration-plan.md` has the open license items
  (phase 5, license key functionality, and phase 6, release). Check which
  items must be done before this release.

### R-5 First-time hints for new users

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

## Checks before each release

### Code and tests

- [ ] `mvn -B test` passes.
- [ ] `mvn -B -Pui-flow verify` passes with JDK 17.
- [ ] The Help text (`HelpCatalog.kt`) agrees with the changed features.

### Package

- [ ] Build the app image and the installer. See
      `distribution/windows/README.md`.
- [ ] `Tennis Record Diagnostics.cmd` in the app image passes.
- [ ] `Validate-Release.ps1` passes.
- [ ] Review the Maven dependency licenses and the SBOM.
- [ ] If a pin in `native-dependencies.json` changed, make a new natives
      release first. See `distribution/windows/README.md`, "Natives release".

### Packaged app

- [ ] Run the packaged smoke test and complete its report. See
      `qa/windows/ui-smoke.md`.
- [ ] Do the manual checks of R-2.
- [ ] First start with empty app data: the analytics consent dialog (when the
      build has analytics) and the Overview help open.
- [ ] More → About: the version is correct. The License, License notice, and
      Third-party notices buttons open the files from `legal/`.
- [ ] More → Contact: "Write an email" opens the email app.
- [ ] More → Settings: "Open folder" opens the app data folder.
- [ ] First-time hints (R-5): each hint shows at its trigger. A closed hint
      does not show again. "Show all hints again" shows the hints again.

### Publish

- [ ] The release workflow makes a draft release. Check the files and the
      release notes on the GitHub releases page.
- [ ] The release notes link the natives release.
- [ ] Publish the release with "Set as the latest release" selected.

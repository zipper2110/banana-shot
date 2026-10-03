# Release checklist

Use this guide for each Windows release. Do the steps in the given sequence.
Do not start a step before the step before it is complete.

The details of each step are in the linked documents. Do not copy them here.
The open work is in `backlog.md`.

## 1. Scope

- [ ] Select the version number (`MAJOR.MINOR.PATCH`).
- [ ] Check `backlog.md`. Each item that must be in this release is done.

## 2. Texts in the app

The features change between releases. Make sure that each text agrees with
the features of this release.

- [ ] Help: `HelpCatalog.kt`. Check each help page for each changed feature.
- [ ] Hints: `HintBalloon`, `ScoreSettingsHint`, `TransformHints`, and the
      first-time hints.
- [ ] Tooltips on the changed controls.
- [ ] More → About and More → Contact.

## 3. Feedback Worker

See `feedback-worker/README.md` and `docs/feedback/b-8-tasks.md`.

- [ ] The feedback Worker is deployed with the D1 schema and the secrets
      `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, and `RATE_LIMIT_KEY`.
- [ ] `FEEDBACK_INGESTION_ENABLED` is `true`. The synthetic report
      (`feedback-contract/v1/smoke-report.json`) returns `201`, and the author
      chat gets the message. A second send returns `200`.
- [ ] The GitHub variable `FEEDBACK_ENDPOINT` is the URL of the Worker
      (`https://<host>/v1/feedback`). `Validate-AppImage.ps1` refuses a build
      without it.

## 4. Native dependencies

- [ ] If a pin in `native-dependencies.json` changed, make a new natives
      release. See `distribution/windows/README.md`, "Natives release".

## 5. Tests

- [ ] `mvn -B test` passes.
- [ ] `mvn -B -Pui-flow verify` passes with JDK 25.

## 6. Dry run

- [ ] Start the "Windows release" workflow by hand with the version number.
      See `distribution/windows/README.md`, "Release requirements". The
      workflow builds the package and runs `Validate-Release.ps1`.
- [ ] Download the workflow artifact.
- [ ] Review the Maven dependency licenses and the SBOM
      (`bananashot-sbom.json`).
- [ ] "Java libraries" in `distribution/THIRD-PARTY-NOTICES.txt` lists each
      library of the SBOM with its version. The end of the file has the full
      text of each license that the list names.

## 7. Packaged app

Install the app with `BananaShot-win-Setup.exe` of the dry run. The app is
in `%LocalAppData%\BananaShot\current`.

- [ ] The setup installs with no administrator rights, makes the Start menu
      shortcut, and starts the app.

- [ ] `BananaShot Diagnostics.cmd` in the app folder passes. The check
      "libmpv load" shows that Windows finds all libmpv DLLs.
- [ ] The libmpv folder of the app has `BUILD-INFO.txt` and `LICENSES/` of
      the LGPL build that `native-dependencies.json` pins.
- [ ] Run the packaged smoke test with `-Installed` and complete its report.
      See `qa/windows/ui-smoke.md`. The smoke test does a real FFmpeg export and
      checks the adjustment controls.
- [ ] First start with empty app data: the analytics consent dialog (when the
      build has analytics) and the Overview help open.
- [ ] More → About: the version is correct. The License, License notice, and
      Third-party notices buttons open the files from `legal/`.
- [ ] More → Contact: "Write an email" opens the email app. "Open log folder"
      opens the folder that contains `bananashot.log`.
- [ ] Feedback: in the sidebar, click Feedback. Select a topic, write a
      message, give an email address, and select "Attach the log files".
      "Show the data" shows the report and the log text. Click Send. The form
      shows "Thank you" and the report ID. The author chat gets the message
      and the `.log.gz` file with the same ID.
- [ ] Feedback without network: turn off the network and send a report. The
      form keeps the text and shows "Try again", "Copy report", and "Write an
      email". Turn on the network and click "Try again". The author chat gets
      the report one time.
- [ ] Help: the end of a help page has "Is something not clear? Tell us." The
      link opens the form with the topic Question.
- [ ] More → Settings: "Open folder" opens the app data folder.
- [ ] First-time hints: each hint shows at its trigger. A closed hint does not
      show again. "Show all hints again" shows the hints again.

If a check fails, fix the cause and start again at step 5.

## 8. Release

- [ ] Push the tag `v<version>` on `master`. The release workflow makes a
      draft release.
- [ ] On the GitHub releases page, check the files and the release notes.
- [ ] The release notes link the natives release.
- [ ] Publish the release with "Set as the latest release" selected.

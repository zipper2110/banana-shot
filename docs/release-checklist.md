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

## 3a. Analytics Worker and privacy notice

See `analytics-worker/README.md` and `docs/analytics/b-9-tasks.md`.

- [ ] The privacy notice (`docs/analytics/privacy-notice.md`) is on the
      landing site with the effective date. The URL opens.
- [ ] The analytics Worker is deployed with the D1 schema in the EU
      jurisdiction and the secret `RATE_LIMIT_KEY`.
- [ ] The deployed Worker knows all counter keys of this release
      (`analytics-contract/v1/counter-keys.json`). Deploy the Worker before
      the app release, because the Worker refuses a summary with an unknown
      key.
- [ ] `ANALYTICS_INGESTION_ENABLED` is `true`. The synthetic summary
      (`analytics-contract/v1/smoke-summary.json`) returns `204`.
- [ ] The GitHub variables `ANALYTICS_ENDPOINT` (`https://<host>/v1/session`),
      `ANALYTICS_PRIVACY_URL`, and `ANALYTICS_NOTICE_VERSION` are set.
      `Validate-AppImage.ps1` refuses a build without them.

## 4. Native dependencies

- [ ] If a pin in `native-dependencies.json` changed, make a new natives
      release. See `distribution/windows/README.md`, "Natives release".

## 5. Tests

- [ ] `<revision>` in `pom.xml` is the version of the new release with
      `-SNAPSHOT` (for example `1.4.0-SNAPSHOT`).
- [ ] CI passed on `master` with this version. Then `VersionPolicyFileTest`
      has checked that no rule in `release/version-policy.json` stops the new
      version.
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
- [ ] First start with empty app data: the analytics consent dialog opens.
      After you answer it, the Overview help opens. "Read privacy notice"
      opens the notice on the site.
- [ ] Analytics: click "Enable analytics", and use some tabs. Within 5
      minutes, a row with this app version appears in D1
      (`analytics-worker/queries/sessions.sql`). Then turn off the analytics
      in More → Privacy.
- [ ] More → About: the version is correct. The License, License notice, and
      Third-party notices buttons open the files from `legal/`.
- [ ] More → Contact: "Write an email" opens the email app. "Open log folder"
      opens the folder that contains `bananashot.log`.
- [ ] Feedback: in the sidebar, click Feedback. Select a topic, write a
      message, give an email address, and select "Attach the log files".
      "Show the data" shows the report and the log text. Click Send. The form
      closes, and the popup "Report sent" shows the report ID. The author
      chat gets the message and the `.log.gz` file with the same ID.
- [ ] Feedback without network: turn off the network and send a report. The
      form keeps the text and shows "Try again", "Copy report", and "Write an
      email". Turn on the network and click "Try again". The author chat gets
      the report one time.
- [ ] Help: the end of a help page has "Is something not clear? Tell us." The
      link opens the form with the topic Question.
- [ ] More → Settings: "Open folder" opens the app data folder.
- [ ] First-time hints: each hint shows at its trigger. A closed hint does not
      show again. "Show all hints again" shows the hints again.

Install over the previous release (B-26, check 3). Skip this for the first
release: B-26 does it with two test builds.

- [ ] Uninstall the app. Install the previous published release, start it,
      and make a project. Then run `BananaShot-win-Setup.exe` of the dry run.
      The app closes, or the setup asks you to close it. The new version
      starts. The projects, the preferences, and the export history stay.

Uninstall (B-26, check 5):

- [ ] Uninstall the app in Windows Settings → Apps. The app data
      (`%APPDATA%\BananaShot`) and `HKCU\Software\JavaSoft\Prefs` stay.

If a check fails, fix the cause and start again at step 5.

## 8. Release

- [ ] Push the tag `v<version>` on `master`. The release workflow makes a
      draft release.
- [ ] On the GitHub releases page, check the files and the release notes.
- [ ] The release notes link the natives release.
- [ ] The setup file is `BananaShot-win-Setup.exe`, with no version in the
      name. The stable URL of "Update and restart"
      (`releases/latest/download/BananaShot-win-Setup.exe`) needs this name.
- [ ] Publish the draft within 7 days after the workflow built it. Each day as
      a draft is a day less of use for the users, because the build expiry
      starts at the build date. If more than 7 days passed, delete the draft
      and run the release workflow of the tag again. No script can check the
      day of the publish.
- [ ] Publish the release with "Set as the latest release" selected. The
      stable URL of the setup file points to the latest release.

## 9. After the release

- [ ] Set `latest` in `release/version-policy.json` to the new release, and
      push the change to `master`:
      - `version`: the new version.
      - `downloadUrl`: `https://github.com/zipper2110/banana-shot/releases/latest`.
      - `installerUrl`:
        `https://github.com/zipper2110/banana-shot/releases/download/v<version>/BananaShot-win-Setup.exe`.
      - `notes` (optional): a short text about the release. The app shows it
        as plain text.
- [ ] Set `<revision>` in `pom.xml` to the next version with `-SNAPSHOT`.
- [ ] "Update and restart" (B-26, check 4). Skip this for the first release.
      Install the previous release, and start it. GitHub can need about 5
      minutes to show the new `latest`. The update notice shows the new
      version. Click "Update and restart", then "Update" in the Velopack
      dialog. The new version starts with the same projects, preferences,
      and export queue.
- Recommendation (not a check): wait about 30 days before you add a rule that
  stops the version before this release (N-1). If the new version has a
  serious bug on some computers, users can install N-1 again until a fix is
  available. Stop N-1 earlier when necessary, for example when N-1 can damage
  project files.

## Emergency rebuild

Each build stops 6 months after its build date (`build-expiry-spec.md`,
"Build expiry"). Do this when no normal release is ready and the newest build
expires in less than 60 days. The release age reminder (below) opens an issue
at this time.

- [ ] Push the next patch tag (for example `v1.4.1`) on the commit of the
      newest release. The release workflow builds it with a new build date, so
      the new build works for 6 more months.
- [ ] If the release workflow of the old commit fails (for example, an action
      that GitHub no longer supports), fix the workflow on a branch from the
      release commit. Then push the tag on the commit with the fix.
- [ ] Do steps 8 and 9 for the new build.

Why 60 days: the expiry warning starts 30 days before the expiry. With 60
days, the update notice gets to the users before the warning. Also, a new
user who downloads the newest release gets a build with some months of use.

## Natives releases

- Do not delete a natives release (for example `natives-2026-09`) while a
  release tag uses it. The release workflow downloads the natives release
  that `native-dependencies.json` of the tagged commit names. An emergency
  rebuild of an old commit fails when its natives release is deleted.

## Release age reminder

- The weekly workflow `release-age-reminder.yml` opens an issue when the
  newest release is older than 4 months (`build-expiry-spec.md`, "Release age
  reminder"). It reads the date of `BananaShot-win-Setup.exe` of each
  published release. If the name of the setup file changes, change it also in
  `.github/scripts/release-age-reminder.js`.
- In a public repository, GitHub turns off scheduled workflows after 60 days
  with no activity in the repository. GitHub sends an email to the owner
  before it does this. If you get this email, the reminder can stop: treat
  the email as the reminder, and turn the workflow on again.

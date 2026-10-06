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

### B-8 Requests for bug reports and features

- Ask users to send bug reports and feature requests.
- Decided on 2026-10-03: an in-app feedback form sends the report to a
  Cloudflare Worker, and the Worker sends it to the author with a Telegram
  bot. `docs/feedback/b-8-tasks.md` has the decisions and the tasks.
- Status 2026-10-03: the code of T1 to T5 is done. The Worker and the bot
  work. Open: set the GitHub variable `FEEDBACK_ENDPOINT`, and do the manual
  check of T5 with an installed dry-run build. The site text waits for B-10.
- Replies inside the app are B-33 (post-release).

### B-9 Analytics

- The app code is in `org.litvin.analytics`. The Worker is in
  `analytics-worker`. The design is `docs/analytics/design.md`.
  `docs/analytics/b-9-tasks.md` has the state, the open questions, and the
  tasks.
- Status 2026-10-03: T1 to T6 are done. The Worker is deployed, and the
  GitHub variables are set. Open in T7: the dry run with an installed build,
  and the privacy notice on the site (B-10).

### B-10 Landing site

- Make a landing site for the app.
- `docs/site/b-10-tasks.md` has the decisions and the tasks. Decided on
  2026-10-03: the domain is `banana-shot-editor.app` (on Cloudflare), and
  the first version is the full site.
- Status 2026-10-03: the pages are in `site/public/` (T1 to T6). Not
  deployed yet.
- Reminder: the site must have the privacy notice page before the first
  release. The page (`site/public/privacy/index.html`) has the analytics
  notice (B-9) and the feedback notice (B-8). There is no temporary page
  (decided on 2026-10-03).
  - The GitHub variable `ANALYTICS_PRIVACY_URL` is now the repository URL,
    a temporary value. When the page is live, change it to
    `https://banana-shot-editor.app/privacy/` (B-10 decision 5).
    The consent dialog and the Privacy page of each release open this URL.
  - Write the effective date in the analytics notice when the page goes
    live.
  - The B-9 task T7 stays open until the page is live.

### B-21 First dry run of the release workflow

- The "Windows release" workflow has never run. Start a dry run now with
  the current installer. It can find problems before B-23 and B-24.
- B-26 does the install checks with the installer of the dry run.
- In the app image, run `runtime\bin\java --list-modules`. The JDK 17
  jpackage default can omit `jdk.crypto.ec` and `jdk.crypto.mscapi`, but
  `build-expiry-spec.md` requires them. Write the result in B-23.
- Done when: the dry run passes, and each problem that it finds is fixed or
  is an item in this file. Do the dry run again after B-23 and B-24.
- Status 2026-10-03:
  - The first dry run (version 0.0.1, commit 17bc4b8, Temurin 25.0.4) passed
    in CI. `mvn -B test`: 706 tests, 0 failures, 2 skipped.
    `Validate-AppImage.ps1` passed: the runtime has `jdk.crypto.mscapi` and
    `java.net.http`, and the expiry date is 2027-04-03. All packaged
    diagnostics passed (libmpv load, FFmpeg, `Windows-ROOT` with 564
    certificates, HTTPS). `vpk pack` made `BananaShot-win-Setup.exe` and the
    `.nupkg` package. The workflow artifact has 10 files (336 MB).
  - Expected warnings: the open L-5.2 stories (a real release fails until
    B-4 is done) and the unsigned files (B-22).
  - Not a problem: at the end of the job, the runner stopped an orphan
    `java` process (probably the Kotlin compile daemon). The Maven cache was
    not saved because a different job saved the same key.
  - Step 5 review of the artifact: the SHA-256 file agrees with the setup
    EXE. The SBOM has 20 Maven components. They are the same as the 20
    library JARs in `lib/app/app` of the `.nupkg`. All are `required`, and
    no test library is in the SBOM. The licenses are Apache-2.0, MIT,
    EPL-1.0 or LGPL (Logback), and LGPL-2.1+ or Apache-2.0 (JNA). All
    are acceptable. The `THIRD-PARTY-NOTICES.txt` of the artifact is the
    same as the file in the repository.
  - Problem: `THIRD-PARTY-NOTICES.txt` says that the license texts are in
    the JAR files. These 5 JARs have no license file:
    `kotlin-stdlib-2.2.20`, `kotlin-reflect-2.2.20`, `annotations-13.0`
    (Apache-2.0), `logback-classic-1.5.18`, and `logback-core-1.5.18`
    (EPL-1.0 or LGPL-2.1). Apache-2.0 and EPL-1.0 require a copy of the
    license with the binaries. The notices also do not name `jsvg` (MIT)
    and the JetBrains `annotations`.
  - Fixed 2026-10-03: "Java libraries" in `THIRD-PARTY-NOTICES.txt` now
    lists each library, its version, and its license. The end of the file
    has the full texts of the Apache License 2.0, the EPL-1.0, and the MIT
    License (with the SLF4J, JSVG, and Feather copyright lines). The file
    tells that BananaShot uses Logback under the EPL-1.0 and JNA under the
    Apache License 2.0. Step 6 of `release-checklist.md` now has a check
    that this list agrees with the SBOM.
  - Still to do: a new dry run with the fixed notices. Its installer is
    version N-1 for B-26.

### B-23 Bundle the JDK 25 runtime

- Build with JDK 25 (LTS) and bundle its runtime. Keep the bytecode target
  17 unless a reason to change it comes up.
- Update `ci.yml`, `windows-release.yml`, and the JDK 17 check in
  `Build-AppImage.ps1`. Update the JDK version in the docs.
- Add `--enable-native-access=ALL-UNNAMED` to the jpackage java options.
  JNA calls native code, and JDK 24 and later warn about it.
- Give jpackage an explicit `--add-modules` list. Use `jdeps` to find the
  modules. Add `jdk.crypto.mscapi` (the `Windows-ROOT` key store). In JDK 22
  and later, the elliptic-curve code is in `java.base`.
- Add checks to `DistributionDiagnostics`: the `Windows-ROOT` key store
  loads, and an HTTPS request to GitHub works.
- Check that Kotlin, JNA, FlatLaf, and assertj-swing work with JDK 25.
- Done when: `mvn -B test` and the ui-flow tests pass with JDK 25, and the
  packaged diagnostics pass.
- Status 2026-10-03:
  - Done: `ci.yml`, `windows-release.yml`, `Build-AppImage.ps1`, the docs,
    and the ui-flow spike test use JDK 25. The bytecode target stays 17.
    `--enable-native-access=ALL-UNNAMED` is in the jpackage java options and
    in `BananaShot Diagnostics.cmd`.
  - `jdeps` (JDK 25) gives java.base, java.desktop, java.naming,
    java.net.http, java.prefs, and java.sql. `Build-AppImage.ps1` gives
    `--add-modules` with these modules, java.logging, java.xml,
    jdk.crypto.mscapi, jdk.localedata, jdk.charsets, and jdk.accessibility.
  - `DistributionDiagnostics` checks that `Windows-ROOT` loads and that an
    HTTPS request to `raw.githubusercontent.com` gets an HTTP status.
  - `mvn -B test` passes with Temurin 25.0.2 (local, 2026-10-03). A test app
    image with the module list passed `Validate-AppImage.ps1` up to the
    version check (a local SNAPSHOT build) and the packaged diagnostics.
  - Kotlin 2.2.20, JNA 5.18.1, FlatLaf 3.7.2: compile and tests pass.
  - Found and fixed: in JDK 25, `File.getCanonicalPath` opens the file on
    Windows. `JsonFileIO` made its lock key with it before it took the lock.
    Thus, a reader blocked the move of a writer ("Access is denied"), and
    `JsonFileIOTest` failed. The key now comes from the normalized absolute
    path (`fileLockKey`).
  - `mvn -B -Pui-flow verify` passes with Temurin 25.0.2 (local,
    2026-10-03): 706 unit tests and 17 ui-flow tests, 1 skipped by design
    (`ValidationRecoveryUiFlowIT`). `AssertJSwingCompatibilityUiFlowIT`
    passes, so assertj-swing works with JDK 25.
  - The CI run of the release workflow passed with Temurin 25.0.4
    (2026-10-03, B-21): tests, `Validate-AppImage.ps1`, and the packaged
    diagnostics.
- All "Done when" conditions are true.

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
- Status 2026-10-03: the logic is done, the user interface is not.
  - `UpdateOptions` (in `org.litvin.license.update`) decides if "Update and
    restart" shows and gives the installer URL and the download page (E9-S1).
  - `UpdateAndRestart` asks first when an export runs, downloads the setup
    EXE to a temporary folder with progress (redirects followed), does the
    close sequence, starts the setup with no `--silent` as the last step,
    and exits. A failed download deletes the partial file and keeps the app
    open. A second click during the download has no effect (E9-S2).
  - Tests: `UpdateAndRestartTest` (fake download and setup start, and a local
    HTTP server for the real download).
- Still to do: the buttons in the update notice, the expiry warning, and
  expired mode (E8), and the wiring of the close sequence and the saved time
  (E7). The download uses the trust of E5-S4 (2026-10-03).

### B-26 Pre-release install testing

- This item has all the checks that need the app installed with
  `BananaShot-win-Setup.exe`. Other items refer to it: B-21, B-24, B-30,
  and E11 of `l-5.2-epics.md`. Do the checks together, on a clean Windows
  account.
- No automatic test installs a new version over an old version.
- You need two dry-run builds with different versions: version N-1 and
  version N.
- Checks with one build (version N-1):
  1. Fresh install: do the checks of step 7 of `release-checklist.md`
     (B-21, B-24). The setup installs with no administrator rights, makes
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
  - Checks 1 and 2 need the new dry run of B-21 (with the fixed notices).
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
- Done when: all checks pass with two dry-run builds. Write the result of
  each check in this item.

### B-27 Release workflow hardening

- Pin each third-party action to a commit SHA, mainly
  `softprops/action-gh-release` and `jay0lee/certum-cloud-code-sign`. Keep
  the version as a comment.
- Now `mvn -B test` runs in the job that has the `contents: write` and
  `id-token: write` permissions. Run the tests in a separate job with
  read-only permissions. Give the write permissions only to the job that
  publishes.
- Done when: a dry run passes with the new jobs.

### B-35 GPU popup only with more than one GPU

- At the first start, the app shows the "GPU preference set" popup
  (`SwingApplicationFactory`, `shouldShowGpuRestartNotification`). The popup
  tells the user to restart the app.
- If the system has only one GPU, the GPU preference has no effect, and the
  popup is not necessary. Do not show the popup in this case.
- Count the GPUs before the app shows the popup.
- Done when: a system with one GPU does not show the popup, a system with
  two or more GPUs shows it, and a unit test covers the new rule.

### B-36 Relax the rules for project names

- Now `NewProjectRules.nameError` refuses a project name that is not a
  correct Windows file name: the characters `< > : " / \ | ? *`, a period at
  the end, and reserved names such as `CON` and `COM1`. The cause is that the
  project name is also the name of the project folder.
- Let the user type these names. For example, "Final 3:2" or "Who won?" must
  be correct names.
- Keep the project name and the folder name separate. Make a safe folder name
  from the project name. Keep the maximum length and the rule for an empty
  name.
- Done when: the dialog accepts the names in the examples, the app makes a
  correct folder for them, and the unit tests cover the new rules.

### B-22 Code signing

- Smart App Control is confirmed (2026-10-03, dry run of B-21 on a tester
  computer). The CodeIntegrity log has 3077 events with the Smart App
  Control policy `{0283ac0f-fff1-49ae-ada1-8a933130cad6}`. Windows blocked
  `ffmpeg.exe`, `swscale-8.dll`, `libharfbuzz-0.dll`, and `libstdc++-6.dll`
  (error 4551). The preview was black and the export did not work.
  `BananaShot.exe`, the Velopack files, the Java runtime, and JNA were not
  blocked. Windows stops at the first blocked dependency of `libmpv-2.dll`,
  so the log does not show all files that fail. The user cannot allow a
  blocked file, so the first release must be signed.
- Decision (2026-10-03): Certum "Standard Code Signing in the Cloud" (OV,
  SimplySign cloud HSM, EUR 209 per year, for an individual). The author
  bought it on 2026-10-04. The release workflow signs in CI. Rejected:
  - Azure Artifact Signing: individuals only from the USA and Canada. The
    author is in Georgia.
  - Certum Open Source: ELv2 is not an open source license, and paid
    releases are planned. Certum can revoke the certificate.
  - The Microsoft Store (MSIX), Sectigo, SSL.com, and shared-certificate
    services (for example Bamboo Deploy): more work, a higher price, or
    the files get the publisher name of a different company.
- From 2026-02-27, a certificate is valid for at most 459 days. Certum
  reissues it free of charge for the rest of a multi-year order.
- Done (2026-10-04): `windows-release.yml` signs with the community action
  `jay0lee/certum-cloud-code-sign` and `vpk --signTemplate`. vpk signs each
  EXE and DLL of the app image, the setup EXE, and `Update.exe`.
  `Test-ReleaseSignatures.ps1` checks the signature and the timestamp of
  each EXE and DLL in the setup and the update package. A tag release
  without the signing secrets fails. See `distribution/windows/README.md`,
  "Code signing".
- Done (2026-10-06): the certificate is issued (RSA 3072, valid until
  2027-10-06, thumbprint `DB2D2E3BB8A4BBC15E4F3907C6B8D6136152CDCA`). The
  secrets `CERTUM_USERNAME` and `CERTUM_TOTP_SECRET` and the variable
  `CERTUM_CERT_SHA1` are set in the GitHub environment `windows-release`.
  The signed dry run 37442956227 passed the signature check: the setup EXE
  and 116 EXE and DLL files.
- Done (2026-10-06): the setup of the signed dry run installed on a
  Windows 11 computer with Smart App Control on. The preview and the export
  work. The "Done when" condition is met.
- To do (not blocking the release):
  - Done (2026-10-06): when libmpv does not load or mpv does not start, the
    video area shows an error card in place of a black area. For Windows
    errors 225, 226, 1260 and 4550-4559, the card names Windows security and
    Smart App Control. `LibMpv` reads the error code with `LoadLibraryEx`,
    because the JNA error has only the translated text.
  - Decision (2026-10-06): the error card shows "Report this problem" only
    when the cause can be a bug of the app: the video player does not start,
    or a file does not open for an unknown reason (`UNREADABLE`). For a
    cause that the user can fix (file not found, copy not finished, damaged
    file), the card shows no report button. The report contains the
    technical cause and the log files.
  - Done (2026-10-06): when Windows blocks FFmpeg, the export fails with
    "CreateProcess error=4551". The export error now names Windows security
    and Smart App Control, and it does not give the hardware encoder advice.
    `WindowsSecurityBlock` has the list of block errors for the preview and
    the export.
  - Done (2026-10-06): an installed app uses its own `natives` folder
    before `MPV_PATH`, `FFMPEG_PATH` and `FFPROBE_PATH`. The order for each
    tool is: the `bananashot.*` system property, the bundled file, the
    environment variable, the fallback. In development, the app home has no
    bundle, so `MPV_PATH` still comes before `target/native`.
  - JNA extracts an unsigned `jnidispatch.dll` at run time. On the tester
    computer, Smart App Control did not block it (probably because the file
    has reputation). Keep this in the install check.
  - Change the Smart App Control notes on the site (`download` and `faq`
    pages, B-10) after the first signed release. An OV signature does not
    give SmartScreen reputation at once, so keep the "Run anyway" steps.
    Update (2026-10-06): SmartScreen did not show a warning for the signed
    setup of the dry run on the author's computer. Check this on a second
    computer before you remove the "Run anyway" steps.
  - Chrome blocks the download of the signed dry run (2026-10-06) as
    dangerous or suspicious. The user must allow it on the downloads page.
    Check the file on VirusTotal by hash. If no engine detects it, the cause
    is low download reputation. Then add the Chrome "Keep" steps to the
    `download` page, and submit the setup EXE to Microsoft as a software
    developer.
- Done when: the installer of a signed dry run starts on a Windows 11
  computer with Smart App Control on, and the preview and the export work.

## Post-release

Do these items after the first public release.

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

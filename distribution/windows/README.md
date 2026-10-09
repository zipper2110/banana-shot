# Windows distribution

The Windows x64 package is a self-contained `jpackage` application image and
a per-user Velopack installer (`BananaShot-win-Setup.exe`). It includes Java 25, the pinned LGPL libmpv build, and the pinned
BtbN FFmpeg GPL shared build listed in `native-dependencies.json`. Only the
FFmpeg archive's `bin/` directory and `LICENSE.txt` are bundled; its headers,
import libraries and HTML docs are not.

## Local app-image build

```powershell
.\distribution\windows\Get-NativeDependencies.ps1
mvn test
.\distribution\windows\Build-AppImage.ps1 -Version 1.0.0 -FeedbackEndpoint https://<host>/v1/feedback
.\distribution\windows\Validate-AppImage.ps1 -Version 1.0.0
& ".\target\package\app-image\BananaShot\BananaShot Diagnostics.cmd"
```

`-FeedbackEndpoint` is the URL of the feedback Worker (`feedback-worker/README.md`).
Without it, the feedback form of the app cannot send, and `Validate-AppImage.ps1`
refuses the image. For a local build without the Worker, give
`-AllowNoFeedbackEndpoint` to `Validate-AppImage.ps1`.

`-AnalyticsEndpoint`, `-AnalyticsPrivacyUrl`, and `-AnalyticsNoticeVersion`
turn on the usage analytics (`analytics-worker/README.md`). Give all three or
none. Without them, the app sends no analytics and does not ask for consent,
and `Validate-AppImage.ps1` refuses the image. For a local build without
analytics, give `-AllowNoAnalytics` to `Validate-AppImage.ps1`.

`Validate-AppImage.ps1` runs `org.litvin.license.BuildInfoPrinter` with the
bundled runtime. It fails when the build date is more than 7 days ago or later
than tomorrow, when the expiry date is not 6 calendar months after the build
date, when the app version is not the jpackage `--app-version`, or when the
runtime does not contain the modules for the HTTPS request of the rules file,
or when the image has no valid feedback endpoint or analytics properties.
The packaged diagnostics do not do these checks.

## Velopack installer

`vpk pack` makes the installer and the update package from the app image. It
needs the .NET SDK and the pinned `vpk` version of `windows-release.yml`:

```powershell
dotnet tool install --global vpk --version 1.2.161
.\distribution\windows\Build-VelopackRelease.ps1 -Version 1.0.0
```

The files are in `target\package\velopack`:

- `BananaShot-win-Setup.exe`: the installer. The name is the same in each
  release. "Update and restart" in the app downloads
  `releases/latest/download/BananaShot-win-Setup.exe`.
- `BananaShot-<version>-full.nupkg`: the update package.
- `releases.win.json`: the release feed.

The installer:

- installs for the current Windows user in `%LocalAppData%\BananaShot`, with
  no administrator rights. The app runs from the `current` folder.
- makes a Start menu shortcut and a desktop shortcut.
- shows no wizard and no license page. See "Installer license page" in
  `docs/backlog.md` (B-24).
- over an installed version, asks "Update" or "Cancel", stops the running
  app, installs, and starts the new version.

The app data (`%APPDATA%\BananaShot`) and the preferences
(`HKCU\Software\JavaSoft\Prefs`) are outside the install folder. An update or
an uninstall does not delete them.

Velopack starts the app with a hook argument (`--veloapp-install`,
`--veloapp-updated`, `--veloapp-obsolete`, `--veloapp-uninstall`) when it
installs, updates, or removes the app. The app does not use a Velopack SDK.
`SwingMainApp` exits at once for each argument that starts with `--veloapp-`.
Thus, `vpk pack` needs `--skipVeloAppCheck`.

The app icon source is `src/main/resources/icons/app-icon.svg`. The app uses
this SVG for the window icon. `assets/bananashot.ico` is generated from it.
After you change the SVG, run this command to generate the ICO again:

```powershell
.\distribution\windows\New-AppIcon.ps1 -PngDirectory design\app-icon
```

The site uses two of these PNG files. Copy them after you generate the icon
again: `design/app-icon/bananashot-32.png` to `site/public/favicon-32.png`, and
`design/app-icon/bananashot-256.png` to `site/public/assets/icon-256.png`.

## Release requirements

Use [docs/release-checklist.md](../../docs/release-checklist.md) for each
release. It gives the release steps in sequence. The open work is in
[docs/backlog.md](../../docs/backlog.md).

- Build on Windows x64 with Eclipse Temurin JDK 25 and the .NET SDK (for `vpk`).
- Code signing: see "Code signing" below. A release must be signed. A dry run
  without the signing secrets builds unsigned files and shows a warning.
- The repository variable `FEEDBACK_ENDPOINT` must be the URL of the feedback
  Worker (`https://<host>/v1/feedback`). Without it, the workflow fails at
  "Validate application image".
- The repository variables `ANALYTICS_ENDPOINT` (`https://<host>/v1/session`),
  `ANALYTICS_PRIVACY_URL` (the notice on the landing site), and
  `ANALYTICS_NOTICE_VERSION` (`2`) must be set. Without them, the workflow
  fails at "Validate application image".
- The release workflow creates a draft release. Check it on the GitHub
  releases page, then click "Publish release".
- To test the release workflow without a release, start it by hand: open the
  Actions tab, select "Windows release", click "Run workflow", and type a
  version. This dry run builds, tests and checks everything. It uploads the
  files as the workflow artifact `bananashot-v<version>-dry-run-windows-x64`
  and does not create a tag or a release.
- The release workflow has three jobs:
  - `test` runs `mvn -B test` with a read-only token and no secrets.
  - `windows-installer` starts after `test`. It builds, signs, and checks the
    release files, and uploads them as the workflow artifact. It has a
    read-only token. Only this job uses the GitHub environment
    `windows-release`, which has the signing secrets.
  - `publish` downloads the workflow artifact and creates the draft release.
    Only this job has the `contents: write` permission. Only a tag push starts
    it. A dry run skips it.
- Each workflow pins each action to a full commit SHA. The comment after the
  SHA gives the version. To update an action, get the commit SHA of the new
  tag, for example `gh api repos/softprops/action-gh-release/commits/v3.0.3 --jq .sha`,
  and change the SHA and the comment.
- BananaShot uses the Elastic License 2.0. Bundle only an LGPL build of
  libmpv (`-Dgpl=false`), because the app loads libmpv into its own process.
  `Validate-Release.ps1` checks this.
- Run FFmpeg only as a separate process. The GPL FFmpeg build is then an
  aggregate, and its license does not cover the app.
- Publish the corresponding source of every bundled binary. The app release
  contains the app source. The natives release (see below) contains the
  FFmpeg and libmpv binaries and their source. The release workflow checks
  that the natives release has the source files, and it links the natives
  release in the release notes.
- Review the generated Maven dependency license/SBOM output before release.

## Code signing

Smart App Control (Windows 11) blocks each EXE or DLL that has no trusted
signature and no reputation. The user cannot allow the file. On the test
computer of 2026-10-03, it blocked `ffmpeg.exe` and some DLLs of libmpv, so
the preview was black and the export did not work (B-22).

The release workflow signs with a Certum "Standard Code Signing in the Cloud"
certificate (OV, key in the SimplySign cloud HSM):

1. The step "Sign in to Certum SimplySign" uses the community action
   `jay0lee/certum-cloud-code-sign`, pinned to a full commit SHA. It installs
   SimplySign Desktop on the runner, logs in with a TOTP code, and gives the
   signtool path and the certificate thumbprint.
2. `Build-VelopackRelease.ps1 -SignTemplate` gives vpk the signtool command
   (SHA-256, timestamp from `http://time.certum.pl`). vpk signs each EXE and
   DLL of the app image that has no trusted signature yet: the launcher, the
   FFmpeg files, and the libmpv files. The files of the Java runtime keep the
   signatures of their vendors. Then vpk signs the setup EXE, `Update.exe`,
   and the start stub.
3. `Test-ReleaseSignatures.ps1` opens the update package and checks that each
   EXE and DLL and the setup EXE has a valid signature with a timestamp.

Settings of the GitHub environment `windows-release`:

- Secret `CERTUM_USERNAME`: the e-mail of the SimplySign account.
- Secret `CERTUM_TOTP_SECRET`: the Base32 TOTP secret of SimplySign (the seed
  of the one-time codes, not a code).
- Variable `CERTUM_CERT_SHA1` (optional): the SHA-1 thumbprint of the
  certificate. With it, the action refuses a different certificate.

A tag release without the secrets fails. A dry run without them builds
unsigned files and shows a warning.

Known gap: JNA extracts an unsigned `jnidispatch.dll` from its JAR at run
time. Smart App Control did not block it on the test computer, probably
because the file has reputation.

## Natives release

The natives release keeps copies of the pinned native builds and their
corresponding source in this repository. App builds then do not depend on
upstream hosting. BtbN removes its daily builds after about two weeks.

Make a new natives release only when you change a pin in
`native-dependencies.json`.

1. Start Docker Desktop. The FFmpeg source collection runs in a Linux
   container.
2. Run the script. It downloads the pinned builds from their upstream URLs,
   and the source of each FFmpeg library stage. This takes about one hour and
   needs several GB of free disk space.

   ```powershell
   .\distribution\windows\New-NativesRelease.ps1
   ```

   The files are in `target\natives-release`. The default tag is
   `natives-YYYY-MM`. Use `-ReleaseTag` to set a different tag.
3. On GitHub, open the releases page and click "Draft a new release".
4. In "Choose a tag", type the tag (for example `natives-2026-09`) and select
   "Create new tag". Keep the target branch `master`.
5. Type the tag as the release title. Paste the text of `README.txt` as the
   description.
6. Drag all files from `target\natives-release` into the assets area. Wait
   until each upload is complete.
7. Select "Set as a pre-release". Clear "Set as the latest release". The app
   releases must stay the latest release.
8. Click "Publish release". A draft release is not visible to other users, and
   its download URLs do not work in the release workflow.
9. In `native-dependencies.json`, set `nativesRelease` to the new tag and
   URL. Move each upstream URL to `upstreamUrl` or `upstreamSourceUrl`. Set
   `url` and `sourceUrl` to the natives release
   (`https://github.com/zipper2110/banana-shot/releases/download/<tag>/<file>`).
   Set the FFmpeg `sourceSha256` from `SHA256SUMS.txt`. Do not change the
   other SHA-256 values. The files are the same.
10. Change the natives release link in `distribution/THIRD-PARTY-NOTICES.txt`.
    `Validate-Release.ps1` checks the URLs and the link.

If the FFmpeg source collection fails, fix the cause and run the script again
with `-Resume`. It keeps the stage archives of the failed run.

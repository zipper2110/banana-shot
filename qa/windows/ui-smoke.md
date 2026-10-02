# Packaged Windows UI Smoke

Run this only on Windows from a packaged app image with its libmpv and FFmpeg dependencies present. It checks native integration that the deterministic `ui-flow` Maven tests intentionally fake.

```powershell
pwsh -File qa/windows/Run-UiSmoke.ps1 -KeepArtifacts
```

After the app closes, the runner checks that the app wrote `logs/bananashot.log` in the app-data directory. The runner prints absolute paths for the report, screenshots/artifacts directory, and isolated app-data directory. Use the checked-in `src/test/resources/media/ui-smoke.mp4`; do not substitute a local video. The runner leaves the QA root in place on failure and when `-KeepArtifacts` is supplied. To validate fixture, FFprobe metadata, and a known executable without launching it:

```powershell
pwsh -File qa/windows/Run-UiSmoke.ps1 -ExecutablePath "C:\path\to\BananaShot.exe" -ValidateOnly
```

## Automated native checks

Before the runner opens the app for the manual checklist, it runs `NativeSmokeIT` with Maven (profile `ui-smoke`). This test uses the libmpv and FFmpeg files of the packaged app and the real app window:

- It imports the fixture, sets Colors brightness to 20, and sets Crop rotation to 15 degrees. The mpv preview must show each change, and the project must save each value.
- It does a real FFmpeg export of the full video. The output must have the size and the duration of the source. A frame of the output must show the brightness change and the rotation.

The test moves the mouse. Do not use the mouse or the keyboard until it finishes. The runner writes the result into the report rows "Adjustment controls" and "FFmpeg export". The measured values and the frames are in the `native` folder of the artifacts directory. If the test fails, the runner stops. The Maven reports are in `target/failsafe-reports`, and the window diagnostics are in `target/ui-smoke-artifacts`.

To run only the native checks during development (the app finds the natives as in a development run):

```powershell
mvn -B -Pui-smoke test-compile failsafe:integration-test@ui-smoke failsafe:verify@ui-smoke
```

To do only the manual checklist again, add `-SkipNativeChecks` to the runner command.

## Checklist

1. **Projects launch state** — The app opens on Projects. Project-dependent navigation is unavailable until a project is selected. Capture a screenshot.
2. **Import the fixture** — Choose *Import Match*, use the native chooser to select `ui-smoke.mp4`, and confirm Points opens with the project/source path. Capture a screenshot.
3. **Video playback** — Play, pause, and seek within the short clip. Preview state and timeline position should change without an error dialog. Capture a screenshot.
4. **Edit and score** — In Points mark one point boundary pair; then award Player 1 a point in Scoring. The visible values and saved project data should retain each edit. Capture a screenshot.
5. **Adjustment controls and FFmpeg export** — The automated native checks do these steps. Make sure that their report rows show "Pass".
6. **Relaunch and recents** — Close the app, relaunch it with the same runner-created app-data directory, and confirm the project appears in recents and reopens. Capture a screenshot.

## Report fields

Complete the report path printed by the runner. For every step, record pass/fail, screenshot path, expected result, and actual result. For any failure also record exact reproduction steps, severity (`blocker`, `high`, `medium`, or `low`), and a triage summary identifying product behavior, packaging/native integration, or smoke-harness ownership.

Do not commit generated reports, app data, exports, or screenshots. They belong under ignored `target/ui-smoke`.

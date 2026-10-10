# B-11 E1: Regional Formats

This file holds the tasks of epic E1 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 1 (`i18n` package), 5 (Formats), 14 (Documentation)
- Depends on: —
- Feature: the English app shows dates, times, numbers, and file sizes in
  the Windows regional format. Example: a user with the regional format
  "Spanish (Spain)" sees "10/10/26" in the exports table and "2,35 GB" for a
  size. After E4, the same user with the app in Spanish sees "10 oct 2026".
- Prepares: the format layer of all later epics. It also removes a hidden
  risk now: a JVM with a German default locale writes "1,5" into an FFmpeg
  argument, and a JVM with an Arabic or Persian default locale writes
  Arabic-Indic digits into a time code.
- Size: a few days.

Not in this epic:

- The message catalog. The units ("s", "GB") stay English strings in
  `DisplayFormats`. E3-T20 moves them into the catalog.
- The numbers in the exported video. They keep the current formats until
  E5-T5 gives them the format of the video language.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E1-T1 | `i18n` package and its dependency rule | — | open |
| E1-T2 | `MachineFormats` and the machine texts | E1-T1 | open |
| E1-T3 | Tests with other default locales | E1-T2 | open |
| E1-T4 | Test that finds calls with the default locale | E1-T2 | open |
| E1-T5 | `DisplayFormats` | E1-T1 | open |
| E1-T6 | Use `DisplayFormats` in the UI | E1-T5 | open |
| E1-T7 | Measure the `HOST` locale provider on Windows | E1-T5 | open |
| E1-T8 | Documentation | E1-T1–E1-T7 | open |

### E1-T1 `i18n` package and its dependency rule

- Status: open
- Plan: 1 ("New package `org.litvin.i18n`")
- Work:
  - Make the package `org.litvin.i18n` in `src/main/kotlin/org/litvin/i18n`.
  - The package is a leaf, like `shared.util`. It does not depend on
    another `org.litvin` package.
  - All packages can depend on it.
- Acceptance:
  - `ArchitectureDependencyHygieneTest` fails when a file in `i18n` imports
    another `org.litvin` package.
  - `docs/architecture-rules.md` lists the package and the rule.
- Tests: —

### E1-T2 `MachineFormats` and the machine texts

- Status: open
- Plan: 1, 5 ("Numbers for machines", "Time codes that the user can edit")
- Work:
  - Add `MachineFormats` to `i18n`. It always uses `Locale.ROOT`.
  - Move to `MachineFormats` all formats for FFmpeg arguments, ASS files,
    shader parameters, JSON, and the analytics payload. Start from the 60
    uses of `Locale.US`, `Locale.ROOT`, and `Locale.ENGLISH` in
    `src/main/kotlin`. Examples: `FFmpegCommandBuilder.kt`,
    `AssOverlayWriter.kt`, `export/scoreboard/ScoreboardAss.kt`,
    `export/comments/CommentAss.kt`, `media/mpv/MpvShaderParams.kt`,
    `media/mpv/MpvAssOverlay.kt`, `EdlIO.kt`, `JsonFileIO.kt`.
  - Move the time codes to `MachineFormats`:
    - `shared/util/Timecode.kt`, `Timecode.format`: it calls
      `String.format` without a locale now.
    - `ui/commons/CommentDialog.kt`: the time code that `Timecode.parse`
      reads again.
  - Give `Locale.ROOT` to the hexadecimal color formats
    (`Palette.kt`, `ColorPickerDialog.kt`, `CommentDialog.kt`,
    `ScoreboardDisplay.kt`, `ScoreboardSettingsDialog.kt`,
    `ExportSettingsPreferences.kt`). They are safe now, but the scan test
    of E1-T4 must accept no exceptions.
  - Do not change the output. The current tests stay green without a
    change of the expected values.
- Acceptance:
  - No format for a machine uses the default locale.
  - All current tests pass.
- Tests: —

### E1-T3 Tests with other default locales

- Status: open
- Plan: 5 ("Numbers for machines"), 12
- Work:
  - Add a JUnit extension or a base class that sets the default locale for
    one test and sets the old value again after the test.
  - Run these tests with the default locales `de-DE`, `tr-TR`, and `ar-EG`:
    - the FFmpeg command builder tests (`FFmpegCommandBuilder*Test`),
    - the ASS writer tests (`OverlayAssWriterTest`, `ScoreboardAssTest`,
      `CommentAssTest`, `MpvAssOverlayTest`),
    - `TimecodeTest` and the JSON tests (`JsonFileIOTest`, `EdlIOTest`).
  - Each output must be the same as with `en-US`.
- Acceptance:
  - The tests fail if a machine format uses the default locale. Prove this
    one time: remove `Locale.ROOT` from one call and see the failure.
  - The tests do not change the default locale for the other tests.
- Tests: —

### E1-T4 Test that finds calls with the default locale

- Status: open
- Plan: 5
- Work:
  - Add a source scan test for `src/main/kotlin`. It finds these calls:
    - `"…".format(…)` and `String.format(…)` without a locale,
    - `toUpperCase()` and `toLowerCase()` without a locale,
    - `DateTimeFormatter.ofPattern` without a locale,
    - `NumberFormat` and `DecimalFormat` without a locale.
  - The Kotlin `uppercase()` and `lowercase()` without an argument use
    `Locale.ROOT`. The test accepts them.
  - The test has an allowlist for accepted exceptions. Each entry has a
    reason.
- Acceptance:
  - The allowlist is empty, or each entry has a written reason.
  - A new call without a locale makes the test fail.
- Tests: —

### E1-T5 `DisplayFormats`

- Status: open
- Plan: 5
- Work:
  - Add `DisplayFormats` to `i18n`. It gets the format locale and the app
    language through the constructor. In E1, the app language is always
    English.
  - The format locale is `Locale.getDefault(Locale.Category.FORMAT)`. Do not
    make a locale from the app language and the Windows region (for example
    `en-ES`).
  - Dates: if the language of the format locale is the app language, use
    `FormatStyle.MEDIUM`. If not, use `FormatStyle.SHORT`.
  - Times: the 24-hour or 12-hour form of the format locale.
  - Numbers, percentages, file sizes, and seconds: the format locale.
  - The units stay English strings in this epic (see "Not in this epic").
  - `app` makes one instance at the start and gives it to the presenters.
    Do not change the default locale of the JVM.
- Acceptance:
  - Unit tests with the format locales `en-US`, `es-ES`, and `de-DE` give
    the values of the table in plan section 5.
  - With `es-ES` and the app in English, a date is "10/10/26", and a size
    is "2,35 GB".
- Tests: —

### E1-T6 Use `DisplayFormats` in the UI

- Status: open
- Plan: 5 ("Dates", "Numbers in the UI")
- Work:
  - `ui/tabs/export/ExportsTable.kt`: the date ("d MMM yyyy") and the time
    ("HH:mm").
  - `license/EffectiveExpiry.kt`, `ExpiryMomentFormat`: the expiry moment.
  - `export/RenderFormatting.kt`: `formatSize` and `formatSizeProgress`.
  - `ui/commons/Durations.kt`: `formatSeconds`.
  - `ui/tabs/crop/TransformControls.kt`: the transform values.
  - `ui/tabs/scoring/ui/ScoringPlaybackBar.kt`, the percentages, and the
    other numbers that the user reads. Find them with the uses of
    `Locale.US` in `ui` that E1-T2 did not move.
  - Do not change the time codes that the user can edit. They are machine
    formats (E1-T2).
  - Do not change the texts in the exported video (see "Not in this
    epic").
- Acceptance:
  - With the regional format "Spanish (Spain)", the exports table shows
    "10/10/26", and the sizes show "2,35 GB".
  - With the regional format "English (United States)", the app shows the
    same texts as before this epic, except for the date form of
    `FormatStyle.MEDIUM` ("Oct 10, 2026").
  - The presenter tests use a fixed format locale, so they do not depend
    on the computer that runs them.
- Tests: —

### E1-T7 Measure the `HOST` locale provider on Windows

- Status: open
- Plan: 5 ("An alternative is the JDK `HOST` locale provider")
- Work:
  - On Windows, start the app with `-Djava.locale.providers=HOST,CLDR`.
  - Change the regional format and its custom settings (for example the
    date form) in Windows.
  - Compare the formats with the default provider (CLDR).
- Acceptance:
  - The plan has a decision: keep CLDR, or use `HOST`. Write the reason.
  - If the decision is `HOST`, the build scripts give the property, and
    `Validate-AppImage.ps1` checks it.
- Tests: — (manual)

### E1-T8 Documentation

- Status: open
- Plan: 14
- Work:
  - `docs/architecture-rules.md`: the `i18n` package, `MachineFormats`,
    `DisplayFormats`, and the rule "do not change the default locale of the
    JVM".
  - `CONTRIBUTING.md`: which format to use for a machine and for the user.
  - The plan, "Current state": write that the formats are done.
- Acceptance:
  - A contributor can find the format rules in `CONTRIBUTING.md`.
- Tests: —

## Done when

- All tasks are `done`.
- The FFmpeg and ASS tests pass with the default locales `de-DE`, `tr-TR`,
  and `ar-EG`.
- The scan test of E1-T4 has no unexplained entries in its allowlist.

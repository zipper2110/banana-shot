# B-11 E1: Regional Formats

This file holds the tasks of epic E1 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 1 (`i18n` package), 5 (Formats), 14 (Documentation)
- Depends on: —
- Size: a few days.
- Value:
  - The English app shows dates, times, numbers, and file sizes in the
    Windows regional format of the user. Example: a user with the regional
    format "Spanish (Spain)" sees "10/10/26" in the exports table and
    "2,35 GB" for a size. After E4, the same user with the app in Spanish
    sees "10 oct 2026".
  - The export becomes safe from the regional format. Now a JVM with a
    German default locale can write "1,5" into an FFmpeg argument, and a
    JVM with an Arabic or Persian default locale can write Arabic-Indic
    digits into a time code. Both break the export.
- Prepares: the format layer of all later epics.

Not in this epic:

- The message catalog. The units ("s", "GB") stay English in this epic.
  E3-T20 moves them into the catalog.
- The numbers in the exported video. They keep the current formats until
  E5-T5 gives them the format of the video language.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E1-T1 | `i18n` package and its dependency rule | — | open |
| E1-T2 | Machine formats do not use the default locale | E1-T1 | open |
| E1-T3 | Tests with other default locales | E1-T2 | open |
| E1-T4 | Test that finds calls with the default locale | E1-T2 | open |
| E1-T5 | Display formats | E1-T1 | open |
| E1-T6 | The UI shows the display formats | E1-T5 | open |
| E1-T7 | Decide the locale provider (`HOST` or CLDR) | E1-T5 | open |
| E1-T8 | Documentation | E1-T1–E1-T7 | open |

### E1-T1 `i18n` package and its dependency rule

- Status: open
- Plan: 1 ("New package `org.litvin.i18n`")
- Value: all format and text code has one place. All packages can use it
  without a dependency cycle.
- Requirements:
  - The package `org.litvin.i18n` is a leaf package, like `shared.util`.
    It does not depend on another `org.litvin` package.
  - All packages can depend on it.
- Acceptance:
  - The architecture test fails when a file in `i18n` imports another
    `org.litvin` package.
  - `docs/architecture-rules.md` gives the package and its rule.
- Tests: —

### E1-T2 Machine formats do not use the default locale

- Status: open
- Plan: 1, 5 ("Numbers for machines", "Time codes that the user can edit")
- Value: the export, the preview, and the saved files are correct with all
  Windows regional formats.
- Requirements:
  - All texts that a program reads use one fixed format (`MachineFormats`,
    `Locale.ROOT`): FFmpeg arguments, ASS files, shader parameters, JSON
    files, the EDL file, the analytics payload, and the time codes.
  - A time code that the user can edit uses ASCII digits and ".". The app
    can read each time code that it writes, with all regional formats.
  - The hexadecimal color texts also give the locale. They are safe now,
    but E1-T4 must accept no exceptions.
  - The output does not change. The current tests stay green without a
    change of the expected values.
  - The readers of machine texts also do not use the default locale. A
    project, a queue, or a settings file that the app saved with one
    regional format opens correctly with all other regional formats.
  - A time code that the user types can have "." or "," as the decimal
    separator (see the plan, "Decisions", "Typed decimal separator"). A
    Spanish numeric keypad gives ",".
  - A time code with other characters (letters, two decimal separators)
    gives a clear error in the dialog. The dialog shows a catalog text, not
    the message of an exception. The old value stays.
  - Digits of another script (for example full-width digits "１２" from a
    Japanese input method, or Arabic-Indic digits) give the same value as
    the digits 0–9, or a clear error. They never give a different value.
    Now `toInt()` accepts them, and `split(":")` does not accept a
    full-width "：".
  - If an older build wrote a machine value with the default locale into a
    saved file, the reader accepts it. If no older build did this, write
    the proof in the notes of this task.
- Acceptance:
  - No format for a machine uses the default locale. The scan of E1-T4
    proves it.
  - All current tests pass without a change of the expected values.
  - `Timecode.parse` gives the same value for "1:23.5" and "1:23,5".
  - `Timecode.parse` rejects "1:23.5.0" and "1:2a" with an error, and
    does not crash.
  - `Timecode.parse` of "１：２３" and "١:٢٣" gives the value of "1:23", or
    a clear error. The test writes down which.
  - A test saves a project with the default locale `de-DE`, and reads it
    with `en-US`. The values are the same. The test also does the
    opposite.
- Notes: the time code in the comment dialog is a known risk. It uses
  `String.format` without a locale, and `Timecode.parse` reads it again.
  Start with it. `Timecode.format` also uses `String.format` without a
  locale.
- Tests: —

### E1-T3 Tests with other default locales

- Status: open
- Plan: 5 ("Numbers for machines"), 12
- Value: CI finds a machine format that depends on the default locale
  before a user finds it.
- Requirements:
  - These tests run with the default locales `de-DE` (decimal comma),
    `tr-TR` (a different "i"), `ar-EG` (Arabic-Indic digits), and
    `th-TH-u-nu-thai` (Thai digits and the Buddhist calendar): the FFmpeg
    command tests, the ASS file tests, the time code tests, and the JSON
    and EDL file tests.
  - The tests also set the `FORMAT` and the `DISPLAY` categories of the
    default locale, not only the main default locale. Some calls read a
    category.
  - The output of each test is the same as with `en-US`.
  - A test that sets the default locale sets the old value again after
    the test. It does not change the result of other tests.
- Acceptance:
  - The tests pass with the three locales.
  - If one machine format uses the default locale, a test fails. Prove
    this one time: remove the locale from one call and see the failure.
- Tests: —

### E1-T4 Test that finds calls with the default locale

- Status: open
- Plan: 5
- Value: a new format call without a locale cannot go into the code.
- Requirements:
  - A source scan test of `src/main/kotlin` finds these calls without a
    locale: `"…".format(…)`, `String.format(…)`, `toUpperCase()`,
    `toLowerCase()`, `DateTimeFormatter.ofPattern`, `NumberFormat`,
    `DecimalFormat`, `DecimalFormatSymbols`, `SimpleDateFormat`,
    `java.text.MessageFormat`, `Collator.getInstance()`, `printf`, and
    `java.util.Formatter`.
  - The test also finds an explicit default locale outside `i18n`:
    `Locale.getDefault()` and `Locale.getDefault(…)`. Only `i18n` and the
    app start read the default locale. Thus a call such as
    `String.format(Locale.getDefault(), …)` cannot hide the default
    locale.
  - The test accepts the Kotlin `uppercase()` and `lowercase()` without an
    argument, because they use `Locale.ROOT`.
  - An allowlist holds the accepted exceptions. Each entry has a reason.
  - The test fails if an allowlist entry is no longer in the code.
- Acceptance:
  - A new call without a locale makes the test fail.
  - A new `String.format(Locale.getDefault(), …)` outside `i18n` makes the
    test fail.
  - The allowlist is empty, or each entry has a written reason.
- Tests: —

### E1-T5 Display formats

- Status: open
- Plan: 5, "Decisions" ("Format locale", "Digits")
- Value: one component gives all formats that the user reads. All later
  epics use it.
- Requirements:
  - `DisplayFormats` gets the format locale and the app language. In E1,
    the app language is always English.
  - The format locale is the Windows regional format
    (`Locale.getDefault(Locale.Category.FORMAT)`). Do not make a locale
    from the app language and the Windows region (for example `en-ES`).
  - Dates: if the language of the format locale is the app language, use
    the medium date form of the format locale ("10 oct 2026"). If not, use
    the short form with only digits ("10/10/26"). Thus an English window
    does not show Spanish month names.
  - Times: the 24-hour or the 12-hour form of the format locale.
  - Numbers, percentages, file sizes, and seconds: the decimal and the
    group separators of the format locale.
  - Digits: always the digits 0–9 (see "Decisions", "Digits"). This
    applies also when the format locale has a Unicode extension for the
    digits (`-u-nu-arab`, `-u-nu-thai`).
  - Calendar: always the Gregorian calendar (see "Decisions",
    "Calendar"), also with `th-TH` or `-u-ca-japanese`.
  - Negative numbers use the minus sign of the format locale. Some locales
    use U+2212 (for example `sv-SE`). The UI font must show it.
  - If the JDK has no data for the format locale, or the format locale is
    empty (`Locale.ROOT`, `und`), `DisplayFormats` uses `en-US`. It does
    not throw an exception, and the log gets one warning.
  - The app reads the format locale one time at the start. A change of the
    Windows regional format while the app runs applies at the next start.
    One window never shows two formats.
- Acceptance: unit tests with fixed locales and the app in English give
  these results (measured on JDK 21).

  | Format locale | Date | Time of 15:45 | 2.35 GB | 64 % |
  |---|---|---|---|---|
  | `en-US` | Oct 10, 2026 | 3:45 PM | 2.35 GB | 64% |
  | `es-ES` | 10/10/26 | 15:45 | 2,35 GB | 64 % |
  | `es-MX` | 10/10/26 | 15:45 | 2.35 GB | 64% |
  | `de-DE` | 10.10.26 | 15:45 | 2,35 GB | 64 % |
  | `ar-EG` | only the digits 0–9 | only the digits 0–9 | only the digits 0–9 | only the digits 0–9 |
  | `th-TH-u-nu-thai` | year 2026, only the digits 0–9 | only the digits 0–9 | only the digits 0–9 | only the digits 0–9 |
  | `ja-JP-u-ca-japanese` | year 2026 | — | — | — |
  | `Locale.ROOT` and `xx-XX` | the `en-US` result, no exception | the `en-US` result | the `en-US` result | the `en-US` result |

  - With `de-DE`, the number 1234567 has a group separator ("1.234.567").
    With `sv-SE`, the value −2.5 shows with the minus sign of the locale.
- Notes: the JDK puts special spaces into some formats. "3:45 PM" has
  U+202F (narrow no-break space), and "64 %" has U+00A0 (no-break space).
  The tests must expect these characters. The UI font must show them.
- Tests: —

### E1-T6 The UI shows the display formats

- Status: open
- Plan: 5 ("Dates", "Numbers in the UI")
- Value: the user sees the formats of their Windows regional format.
- Requirements:
  - These texts use `DisplayFormats`: the date and the time in the exports
    table, the expiry moment, the file sizes and the size progress of the
    export, the durations in seconds, the values of the Transform tab, and
    the percentages and other numbers that the user reads.
  - The time codes that the user can edit do not change. They are machine
    formats (E1-T2).
  - The texts in the exported video do not change (see "Not in this
    epic").
  - No code compares or parses a display number as a text. Example: the
    Transform tab now compares the formatted value with `"0.0"` and
    `"-0.0"`. With a decimal comma, this check fails, and the tab can show
    "-0,0" or "+0,0". Compare the number, not its text.
  - A value field that shows a number in the format locale also accepts
    the typed value in this form. The user can type "1,5" or "1.5" (see
    the plan, "Decisions", "Typed decimal separator"). A group separator
    in a typed value is not necessary, because the values are small.
  - A typed value that is not a number keeps the old value, as now.
- Acceptance:
  - With the regional format "Spanish (Spain)", the exports table shows
    "10/10/26" and "15:45", and the sizes show "2,35 GB".
  - With the regional format "English (United States)", the app shows the
    same texts as before this epic, with two known changes: the date
    "Oct 10, 2026" in place of "10 Oct 2026", and the 12-hour time
    "3:45 PM" in place of "15:45". The release notes tell about them.
  - The presenter tests use a fixed format locale. They do not depend on
    the computer that runs them.
  - With `es-ES`, the Transform tab shows "0,0°" for the values 0.0 and
    −0.0, never "-0,0°" or "+0,0°".
  - With `es-ES`, the user types "1,5" and "1.5" in a Transform value
    field, and both give 1.5. The text "abc" keeps the old value.
- Tests: —

### E1-T7 Decide the locale provider (`HOST` or CLDR)

- Status: open
- Plan: 5 ("An alternative is the JDK `HOST` locale provider")
- Value: the formats follow the custom changes of the user in Windows (for
  example a changed date form), if the `HOST` provider does this well.
- Requirements:
  - On Windows, compare the formats of the default provider (CLDR) and of
    `-Djava.locale.providers=HOST,CLDR`. Use a standard regional format and
    a regional format with custom changes.
  - Time box: one day. If the result is not clear, keep CLDR.
  - Also compare the bad cases: a custom date form with a text that is
    not a date part, a custom digit setting, and a custom decimal
    separator that is the same as the group separator. With `HOST`, the
    rules of E1-T5 (digits 0–9, Gregorian calendar) must stay true.
- Acceptance:
  - The plan, "Decisions", has the decision and the reason.
  - If the decision is `HOST`, the installed app uses it, and the
    validation of the app image checks it.
- Tests: — (manual)

### E1-T8 Documentation

- Status: open
- Plan: 14
- Value: a contributor knows which format to use, and does not add a new
  locale risk.
- Requirements:
  - `docs/architecture-rules.md` gives the `i18n` package, the two format
    components, and the rule "do not change the default locale of the
    JVM".
  - `CONTRIBUTING.md` tells which format to use for a machine and for the
    user.
  - The plan, "Current state", tells that the formats are done.
- Acceptance:
  - A contributor can find the format rules in `CONTRIBUTING.md`.
- Tests: —

## Done when

- All tasks are `done`.
- The FFmpeg, ASS, time code, and JSON tests pass with the default locales
  `de-DE`, `tr-TR`, and `ar-EG`.
- The scan test of E1-T4 has no unexplained entries in its allowlist.
- With the regional format "Spanish (Spain)", the English app shows the
  dates, the times, and the numbers in the Spanish format.

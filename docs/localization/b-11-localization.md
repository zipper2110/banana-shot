# B-11 Localization: Plan for More Languages in the App

This file tells what we must change so that the app can show more languages
than English. The backlog item is B-11 in `docs/backlog.md`.

The plan comes from a review of the code on 2026-10-10 (version
`1.0.1-SNAPSHOT`). It is an outline. Each section gives the work, the
decisions, and the risks. The tasks are in one file for each epic (see
"Epics").

## Scope

In the scope:

- The desktop app: all windows, tabs, dialogs, hints, help, tooltips,
  accessible names, and messages.
- The texts in the exported video: the scoreboard, the statistics card, and
  the set summaries.
- The texts that the app gets from a server: the rules file
  (`release/version-policy.json`) and the update notice.
- The contracts and the servers that must know the language: analytics,
  feedback, and the cockpit.
- The installer, the tests, the CI, and the translation workflow.

Not in the scope:

- The landing site (`site/`), its FAQ, its changelog, and its privacy page.
  The app links to the English pages until the site has other languages.
- Right-to-left languages (Arabic, Hebrew). See "9. Right-to-left and
  complex scripts".
- The log files. The log stays in English, because the author reads it.

## Decisions

The author must make these decisions before the work starts. Each row gives a
proposal.

| Subject | Proposal |
|---|---|
| First language | Spanish (Spain and Latin America, also padel). See `docs/marketing/strategy.md` and B-45 P-6 |
| Next languages | The languages of real users. Get them from a new analytics attribute (see "10. Analytics") |
| Video language | A separate setting of each export. The default is the app language (see "6. Texts in the exported video") |
| Format locale | Numbers use the Windows format locale. Dates use the Windows format locale with month names only when its language is the app language (see "5. Formats") |
| Language change | The change applies at the next start. The setting has a "Restart now" button |
| Message format | `.properties` files in UTF-8 with the ICU message syntax, read by ICU4J (see "2. Message catalog") |
| Translation | Machine translation with a glossary as the first draft. A native speaker who plays tennis or padel reviews each language |
| Translation tool | The files stay in the repository. A hosted tool (Weblate or Crowdin) only if volunteer translators come |
| Incomplete language | The app does not show a language until 100% of its texts are translated and reviewed |
| Legal texts | The consent dialog and the Privacy page are translated. The author decides if the English text is the binding version (get legal advice) |
| Notice version | Only a change of the content of the privacy notice increases `notice_version`. A new or updated translation does not |
| Right-to-left | Not in B-11 |

## Goal

A user who does not read English can do all the steps in their language:

- The app starts in the language of Windows, if the app has this language.
  Otherwise it starts in English.
- The user can change the language in More, Settings.
- All texts of the app are in the selected language. Dates, numbers, and
  sizes use the Windows regional format (see "5. Formats").
- The exported video shows the scoreboard and statistics texts in the
  selected video language. All texts fit in their boxes.
- The update notice and the expiry messages are in the selected language.
- A new English text in a pull request cannot go into a release without a
  translation, or the release shows the English text and the CI tells about
  it. The app never shows a message key.

## Current state

- The app has no message catalog. All texts are string literals in Kotlin.
  There are about 1,100 different texts in about 150 files.
- Some texts are in the domain packages, not in `ui`. Examples:
  - `stats/StatRows.kt`: the names of the statistics ("Points won on serve").
  - `stats/StatsCard.kt`: "Match statistics", "Set 1 statistics".
  - `scoring/Sport.kt`: the badges "GOLDEN POINT", "STAR POINT", and
    `title` and `sideNoun`. The default side name "Team 1" is made from the
    parts "Team" and "1".
  - `ScoreboardTimeline.kt`: the default names "Player 1", "Team 1".
  - `scoring/ScoreboardSettingsV1.kt`: the style and position titles, the
    default title "Your tournament or club" (`DEFAULT_TITLE`), and the credit
    line "BananaShot app" (`APP_CREDIT`). See "Saved default texts" below.
  - `scoring/MatchRules.kt`: the titles of the match formats.
  - `stats/StatRows.kt`: the reasons why a statistic is not available
    ("There are not sufficient points.").
  - `export/ExportVideoOptions.kt`, `export/EncoderCapabilities.kt`, and
    `export/ExportPlanner.kt` (`ExportResolution.label`): the quality,
    encoder, and resolution names.
  - `export/ExportFailureAdvice.kt`: the advice after a failed export.
  - `ScoreboardTimeline.kt` and `AssOverlayWriter.kt`: the text overlay
    "Player 1: pts …". Check if the app still uses it. If not, remove it.
  - `ui/expiry/ExpiryTexts.kt` and `license/EffectiveExpiry.kt` say "English
    only until localization (B-11)".
- Saved default texts: the project keeps `ScoreboardSettingsV1.title`, and
  its default is the English text "Your tournament or club". The video shows
  this text. A Spanish user gets an English title in the video, and old
  projects keep the English text.
- Formats use fixed locales: `Locale.US`, `Locale.ROOT`, `Locale.ENGLISH`.
  This is correct for FFmpeg arguments, ASS files, and JSON. It is not
  correct for texts that the user reads, for example the date in
  `ExportsTable.kt` ("d MMM yyyy") and the expiry moment
  (`ExpiryMomentFormat`).
- Some formats use the default locale: 10 calls of `"…".format(…)` and
  `String.format(…)` without a locale. Most write hexadecimal colors, which
  are safe. `ui/commons/CommentDialog.kt` writes a time code with
  `"%02d:%02d:%02d.%03d".format(…)`, and `Timecode.parse` reads it again.
  With a Windows regional format in Arabic or Persian, `String.format`
  writes Arabic-Indic digits (JDK 21 with `ar-EG` gives U+0665 for 5). This
  is a risk now, also without right-to-left languages in the app.
- The Kotlin functions `uppercase()` and `lowercase()` without an argument
  use `Locale.ROOT`. They are safe for machine texts. They are not correct
  for user texts in Turkish or German.
- The bundled runtime has `jdk.localedata`
  (`distribution/windows/Build-AppImage.ps1`). Thus the date and number
  formats of other locales are available, and the packaging needs no change.
- The UI font is "Segoe UI". The scoreboard fonts are Windows fonts (Segoe
  UI, Arial Black, Georgia, Trebuchet MS, Tahoma, Consolas, Ink Free). B-13
  section 5 decided to replace the scoreboard fonts with bundled open fonts.
- The analytics attribute `language` has only the value `en`
  (`AnalyticsEvent.Language`). The analytics worker rejects other values.
- The rules file fields `message` and `latest.notes` are English only.
- The feedback report does not tell the language of the app.
- Shortcuts use letter keys (Q, W, E, R, C, V, A, S, F). The help shows the
  key names in English ("Space", "Shift+Left").

## 1. Architecture

### New package `org.litvin.i18n`

- A leaf package, like `shared.util`. It must not depend on another
  `org.litvin` package.
- All packages can depend on it. Add this rule to
  `docs/architecture-rules.md` and to `ArchitectureDependencyHygieneTest`.
- Contents:
  - `AppLanguage`: the enum of the languages of the app, with the BCP 47
    tag and the name of the language in this language ("Español").
  - `Messages`: gets a text for a key and arguments. One instance for the
    UI language and one for each video language.
  - `Text`: a value with a key and arguments. Domain code returns a `Text`,
    not an English string.
  - `DisplayFormats`: dates, numbers, sizes, and durations for the user.
  - `MachineFormats`: the fixed formats for FFmpeg, ASS, JSON, and the log.
    These always use `Locale.ROOT`.

### Rules for the code

- The domain packages (`stats`, `scoring`, `export`, `projects`, `license`)
  do not make texts for the user. They return enums, `Text` values, or
  numbers. The UI or the overlay writer makes the text.
- Presenters get `Messages` through the constructor and put the final
  strings into the `ViewState`. The views stay passive. The tests of the
  presenters use the English catalog.
- Do not make a sentence from parts. Use one key for the full sentence with
  placeholders. The word order is different in other languages.
- Do not put HTML markup in the catalog if possible. When a text needs bold
  parts, use named placeholders for these parts.
- Do not save a default text in a file. Save "not set" (an empty or null
  value), and show the text from the catalog. For the scoreboard title, the
  empty field shows "Your tournament or club" as a placeholder in the
  language of the app. An old project with the English default text gets
  "not set" when the app reads it. The author decides if `APP_CREDIT` gets a
  translation or stays as the brand text.
- Component names (`name = "more-settings-theme"`) stay in English. Tests
  find components by name, not by text.
- Do not change the default locale of the JVM. Pass the locale to each
  format call. A changed default locale can break the FFmpeg arguments and
  the ASS files (decimal comma in place of a decimal point).

## 2. Message catalog

### Format

- One file for each language: `src/main/resources/i18n/messages.properties`
  (English, the source) and `messages_es.properties`, and so on.
- UTF-8. `ResourceBundle` reads `.properties` files as UTF-8 since Java 9.
  `Properties.load` with an `InputStream` does not. Use a `Reader` there.
- Message syntax: ICU MessageFormat. It has plural forms and selection.
  Russian, Polish, and other languages need more than 2 plural forms
  ("1 point", "2 points", "5 points" are 3 forms in Russian). The JDK
  `MessageFormat` and `ChoiceFormat` cannot do this correctly.
- Library: ICU4J (Unicode license, compatible with the app license). It adds
  about 14 MB to the installer. If the size is a problem (B-29), an
  alternative is our own plural rules for the shipped languages from the
  CLDR data, with the JDK `MessageFormat`. Measure the size before the
  decision.

### Keys

- Form: `<area>.<part>.<item>`. Examples: `export.quality.title`,
  `scoring.settings.deuceRule.goldenPoint`, `stats.row.servicePointsWon`.
- Each key has a comment for the translator: where the text shows, what the
  placeholders are, and the maximum length if the space is small.
- A Kotlin object holds the keys as constants. A test checks that each
  constant has a text in the English file, and that each key in the file
  has a constant (no unused keys).

### Fallback

- A missing text in a language uses the English text. The log gets one
  warning for each missing key.
- A missing key in the English file is a test failure, not a runtime case.

## 3. Extraction of the texts

This is the largest part of the work. Do it in small pull requests, one area
in each. Each pull request moves the texts to the English catalog and does
not change the behavior. The tests stay green.

Areas, in a proposed order:

1. Common widgets and dialogs (`ui/commons`: `MessageDialog`, `DialogKit`,
   `FilePicker`, `HintRegistry`, `TransportButtons`, `SpeedControl`).
2. App shell, sidebar, and the More window (`SwingMainApp`, `ui/more`).
3. Projects tab and the new-project dialogs, with the padel rules.
4. Points tab.
5. Scoring tab and the score settings dialog.
6. Statistics tab and the statistics names (`stats/StatRows.kt`).
7. Colors and Transform tabs.
8. Export tab, the queue, the exports table, and `ExportFailureAdvice`.
9. Expiry and update texts (`ui/expiry`, `license`).
10. Privacy, consent, and feedback (`ui/privacy`, `ui/feedback`).
11. Help (`ui/help/HelpCatalog.kt`). The help is long. It gets its own file
    for each language (`help.properties`, `help_es.properties`).
12. The texts in the video (see section 6).

Leave these texts in English:

- The log messages and the exception messages that only the log shows.
- The diagnostics tab (`ui/tabs/test`). It shows only with the test flag.
- `BananaShot Diagnostics.cmd` and the PowerShell scripts.
- Technical texts in the feedback report (`error`, system info).

A test finds the texts that are still hard-coded. It scans the `ui` package
for string literals given to `text`, `toolTipText`, `title`, and the
dialog functions. It also scans the other main packages (`stats`,
`scoring`, `export`, `projects`, `license`, and the root package) for
string literals in `title`, `label`, `text`, and `const val` texts. Many
user texts are there (see "Current state"). Log messages and exception
messages are not in the scan. An allowlist holds the accepted exceptions
(for example "F1", "4K"). Start the test with all current texts in the
allowlist, and make the list shorter with each extraction pull request.

## 4. Language selection

- At the first start, the app uses the display language of Windows
  (`Locale.getDefault(Locale.Category.DISPLAY)`). If the app does not have
  this language, the app uses English. A regional variant uses the main
  language: `es-AR` and `es-MX` use `es`.
- More, Settings, gets a "Language" row. The list shows each language in its
  own language ("English", "Español"). The preferences node `org/litvin`
  keeps the choice (`ui.language`). The value "system" follows Windows.
- A change applies at the next start. The row shows "The app uses the new
  language after a restart" and a "Restart now" button. A live change is
  possible later, but each custom-painted component must then render again.
  The cost is high and the gain is small.
- The app must do the restart safely: save the open project, and keep the
  export queue (the queue already survives a restart).
- The restart and the instance lock (B-19): the old process holds the lock
  until it exits. A new process that starts before this shows "already
  running" and quits. Thus:
  - Use the close sequence of `UpdateAndRestart`. It also asks the user
    when an export runs.
  - Start the new process with a restart argument. With this argument, the
    new process tries to get the lock again for some seconds before it
    shows "already running".
  - Start the Velopack launcher, not the JAR. Find its path from the
    install folder.
- Swing and FlatLaf have their own texts (for example the context menu of a
  text field). Set the locale of these components with the app language.
  FlatLaf has translations for some languages. The native Windows file
  dialog uses the language of Windows. This is acceptable.

## 5. Formats

- The format locale is the Windows regional format
  (`Locale.getDefault(Locale.Category.FORMAT)`), for example `es-ES`.
- Do not make a locale from the app language and the Windows region (for
  example `en-ES`). The CLDR data does not have most of these locales, and
  the JDK then uses the `en` formats. Measured on JDK 21:

  | Locale | MEDIUM date | `%.2f` of 2.35 |
  |---|---|---|
  | `es-ES` | 10 oct 2026 | 2,35 |
  | `en-ES` | Oct 10, 2026 | 2.35 |
  | `en-DE` | 10 Oct 2026 | 2,35 |
  | `en-US-u-rg-eszzzz` | Oct 10, 2026 | 2.35 |

- Dates:
  - If the language of the format locale is the app language, use
    `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` with the format
    locale ("10 oct 2026").
  - If not, use `FormatStyle.SHORT` with the format locale. It has only
    digits ("10/10/26" for `es-ES`, "10.10.26" for `de-DE`). Thus an English
    window does not show Spanish month names.
  - This applies to `ExportsTable` and `ExpiryMomentFormat`. Times use the
    24-hour or 12-hour form of the format locale.
- An alternative is the JDK `HOST` locale provider. It reads the real
  Windows settings, also the changes of the user. Measure it on Windows
  before you select it.
- Numbers in the UI: always use the format locale. Example: the decimal
  separator of `es-ES` gives "1,5 s". This applies to `formatSeconds`,
  `RenderFormatting` sizes ("2,35 GB"), the transform values, and the
  percentages.
- Numbers for machines: FFmpeg arguments, ASS files, shader parameters, JSON,
  and the analytics payload always use `MachineFormats`. Add a test that runs
  the FFmpeg command builder tests and the ASS writer tests with the default
  locale set to `de-DE`, `tr-TR`, and `ar-EG`. The output must be the same
  as with `en-US`. `de-DE` has a decimal comma, `tr-TR` has a different "i",
  and `ar-EG` has Arabic-Indic digits.
- Time codes that the user can edit (`CommentDialog`, `EditPointDialog`)
  are machine formats: ASCII digits and ".". Thus "1,5 s" and "1:23.5" can
  show in the same window. This is acceptable.
- Units: "s", "GB", "fps". Put the units in the catalog with the number as a
  placeholder. Some languages put a space or a different symbol.
- Upper case: the scoreboard titles use `uppercase(Locale.US)`. Use the
  locale of the video language. German "ß" and Turkish "i" change in a
  different way. The Kotlin `uppercase()` without an argument uses
  `Locale.ROOT`. It is correct for machine texts, not for user texts.
- Sort order: sort project names with a `Collator` of the app locale.
- Time codes (`1:23:45`) and score values (15, 30, 40) stay the same in all
  languages.
- Export file names (`<project>-points-<preset>-1080p.mp4`) stay in English.
  The words are short codes, and a file name with only ASCII letters is
  safer. The project name in the file name can have any letters already.

## 6. Texts in the exported video

The video is the result that the user shares. The people who watch it can
use a different language from the user. Thus the video language is a
separate setting.

### Setting

- The Export tab gets a "Video language" choice. The default is the app
  language. The list has only the languages with a complete video glossary.
- The project keeps the last choice in the export settings. Thus a new
  export of the same project makes the same video.
- A queued render job keeps the language that was selected when the user
  queued it (`SavedRenderQueue`). A language change after the queue does not
  change the job. Do this already in E4, when the video uses the app
  language: a language change restarts the app, and the queue survives the
  restart. `SavedRenderQueue` ignores unknown fields, so older builds can
  read the queue.
- The live preview of the scoreboard and of the statistics card uses the
  video language, not the app language.

### Texts to translate

- Scoreboard: the deciding-point badges (`Sport.kt`: "GOLDEN POINT", "STAR
  POINT", "DECIDING POINT"), the titles of the layouts, "SET" and "GAME"
  marks, and the tiebreak marks.
- The score words. "AD" is common in many languages, but some players use a
  local word (for example "V" for "ventaja" in Spanish). The native reviewer
  of each language decides. Keep the digits.
- Statistics card: the title ("Match statistics", "Set 2 statistics"), the
  names of the statistics, "Point difference", and the momentum chart texts.
- Set summaries.
- The default side names: "Player 1", "Team 1".
- User texts (player names, comments, project names) are not translated.

### Fit

- Translations are often longer than English. German is up to 30% longer,
  and Spanish is about 20% longer. The scoreboard boxes and the statistics
  card columns have fixed sizes.
- Measure each text with `ScoreboardFonts.textWidth`. If a text is too long,
  make the font smaller down to a limit. Then cut the text with "…".
- The catalog comment of each video text gives the maximum length.
- A test renders each scoreboard layout and the statistics card with the
  longest text of each language. Each text must stay inside its box.

### Fonts

- The export (libass), the preview (libmpv), and the Java2D thumbnails must
  use the same font files. Otherwise the preview and the video are
  different.
- Do this together with B-13 section 5: bundle open fonts and give the
  folder to libass and libmpv. Select fonts with Latin Extended, Cyrillic,
  and Greek letters. Examples: Inter, Noto Sans, Roboto.
- Chinese, Japanese, and Korean need large fonts (Noto Sans CJK, more than
  15 MB for each). Add them only with one of these languages, or download
  them on demand.
- This also fixes a current problem: a player name or a comment in Cyrillic
  or another script can show with a fallback font or with empty boxes.
- A test checks that each font can show all letters of the video texts of
  each language (`Font.canDisplayUpTo`).

## 7. The UI layout

- Use layouts that grow with the text. Do not set fixed widths for buttons,
  labels, and table columns with text.
- Custom-painted parts with text need a check: `ScorePanel` ("AFTER THE
  POINT", "SETS", "GAMES"), `SwingTimelineComponent`, `MomentumChart`,
  `StatsTable`, `ProjectsHeader`, the sidebar, and the hint balloons.
- The UI font "Segoe UI" has no Chinese, Japanese, or Korean letters. A
  physical font in Swing does not fall back to another font. A language with
  these letters needs a different UI font (see E8).
- Long words do not wrap. German has long nouns. `WrapText` and the help
  must break a long word if it is wider than the line.
- Test with pseudo-localization (see section 12).

## 8. Shortcuts and key names

- The shortcuts use key codes (`KeyEvent.VK_Q`), so they work with other
  keyboard layouts. On an AZERTY keyboard, Q and W are in different places.
  This is acceptable for B-11. A later item can let the user change the keys.
- With a Cyrillic or Greek keyboard layout, a letter key gives a different
  character. Check that each shortcut works with the Russian and Greek
  layouts. Bind the key code, not the typed character.
- The key names in the help and the hints come from the catalog ("Espacio",
  "Mayús+Izquierda"). The letters stay the same.

## 9. Right-to-left and complex scripts

- Not in B-11. Arabic and Hebrew need a mirrored layout
  (`ComponentOrientation`), and many custom-painted components draw text
  from left to right. The scoreboard layouts also need a mirrored design.
- What B-11 must do now: user texts in any script (player names, comments,
  project names) must work. Test the input with an input method (IME) for
  Chinese and Japanese, and test `SafeFileName` with non-Latin names.

## 10. Analytics

- Add the new values to `AnalyticsEvent.Language`. Add them to
  `analytics-contract/v1/attributes.json`, to `analytics-worker/src/
  attributes.ts`, and to the cockpit.
- Order: deploy the worker that accepts the new values before the app
  release that sends them. The current worker rejects an unknown language
  (see `invalid-summaries.json`, "unknown language").
- The test language `qps` and the languages that only reviewers see (section
  13) are not values of the attribute. When one of them is on, the app sends
  `en`. Thus the worker does not reject the summaries of the reviewers.
- Before the first translation, add the attribute `os_language`: the
  primary language subtag of Windows (for example `es`, `ru`), from a fixed
  list of about 40 values, plus `other`. This tells which languages to add
  next. It is a new attribute of the extended level, so:
  - Change `docs/analytics/design.md`, the privacy notice
    (`docs/analytics/privacy-notice.md` and the site privacy page), the
    consent dialog, and the Privacy page.
  - Increase `notice_version`. The app asks for consent again. Later
    translations of the notice do not increase it (see "Decisions").
  - Release it some weeks before the first translation, to get data.

## 11. Texts from servers and other contracts

### Rules file and update notice

- Add optional fields with translations to `release/version-policy.json`:
  `latest.notesByLanguage` and `messageByLanguage` in each rule, for example
  `{"es": "…"}`. Keep `schema: 1`. Released builds ignore the new fields, and
  this is safe (see `build-expiry-spec.md`, "Changes to the file format").
- The app uses the text of the app language. If there is none, it uses the
  English field.
- The app shows these texts as plain text, as now.
- Add the check of the translated fields to `docs/release-checklist.md`.

### Feedback

- Add the optional fields `app_language` and `os_language` to the feedback
  report (`feedback-contract/v1/report.schema.json`). `os_language` uses the
  same values as the analytics attribute. The schema has
  `additionalProperties: false`, so deploy the feedback worker first.
- The Telegram message shows the language. The user can write in their
  language. The author can use machine translation to read and answer.
- The feedback form says in which languages the author can answer.

### Installer and Windows

- The Velopack installer shows almost no text. Check its texts on a Windows
  in Spanish. The app name "BananaShot" does not change.
- The Windows shortcut names stay "BananaShot".

## 12. Tests and quality checks

- Catalog tests:
  - All language files have the same keys as the English file, or fewer
    (missing keys use the fallback).
  - The placeholders of each translation are the same as in English.
  - Each message parses with ICU.
  - No unused keys, and no key in the code without a text.
- The hard-coded text test of section 3.
- Pseudo-localization: a test language (`qps`) that changes each text, for
  example "Export" to "[Éxƥôŕţ ~~~~]". It makes texts 40% longer and uses
  accented letters. A system property switches it on. It shows hard-coded
  texts, cut texts, and missing letters.
- The video texts of `qps` use only Latin-1 and Latin Extended-A letters
  ("É", "ô", "ŕ", "ţ"). A letter such as "ƥ" (Latin Extended-B) is not in
  some scoreboard fonts, and no planned language uses it. The font tests of
  section 6 find the font gaps.
- The UI flow tests (`ui/flow`) run one time with `qps` in CI and save
  screenshots (`UiFlowArtifacts`). A person checks the screenshots for each
  release with new texts.
- The locale tests of section 5 (`de-DE`, `tr-TR`, `ar-EG` default locale).
- The fit tests and the font tests of section 6.
- Screenshots for translators: the UI flow tests make screenshots of each
  tab in each language. The reviewer checks the texts in their place.
- Windows smoke test (`qa/windows/ui-smoke.md`): add one pass on Windows in
  Spanish for each new language.

## 13. Translation workflow

- The source language is English. The English texts use Simplified
  Technical English (`AGENTS.md`). Short and clear sentences give better
  translations.
- A glossary for each language: `docs/localization/glossary.md` (or a CSV).
  It holds the sport terms and the app terms. Examples: point, game, set,
  tiebreak, deuce, advantage, golden point ("punto de oro"), star point,
  serve, return, break point, favorite, project, export, scoreboard,
  Transform. Each term has one translation, and all texts use it.
- First draft: machine translation with the glossary and the comments of
  the keys.
- Review: a native speaker who plays tennis or padel. Good candidates are
  users from the feedback form or the local chats of the marketing plan.
  The reviewer works from the screenshots, not only from the file.
- Changes to English texts:
  - Each translated file keeps a hash of the English text that it
    translates. A change of the English text makes the translation stale.
  - A CI check lists the missing and the stale keys for each language. It
    does not stop a pull request. It stops a release if a shipped language
    has missing keys (see the release checklist).
  - A stale translation stays in use until the reviewer updates it.
- New languages: the app shows a language only when it is complete and
  reviewed. Until then, a system property can show it for the reviewers.

## 14. Documentation to change

- `docs/architecture-rules.md`: the `i18n` package and the rules of section 1.
- `CONTRIBUTING.md`: how to add a text, and how to add a language.
- `docs/release-checklist.md`: the translation check, the rules file
  translations, and the worker deploy order.
- `docs/analytics/design.md` and the privacy notice: `os_language` and the
  new `language` values.
- `feedback-contract/v1/README.md`: `app_language` and `os_language`.
- `docs/macos/b-13-macos.md`: the bundle declares the languages of the app
  in `Info.plist` (`CFBundleLocalizations`). Without it, the native dialogs
  and the app menu on macOS stay in English.
- `docs/licensing/build-expiry-spec.md`: the translated fields.
- `docs/padel.md`: P-6 points to this plan.
- `README.md`: the list of the languages.
- `distribution/THIRD-PARTY-NOTICES.txt`: ICU4J and the bundled fonts.

## Parts to cover

Not all parts are necessary for the first language. The table tells when
each part becomes necessary. The column "Epic" refers to "Epics".

| Part | Section | Necessary for | Epic |
|---|---|---|---|
| Machine formats safe from the default locale | 5 | All languages. It also protects the current English app | E1 |
| Display formats (dates, numbers, sizes) | 5 | All languages | E1 |
| Language data in analytics and feedback | 10, 11 | The choice of the next languages. Not necessary for Spanish | E2 |
| `i18n` package and message catalog | 1, 2 | All languages | E3 |
| Extraction of the UI texts | 3 | All languages | E3 |
| Extraction of the video texts from the domain packages | 3, 6 | All languages | E3 |
| Saved default texts (scoreboard title) | 1 | All languages | E3 |
| Queued render job keeps its language | 6 | All languages | E4 |
| Pseudo-localization and the hard-coded text test | 12 | All languages | E3 |
| Language setting, detection, and restart | 4 | All languages | E3 (hidden), E4 (shown) |
| Layout fixes for longer texts | 7 | All languages | E3, E4 |
| Fit of the video texts (smaller font, "…") | 6 | All languages | E3 |
| New values of the analytics `language` attribute | 10 | Each new language. Without them, the worker rejects the summaries | E4 |
| Glossary, translation, and review | 13 | Each new language | E4 |
| Separate video language | 6 | Not necessary. A user can share a video in a different language | E5 |
| Translated update notes and expiry messages | 11 | Not necessary. The English text is the fallback | E6 |
| Plural rules with more than 2 forms (ICU) | 2 | Russian, Polish, Czech, Ukrainian, Arabic, and others. Spanish has 2 forms | E3 (syntax), E7 (check) |
| Shortcuts with Cyrillic and Greek keyboard layouts | 8 | Russian, Ukrainian, Greek, and other non-Latin layouts | E7 |
| Bundled fonts for Latin, Cyrillic, and Greek | 6 | macOS (B-13). On Windows, the current fonts have these letters (verify "Ink Free") | E7 |
| Fonts and input methods for CJK and other scripts | 6, 9 | Chinese, Japanese, Korean, Hindi, Thai | E8 |
| Live language change without restart | 4 | Not necessary | Later |
| Right-to-left languages | 9 | Arabic, Hebrew | Later |
| Hosted translation tool | 13 | Not necessary. Only if volunteers come | Later |
| User-defined shortcuts | 8 | Not necessary. AZERTY users can ask for it | Later |

## Epics

Each step of the implementation is an epic. Each epic gives a feature that a
user or the author can see, and the epic prepares the next epic. You can
release the app after each epic. Each epic has its own file with its tasks.

| Epic | File | Feature | Depends on | Size |
|---|---|---|---|---|
| E1 | [b-11-e1-regional-formats.md](b-11-e1-regional-formats.md) | The English app uses the Windows regional format | — | A few days |
| E2 | [b-11-e2-language-data.md](b-11-e2-language-data.md) | The cockpit and the feedback show the languages of the users | — | A few days, plus the time for the data |
| E3 | [b-11-e3-translatable-app.md](b-11-e3-translatable-app.md) | All texts come from the catalog. A test language shows it | E1 | 4 to 5 weeks |
| E4 | [b-11-e4-spanish.md](b-11-e4-spanish.md) | The app and the video in Spanish | E3 (E2 released some weeks before) | 1 to 2 weeks |
| E5 | [b-11-e5-video-language.md](b-11-e5-video-language.md) | A video language separate from the app language | E4 | A few days |
| E6 | [b-11-e6-server-notices.md](b-11-e6-server-notices.md) | Translated update notes and expiry messages | E3 | 1 to 2 days |
| E7 | [b-11-e7-latin-cyrillic-languages.md](b-11-e7-latin-cyrillic-languages.md) | More languages with Latin and Cyrillic letters | E4, E2 | About 1 week for each language |
| E8 | [b-11-e8-other-scripts.md](b-11-e8-other-scripts.md) | Languages with other scripts (CJK) | E7, E2 | 1 to 2 weeks for a new script |

### How to use the epic files

- Each epic file has the feature, the dependencies, a summary table of the
  tasks, and the tasks.
- Each task has a status, the plan sections, the work, the acceptance
  criteria, and the tests that prove it.
- The plan has priority. If the plan changes, change the tasks too.
- Task IDs have the form `E<epic>-T<number>`, for example `E3-T21`. The
  language tasks of E7 and E8 have the form `E7-L-<code>`, for example
  `E7-L-pt`.
- Do not remove a task when it is done. Set its status to `done`, and write
  the test classes or the test methods in "Tests".
- Keep the summary table of each epic file up to date when a task status
  changes. Set the epic status to `done` when all its tasks are done.
- One pull request can contain one or more tasks of one epic. Each pull
  request must pass CI.

Status values:

| Status | Meaning |
|---|---|
| `open` | No work started. |
| `in-progress` | Work started. The task is not complete. |
| `done` | All acceptance criteria pass. "Tests" names the proof. |

### Later

These parts are not in B-11. Make a new backlog item for each one when the
users ask for it: live language change, right-to-left languages, a hosted
translation tool, and user-defined shortcuts.

## Costs

- ICU4J: about 14 MB more in the installer (or our own plural rules).
- Bundled fonts: a few MB for Latin, Cyrillic, and Greek. More than 15 MB
  for each CJK language.
- Native reviewers: their time, or a fee for a professional review.
- A hosted translation tool: free or paid, only if volunteers come.

## Done when

B-11 is done after E6. E7 and E8 continue with each new language.

- The app has a complete Spanish translation, reviewed by a native speaker.
- At the first start on Windows in Spanish, the app shows Spanish.
- The user can change the app language and the video language.
- A Spanish export shows the Spanish scoreboard and statistics texts, and
  all texts fit.
- The FFmpeg and ASS tests pass with the default locales `de-DE`, `tr-TR`,
  and `ar-EG`.
- The hard-coded text test has no UI texts in the allowlist.
- The analytics, feedback, and rules file contracts know the language.
- The Windows smoke test passes in Spanish.

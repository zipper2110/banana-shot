# B-11 Localization: Plan for More Languages in the App

This file tells what we must change so that the app can show more languages
than English. The backlog item is B-11 in `docs/backlog.md`.

The plan comes from a review of the code on 2026-10-10 (version
`1.0.1-SNAPSHOT`). It is an outline. Each section gives the work, the
decisions, and the risks. A task list for each phase comes later.

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
| Language change | The change applies at the next start. The setting has a "Restart now" button |
| Message format | `.properties` files in UTF-8 with the ICU message syntax, read by ICU4J (see "2. Message catalog") |
| Translation | Machine translation with a glossary as the first draft. A native speaker who plays tennis or padel reviews each language |
| Translation tool | The files stay in the repository. A hosted tool (Weblate or Crowdin) only if volunteer translators come |
| Incomplete language | The app does not show a language until 100% of its texts are translated and reviewed |
| Legal texts | The consent dialog and the Privacy page are translated. The author decides if the English text is the binding version (get legal advice) |
| Right-to-left | Not in B-11 |

## Goal

A user who does not read English can do all the steps in their language:

- The app starts in the language of Windows, if the app has this language.
  Otherwise it starts in English.
- The user can change the language in More, Settings.
- All texts of the app are in the selected language. Dates, numbers, and
  sizes use the formats of this language.
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
  - `scoring/Sport.kt`: the badges "GOLDEN POINT", "STAR POINT".
  - `ScoreboardTimeline.kt`: the default names "Player 1", "Team 1".
  - `export/ExportFailureAdvice.kt`: the advice after a failed export.
  - `ui/expiry/ExpiryTexts.kt` and `license/EffectiveExpiry.kt` say "English
    only until localization (B-11)".
- Formats use fixed locales: `Locale.US`, `Locale.ROOT`, `Locale.ENGLISH`.
  This is correct for FFmpeg arguments, ASS files, and JSON. It is not
  correct for texts that the user reads, for example the date in
  `ExportsTable.kt` ("d MMM yyyy") and the expiry moment
  (`ExpiryMomentFormat`).
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
dialog functions. An allowlist holds the accepted exceptions (for example
"F1", "4K"). Start the test with all current texts in the allowlist, and
make the list shorter with each extraction pull request.

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
- Swing and FlatLaf have their own texts (for example the context menu of a
  text field). Set the locale of these components with the app language.
  FlatLaf has translations for some languages. The native Windows file
  dialog uses the language of Windows. This is acceptable.

## 5. Formats

- Dates: `ExportsTable` and `ExpiryMomentFormat` use
  `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` with the app
  locale. Times use the 24-hour or 12-hour form of the locale.
- Numbers in the UI: the decimal separator of the locale ("1,5 s" in
  Spanish). This applies to `formatSeconds`, `RenderFormatting` sizes
  ("2,35 GB"), the transform values, and the percentages.
- Numbers for machines: FFmpeg arguments, ASS files, shader parameters, JSON,
  and the analytics payload always use `MachineFormats`. Add a test that runs
  the FFmpeg command builder tests and the ASS writer tests with the default
  locale set to `de-DE` and `tr-TR`. The output must be the same as with
  `en-US`.
- Units: "s", "GB", "fps". Put the units in the catalog with the number as a
  placeholder. Some languages put a space or a different symbol.
- Upper case: the scoreboard titles use `uppercase(Locale.US)`. Use the
  locale of the video language. German "ß" and Turkish "i" change in a
  different way.
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
  change the job.
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
- Before the first translation, add the attribute `os_language`: the
  primary language subtag of Windows (for example `es`, `ru`), from a fixed
  list of about 40 values, plus `other`. This tells which languages to add
  next. It is a new attribute of the extended level, so:
  - Change `docs/analytics/design.md`, the privacy notice
    (`docs/analytics/privacy-notice.md` and the site privacy page), the
    consent dialog, and the Privacy page.
  - Increase `notice_version`. The app asks for consent again.
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

- Add the optional field `app_language` to the feedback report
  (`feedback-contract/v1/report.schema.json`). The schema has
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
- The UI flow tests (`ui/flow`) run one time with `qps` in CI and save
  screenshots (`UiFlowArtifacts`). A person checks the screenshots for each
  release with new texts.
- The locale tests of section 5 (`de-DE`, `tr-TR` default locale).
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
- `feedback-contract/v1/README.md`: `app_language`.
- `docs/licensing/build-expiry-spec.md`: the translated fields.
- `docs/padel.md`: P-6 points to this plan.
- `README.md`: the list of the languages.
- `distribution/THIRD-PARTY-NOTICES.txt`: ICU4J and the bundled fonts.

## Order of the work

1. **Decisions.** The author makes the decisions of the table above.
2. **Analytics `os_language`** (a few days). Section 10. Release it early to
   get data.
3. **Base** (about 1 week). The `i18n` package, the English catalog, the
   formats (section 5), the locale tests, and the hard-coded text test. No
   visible change.
4. **Extraction** (2 to 3 weeks). Section 3, one pull request for each area.
   Pseudo-localization from the first pull request.
5. **Language setting** (a few days). Section 4.
6. **Video language and fonts** (1 to 2 weeks). Section 6. Do the bundled
   fonts together with B-13 section 5.
7. **Layout fixes** (about 1 week). Sections 7 and 8, from the `qps`
   screenshots.
8. **Contracts** (a few days). Sections 10 and 11. Deploy the workers first.
9. **Spanish** (1 to 2 weeks, mostly review time). The glossary, the draft,
   the review, and the smoke test. This is also B-45 P-6.
10. **Release and beta.** Give the build to a few Spanish-speaking users
    before the public release notes say "Spanish".
11. **More languages.** Use the `os_language` data. Each new language needs
    only steps 9 and 10, plus fonts for a new script.

## Costs

- ICU4J: about 14 MB more in the installer (or our own plural rules).
- Bundled fonts: a few MB for Latin, Cyrillic, and Greek. More than 15 MB
  for each CJK language.
- Native reviewers: their time, or a fee for a professional review.
- A hosted translation tool: free or paid, only if volunteers come.

## Done when

- The app has a complete Spanish translation, reviewed by a native speaker.
- At the first start on Windows in Spanish, the app shows Spanish.
- The user can change the app language and the video language.
- A Spanish export shows the Spanish scoreboard and statistics texts, and
  all texts fit.
- The FFmpeg and ASS tests pass with the default locales `de-DE` and
  `tr-TR`.
- The hard-coded text test has no UI texts in the allowlist.
- The analytics, feedback, and rules file contracts know the language.
- The Windows smoke test passes in Spanish.

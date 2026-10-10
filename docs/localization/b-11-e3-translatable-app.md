# B-11 E3: Translatable App with a Test Language

This file holds the tasks of epic E3 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 1, 2, 3, 4, 6 ("Fit"), 7, 8, 9, 12, 14
- Depends on: E1 (`i18n` package and `DisplayFormats`)
- Feature: a test language. Reviewers and developers start the app with a
  system property, and all texts show in the pseudo-language (`qps`), also
  in the exported video. This shows that all texts come from the catalog
  and that the layout accepts longer texts.
- Prepares: all translations. After this epic, a new language is a new file
  and not a code change.
- Size: 4 to 5 weeks. This is the largest epic. The release after each pull
  request shows no change for English users.

Order of the work:

1. The tools: E3-T1 to E3-T5. Do them before the extraction.
2. The extraction: E3-T6 to E3-T17, one area in each pull request. Each
   pull request moves the texts to the English catalog, does not change the
   behavior, shows its result in `qps`, and makes the allowlist of E3-T4
   shorter.
3. The parts that need the extracted texts: E3-T18 to E3-T24.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E3-T1 | Decide the message library (ICU4J or own plural rules) | — | open |
| E3-T2 | Message catalog and `Messages` | E3-T1 | open |
| E3-T3 | Test language `qps` | E3-T2 | open |
| E3-T4 | Hard-coded text test with an allowlist | E3-T2 | open |
| E3-T5 | UI flow tests with `qps` in CI | E3-T3 | open |
| E3-T6 | Extract: common widgets and dialogs | E3-T2 | open |
| E3-T7 | Extract: app shell, sidebar, and More | E3-T2 | open |
| E3-T8 | Extract: Projects tab and new-project dialogs | E3-T2 | open |
| E3-T9 | Extract: Points tab | E3-T2 | open |
| E3-T10 | Extract: Scoring tab and score settings | E3-T2 | open |
| E3-T11 | Extract: Statistics tab and statistics names | E3-T2 | open |
| E3-T12 | Extract: Colors and Transform tabs | E3-T2 | open |
| E3-T13 | Extract: Export tab, queue, and exports table | E3-T2 | open |
| E3-T14 | Extract: expiry and update texts | E3-T2 | open |
| E3-T15 | Extract: privacy, consent, and feedback | E3-T2 | open |
| E3-T16 | Extract: help and key names | E3-T2 | open |
| E3-T17 | Extract: texts in the video | E3-T2 | open |
| E3-T18 | Saved default texts | E3-T10, E3-T17 | open |
| E3-T19 | Fit of the video texts | E3-T17 | open |
| E3-T20 | Units in the catalog | E3-T2 | open |
| E3-T21 | Language setting, detection, and restart (hidden) | E3-T2 | open |
| E3-T22 | Layout fixes for longer texts | E3-T5, E3-T6–E3-T16 | open |
| E3-T23 | User texts in any script | — | open |
| E3-T24 | Documentation and the empty allowlist | E3-T1–E3-T23 | open |

### E3-T1 Decide the message library

- Status: open
- Plan: 2 ("Format"), "Costs"
- Work:
  - Add ICU4J to a local build, and measure the size of the installer and
    of the app folder. Check if a smaller ICU4J part is possible (for
    example only the plural rules and `MessageFormat`).
  - Compare with the alternative: own plural rules from the CLDR data for
    the shipped languages, with the JDK `MessageFormat`.
  - Check the installer size limits of B-29.
- Acceptance:
  - The plan, "Decisions", has the decision and the measured sizes.
  - The message syntax is ICU MessageFormat in both cases, so the catalog
    does not change in E7.
- Tests: — (decision)

### E3-T2 Message catalog and `Messages`

- Status: open
- Plan: 1, 2
- Work:
  - `i18n`: `AppLanguage` (the enum with the BCP 47 tag and the native name
    of each language), `Messages`, and `Text` (a key with arguments).
  - The English catalog: `src/main/resources/i18n/messages.properties`,
    UTF-8. Read it with a `Reader`, not with `Properties.load(InputStream)`.
  - Keys in the form `<area>.<part>.<item>`. A Kotlin object holds the keys
    as constants. Each key has a comment for the translator: where the text
    shows, what the placeholders are, and the maximum length if the space is
    small.
  - Fallback: a missing text in a language uses the English text. The log
    gets one warning for each missing key. The app never shows a key.
  - `app` makes the UI instance of `Messages` and gives it to the
    presenters through the constructor. The presenter tests use the English
    catalog.
  - Add the library of E3-T1 to `pom.xml` and to
    `distribution/THIRD-PARTY-NOTICES.txt`.
  - The catalog tests (plan section 12):
    - each key constant has a text in the English file, and each key in the
      file has a constant;
    - each message parses with ICU;
    - the language files have the same keys as the English file, or fewer;
    - the placeholders of each translation are the same as in English.
- Acceptance:
  - The catalog tests pass with a first set of keys.
  - A test proves the fallback: a language file without a key gives the
    English text and one log warning.
- Tests: —

### E3-T3 Test language `qps`

- Status: open
- Plan: 12 ("Pseudo-localization"), 10
- Work:
  - A system property switches on `qps`. The app makes the `qps` texts from
    the English catalog at the start. It needs no file.
  - The change: accented letters, and 40% longer texts in brackets, for
    example "Export" to "[Éxƥôŕţ ~~~~]". Keep the placeholders and the ICU
    syntax.
  - The video texts of `qps` use only Latin-1 and Latin Extended-A letters
    ("É", "ô", "ŕ", "ţ").
  - With `qps` on, the analytics attribute `language` is `en`.
- Acceptance:
  - A unit test checks the change, the placeholders, and the letter set of
    the video texts.
  - With the property, the extracted texts show in `qps`.
  - An analytics summary of a `qps` session has `"language": "en"`.
- Tests: —

### E3-T4 Hard-coded text test with an allowlist

- Status: open
- Plan: 3 (last part)
- Work:
  - A source scan test finds the string literals given to `text`,
    `toolTipText`, `title`, and the dialog functions in the `ui` package.
  - It also scans `stats`, `scoring`, `export`, `projects`, `license`, and
    the root package for string literals in `title`, `label`, `text`, and
    `const val` texts.
  - It ignores log messages and exception messages that only the log
    shows, and the diagnostics tab (`ui/tabs/test`).
  - The allowlist starts with all current texts. Each extraction task
    makes it shorter. The test fails if a text is in the allowlist but not
    in the code (so the list does not keep old entries).
- Acceptance:
  - The test passes with the full allowlist.
  - A new hard-coded text makes the test fail.
- Tests: —

### E3-T5 UI flow tests with `qps` in CI

- Status: open
- Plan: 12
- Work:
  - The UI flow tests (`src/test/kotlin/org/litvin/ui/flow`) run one more
    time in CI with `qps`.
  - `UiFlowArtifacts` saves a screenshot of each tab and each dialog that
    the tests open. CI keeps them as an artifact.
- Acceptance:
  - The CI run has the `qps` screenshots.
  - The run with `qps` does not double the CI time. If it does, run it only
    on the main branch and on release tags.
- Tests: —

### Extraction tasks (E3-T6 to E3-T17)

These rules apply to each extraction task:

- Move the texts of the area to the English catalog. Do not change the
  behavior or the English texts.
- Do not make a sentence from parts. Use one key with placeholders.
- Do not put HTML markup in the catalog if possible. Use named
  placeholders for bold parts.
- The domain packages return enums, `Text` values, or numbers, not English
  strings. The UI or the overlay writer makes the text.
- Component names (`name = "…"`) stay in English.
- Leave the log messages, the diagnostics tab, the PowerShell scripts, and
  the technical texts of the feedback report in English.
- Remove the texts of the area from the allowlist of E3-T4.
- Check the area in `qps` (E3-T5 screenshots). Write the layout problems
  into E3-T22.

Acceptance of each extraction task:

- The allowlist has no texts of the area.
- All tests pass. The presenter tests use the English catalog.
- In `qps`, the area shows no English text.

### E3-T6 Extract: common widgets and dialogs

- Status: open
- Plan: 3 (area 1)
- Work: `ui/commons`: `MessageDialog`, `DialogKit`, `FilePicker`,
  `HintRegistry`, `TransportButtons`, `SpeedControl`, and the other shared
  widgets. Set the locale of the Swing and FlatLaf components with the app
  language (plan section 4), so that their own texts follow it.
- Tests: —

### E3-T7 Extract: app shell, sidebar, and More

- Status: open
- Plan: 3 (area 2)
- Work: `SwingMainApp`, the sidebar, and `ui/more` (the More window, the
  Settings page, and the About page).
- Tests: —

### E3-T8 Extract: Projects tab and new-project dialogs

- Status: open
- Plan: 3 (area 3), 5 ("Sort order")
- Work:
  - `ui/tabs/projects`, the new-project dialogs, and the padel rules.
  - Sort the project names with a `Collator` of the app locale.
- Tests: —

### E3-T9 Extract: Points tab

- Status: open
- Plan: 3 (area 4)
- Work: `ui/tabs/points`, with `EditPointDialog` ("Invalid time format").
- Tests: —

### E3-T10 Extract: Scoring tab and score settings

- Status: open
- Plan: 3 (area 5), "Current state"
- Work:
  - `ui/tabs/scoring` and the score settings dialog.
  - `scoring/MatchRules.kt`: the titles of the match formats.
  - `scoring/ScoreboardSettingsV1.kt`: the style and position titles.
  - `ScorePanel` ("AFTER THE POINT", "SETS", "GAMES").
- Tests: —

### E3-T11 Extract: Statistics tab and statistics names

- Status: open
- Plan: 3 (area 6), "Current state"
- Work:
  - `ui` of the Statistics tab, `StatsTable`, and `MomentumChart`.
  - `stats/StatRows.kt`: the names of the statistics and the reasons why a
    statistic is not available. `stats` returns keys or enums.
  - The statistics card of the video is in E3-T17.
- Tests: —

### E3-T12 Extract: Colors and Transform tabs

- Status: open
- Plan: 3 (area 7)
- Work: `ui/tabs/adjustments` and `ui/tabs/crop`.
- Tests: —

### E3-T13 Extract: Export tab, queue, and exports table

- Status: open
- Plan: 3 (area 8), "Current state"
- Work:
  - `ui/tabs/export`: the Export tab, the queue, and `ExportsTable`.
  - `export/ExportVideoOptions.kt`, `export/EncoderCapabilities.kt`, and
    `export/ExportPlanner.kt` (`ExportResolution.label`): the quality,
    encoder, and resolution names.
  - `export/ExportFailureAdvice.kt`: the advice after a failed export.
  - `export/RenderFormatting.kt`: `formatCutMode` and `formatContent`.
  - Export file names stay in English (plan section 5).
- Tests: —

### E3-T14 Extract: expiry and update texts

- Status: open
- Plan: 3 (area 9)
- Work:
  - `ui/expiry` (`ExpiryTexts.kt`, `ExpiryBar`, `ExpiredDialog`,
    `UpdateRunner`) and the texts of `license`.
  - Remove the comments "English only until localization (B-11)" from
    `ExpiryTexts.kt` and `license/EffectiveExpiry.kt`.
  - The texts from the rules file stay English. E6 translates them.
- Tests: —

### E3-T15 Extract: privacy, consent, and feedback

- Status: open
- Plan: 3 (area 10)
- Work:
  - `ui/privacy` and `ui/feedback`.
  - The constants `AnalyticsConsentDialog.INTRO`,
    `PrivacyPage.ANALYTICS_COLLECTED`, and `ANALYTICS_EXCLUDED` (B-9
    decision 16) become keys. Their tests check the English catalog.
  - The move of the texts does not change `NOTICE_VERSION`.
- Tests: —

### E3-T16 Extract: help and key names

- Status: open
- Plan: 3 (area 11), 8
- Work:
  - `ui/help/HelpCatalog.kt`. The help gets its own file for each language
    (`help.properties`, then `help_es.properties`, and so on). Add it to the
    catalog tests of E3-T2.
  - The key names in the help and the hints come from the catalog
    ("Space", "Shift+Left"). The letters (Q, W, E) stay the same.
- Tests: —

### E3-T17 Extract: texts in the video

- Status: open
- Plan: 3 (area 12), 6 ("Texts to translate")
- Work:
  - `app` makes one `Messages` instance for the video. In E3, the video
    language is the app language.
  - `scoring/Sport.kt`: the badges ("GOLDEN POINT", "STAR POINT",
    "DECIDING POINT"), `title`, and `sideNoun`. The default side names
    ("Player 1", "Team 1") get one key each with the number as a
    placeholder. Do not make them from "Team" and "1".
  - `ScoreboardTimeline.kt`: the default names.
  - The scoreboard layouts (`export/scoreboard`): the layout titles, the
    "SET" and "GAME" marks, the tiebreak marks, and the score words ("AD").
    The digits stay.
  - `stats/StatsCard.kt`: the title ("Match statistics", "Set 2
    statistics"), "Point difference", and the momentum chart texts.
  - The set summaries.
  - Upper case: use the locale of the video language in place of
    `uppercase(Locale.US)`.
  - `ScoreboardTimeline.kt` and `AssOverlayWriter.kt`: the overlay "Player
    1: pts …". If the app does not use it, remove it. If not, extract it.
- Tests: —

### E3-T18 Saved default texts

- Status: open
- Plan: 1 ("Rules for the code"), "Current state" ("Saved default texts")
- Work:
  - `ScoreboardSettingsV1.title`: save "not set" (empty or null), not the
    English default. The empty field shows "Your tournament or club" as a
    placeholder from the catalog.
  - When the app reads an old project with the English default text
    `DEFAULT_TITLE`, the title becomes "not set".
  - The author decides if `APP_CREDIT` ("BananaShot app") gets a
    translation or stays as the brand text. Write the decision in the plan.
  - Find other saved default texts and do the same.
- Acceptance:
  - A new project saves no English default text.
  - A test reads an old project file with the English default and gets
    "not set".
  - In `qps`, the video shows the `qps` placeholder title.
- Tests: —

### E3-T19 Fit of the video texts

- Status: open
- Plan: 6 ("Fit")
- Work:
  - Measure each video text with `ScoreboardFonts.textWidth`. If the text is
    too long, make the font smaller down to a limit. Then cut the text with
    "…".
  - The catalog comment of each video text gives the maximum length.
  - A test renders each scoreboard layout and the statistics card with the
    `qps` texts. Each text must stay inside its box.
  - The export (libass), the preview (libmpv), and the thumbnails (Java2D)
    use the same fit result.
- Acceptance:
  - The fit test passes with `qps` for all layouts.
  - The English video does not change.
- Tests: —

### E3-T20 Units in the catalog

- Status: open
- Plan: 5 ("Units")
- Work:
  - Move the units of `DisplayFormats` ("s", "GB", "MB", "KB", "fps", "%")
    to the catalog, with the number as a placeholder.
- Acceptance:
  - In `qps`, the units show in the pseudo-language.
- Tests: —

### E3-T21 Language setting, detection, and restart (hidden)

- Status: open
- Plan: 4
- Work:
  - Detection: at the first start, the app uses
    `Locale.getDefault(Locale.Category.DISPLAY)`. A regional variant uses
    the main language (`es-AR` uses `es`). If the app does not have the
    language, it uses English.
  - The preferences node `org/litvin` keeps the choice (`ui.language`). The
    value `system` follows Windows.
  - More, Settings, gets a "Language" row. Each language shows in its own
    language. The row shows "The app uses the new language after a restart"
    and a "Restart now" button.
  - The row stays hidden while English is the only language. The `qps`
    property shows it, with `qps` in the list.
  - Restart:
    - Use the close sequence of `UpdateAndRestart`
      (`license/update/UpdateAndRestart.kt`). It saves the open project and
      asks the user when an export runs. The export queue survives the
      restart.
    - Start the Velopack launcher with a restart argument. Find its path
      from the install folder. Do not start the JAR.
    - With the restart argument, the new process tries to get the instance
      lock (B-19) again for some seconds before it shows "already running".
- Acceptance:
  - Unit tests for the detection: `es-AR`, `es-MX`, `fr-FR` (not in the
    app: English), and `system`.
  - "Restart now" restarts the installed app on Windows, and the new
    process does not show "already running".
  - Without the `qps` property, the row does not show.
- Tests: —

### E3-T22 Layout fixes for longer texts

- Status: open
- Plan: 7
- Work:
  - Use layouts that grow with the text. Remove fixed widths of buttons,
    labels, and table columns with text.
  - Check the custom-painted parts: `ScorePanel`, `SwingTimelineComponent`,
    `MomentumChart`, `StatsTable`, `ProjectsHeader`, the sidebar, and the
    hint balloons.
  - Long words: `WrapText` and the help break a word that is wider than the
    line.
  - Fix the problems from the `qps` screenshots of the extraction tasks.
- Acceptance:
  - The `qps` screenshots show no cut text and no text outside its box.
- Tests: —

### E3-T23 User texts in any script

- Status: open
- Plan: 9 ("What B-11 must do now")
- Work:
  - Test the input of player names, comments, and project names with an
    input method (IME) for Chinese and Japanese on Windows.
  - Test `SafeFileName` with non-Latin names (Cyrillic, Greek, Chinese).
  - Fix the problems that you find.
- Acceptance:
  - A unit test for `SafeFileName` with non-Latin names passes.
  - The manual IME check is written in `qa/windows/ui-smoke.md`.
- Tests: —

### E3-T24 Documentation and the empty allowlist

- Status: open
- Plan: 14, "Done when"
- Work:
  - `CONTRIBUTING.md`: each new text goes into the catalog. How to add a
    key and its comment. How to add a language.
  - `docs/architecture-rules.md`: the rules of plan section 1, "Rules for
    the code".
  - `distribution/THIRD-PARTY-NOTICES.txt`: check the library of E3-T1.
  - The plan, "Current state": write what E3 changed.
- Acceptance:
  - The allowlist of E3-T4 has no UI texts. Only short codes ("F1", "4K")
    stay.
  - In `qps`, the app and the exported video show no English text.
- Tests: —

## Done when

- All tasks are `done`.
- In `qps`, all texts of the app and of the exported video come from the
  catalog, and all video texts fit.
- The English app and the English video do not change.

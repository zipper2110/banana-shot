# B-11 E3: Translatable App with a Test Language

This file holds the tasks of epic E3 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 1, 2, 3, 4, 6 ("Fit"), 7, 8, 9, 12, 14
- Depends on: E1 (`i18n` package and display formats)
- Size: 4 to 5 weeks. This is the largest epic.
- Value:
  - A new language becomes a new file, not a code change.
  - A test language (`qps`) proves it: with a system property, all texts
    of the app and of the exported video show in the pseudo-language, and
    the layout accepts texts that are 40% longer.
  - English users see no change. The app can be released after each pull
    request.
- Prepares: all translations (E4, E7, E8).

Order of the work:

1. The tools: E3-T1 to E3-T5. Do them before the extraction.
2. The extraction: E3-T6 to E3-T17, one area in each pull request.
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
- Value: correct plural forms in all later languages, at a known cost in
  installer size.
- Requirements:
  - Measure the size of the installer and of the app folder with ICU4J.
    Check if a smaller part of ICU4J is possible (only the plural rules and
    `MessageFormat`).
  - Compare with the alternative: own plural rules from the CLDR data, with
    the JDK `MessageFormat`.
  - Compare the sizes with the installer size limits of B-29.
- Acceptance:
  - The plan, "Decisions", has the decision and the measured sizes.
  - The catalog syntax is ICU MessageFormat in both cases. Thus the catalog
    does not change when E7 adds languages with more plural forms.
- Tests: — (decision)

### E3-T2 Message catalog and `Messages`

- Status: open
- Plan: 1, 2
- Value: one source for all texts of the app. A translator gets the text,
  its place, and its placeholders. The app never shows a key.
- Requirements:
  - The catalog has one file for each language. English is the source.
    The files are UTF-8, and all letters (for example "ñ", "ü", "я") read
    correctly.
  - Each key has the form `<area>.<part>.<item>` and a comment for the
    translator: where the text shows, what each placeholder is, and the
    maximum length if the space is small.
  - Placeholders have names (`{playerName}`), not numbers. A translator
    can change their order.
  - Plural texts use the ICU plural syntax.
  - An apostrophe in a text shows as written ("don't", "l'export"). A
    translator does not need to write it two times.
  - A placeholder value shows as given. A player name with "{", "'", or
    "<b>" does not change the message and does not become markup.
  - The code refers to keys through constants, not through free strings.
  - Domain code returns a `Text` (a key with arguments), an enum, or a
    number. The UI or the overlay writer makes the final string.
  - Fallback: a missing text in a language uses the English text. The log
    gets one warning for each missing key. A missing English text is a
    test failure, not a runtime case.
  - The app gives the UI instance of `Messages` to the presenters. The
    presenter tests use the English catalog.
  - The library of E3-T1 is in the build and in the third-party notices.
- Acceptance:
  - The catalog tests pass with a first set of keys:
    - each key constant has an English text, and each English key has a
      constant;
    - each message parses;
    - each language file has the same keys as the English file, or fewer;
    - the placeholders of each translation are the same as in English.
  - A test proves the fallback: a language file without a key gives the
    English text and one log warning.
  - A test proves the apostrophe rule and the rule for placeholder values.
- Tests: —

### E3-T3 Test language `qps`

- Status: open
- Plan: 12 ("Pseudo-localization"), 10
- Value: a developer or a reviewer sees at once which texts are still hard
  coded, which texts are cut, and which letters are missing.
- Requirements:
  - A system property switches on `qps`. The app makes the `qps` texts from
    the English catalog at the start. It needs no file.
  - The change: accented letters, 40% longer texts, and brackets around
    each text, for example "Export" to "[Éxƥôŕţ ~~~~]". Placeholders and
    the ICU syntax stay.
  - The video texts of `qps` use only Latin-1 and Latin Extended-A letters
    ("É", "ô", "ŕ", "ţ").
  - With `qps` on, the analytics attribute `language` is `en`, and the
    feedback field `app_language` is `en`.
- Acceptance:
  - A unit test checks the change, the placeholders, and the letter set of
    the video texts.
  - With the property, the extracted texts show in `qps`.
  - An analytics summary of a `qps` session has `"language": "en"`.
- Tests: —

### E3-T4 Hard-coded text test with an allowlist

- Status: open
- Plan: 3 (last part)
- Value: CI finds a new hard-coded English text. The allowlist shows the
  progress of the extraction.
- Requirements:
  - A source scan test finds the string literals that the user can see:
    - in the `ui` package: the texts given to `text`, `toolTipText`,
      `title`, accessible names, and the dialog functions;
    - in `stats`, `scoring`, `export`, `projects`, `license`, and the root
      package: the literals in `title`, `label`, `text`, and `const val`
      texts.
  - It ignores log messages, exception messages that only the log shows,
    and the diagnostics tab.
  - The allowlist starts with all current texts. Each extraction task
    makes it shorter.
  - The test fails if an allowlist entry is no longer in the code. Thus
    the list does not keep old entries.
- Acceptance:
  - The test passes with the full allowlist.
  - A new hard-coded text makes the test fail.
- Tests: —

### E3-T5 UI flow tests with `qps` in CI

- Status: open
- Plan: 12
- Value: each pull request shows its texts and layout in `qps` without a
  manual start of the app.
- Requirements:
  - The UI flow tests run one more time in CI with `qps`.
  - The run saves a screenshot of each tab and each dialog that the tests
    open. CI keeps them as an artifact.
- Acceptance:
  - The CI run has the `qps` screenshots.
  - The `qps` run adds less than the time of one normal UI flow run. If it
    adds more, it runs only on the main branch and on release tags.
- Tests: —

### Extraction tasks (E3-T6 to E3-T17)

Each extraction task moves the texts of one area to the English catalog.
The value of each task: the area is ready for translation, and English
users see no change.

Requirements of each extraction task:

- All texts that the user can see in the area come from the catalog:
  labels, buttons, menus, tooltips, window titles, table headers,
  accessible names and descriptions, hints, status texts, and messages.
- The English texts do not change.
- A sentence is one key with placeholders. Do not make a sentence from
  parts, because the word order is different in other languages.
- No HTML markup in the catalog, if possible. Use named placeholders for
  bold parts.
- The domain packages return enums, `Text` values, or numbers, not English
  strings.
- Component names (`name = "…"`) stay in English. Tests find components by
  name.
- These texts stay in English: the log, the exception messages that only
  the log shows, the diagnostics tab, the PowerShell scripts, and the
  technical texts of the feedback report.
- Write the layout problems that `qps` shows into E3-T22.

Acceptance of each extraction task:

- The allowlist of E3-T4 has no texts of the area.
- All tests pass. The presenter tests use the English catalog.
- In `qps`, the area shows no English text (except the brand name
  "BananaShot" and short codes such as "4K").

The tasks below give the area and the special requirements of the area.

### E3-T6 Extract: common widgets and dialogs

- Status: open
- Plan: 3 (area 1), 4 (Swing and FlatLaf texts)
- Area: the shared widgets and dialogs (message dialogs, file pickers,
  hints, transport buttons, speed control).
- Special requirements:
  - The texts of Swing and FlatLaf (for example "OK", "Cancel", and the
    context menu of a text field) follow the app language.
- Tests: —

### E3-T7 Extract: app shell, sidebar, and More

- Status: open
- Plan: 3 (area 2)
- Area: the main window, the sidebar, and the More window (Settings,
  About).
- Tests: —

### E3-T8 Extract: Projects tab and new-project dialogs

- Status: open
- Plan: 3 (area 3), 5 ("Sort order")
- Area: the Projects tab, the new-project dialogs, and the padel rules.
- Special requirements:
  - The project names sort in the order of the app language (for example,
    "Ángel" comes before "Bruno" in Spanish).
- Tests: —

### E3-T9 Extract: Points tab

- Status: open
- Plan: 3 (area 4)
- Area: the Points tab and the point dialogs.
- Tests: —

### E3-T10 Extract: Scoring tab and score settings

- Status: open
- Plan: 3 (area 5), "Current state"
- Area: the Scoring tab, the score panel ("AFTER THE POINT", "SETS",
  "GAMES"), the score settings dialog, the titles of the match formats,
  and the titles of the scoreboard styles and positions.
- Tests: —

### E3-T11 Extract: Statistics tab and statistics names

- Status: open
- Plan: 3 (area 6), "Current state"
- Area: the Statistics tab, the statistics table, the momentum chart, the
  names of the statistics, and the reasons why a statistic is not
  available.
- Special requirements:
  - The statistics names are shared with the statistics card of the video
    (E3-T17). The `stats` package returns keys or enums.
- Tests: —

### E3-T12 Extract: Colors and Transform tabs

- Status: open
- Plan: 3 (area 7)
- Area: the Colors tab and the Transform tab.
- Tests: —

### E3-T13 Extract: Export tab, queue, and exports table

- Status: open
- Plan: 3 (area 8), "Current state"
- Area: the Export tab, the queue, the exports table, the names of the
  qualities, encoders, and resolutions, the cut mode and content texts,
  and the advice after a failed export.
- Special requirements:
  - The export file names stay in English (plan section 5).
- Tests: —

### E3-T14 Extract: expiry and update texts

- Status: open
- Plan: 3 (area 9)
- Area: the expiry bar, the expired dialog, the update texts, and the
  texts of the `license` package.
- Special requirements:
  - The texts from the rules file stay as the server sends them. E6
    translates them.
  - Remove the comments "English only until localization (B-11)".
- Tests: —

### E3-T15 Extract: privacy, consent, and feedback

- Status: open
- Plan: 3 (area 10)
- Area: the consent dialog, the Privacy page, and the feedback form.
- Special requirements:
  - The privacy texts that tests protect now (B-9 decision 16) become
    keys. Their tests check the English catalog.
  - The move does not change the notice version.
  - The feedback report that the author gets (sent, copied, or by email)
    keeps its structure and its field names in English. The text of the
    user stays as the user wrote it.
- Tests: —

### E3-T16 Extract: help and key names

- Status: open
- Plan: 3 (area 11), 8
- Area: the help and the key names in the help and in the hints.
- Special requirements:
  - The help has its own catalog file for each language, because it is
    long. The catalog tests of E3-T2 include it.
  - The key names come from the catalog ("Space", "Shift+Left"). The
    letter keys (Q, W, E) stay the same in all languages.
- Tests: —

### E3-T17 Extract: texts in the video

- Status: open
- Plan: 3 (area 12), 6 ("Texts to translate")
- Area: the scoreboard (badges such as "GOLDEN POINT", layout titles,
  "SET" and "GAME" marks, tiebreak marks, score words such as "AD"), the
  statistics card (titles, statistics names, "Point difference", the
  momentum chart), the set summaries, and the default side names ("Player
  1", "Team 1").
- Special requirements:
  - The video texts have their own `Messages` instance. In E3, the video
    language is the app language. E5 makes it separate.
  - The export, the preview, and the thumbnails show the same texts.
  - Each default side name is one key with the number as a placeholder.
    Do not make it from "Team" and "1".
  - The digits of the score stay the same in all languages.
  - Upper case uses the rules of the video language (German "ß", Turkish
    "i"), not the rules of English.
  - The text overlay "Player 1: pts …": if the app does not use it, remove
    it. If the app uses it, extract it.
- Tests: —

### E3-T18 Saved default texts

- Status: open
- Plan: 1 ("Rules for the code"), "Current state" ("Saved default texts")
- Value: a project shows its default texts in the current language. Now
  the English default title goes into each project file and into each
  video.
- Requirements:
  - A project saves "not set" for a text that the user did not change, not
    the English default.
  - The scoreboard title:
    - With "not set", the video shows the default title "Your tournament or
      club" in the video language. The empty title field shows the same
      text as a placeholder in the app language.
    - If the user clears the field, the title becomes "not set" again. To
      show no title, the user turns off the "Title" switch, as now.
  - An old project with the English default title reads as "not set".
  - Use a saved form of "not set" that an older build can open (for
    example, leave out the field, so that the older build uses its English
    default).
  - The author decides if the credit line "BananaShot app" gets a
    translation or stays as the brand text. Write the decision in the
    plan, "Decisions".
  - The same rule applies to other saved default texts. Find them.
- Acceptance:
  - A new project saves no English default text.
  - A test reads an old project file with the English default title and
    gets "not set".
  - A test proves that the reader of the current release opens a project
    with "not set" without an error.
  - In `qps`, the video shows the `qps` placeholder title.
- Tests: —

### E3-T19 Fit of the video texts

- Status: open
- Plan: 6 ("Fit")
- Value: a translated text never goes outside its box in the video.
- Requirements:
  - If a video text is too wide for its box, the font becomes smaller,
    down to a minimum size (proposal: 80% of the normal size). If the text
    is still too wide, it is cut with "…".
  - The export, the preview, and the thumbnails use the same fit result.
  - The catalog comment of each video text gives the maximum length.
  - A fit test renders each scoreboard layout and the statistics card with
    the texts of a given language. It reports each text that needs a
    smaller font or a cut.
- Acceptance:
  - With `qps`, no text goes outside its box in any layout.
  - With English, no text gets a smaller font or a cut, and the video does
    not change.
- Tests: —

### E3-T20 Units in the catalog

- Status: open
- Plan: 5 ("Units")
- Value: a language can use its own unit symbols and spaces.
- Requirements:
  - The units of the display formats ("s", "GB", "MB", "KB", "fps", "%")
    come from the catalog, with the number as a placeholder.
- Acceptance:
  - In `qps`, the units show in the pseudo-language.
  - In English, the texts do not change.
- Tests: —

### E3-T21 Language setting, detection, and restart (hidden)

- Status: open
- Plan: 4
- Value: the user gets the language of Windows without an action, and can
  change it. The setting stays hidden until E4 adds a real language.
- Requirements:
  - The default value of the setting is "System". With "System", the app
    uses the Windows display language at each start. A regional variant
    uses the main language (`es-AR` uses `es`). If the app does not have
    the language, it uses English.
  - The user preferences keep the choice.
  - More, Settings, has a "Language" row. Each language shows in its own
    language ("English", "Español").
  - After a change, the row shows "The app uses the new language after a
    restart" and a "Restart now" button.
  - "Restart now" does the normal close (save the open project, keep the
    export queue). If an export runs, the app asks the user first.
  - If the user cancels the restart, the app keeps the current language
    and uses the new language at the next start.
  - The new process does not show "already running". It waits some
    seconds for the old process to release the instance lock (B-19).
  - If the app cannot restart itself (for example a start from the
    development build), the row tells the user to close and start the app
    again. The button does not show.
  - The row stays hidden while English is the only language. The `qps`
    property shows it, with `qps` in the list.
- Acceptance:
  - Unit tests for the detection: `es-AR` and `es-MX` give `es` (with a
    test catalog), `fr-FR` gives English, and a stored choice has priority
    over Windows.
  - On Windows, "Restart now" restarts the installed app in the new
    language, and the new process does not show "already running".
  - A cancel during a running export keeps the export.
  - Without the `qps` property, the row does not show.
- Notes: the update flow (`UpdateAndRestart`) has the close sequence and
  the question about a running export. Use the same sequence. Start the
  Velopack launcher, not the JAR.
- Tests: —

### E3-T22 Layout fixes for longer texts

- Status: open
- Plan: 7
- Value: a translation that is 40% longer than English shows completely.
- Requirements:
  - Buttons, labels, and table columns with text grow with the text.
  - The custom-painted parts with text (score panel, timeline, momentum
    chart, statistics table, projects header, sidebar, hint balloons)
    accept longer texts.
  - A word that is wider than its line breaks (German has long nouns).
  - The fixes include all problems that the extraction tasks wrote here.
- Acceptance:
  - The `qps` screenshots show no cut text and no text outside its box.
  - In English, the screenshots of the UI flow tests do not change, or
    each change is accepted in the pull request.
- Tests: —

### E3-T23 User texts in any script

- Status: open
- Plan: 9 ("What B-11 must do now")
- Value: a user can type player names, comments, and project names in any
  script. This is necessary also for English users.
- Requirements:
  - The input of player names, comments, and project names works with an
    input method (IME) for Chinese and Japanese on Windows.
  - Project names in Cyrillic, Greek, and Chinese give valid folder and
    file names.
- Acceptance:
  - A unit test for the safe file names with non-Latin names passes.
  - The manual IME check is in `qa/windows/ui-smoke.md`, and it passes.
- Tests: —

### E3-T24 Documentation and the empty allowlist

- Status: open
- Plan: 14, "Done when"
- Value: a contributor adds each new text to the catalog, and a translator
  can add a language without help.
- Requirements:
  - `CONTRIBUTING.md` tells how to add a text and its comment, and how to
    add a language.
  - `docs/architecture-rules.md` has the rules of plan section 1, "Rules
    for the code".
  - The third-party notices have the library of E3-T1.
  - The plan, "Current state", tells what E3 changed.
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

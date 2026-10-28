# B-11 E4: Spanish

This file holds the tasks of epic E4 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: in-progress
- Plan sections: 4, 6 ("Setting", "Fit"), 10, 12, 13, 14
- Depends on: E3. E2 must be released some weeks before (for the data, not
  for the code).
- Size: about 1 week. Most of the code work is in E3. No native review
  (plan, "Decisions", "Translation", 2026-10-28).
- Value:
  - Spanish-speaking players (Spain and Latin America, tennis and padel)
    can use the app and share videos in Spanish.
  - The app starts in Spanish on a Windows in Spanish. The user can change
    the language in More, Settings. The video uses the app language.
- Prepares: the process for each new language: glossary, draft, fit
  check, smoke test. E7 and E8 repeat it.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E4-T1 | Find the reviewer | — | later |
| E4-T2 | Spanish glossary | — | done |
| E4-T3 | Check of the missing and the stale texts | — | done |
| E4-T4 | Screenshots for translators | E3-T5 | done |
| E4-T5 | Machine draft | E4-T2, E4-T3 | done |
| E4-T6 | Legal texts | E4-T5 | done |
| E4-T7 | Review by a native speaker | E4-T1, E4-T4, E4-T5, E4-T6 | later |
| E4-T8 | Fit tests for Spanish | E4-T5 | done |
| E4-T9 | Queued render job keeps its language | — | done |
| E4-T10 | The app sends the language `es` | — | in-progress |
| E4-T11 | Show the language setting | E4-T5, E4-T8 | done |
| E4-T12 | Windows smoke test in Spanish | E4-T11 | in-progress |
| E4-T13 | Documentation and release | E4-T2–E4-T12, without E4-T7 | open |

### E4-T1 Find the reviewer

- Status: later. Decided on 2026-10-28: Spanish ships without a reviewer
  (plan, "Decisions", "Translation"). Do this task if a reviewer comes
  forward.
- Plan: 13 ("Review"), "Costs"
- Value: a person who knows the language and the sport makes sure that the
  texts are correct and natural.
- Requirements:
  - The reviewer is a native speaker of Spanish who plays tennis or padel.
    Good candidates are users from the feedback form or the local chats of
    the marketing plan.
  - The agreement covers the review of the draft and of the screenshots,
    and the updates of later releases. It gives a fee if necessary.
  - The Spanish must be clear for users in Spain and in Latin America. If
    possible, find one reviewer from each region.
- Acceptance:
  - The plan, "Decisions", names the reviewers (with their consent) or the
    service.
- Tests: — (decision)

### E4-T2 Spanish glossary

- Status: done
- Plan: 13 ("A glossary for each language"), 6 ("The score words")
- Value: each term has one translation in all texts. The draft and the
  review use the same terms.
- Requirements:
  - `docs/localization/glossary.md` (or a CSV) has one table for each
    language. Spanish is the first.
  - The sport terms: point, game, set, tiebreak, deuce, advantage, golden
    point ("punto de oro"), star point, serve, return, break point, and the
    padel terms of B-45 P-6.
  - The app terms: favorite, project, export, scoreboard, Transform, and the
    names of the tabs.
  - The score words: "AD" stays (plan, "Decisions", "Score word AD").
  - Spain and Latin America are two app languages (plan, "Decisions",
    "Spanish variants"). If they use different terms, the glossary gives the
    term of each variant. The Latin American catalog files
    (`*_es_419.properties`) have only the different texts.
  - A "do not translate" list: the brand "BananaShot", the codes ("4K",
    "1080p", "H.264", "HEVC", "NVENC", "fps"), the key letters (Q, W, E),
    and the file extensions. A test checks that each such term in an
    English text is also in its translation.
- Acceptance:
  - The glossary has no open term questions. Without a reviewer
    (2026-10-28), the glossary is final for the release. A later reviewer
    (E4-T7) can change it.
- Tests: `MessageCatalogTest` (the "do not translate" list).

### E4-T3 Check of the missing and the stale texts

- Status: done
- Plan: 13 ("Changes to English texts")
- Value: after a change of an English text, the author knows which
  translations to update. A release cannot ship a language with missing
  texts.
- Requirements:
  - Each translated text records the English text that it translates (for
    example a hash in a comment line above the key). A translator can keep
    this record when they edit the file.
  - A tool lists the missing and the stale keys for each language. A key is
    stale when the English text changed after the translation.
  - CI shows the list. It does not stop a pull request.
  - The release stops if a shipped language has missing keys.
  - A stale translation stays in use until a new draft updates it.
  - Bad records:
    - A translated key without a record is stale, not current.
    - A record with a wrong form (for example a cut hash) is stale. The
      tool tells the key and does not stop.
    - A change of only the spaces or the punctuation of the English text
      also makes the key stale. The author decides if the translation
      changes.
  - A renamed English key: the old key in the Spanish file is a test
    failure (E3-T2). The tool tells the old key and the new key, so the
    author can move the translation. The new key is missing until then.
  - The check includes the help file of each language.
- Acceptance:
  - A test changes an English text and sees the key as stale.
  - A test removes a Spanish key and sees the key as missing.
  - A test removes the record of a Spanish key and sees the key as stale.
  - A test gives an empty Spanish value and sees the key as missing.
  - The release checklist has the step.
- Notes: the record is the line `#@en=<hash>` above each key: the first 8
  hex digits of the SHA-256 of the English text. `TranslationCheckMain`
  (test sources) lists the missing, the stale, and the renamed keys, and
  writes the records (`--stamp`). CI shows the list in the job summary. The
  "Windows release" workflow runs it with `--fail-on-missing`. The first
  records (2026-10-28) come from the git history: no translation was older
  than its English text. The only exceptions were `points.count.favorites`
  and `points.count.comments` in Spanish. English got a `one` form there
  later, and the Spanish texts already had it, so they are current.
- Tests: `TranslationCheckTest`.

### E4-T4 Screenshots for translators

- Status: done
- Plan: 12 ("Screenshots for translators")
- Value: the author (and a later reviewer) sees each text in its place,
  and can find texts that are wrong for their place or too long.
- Requirements:
  - The UI flow tests can run with a given language and save a screenshot
    of each tab and each dialog (as E3-T5 does for `qps`).
  - One command or one CI job makes the screenshots for one language.
- Acceptance:
  - One command gives a folder with the screenshots of the Spanish draft.
- Notes: the command is `qa/Make-TranslatorScreenshots.ps1 -Language es`.
  It runs the UI flow tests with `bananashot.language` and
  `bananashot.uiFlow.screenshots=true`. The files go to
  `target/translator-screenshots/<language>/<test class>/<test method>/`.
  `-Tests AllWindowsUiFlowIT` runs only the test that opens each tab, each
  Help page, each More section, and the feedback form, and saves a named
  screenshot of each (21 files, about 30 s). With `-Tests`, the screenshots
  of the other classes stay. In the other tests,
  `UiFlowScreenshots` saves each new window title before each action of the
  driver, so the dialogs of these tests are also in the folder. A failed
  test does not stop the run. The script tells the failed classes. The tests
  use the mouse, so do not use the PC during the run. A screenshot shows only
  the visible part of a long Help page; the full text is in the help file.
  This task does not need the CI part of E3-T5.
- Tests: `AllWindowsUiFlowIT` (also without screenshots: each tab and each
  window opens in the app language).

### E4-T5 Machine draft

- Status: done
- Plan: 13 ("First draft")
- Value: the machine draft is the Spanish text of the release. A later
  reviewer corrects a full draft and does not translate from the start.
- Requirements:
  - A machine translation makes the Spanish catalog and the Spanish help.
    It gets the glossary and the comments of the keys.
  - The records of E4-T3 are in the files.
  - The plural texts have the plural forms of Spanish.
  - Machine translation often breaks a message. The draft is not accepted
    until the catalog tests pass. The tests must find these errors:
    - a translated placeholder name (`{nombreJugador}` in place of
      `{playerName}`);
    - translated ICU keywords (`uno` in place of `one`, `plural` changed);
    - a lost or added apostrophe that changes a quoted part;
    - added markup, or a lost `<b>` placeholder;
    - a translated term of the "do not translate" list (E4-T2);
    - a changed key name.
  - The draft keeps the key comments and the records of E4-T3.
- Acceptance:
  - The catalog tests of E3-T2 pass with the Spanish files.
  - The check of E4-T3 shows no missing key.
  - A test file with each machine translation error above makes the
    catalog tests fail.
- Notes: the test files are `src/test/resources/catalog-check/`. The
  Spanish file has one error in each key. The checks find them as follows:
  - a translated placeholder and an added apostrophe before a placeholder:
    the placeholder check;
  - `uno` in place of `one`: the plural check;
  - a changed `plural` or `other`, and a lost apostrophe before a brace:
    the parse check;
  - added or lost markup: the markup check;
  - a translated term: the "do not translate" check;
  - a changed key name: the key check (a missing key and an extra key).
  No catalog text uses `select` now. If a text uses it later, add a check
  for its case names.
- Tests: `MessageCatalogTest` ("the catalog checks find each error of a
  machine translation").

### E4-T6 Legal texts

- Status: done
- Plan: "Decisions" ("Legal texts", "Notice version")
- Value: a Spanish user understands what the app collects, and the author
  knows which text is binding.
- Requirements:
  - The author gets legal advice and decides if the English privacy notice
    is the binding version. Write the decision in the plan.
  - The consent dialog and the Privacy page are translated. If the English
    text is binding, the translated texts say so, and link to the English
    notice.
  - A translation does not increase the notice version.
  - The site stays in English (plan, "Scope"). A link from the Spanish app
    to the privacy page tells that the page is in English.
  - The translated consent dialog has the same choices and the same
    default as the English dialog. A translation cannot change the
    meaning of a choice.
- Acceptance:
  - The plan has the decision. E4 is not released without it.
  - The notice version does not change.
  - The author confirms that the Spanish choices have the same meaning as
    the English choices (for example with a back-translation into
    English).
- Notes:
  - Decided on 2026-10-28: the English privacy notice on the site is the
    binding version, and it is not translated (plan, "Decisions", "Legal
    texts"). The notice version does not change.
  - The key `privacy.notice.english` tells that the notice is in English
    and that the English text is binding. The Privacy page shows it above
    the "Privacy notice" button, and the consent dialog shows it under its
    texts. English does not show it. Each language has the text, so E7 and
    E8 need no work for it.
  - Back-translation of the Spanish consent dialog (2026-10-28):
    - "Solo esenciales": "Essential only".
    - "Enviar ampliadas": "Send extended".
    - "Por defecto, {app} envía estadísticas de uso esenciales y anónimas
      …": "By default, {app} sends essential and anonymous usage statistics
      …", with the same list of data.
    - "También puedes enviar estadísticas ampliadas …": "You can also send
      extended statistics …", with the same list of data.
    - "Nunca recogemos vídeo, nombres de proyecto, rutas, marcadores ni
      datos personales": "We never collect video, project names, paths,
      scores, or personal details".
    - The default and the close of the window (essential) are in the code,
      so they do not change with the language.
  - The author confirmed the back-translation on 2026-10-28.
- Tests: `PrivacyPageTest` ("a translated app tells that the privacy notice
  is in English and binding").

### E4-T7 Review by a native speaker

- Status: later. Decided on 2026-10-28: not necessary for the release
  (plan, "Decisions", "Translation"). Do this task if a reviewer comes
  forward. The corrections go into the next release.
- Plan: 13 ("Review")
- Value: the Spanish texts are correct, natural, and use the terms of the
  players.
- Requirements:
  - The reviewer works from the screenshots of E4-T4, not only from the
    files.
  - The reviewer checks the glossary terms, the score words, the video
    texts, the help, and the legal texts.
  - The changes go into the files. New screenshots get a last check.
- Acceptance:
  - The reviewer accepts all texts. The plan has the date.
- Tests: — (manual)

### E4-T8 Fit tests for Spanish

- Status: done
- Plan: 6 ("Fit")
- Value: a Spanish video and the Spanish UI show all texts completely.
- Requirements:
  - The fit test of E3-T19 runs with Spanish.
  - If a video text is cut, the author gives a shorter text first. Change
    the layout only if no shorter text is possible.
  - The Spanish UI screenshots show no cut text.
- Acceptance:
  - The fit test passes with Spanish. No text is cut with "…" unless the
    author accepts it.
- Notes:
  - The video fit test passes for all languages, not only Spanish. No
    Spanish video text needed a shorter text.
  - The Spanish UI screenshots of `qa/Make-TranslatorScreenshots.ps1`
    (1200 × 800) show no cut text. The cuts were layout problems, so the fix
    is in the layout, and the Spanish texts did not change:
    - The Thumbnail tab: the controls and the style cards use the full
      width. A low window makes only the preview smaller.
    - The Thumbnail tab: the "Color set" header puts "Changed" and "Use the
      style colors" in a second row when one row is too narrow. "Open
      folder" shows only its icon when the save text needs the space.
    - The Points tab: a narrow point bar shows a compact Point start (the
      time of a pending point is under the title). The outcome buttons share
      the width by their names, and "Winner:" hides when the names do not
      fit. Comment and Replay show only their icons when the space is too
      small. The two settings buttons use two rows when one text does not
      fit.
    - The projects table hides the size column and then the duration
      column, so that the name and the video path keep 140 px each.
  - The program argument `--window-size=1200x800` (or
    `-Dbananashot.windowSize=1200x800`) opens the main window at one size,
    for a check of the layout by hand (see the README).
- Tests: `VideoTextFitTest` (all languages, and negative tests for a
  cut, a cover, a text outside its box, and a smaller font),
  `ProjectRowTest` (the hidden columns), `WindowSizeTest`.

### E4-T9 Queued render job keeps its language

- Status: done
- Plan: 6 ("Setting")
- Value: a video in the queue renders in the language that the user saw
  when they queued it. A language change restarts the app, and the queue
  survives the restart, so this case is real.
- Requirements:
  - Each queued job keeps its video language.
  - A language change after the queue does not change the job.
  - A job without a language (from an older build) uses English, because
    the older build had only English.
  - An older build can read a queue with the new field.
  - A job with a language that this build does not have (for example from
    a newer build, or a language that is now hidden) or with a broken
    value renders in English. The job stays in the queue. It is not lost
    (see the plan, "Decisions", "Unknown language value").
  - A job with a language whose texts are incomplete uses the English
    fallback for the missing texts, and renders.
- Acceptance:
  - A test queues a job in Spanish, changes the app language to English,
    reads the queue again, and renders the job with the Spanish texts.
  - A test reads a queue file of the current release.
  - A test reads a queue with the language `pt` and a queue with the
    language `123`. The jobs stay, and they render in English.
- Notes: done with E5-T3. The job keeps its video texts and the language
  code (`RenderJob.videoLanguage`).
- Tests: `VideoLanguageExportTest` (the queued job, the unknown and broken
  languages, the queue file of the current release).

### E4-T10 The app sends the language `es`

- Status: in-progress
- Plan: 10, "Decisions" ("Language values")
- Value: the author sees how many users use the app in Spanish.
- Requirements:
  - With the app in Spanish, the analytics attribute `language` and the
    feedback field `app_language` are `es`. The contracts and the workers
    accept `es` since E2. No deployment is necessary before the release.
  - A language that the system property selects sends its own code.
    `qps` sends `en`.
  - With "System" and a Windows language that the app does not have (for
    example Italian), `language` is `en`, and `os_language` is `it`.
  - With Spanish and some stale or fallback texts, `language` stays `es`.
- Acceptance:
  - A test: with the app in Spanish, the summary has `"language": "es"`,
    and the feedback report has `"app_language": "es"`.
  - A test: with Windows in Italian and "System", the summary has
    `"language": "en"` and `"os_language": "it"`.
  - The cockpit shows the first `es` data after the release.
- Notes:
  - The example was French. French is now an app language, so the
    example is Italian.
  - `appLanguage()` and `osLanguage()` in `AppServices.kt` give the two
    values. The analytics summary and the feedback report use the same
    functions.
  - Open: the first `es` data in the cockpit after the release.
- Tests: `LanguageValuesTest` (Spanish of Spain and of Latin America,
  Spanish selected on a Windows in English, Italian with "System", the
  system property with `ru` and `qps`, Spanish with missing texts).

### E4-T11 Show the language setting

- Status: done
- Plan: 4, 13 ("New languages"), "Decisions" ("Incomplete language",
  "Translation", "Existing users")
- Value: Spanish users get the app in Spanish without an action, and all
  users can select the language.
- Requirements:
  - The "Language" row of E3-T21 shows to all users.
  - The list has only the complete languages whose checks pass. A native
    review is not necessary. The catalog tests fail for a missing text, so
    a language that is not complete cannot be in a build (plan,
    "Decisions", "Incomplete language").
  - An existing user with Windows in Spanish gets Spanish after the update,
    because the setting is "System". At the first start in Spanish, the app
    shows a notice one time: the app is now in Spanish, and the user can
    change the language in More, Settings (see "Decisions", "Existing
    users").
  - The Swing and FlatLaf texts show in Spanish.
  - The notice for existing users:
    - It shows only if the app language changed because of "System". It
      does not show to a new user, to a user who selected the language,
      or to a user whose Windows language the app does not have.
    - It shows one time. It does not show again after a restart, also if
      the user closed it with the close button or Escape. If the app
      stops before the notice shows, it shows at the next start.
    - It has a button that sets English and restarts the app. The button
      text is in English ("Use English"), so a user who does not read
      Spanish can use it.
  - The user can find the language setting without the ability to read
    the current language (see the plan, "Decisions", "Path back to
    English"): the "Language" row has a globe icon and the English word
    "Language" next to the translated label. The list shows each language
    in its own language.
- Acceptance:
  - On a Windows in Spanish, the first start shows Spanish.
  - On a Windows in English, the app shows English, and the user can
    select Spanish. "Restart now" applies it.
  - An existing user with Windows in Spanish sees the notice one time.
  - A new user with Windows in Spanish does not see the notice.
  - A user who closes the notice with Escape does not see it again.
  - "Use English" in the notice restarts the app in English, and the
    stored value is English (not "System").
  - A language that is not complete fails the catalog tests.
- Notes:
  - Decided on 2026-10-28: the release ships all complete languages whose
    checks pass, not only Spanish (plan, "Decisions", "Languages of the
    first release"). The Settings list shows all of them now.
  - `SystemLanguageNotice` has the logic of the notice. The app records the
    notice as shown when it opens, not before, so a stop before the notice
    opens shows it at the next start.
  - In the notice, OK, Escape, and the close button give the same result.
  - `SwingMainApp.applySwingLocale` sets the locale of the Swing and FlatLaf
    texts. No unit test checks it. The smoke test (E4-T12) checks it.
  - Decided on 2026-10-28: the app has no hidden languages and no system
    property that shows them. The failing catalog tests are enough.
- Tests: `SystemLanguageNoticeTest` (one time, a new user, Escape, a stop
  before the notice opens, "Use English" with and without a restart, no
  notice for a selected language or an unknown Windows language),
  `LanguageCardTest` (the globe icon and the English word "Language", the
  native names, "Restart now"), `LanguageSettingsTest` (Spanish Windows
  gives Spanish).

### E4-T12 Windows smoke test in Spanish

- Status: in-progress
- Plan: 11 ("Installer and Windows"), 12
- Value: the full flow works for a real Spanish user on a real Windows.
- Requirements:
  - `qa/windows/ui-smoke.md` has a pass on Windows in Spanish:
    - the installer texts, and the app name and shortcut names stay
      "BananaShot";
    - the formats: "10 oct 2026" in the exports table and "2,35 GB" with
      the regional format "Spanish (Spain)";
    - one export with each scoreboard layout and the statistics card;
    - a change of the language to English and back with "Restart now".
  - The pass also covers the mixed and bad cases:
    - Windows in Spanish with the regional format "English (United
      States)": the app is in Spanish, and the dates show only digits;
    - Windows in English with the regional format "Spanish (Spain)": the
      app is in English, and the numbers have a decimal comma;
    - a time code typed with the "," key of the numeric keypad;
    - a Windows user name with "ñ" or "é" in the install path, and
      "Restart now";
    - the notice for existing users, and "Use English" in it.
- Acceptance:
  - The smoke test passes in Spanish.
- Tests: — (manual)

### E4-T13 Documentation and release

- Status: open
- Plan: 14
- Value: the next language uses a written process, not the memory of the
  author.
- Requirements:
  - `CONTRIBUTING.md` gives the process for a new language: glossary,
    draft, fit check, smoke test. A native review is optional. The language code must be in the
    language list of E2-T1.
  - The release checklist has the translation check (E4-T3).
  - `README.md` lists the languages.
  - `docs/padel.md` (P-6) points to this epic.
  - The macOS plan (B-13) tells that the bundle must declare the languages
    of the app (`CFBundleLocalizations`). Without it, the native dialogs on
    macOS stay in English.
- Acceptance:
  - The process for a new language is written, so E7 can use it.
- Tests: —

## Done when

- All tasks are `done` or `later`.
- The app has a complete Spanish translation. A native review is not
  necessary.
- At the first start on Windows in Spanish, the app shows Spanish.
- A Spanish export shows the Spanish scoreboard and statistics texts, and
  all texts fit.
- The Windows smoke test passes in Spanish.

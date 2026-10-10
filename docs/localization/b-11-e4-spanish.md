# B-11 E4: Spanish

This file holds the tasks of epic E4 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 4, 6 ("Setting", "Fit"), 10, 12, 13, 14
- Depends on: E3. E2 must be released some weeks before (for the data, not
  for the code).
- Size: 1 to 2 weeks, mostly review time. Most of the code work is in E3.
- Value:
  - Spanish-speaking players (Spain and Latin America, tennis and padel)
    can use the app and share videos in Spanish.
  - The app starts in Spanish on a Windows in Spanish. The user can change
    the language in More, Settings. The video uses the app language.
- Prepares: the process for each new language: glossary, draft, review,
  fit check, smoke test. E7 and E8 repeat it.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E4-T1 | Find the reviewer | — | open |
| E4-T2 | Spanish glossary | — | open |
| E4-T3 | Check of the missing and the stale texts | — | open |
| E4-T4 | Screenshots for translators | E3-T5 | open |
| E4-T5 | Machine draft | E4-T2, E4-T3 | open |
| E4-T6 | Legal texts | E4-T5 | open |
| E4-T7 | Review by a native speaker | E4-T1, E4-T4, E4-T5, E4-T6 | open |
| E4-T8 | Fit tests for Spanish | E4-T5 | open |
| E4-T9 | Queued render job keeps its language | — | open |
| E4-T10 | The app sends the language `es` | — | open |
| E4-T11 | Show the language setting | E4-T7 | open |
| E4-T12 | Windows smoke test in Spanish | E4-T11 | open |
| E4-T13 | Documentation and release | E4-T1–E4-T12 | open |

### E4-T1 Find the reviewer

- Status: open
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

- Status: open
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
  - The score words: the reviewer decides if "AD" stays or changes (for
    example "V" for "ventaja").
  - If Spain and Latin America use different terms, the glossary selects a
    term that both understand, and gives the reason.
  - A "do not translate" list: the brand "BananaShot", the codes ("4K",
    "1080p", "H.264", "HEVC", "NVENC", "fps"), the key letters (Q, W, E),
    and the file extensions. A test checks that each such term in an
    English text is also in its translation.
- Acceptance:
  - The reviewer of E4-T7 accepts the glossary before the review of the
    texts.
- Tests: —

### E4-T3 Check of the missing and the stale texts

- Status: open
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
  - A stale translation stays in use until the reviewer updates it.
  - Bad records:
    - A translated key without a record is stale, not current.
    - A record with a wrong form (for example a cut hash) is stale. The
      tool tells the key and does not stop.
    - A change of only the spaces or the punctuation of the English text
      also makes the key stale. The reviewer decides if the translation
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
- Tests: —

### E4-T4 Screenshots for translators

- Status: open
- Plan: 12 ("Screenshots for translators")
- Value: the reviewer sees each text in its place, and can find texts that
  are wrong for their place or too long.
- Requirements:
  - The UI flow tests can run with a given language and save a screenshot
    of each tab and each dialog (as E3-T5 does for `qps`).
  - One command or one CI job makes the screenshots for one language.
- Acceptance:
  - The reviewer gets a folder with the screenshots of the Spanish draft.
- Tests: —

### E4-T5 Machine draft

- Status: open
- Plan: 13 ("First draft")
- Value: the reviewer corrects a full draft and does not translate from
  the start.
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
- Tests: —

### E4-T6 Legal texts

- Status: open
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
  - The reviewer of E4-T7 confirms that the Spanish choices have the same
    meaning as the English choices.
- Tests: —

### E4-T7 Review by a native speaker

- Status: open
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

- Status: open
- Plan: 6 ("Fit")
- Value: a Spanish video and the Spanish UI show all texts completely.
- Requirements:
  - The fit test of E3-T19 runs with Spanish.
  - If a video text is cut, the reviewer gives a shorter text first. Change
    the layout only if no shorter text is possible.
  - The Spanish UI screenshots show no cut text.
- Acceptance:
  - The fit test passes with Spanish. No text is cut with "…" unless the
    reviewer accepts it.
- Tests: —

### E4-T9 Queued render job keeps its language

- Status: open
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
- Tests: —

### E4-T10 The app sends the language `es`

- Status: open
- Plan: 10, "Decisions" ("Language values")
- Value: the author sees how many users use the app in Spanish.
- Requirements:
  - With the app in Spanish, the analytics attribute `language` and the
    feedback field `app_language` are `es`. The contracts and the workers
    accept `es` since E2. No deployment is necessary before the release.
  - A language that only reviewers see sends its own code. `qps` sends
    `en`.
  - With "System" and a Windows language that the app does not have (for
    example French), `language` is `en`, and `os_language` is `fr`.
  - With Spanish and some stale or fallback texts, `language` stays `es`.
- Acceptance:
  - A test: with the app in Spanish, the summary has `"language": "es"`,
    and the feedback report has `"app_language": "es"`.
  - A test: with Windows in French and "System", the summary has
    `"language": "en"` and `"os_language": "fr"`.
  - The cockpit shows the first `es` data after the release.
- Tests: —

### E4-T11 Show the language setting

- Status: open
- Plan: 4, 13 ("New languages"), "Decisions" ("Incomplete language",
  "Existing users")
- Value: Spanish users get the app in Spanish without an action, and all
  users can select the language.
- Requirements:
  - The "Language" row of E3-T21 shows to all users.
  - The list has only the complete and reviewed languages. A system
    property shows the other languages for the reviewers.
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
  - A language that is not reviewed does not show without the property.
- Tests: —

### E4-T12 Windows smoke test in Spanish

- Status: open
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
    draft, review, fit check, smoke test. The language code must be in the
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

- All tasks are `done`.
- The app has a complete Spanish translation, reviewed by a native speaker.
- At the first start on Windows in Spanish, the app shows Spanish.
- A Spanish export shows the Spanish scoreboard and statistics texts, and
  all texts fit.
- The Windows smoke test passes in Spanish.

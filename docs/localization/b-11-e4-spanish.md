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
  fit check, smoke test, analytics value. E7 and E8 repeat it.

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
| E4-T10 | Analytics value `es` | — | open |
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
- Acceptance:
  - A test changes an English text and sees the key as stale.
  - A test removes a Spanish key and sees the key as missing.
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
- Acceptance:
  - The catalog tests of E3-T2 pass with the Spanish files.
  - The check of E4-T3 shows no missing key.
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
- Acceptance:
  - The plan has the decision. E4 is not released without it.
  - The notice version does not change.
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
- Acceptance:
  - A test queues a job in Spanish, changes the app language to English,
    reads the queue again, and renders the job with the Spanish texts.
  - A test reads a queue file of the current release.
- Tests: —

### E4-T10 Analytics value `es`

- Status: open
- Plan: 10
- Value: the author sees how many users use the app in Spanish. The
  summaries of Spanish users are not rejected.
- Requirements:
  - The analytics contract, the worker, the app, and the cockpit know the
    value `es` of the attribute `language`.
  - The worker that accepts `es` is in production before the app release.
    The current worker rejects an unknown language.
  - `qps` and the languages that only reviewers see send `en`.
- Acceptance:
  - The worker tests accept `es`.
  - The production worker accepts the smoke summary with `es` before the
    release.
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
- Acceptance:
  - On a Windows in Spanish, the first start shows Spanish.
  - On a Windows in English, the app shows English, and the user can
    select Spanish. "Restart now" applies it.
  - An existing user with Windows in Spanish sees the notice one time.
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
    draft, review, fit check, smoke test, analytics value, deployment
    order.
  - The release checklist has the translation check (E4-T3) and the worker
    deployment order (E4-T10).
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

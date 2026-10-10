# B-11 E4: Spanish

This file holds the tasks of epic E4 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 4, 6 ("Setting", "Fit"), 10, 12, 13, 14
- Depends on: E3. E2 must be released some weeks before (for the data, not
  for the code).
- Feature: the app and the exported video in Spanish. The app starts in
  Spanish on a Windows in Spanish. The user can select the language in
  More, Settings. The video uses the app language.
- Prepares: the process for each new language: glossary, draft, review,
  smoke test, analytics value. E7 and E8 repeat it.
- Size: 1 to 2 weeks, mostly review time. Most of the code work is in E3.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E4-T1 | Find the reviewer | — | open |
| E4-T2 | Spanish glossary | — | open |
| E4-T3 | Check of the missing and the stale texts | — | open |
| E4-T4 | Screenshots for translators | — | open |
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
- Work:
  - Find a native speaker of Spanish who plays tennis or padel. Good
    candidates are users from the feedback form or the local chats of the
    marketing plan (`docs/marketing/strategy.md`).
  - Agree on the work: the review of the draft, the screenshots, and the
    updates of later releases. Agree on a fee if necessary.
  - The Spanish must be clear for users in Spain and in Latin America. If
    possible, find one reviewer from each region.
- Acceptance:
  - The plan, "Decisions", names the reviewers (with their consent) or the
    service.
- Tests: — (decision)

### E4-T2 Spanish glossary

- Status: open
- Plan: 13 ("A glossary for each language"), 6 ("The score words")
- Work:
  - Make `docs/localization/glossary.md` (or a CSV). One table for each
    language. The first language is Spanish.
  - The sport terms: point, game, set, tiebreak, deuce, advantage, golden
    point ("punto de oro"), star point, serve, return, break point, and the
    padel terms of B-45 P-6.
  - The app terms: favorite, project, export, scoreboard, Transform, and
    the names of the tabs.
  - The score words: the reviewer decides if "AD" stays or changes (for
    example "V" for "ventaja").
  - Each term has one translation, and all texts use it.
- Acceptance:
  - The reviewer of E4-T7 accepts the glossary before the review of the
    texts.
- Tests: —

### E4-T3 Check of the missing and the stale texts

- Status: open
- Plan: 13 ("Changes to English texts")
- Work:
  - Each translated file keeps a hash of the English text that it
    translates, for each key. Select a format that a translator can keep
    (for example a comment line above each key).
  - A tool lists the missing and the stale keys for each language. A stale
    key has a hash that is not the hash of the current English text.
  - CI runs the tool and shows the list. It does not stop a pull request.
  - The release workflow stops if a shipped language has missing keys.
  - A stale translation stays in use until the reviewer updates it.
- Acceptance:
  - A test changes an English text and sees the key as stale.
  - A test removes a Spanish key and sees the key as missing.
  - `docs/release-checklist.md` has the step.
- Tests: —

### E4-T4 Screenshots for translators

- Status: open
- Plan: 12 ("Screenshots for translators")
- Work:
  - The UI flow tests can run with a given language and save a screenshot
    of each tab and each dialog (as E3-T5 does for `qps`).
  - A script or a CI job makes the screenshots for one language.
- Acceptance:
  - The reviewer gets a folder with the screenshots of the Spanish draft.
- Tests: —

### E4-T5 Machine draft

- Status: open
- Plan: 13 ("First draft")
- Work:
  - Make `messages_es.properties` and `help_es.properties` with machine
    translation. Give the glossary and the comments of the keys to the
    translation.
  - Add the hashes of E4-T3.
  - Spanish has 2 plural forms. Check the plural messages.
- Acceptance:
  - The catalog tests of E3-T2 pass with the Spanish files.
  - The check of E4-T3 shows no missing key.
- Tests: —

### E4-T6 Legal texts

- Status: open
- Plan: "Decisions" ("Legal texts", "Notice version")
- Work:
  - The author gets legal advice and decides if the English text of the
    privacy notice is the binding version. Write the decision in the plan.
  - Translate the consent dialog and the Privacy page. If the English text
    is binding, the translated texts say so.
  - A translation does not increase `notice_version`.
- Acceptance:
  - The plan has the decision.
  - `NOTICE_VERSION` does not change.
- Tests: —

### E4-T7 Review by a native speaker

- Status: open
- Plan: 13 ("Review")
- Work:
  - The reviewer works from the screenshots of E4-T4, not only from the
    file.
  - The reviewer checks the glossary terms, the score words, the video
    texts, and the help.
  - Apply the changes. Make new screenshots, and get a last check.
- Acceptance:
  - The reviewer accepts all texts. Write the date in the plan.
- Tests: — (manual)

### E4-T8 Fit tests for Spanish

- Status: open
- Plan: 6 ("Fit")
- Work:
  - Run the fit test of E3-T19 with the Spanish texts.
  - Check the UI screenshots for cut texts.
  - If a video text is cut, ask the reviewer for a shorter text first.
    Change the layout only if no shorter text is possible.
- Acceptance:
  - The fit test passes with Spanish. No text is cut with "…" unless the
    reviewer accepts it.
- Tests: —

### E4-T9 Queued render job keeps its language

- Status: open
- Plan: 6 ("Setting")
- Work:
  - `export/SavedRenderQueue.kt` keeps the video language of each job
    (the app language when the user queued it).
  - A language change after the queue does not change the job. A language
    change restarts the app, and the queue survives the restart.
  - `SavedRenderQueue` ignores unknown fields, so older builds can read
    the queue. A job without the field uses English.
- Acceptance:
  - A test queues a job in Spanish, changes the app language to English,
    reads the queue again, and renders the job with the Spanish texts.
  - A test reads a queue file of the current release.
- Tests: —

### E4-T10 Analytics value `es`

- Status: open
- Plan: 10
- Work:
  - Add `es` to `AnalyticsEvent.Language`,
    `analytics-contract/v1/attributes.json`, and
    `analytics-worker/src/attributes.ts`. Add examples to the contract
    fixtures. The cockpit shows the value.
  - Deploy the worker before the app release. The current worker rejects
    an unknown language.
  - `qps` and the languages that only reviewers see send `en`.
- Acceptance:
  - The worker tests accept `es`.
  - The production worker accepts the smoke summary with `es` before the
    release.
- Tests: —

### E4-T11 Show the language setting

- Status: open
- Plan: 4, 13 ("New languages"), "Decisions" ("Incomplete language")
- Work:
  - Show the "Language" row of E3-T21 to all users.
  - The list shows only complete and reviewed languages. A system property
    shows the other languages for the reviewers.
  - Check the Swing and FlatLaf texts in Spanish (for example the context
    menu of a text field).
- Acceptance:
  - On a Windows in Spanish, the first start shows Spanish.
  - The user can change the language, and "Restart now" applies it.
  - A language that is not reviewed does not show without the property.
- Tests: —

### E4-T12 Windows smoke test in Spanish

- Status: open
- Plan: 11 ("Installer and Windows"), 12
- Work:
  - Add a pass on Windows in Spanish to `qa/windows/ui-smoke.md`.
  - Check the Velopack installer texts on a Windows in Spanish. The app
    name and the shortcut names stay "BananaShot".
  - Check the formats: "10 oct 2026" in the exports table and "2,35 GB".
  - Export one video with each scoreboard layout and the statistics card.
- Acceptance:
  - The smoke test passes in Spanish.
- Tests: — (manual)

### E4-T13 Documentation and release

- Status: open
- Plan: 14
- Work:
  - `README.md`: the list of the languages.
  - `docs/padel.md`: P-6 points to this epic.
  - `docs/release-checklist.md`: the translation check (E4-T3) and the
    worker deploy order (E4-T10).
  - `docs/macos/b-13-macos.md`: the bundle declares the languages of the
    app in `Info.plist` (`CFBundleLocalizations`).
  - `CONTRIBUTING.md`: the process for a new language (glossary, draft,
    review, smoke test, analytics value).
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

# B-11 E7: More Languages with Latin and Cyrillic Letters

This file holds the tasks of epic E7 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 2 (plural forms), 6 ("Fonts", "Fit"), 8, 12, 13
- Depends on: E4 (the process for a new language), E2 (the data)
- Size: about 1 week for each language, mostly review time.
- Value: a large part of the users can use the app in their language. The
  data of E2 selects the languages. Probable candidates: Portuguese,
  French, Italian, German, Russian.

B-11 is done after E6. This epic continues with each new language.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E7-T1 | Select the languages | E2 | open |
| E7-T2 | Plural forms with the CLDR rules | — | open |
| E7-T3 | Shortcuts with Cyrillic and Greek keyboard layouts | — | open |
| E7-T4 | Font check for each language | — | open |
| E7-L-`<code>` | One task for each language (see the template) | E7-T1–E7-T4 | — |

When E7-T1 selects a language, add a row `E7-L-<code>` to this table (for
example `E7-L-pt`), and copy the template below.

### E7-T1 Select the languages

- Status: open
- Plan: "Decisions" ("Next languages"), 10
- Value: the translation work goes to the languages with the most users.
- Requirements:
  - Use at least 4 weeks of `os_language` data from the cockpit (E2).
  - Compare the data with the marketing plan.
  - Select the languages and their order. A reviewer must be available for
    each one.
- Acceptance:
  - The plan, "Decisions", has the list, the order, and the data.
- Tests: — (decision)

### E7-T2 Plural forms with the CLDR rules

- Status: open
- Plan: 2 ("Message syntax"), 12
- Value: texts with a number are correct in languages with more than two
  plural forms ("1 punto", but Russian "1 очко", "2 очка", "5 очков").
- Requirements:
  - Each plural message of each language has all plural categories of the
    language (CLDR rules). Example: Russian needs `one`, `few`, `many`, and
    `other`.
  - If E3-T1 selected own plural rules, the rules of each new language come
    from the CLDR data.
- Acceptance:
  - A catalog test fails for a Russian message with only `one` and
    `other`.
  - A test formats a Russian plural message with 1, 2, 5, and 21, and gets
    the correct form for each number.
- Tests: —

### E7-T3 Shortcuts with Cyrillic and Greek keyboard layouts

- Status: open
- Plan: 8
- Value: a user with a Russian, Ukrainian, or Greek keyboard layout can use
  all shortcuts without a change of the layout.
- Requirements:
  - Each shortcut works with the Russian and the Greek keyboard layouts on
    Windows. The shortcut binds the physical key, not the typed character.
- Acceptance:
  - A source scan test fails for a shortcut that binds a typed character.
  - The manual check with the Russian and the Greek layouts is in
    `qa/windows/ui-smoke.md`, and it passes.
- Tests: —

### E7-T4 Font check for each language

- Status: open
- Plan: 6 ("Fonts")
- Value: no text of a shipped language shows empty boxes or a different
  font in the UI or in the video.
- Requirements:
  - Each scoreboard font can show all letters of the video texts of each
    shipped language.
  - The UI font can show all letters of the UI texts of each shipped
    language.
  - If B-13 bundled open fonts, the check uses these fonts.
- Acceptance:
  - A font test passes for each shipped language. A font that cannot show
    a language is not in the list of styles for this language.
  - On Windows, the "Ink Free" style shows Cyrillic and Greek letters, or
    the style is not available for these languages.
- Tests: —

### Template: E7-L-`<code>` `<Language>`

- Status: open
- Plan: 13, and the process of E4 (`CONTRIBUTING.md`)
- Value: the users of `<Language>` can use the app and share videos in
  their language.
- Requirements:
  - The glossary of the language is in `docs/localization/glossary.md`.
  - A native speaker who plays tennis or padel reviews the texts from the
    screenshots (E4-T4).
  - The catalog and the help of the language come from a machine draft
    with the glossary and the key comments, and then the review.
  - The analytics contract, the worker, the app, and the cockpit know the
    value `<code>`. The worker is in production before the app release.
  - The Swing and FlatLaf texts show in the language.
  - `README.md` and the macOS bundle (B-13) list the language.
  - If the author answers feedback in the language, the feedback form
    tells it (E2-T6).
  - German has the longest texts. For German, check the screenshots with
    care.
- Acceptance:
  - The check of E4-T3 shows no missing key for the language.
  - The reviewer accepts all texts.
  - The fit tests (E3-T19), the font test (E7-T4), and the plural test
    (E7-T2) pass for the language.
  - The Windows smoke test passes in the language.
  - The language shows in the list of More, Settings, and in the video
    language list.
- Tests: —

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks E7-T1 to E7-T4 are done before the first
language of this epic.

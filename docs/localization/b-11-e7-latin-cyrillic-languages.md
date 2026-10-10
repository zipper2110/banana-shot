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
  - A plural message with a case that the language does not have (for
    example `few` in Spanish, often a copy from another language) is a
    test failure.
  - A number with decimals uses the CLDR category for decimals. Example:
    Russian "1,5 секунды" uses `other`, not `one`.
  - Zero uses the category of the language. Example: French "0 point"
    uses `one`. Use an explicit `=0` case only when the English text has
    one too.
- Acceptance:
  - A catalog test fails for a Russian message with only `one` and
    `other`.
  - A test formats a Russian plural message with 1, 2, 5, and 21, and gets
    the correct form for each number.
  - The test also uses 0, 11, 12, 22, 25, 101, 111, and 1.5 for Russian
    and Polish, and 0 and 1.5 for French.
  - A catalog test fails for a Spanish message with a `few` case.
- Tests: —

### E7-T3 Shortcuts with Cyrillic and Greek keyboard layouts

- Status: open
- Plan: 8
- Value: a user with a Russian, Ukrainian, or Greek keyboard layout can use
  all shortcuts without a change of the layout.
- Requirements:
  - Each shortcut works with the Russian and the Greek keyboard layouts on
    Windows. The shortcut binds the physical key, not the typed character.
  - No shortcut uses Ctrl+Alt with a letter or a punctuation key. Windows
    sends AltGr as Ctrl+Alt. Polish, German, Czech, and other layouts type
    letters with AltGr (for example "ą", "ł", "@", "€"). Such a shortcut
    acts when the user types the letter.
  - No shortcut uses a punctuation key ("[", "]", "+", "/", ";"). On many
    layouts these keys have other characters or need AltGr.
  - While a text field has the focus, the user can type all letters of
    the layout, also with AltGr.
- Acceptance:
  - A source scan test fails for a shortcut that binds a typed character.
  - The scan test also fails for a shortcut with Ctrl+Alt and for a
    shortcut with a punctuation key.
  - The manual check types "ą", "ł", and "€" with AltGr (Polish layout)
    in a player name and a comment. The letters show, and no shortcut
    acts.
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
  - The check uses all letters of the language (the CLDR exemplar
    characters), not only the letters of the current texts. Thus player
    names in the language also show. Examples of gaps in old fonts: the
    Romanian "ș" and "ț" with a comma below (U+0219, U+021B), and the
    Vietnamese letters with two accents.
  - The check uses the letters in the composed form (NFC) and in the
    decomposed form (NFD), because a user can type both.
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
  - The language code is in the language list of E2-T1. Thus the
    analytics and the feedback accept it without a deployment. If the code
    is not in the list, change the list first, and deploy the workers
    before the app release.
  - The Swing and FlatLaf texts show in the language. If the JDK and
    FlatLaf have no texts for the language, the catalog gives them. No
    Swing text shows in English or in the Windows language.
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
  - The catalog tests find no plural case that the language does not
    have, and no case that it needs and does not have.
  - The detection maps the regional variants of the language to it (for
    example `pt-BR` and `pt-PT`). If the variants need different texts,
    the plan, "Decisions", tells which variant ships and why.
- Tests: —

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks E7-T1 to E7-T4 are done before the first
language of this epic.

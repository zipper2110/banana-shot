# B-11 E7: More Languages with Latin and Cyrillic Letters

This file holds the tasks of epic E7 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 2 (plural forms), 6 ("Fonts", "Fit"), 8, 12, 13
- Depends on: E4 (the process for a new language), E2 (the data)
- Feature: the next languages from the data of E2. Probable candidates:
  Portuguese, French, Italian, German, Russian.
- Prepares: a large part of the users can use the app in their language.
- Size: about 1 week for each language, mostly review time.

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
- Work:
  - Read the `os_language` data in the cockpit (E2). Use at least 4 weeks
    of data.
  - Compare it with the marketing plan (`docs/marketing/strategy.md`).
  - Select the languages and their order. Check that a reviewer is
    available for each one.
- Acceptance:
  - The plan, "Decisions", has the list, the order, and the data.
- Tests: — (decision)

### E7-T2 Plural forms with the CLDR rules

- Status: open
- Plan: 2 ("Message syntax"), 12
- Work:
  - The catalog tests check each plural message of each language. The
    message must have each plural category of the language (CLDR rules).
    Example: Russian needs `one`, `few`, `many`, and `other`.
  - If E3-T1 selected own plural rules, add the rules of each new language
    from the CLDR data.
- Acceptance:
  - A test fails for a Russian message with only `one` and `other`.
- Tests: —

### E7-T3 Shortcuts with Cyrillic and Greek keyboard layouts

- Status: open
- Plan: 8
- Work:
  - Check that each shortcut binds the key code (`KeyEvent.VK_Q`), not the
    typed character.
  - Test each shortcut on Windows with the Russian and the Greek keyboard
    layouts.
  - Fix each shortcut that uses the typed character.
- Acceptance:
  - A source scan test fails for a shortcut that binds a character.
  - The manual check is written in `qa/windows/ui-smoke.md`.
- Tests: —

### E7-T4 Font check for each language

- Status: open
- Plan: 6 ("Fonts")
- Work:
  - A test checks that each scoreboard font can show all letters of the
    video texts of each language (`Font.canDisplayUpTo`).
  - On Windows, verify "Ink Free" with Cyrillic and Greek letters.
  - If B-13 bundled the open fonts already, check these fonts.
  - Check the UI font ("Segoe UI") with all letters of the UI texts.
- Acceptance:
  - The font test passes for each shipped language, or a font that cannot
    show a language is not in the list for this language.
- Tests: —

### Template: E7-L-`<code>` `<Language>`

- Status: open
- Plan: 13, and the process of E4
- Work:
  - The glossary for the language in `docs/localization/glossary.md`.
  - The reviewer: a native speaker who plays tennis or padel.
  - The machine draft (`messages_<code>.properties`,
    `help_<code>.properties`) with the glossary and the key comments.
  - The review with the screenshots (E4-T4).
  - The fit tests (E3-T19) and the UI screenshots. German has the longest
    texts: check its screenshots with care.
  - The analytics value `<code>`: the contract, the worker, and the cockpit.
    Deploy the worker before the app release.
  - The FlatLaf texts in the language.
  - The Windows smoke test in the language.
  - `README.md`: the list of the languages. `Info.plist` of B-13
    (`CFBundleLocalizations`).
- Acceptance:
  - The check of E4-T3 shows no missing key for the language.
  - The reviewer accepts all texts.
  - The fit tests, the font test, and the plural test pass.
  - The language shows in the list of More, Settings, and in the video
    language list.
- Tests: —

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks E7-T1 to E7-T4 are done before the first
language of this epic.

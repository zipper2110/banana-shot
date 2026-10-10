# B-11 E6: Translated Notices from the Server

This file holds the tasks of epic E6 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 11 ("Rules file and update notice"), 14
- Depends on: E3. You can do it at any time after E3. The first translated
  texts need E4.
- Feature: the update notes and the expiry messages show in the language of
  the app.
- Prepares: the translated release notes for each later release.
- Size: 1 to 2 days.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E6-T1 | Translated fields in the rules model | — | open |
| E6-T2 | The app shows the text of the app language | E6-T1 | open |
| E6-T3 | Specification and release checklist | E6-T1 | open |
| E6-T4 | First translated notes | E6-T2, E6-T3, E4 | open |

### E6-T1 Translated fields in the rules model

- Status: open
- Plan: 11 ("Rules file and update notice")
- Work:
  - Add the optional fields `latest.notesByLanguage` and
    `messageByLanguage` (in each rule) to `license/VersionRules.kt` and
    `license/VersionRulesParser.kt`. The value is an object with a language
    tag as the key, for example `{"es": "…"}`.
  - Keep `schema: 1`. Released builds ignore the new fields, and this is
    safe: they show the English text (`build-expiry-spec.md`, "Changes to
    the file format").
  - `license/SavedVersionRules.kt` keeps the new fields in the saved copy.
  - The parser ignores a field with a wrong type, and keeps the English
    text. It does not reject the full file.
- Acceptance:
  - A test parses a file with the new fields.
  - A test parses the current `release/version-policy.json`.
  - A test proves that the parser of the current release accepts a file
    with the new fields (a fixture with the new fields, read by the parser
    without the change).
  - A test gives a field with a wrong type, and the parser keeps the rules.
- Tests: —

### E6-T2 The app shows the text of the app language

- Status: open
- Plan: 11 ("Rules file and update notice")
- Work:
  - The update notice and the expiry messages use the text of the app
    language. If there is none, they use the English field.
  - A regional tag uses the main language (`es-AR` uses `es`), as the
    language detection of E3-T21.
  - The app shows these texts as plain text, as now.
- Acceptance:
  - Tests for the app languages English and Spanish, with and without a
    Spanish text in the file.
  - A test gives HTML in a translated text, and the app shows it as plain
    text.
- Tests: —

### E6-T3 Specification and release checklist

- Status: open
- Plan: 11, 14
- Work:
  - `docs/licensing/build-expiry-spec.md`: the new fields and the fallback.
  - `docs/release-checklist.md`: for each shipped language, add the
    translated notes and messages, and check them before the push of the
    rules file.
- Acceptance:
  - The spec and the checklist name the new fields.
- Tests: —

### E6-T4 First translated notes

- Status: open
- Plan: 11
- Work:
  - Add the Spanish notes of the next release to
    `release/version-policy.json`. The reviewer of E4 checks them.
  - After the push, check the update notice on a Windows with the app in
    Spanish and in English.
- Acceptance:
  - The Spanish app shows the Spanish notes. The English app shows the
    English notes.
- Tests: — (manual)

## Done when

- All tasks are `done`.
- The update notice and the expiry messages from the server show in the app
  language, with the English text as the fallback.

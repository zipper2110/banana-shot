# B-11 E6: Translated Notices from the Server

This file holds the tasks of epic E6 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 11 ("Rules file and update notice"), 14
- Depends on: E3. You can do it at any time after E3. The first translated
  texts need E4.
- Size: 1 to 2 days.
- Value: a user who does not read English understands why to update and
  what the expiry message tells. These texts come from the server, so E3
  and E4 do not translate them.
- Prepares: translated release notes for each later release.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E6-T1 | Translated fields in the rules file | — | open |
| E6-T2 | The app shows the text of the app language | E6-T1 | open |
| E6-T3 | Specification and release checklist | E6-T1 | open |
| E6-T4 | First translated notes | E6-T2, E6-T3, E4 | open |

### E6-T1 Translated fields in the rules file

- Status: open
- Plan: 11 ("Rules file and update notice")
- Value: the author can publish the notes and the messages in more than one
  language, and the released builds continue to work.
- Requirements:
  - The rules file has two new optional fields: `latest.notesByLanguage`,
    and `messageByLanguage` in each rule. The value is an object with a
    language code as the key, for example `{"es": "…"}`.
  - The file keeps `schema: 1`. Released builds ignore the new fields, and
    show the English text (`build-expiry-spec.md`, "Changes to the file
    format").
  - The saved copy of the rules keeps the new fields.
  - A new field with a wrong type is ignored. The app keeps the English
    text and all other rules. It does not reject the full file.
  - Bad values in the new fields:
    - An empty or blank text for a language is ignored. The app shows the
      English text, not an empty notice.
    - A key that is not a value of the language list (for example `ES`,
      `es-ES`, or `qps`) is ignored. The log gets one warning.
    - A value that is not a text (a number, an object) is ignored for this
      language only.
  - The English fields stay required, as now. A rule with only translated
    texts and no English text is not valid, as now.
- Acceptance:
  - A test parses a file with the new fields.
  - A test parses the current `release/version-policy.json`.
  - Before the parser change, a test reads a fixture with the new fields
    and passes. This proves that the released builds accept the file.
  - A test gives a field with a wrong type, and the app keeps the rules.
  - A test gives an empty Spanish text, a key `es-ES`, and a number value.
    The Spanish app shows the English text in each case.
- Tests: —

### E6-T2 The app shows the text of the app language

- Status: open
- Plan: 11 ("Rules file and update notice")
- Value: the user reads the notes and the messages in their language.
- Requirements:
  - The update notice and the expiry messages from the server use the text
    of the app language. If the file has no text for it, they use the
    English field.
  - A language code with a region uses the main language (`es-AR` uses
    `es`), as the detection of E3-T21.
  - The app shows these texts as plain text, as now.
  - With `qps`, the app shows the English text.
  - A long translated text wraps in the notice, as the English text does.
    The notice buttons stay visible.
- Acceptance:
  - Tests for the app languages English and Spanish, with and without a
    Spanish text in the file.
  - A test gives HTML in a translated text, and the app shows it as plain
    text.
- Tests: —

### E6-T3 Specification and release checklist

- Status: open
- Plan: 11, 14
- Value: the author does not forget the translations at a release.
- Requirements:
  - `docs/licensing/build-expiry-spec.md` gives the new fields and the
    fallback.
  - The release checklist tells to add the translated notes and messages
    for each shipped language, and to check them before the push of the
    rules file.
  - The release checklist tells to update or remove each translation when
    the English text changes. An old translation that tells a different
    thing is worse than the English fallback.
- Acceptance:
  - The specification and the checklist name the new fields.
- Tests: —

### E6-T4 First translated notes

- Status: open
- Plan: 11
- Value: the first Spanish users get the update notes in Spanish.
- Requirements:
  - The rules file has the Spanish notes of the next release. The reviewer
    of E4 checks them.
- Acceptance:
  - On Windows, the Spanish app shows the Spanish notes, and the English
    app shows the English notes.
- Tests: — (manual)

## Done when

- All tasks are `done`.
- The update notice and the expiry messages from the server show in the app
  language, with the English text as the fallback.

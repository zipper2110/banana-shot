# B-11 E8: Languages with Other Scripts

This file holds the tasks of epic E8 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: open
- Plan sections: 6 ("Fonts"), 7, 9, 13
- Depends on: E7 (the shared tasks E7-T1 to E7-T4), E2 (the data)
- Size: 1 to 2 weeks for the first language of a new script. Less for the
  next languages of the same script.
- Value: users of Chinese, Japanese, Korean, or other languages with large
  character sets can use the app in their language, if the data of E2
  shows a need.

Not in this epic: right-to-left languages (plan section 9).

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E8-T1 | Select the language | E2 | open |
| E8-T2 | Video fonts for the script | E8-T1 | open |
| E8-T3 | UI font for the script | E8-T1 | open |
| E8-T4 | Line breaks without spaces | E8-T1 | open |
| E8-T5 | Input method tests | E8-T1 | open |
| E8-L-`<code>` | One task for each language (template of E7) | E8-T2–E8-T5 | — |

When E8-T1 selects a language, add a row `E8-L-<code>` to this table, and
copy the template of E7 ("Template: E7-L-`<code>` `<Language>`").

### E8-T1 Select the language

- Status: open
- Plan: "Decisions" ("Next languages"), 10
- Value: the high cost of a new script goes to a language with enough
  users.
- Requirements:
  - Compare the number of users of each language (`os_language` data) with
    the cost of the fonts and of the review.
  - A reviewer must be available.
- Acceptance:
  - The plan, "Decisions", has the language, the data, and the reviewer.
- Tests: — (decision)

### E8-T2 Video fonts for the script

- Status: open
- Plan: 6 ("Fonts"), "Costs"
- Value: the video shows the letters of the script, and the preview shows
  the same letters as the export.
- Requirements:
  - A font for the script with a license that allows distribution (for
    example Noto Sans CJK). The third-party notices name it.
  - A decision: bundle the font (more than 15 MB for each CJK language), or
    download it when the user selects the language. Write the decision and
    the reason in the plan.
  - The export, the preview, and the thumbnails use the same font file.
- Acceptance:
  - The font test of E7-T4 and the fit tests of E3-T19 pass for the
    language.
  - The preview and the export show the same letters.
- Tests: —

### E8-T3 UI font for the script

- Status: open
- Plan: 7
- Value: the UI shows the letters of the script, not empty boxes.
- Requirements:
  - The UI font now is "Segoe UI". It has no Chinese, Japanese, or Korean
    letters, and Swing does not use a fallback font for it.
  - With the language, the UI uses a font that has the letters (for example
    the Windows fonts Microsoft YaHei, Yu Gothic, Malgun Gothic), or a font
    with a fallback.
  - The custom-painted components use the same font.
  - The user texts in Latin letters (player names) still show correctly.
- Acceptance:
  - The screenshots of E4-T4 in the language show no empty boxes.
- Tests: —

### E8-T4 Line breaks without spaces

- Status: open
- Plan: 7
- Value: long texts in Chinese and Japanese wrap correctly in the UI and in
  the help.
- Requirements:
  - Chinese and Japanese have no spaces between words. The text wrap of the
    UI and of the help breaks a line between characters, by the line break
    rules of the language (for example `java.text.BreakIterator`).
- Acceptance:
  - A unit test wraps a Chinese and a Japanese text to a given width. No
    line is wider than the width, and no line starts with a closing
    punctuation mark.
- Tests: —

### E8-T5 Input method tests

- Status: open
- Plan: 9
- Value: a user can type in their script in all text fields when the app
  is in their language.
- Requirements:
  - The input method of the language works on Windows for player names,
    comments, project names, and the search fields.
  - E3-T23 did a first check. Repeat it with the app in the language.
- Acceptance:
  - The manual check is in `qa/windows/ui-smoke.md`, and it passes.
- Tests: — (manual)

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks of the script are done before its first
language.

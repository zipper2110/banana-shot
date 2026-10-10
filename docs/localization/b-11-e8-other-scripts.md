# B-11 E8: Languages with Other Scripts

This file holds the tasks of epic E8 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 6 ("Fonts"), 7, 9, 13
- Depends on: E7 (the shared tasks E7-T1 to E7-T4), E2 (the data)
- Feature: Chinese, Japanese, Korean, or other languages with large
  character sets, if the data of E2 shows a need. Right-to-left languages
  are not in this epic (plan section 9).
- Size: 1 to 2 weeks for the first language of a new script. Less for the
  next languages of the same script.

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
- Work:
  - Read the `os_language` data in the cockpit. Compare the users of each
    language with the cost of the fonts and the review.
  - Select the language and find a reviewer.
- Acceptance:
  - The plan, "Decisions", has the language and the data.
- Tests: — (decision)

### E8-T2 Video fonts for the script

- Status: open
- Plan: 6 ("Fonts"), "Costs"
- Work:
  - Select a font for the script (for example Noto Sans CJK). Check its
    license, and add it to `distribution/THIRD-PARTY-NOTICES.txt`.
  - Decide: bundle the font (more than 15 MB for each CJK language), or
    download it on demand when the user selects the language. Write the
    decision in the plan.
  - The export (libass), the preview (libmpv), and the thumbnails (Java2D)
    use the same font file.
  - Run the font test of E7-T4 and the fit tests of E3-T19 with the
    language.
- Acceptance:
  - The font test passes for the language.
  - The preview and the export show the same letters.
- Tests: —

### E8-T3 UI font for the script

- Status: open
- Plan: 7
- Work:
  - The app sets "Segoe UI" as the UI font (`SwingMainApp.kt`). Segoe UI
    has no Chinese, Japanese, or Korean letters, and a physical font in
    Swing does not fall back to another font.
  - Select the UI font for the language from the Windows fonts (for
    example Microsoft YaHei, Yu Gothic, Malgun Gothic), or use a font with
    a fallback.
  - The custom-painted components use the same font.
- Acceptance:
  - The screenshots of E4-T4 in the language show no empty boxes.
- Tests: —

### E8-T4 Line breaks without spaces

- Status: open
- Plan: 7, "Epics" (E8)
- Work:
  - Chinese and Japanese have no spaces between words. `WrapText` and the
    help must break a line between characters. Use
    `java.text.BreakIterator.getLineInstance` with the app locale.
- Acceptance:
  - A unit test wraps a Chinese and a Japanese text to a given width.
- Tests: —

### E8-T5 Input method tests

- Status: open
- Plan: 9
- Work:
  - Test the input with the input method of the language on Windows: player
    names, comments, project names, and the search fields.
  - E3-T23 did a first check. Repeat it with the app in the language.
- Acceptance:
  - The manual check is written in `qa/windows/ui-smoke.md`.
- Tests: — (manual)

## Done when

This epic has no end. Each language task is done when its acceptance
criteria pass. The shared tasks of the script are done before its first
language.

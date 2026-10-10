# B-11 E5: Video Language

This file holds the tasks of epic E5 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the status values are in the
plan, "Epics".

## Epic

- Status: open
- Plan sections: 6 ("Setting")
- Depends on: E4
- Feature: the user selects the language of the video on the Export tab,
  separately from the app language. Example: a user with the app in Spanish
  exports a video with English texts for an international club.
- Prepares: with more languages, each user can select any video language.
- Size: a few days.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E5-T1 | Video language in the export settings | — | open |
| E5-T2 | "Video language" choice on the Export tab | E5-T1 | open |
| E5-T3 | The render uses the selected language | E5-T1 | open |
| E5-T4 | The preview uses the video language | E5-T1 | open |
| E5-T5 | Numbers in the video | E5-T3, E5-T4 | open |
| E5-T6 | Checks and documentation | E5-T1–E5-T5 | open |

### E5-T1 Video language in the export settings

- Status: open
- Plan: 6 ("Setting")
- Work:
  - The project keeps the last video language in its export settings.
    Thus a new export of the same project makes the same video.
  - The field is optional. A project without the field uses the app
    language. Older builds ignore the field.
  - The list of video languages has only the languages with a complete
    and reviewed video part of the catalog. A test finds the video keys
    (for example by their key prefix).
- Acceptance:
  - A test saves and reads a project with the video language.
  - A test reads a project of the current release and gets the app
    language.
- Tests: —

### E5-T2 "Video language" choice on the Export tab

- Status: open
- Plan: 6 ("Setting")
- Work:
  - The Export tab gets a "Video language" choice. The default is the app
    language. Each language shows in its own language.
  - The presenter puts the list and the selection into the `ViewState`.
  - If only one language has a complete video part, the choice does not
    show.
- Acceptance:
  - A presenter test: the default, a change, and the saved value.
  - A UI flow test selects a language.
- Tests: —

### E5-T3 The render uses the selected language

- Status: open
- Plan: 6 ("Setting")
- Work:
  - `app` makes one `Messages` instance for each video language that a job
    needs.
  - The queued job keeps the selected video language (`SavedRenderQueue`,
    E4-T9).
  - The scoreboard, the statistics card, the set summaries, and the default
    side names use the video language.
- Acceptance:
  - A test renders the ASS file of a job with the app in Spanish and the
    video in English, and gets the English texts.
- Tests: —

### E5-T4 The preview uses the video language

- Status: open
- Plan: 6 ("Setting")
- Work:
  - The live preview of the scoreboard (libmpv overlay) and of the
    statistics card uses the video language, not the app language.
  - The Java2D thumbnails of the scoreboard styles use the video language.
  - A change of the video language updates the preview at once.
- Acceptance:
  - A test checks that the preview overlay and the export use the same
    texts for the same video language.
- Tests: —

### E5-T5 Numbers in the video

- Status: open
- Plan: 6 ("Setting")
- Work:
  - The numbers in the video (for example the percentages of the
    statistics card) use the format of the video language, not the Windows
    regional format. The people who watch the video can live in a
    different region.
  - Score values and time codes stay the same in all languages.
- Acceptance:
  - A test renders the statistics card in English and in Spanish and
    checks the decimal separator.
- Tests: —

### E5-T6 Checks and documentation

- Status: open
- Plan: 12, 14
- Work:
  - Run the fit tests of E3-T19 for each video language.
  - Add one export with a different video language to
    `qa/windows/ui-smoke.md`.
  - The help of the Export tab tells about the choice (in all languages).
- Acceptance:
  - The smoke test passes.
- Tests: —

## Done when

- All tasks are `done`.
- The user can change the video language separately from the app language.
- The preview and the export show the same texts.

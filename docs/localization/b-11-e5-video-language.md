# B-11 E5: Video Language

This file holds the tasks of epic E5 of B-11 (localization). The plan is in
`b-11-localization.md` ("the plan"). The plan has priority. If the plan
changes, change the tasks too. The rules for the tasks and the status values
are in the plan, "Epics".

## Epic

- Status: in-progress
- Plan sections: 6 ("Setting"), "Decisions" ("Video number format")
- Depends on: E4
- Size: a few days.
- Value: the user makes a video for the people who watch it, not for
  themselves. Example: a user with the app in Spanish exports a video with
  English texts for an international club. A coach with the app in English
  exports a video in Spanish for their players.
- Prepares: with more languages, each user can select any video language.

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| E5-T1 | The project keeps the video language | — | done |
| E5-T2 | "Video language" choice on the Export tab | E5-T1 | done |
| E5-T3 | The render uses the selected language | E5-T1 | done |
| E5-T4 | The preview uses the video language | E5-T1 | done |
| E5-T5 | Numbers in the video | E5-T3, E5-T4 | done |
| E5-T6 | Checks and documentation | E5-T1–E5-T5 | in-progress |

Notes for all tasks:

- The code that makes a video text runs in `I18n.inVideo(language) { … }`.
  In this block, `tr`, `DisplayFormats.upper`, and the number formats use
  the video language. The block is bound to the thread. Thus the plan's
  "one `Messages` instance for each video language" needs no change of the
  domain code, which reads its texts with `tr`. A video text that the code
  makes outside of a block has the app language.
- A block must not contain a modal dialog: all texts of the dialog then get
  the video language. `ScoreboardSettingsDialog` gets the language and
  makes only its previews in a block.

### E5-T1 The project keeps the video language

- Status: done
- Plan: 6 ("Setting")
- Value: a new export of the same project makes the same video.
- Requirements:
  - The project keeps the last selected video language in its export
    settings.
  - The field is optional. A project without the field uses the app
    language. An older build can open a project with the field.
  - If the saved language is not available (for example the user opens
    the project on a computer with an older build), the app uses the app
    language.
  - The project saves the video language only when the user selects one.
    A project without a selection follows the app language. Thus a user
    who changes the app language from English to Spanish gets Spanish
    videos, also for a project that they exported before.
  - A saved language that this build does not have, or a broken value,
    gives the app language. The app does not overwrite the saved value
    until the user selects a language (see the plan, "Decisions",
    "Unknown language value").
  - If an older build saves the project, the field can be lost. The
    project then follows the app language. This is accepted.
- Acceptance:
  - A test saves and reads a project with the video language.
  - A test reads a project of the current release and gets the app
    language.
  - A test exports a project without a selection, changes the app
    language, and exports again. The second video uses the new app
    language.
  - A test reads a project with the video language `pt` (not in this
    build) and a project with a broken value. Both give the app language,
    and a save without a user change keeps the old value.
- Notes: the field is `videoLanguage` in the project manifest (`.trproj`),
  not in the preferences. Thus the language goes with the project folder.
  `ProjectVideoLanguage` reads and saves it. Each manifest change of the
  app is a copy of the read manifest, so an unknown value stays.
- Tests: `ProjectVideoLanguageTest`,
  `VideoLanguageExportTest.an export without a selection uses the app language of the time of the export`.

### E5-T2 "Video language" choice on the Export tab

- Status: done
- Plan: 6 ("Setting")
- Value: the user selects the video language where they make the video.
- Requirements:
  - The Export tab has a "Video language" choice. The default is the app
    language. Each language shows in its own language.
  - The list has the same languages as the "Language" row of More,
    Settings. Each shipped language is complete, also its video texts
    ("Decisions", "Incomplete language").
  - If the app has only one language, the choice does not show.
  - A language whose video fonts are not available (for example a CJK
    font that is not downloaded yet, see E8-T2) is not selectable. The
    list tells the reason.
- Acceptance:
  - A presenter test checks the default, a change, and the saved value.
  - A UI flow test selects a language.
- Notes:
  - The Export tab has no presenter. `SwingExportPanelTest` checks the
    default, a change, the saved value, and the language of the queued
    job.
  - `ExportConfigurationUiFlowIT` selects German (Spanish when the app
    language is German) and checks the queued job and the project file.
  - The font check starts with E8-T2, because all current languages use
    the same fonts.
- Tests: `SwingExportPanelTest.theVideoLanguageStartsAtTheAppLanguageAndTheProjectKeepsTheSelection`,
  `SwingExportPanelTest.withOnlyOneLanguageTheVideoLanguageRowDoesNotShow`,
  `SwingExportPanelTest.aChangeOfTheVideoLanguageInCodeIsNotASelection`,
  `ExportConfigurationUiFlowIT."the selected video language goes into the queued job and the project"`.

### E5-T3 The render uses the selected language

- Status: done
- Plan: 6 ("Setting")
- Value: the exported video shows the texts in the selected language.
- Requirements:
  - The scoreboard, the statistics card, the set summaries, and the default
    side names use the video language.
  - The queued job keeps the selected video language (E4-T9).
  - User texts (player names, comments, the title that the user wrote) do
    not change.
- Acceptance:
  - A test renders the ASS file of a job with the app in Spanish and the
    video in English, and gets the English texts.
  - A test renders a job whose video language has a missing video text.
    The video shows the English text for it, and the export does not
    fail.
- Notes:
  - `ExportPlanner.buildRenderPlan` makes the scoreboard spans, the
    statistics card, and the set summaries in the video language when the
    user queues the export. The job keeps these texts and the language code
    (`RenderJob.videoLanguage`). The ASS writer runs in the language of the
    job (`RenderOverlayScript`), for the upper case and the default title.
  - A default side name or a default title of any app language shows in the
    video language (`Sport.videoSideName`,
    `ScoreboardSettingsV1.videoTitle`). A name or a title that the user
    typed does not change.
- Tests: `VideoLanguageExportTest`.

### E5-T4 The preview uses the video language

- Status: done
- Plan: 6 ("Setting")
- Value: the user sees the video texts before the export.
- Requirements:
  - The live preview of the scoreboard and of the statistics card, and the
    thumbnails of the scoreboard styles, use the video language, not the
    app language.
  - A change of the video language updates the preview at once.
  - Fast changes of the video language never show a preview with texts
    of two languages. The last selection wins.
- Acceptance:
  - A test checks that the preview and the export give the same texts for
    the same video language.
- Notes: the previews are on the Points tab, in the Scoreboard style
  dialog, and on the Stats tab. Each tab reads the language of the project
  when it opens, so a change on the Export tab shows at the next tab open.
  The texts of one preview come from one block, so a preview never has two
  languages.
- Tests: `VideoLanguageExportTest.the preview and the export show the same texts for the same video language`.

### E5-T5 Numbers in the video

- Status: done
- Plan: 6 ("Setting"), "Decisions" ("Video number format")
- Value: the people who watch the video see numbers in the format of their
  language, not in the format of the computer that made the video.
- Requirements:
  - The numbers in the video (for example "4.2 s" and "64%" on the
    statistics card) use the format of the video language:
    - If the language of the Windows regional format is the video language,
      use the regional format. Example: video in Spanish, regional format
      "Spanish (Mexico)": "4.2 s".
    - If not, use the default format of the video language. Example: video
      in Spanish, regional format "English (United States)": "4,2 s".
  - Score values and time codes stay the same in all languages.
  - The video always shows the digits 0–9 and the Gregorian calendar, also
    when the regional format has a digit or a calendar extension (E1-T5).
- Acceptance:
  - A test renders the statistics card with the three cases: English,
    Spanish with `es-MX`, and Spanish with `en-US`. It checks the decimal
    separator and the percent sign.
  - A test renders the statistics card with the video in English and the
    regional format `ar-EG`. The card has only the digits 0–9.
- Notes: the rule is in `DisplayFormats` (the number locale in a video
  block).
- Tests: `VideoLanguageExportTest.the statistics card shows the numbers in the video number format`,
  `VideoLanguageTest`.

### E5-T6 Checks and documentation

- Status: in-progress
- Plan: 12, 14
- Value: the separate video language stays correct in later releases.
- Requirements:
  - The fit tests of E3-T19 run for each video language.
  - `qa/windows/ui-smoke.md` has one export with a video language that is
    different from the app language.
  - The help of the Export tab tells about the choice, in all languages.
- Acceptance:
  - The smoke test passes.
- Notes:
  - Done: the help of the Export tab (`help.export.workflow.videoLanguage`,
    in all languages) and step 6 of `qa/windows/ui-smoke.md`.
  - Done: the fit test (`VideoTextFitTest`, done with E4-T8) runs for each
    language of `AppLanguage`, so it covers each video language.
  - Open: the smoke test did not run yet.
- Tests: `VideoTextFitTest`.

## Done when

- All tasks are `done`.
- The user can change the video language separately from the app language.
- The preview and the export show the same texts.

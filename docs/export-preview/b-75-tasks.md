# B-75 Tasks: Export preview

This file tracks the work of B-75 (preview of the export before the
encode). The backlog item is in `docs/backlog.md`.

## Goal

Now the user sees the result of an export only after the encode. An
encode can take an hour. When the result is not correct, the user must
change the settings and encode again.

The preview plays the export result before the encode, in a libmpv
player. The user sees the kept points, the scoreboard, the comments, the
replays, and the cards as the export will show them. Then the user
changes the settings and sees the change at once.

## Decisions

Write each new decision here, at once, with its date.

| # | Task | Decision |
|---|---|---|
| 1 | all | Decided on 2026-11-17: the preview uses libmpv. It does not encode with FFmpeg. |
| 2 | T4 | Decided on 2026-11-17: the preview is a separate window. A Preview button on the Export tab opens it. The Export tab layout does not change in other ways. |
| 3 | all | Decided on 2026-11-17: the preview is a free feature. |
| 4 | T5 | Decided on 2026-11-17: the window has a strip of items under the player. Each point, replay, and card is a block. A click on a block, or a key, goes to the item. |
| 5 | — | Decided on 2026-11-17: no preview of a job that is in the export queue, and no sample encode with FFmpeg in B-75. |
| 6 | T4, T6 | Decided on 2026-11-17: the preview window is modal. While it is open, the user cannot use the main window. Thus the settings cannot change while the preview plays. The window gets the plan one time, when it opens. There is no live update (old Q1) and no tab change (old Q3). To see other settings, the user closes the window, changes the settings, and opens the preview again. |
| 7 | T4 | Decided on 2026-11-17 (Q4): the preview window has no Export button. The user closes the window and uses the Export button of the Export tab. |
| 8 | T4 | Decided on 2026-11-17 (Q5), as a trial: the preview can play while an export encodes. The window shows no warning. Look at this decision again after the checks of T7. |
| 9 | T2 | Decided on 2026-11-17 (Q2): at a cut between two points, the preview shows no black and no other effect. While the player seeks, the picture stays on the last frame, and the sound stops. The export has hard cuts, so an effect would show a wrong result. |
| 10 | T4 | Decided on 2026-11-17: the preview window shows a note label about the stops of decision 9. The text tells the user that the stops between points are only in the preview, and that the exported video does not have them. Example (English): "The preview can stop for a moment between points. The exported video has no stops." The label shows when the content has a cut (only points, only favorites, the point filter, a replay, or a card). A full video without replays and cards has no cut, so it shows no label. |
| 11 | T0 | Result of the code study, 2026-11-17: way B. The preview seeks in the source file. It does not use an mpv EDL. Reason: the replays and the cards need a director in each way, and `ReplayDirector` already plays the replays with seeks. The stop at a cut was not measured. T7 checks it in the running app. |
| 12 | T0, T2 | Result of the code study, 2026-11-17: the overlays go to the mpv `osd-overlay` at each new state, as on the Points tab. The events come from the code of the export files: `AssOverlayWriter.scoreboardEvents`, `CommentAss.events` with the animation state of that moment, `ReplayBadgeAss`, and the card pages (`ExportPreviewOverlay`). The fade of the board uses a `\fade` tag with the alpha of that moment. |
| 13 | T0, T3 | Result of the code study, 2026-11-17: a card pauses the player on the still frame of the export (`FFmpegCommandBuilder.stillFrameSeekMs`) and shows the card pages over it. A timer counts the card time. |
| 14 | T2, T3 | Implemented on 2026-11-17: a seek of the user into a replay starts that replay from its start. Two items that touch in the source (for example two points with no gap) play on with no seek. |
| 15 | T4 | Implemented on 2026-11-17: the keys are the keys of the other players. Space plays and pauses. Left and Right move 1 s, and Shift with Left or Right moves 5 s. Page Up and Page Down, and Up and Down, go to the previous and the next item. Esc closes the window. |
| 16 | T4 | Implemented on 2026-11-17: no HTML mockup. The author asked to implement at once. The window uses the playback bar of the Colors & Transform tab (`VideoPlaybackBar`). The Export tab footer has the size and the settings on top, and the Preview button left of Start export in a row under them. |
| 17 | all | Implemented on 2026-11-17: the code. `ReplayDirector` moved to `org.litvin.export.preview`, because a tab package cannot import another tab package (`ArchitectureDependencyHygieneTest`). The model (`ExportPreviewTimeline`), the director (`ExportPreviewDirector`), and the overlay (`ExportPreviewOverlay`) are in the same package and have no Swing code. The window (`ExportPreviewWindow`) and the strip (`ExportPreviewStrip`) are in `org.litvin.ui.tabs.export`. The director takes the place of a presenter. |
| 18 | T4 | Implemented on 2026-11-17: the window is a modal `JDialog`. On Windows, a dialog has no Maximize button. The window opens at 80% of the screen, and the user can change its size. |

## What the preview shows

The preview gets its data from the same code as the export:
`ExportPlanner.buildRenderPlan` makes the `RenderJob`, and
`ExportPassPlanner.plan` puts the passes in output order. Thus the preview
and the export cannot have different rules for the content.

The preview shows:

- The content: the full video with the trim (B-62), only points, only
  favorites, the serve clips (B-68), and the point filter (B-69).
- The order of the parts: the points, the replays after their points, the
  set cards, and the statistics card at the end.
- The scoreboard and the comments of the export, with the export timing
  rules (for example B-63: "Only points" ignores the start and end
  settings).
- The replays: the speed, the zoom, the still frame, the dips, the badge,
  the replay comments, and the slowed sound.
- The colors, the crop, and the rotation of the project.
- The texts in the video language of the export.
- The shape of the output frame: the overlays use the output size, so the
  scoreboard has the same size and place as in the export.

The preview does not show:

- The picture quality of the encode (bitrate, encoder artifacts) and the
  sharpness of the output resolution. The player scales the video to the
  window.
- The output frame rate.
- Exact cuts. At a cut between two points, the player can stop on the last
  frame for a short time while it seeks (decision 9). The export has no
  stop.

## Approach

The preview has three parts:

1. A timeline model (T1). It has no Swing code. It gets the `RenderJob`
   and the passes, and gives a list of items in output order: a video item
   (a point, or a part of the full video), a replay item, and a card item.
   It changes the output time to the source time and back. It also gives
   the overlay time: the scoreboard and the comments of the export use a
   video timeline without the replays and the cards.
2. A preview director (T2, T3). It plays the items in one libmpv player.
   It seeks at each cut. For a replay, it uses the same steps as
   `ReplayDirector` on the Points tab. For a card, it holds the frozen
   frame and shows the card.
3. The window (T4, T5): the player, the transport controls, and the item
   strip.

There are two possible ways to play the cuts. T0 selects one:

- A: an mpv EDL (`edl://`) of the kept points. mpv then plays one
  timeline. The mpv time is the video timeline of the export. Thus the
  player can load the ASS file of the export (`sub-add`) with no time
  change. The director stops the EDL for each replay and each card.
- B: seeks in the source file, as `ReplayDirector` does now. The director
  changes the overlay times to the source time, or it sends the overlay
  events at each time update (`osd-overlay`).

## Open questions

- Q1: closed by decision 6 (no live update).
- Q2: closed by decision 9 (no black at a cut; the picture stays on the
  last frame while the player seeks).
- Q3: closed by decision 6 (the user cannot change the tab).
- Q4: closed by decision 7 (no Export button in the window).
- Q5: closed by decision 8 (the preview plays during an encode, as a
  trial).

## Tasks

### T0 Code study and spike

- Read `ReplayDirector`, `MpvSwingMediaPlayerAdapter`, `AssOverlayWriter`,
  `RenderOverlayScript`, and `ExportPassPlanner`.
- Try way A and way B with a short 4K sample from `D:\`. Measure the stop
  at a cut. Do not run long benchmarks.
- Check how the player shows a card: the frozen frame and the card image
  (`setImageOverlay`), or the card ASS of the export
  (`AssOverlayWriter.writeStatsCard`).
- Done when: this file has the decisions for the way to play the cuts, the
  overlay timing, and the cards.

### T1 Timeline model

- A new class in `org.litvin.export`, for example `ExportPreviewTimeline`.
  No Swing code.
- Input: the `RenderJob` and the result of `ExportPassPlanner.plan`.
- Output: the items in output order, each with its output start and
  duration, its source range, and its kind (video, replay, card). A video
  item also has its point id.
- Functions: output time to item and source time; source time to output
  time; the overlay time of an output time; the item before and after an
  output time.
- Tests: full video, full video with trim, only points, only favorites,
  point filter, serve clips, replays, set cards, and the statistics card.
  The total duration of the timeline is the same as the duration that the
  export gives for the progress (`Pass.progressBaseMs`).
- Done when: the tests pass, and the model does not repeat a rule of
  `ExportPlanner` or `ExportPassPlanner`.

### T2 Preview director: points and overlays

- A director without Swing code, as `ReplayDirector`. It gets the player
  times and gives the commands for the player.
- It plays the video items in output order and seeks at each cut.
- It shows the scoreboard and the comments of the export at the correct
  output time (the way of T0).
- It applies the colors, the crop, and the rotation of the job
  (`applyPreviewAdjustments`).
- Tests: the commands at the cuts, a user seek, the end of the timeline.
- Done when: the director plays "Only points" with the scoreboard and the
  comments in a test with a fake player.

### T3 Preview director: replays and cards

- It plays each replay item with the steps of `ReplayDirector`: the speed,
  the zoom (`Replays.zoomsIn`), the still frame, the dips, and the sound.
  Use the same code where possible. Do not copy it.
- It shows the replay badge and the replay comments (`ReplayBadgeAss`, the
  comments of `ExportReplay`).
- It holds the frozen frame for each card item and shows the card for the
  card duration.
- Tests: a point with two replays, a set card, the statistics card at the
  end.
- Done when: the director plays a project with replays and cards in a test
  with a fake player.

### T4 Preview window

- Make an HTML mockup first in `design/export-preview/`. The author
  approves it before the Swing work.
- A Preview button on the Export tab, next to Export. It is disabled for
  the same reasons as Export (`ExportPlanner.initializationReadiness`).
- A modal window (decision 6). The main window does not get input while
  it is open. The export queue continues to encode. The window has the
  player area in the output frame shape,
  play and pause, a seek bar in output time, the output time and the total
  time, and the previous and next item buttons.
- Keys: Space plays and pauses; Left and Right move 5 s; the item keys of
  T5.
- The title shows the content and the resolution, for example "Preview –
  Only favorites, 1080p".
- The note label about the stops between points (decision 10). Put it
  under the player, near the item strip, in the style of a small note,
  not a warning.
- Esc closes the window. The window keeps its size and place. When it
  closes, it releases the player.
- MVP: the presenter is in `org.litvin.ui.tabs.export.presenter`
  (`docs/architecture-rules.md`).
- Texts in all app languages. A help entry.
- Done when: the user opens the window, plays the export result, and
  closes the window. The other tabs still play their videos.

### T5 Item strip

- A strip under the seek bar. Each point, replay, and card is a block. The
  width of a block shows its output duration.
- The current item has a mark. Hover shows the point number and the score,
  or the card name.
- A click on a block goes to the start of the item. Keys go to the previous
  and the next item.
- Use the colors of the Points tab where they have the same meaning (for
  example the lime replay block). All colors are in `Palette`.
- Done when: the user goes from point to point with the strip and with the
  keys.

### T6 Plan when the window opens

- Changed on 2026-11-17 by decision 6: no live update.
- When the window opens, it gets the plan from the current settings of the
  Export tab, with the same code as the Export button. The plan does not
  change while the window is open.
- The window opens only when the export is ready. The Preview button is
  disabled for the same reasons as Export (T4).
- Done when: the user previews "Only points", closes the window, selects
  "Only favorites", opens the preview again, and the preview shows only
  the favorites.

### T7 Checks

- Compare the preview with a real export of a sample project: full video
  with trim, only points with replays, only favorites with cards. Take a
  screenshot at the same output times. The scoreboard, the comments, and
  the cards are the same.
- Check a source with non-square pixels (B-59) and a rotated source.
- Check on macOS (B-13). The mpv video on macOS uses its own window
  (`MacVideoWindow`).
- Done when: the checks pass, or each difference has a backlog item.

# B-50 Tasks: Instant Replay

This file tracks the work of B-50 (instant replay of a part of a point).
The backlog item and its decisions are in `docs/backlog.md`. The UX mockup
is `design/instant-replay/index.html`.

## Goal

The user can show a part of a point again, slower and zoomed in, right
after the point. The players on the Points tab and on the Colors and
Transform tab play the replays. Each export includes them.

## Decisions

The decisions of 2026-10-22 and 2026-10-26 are in B-50 in
`docs/backlog.md`. Write each new decision of this file here, at once.

Decided on 2026-10-26 (the author accepted the seek approach of T0 and
the proposals Q1 to Q10 of the code study):

| # | Task | Decision |
|---|---|---|
| 1 | T3 | The live players play a replay with seeks in the source video (T0). During the dip to black, the controller waits for the `playback-restart` event of mpv before the fade in. No second player. |
| 2 | T3, T6 | Q1: the "fade" at the cut into a replay and out of it is a dip to black: the video fades out, then the next picture fades in. It is not a crossfade. Reason: the export joins its passes by stream copy, and the live player has one decoder. The black also hides the freeze at the seek. |
| 3 | T4 | Q2: the effects are Fade (dip to black, the default), Flash (dip to white), and Cut (no effect). |
| 4 | T3, T6 | Q3: the slowed sound has a lower pitch, as a slow tape. The pitch is not kept. |
| 5 | T3, T4 | Q4: while the popup is open, the main player stops and shows a black area with the text "The replay editor uses the video". The list, the timeline, and the other controls of the main window work. Play in the main window does nothing until the popup closes. |
| 6 | T1 | Q5: a replay keeps its source times when the user changes its point. When the part goes out of the allowed window (3 s around the point), the app cuts it to the window. A replay shorter than 0.5 s after the cut is removed. Delete removes the replays of the point. |
| 7 | T3, T6 | Q6: the zoom goes in at the start of the first play and out at the end of the last play. Between two repeats, a short dip to black (0.2 s). |
| 8 | T3, T6 | Q7: the zoom in and the zoom out take 0.4 s of output time each. |
| 9 | T6 | Q8: each comment that is visible during the replay part in the source video shows again during the replay, at the same moment of the part. Its time gets longer by the slow speed. Each repeat shows it again. |
| 10 | T5 | Q9: the spiky edges of a replay block show when the block is 16 px or wider. A narrower block is a plain lime tick, at least 3 px wide. |
| 11 | T6 | Q10: the set cards and the statistics card go after the replays of the last point. |

## Open questions

None. Write each new decision in "Decisions" at once.

## For review

The author was away during the work of 2026-10-26. These choices were made
without a question. Check them, and change them if necessary.

| # | Task | Choice |
|---|---|---|
| R1 | T6 | The badge texts: en and pt "REPLAY", de "WIEDERHOLUNG", es "REPETICIÓN", fr "RALENTI", ru "ПОВТОР", ja "リプレイ", zh_Hans "回放", zh_Hant "重播". |
| R2 | T6 | The export badge has no replay icon, only the text. The app has no icon font for ffmpeg. The sizes are relative to the frame height: the badge is 7% high, 4.5% from the top, and 3% from the right. |
| R3 | T6 | A replay uses the Transform geometry and the colors at the start of its part. A change point inside the part has no effect in the replay. |
| R4 | T6 | With change points, the zoom works on the frame at the output size, not at the source size. Thus a strong zoom looks softer than without change points. |

## Tasks

| Task | Scope | Depends on | Status |
|---|---|---|---|
| T0 | Prototype: the freeze at each seek | — | done |
| T1 | Replay data in `edl.json` | — | done |
| T2 | Replay timeline: source time and play time | T1 | done |
| T3 | Live playback of the replays | T0, T2 | open |
| T4 | Replay editor popup | T1, T3 | open |
| T5 | Replays on the Points tab: timeline, list, button | T2, T4 | open |
| T6 | Export of the replays | T1 | done |
| T7 | Texts, help, and translations | T4, T5 | open |
| T8 | Site, changelog, and version scope | T1–T7 | open |

Status values: `open`, `in-progress`, `done`. When a task is done, write
the test classes in its "Tests" line. Do not remove the task.

### T0 Prototype: the freeze at each seek

The live players play a replay with seeks in the source video (B-50). This
prototype measures how long the picture stops at each seek.

- Status: done. The author accepted the seek approach (decision 1).
- The prototype plays a sample video in a real mpv player with the options
  of `MpvSwingMediaPlayerAdapter`. At the end of each test point, it seeks
  back to a replay part, sets the speed to 0.5 and a zoom crop, plays the
  part, and seeks forward to the point end.
- It measures for each seek:
  - the time from the seek command to the `playback-restart` event of mpv;
  - the largest wall-clock gap between two `time-pos` updates around the
    seek (the time that the picture stops).
- Sample videos: a 4K60 HEVC phone video, a 4K60 H.264 video, and a 1080p
  video (see the dev run notes).
- Variants: the default options, and a demuxer cache that keeps the last
  part of the video in memory (`cache=yes`, `demuxer-max-back-bytes`).
- Done when: the results are in this file, and the author decides if the
  seek approach of B-50 is good enough.
- Code: `src/test/kotlin/org/litvin/media/mpv/ReplaySeekSpikeMain.kt`.
- Results of 2026-10-26 (the author's laptop, NVIDIA RTX 3060, d3d11va,
  6 test points for each video, the replay part 8 s before the point end,
  speed 0.5, zoom 2×). "Freeze" is the time that the picture stops:

  | Video | Keyframe interval | Into the replay (median, max) | Out of the replay (median, max) |
  |---|---|---|---|
  | 4K60 HEVC phone video (`PXL_*.mp4`) | 1.0 s | 233 ms, 242 ms | 149 ms, 262 ms |
  | 1080p60 HEVC (`Timeline 1.mp4`) | 1.0 s | 79 ms, 118 ms | 64 ms, 98 ms |
  | 4K60 H.264 export of the app | 4.2 s | 1375 ms, 1610 ms | 1074 ms, 1511 ms |

  - No frame drops in any run. After the restart, the video plays
    smoothly (the largest gap between two frames is 40 to 145 ms).
  - The freeze comes from the decode from the last keyframe to the seek
    target. Thus it grows with the keyframe interval and the frame size.
  - The demuxer cache (`cache=yes`, 300 MiB back buffer) does not make the
    seeks faster. The variant is not necessary.
  - Phone videos (1 s keyframes) freeze for less than 0.3 s. A dip to
    black of 0.3 s or more (decision 2) hides this freeze. With Cut, the user sees
    a still picture for up to 0.3 s.
  - Videos with long keyframe intervals (for example an export of the app,
    or some cameras) freeze for 1 to 1.7 s. With the dip to black, the
    black stays until the replay is ready.
  - The same freeze occurs at each repeat (decision 7), because a repeat is a seek
    back to the start of the part.
- A seamless cut needs a second player that seeks before the cut, and
  thus two hardware decoders. Do that only if users ask for it.

### T1 Replay data in `edl.json`

- Status: done.
- `PointV1` gets a list `replays` (`ReplayV1`). Each replay has:
  `startMs`, `endMs`, `zoom` (`x`, `y`, `width` as fractions of the frame
  after the Transform tab; null is no zoom), `speed` (0.1 to 1, default
  0.5), `repeats` (1 to 5), `muted`, `effect` (`FADE`, `FLASH`, `CUT`),
  and `showBadge` (the "REPLAY" badge of the mockup, default on).
- The zoom frame has the aspect of the frame. The smallest frame is 0.25
  of the frame (a zoom of 4×), as in the mockup.
- Old projects have no `replays`. An empty list is not written, so their
  `edl.json` does not change.
- `Replays` has the limits and the rules. `EdlIO` fits the replays on read
  and on write: it limits the part to 3 s around the point, the speed, the
  repeats, and the frame, and sorts the replays by start time. An unknown
  effect reads as `FADE`.
- `PointsDispatcher.updatePoint` fits the replays to the new point
  (decision 6). Delete removes the point with its replays.
- Tests: `ReplaysTest`

### T2 Replay timeline: source time and play time

- Status: done. `ReplayTimeline` (and `Replays.playMs`, the time
  constants of the effects and the zoom).
- A new class gives the play order of a project: the source video with
  each replay inserted after the end of its point, and each repeat.
- It converts in two directions: from a play time to a source time (or to
  "replay N, at M ms of the part"), and back.
- It gives the length of each replay in play time (part length / speed ×
  repeats).
- The players (T3), the timeline (T5), and the Export tab (the export
  length) use it.
- Tests: `ReplayTimelineTest`

### T3 Live playback of the replays

- One controller for each player (Points tab and Colors and Transform
  tab). It watches the `time-pos` of the player. When the video reaches
  the end of a point with replays, it:
  1. starts the cut effect (decision 2);
  2. seeks to the start of the replay part;
  3. sets the speed, the sound (decision 4, or muted), and the zoom crop in
     `tr-adjust.hook` (the zoom goes in smoothly, decision 8);
  4. at the end of the part, plays the next repeat or the next replay;
  5. after the last replay, seeks to the point end and sets the normal
     speed, sound, and crop again.
- The zoom frame is inside the crop of the Transform tab. The colors of
  the Colors tab stay on.
- The scoreboard overlay is hidden during a replay.
- The clock of the player shows "Replay". The seek keys and a click on the
  timeline move in play time (T2). The marking keys C, Q, W, and E do not
  work during a replay.
- Pause, frame step, and the speed control of the player work during a
  replay. (The speed control multiplies the replay speed.)
- Tests: —

### T4 Replay editor popup

- A separate window with its own player (decision 5), as in the mockup: the part
  bar with the two edges, the zoom frame on the video, speed, repeats,
  sound, the cut effect (decision 3), Preview, Save, and Cancel.
- The zoom frame uses the mouse logic of the crop frame
  (`CropEditorOverlay`) and is drawn with the editor overlay of mpv.
- A new replay starts 1.5 s before the playhead and ends 1.5 s after it
  (decision of 2026-10-26). The part stays inside 3 s around the point.
- One tab for each replay of the point. "Add" makes one more replay at the
  playhead.
- `I` and `O` set the start and the end of the part. No key opens the
  popup or adds a replay (decision of 2026-10-26).
- A strong zoom on a small video gives a warning ("the picture can look
  soft").
- Tests: —

### T5 Replays on the Points tab: timeline, list, button

- The replay button at the right end of the playback bar, and a replay
  icon on each row of the list. They open the popup for the point at the
  playhead, or for the row.
- A point with replays has a lime REPLAY tag in the list ("2 REPLAYS" for
  two or more).
- The timeline shows each replay as a lime block after its point. The
  width is in proportion to the play time of the replay (T2). The edges
  are spiky (decision 10). The ruler shows source times and skips over the blocks
  (decision of 2026-10-26).
- A click on a block opens the popup on that replay.
- Tests: —

### T6 Export of the replays

- Status: done. A test export of one replay pass (2 s of `D:S.mp4`, 4K60,
  speed 0.5, zoom 2×) gave 4.0 s of video at 60 fps and 48 kHz sound, with
  the zoom in, the zoom out, the dip from black and the dip to white.
- `ExportPassPlanner` gets a replay pass, as the card pass. A video chunk
  ends after each point with replays, and the replay passes follow it. The
  passes join by stream copy, as now.
- A full video export also splits at the end of each point with replays.
- The replay pass reads the part from the source, and applies:
  - the speed (`setpts`) and the sound (decision 4, or silent audio when muted);
  - the zoom: a `perspective` filter that changes with the frame number,
    inside the geometry of the Transform tab, then the scale to the output
    size (decision 8). The crop filter cannot change its size during the
    stream, and a scale up of 4× before a fixed crop is too slow;
  - the colors of the Colors tab;
  - the cut effect at its start and its end (decision 2);
  - the comments (decision 9) and the "REPLAY" badge (`ReplayBadgeAss`),
    in one ASS file for each replay pass. No scoreboard;
  - the frame rate of the source (`fps`), so that the join can copy the
    streams. The sound converts back to the sample rate of the source
    (`SourceSampleRateProbe`).
- The chunk before a replay gets the end of the cut effect (for example a
  fade out over its last frames).
- Full and Only points include all replays. Only favorites includes the
  replays of the favorite points.
- The Export tab shows the export length with the replays (T2).
- Set cards and the statistics card go after the replays (decision 11).
- Keep the end-to-end check small: a short 1080p clip (see the note about
  heavy local runs).
- Tests: `ExportPassPlannerTest`, `FFmpegCommandBuilderReplayTest`,
  `ReplayExportOverlayTest`

### T7 Texts, help, and translations

- All new texts in `messages.properties` and in all app languages.
- A help topic about replays.
- Tests: —

### T8 Site, changelog, and version scope

- The changelog and the site pages that list the features.
- Move B-50 to the version scope file of its release.
- Tests: —

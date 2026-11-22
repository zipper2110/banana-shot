# Automatic point detection in the app (iteration 1)

This file is the plan for the first integration of the lab detection (see `README.md`) into the app.
The aim: the user runs the detection on a new match in the app, and corrects the wrong results with the
normal tools. The app keeps the detection output and the corrections for the lab.

## Decisions (2026-11-23, with the user)

1. **External process.** The app starts the lab script `tools/auto-analysis/detect.py` with the Python of the
   lab venv, and reads a result file. This works only on a PC with the lab (the user's PC). A port of the
   models into the app is a later step. The feature shows only when the lab path is set.
2. **Automatic points are normal points.** The detection adds normal points to the project. The user corrects
   them with the tools of the Points tab. There is no separate review list.
3. **Raw file.** The detection output stays in the project folder, with the settings and the run time of each
   stage. The corrections are the difference between this file and the project at a later time.
4. **Scope.** Rally boundaries; the winner with its confidence; first-serve fault candidates. The lab score
   decoder does not write a score: the app counts the score from the winners, as for manual points.
5. **Two end values.** The detection gives the rally end. A padding setting (before the start, after the end)
   makes the clip boundary.
6. **"By the video" and "by the players".** The winner of a point means what the video shows. The score
   override means what the players counted. The detection never writes a score override.
7. **Reason.** A point can have an optional reason: players' call, replayed, score lost. A winner change with
   a reason is not a detection error. A replayed point is a real rally that does not count.
8. **Settings.** The first settings: padding before and after; the detection threshold; short points from
   serve events on or off; the winner stage and the fault stage on or off (they need the slow ball pass).
9. **Player 1.** The lab knows "near end" and "far end", not the names. Player 1 of the project is the player
   at the near end in the first detected rally. The user can swap the names. The player at the near end of each
   rally comes from the shirt colors (`identity.py`, E22), not from the score decoder.

10. **Branch (the user).** All app changes of this work stay on the branch `auto-detect`, so that the user can
    release from `master` without them. Merge into `master` only when the user says so.

11. **English texts.** The detection UI shows only on a PC with the lab. Its texts are English only and are not in
    the message catalog. They are in the folder `ui/tabs/points/detect`, which `HardCodedTextScanTest` ignores (as
    the developer test tab). Put the texts into the catalog when the detection becomes a feature for all users.
12. **Padding default.** The rally model learned the boundaries from the marked projects of the user. Thus a
    detected clip is already like a manual clip (with the time after the rally that the user keeps). The default
    padding is 0 s before and 0 s after. A negative value makes the clips shorter.

## How it works

- **Switch on.** The file `%APPDATA%/BananaShot/autodetect.json` names the Python of the lab venv, `detect.py`, the
  work folder, and more environment variables (`HF_HOME`). Without the file, the app shows no detection.
- **Run.** Points tab, under the points list: "Detect points". The dialog has the settings, Start, Stop, and
  "Add points". The run continues when the dialog closes or the tab changes. A stopped run continues after its
  last complete stage. The work files are in `<workRoot>/<project folder name>` (large: keyframes, ball data).
- **Result.** `<project>/autodetect/result-<time>.json`: the detected rallies and the run time of each stage.
- **Import.** "Add points" adds the rallies as normal clips with the mark `auto` (`PointV1.auto`), the winners
  (`score.json` outcomes), and in advanced scoring mode a fault clip for each first-serve fault candidate. A rally
  at the time of a clip of the project is skipped, so a second import adds nothing twice.
  `<project>/autodetect/import-<time>.json` records which clip each rally became.
- **Mark in the list.** A dot after the length of an automatic clip: blue, or yellow for a clip to check first
  (a short point, no winner, or a winner confidence under 70 %). The tooltip gives the confidence.
- **Reason.** Edit point dialog: "Score differs from the video" (none, players' call, replayed, score lost), in
  `score.json` `reasons`.
- **Corrections.** `python tools/auto-analysis/corrections.py <project folder>` compares the import record with the
  project now: deleted rallies, moved boundaries, changed winners (split by reason), added clips, fault clips, and
  the stage times. It writes `<project>/autodetect/corrections-<time>.json`.

## Limits of iteration 1

- One video file only (not a match of several files).
- Singles tennis only. The models learned from 4 singles matches of one camera position type.
- A video with no keyframe each second gets one decoded frame each second (slower first stage, about 6x real time).
- The winner uses "player 1 = near end in the first rally". The server and the score are not set.
- The ball stage takes about 0.6 x the video length on the RTX 3060 for a 60 fps video.
- The court detector needs a camera high behind the court, as in the lab matches. For another camera position
  (E23: a low camera) the script finds that the court model is not plausible. Then the result has only the rally
  boundaries, and the dialog shows a warning.

## Tasks

| # | Task | State |
|---|---|---|
| 1 | Lab: `detect.py <video> <work folder>` runs all stages for one video with no marks, writes `result.json` and the time of each stage, prints progress lines | done |
| 2 | Lab: a final rally model trained on all marked singles matches (the leave-one-out models are only for tests) | done |
| 3 | App: the domain package for the detection (settings, runner of the external process, result model, import into the points and the score) | done |
| 4 | App: the raw file and the run log in the project folder; the correction report (difference to the project) | done |
| 5 | App: the "Detect points" action in the Points tab with the settings and the progress | done |
| 6 | App: the mark "automatic" on a point, and the optional reason | done |
| 7 | Tests for the domain parts; one run on a real match | done: tests, and one real run through the app runner (E23) |

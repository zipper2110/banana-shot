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

## Decisions for the user flow (2026-11-23, with the user; iteration 2)

13. **The flow.** The user makes the project, sets the players and the scoring mode, corrects the video (zoom,
    crop, colors, trim), and then clicks "Detect points". The detection runs, asks which player is which, and adds
    the points. The user reviews the points with the normal tools. After the review, the project is a normal
    project.
14. **One action.** "Detect points" runs the detection and adds the points. There is no separate "Add points"
    step that the user must start. If the user closed the dialog during the run, the bar under the points list
    shows the result and continues from the player question.
15. **Which player is which.** After the run, the dialog shows a frame of the first rally and asks: "Who is at the
    near end?" The two answers are the players of the project, with their names and colors. The detection follows
    that player through the end changes (decision 9). A "Swap players" action for all automatic points corrects a
    wrong answer. The first server is not the anchor: the server is unknown in many rallies (71 of 178, E23b).
16. **Basic scoring mode.** A point gets only its winner. A first-serve fault is not a clip, and the clip of the
    point starts at the second serve. A double fault is a point for the receiver.
17. **Advanced scoring mode.** A point gets its winner with no ending kind ("winner only"), and a first-serve fault
    becomes a "Serve fault" clip. The detection of the ending kind is a later step.
18. **Review.** An automatic clip has a dot until the user changes it or accepts it. The list shows the count of
    the clips to review, and has "Accept, next" (key T) and "Finish review". "Accept, next" accepts the selected
    clip and plays the next clip to review. "Finish review" removes all dots. The mark `auto` stays in the project
    file for the corrections report.
    An edit of the clip, or a winner or an ending from the user, also ends the review of that clip.
19. **Detection overlay.** A switch in the Points tab draws the detection data on the preview during playback: the
    court lines, the ball with a trail, the landing points, the serve speed. No video is rendered. It is for the
    full video, and it is a debug view for now.
20. **Settings.** What to detect: "Rallies only" (fast) or "Rallies and winners". With winners: serve faults on or
    off, short points on or off. Rally sensitivity: fewer, normal, more. Time before and after each clip. Winner
    confidence limit. "Inside the trim only": the import skips the rallies outside the trim (the run still reads
    the full video; a shorter run is a later step).

## Decisions for the review window (2026-11-23, with the user; iteration 3)

These decisions replace the review row and the overlay switch of the Points tab (decisions 18 and 19).

21. **Review window.** The review of the automatic points is in a separate window with its own player, as the
    replay editor is. It opens after the import. While it is open, the Points tab shows a note in place of the
    video. The changes go into the project at once.
22. **Bounds bar.** A bar under the video of the window shows the selected point with the time around it. The user
    drags the start edge and the end edge of the point. The keys I and O put the start and the end at the playhead.
    The window has no time inputs.
23. **Two ways to accept.** "Accept, next" (key T) accepts the selected point and goes to the next point to review.
    "Accept all points and close" accepts all points with one click and closes the window. A correction of a point
    (winner or bounds) also accepts that point.
24. **End of the flow.** After "Accept all points and close", the detection flow is complete: no dots, no review
    button. The project is a normal project.
25. **Continue later.** A close with the X, with Escape, or with "Continue later" keeps the review open. The bar of
    the Points tab then shows "Continue review (N)". When all automatic clips are reviewed, the bar shows "Open
    review": the user can open the window again.
29. **The review window is a tool for the developer of the detection, for now.** Its goals: show what the
    detection found in the rally, show the winner and its reasons, let the user correct the winner, and let the
    user write a note about what the detection did wrong. For the selected point, the window shows the facts of
    the rally (rally model, shots, server, fault, end change), the last shot with the decision of the ball rule,
    and the probability from the ball and after the score model. It also shows the shot chain of the rally
    (`chain` in `result.json`, from `shot_chain.py`): one line for each shot (hitter, how the hitter is known,
    in, out, or net, and the place of the bounce), the decision of the chain, and a warning when the score model
    gives the other winner. The chain is the winner evidence of the score model (E29; `CHAIN=0` in the
    environment gives the old rule). A fault clip shows the player at the near end of its point, the
    server, the serve speed, and the shots of the fault candidate (`fault.chain` in `result.json`: the shots from
    1 s before the serve event to 2.5 s after it). These shots have no decision, and a fault clip has no winner.
    The serve of a fault clip is "in" only if it lands in the service boxes (the side of the box is not tested). With the "Shots" switch on, the video has a shot panel at the top right
    (one box of a constant size: the number of the shot, the hitter, the start and end time, the outcome), and
    the timeline has two shot tracks below the bar. The upper track has the color of the hitter. The lower
    track shows the time with a ball track; its color says how the hitter is known (white: hit seen, blue: the
    ball came back, gray: hit not seen). A line at the end of a shot is green for in, red for out, and yellow
    for net. A shot with no seen end can have a predicted end (E32; `predicted` of the shot in `chain`: `t`,
    `end`, `court`, `margin`, `sigma`): the landing of the fit of its last flight, if the 1-sigma error is not
    more than 1 m. A predicted landing nearer to a line than 1.2 m has the end "line" (no decision; gray). The panel then says
    "Predicted: ...", and the line on the timeline is a broken line. A shot that starts more than 1 s after the end of the rally is gray on the two tracks, and the
    panel says "After the point" (the players handle the ball). The
    predicted end is for the display only. The winner evidence does not use it. The window has the "Frame step" toggle of the
    Points tab (key F, the same setting): Left and Right then step one frame while the video is paused. The
    "Overlay" switch (key V) hides and shows all parts of the overlay; the switches of the parts keep their
    state. The notes go to `autodetect/review-notes.json` of the project, with the times of the detected rally.
    The lab reads them to improve the detection. `result.json` has the new fields `lastShot.nearWins`,
    `lastShot.case`, and `lastShot.t` for this.
28. **Picture of the player question.** The question shows a picture of a rally where the pose stage found the
    two players. The player at the near end is bright, and the picture is darker around that player. The picture
    has no frame around the player: a frame looks like a prototype. The question stays "Who is at the near end?", and
    the text says that this player is highlighted: the far player is also in the picture. The
    picture at the start of the first rally is not usable for a low camera: the near player can be out of the
    picture. `detect.py` gives each rally its best picture (`view`).
27. **Near end.** For the selected point, the review window shows "Near end: <player>": the player that the
    detection saw at the near end in that point (`auto.nearPlayer`). A wrong name tells the user that the detection
    has the players wrong, and not only the winner of one point. "Swap players" is next to it. It changes the
    winner and the player at the near end of: this point, this point and the next points (the detection lost the
    players after a change of ends), or all automatic points.
26. **Overlay.** The detection overlay switch is in the review window only. "Review points" in the "Detect points"
    dialog opens the window again at a later time, also after the review is complete.

## How it works

- **Switch on.** The file `%APPDATA%/BananaShot/autodetect.json` names the Python of the lab venv, `detect.py`, the
  work folder, and more environment variables (`HF_HOME`). Without the file, the app shows no detection.
  On a Mac the file is `~/Library/Application Support/BananaShot/autodetect.json`; the setup is in
  `mac-setup.md`.
- **Run.** Points tab, under the points list: "Detect points". The dialog has the settings (decision 20) and one
  action, "Detect points". During a run, the dialog shows only two progress bars (the step and the total), the
  estimate of the time left, and "Stop". The estimate uses the reference time of each stage (a part of the video
  length, from E23b) and the measured speed of the stage that runs. Only the ball stage reports its progress: the
  bar of each other stage moves with the time. The run continues when the dialog closes or the tab changes. A
  stopped run continues after its last complete stage. The work files are in `<workRoot>/<project folder name>` (large: keyframes, ball
  data).
- **Result.** `<project>/autodetect/result-<time>.json`: the detected rallies and the run time of each stage.
  `<project>/autodetect/overlay-<time>.json`: the data of the detection overlay.
- **Player question.** When the run ends, the dialog asks "Who is at the near end?" with the picture of the first
  rally from the work files. If the dialog is closed at that time, the bar shows "Result ready", and the dialog
  asks when the user opens it. A result with no winners gets no question.
- **Import.** After the answer, the app adds the rallies as normal clips with the mark `auto` (`PointV1.auto`), the
  winners (`score.json` outcomes), and in advanced scoring mode a fault clip for each first-serve fault candidate.
  A rally at the time of a clip of the project is skipped, so a second import adds nothing twice. With "Only inside
  the trim of the video", a rally that is not fully inside the trim is skipped.
  `<project>/autodetect/import-<time>.json` records which clip each rally became, and if the players were swapped.
  "Add points" in the dialog does the import of the newest result again, for example with other settings.
  "Swap players" gives each automatic point with no ending kind the other winner.
- **Mark in the list.** A dot after the length of an automatic clip that is not reviewed: blue, or yellow for a
  clip to check first (a short point, no winner, or a winner confidence under 70 %). The tooltip gives the
  confidence.
- **Review window.** It opens after the import, and from "Continue review (N)" in the bar. Left: the video, the
  bounds bar, and the transport. Right: the list of all points, the winner of the selected point, "Accept, next",
  "Delete", "Add point", "Swap players", and "Accept all points and close". A point is not reviewed while
  `auto.checked` is false. Keys: Space play, Up and Down select, Left and Right 1 s (Shift: 5 s), I and O bounds,
  Q, W, E winner, T accept, N add, Delete delete, Escape close.
- **Detection overlay.** The switch "Detection overlay" in the review window shows when the project has overlay
  data. It draws the court lines (blue), the net line (yellow), the ball with a trail (green), the landing points
  (red rings), the serve speed, and the body poses (white skeletons) over the video. The pose stage reads one
  picture each second. A skeleton stays on the video until the next picture has poses.
  The picture of second k is the last video frame of that second (ffmpeg `fps=1:round=down`), so `overlay.json`
  gives a pose the time of that frame, about k + 1 s. Each part of the overlay
  (court, ball, landing points, serve speed, players, shots, outcome, court diagram) has its own switch. "Court diagram"
  draws a top view of the court below the shot panel, with the near end at the bottom. It has a dot for the end of each
  shot of the selected point up to the video time: green for in, red for out or a bounce on the side of the hitter,
  yellow for net (on the net line). A ring with no fill is a predicted end. The newest end has a white ring and its
  shot number. The colors are the colors of the shot tracks. A serve is "in" by the lines of the court, not by its service box. "Ball" draws only the
  ball detections that a flight uses (`ball` in `overlay.json`: the detection nearest to the fitted path, not farther
  than 25 px). Before, it drew the best detection of each frame, and about half of those were false. "Shots" writes the data
  of the flight of the moment: its time range, direction, speed, start point, net height, and landing point in m
  on the court, and it draws the landing point of the fit before the bounce. "Outcome" writes the decision of the
  winner of the selected point near its last shot, and marks the landing point of that shot. "Shots" also draws
  the path of the 3D fit of the flight (`curve` in `overlay.json`): white for the part with detections, yellow
  for the predicted part to the bounce. "Bounces, net" marks the ball events of `overlay.json` (`events`): a
  crosshair at a bounce with its place on the court in m (the color of the court diagram: green for in, red for out or for a bounce on the side of the hitter, yellow when the place is not known), a red diamond at a net hit (`net_events.py`: the moment at which the ball touches the net), and a white ring at a
  hit. "Fit landing" is the old red ring: the place where the 3D fit comes down. The app draws it with libass, at about 30 updates each second.
  The marks follow a crop of the video. They do not follow a rotation.
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
- The court detector works for a camera behind the court: high (3 to 4 m, the lab matches) or low (2 m, E23b).
  If the script finds that the court model is not plausible, the result has only the rally boundaries, and the
  dialog shows a warning.
- The winner accuracy is measured only for a high camera and 60 fps. For a low camera, or for another frame rate,
  the chain runs (E23b), but there is no number.

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
| 8 | App: the user flow of iteration 2 (decisions 13 to 20): one action, the player question, the review, the detection overlay, the new settings | done |
| 9 | App: the review window (decisions 21 to 26), and the run view with the progress bars | done |

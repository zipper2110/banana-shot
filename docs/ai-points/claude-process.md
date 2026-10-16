# Claude process: mark the points and the score of a match

Version 1 (2026-10-15). This file describes the process that Claude used
in run 1. Improve the process in the next sessions. Record each change
here and add a new row to "Version history".

- Results and the step log of run 1: [claude-marking-log.md](claude-marking-log.md).
- Scripts and raw notes of run 1: [claude-run-1/](claude-run-1/).
- Target boundaries and hard cases: [ai-points-trial.md](ai-points-trial.md),
  sections 4.3 and 4.4.

## 1. Inputs

- The source video (run 1: `D:\PXL_20261001_130801998.mp4`, 4K, 60 fps,
  HEVC, a keyframe each 1 s).
- From the user: the sport, the player names, the team colors and the
  score rules.
- Optional, for the evaluation only: a project that the user marked by
  hand. Do not use its points or outcomes to make the new project.

## 2. Target boundaries

The full rule is in [ai-points-trial.md](ai-points-trial.md) 4.3 and 4.4.
In short:

- Start: at the ball toss. At the latest, just after the serve hit.
- End: the first invalid bounce (for example an out), plus about 3 s.
- First-serve fault: a parameter. "Skip" (the user's choice) starts the
  point at the second-serve toss. "Include" starts it at the first-serve
  toss.

Run 1 did not use this rule yet. Run 1 ended a point when the players
relaxed, and it marked most fault serves as separate points.

## 3. Tools

- ffmpeg with CUDA (`-hwaccel cuda`). GPU: RTX 3060 Laptop.
- A Python venv with numpy, scipy and pillow.
- Claude reads the JPG sheets as images.
- Make one GPU decode at a time. Two decodes at the same time slow each
  other down.

## 4. Points: the steps

Run all commands from the work directory (for example a scratchpad
folder `work/`). Keep the video on the local disk.

### 4.1 Extract the audio (1 min)

```bash
ffmpeg -v error -y -i "D:/PXL_20261001_130801998.mp4" -map 0:a:0 -ac 1 -ar 16000 audio16k.wav
```

### 4.2 Find the loud near hits (5 min)

```bash
venv/Scripts/python hits.py audio16k.wav env.npy 12 hits.csv
venv/Scripts/python nearhits.py env.npy nearhits.txt
```

- `hits.py`: a band-pass filter from 1.5 to 7 kHz, the energy each 5 ms,
  and the background as a 1 s median. It saves the envelope to `env.npy`.
- `nearhits.py`: keeps only loud peaks (10 dB over the background and a
  rise of 10 dB in 20 ms). Run 1 found 558 near hits.
- The hits of the other courts are also in the audio. Thus use the near
  hits only as a marker on the frames. Do not find points from the audio
  alone.

### 4.3 Extract the frames (about 10 min, plus 1 h for 4 fps)

Keyframes only, 1 frame for each second (frame n is at t = n − 1 s):

```bash
ffmpeg -v error -y -skip_frame nokey -hwaccel cuda -i "D:/PXL_20261001_130801998.mp4" -vf "scale=640:-2" -fps_mode passthrough -q:v 4 -frame_pts 0 kf/%05d.jpg
```

A crop of the far half of the court, from the keyframes:

```bash
ffmpeg -v error -y -skip_frame nokey -hwaccel cuda -i "D:/PXL_20261001_130801998.mp4" -vf "crop=3072:1000:384:0,scale=600:-2" -fps_mode passthrough -q:v 4 far1/%05d.jpg
```

The full frame at 4 fps (frame n is at t = (n − 1) / 4 s). It takes about
1 h and gives little more than the keyframes:

```bash
ffmpeg -v error -y -hwaccel cuda -hwaccel_output_format cuda -i "D:/PXL_20261001_130801998.mp4" -vf "fps=4,scale_cuda=640:360,hwdownload,format=nv12" -q:v 4 f4/%06d.jpg
```

The crop values are for the camera position of run 1. Find new values for
a new video.

### 4.4 Make the review sheets (5 min)

```bash
venv/Scripts/python dual.py d1 0 6921 1 6 4 400
```

- One "dual" sheet is 6 x 4 tiles, 24 s of video.
- Each tile shows the far crop above the full frame, with a time label.
  A red "H" shows a near hit within ±0.5 s.
- Other sheet types: `sheets.py` (keyframes only) and `sheets4.py` (4 fps,
  for a close look at one point).

### 4.5 Review the sheets and write the notes (about 6 h)

Claude reads each sheet in time order. For each point, Claude writes one
line in `points.txt`:

```text
M:SS.s M:SS.s side winner conf note
5:26.0 5:30.5 D Y low  toss 5:26, H 5:27, rally to ~5:30
```

- Start: the serve toss, or the first "H" of the serve.
- End: the last visible hit, or the moment the players relax.
- side: `D` (deuce, right) or `A` (ad, left), or `?`.
- winner: the team letter, or `?`. conf: `low`, `med` or `high`.
- Lines that start with a space or "(" are comments. Lines that start
  with `G`, `S2G` or `===` are game and set headers.
- Signals that a point is in play: the serve toss, the near net player
  in a low ready stance, the "H" marks, the players run.
- Signals that a point has ended: players walk, pick up balls, turn
  away, or meet at the net.
- For an unclear point, make a 4 fps sheet with `sheets4.py`, or a 4K
  far crop at 5 fps with `endhr.sh`.

### 4.6 Check the notes with the game structure

- The serve side alternates in a game. The first point of a game is from
  the deuce side.
- A game has 4, 5 or 6 points, or an even count of 8 or more.
- The server team changes after each game. The teams change ends after
  each odd game.
- A break in the pattern shows a missed point or a false point. Look at
  that place again.

### 4.7 Write the project (1 min)

```bash
venv/Scripts/python mkproj.py points.txt "$APPDATA/BananaShot/projects" "<new project name>" "<project dir with the names and rules>"
```

- The script reads only the lines that start with two times
  (`M:SS.s M:SS.s`). It stops if two points overlap.
- It writes `<name>.trproj`, `edl.json`, `score.json` and
  `adjustments.json` (identity values).
- It copies the names, colors, scoreboard and rules from a given project.
- Known limits: the source video path is fixed in the script, and the
  winners are not written (all outcomes are `NONE`).
- Never write into the user's hand project.

### 4.8 Evaluate (1 min)

```bash
venv/Scripts/python compare.py "<new project dir>" "<hand project dir>"
```

- A match: the two points overlap by 50 % or more of the shorter point.
  Each point matches only once.
- The output: recall, precision, the start and end errors (median and
  p90), and the missed and extra points by time.
- Run 1: recall 91.8 %, precision 80.4 %, start error median 1.2 s, end
  error median 2.5 s.

## 5. Score: the steps

Run 1 tried this only for 0:00 to 38:24. Then the user decided to mark
the points only.

1. Look at the end of each point: a 4 fps sheet of the last 5 s
   (`sheets4.py`) and a 4K far crop at 5 fps (`endhr.sh`).
2. Look for the signals after the point:
   - A high-five or a talk of a pair at the net: often the winners.
   - A player who picks up the ball or turns away: often the losers.
3. Use the game structure. The server team changes after each game. Thus
   the next server and the point count of a game limit the possible
   winners.
4. Write the winner and the confidence in `points.txt`.

Result: the winner was known for 13 of 69 points, most with low
confidence. The reasons:

- The ball is too small at 1 to 4 fps. The end of a rally falls between
  two frames.
- The camera does not show the near baseline. An out on the near side is
  not visible.
- The near players often leave the frame.

The score step is not usable in version 1. It needs a ball tracker that
finds the bounce position.

## 6. What did not work

- Points from the audio alone: the hits of the other courts mix with the
  real hits.
- Speech recognition (faster-whisper) for the score calls: the hall has
  music, and the players do not call the score loudly. The output was
  hallucinations.
- Motion energy from frame differences: players also walk between
  points, and the near players leave the frame.
- A guess of a match boundary from a change of the team colors: run 1
  saw two matches, but the video has one match.

## 7. Next improvements

The full list is in [claude-marking-log.md](claude-marking-log.md),
"How to improve the match analysis". The first candidates:

1. A pose model on the 1 fps keyframes finds the serve toss (a player at
   the baseline with a wrist above the head). This gives the start of
   each point automatically.
2. A ball tracker at 30 fps, only in a window after each rally, finds
   the last bounce. This gives the end of each point and the data for
   the winner.
3. The stereo audio separates the hits on this court from the hits on
   the other courts.
4. The game-structure checks (4.6) as code, to show the places to
   review.
5. Claude reviews only the doubtful places, not all sheets.

Licenses: Ultralytics YOLO is AGPL. Use it for tests only. RTMPose and
YOLOX are Apache 2.0 and can go into the app.

## Version history

| Version | Date | Change | Result on the run 1 video |
|---|---|---|---|
| 1 | 2026-10-15 | Review by eye of 1 fps "dual" sheets, with the near hits from the audio. | Recall 91.8 %, precision 80.4 %, no winners, about 7 h. |

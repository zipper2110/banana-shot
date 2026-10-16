# Log: Claude marks and scores a full match

Started on 2026-10-15. This file records the approaches that Claude
selected and the steps that Claude did. Use it to see what worked and to
improve the approach. It is related to the trial plan in
[ai-points-trial.md](ai-points-trial.md). The process as a repeatable
procedure is in [claude-process.md](claude-process.md).

## Task

- Video: `D:\PXL_20261001_130801998.mp4` (4K, 60 fps, HEVC, 1 h 55 min,
  AAC stereo 48 kHz).
- Goal: make a BananaShot project with all points marked and scored. The
  points and the score must be precise.
- Ground truth: the user marked this video by hand in the project
  "Dima-Yana = Iliya-Marina". Claude does not use these marks or outcomes
  to make the new project. Claude uses them only at the end, to measure
  the result.
  - Exception: Claude read the score rules of that project (doubles,
    best of 3 sets, 6 games, tiebreak at 6:6, advantage) and the player
    names. A user gives these values to the app in all cases.
  - Claude saw the first 20 point times and the first 50 outcomes by
    accident when it read the file format. Claude does not use them.

## Setup facts

- Indoor hard court. The camera is on a fixed stand behind one baseline,
  at about head height. The near half of the court is large. The far
  players are small (about 20 to 40 px high at 640 px width).
- Doubles. One team: a man in a green shirt and a woman in black. The
  other team: a man in a yellow shirt and a woman in white or pink.
- Other courts are in the same hall. Their ball hits are in the audio.
- Keyframes are at each 1 s (GOP = 60 frames). Thus a decode of
  keyframes only (`-skip_frame nokey`) gives 1 frame for each second at a
  very low cost.

## Steps

| # | Step | Result | Time |
|---|---|---|---|
| 1 | Read the project format (`*.trproj`, `edl.json`, `score.json`, `adjustments.json`). | A point has `id`, `startMs`, `endMs`, `favorite`. `score.json` has `outcomes` (`P1`, `P2`, `NONE`), names, rules, `serverMarks`. | 2 min |
| 2 | Extract the audio: mono, 16 kHz WAV, with ffmpeg. | 220 MB file. | 1 min |
| 3 | Find ball hits in the audio: band-pass 1.5 to 7 kHz, energy each 5 ms, peaks above a 1 s median background. | Poor. Only 458 peaks in 2 h with strict values. With loose values, the peaks of other courts and the echo of the hall mix with the real hits. Audio alone cannot find the points here. Keep it as a helper only. | 5 min |
| 4 | Extract 1 keyframe for each second at 640 px with CUDA decode. | About 6,900 JPG files, about 8 min. | 8 min |
| 5 | Look at contact sheets (6 x 8 frames, 320 px each, time label). | The warm-up and the play are visible. At 320 px the far players are small, but the posture shows the state. | - |
| 6 | Try speech recognition (faster-whisper, model "small", CPU) to find score calls. | Failed. The voice filter finds no speech. Without the filter, the model writes hallucinations ("music", subtitles credits). The hall has music, and the players do not call the score loudly. Dropped. | 10 min |
| 7 | Motion energy from a 10 fps, 480 px gray copy (frame difference in the far half and the near half). | Not useful alone. Players walk between points, and the near players often go out of the frame. | 5 min |
| 8 | 1 fps review in sheets of 6 x 4 tiles (400 px, 24 s for each sheet), from 5:00 to 13:35. | Points with a serve from the left (ad) side are clear: the toss is visible. A near serve from the right (deuce) side is often out of the frame. Short points and the end of a rally are often not clear at 1 fps. | 20 min |
| 9 | Extract 4 fps at 640 px for the full video (CUDA decode, `scale_cuda`). | About 1 h for the full video. Use 2 fps for the review pass and 4 fps for the end of each point. | 60 min |
| 10 | Mark a red "H" on each frame that has a loud near-court hit in the audio (peak 10 dB above the 1 s median and a rise of 10 dB in 20 ms). | Useful. It shows the serve and the hits of the near players. It does not show the far hits. | 5 min |
| 11 | For the winner: a 4 fps sheet of the last 5 s, and a crop of the far half from the 4K source (5 fps, 960 px). | The crop shows the far players and the ball well. But the winner is often still not clear: the camera does not show where the ball lands on the near baseline, and the near players leave the frame. | - |
| 12 | Review speed of games 1 and 2 (6.5 min of video, 16 points). | About 60 min of work. The full match at this speed is about 15 to 20 h. Point boundaries are clear for most points. The winner is clear for about half of the points. | 60 min |

| 13 | Far-court crops from keyframes only (1 fps, 600 px), shown above the full frame ("dual" sheets, 6 x 4 tiles, 24 s each). | The best view so far. The far players and their serve toss are clear. A second full-video GPU decode at the same time as the 4 fps decode made both slow; one decode at a time is better. | 10 min |
| 14 | Review with dual sheets from 12:00 to 38:24 (set 1 and the start of set 2). | 69 points marked in 0:00 to 38:24. Set 1 seems to end at 31:36 after 8 games (all four players meet at the net). The winner is known for 13 points only, most with low confidence. | about 2 h |
| 15 | Checkpoint with the user. Decision: mark the points only, with no winners. | All outcomes are `NONE`. | - |
| 16 | Review with dual sheets from 38:24 to the end (115:21). | 250 points in total. The last point ends at 114:33. The players shake hands at 114:44 to 114:51. | about 4 h |
| 17 | Write the project "PXL_20261001 Claude points" with `mkproj.py`. Names, colors, scoreboard and rules come from the hand project (a user gives them in all cases). | 250 points, all outcomes `NONE`, identity adjustments. The hand project is not changed. | 1 min |
| 18 | Compare with the hand project (`compare.py`). A match = overlap of 50 % or more of the shorter point. | See "Result". | 1 min |

## Result

| Metric | Value |
|---|---|
| Hand points | 219 |
| Claude points | 250 |
| Matched | 201 |
| Recall | 91.8 % (18 points missed) |
| Precision | 80.4 % (49 extra points) |
| Start error (Claude − hand) | median −0.1 s, median of absolute 1.2 s, p90 of absolute 7.9 s |
| End error (Claude − hand) | median +1.8 s, median of absolute 2.5 s, p90 of absolute 6.4 s |
| Median point length | hand 7.8 s, Claude 11.5 s |
| Winners | none (by decision) |
| Total work time | about 7 h |

What the errors show:

- Most extra points are short (4 to 6 s) and come a few seconds before
  the next point. They are mostly fault serves, let serves, or short
  breaks that Claude marked as a separate point. The hand marks do not
  include fault serves. 43 of the 49 extra points do not touch any hand
  point.
- A test rule shows this: drop a Claude point of 6 s or less when the
  next point starts 8 s or less after it. Then precision goes to 88.0 %
  and recall stays at 90.4 %. This rule is tuned on the ground truth, so
  it is not in the project. Use it as a start value for the next run.
- The extra points are dense from 59:00 to 106:00 (36 of 49). In that
  part the near team often serves from the right side, out of the frame.
  Claude marked a point at each pause in the posture.
- The hand project starts at 3:29. Claude took 3:29 to 5:26 as warm-up and
  missed one point there.
- The hand project has no gap of more than 2 min. Claude thought that
  79:36 to 82:30 was a break between two matches. The hand project has 6
  points in it. Thus it is one match, and the "second match" idea was
  wrong. Do not guess a match boundary from a change of the team colors
  or of the players' positions.
- Claude ends a point 1.8 s later than the user. The user ends a point at
  the last hit. Claude ends it when the players relax.

## Status at the checkpoint (38:24 of 115:21)

- Point boundaries: good. Start = the serve toss (first serve). End = the
  last visible hit or the moment players relax. Error about 1 to 2 s.
- Winners: poor. At 1 fps the end of the rally is between two frames, the
  ball is too small, and the near players leave the frame. The signals
  that work: a high-five or a talk of a pair at the net (often the winners),
  a player who picks up the ball or turns away (often the losers), and the
  end of a game (change of ends or of the server).
- Cost: about 3 h of work for one third of the match. The full match needs
  about 6 to 7 h more, and the winners still stay unknown for most points.
- Raw notes and the scripts are in `claude-run-1/`. The final notes are
  in `claude-run-1/points-raw.txt`.

## Useful rules found

- The serve side alternates in a game: point 1 from the deuce side
  (right), point 2 from the ad side (left), and so on. A gap in this
  pattern shows a missed point or a false point.
- A game has 4, 5, or 6 points, or an even count of 8 or more (deuce).
  Thus the count of serves in a game is a check.
- The teams change ends after games 1, 3, 5, and so on. The server team
  changes after each game.
- When the near team receives, the near net player stands still in a low
  ready stance until the serve. The stance shows the wait for a serve.

## How to improve the match analysis

Points (boundaries):

- Mark fault serves apart from points. A fault serve is short (under
  6 s) and the same server serves again a few seconds later. Join it to
  the next point, or drop it. This one rule gives most of the precision
  gain (see "Result").
- Use the target boundaries in
  [ai-points-trial.md](ai-points-trial.md) 4.3: start at the ball toss,
  end at the first invalid bounce plus about 3 s. The hand marks of this
  video end earlier (at the last hit), so do not tune the end padding on
  them.
- Use a higher frame rate only near the start and the end of a point.
  Use 1 fps keyframes for the overview. A full 4 fps decode costs about
  1 h and gives little more than the keyframes.
- Make one GPU decode at a time. Two decodes at the same time slow each
  other down.
- Use a far-court crop above the full frame (the "dual" sheet). It was
  the best view in this run.

Winners and score:

- A ball and player detector (for example a small YOLO model on the GPU)
  is necessary. A review of 1 fps sheets by eye cannot see the ball, and
  the winner stays unknown for most points.
- Track each team by its shirt color. Then the positions show the server,
  the serve side and the change of ends.
- Use the game structure as a check: the serve side alternates in a
  game, the server team changes each game, and the teams change ends
  after each odd game. A gap in the pattern shows a missed or a false
  point. With the serve side and the server, the count of points in a
  game limits the possible winners.
- Use the signals after the point: a high-five or a talk of a pair at
  the net shows the winners. A player who picks up the ball or turns
  away is often on the losing team.
- Ask the user for a few known scores (for example the set scores). They
  fix the game count and remove many wrong choices.

Camera and input:

- The camera does not show the near baseline and the near right side.
  The near serve from the right side is often out of the frame. Tell the
  user to place the camera higher and further back, so that the full
  court is in the frame.
- Speech recognition did not work in a noisy hall with music. The
  players do not call the score loudly. Do not use it.
- Audio hits alone cannot find the points when other courts are near.
  Use loud near hits only as a marker on the frames.

Cost:

- This run took about 7 h of work for 115 min of video, for the point
  boundaries only. An automatic detector is necessary for a practical
  tool.

## Approaches considered

- Audio hits only: rejected after step 3 (noise of the other courts).
- Look at all frames at 1 fps (Claude reads the images): slow, but it does
  not depend on a trained model. Use it to find the points.
- Speech: the server often calls the score aloud before the serve. If the
  calls are audible, they give fixed checks for the score.
- Score rules: the server team changes after each game, and the teams
  change ends after each odd game. These rules help to find the game
  boundaries and to check the count of points.

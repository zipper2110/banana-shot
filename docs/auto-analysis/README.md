# Auto-analysis lab: automatic tennis video analysis

Started on 2026-11-16. This folder is the journal of an exploration. It is
not a backlog item and not a product decision. Read this file first. Add
each new experiment to "Experiment log" at once, also when it fails. The
goal is that nobody tries the same thing two times.

Related older work: [../ai-points/](../ai-points/) (Claude marked a match
by eye on 2026-10-15; brainstorm of the product shapes).

## 1. Goal and scope

Rival apps (SwingVision and others, see
[../marketing/competitors.md](../marketing/competitors.md)) analyze a
tennis video automatically. We explore how to do the same.

Wanted features, in the order of priority:

1. Rally detection: the start and the end of each point.
2. Detection of a faulty first serve.
3. The winner of each rally (and thus the score).
4. Shot detection: stroke type, ball speed, ball landing point on a court
   diagram.
5. Other statistics on top.

Scope now (user decision, 2026-11-16):

- Local processing on the desktop of the user. Cloud processing is a later
  topic.
- Prototypes only. They can be separate scripts or a separate tab. The
  integration into the app screens is out of scope.
- Hardware target: all machines if possible. If not: Apple silicon Macs,
  or Windows with a dedicated GPU.
- Low budget. Lab machines: a Windows laptop (i7-11800H, 16 GB RAM, RTX
  3060 Laptop 6 GB) and a MacBook Air M1.

## 2. Files

| Path | Content |
|---|---|
| `docs/auto-analysis/README.md` | This journal: facts, hypotheses, experiment log, decisions |
| `docs/auto-analysis/research/ball-court-tracking.md` | Research: ball trackers, court detection, bounces, 3D and speed |
| `docs/auto-analysis/research/rally-events-strokes.md` | Research: rally segmentation, event spotting, strokes, pose, audio, foundation models |
| `docs/auto-analysis/research/products-and-open-source.md` | Research: how the rival products work, open-source projects, inference runtimes |
| `tools/auto-analysis/` | The lab scripts (Python) |
| `D:\bananashot-lab\` | Lab data, not in git: `venv`, `data/<match>/`, model cache `hf/` |
| `D:\bananashot-lab\demo\index.html` | Demo page of the prototypes (`demo_report.py`): numbers, timelines, point tables, and clips with the ball analysis (`demo_clip.py`) for the 4 singles matches |

The research reports come from web research by subagents on 2026-11-16.
They mark each claim as verified or not verified. Check a number in its
source before a decision depends on it.

## 3. Assets

### 3.1 Marked matches (ground truth)

The author's own projects in `%APPDATA%\BananaShot\projects`. Each has the
point boundaries (`edl.json`) and the winner of each point (`score.json`,
`P1` or `P2`). `tools/auto-analysis/matches.json` maps the match ids to the
videos and projects.

| Id | Video | Type | Points | Notes |
|---|---|---|---|---|
| m1001 | `PXL_20261001_130801998` | Doubles, indoor hall | 219 | Other courts near. Low camera: the near baseline is not in the frame |
| m1015 | `PXL_20261015_115716442` | Singles | 98 | |
| m1016 | `PXL_20261016_140220340` | Doubles | 219 | |
| m1017 | `PXL_20261017_090443739` | Beach tennis, doubles | 97 | Another sport: no bounce, sand court |
| m1020 | `PXL_20261020_162033855` | Singles | 153 | |
| m1021 | `PXL_20261021_060501904` | Singles | 162 | |
| m1030 | `PXL_20261030_130316107` | Singles, outdoor hard court | 153 | Full court in the frame |
| m0913, m1113 | | ? | 0 | Not marked. Use for blind tests |

Total: about 1100 points with winners.

Limits of the ground truth:

- The marks skip a first-serve fault: the point starts at the second
  serve. Thus there is no label "fault". We must add fault labels for the
  fault experiments.
- The end padding differs between the matches (see
  [../ai-points/ai-points-trial.md](../ai-points/ai-points-trial.md) 4.3).
- `P1` and `P2` are players, not court ends. The side of each player
  changes with the game count. A winner experiment must compute the side
  from the score.
- No labels for hits, bounces, stroke types, or speed.

### 3.2 Video facts

- 4K (3840 x 2160), 60 fps, HEVC, about 65 Mbit/s, 30 to 66 GB for each
  match. Stereo AAC audio, 48 kHz.
- One keyframe each second (GOP of 60 frames). No B-frames: each frame
  refers only to earlier frames.

## 4. Measured facts about the decode cost (E0, 2026-11-16)

The decode of the 4K60 video is the main cost. Three decode tiers exist on
the Windows lab machine (ffmpeg 8.1, `-hwaccel cuda`):

| Tier | What | Command key | Speed | Time for a 94 min match |
|---|---|---|---|---|
| T1 | Keyframes only, 1 frame each second | `-skip_frame nokey` | About 40x real time | 2.3 min |
| T2 | A burst of 6 successive frames (100 ms) each second | input option `-bsf:v noise=drop='gte(mod(n\,60)\,6)'` | About 14x real time | About 7 min |
| T3 | All frames | `-hwaccel_output_format cuda` and `scale_cuda` | 6x real time (E0b) | About 16 min |

- T2 is a new trick. The bitstream filter drops the packets of each GOP
  after the first 6. The decoder gets only the keyframe and 5 P-frames.
  The frames are clean, because the video has no B-frames. T2 gives real
  60 fps motion (ball flight, 3-frame tracker input) once each second, for
  a fraction of the full decode cost.
- T2 assumes that a keyframe is at each 60th packet. Check this for each
  file (count of keyframes against count of packets), or drop by the
  packet time.
- The audio extraction takes about 1 min for a match.
- Run one GPU decode at a time.

Design rule from these facts: use T1, T2, and the audio for the full
match. Use T3 only inside the rallies (about 25 to 30 % of a match).

Frame times (found in E20, 2026-11-22): the phone videos are not exactly
60 fps. m1030 has 59.88 fps on average, the other matches about 60.005. In a
long decode pass, "frame number / 60" drifts: 11 s at the end of m1030 and
0.9 s at the end of m1021. Rule: for a pass longer than some minutes, take
the time of each frame from the packet times (`frame_times.py`, ffprobe
with no decode, cached in `<match>/pts.npy`). Short windows (one point) are
not affected.

## 5. Ideas and hypotheses

Each hypothesis has an id. The experiment log refers to these ids.

### 5.1 Rally detection

| Id | Hypothesis | Cost |
|---|---|---|
| H1 | Audio hit features (energy peaks in 1.5 to 7 kHz) with a time context of ±8 s find most rallies. Other courts make false hits. | T-audio, CPU |
| H2 | Embeddings of the 1 fps keyframes from a frozen image model (DINOv2 ViT-S), with a small classifier trained on the marked matches, find the rallies on a new match and a new camera position. | T1, small GPU or CPU |
| H3 | Player positions and poses in court coordinates (meters) are a camera-independent signal. A classifier on them moves between matches better than H2. A wrist above the head at the baseline shows the serve. | T1, pose model |
| H4 | "A ball is in flight in the court area" each second, from a T2 burst (frame differences, or a 3-frame ball tracker), separates rallies from dead time better than players do. | T2 |
| H5 | A fusion of H1 to H4 with a small sequence model (GRU or TCN) reaches the go rule of the trial plan: recall 97 %, precision 90 %, boundary error 1 s. | CPU |
| H6 | The stereo level difference of a hit tells this court from the next courts. | T-audio |

### 5.2 First-serve fault

| Id | Hypothesis |
|---|---|
| H10 | A fault is a serve event with at most one more hit, and the same player serves again from the same side within about 20 s. A serve detector (pose) and a hit count are enough. No ball track is necessary. |
| H11 | With a ball track: the first bounce after the serve is outside the service box, or the ball stops at the net. More exact, but it needs T3 and a court model. |

### 5.3 Rally winner and score

| Id | Hypothesis |
|---|---|
| H20 | Ball track plus court model: the last bounce (in or out, which half) and the last hitter give the winner. This is the method of SwingVision. |
| H21 | Score grammar: the serve side (deuce or ad) alternates in a game, the server changes each game, the players change ends after each odd game. A Viterbi search over legal score sequences joins weak evidence for each point into a consistent score. If the user gives the final set scores, the search gets much narrower. No published work does this from video. |
| H22 | The last hitter is audible: a near hit is louder than a far hit. With the pose of the players at the hit time, the last hitter is known without a ball track. |
| H23 | A small classifier on the last seconds of the point and the seconds after it (body language, who gets the ball) predicts the winner side above chance. Use it as one evidence input for H21. |
| H24 | The next serve tells the result of the game: if the server changes, the game ended. The count of points in a game limits the possible winners (4 points: one player won all). |

### 5.4 Shots

| Id | Hypothesis |
|---|---|
| H30 | A heatmap ball tracker (WASB-SBDT, MIT) on a court crop works on our video after a fine-tune on some hundred own frames. |
| H31 | With a fixed camera, the court model comes from one frame: the user clicks 4 corners, or a keypoint model proposes them. |
| H32 | Hits and bounces come from the 2D track (direction changes) plus the audio hits. |
| H33 | Stroke type comes from the pose sequence of the hitter around the hit time (side of the ball against the body, wrist path). |
| H34 | Ball speed comes from a ballistic fit between the hit and the bounce (gravity, drag). SwingVision reports the average speed from contact to bounce. |

### 5.5 Where help from the user can lift the result

The user decided (2026-11-22): do not build these steps now; record where
they can help. Order: largest expected gain first.

| Id | Help from the user | Expected effect | Evidence |
|---|---|---|---|
| U1 | Review of the detected rallies: delete a false rally, add a missed point | The score decoder needs the right list of points. Winner accuracy goes from the fully automatic level to the level on marked points | E21b against E16e: 78 % with detected rallies, 82 % with marked points |
| U2 | Fix of a wrong point winner during the review | The decoder runs again with the fixed point as a fact; the score after it is right again. One wrong winner now breaks the games count for the rest of the set | Demo report: score in the game 75 % right, with the games count 27 % |
| U3 | First server and the player at the near end at the start (2 clicks) | Removes the 4 start hypotheses of the decoder | E14 |
| U4 | Final score of each set (typed by the user) | A strong constraint for the decoder; not tested | - |
| U5 | 4 court corners on one frame when the automatic court model fails or after a camera move | All 3D geometry depends on the court model (m1030 after 3700 s) | E19, E10b |
| U6 | Select the same player in some rallies (as SwingVision does) | Player identity when the shirt colors are similar or a player changes the shirt | E11, E14e |
| U7 | The user clicks the 4 corners of the court (or 4 line crossings) in one frame | The court model for a camera position that the detector does not know (E23). All ball stages (winner, fault, speed, landing point) need it | E23: wrong model on the low-camera video |

## 6. Experiment log

Metrics (same as `docs/ai-points/claude-run-1/compare.py`): a predicted
point matches a marked point if the overlap is 50 % or more of the shorter
one. Recall = matched / marked. Precision = matched / predicted. Start and
end error in seconds. AUC = quality of the per-second curve. All tests are
"leave one match out": the test match is never in the training set.

| Id | Date | What | Result | Verdict |
|---|---|---|---|---|
| E0 | 2026-11-16 | Decode tiers T1 and T2 (section 4) | T1 40x, T2 14x real time | Use |

| E1 | 2026-11-16 | H1: audio hit features only (`feat_audio.py`), gradient boosting, context ±8 s | AUC 0.893, recall 69.4 %, precision 84.1 % | Useful as one input. Not enough alone |
| E2 | 2026-11-16 | H2: DINOv2 ViT-S embeddings of the keyframes only (`feat_dino.py`), PCA 24 | AUC 0.862, recall 72.6 %, precision 85.7 % | Useful as one input. Fails on a new venue (m1030: AUC 0.67) |
| E3 | 2026-11-16 | E1 + E2 together | AUC 0.924, recall 81.8 %, precision 90.1 %, start error 0.85 s, end error 1.0 s (medians) | Baseline to beat |

| E4 | 2026-11-16 | H31: court model from rough line guesses (`court_fit.py`). Stage 1: 5 guessed lines snap to white pixels. Stage 2: chamfer refinement of the 4 corners against all court lines | The overlay fits the court lines on 6 of 6 tennis matches (checked by eye, `data/court/all_fit.jpg`). Mean line distance 1.7 to 4.1 px (8.2 px with a hard shadow, still a good fit) | Use. Rough guesses (error up to 15 px) are enough |

| E6 | 2026-11-16 | H5 (part): same features as E3, but a bidirectional GRU (2 layers, 64 units, `seq_eval.py`) in place of gradient boosting with a context window | AUC 0.929, recall 86.8 %, precision 86.3 %, start error 0.52 s, end error 0.89 s. Best match m1020: 98.0 % / 95.5 %. Worst tennis match m1030: 71.2 % / 72.7 % | Use the GRU as the default model. The boundaries are better |

| E6b | 2026-11-16 | H1 again: log-mel audio features (`feat_mel.py`: 64 bands, mean, max, and onset strength for each second) in the GRU | Audio only (mel): recall 84.3 %, precision 83.6 %. Hit counts + mel: 84.4 % / 83.6 %. Hit counts + mel + DINO: AUC 0.935, recall 88.0 %, precision 86.8 %, start error 0.50 s, end error 0.85 s | New best. Audio alone is almost as good as audio with the keyframes. The first verdict "audio is not enough alone" came from weak features (hit counts) |
| E4b | 2026-11-16 | Court model over time (`court_track.py`): fit again each 60 s, start from the neighbor window | The camera moves during a match. m1030: small moves of 20 to 40 px some times, and one large move near minute 60. The tracker follows the small moves. It fails on the large move | Small moves: use. Large moves: need a detection with no start guess (task given to a subagent: `court_auto.py`) |
| E7 | 2026-11-16 | H4: ball-in-flight tracklets from T2 bursts, no model (`burst_ball.py`). Test on 5 min of m1030 | A ball-colored tracklet is in 51 % of the rally seconds and in 13 % of the other seconds. A fast 4-frame tracklet: 33 % against 8 % | Promising as one input. Full-match test is open (see E8) |
| E8 | 2026-11-16 | H4 on full matches: ball features (`feat_ball.py`) alone and added to the GRU. 6 tennis matches | Ball only: AUC 0.746, recall 65.1 %, precision 61.8 % (singles 0.79 to 0.89 AUC, doubles 0.66, m1030 0.60). Audio + mel + DINO on the same 6 matches: AUC 0.955, recall 90.5 %, precision 88.7 %, start error 0.48 s, end error 0.74 s. With ball features added: 88.8 % / 88.3 % | H4 not confirmed in this form. The classical ball tracklets add nothing to the fusion. Do not spend more time on frame differences; test a learned tracker (E15) |
| E3b | 2026-11-16 | Error analysis of the best model (audio + mel + DINO, 6 matches) | 116 extra points. 77 of them end less than 15 s before a marked point start, and 65 of those are 7 s or shorter: probably first-serve faults that the marks skip. The missed points are short (median 6 s) | The real precision is higher than the number. But the model learns to hide most faults (77 candidates for 1004 points; a real fault rate is 30 to 40 %), so it is not a fault detector |
| E0b | 2026-11-16 | T3 decode speed, measured with an idle GPU | Full 4K60 decode with a scale on the GPU: 6x real time (60 s of video in 10 s). CPU only (8 cores): 1.1x real time | A dense pass of a full match is about 16 min on the GPU. The old "2x" was a CPU filter limit. CPU-only machines cannot do dense passes of a full match in a practical time |
| E11 | 2026-11-16 | Who is at the near end (`near_identity.py`): torso color of the near player minus the far player, 2 clusters, vote in each game. Against the end changes from the rules (`sides.json`) | m1020: 100 % agreement. m1030 and m1015: the two differ only near manual score fixes, and there the vision is right. m1021: the color fails in the second half (hard sun and shadow, similar shirts) | Color works but needs a second signal. After a manual score fix the rule-based ends are wrong |
| E12 | 2026-11-16 | H24 input: server end from the pose (`server_end.py`): the player with a wrist above the shoulders at the point start | A raised arm is visible at 1 frame/s for 30 to 84 % of the points. When visible, the server end agrees with the marks in 96 to 100 % of the points (3 matches). A vote in each game gives the near player with no use of color; it repaired 7 games of m1021 | Use pose + color + rules together (`ends.json`). 1 frame/s misses many serves: a serve detector needs a dense pass near the point start |
| E13 | 2026-11-16 | H23: winner end with no ball track (`winner_baseline.py`): logistic regression on the seconds end-4 to end+7, 554 singles points | Mel audio: 49.6 % (chance). DINO keyframes: 60.8 %. Both: 57.4 %. Rule "the server wins": 50 % | Weak signal in the images, none in the audio. Not usable alone. The winner needs the ball (H20) or a much better event model |
| E15 | 2026-11-16 | H30: learned ball detector with no training (`ball_wasb.py`, `wasb_hrnet.py`, `ball_tracks.py`, `ball_view.py`). WASB-SBDT tennis weights (MIT), T3 decode, court crop, frames 1/30 s apart. One window of 44 to 50 s in 4 matches. No ball ground truth: the figure is the share of point frames inside a moving track | Scale 0.35 of the 1920-wide frame is the best single scale. Coverage of the point frames: m1015 daylight 73 %, m1030 daylight 65 %, m1016 doubles 47 %, m1020 night 42 %. Scales 0.35 + 0.5 merged: 76 / 70 / 50 / 47 %. Speed on the RTX 3060: scale 0.35 = 1.6x real time, 0.5 = 0.8x, 0.75 = 0.4x, 1.0 = 0.2x | H30 confirmed for daylight video: the tracks show clear flight arcs, bounces, and hits. Night video and doubles need more work. See "E15: details" |
| E17a | 2026-11-16 | H34 base: 3D ball flight from one camera (`ball_events.py`, `ball_3d.py`). Camera matrix from the court homography; path split at the kinks (sudden velocity changes); ballistic fit with drag for each segment. One 45-s window of m1015 | Camera: focal length 1672 to 1738 px in all 6 tennis matches, 3.1 to 3.7 m high, 5.6 to 9.4 m behind the near baseline (plausible, and the same phone gives the same focal length). Segments of 40 or more frames: fit error 0.7 to 2 px, speeds 53 to 74 km/h, and the fitted height at the segment end is 0.0 to 0.2 m at a bounce with no constraint on it. Segments shorter than about 20 frames give wrong depth and speed (199 km/h, 368 km/h) | Confirmed: one camera + gravity gives 3D position, speed, and landing point. The depth of a ball that flies away from the camera is the weak direction (one landing 14.0 m against 7.5 m from the next segment). Next: connect the segments at bounces and hits in one fit. See "E17a: details" |
| E16 | 2026-11-22 | H20, H32: point winner from the ball (`winner_ball.py`, `winner_sweep.py`). Ball detector on all 554 singles points (window start-1 s to end+2.5 s), 3D flights (`ball_3d.py points`). Rule: last shot, then net (the last flight stops near the net plane), else first bounce in or out. Parameters chosen leave one match out | First rule: 68.8 % correct of the decided points (77 % decided). Classifier on the same quantities: 65.3 %. Rule with the net test and tuned parameters (LOMO): 79 % decided, 74.0 % correct of decided, 69.0 % of all points (coin for the rest). Per match 71.5 to 77.5 %. Selected parameters: margin 0 m (no tolerance), net band -2.5/-4 to +2 m, minimum speed 8 to 30 km/h. Coverage of the point does not change the accuracy much (80 / 74 / 73 %) | H20 partly confirmed: the ball gives +13 points over the best result with no ball (E13, 60.8 %). Not good enough alone. Main faults (m1015 by eye): the last shot of the rally is not tracked (far court, near the camera), slow balls after the point, bounces near a line. Next: combine with the score grammar (E14) and train the detector on our video (E15b) |
| E14 | 2026-11-22 | H21: score grammar on top of the ball winner rule. (a) `score_decode.py`: game boundaries from the marks, forward-backward inside each game. (b) `match_decode.py`: full-match hidden Markov model (games, points, tiebreak, server, ends; first server and ends unknown; 2 % chance of an unmarked point). Evidence: ball rule (E16) and the pose server vote (E12, `serverNearVision`) | (a) With the true game boundaries: 69.7 % -> 74.9 % (+5.2). (b) Full decoder with the vision server: 68.7 % -> 70.4 % (+1.7); with no server evidence: no gain. Calibrated case probabilities add 0.4. Checks: perfect winners give 100 %; winners 70 % sure with the right sign give 100 %; the true server alone gives 47 to 57 % (the server marks the game ends, not the winner). Pose server vote agrees with the rules on 100 % of the 251 points where it exists (m1020, m1021). Case accuracy of the ball rule: net 83 %, bounce seen 67 to 80 %, landing from the fit and out 77 to 79 %, landing from the fit and in 51 to 59 % | The decoder is correct but adds little. The per-point winner evidence is the limit. The landing "in" with no bounce seen is a coin: the return of the opponent is not tracked. Priority: better ball coverage (E15b) |
| E9 | 2026-11-22 | H10: serve detection from the ball (`serve_ball.py`). Serve event = a fast flight from behind a baseline to the other side, or a toss (slow high ball above a baseline) followed by a fast flight. Test at the starts of the 566 marked singles points (start-1 s to start+2.5 s) | A serve event at 51 % of the point starts (m1015 80 %, m1020 57 %, m1030 48 %, m1021 44 %). The server end from the event is right for 97.2 %. The serve side from the start x: 75 % (weak) | Serve detection works where the ball is tracked. The server end from the ball is a second source next to the pose (E12). Recall follows the ball coverage (E15b) |
| E10 | 2026-11-22 | H10, H11: first-serve faults. Ball pass on the window before each point (`ball_wasb.py pre`: from the previous point end + 1 s, at most 22 s, to start - 1 s; `ball_3d.py pre`). Fault candidate = the last serve event from the server end at least 2 s before the start (`fault_ball.py`). Check by eye of all 30 m1015 candidates on frame sheets (`fault_sheet.py`) | m1015: 22 of 30 candidates are real serves. All 6 near-end events of the kind "toss alone" were false (a player walks or bounces the ball); with them removed: 22 of 24 real serves (92 %). Candidates at 24 % of the points. Serve result rule (in / out / net, `serve_result`): serves at the point starts (known in): 54 of 72 called in (75 %); candidates (faults): 17 of 21 called out or net (81 %). Net faults show as a fitted height of 0.0 to 0.7 m at the net plane (in-serves: 0.9 to 1.7 m). Several far-end faults are followed by the second serve after only 3 s | H10 confirmed on m1015: a serve before the marked serve is a first-serve fault, and the ball shows why (net or out). The thresholds come from m1015; the test on the other 3 matches is in work. Recall of faults is not measured (no labels) |
| E10b | 2026-11-22 | H10, H11 on a new match with the m1015 rules: m1030 (daylight, camera moves). Fault candidates and serve result as in E10. By-eye check of every third candidate (12) | Candidates at 27 % of the points. By eye: 12 of 12 are real serves. Serve result: candidates (faults) 30 of 34 called net or out (88 %); serves at the point start (known in) only 30 of 56 called in (54 %; 12 called net) | Fault candidates (a serve before the marked serve) transfer well. The in / out / net call of a serve does not transfer: too many in-serves are called net or out. Causes found: (1) for a near server the "landing" kink is often the return hit of the receiver (ball above the ground, so the depth is too long); (2) after the large camera move of m1030 (about 3700 s) the court model is wrong and all geometry is wrong. Rule change (`serve_result`): net only below 0.7 m at the net plane; a near-server landing behind the service line with no seen bounce gives no decision. Then m1015 + m1030: in-serves 89 of 108 decided called in (82 %), faults 29 of 44 decided called net or out (66 %). Main fault signal = the candidate itself (a serve before the marked serve); the in / out call is a second check. All 4 singles matches with the court tracks of E19: candidates at 24 % of the points (m1015 24, m1020 29, m1021 15, m1030 27 %). m1020 by eye (every fourth candidate, 11): 10 real serves, 1 no serve. Serve result, all 4 matches: in-serves 192 of 254 decided called in (76 %), candidates 70 of 96 decided called net or out (73 %); m1020 alone: 53 of 72 (74 %) and 28 of 33 (85 %). m1021 calls many in-serves net (20 of 49 decided): faint lines. Recall is not known yet |
| E10c | 2026-11-22 | H10: fault recall. By-eye check of 20 random points with no fault candidate (5 in each singles match, pre-window of 10 s or more). New tool `scan_sheet.py`: one sheet for each point, a crop around the server each 0.5 s for the 14 s before the marked serve (sheets in `runs/e10c`) | 3 of 20 have a real first serve that the detector missed (m1015 point 7, m1020 point 70, m1021 point 22); 17 have none (the server walks, gets balls, or bounces the ball). Estimate: about 15 % of the 76 % of points with no candidate are missed faults, so the candidates find about 65 % of the first-serve faults (range about 45 to 90 %, the sample is small). True rate of first-serve faults: about one third of the points. Cause in 2 of the 3 cases plus a pilot case (m1020 point 22): the detector saw the serve flight (16 to 30 ball positions), but the 3D fit is wrong (start 32 m or 124 m behind the net, 321 or 978 km/h), so `is_serve` rejects it. In the other 2 cases no flight was tracked | The fault candidates are precise (about 90 % real serves) with a recall of about two thirds. The first fix is the 3D fit of a serve flight, not the detector (E10d) |
| E10d | 2026-11-22 | H10: serve flight fit with a start prior (`ball_3d.serve_prior`). When the free fit is not plausible and the view ray of the first ball position passes the serve contact zone (y = 11.7 m, height 1.8 to 3.3 m), fit again with the start anchored there | With both ends: fault candidates 134 to 168. By eye (12 random new candidates): far end 6 of 6 real serves, near end 1 of 6 (a ball that the near player bounces also passes the zone). With the far end only: fault candidates 134 to 148 (24 to 26 % of the points), all 14 new ones from the prior; serve events at the point starts 327 to 350 of 566 (58 to 62 %); server end right 95.4 %; decoder 83.8 % (was 83.4 %). Of the 4 known missed faults of E10c, 1 is found now | A small gain; keep the prior for the far end only. The other missed serves have no usable ball flight: they need a second signal (serve pose at 60 fps or the hit sound), see E10e |
| E10e | 2026-11-22 | H10: the serve pose as a second fault signal (`serve_pose.py`). Pose data at 1 frame each second (`pose.npy`). Candidate = the wrist of the player at the server end is 0.5 torso lengths or more above the shoulders, in a second of the window before the point (2 s or more before the marked serve). By-eye checks with `fault_sheet.py` | Pose candidates at 235 of 566 points; ball candidates at 148; both at 121. The pose agrees with 82 % of the ball candidates. Pose-only candidates (114): by eye 7 of 14 are real serves (random 10: 5 real, 1 not clear, 4 false; and 2 real, 2 false in the E10c sample); the false ones are players that walk, most of them far players of m1030. Of the 3 missed faults of the E10c sample, the pose finds 2 (the third has 0.49) | The two signals make three confidence levels: ball and pose (121, the safe ones), ball only (27), pose only (114, about half real). Estimate with these numbers: about 190 to 200 real first-serve faults (one third of the points); ball candidates find about two thirds; ball or pose finds about 90 % with about 3 of 4 candidates real. A pose pass at 10 fps or more (a serve motion over some frames, not one frame) can make the pose signal precise |
| E19 | 2026-11-22 | Automatic court detection and tracking (Opus subagent): `court_auto.py` (white-line mask, Hough lines in two groups, line-pair hypotheses, pre-score, chamfer refinement, fine check on thin bright pixels; ok = cost < 1 px and margin > 0.5, or cost < 2 px and margin > 1.0). `court_track.py` checks each 60-s window and calls `detect` when the check fails | Error against the hand-started fit at mid-match: m1001 1.6, m1015 1.5, m1016 0.9, m1020 1.0, m1021 3.4, m1030 2.6 px. 18 more frames (start, 25 %, 75 %): all correct by eye, 5 marked not ok (faint or dusk). Wrong models cost 1.3 to 6 px, correct ones 0.02 to 0.6 px. Tracks for all 6 matches; camera moves found in m1015 (large near 3000 s), m1021 (many small after 2280 s), m1030 (large near 3780 s). Detection 15 to 50 s on the CPU; a full track 250 to 1250 s | Automatic court detection works on all our matches. No user input is needed. Open: the m1030 start position (other camera position, not ok); faint lines give "not ok"; some small "moves" can be fit noise. `ball_events.court_H` now uses the nearest ok window |
| E16b | 2026-11-22 | E16 and E14 again with the court tracks of E19 (flights recomputed) | Winner rule (LOMO): 81 % decided, 78.3 % correct of decided (was 74.0 %), 72.9 % of all points (was 69.0 %). Per match: m1015 81.0, m1020 73.4, m1021 74.6, m1030 84.7 % (m1030 was 72.9 %). Score grammar with the true game ends: 73.0 -> 78.7 %. Full decoder: see E14c (the decoder numbers of this row had a rule fault) | The court model after camera moves was a large error source. The game ends are the key input for the score grammar. The game ends are the key input for the score grammar; the full decoder cannot find them from the sparse pose server votes. Next: game ends from the serve events (E9) |
| E14c | 2026-11-22 | H21 again. (1) Fault fix in `match_decode.step`: the deuce merge changed (3, 5) back to (3, 4), so a game after an advantage never ended (same fault in the tiebreak). Check: a replay of the marks through `step` now agrees with the app score on all points, except 5 manual fixes in the marks. (2) New option `EPS_NULL`: a marked point that is not a point (outcome NONE, a let). (3) New upper-limit modes: `true_end` (true server end), `true_id` (true server identity). `python match_decode.py [EPS_NULL]` | Ball rule alone 72.2 %. Decoder (EPS_NULL 0 / 0.03): no server evidence 72.4 / 72.6 %; pose server vote (E12) 74.5 / 74.2 %; ball serve event (E9) 73.6 / 73.8 %; pose and ball 74.7 / 74.5 %; true server end 74.4 / 74.9 %; true server identity 79.6 / 79.8 % | The old "no gain" result of E14 (b) and E16b came from the rule fault. Server evidence now helps: +2.5 points with the real votes. The server end gives less than the server identity: the players change ends after odd games, so the server end often stays the same across a game end. Next: the server identity from player appearance (E5, shirt color) can give up to +7.5 points. EPS_NULL gives no clear gain |
| E14d | 2026-11-22 | H21 with the near-player identity as evidence: the raw torso-color cluster of each point from E11 (`near_identity.py` now also writes `rawCluster`, with no vote over the games of the marks). Emission P_ID for "cluster 0 = decoder player 1 at the near end" (the mapping is free, because the start states cover both). `match_decode.py` runs it as the last 4 modes | Raw color agrees with the rules: m1015 91.8 %, m1020 100 %, m1021 62.5 % (sun and shadow), m1030 82.6 %. Decoder (P_ID 0.85): color only 75.8 %; color + pose server 76.2 %; color + ball server 74.2 %; color + both 75.5 %. P_ID 0.7: 75.1 / 74.9 %; P_ID 0.95: 76.2 / 76.2 %. Ball rule alone 72.2 % | The near-player identity gives +4 points: it shows the end changes, so the decoder finds the game ends. It is the best single input after the ball. A better identity (m1021 fails) and a dense serve detector can add more (true server identity: 79.6 %) |
| E14e | 2026-11-22 | H21 with a local end-change signal. Cause of the m1021 color failure: one player changes the shirt at about point 100 (m1030 has a shirt change too). A fixed 2-cluster model is then wrong for the rest of the match, and no fixed channel weight is general (m1021 needs the chroma, m1030 the lightness). New: `near_identity.flips` = a running reference of the color difference "near minus far"; `flip` = the sign against the reference changed since the last point. The decoder uses it as transition evidence (`trans`: P(flip seen) 0.9 at an end change, 0.02 with no change). Modes `main(u, "flip")` | Flip signal against the true end changes (4 matches): 39 of 41 found, 6 false flips in 547 pairs (m1021: 11 of 13, 4 false). Decoder: flip only 76.5 %; flip + pose server 78.0 %; flip + ball server 78.0 %; flip + both 77.8 %. Per match with the pose server: m1015 81.2, m1020 75.7, m1021 74.1, m1030 82.4 %. Ball rule alone 72.2 % | Best winner result so far: +5.8 points from the score grammar with fair inputs only (no marks). It is near the limit of this evidence (true server identity 79.6 %). A shirt change gives one wrong flip only. More gain must come from the ball evidence (E15b) |
| E15b | 2026-11-22 | H30: fine-tune the WASB ball detector on our own video with pseudo-labels (`ball_labels.py`: projections of reliable 3D flights, 12,762 groups; `ball_train.py`: focal loss, 5 epochs, about 7 min for each epoch). Two folds: A = train m1015 + m1030, test m1020 + m1021; B = the reverse. Weights `e15b_a.pth`, `e15b_b.pth`; outputs with the tag `0.35_e15b_a` / `_b`; `flights_s0.35_mix.json` = fold A flights for m1020, m1021 and baseline flights for the other two | Fold A: flight coverage m1020 0.41 -> 0.39, m1021 0.28 -> 0.49; fewer false blobs (blobs for each frame with a blob 1.64 -> 1.10 and 1.87 -> 1.40). Fold B: coverage m1015 0.42 -> 0.08, m1030 0.42 -> 0.20; frames with a blob 0.67 -> 0.18 and 0.73 -> 0.31. Fold B with a lower heatmap threshold on 40 s of m1015: 0.3 gives blobs on 55 % of the frames, 0.15 gives 77 % but 8 blobs for each frame (noise). Winner rule with the fold A flights (LOMO): 73.0 % of all points (baseline 72.9 %); m1021 69.0 % (was 67.7 %) | Not confirmed in this form. Cause: the pseudo-labels cover only a part of the visible balls, so the training teaches the model that the other visible balls are background. The model gets careful; on a new court it finds almost nothing. Second finding: the winner accuracy does not depend on the coverage (81 / 76 / 80 % correct of decided in the low / middle / high coverage thirds). More coverage is not the way to a better winner. Do not repeat this training. If a later task needs it (E15c): no loss in the frames with no label where the base model fires, or labels by hand for 500 frames |
| E16c | 2026-11-22 | H20: error study of the winner rule by the time of the last tracked shot against the marked end. New parameter `winner_ball.T_CUT`: use only flights that start before the marked end + T_CUT (was + 1.0 s) | Before: shot start 2 to 4 s before the marked end: 85 to 87 % correct of decided (293 points); more than 4 s before: 65.7 % (108 points; the real last shot was not tracked); later than 2 s before: 50 to 70 % (47 points; ball handling after the point). By case: net 91 %, bounce seen and out 88 %, bounce seen and in 72 %, fit landing out 73 %, fit landing in 62 %. T_CUT sweep (LOMO, share of all points): + 1.0: 72.9 %; - 0.5: 74.7 %; - 1.0: 75.3 %; - 1.5: 76.5 % (87 % decided, 80.6 % of decided); - 2.0: 75.8 %. With T_CUT - 1.5 the best min_speed falls to 8 km/h. Decoder with T_CUT - 1.5: no server 75.8 %; color flip + pose server 81.0 %; color flip + ball server 80.9 %; true server identity 82.1 %. Per match (flip + pose): m1015 86.5, m1020 78.9, m1021 76.6, m1030 84.5 % | T_CUT - 1.5 is the new default. Best winner result: 81.0 % with fair inputs. The marks end about 2 to 3 s after the last shot; an automatic rally end must keep this offset or T_CUT must move. Then `case_prob` measured again with the cut and with the class "early" (last shot more than 4 s before the end): net 0.95 (early 0.77), out 0.88 to 0.90 (early 0.55), bounce seen and in 0.72, fit landing in 0.57 (early 0.65). Decoder: color flip + pose server 82.1 %; + ball server 82.9 %; + both 83.4 % (m1015 88.5, m1020 85.5, m1021 75.9, m1030 85.8 %). Note: these 9 probabilities come from the same 4 matches (not LOMO), so 83.4 % is a little optimistic. Open: the missed last shot (E16d) |
| E16e | 2026-11-22 | H20, H21: honest decoder number. The case probabilities of the ball rule (`match_decode.case_key`, 8 case types) are measured on 3 matches and used on the fourth (`use_lomo_cases`). Flights with the far-end serve prior (E10d) | In-sample table: 83.8 %. Leave one match out: **82.1 %** (m1015 88.5, m1020 82.9, m1021 75.3, m1030 84.5 %). With the pose vote only: 81.4 %; with no server evidence: 80.1 %. Ball rule alone: 76.5 % | Report 82 % for the winner on marked points. The gain of the decoder over the ball rule is about 5.5 points |
| E20 | 2026-11-22 | H4 with a learned tracker, H10: WASB at scale 0.35 over the full marked part of the 4 singles matches (`runs/full_chain.sh`, 18 500 s of video, about 1.7x real time with the full decode). 3D flights in chunks of 30 s (`ball_full.py`, about 30x real time on the CPU). Per-second features (`feat_wasb.py`). Serve events over the full match (`serve_full.py`). Rally GRU, leave one match out, 4 matches (3 for training). **The first run had a time fault** (frame number / 60, see section 4): the features of m1030 were up to 11 s late, and the first result was "no gain". The numbers here are after the fix (`frame_times.py`) | Rally GRU: audio + mel + DINO 87.3 % recall / 87.3 % precision (AUC 0.947). With the 3 flight columns (time in a flight, max speed, flights over the net): **90.3 % / 87.7 %** (AUC 0.966), start error 0.42 s, end error 0.63 s; m1030 recall 75.8 to 83.0 %, m1015 85.7 to 91.8 %. With all 8 ball columns: 89.8 % / 87.4 %. Serve events (after E10d): 1392; an event is at 63.6 % of the marked point starts; 51 % of the events are rally shots (the "fast" kind). With the rule "no flight over the net in the 4 s before": 675 events, 51 % at a point start, 35 % in the gap before a point (first-serve faults, lets, ball returns), 12 % in a point; recall of point starts 58.7 % | H4 confirmed with the learned tracker: the flight features add about 3 points of recall. Cost: the full decode of the match (1.7x real time), so this is a second pass, not the first filter. Serve events alone cannot find the rally starts; they stay an evidence for the server end and for fault candidates. Lesson: check the frame times before a conclusion from a long pass |
| E21 | 2026-11-22 | End-to-end prototype with no marks (`pipeline.py`): rally GRU with the flight features (E20) -> 3D flights of the full match -> ball rule, server votes, color flips -> score decoder. New in the decoder: a probability for each rally that it is not a point (from the GRU probability and the count of tracked shots) and a probability of a missed point before it (from the gap length). Evaluation on the detected rallies that match a marked point | Rallies: recall 90.3 %, precision 87.7 %. Winner right on the found points: 75.0 % (m1015 82, m1020 84, m1021 67, m1030 70 %); found and right: 68.8 % of all marked points. The ball rule alone gives about 74 % on the found points, so the decoder adds almost nothing here (on marked points it adds 7 points). Tests: fixed or per-rally probabilities change the result by about 1 point; false rallies removed by an oracle: no gain; the marked end time in place of the detected end: +2 points. A start moved to a ball serve event makes the start error larger (0.42 to 0.6 s). Score from the decoder on marked points: the score in the game is right before 75 % of the points, with the games count 28 %; a replay of the decided winners gives the full score right for 9 % (errors add up) | The score decoder needs the right list of points: with missed and false rallies it loses the place in the score. The fully automatic chain works, but the winner is at the level of the ball rule. The largest gain comes from a review of the rallies by the user (U1, section 5.5) or from a better rally detector. Demo: `demo_report.py` writes `demo/index.html` in the lab folder |
| E21b | 2026-11-22 | Why the fully automatic chain loses winners, and fixes (`pipeline.py`). Oracle tests: false rallies removed; missed points added; marked start, marked end, or both for the found rallies; a perfect \"not a point\" probability. Then: a study of the missed points and three fixes | Causes: (1) **the end time**. With the marked end for the found rallies the winner is right for 81.1 % (75.8 % with the detected end); the marked start gives no gain. A late end lets ball handling after the point into the winner rule. (2) **Short points**. 55 marked points are missed; almost all are 4 to 7 s long (ace, serve winner, double fault) and the GRU gives them a probability near 0. (3) False rallies are not a cause: a perfect \"not a point\" probability gives no gain (76.0 %); a learned one (logistic regression or boosted trees on duration, probability, shots, gaps; AUC 0.83 to 0.95) gives +1 point at most. Fixes: (a) a ball serve event outside the rallies with no other serve and no rally start in the next 12 s becomes a short point with a \"not a point\" probability of 0.5: 49 added, 21 right; rally recall 90.3 to **94.0 %**, precision 87.7 to 84.2 %. (b) Time cut of the winner rule for detected ends: -2.5 s in place of -1.5 s (sweep: -2.0 77.7, -2.5 78.3, -3.0 76.4, -3.5 69.9 %). (c) Case probabilities from the other matches (E16e). An end from the last fast flight + 2.5 s: mixed (m1030 +12, m1020 -11 points), not used. Result: winner right on the found points **78.3 %** (m1015 82, m1020 83, m1021 72, m1030 78 %); found and right: **74.7 %** of all marked points (was 68.8 %) | The end time of a rally is the key input for the winner. A better end detector is the next gain for the automatic chain (target: the 81 % of the marked end). The color evidence is cached in `<match>/color_cache.json` (the image reads were the slow part of each run) |
| E22 | 2026-11-23 | Which player is at the near end of each detected rally, with no marks and no score (`identity.py`). Need: the app wants the winner as player 1 or player 2. Check of the decoder first: its state "player 1 is near" for each rally against `ends.json`. Then: the shirt color difference (near minus far, Lab) of each rally, the sign against a running reference, and a 2-state hidden Markov model (end change probability 0.04) | Decoder state: right for 30 to 66 % of the rallies (the states of the rallies are not one path). So the winner as a player was right for only 37 to 56 %, although the winner as an end is right for 78 %. Color identity: m1015 100 %, m1020 100 %, m1021 99.4 %, m1030 100 % with weight 0.5 for the lightness and update rate 0.3. Other weights fail on one match: full lightness with rate 0.5 gives 65 % on m1021 (sun and shadow); no lightness gives 62 % on m1030. With the color identity, the winner as a player is right as often as the winner as an end (82, 83, 72, 78 %) | Use `identity.p1_near` for the player names (`detect.py` does). **Caution:** the two weights were chosen on the same 4 matches; players with shirts of the same color will fail. Next: give this identity to the decoder as a fixed input (E22b) |
| E22b | 2026-11-23 | Decoder with the color identity of E22 as evidence for each rally (`pipeline.py`, `ID_ABS`): P(the identity is right) in place of the local flip evidence of E11 | Fully automatic chain, winner right on the found points: flips 78.3 %; identity with 0.9: 79.8 %; 0.97: **80.5 %**; 0.995: 80.3 %. All 4 matches get better (m1015 85.6, m1020 83.8, m1021 75.2, m1030 79.7 %). Found and right: 74.7 to **76.9 %** of all marked points | Adopted: `ID_ABS = 0.97` in `pipeline.py` and `detect.py`. The identity fixes the parity of the game count, so the decoder now adds +2 points to the ball evidence. Caution as for E22: the color weights were chosen on these 4 matches |
| E23 | 2026-11-23 | First run of the full chain on a video that is not in the lab set (`detect.py`, a 96 min video of 2026-09-13, no marks): another hall, a low camera close behind the near baseline, a keyframe each 2 s, a variable frame rate (36.9 fps average) | Run time 52 min (0.54 x the video length): media 691 s (one decoded frame each second, because the video has no keyframe each second), audio 26 s, image 83 s, court 232 s, ball 1286 s, flights 41 s, pose 754 s, rallies and winners 11 s. **Court model: wrong**, with a good fit cost (the detector expects a camera high behind the court). So the ball crop was the upper half of the frame, the ball was in 20 % of the frames, and only 64 of 169 rallies got a winner. **Rally detection: plausible.** 171 rallies, median 8 s, median gap 18 s; a sheet of 8 rallies by eye shows a serve at the start, play in the middle, and walking players after the end | The rally model works in a new hall with a new camera position (by eye, no marks). The court detector does not work for a low camera, and all ball stages depend on it. `detect.py` now checks the court model (corners not far outside the frame, near baseline below far baseline) and, if it is not plausible, gives only the rally boundaries with a warning. The check passes on the 6 lab matches |
| E23b | 2026-11-23 | The chain for the low-camera video of E23. Four faults found and corrected. (1) `court_auto.py`: the camera (1.97 m high, 4.5 m behind the baseline) sees the full far half through the net mesh, so the correct model had a cost of 5.3 px and lost. New: `camera()` gives the camera position from the court model; `hidden()` marks the model points behind the net; such a point costs 1 px at most. This rule applies only to a low camera, and `detect()` has two passes: the first pass accepts only a low-camera model that passes the confidence limits, the second pass is the old search. (2) `court_track.py`: the track mask threshold was too high for the court lines (the hall roof is bright); for a low camera the track uses the detector mask and the same rule. (3) `burst_ball.roi_mask`: a court region corner behind the camera gave a wrong crop (the right 24 % of the frame was cut). (4) The video is a mix of 30 fps and 60 fps, and the flight code counts in frames of 1/60 s: `detect.py` now decodes such a video at a constant 30 fps with stride 1 and puts the detections on a 60 fps time line | Court: 5 of 5 test frames correct and trusted (5 to 12 px from a hand model); track: 97 of 97 windows ok, cost 0.05 px, camera height constant. The 6 lab matches: the same results as before for all 24 test frames (with the rule for each camera height, 3 lab matches got wrong models; with no confidence condition, also 3). Full run: 178 rallies, 157 with a winner (before: 64 of 169), median 3 shots for each rally (before: 0), 17 fault candidates, serve speed median 91 km/h (51 serves). Ball stage 1989 s (87 fps), full run about 62 min for 96 min of video | The chain now runs on a low camera and on a video that is not 60 fps. **Not measured: the accuracy of the winners on this video (no marks).** The ball is in 27 % of the frames; the far player is behind the net mesh, so expect a lower accuracy than in the lab matches. Marks for this video, or the corrections in the app, give the number |
| S1 | 2026-11-16 | Support: `sides.py` replays the score of each marked match like the app (`ScoringEngine.timeline`). Output `sides.json`: server, serve side, game, ends for each point | All 7 matches replay with no error | Use for the fault and winner experiments |

### E4b: details (camera moves)

- One court model for a full match is wrong. With the mid-match model,
  the far player of m1030 at minute 12 mapped to 26 m behind the net
  (the real value is about 12 m). A shift of 20 px at the far baseline is
  many meters.
- Version 1 of the tracker moved the 4 singles corners. Two of them are
  outside the frame, so the fit was unstable and it collapsed onto one
  line (cost 0). Do not use points outside the frame as parameters.
- Version 2 uses the 4 service-line corners (always in the frame), a wide
  search (cap 45 px) before the fine search (cap 14 px), a move limit, and
  a visible-share rule. It follows the small moves. After the large move
  it gives a folded model with a low cost. A validity check of the full
  court quad is necessary.
- A dark frame needs a lower mask threshold (`track_mask`).

### E15: details (learned ball detector)

- Model: the HRNet of WASB-SBDT (https://github.com/nttcom/WASB-SBDT, MIT), tennis weights
  `D:\bananashot-lab\ext\weights\wasb_tennis.pth.tar` (6.1 MB, 1.5 M parameters). Input: 3 RGB frames
  (9 channels, ImageNet mean and standard deviation). Output: 3 heatmaps, sigmoid, threshold 0.5,
  connected components, heatmap-weighted center.
- The lab does not run code from the downloaded repository. `wasb_hrnet.py` is our own definition of
  the same network. The weights load with `strict=True` and `weights_only=True`.
- The network is fully convolutional. The court crop goes in at a selected scale (not 512 x 288).
  The training data is TV video (1280 x 720 shown at 512 x 288), where the ball is about 2 px. Thus a
  small scale is correct for our video: scale 0.35 (frame 672 x 378) and 0.5 (960 x 540) work.
  Scales 0.75 and 1.0 give many false blobs on the near court surface and fewer tracks.
- Frame distance: 1/30 s (each second frame) is better than 1/60 s (coverage 38 % against 28 % on
  m1020). The two phases (even and odd frames) run as separate triplets, so the track is at 60 Hz.
- False detections that we saw: the head of the near player (m1020), insects near a lamp (m1020,
  night), fixed bright dots (a horizontal line in the time plot). A track filter removes the fixed
  dots. The head and the insects need the pose boxes and the court region.
- Gaps: the ball near the camera (large and blurred) and the ball in front of the near player.
- Pictures: `D:\bananashot-lab\data\runs\wasb_<match>.png` (x and y against time).
- Rejected: the full 4K crop. It is 5 times slower and worse.
- Next: run on all marked points (E16), then train on our own video with pseudo-labels from the
  good tracks (E15b) if the coverage limits E16.

### E17a: details (3D flight)

- The image row of the ball mixes height and depth, because the camera is low (3 to 4 m). A map of
  the ball to court meters with the homography is wrong in flight (the ball can be above the
  horizon). Rejected: "pseudo court" positions and depth from the image row.
- `ball_events.py`: `link` + `moving` (chains of blobs), `select` (one path, longest tracks first),
  `kinks` (velocity change above 6 "local m/s"; px/s divided by the px-per-meter of the image row;
  a gap between two tracks is also a kink).
- `ball_3d.py`: `camera(H)`: square pixels, principal point at the frame center, no distortion. The
  focal length comes from the two constraints on the homography columns. `fit_flight`: start
  position and velocity (6 numbers); linear start value with gravity only, then a nonlinear fit
  with drag (deceleration 0.0202 x v^2, no spin).
- Example (m1015, point at 2240 s): shot from (2.1, -3.9, 1.68) m at 71 km/h lands at y = 8.3 m,
  height at the end 0.17 m; next shot from (0.7, -10.3, 2.56) m at 66 km/h lands at y = 9.8 m,
  height 0.14 m; the return from (-2.4, 6.6, 2.27) m at 58 km/h lands at y = -7.4 m, height 0.00 m.
- Not included: spin (Magnus force), lens distortion, the rolling shutter.

### E11 to E13: details

- Energy peaks in the audio are not strokes. A 12 s rally has about 20
  peaks over 9 dB (bounces, steps, echoes, other courts), with no clean
  near/far alternation. H22 (last hitter from loudness) needs a learned
  hit detector, not peaks.
- The score in the marks is what the players agreed, with fixes. It is
  not always the rule-correct score, and the ends in `sides.json` are
  wrong after a fix. For winner labels by court end use `ends.json`.
- The near player wins 44 to 54 % of the points, and the server wins about
  50 %. Thus there is no cheap prior for the winner in amateur play.

### E7: details

- Version 1 (plain 3-frame difference, threshold 14, full frame): no
  signal. 100 to 150 noise candidates in each frame (trees, seats, net,
  codec noise), and bursts with more than 400 blobs were dropped.
- Version 2 added 4 things, and all 4 are necessary: a 3 x 3 blur, a
  court region with a margin (from the court model), a slow noise map
  (threshold = 3.5 times the average difference at that pixel), and no
  candidates on large moving blobs (players).
- The color test (yellow-green pixels) is a strong filter. It will fail
  with balls of another color.
- Speed: about 4x real time on the lab machine while another GPU job ran.
  The Python code is the limit, not the decode.

### S1: facts from the score replay

- The marked scores have manual fixes ("score overrides") in 4 of 7
  matches. The players lost count during the match, and the user fixed the
  score later. Thus a real amateur score is not always the "correct"
  score. A score decoder (H21) must accept such jumps.
- The app does not model the court ends. `sides.py` computes them from
  the tennis rules.

### E4: details

- A first version used only snapped lines and their intersections. It
  failed on 4 of 6 matches: the far baseline has few pixels, and a snap
  can lock to the wrong line (doubles line, net band). Do not use line
  intersections alone.
- The chamfer refinement fixed all 6. It needs a line mask (top-hat
  filter on a median frame of 9 keyframes, which removes the players).
- Open: an automatic first guess (no clicks). Open: a check for a camera
  that moves during the match.
- Camera positions differ much between the matches: center or corner,
  high or low, near baseline in or out of the frame.

### E1 to E3: details

Script: `train_eval.py --feats audio,dino`. Run data:
`D:\bananashot-lab\data\runs\e1-*`.

| Match | Audio: recall / precision | DINO: recall / precision | Both: AUC | Both: recall / precision |
|---|---|---|---|---|
| m1001 doubles, hall | 58.9 / 93.5 | 83.1 / 93.8 | 0.948 | 85.8 / 94.9 |
| m1015 singles | 72.4 / 84.5 | 55.1 / 90.0 | 0.927 | 78.6 / 92.8 |
| m1016 doubles | 79.0 / 84.0 | 82.2 / 91.8 | 0.943 | 84.5 / 88.5 |
| m1017 beach tennis | 47.4 / 92.0 | 47.4 / 97.9 | 0.878 | 58.8 / 98.3 |
| m1020 singles | 85.6 / 87.3 | 93.5 / 91.1 | 0.973 | 95.4 / 91.8 |
| m1021 singles | 79.0 / 78.0 | 79.0 / 88.3 | 0.945 | 88.9 / 87.3 |
| m1030 singles, outdoor | 56.2 / 74.1 | 43.1 / 49.6 | 0.855 | 68.0 / 81.2 |

Findings:

- The two signals add to each other. The fusion is better than each one
  on all matches but one.
- The training set has only 6 matches. A match from a new venue (m1030) or
  a new sport (m1017) is weak. More marked matches from different venues
  will help the embeddings most.
- Audio hits are only 2 to 3 times more frequent inside the points than
  outside them (m1030: 1.47 hits/s inside, 0.70 hits/s outside, at 9 dB).
  Other courts and ball pickups make the outside hits.
- The thresholds trade recall for precision. With lower thresholds
  (start 0.4, end 0.25, minimum length 2 s) the same curves give recall
  90.0 % and precision 81.7 %.
- The missed points are the short points (median 5 to 7 s against 10 s for
  the found points). About half of the missed points have a visible bump
  in the curve that stays below the threshold.
- The patch-grid embeddings (`feat_dinogrid`) and the frame-to-frame
  embedding distance (`feat_dinodelta`) were worse than the CLS + mean
  embedding in a test with 2 matches. Not tested on all 7 matches.

## 7. Decisions

| Date | Decision |
|---|---|
| 2026-11-16 | Lab data and the Python venv are in `D:\bananashot-lab` (disk C is almost full). Scripts are in `tools/auto-analysis`. |
| 2026-11-16 | Use only models with a permissive license (MIT, Apache-2.0, BSD) also in the lab, so that a good result can go into the app. No Ultralytics YOLO (AGPL). |
| 2026-11-16 | Evaluate only inside the marked part of each video (first mark minus 20 s to last mark plus 20 s). |
| 2026-11-22 | Stage 1 (the user): show the result of each task alone (point boundaries, winner, first-serve fault, ball tracking). The other parts can stay manual for now. A fully automatic workflow is the long-term goal. |
| 2026-11-22 | Human help (the user): a step can ask the user for help (example: SwingVision asks the user to select the same player in the detected rallies). Use this only where no automatic way gives a good result. Do not build it in this research; only record where it can help. |
| 2026-11-23 | Integration (the user): put the detection into the app now, so that the user can use it on the next matches and correct the wrong results there. The corrections are the test data and later the training data. The plan is in `app-integration.md`. |
| 2026-11-23 | Truth data, end time (the user): the marked end of a point often includes time after the rally (the reaction of the players, a pause for the viewer). Thus the marked end is not the rally end. The end error of the rally detection and the "marked end" result of E21b are measured against this later time. The app keeps two values apart: the detected rally end, and a padding setting that makes the clip boundary. |
| 2026-11-23 | Truth data, winner (the user): in the older projects the marked winner is what the players counted, not always what the video shows (a wrong call, a replayed point, a lost score, a forgotten end change). No detector can get these right, so the winner accuracy has a ceiling below 100 %. The size is not known. In the app: the winner of a point means "by the video"; the score override means "what the players counted"; a point can have a reason (players' call, replayed, score lost). The lab learns only from winner corrections with no such reason. |

## 8. Experiment plans (next)

Update this list when a plan changes. Move a finished plan to the
experiment log.

| Id | Plan | Needs | Tests |
|---|---|---|---|
| E5 | Player features in court meters (`feat_players.py`) with the court model of each time window (`court_track.json`). Add them to the GRU | Court track of all matches (in work) | H3 |
| E10f | Serve pose from a pose pass at 10 fps or more in the windows before the points (a serve motion over some frames). Target: pose-only candidates with 9 of 10 real | E10e | H10 |
| E21c | Rally end detector: a fine model of the end of a point (ball flights, audio hits, player motion at 10 fps) in the 5 s around the GRU end. Target: the winner result of the marked end (81 %). Also: the final score of each set as a decoder constraint (U4) | E21b | H21 |
| E23c | The winner accuracy for a low camera: needs a marked video with a low camera (the user prepares more marked videos), or the corrections report of the app (`corrections.py`) | E23b | H4 |
| E14b | Score decoder with more evidence: the final set scores from the user, serve detection on all points (E9), better winner evidence (E15b) | E9, E15b | H21 |
| E16d | Winner rule: the missed last shot (last tracked shot more than 4 s before the end: 66 % correct). Use hit sounds or the pose near the end to see a later shot; measure `case_prob` again | E16c | H20 |
| E17b | One fit for a full shot: flight, bounce (position from the homography of the bounce pixel), flight. Then ball speed and hit point statistics | E17a | H31, H34 |
| E17d | Bounce point of a flight: the landing point of the flight fit (`land`, the red ring of the app overlay) is often not at the real bounce. The user saw it on m0913 in the review window (2026-11-23): the ring is in the middle of one smooth part of the 2D ball track, and the track changes direction at another place. A bounce changes the path, so a landing point with no direction change at its time is wrong. Plan: (1) measure how often the fit landing is more than some px or some frames from the nearest direction change of the 2D track (the kinks); (2) take the bounce from the kink when one is near, and fit the flight with the bounce as a fixed end (see E17b); (3) reject a landing that has ball detections on the same smooth path after it. Measure the effect on the in/out cases of the winner rule (`case_prob`) | E17a, E17b | H20, H32 |
| E16f | Net shots (notes of the user in the review window, 2026-11-23, video `PXL_20260913_070045619`). Case 1128 s: a first serve of the far player into the net has no fitted flight at all (0 shots), so the net rule has no input. Case 1162 s: the last shot of the far player into the net has a flight of 9 detections (rms 0.6, ends 1.6 m before the net, 1.0 m high), but `winner_ball.good` needs 10, so the rule uses the shot before it. Plan: (1) accept short flights (n 6 to 9, low rms) that end at the net as net evidence; (2) find a serve of the far player from the track when the fit fails; (3) write a net event (time, end of the hitter) into `overlay.json`, and draw it in the app overlay | E16c | H20 |
| E25 | Rallies that are not points (same notes). Case 1106 s: a player passes the ball to the server. `serve_events` reads the flight (67 km/h, from the near baseline to the net) as a serve, and the chain makes a short point (pNotPoint 0.5). Case 1179 s: the two players are at the same end for the full rally, and the chain has 0 shots. Plan: (1) a short point needs a serve with a toss or a high start point, and a second flight of the receiver or a bounce in the service box; (2) a rally with the two players in one half of the court for most seconds is not a point (`feat_players.py`); (3) a rally with 0 shots and no serve gets a high pNotPoint. Measure on the 4 lab matches: lost real points against removed false points | E21, E5 | H4, H10 |
| E14f | Score decoder: say why it changes a winner (same notes). Case 1087 s: the ball rule is right (72 % for the far player: his shot is in, no shot back), and the decoder gives 55 % to the near player. The next rally (1106 s) is the false short point of E25 with the server at the other end, so the decoder possibly sees a game end here. Plan: (1) write for each rally the best score path with and without the change (score before, server, game end), so the app can show the reason; (2) measure how many decoder changes are right on the lab matches, by ball case; (3) lower the weight of a server change that comes from a rally with a high pNotPoint | E14b, E25 | H21 |
| E17c | Stroke type from the pose sequence around each hit (needs poses at 30 frames/s near the hits) | E16 | H33 |
| E18 | Speed test of the best rally pipeline on the MacBook Air M1 and on the CPU only. Include the ball detector at scale 0.35 | E8 pipeline | Hardware target |

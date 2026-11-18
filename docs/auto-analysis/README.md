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
  `D:ananashot-lab\ext\weights\wasb_tennis.pth.tar` (6.1 MB, 1.5 M parameters). Input: 3 RGB frames
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
- Pictures: `D:ananashot-lab\data
uns\wasb_<match>.png` (x and y against time).
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

## 8. Experiment plans (next)

Update this list when a plan changes. Move a finished plan to the
experiment log.

| Id | Plan | Needs | Tests |
|---|---|---|---|
| E5 | Player features in court meters (`feat_players.py`) with the court model of each time window (`court_track.json`). Add them to the GRU | Court track of all matches (in work) | H3 |
| E9 | Serve detector. First choice now: the ball track (a toss, then a fast flight from behind the baseline) in a dense pass of the 25 s before each marked point. Second choice: the pose (wrist above the head) | E15 on the windows before the points | H10 |
| E10 | Fault labels: use E9 to list the points with 2 serve attempts. Check a sample by eye on frame sheets. Then measure a fault detector (serve landing point against the service box) | E9 | H10 |
| E14 | Score decoder: a Viterbi search over legal score sequences with the server evidence (E12) and the winner evidence (E16). Variant: the user gives the final set scores | E16 | H21 |
| E15b | Train the ball detector on our own video: pseudo-labels from the long, well-fitted flights of E15 and E17a. Targets: night video, doubles, the ball near the camera | E15 on all points | H30 |
| E16 | Winner from the ball flights (`winner_ball.py`): last shot, first bounce in or out, net. Rule and classifier | E15 on all points (in work) | H20, H32 |
| E17b | One fit for a full shot: flight, bounce (position from the homography of the bounce pixel), flight. Then ball speed and hit point statistics | E17a | H31, H34 |
| E17c | Stroke type from the pose sequence around each hit (needs poses at 30 frames/s near the hits) | E16 | H33 |
| E18 | Speed test of the best rally pipeline on the MacBook Air M1 and on the CPU only. Include the ball detector at scale 0.35 | E8 pipeline | Hardware target |

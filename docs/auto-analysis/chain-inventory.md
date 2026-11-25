# Detection chain: high-level inventory and gaps

Date: 2026-11-25. Code state: branch `auto-detect`, commit `19c39e71` (journal state E41).

This file is the high-level inventory of the automatic detection of points, shots and winners. It shows
how the chain is organized, which parts it has, how the parts connect, and what holds the reliability
back. It is the base for the next step: a separate review of each part (section 7). It does not repeat
the experiment log. For the details of an experiment, read the journal [README.md](README.md) at its id.

## 1. How this inventory was made

- Three read-only investigations, then checks of their main claims in the code and the lab data:
  1. The Python chain: `tools/auto-analysis/detect.py` and the scripts that it calls.
  2. The journal: the measured numbers for each capability, the ground truth, the open plans.
  3. The app side: `src/main/kotlin/org/litvin/autodetect/`, `ui/tabs/points/detect/`, `corrections.py`.
- No detection, training or decode ran for this inventory. The stage times come from the `stage_times.json`
  files of earlier app runs in `D:\bananashot-lab\app\`.
- Tags in the findings: **[F]** = a fact, seen in the code or the data. **[V]** = a fact that was checked
  again for this file (code and data). **[I]** = an inference. A part review must confirm an [I] before
  a fix depends on it.
- Performance: only costs of 10 % of the run time or more are in this file (the user's limit).

## 2. The chain at a glance

### 2.1 Stages of `detect.py` (the path that the app runs)

Each stage is skipped when its output files exist (`detect.py:33`). Times: `PXL_e38`, a 96 min video
(m0913, low camera, mixed 30/60 fps), all defaults. Total 7 355 s = 1.28 × real time.

| # | Stage | Code | Input | Output | Decode | Model | Time share |
|---|---|---|---|---|---|---|---|
| 1 | media | `prep.py`, `frame_times.py` | video | `audio.flac`, `kf/*.jpg` (1 each second), `kf_times.csv`, `pts.npy` | keyframes only (full decode at 1 fps if the video has no keyframe each second) | – | 10 % |
| 2 | audio | `feat_audio.py`, `feat_mel.py` | audio | `feat_audio.npy`, `feat_mel.npy`, `hits.npy` | – | – | <1 % |
| 3 | image | `feat_dino.py` | keyframes | `feat_dino.npy` | – | DINOv2 ViT-S, frozen | 1 % |
| 4 | court | `court_track.py`, `court_refine.py` | keyframes | `court_track.json`, `court.json` (one model), `court_track_refined.json` | – | geometric fit | 5 % |
| – | court gate | `detect.court_problem` | `court_track.json` | if the court is not plausible: no ball stages, rallies only | – | – | – |
| 5 | ball | `ball_wasb.py` | video, `court.json` (crop) | `wasb_0_<n>_s0.35_k2.npy` (ball candidates) | **full decode of the whole video**, 2 model passes (`dim`) | WASB HRNet, fine-tune `ball_e38.pth` | **66 %** |
| 6 | flights | `ball_full.py` → `ball_3d.py`, `ball_bounce.py`; `net_events.py`; `feat_wasb.py` | candidates, court windows | `fullflights_s0.35.json` (flights, events), `net_events.json`, `feat_wasbf.npy` | – | physics fit | 6 % |
| 7 | pose | `pose_extract.py` | keyframes | `pose.npy` | – | YOLOX-m + RTMPose-m | 11 % |
| 8 | rallies | `rally_model.py`, `serve_ball.serve_events`, `pipeline.rallies` | audio, DINO, ball features; serve events | rally list (1 s steps) + short points | – | biGRU `rally_ball.pt` | <1 % |
| 9 | winners | `pipeline.evidence` → `shot_chain.py`; `identity.py`; `match_decode.decode` | rallies, flights, events, net events, pose, keyframes | winner, confidence, server, fault, chain | – | HMM score decoder | <1 % |
| 10 | post | `add_views`, `write_overlay` | pose, net events | `view`, `netHit`, `overlay.json`, `result.json` | – | – | <1 % |

### 2.2 Data flow

```
video ──► keyframes 1/s ──► DINO ───────────────┐
  │           ├──► pose ──► server vote, shirt colors ──► identity (near player)
  │           └──► court model ──► ball crop, 3D camera, ground points, in/out
  ├──► audio ──► audio features ────────────────┤
  └──► ball candidates (full video) ──► tracks ──► 3D flights ──► bounce / hit / net events
                                                     │                    │
                                                     ▼                    ▼
                                        ball features ─► rally GRU ◄─ (audio, DINO)
                                                              │
                         serve events ──► short points ──► rally list (start, end)
                                                              │
                rally list + flights + events ──► shot chain ──► winner evidence (pBall)
                                                              │
         pBall + server + identity + pNotPoint + pMiss ──► score decoder (HMM) ──► winner per rally
                                                              │
                                    result.json ──► app import ──► points, winners, fault clips
                                                              │
                              app counts the score from the winners (the decoder score is not used)
```

Two facts from this picture:

- The rally boundaries depend on the ball stage: the rally GRU reads the ball features. Thus the most
  expensive stage must run on the full video before the chain knows where the rallies are. [F]
- The court model, the ball candidates, the rally end and the identity anchor each feed all later
  winner stages. An error there cascades (section 4.3).

## 3. Status of each capability

Numbers from the journal. "LOMO" = leave one match out. "4 lab matches" = m1015, m1020, m1021, m1030
(singles, high camera behind the court, 60 fps, one phone). Details: the journal at each id.

| Capability | Default method | Best measured result | Data | Held out? | Ground truth |
|---|---|---|---|---|---|
| Rally start, end | biGRU on audio, mel, DINO, ball features (E20) | recall 93.6 %, precision 82.2 % (E41); start 0.42 s, end 0.63 s median (E20) | 4 lab matches | LOMO | marked points (end includes reaction time) |
| Short points (ace, double fault) | serve event outside a rally (E21b) | 49 added, 21 right (E21b) | 4 lab matches | partly | marked points |
| First-serve fault | serve event ≥ 2 s before the point (E10) | about 90 % of the candidates real; recall about 65 % (45 to 90 %) | 12 to 24 points by eye | no | **none** (marks skip faults) |
| Server end | pose vote, then ball serve event (E28e) | known for 89 % of rallies, 94 % of them right | 4 lab matches | – | from the score replay |
| Court model | `court_track` + `court_refine` (E19, E26) | 0.67 px / 4 cm on 165 points (E26c) | 7 videos | **no** (tuned on the same videos) | 165 hand marks |
| Ball candidates | WASB + E38 fine-tune + `dim` pass | 72 to 95 % recall on labels (E38) | 2 539 labeled frames, 24 at the m1021 wall | partly | `ball_labels_e27.json` |
| Flights (2D, 3D) | link/select, ballistic fit with drag (E17a, E30, E41) | coverage 52 to 59 % of the marked point time; fit landing error 1.1 to 1.6 m (E33, E38d) | 4 lab matches | – | indirect only |
| Bounce events | `ball_bounce` vertex test (E17d, E40) | 58 of 62 found, 1 of 39 false, 0.08 m median | 62 bounces | no | `bounce_labels.json` |
| Hit events | flight direction change | not measured | **13 hit labels** | – | almost none |
| Net events | `net_events.py` (E16g) | 85 % recall on own candidates, 64 % independent | 143 candidates | no | `net_labels.json` |
| Shot chain (shots, hitter) | `shot_chain.build` (E28 to E41) | 95 % decided, 87.3 % right of decided (E41) | marked points | LOMO case tables | **none for hitter or shot count** (15 points by eye) |
| Near player (identity) | shirt color + 2-state HMM (E22) | 99.4 to 100 % | 4 lab matches | **no** (tuned on the same) | from the score replay |
| Score decoder | full-match HMM (`match_decode`) | 83.9 % on marked points (E28b; not measured again after E32) | 4 lab matches | – | marked winners |
| **Full chain (lab)** | `pipeline.py` | 82.0 % of matched rallies, **78.0 % of all marked points** (E41) | 4 lab matches | LOMO | marked winners |
| **Full chain (app, `detect.py`)** | same functions, other models | **not measured** | – | – | – |

## 4. Answers to the three questions

### 4.1 Does the chain have the necessary parts?

Most of the parts are there. The chain has rally detection, a court model, a ball detector, 3D flights,
bounce and net events, a shot chain, a player identity, a score grammar, and an app import with a review.
These parts are missing or weak:

| Part | State | Effect |
|---|---|---|
| A frozen end-to-end benchmark for the path that the app runs | **missing** (G1) | Nobody can say how reliable the shipped detection is, or if a change made it better |
| A rally end detector | missing; the end comes from the 1 s rally curve (E21c is a plan) | The largest single loss of the winner after the evidence (G4) |
| Hit detection | weak: only a direction change of the ball path; no audio hit and no pose at the hit; 13 hit labels | The hitter and the shot count are not verified; a hit in the air has no event (E40) |
| Player positions at the hit time | missing: pose only at 1 frame each second | The hitter comes from the ball direction only. No player-to-ball check is possible |
| Use of audio hits in the shot stages | missing: `hits.npy` feeds only the rally GRU [V] | H32 planned audio + track for hits; only the track is used |
| Calibration of the confidence for the app path | missing (G3) | The app shows confidences and filters winners by a limit that is not calibrated for its own inputs |
| Fault ground truth | missing | No fault number is better than ±20 % |
| Stroke type, ball speed check | out of scope now | – |

### 4.2 Do the parts connect correctly?

Mostly yes at the function level: `detect.py` and `pipeline.py` call the same `rallies()`, `evidence()`
and `decode()`. The defects are at the interfaces: time bases, versions of the inputs, calibration,
duplicated rules, and silent fallbacks (section 5, G2, G3, G6, G7, G12, G13).

### 4.3 Is there a major misalignment?

Yes. Three of them:

1. **The measured chain is not the shipped chain (G1).** All full-chain numbers come from `pipeline.py`
   with LOMO models and the old ball files. The app runs `detect.py` with models trained on all 4 lab
   matches, the new ball weights, another start state, and fixed case tables. No measurement of
   `detect.py` output against marks exists.
2. **Time base of the 1 fps features (G2).** DINO and pose rows have the keyframe number as their time.
   Audio, ball features and the rally times use real seconds. On m1030 the two drift apart by 11 s at the
   end of the match [V]. m1030 is the weakest lab match.
3. **The score layer (G8).** The lab decodes the full score (server, games, end changes) to choose the
   winners. The app throws that score away and counts its own score from the winners. The decoder is also
   unstable: equal evidence moves 5 to 8 winners on one match (E29b, E30).

## 5. Findings

Order: largest expected effect on the reliability first. Each finding has an id for the part reviews.

### 5.0 Impact ratings

Impact = the effect on the reliability of the shipped result, or on the ability to measure and improve it.
Ratings added 2026-11-25.

- **Critical**: blocks the measurement of all other fixes.
- **High**: a measured or large probable loss of points, winners or score, or a silent wrong result.
- **Medium**: a loss only on some videos or conditions, or a risk that is not measured yet.
- **Low**: maintenance cost, or no direct effect on the result.

The app path is a lab integration now: the app shows the detection only when `autodetect.json` names a lab
venv (`DetectModel.kt:35`). Thus the app-side findings (G11 to G14) affect the lab users only. Before a
release to other users, rate G12 and G13 one step higher.

| Id | Impact | Reason |
|---|---|---|
| G1 | **Critical** | The reliability of the shipped path is not known. No part fix can show an effect on it. R0 comes first |
| G2 | **High** | 11 s offset of DINO and pose at the end of m1030. Phone videos often have a variable frame rate. The fix is small |
| G4 | **High** | The largest measured loss: +2.4 to +5.3 winner points with the marked end. The 2.5 s cut drops about 17 % of the last shots |
| G8 | **High** | Short points are on by default and count in the app score, but only about 43 % of them are real. The decoder starts at 0-0 with no server. 5 to 8 winners move with equal evidence |
| G10 | **High** | No labels for the parts that decide the winner (hitter, shot count, faults). R5 and R6 cannot measure their fixes |
| G12 | **High** | A result can come from old files with no warning (a reused folder name, new weights with an old ball file). Silent wrong results |
| G15 | **High** | All numbers are for one camera setup. A typical user video (low camera, 30 fps, doubles) has no measured result |
| G3 | Medium | The confidence and the review dots are not calibrated for the app path. The rally bounds moved 1 to 6 s with new candidates (E41) |
| G6 | Medium | Good on the lab videos (0.67 px), but no held-out test. A wrong court breaks short points, server end and in/out. High on new camera positions |
| G9 | Medium | 99.4 to 100 % in-sample. A wrong anchor flips the winners of long parts of a match. Not measured held out |
| G5 | Medium | A ball failure also moves the rally bounds. `rally_base.pt` has no measured result. The main cost is speed (P1) |
| G11 | Medium | No effect on the detection now. It makes the planned test data wrong. Fix it before the corrections are used as data |
| G13 | Medium | No direct loss of points. It hides faults of the chain and loses the full run on a late exception |
| G7 | Low | Maintenance cost. A fix in one copy does not reach the others. No measured loss |
| G14 | Low | No guard on the contract. The rally time item is part of G2 |
| P1 | **High** | About 35 to 45 % of the run time. Needs a two-pass design |
| P2 | **High** | Up to 33 % of the run time. One measurement (E38 weights without `dim`) can decide it |
| P4 | Medium | 10 to 18 % on videos with no keyframe each second; none on the others |
| P3 | Low | About 7 % of the run time |

### G1. No measurement of the shipped path; no frozen benchmark — major

- All full-chain numbers (E21 to E41) come from `pipeline.run` (`pipeline.py:219`). [F]
- `detect.py` uses other inputs [F]:

  | Input | `pipeline.py` (measured) | `detect.py` (shipped) |
  |---|---|---|
  | Rally model | `runs/e20_wasbf`, LOMO | `rally_ball.pt`, all 4 matches, trained 2026-11-23 [V] |
  | Ball weights | held-out `e38_f1` to `e38_f4` | `ball_e38.pth`, all 4 matches |
  | Case tables | LOMO | fitted on all 4 matches |
  | Decoder start | marked score from `sides.json` | 0-0, server unknown |
  | Net events, `feat_wasbf` | old files (one pass, old flights) | new files |
  | m1030 margin | 0.0 | −0.15 (`detect.py:451`) |

- A run of `detect.py` on a lab match is not a test: its models saw these matches. m0913 has no marks.
  The app samples have 6 marked points each (E31). [F]
- The benchmark is not frozen: each experiment replaces the flight files (`*.before-eXX` copies). An old
  number cannot be repeated. [F]
- Effect: the reliability of the app detection is not known. A part review cannot show that its fix
  helps the shipped path.
- Next: a benchmark that runs the `detect.py` path per held-out match (LOMO models of each stage),
  with frozen inputs and a fixed report. This is review R0 (section 7).

### G2. Time base: keyframe number against real seconds — major on some videos

- `lab.n_seconds()` says "count of whole seconds = count of keyframes" (`lab.py:22`). [V]
- `feat_dino.npy` and `pose.npy` have one row for each keyframe (row = keyframe number). [V]
- `feat_audio.npy`, `feat_wasb.npy` and the rally times use real seconds (audio samples, `frame_times`). [V]
- The rally GRU puts these rows side by side. `pipeline.evidence` reads the pose of second `s` as row
  `s` (`pipeline.py:101-104`) for the server vote and the shirt colors. [V]
- Measured drift of keyframe time minus keyframe number, at the end of the video [V]:
  m1030 +11.0 s, m1021 −1.1 s, m1015 −0.6 s, m1020 −0.5 s.
- A video with no keyframe each second (`kf.fps1`) has a constant offset of about 1 s. Only the overlay
  corrects it. [F]
- Effect [I]: wrong DINO and pose context late in m1030 (rally model, server vote, identity colors). It
  can explain a part of the weak m1030 result (rally recall 75.8 to 83 %, E20). A phone video with a
  variable frame rate is common, so this is a risk for new videos.
- The start check (`detect.py:327`) tests only the median keyframe step.

### G3. Models and calibration fitted on other inputs than the shipped inputs

- `rally_ball.pt` (2026-11-23) learned features from the old ball candidates. `detect.py` gives it
  features from the E38 weights with the `dim` pass (later). E41 saw rally bounds move 1 to 6 s with new
  candidates. [V] for the dates, [F] for E41.
- `shot_chain.CASE_P` was fitted on marked points with marked ends (T_CUT −1.5). It is applied to
  detected ends (T_CUT −2.5, `pipeline.py:46,133`). [F]
- `p_null`, `p_miss`, `md.PARAMS`, `SHORT_WAIT`, the identity weights and the court constants were
  chosen on the same 4 matches. [F]
- Effect [I]: the confidence that the app shows and filters with (`minWinnerPercent`) is not calibrated
  for the app path. The review dots ("check first" under 70 %) can point to the wrong points.

### G4. The rally end is a single point of failure with no own detector

- The rally end comes from the 1 s rally curve (`lab.segment`, steps of 1 s). [F]
- Each evidence window (`a − 1` to `b + 2.5`), T_CUT and the fault window use it. [F]
- The marked end includes reaction time, so the end metric measures against a later time than the
  real rally end (decision 2026-11-23). [F]
- Measured cost: the winner gets +2.4 points (evidence) to +5.3 points (old rule with decoder) with the
  marked end (E28c, E21b). The 2.5 s cut drops about 17 % of the last shots (E28c). [F]
- E21c (a fine end model) is open. Its target (81 %) is stale: the chain is at 82 % now.

### G5. The rally stage depends on the full ball pass

- `rally_ball.pt` needs `feat_wasbf` (ball features). Thus the ball stage must run on the full video,
  breaks included. [F]
- When the court gate fails, the chain uses `rally_base.pt` (no ball features). That model has no
  separate measured result for the app path. [I]
- A ball failure (crop, court) thus also changes the rally boundaries, not only the winners.
- This is also the largest performance gap (P1).

### G6. Court model: one first model carries all 3D; weak gate; no held-out test

- The 3D camera (focal length, pose) comes from the first model. The refined model corrects only the
  ground points (`ball_events.py:31-35`). [F]
- The ball crop uses one model from the middle of the video (`court.json`) for the full pass. A camera
  move cuts the crop with no warning. [F]
- The gate passes when 70 % of the windows are plausible. Implausible windows are dropped, and
  `window_at` uses the nearest good window with no warning. [F]
- The court constants were chosen with all 7 videos in view (E26c). No held-out test exists. [F]
- Effect: a wrong camera gives wrong speeds and heights. Then the serve thresholds (in m) fail: no short
  points, no server end, wrong in/out. The low-camera far half was 1.4 to 1.8 m off (E26a).

### G7. Duplicated rules with different thresholds

- The bounce logic is in 5 places: `ball_events.kinks`, `ball_3d.flights` anchors, the kink rule of
  `winner_ball.analyse`, the vertex test of `ball_bounce` (used by the chain), `serve_ball.landing`. [F]
- The winner rule is in 3 places: `winner_ball.rule`, `winner_sweep.decide`, `shot_chain`. [F]
- The filters for a good flight differ: n ≥ 10 (fps adapted), n ≥ 6 / 0.15 s, n ≥ 8. [F]
- The chain still gets the old rule's case and decision as input (`w_old`, `old_case`), and the old rule
  decides when the chain fails (G13). [F]
- `netHit` (`net_events.py`) and the net case of the chain can disagree. [F]
- Effect [I]: a fix in one copy does not reach the others. The review window can show facts that the
  decision did not use.

### G8. Score layer: the decoder output and the app score disagree in design

- The decoder uses the score grammar to choose winners, but the app counts the score from the winners
  only. The server and the score path of the decoder are lost. [F]
- The decoder start in `detect.py` is 0-0 with an unknown server. The lab start is the marked score. [F]
- `endChangeBefore` comes from `near_identity.flips`, but the decoder uses `identity` (`FLIP=None`).
  The two can contradict each other. [F]
- Short points count in the app score like rallies, but only about 43 % of the added short points are
  real (E21b). `pNotPoint` is only shown as text. [F]
- App decision 16 (a double fault is a point for the receiver) has no code. [F]
- Decoder stability: std 0.5 to 0.6 points; equal evidence moved 5 to 8 rallies on one match (E28c,
  E29b, E30). [F]

### G9. Identity anchor: one global anchor, in-sample numbers

- `identity.p1_near` is 99.4 to 100 % on the 4 lab matches, but its weights were chosen on them. [F]
- The app asks once ("Who is at the near end?") and applies one `swapped` flag to all rallies. [F]
- `ID_ABS = 0.97` makes the decoder trust the identity strongly. A wrong color run moves winners. [F]
- The shirt colors use the pose rows, so G2 also affects them. [I]

### G10. Ground truth does not cover the parts that decide the winner

- No labels for: first-serve faults, hitter, shot count, serve and ball speed, stroke type. [F]
- Tiny sets: 13 hits, 62 bounces, 45 net hits, 24 ball frames at the m1021 wall. [F]
- No marked video for: low camera, 30 fps, doubles (in the ball era), other phones, clay or grass. [F]
- The winner truth is the players' count, not always the video (decision 2026-11-23). The ceiling is not
  known. Rally precision counts the unmarked faults as false rallies. [F]
- Effect: the part reviews can measure the rally and winner stages, but not the shot stages directly.

### G11. The feedback loop from the app gives wrong numbers

The app plan says: the user's corrections are the test data and later the training data. Today:

- `corrections.py` does not read `swapped` of the import record. After an import with swapped players,
  each kept winner counts as "changed by the user". [V]
- It does not read `minWinnerPercent`. A winner under the limit was never set, but counts as a change. [V]
- It does not read `auto.checked`: a point that the user never reviewed counts as correct. [F]
- It cannot tell "Swap players" from a winner fix. Bounds compare without the gap clamp and rounding.
  "Added" includes clips from before the import, so the recall is wrong. [F]
- Nothing reads `review-notes.json` or `corrections-*.json` automatically. The notes do not store the
  result file or the run. [F]
- Effect: the planned main source of new test data is biased now.

### G12. Stale caches and hidden configuration

- The work folder has the name of the project folder only. A changed video, or two projects with the
  same folder name, reuse old work files. [F]
- The name of the ball file does not include the weights or the code version [V]. New weights or new
  lab code do not run again; the app never passes `--force`. [F]
- `os.environ.setdefault` lets an inherited `BALL_PASSES` or `BALL_WEIGHTS` override the settings. `CHAIN`,
  `COURT_TRACK`, `BLOB_TAG` are read from the environment with no settings entry. [F]
- The overlay is the newest overlay file, not the one of the imported result. [F]
- The lab has the same problem: the lab net events and `feat_wasb` come from the old candidates, while
  the lab chain uses the new ones (E38d). [F]
- Effect [I]: a result can mix versions with no warning. This is the largest silent risk in the app path.

### G13. Silent failures

- A fault in the shot chain falls back to the old rule with no record (`pipeline.py:137`). [V]
- A fault in the fault chain is ignored (`except: pass`, `pipeline.py:179`). [V]
- Warnings (no ball weights, no refined court, no overlay, no pictures) go only to `run.log`. The app
  shows only the first entry of `result.warnings`. [F]
- An exception after the rally stage writes no `result.json`: no partial result. [F]

### G14. The Python–app contract has no guard

- `version` is read but not checked. `video` is not compared with the project video. [F]
- No test uses a real `detect.py` output. No test covers the `@` lines of the runner or
  `corrections.py`. [F]
- Rally times are keyframe numbers × 1 s; the app reads them as video seconds (G2). [F]

### G15. Narrow coverage of conditions

- Measured: one phone, high camera behind the court (3.1 to 3.7 m), 60 fps, singles, outdoor hard courts;
  day, night, hard sun. [F]
- Not measured: low camera, 30 fps, doubles, indoor hall (m0913 has no marks), side or corner cameras,
  other lenses, clay, grass. [F]
- Default flags that were never measured at 30 fps: `SERVE_FILTER`, `FPS_ADAPT` (E29b). [F]

## 6. Performance gaps (10 % or more)

Reference: `PXL_e38`, 96 min, 7 355 s in total (1.28 × real time). Ball stage 66 %, pose 11 %, media 10 %.

| Id | Gap | Share now | Possible gain [I] |
|---|---|---|---|
| P1 | The ball stage decodes and runs the model on the whole video. The rallies are 25 to 30 % of a match (journal section 4). The rally GRU needs the ball features, so a change needs a two-pass design (rally candidates with high recall first, then the ball pass only in the candidate windows) | 66 % | About 35 to 45 % of the total |
| P2 | The `dim` pass doubles the ball stage. It was needed for the WASB weights. With the E38 weights it was never measured alone (E27g(1)) | about 33 % | Up to 33 % if E38 alone has the same recall |
| P3 | Pose runs on each keyframe. The decision uses pose only in the rally seconds (the overlay uses all) | 11 % | About 7 % |
| P4 | A video with no keyframe each second gets a full decode in the media stage, and a second full decode in the ball stage | 10 to 18 % on such videos | The 1 fps pictures can come from the ball decode |

P1 and P2 overlap. Do not add their gains.

## 7. Plan for the part reviews (next step)

Each review takes one part, verifies it on its own level, and uses the finding ids above. Order: R0
first, because the other reviews need it to show an effect on the shipped path.

| Id | Part | Main questions | Findings | Data |
|---|---|---|---|---|
| R0 | Benchmark of the shipped path | Run the `detect.py` path per held-out lab match (LOMO for the rally model, ball weights, case tables), with frozen inputs; report rallies, ends, faults, winners, confidence calibration | G1, G3 | 4 lab matches |
| R1 | Time base and media | Map each 1 fps row to its keyframe time; measure the rally and server effect on m1030; the `kf.fps1` offset | G2, P4 | `kf_times.csv` of all matches |
| R2 | Rally boundaries | Rally end detector (E21c); dependence on the ball features (`rally_base` against `rally_ball`); short points and not-a-point | G4, G5, P1 | marked points |
| R3 | Court model | Held-out test; camera from the refined model; crop per window; gate rule | G6 | `court_ref_marks.json` |
| R4 | Ball candidates and flights | `dim` with the E38 weights; ball pass only in rally windows; flight coverage | P1, P2 | `ball_labels_e27.json` |
| R5 | Ball events and the shot chain | One bounce rule and one winner rule; hit events (audio, pose); hitter labels | G7, G10 | `bounce_labels.json`, new hit labels |
| R6 | Serve and fault | Fault labels; server end from the chain (E28h(3)); `SERVE_FILTER` effect on recall | G10, G15 | new fault labels |
| R7 | Identity and score decoder | Held-out identity; decoder stability; decoder score against the app score; `endChangeBefore`; double fault | G8, G9 | 4 lab matches |
| R8 | App contract and feedback loop | Schema version, real fixtures, cache keys, warnings, `corrections.py` fixes, a link from notes to the run | G11 to G14 | app projects |
| R9 | Coverage | Marked low-camera, 30 fps and doubles videos; which conditions the app must refuse or warn about | G15 | new marked videos |

Rules for each review:

- Start from this file and the journal. Add each experiment to the journal at once (journal rule).
- Report the paired result of the part first, then the full-chain result of R0 with its noise.
- Confirm each [I] item of the part before a fix depends on it.

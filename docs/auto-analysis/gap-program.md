# Gap program: cover the significant gaps of the chain inventory

Started: 2026-11-25. Time box: 2 weeks (to 2026-12-09). Budget: about 5 agent-weeks.
Base: [chain-inventory.md](chain-inventory.md) (gaps G1 to G15, P1 to P4, reviews R0 to R9).
Manager: the session "Chain inventory audit gaps". The manager sets the goals, starts the agents,
reviews and merges their work, and keeps this file current.

This file holds the plan, the rules and the decisions of the program. The experiment results go to
the journal [README.md](README.md), as before.

## 1. Goal

Cover the Critical and High gaps: G1, G2, G4, G8, G10, G12, G15, P1, P2.
Cover the Medium gaps G3, G5, G6, G13, G14 where they share the work of a High gap.
Phase 2 (when a stream is free): G8 score design, G9, G11, G7.

Priorities of the detection (the user, 2026-11-26): 1. the correct rallies; 2. the correct outcome of each
rally. The score is only the result of the points, and no benchmark uses a score measure.

How priority 2 is measured (the user, 2026-11-26): by the events, not by the outcome. The outcome is right
for the right reason only when the events are right: each shot (hit time, hitter), each bounce (time, place,
in or out), each net touch. A right outcome from wrong events is luck and does not carry over to the next
match. Thus:

- The main measures of priority 2 are event measures against event labels: recall, precision and time error
  of hits, bounces and net touches; hitter right; shot count right; in/out right.
- The outcome measure (winner right) stays as a check, not as the target.
- Do not tune or fit a parameter on the outcome when an event measure for it is possible. A fix of a wrong
  outcome must name the event that was wrong and show that this event is now right.
- An analysis of wrong winners gives the wrong event of each one (wrong hitter, missed shot, missed or false
  bounce, wrong in/out, missed net touch).

"Covered" means: the gap has a fix that is merged and on by default, or a measurement that decides the
question, with a journal row. A gap is not covered by a plan or a note only.

### Coverage of the gaps (manager, 2026-11-27 01:30)

Status words: **covered** (a fix on by default, or a measure that decides the question); **measured** (a number
exists, a fix is open); **open** (no decision yet). The milestone batch (Igor, Misha) gives the final held-out check
of all rows.

| Gap | Rating | Status | Evidence | Open |
|---|---|---|---|---|
| G1 shipped path | Critical | covered | `bench.py` with a manifest (E44), Igor held out (E45, E47), case library 4 levels (item 8) | first clean held-out number: Misha m1113 on 897fd28d, rallies recall 94.2 %, precision 92.6 %, winners 84.6 % (check); head code number follows |
| G2 time base | High | covered | E50 (1 fps rows at the keyframe time), m1030 measure E66 | none |
| G4 rally end | High | covered | end detector E60, on by default | the rally misses: 27, of which 23 have the rally GRU silent (S3 E162, the GRU with the ball channel) |
| G8 short points | High | covered | E61 (rule measured against the 70 % target) | none |
| G8 score layer | High | covered | E63, E64, E142: the decoder does not change a winner (the user) | none |
| G10 truth | High | measured | label page v2, 16 of 60 fragments labeled, faults from the marks (E159); E170, E171: shot count 14 of 27, in/out 52 of 54, fault clips 5 right of 12 | more labels (the user); S3 ranked fixes of the shot count (8 E171 proposals); the Faults step |
| G12 stale results | High | covered | E51, import-closure keys (item 13), upstream keys (item 16), safe adoption (item 16b) | none |
| G15 conditions | High | covered (singles) | m0913 (E57), lower camera court (item 10, E55); doubles stopped (the user) | Misha as a new singles video (milestone batch) |
| P1 two-pass | High | covered | E84: not adopted (events lost) | none |
| P2 dim pass | High | covered | E80, E83: `dim` stays | none |
| G3 calibration | Medium | covered | bench calibration table; "check first" 70 %, minWinnerPercent 50 (decision 2026-11-26) | none |
| G5 rally_base | Medium | covered | E62 | none |
| G6 court | Medium | covered | E82: held out 0.70 px | none |
| G13 silent failures | Medium | covered | item 3: warnings in `result.json`, partial result | none |
| G14 contract | Medium | covered | version check, fixture test (item 7) | none |
| G9 identity | Medium | covered | E65, E68 (lab held out), E69 (Igor); sides step for the reference (item 11) | the user's sides step |
| G11 corrections | Medium | covered | E53 | none |
| G7 duplicated rules | Low | covered | E115 measure; owner copies (a) to (d): CHAIN_NETHIT B, SERVE_LAND_EVENT and others on | none |

Event measures (priority 2), not a gap row of the inventory:

| Event | Now (lab, frozen flights) | Open work |
|---|---|---|
| Serve event at a point start | far starts: 10 fixed by KEEP, 17 wait for the head flights (E172) | S4 E172 recount on S1's head flights; SERVE_PRIOR_NEAR only if needed |
| Net touches | 26 to 28 of 35, false 8 to 3 of 70 with netBand on (E137, default on); user labels 14 of 33 | 5 target misses on the S5 case list |
| Bounces | 37 of 42 | none now |
| Ball detector | option 0, no training (plan section 4) | open again when an event loss is measured |
| Audio hits | stopped (+4 true, +5 false) | open again with more labels |

## 2. Hard limits of the lab PC

- One GPU (RTX 3060 Laptop, 6 GB) and 16 GB RAM. **Only one heavy job at a time.** A heavy job uses the
  GPU or decodes a full video. Start each heavy job through the lock:
  `python tools/auto-analysis/gpu_lock.py run --who <stream> --what "<text>" -- <command>`.
  `gpu_lock.py status` shows the holder. The log is `D:\bananashot-lab\locks\heavy.log`.
- Run a heavy job in the background. Do not wait in a loop for it.
- **Run each heavy job and each benchmark from a code snapshot of one commit**, not from a checkout that can
  change: `python tools/auto-analysis/bench.py snapshot <commit>` (writes `D:\bananashot-lab\ws\s1\code\<commit>`).
  Use a commit of 30ef13a8 or newer: the older `gpu_lock.py` has no queue order.
  A running `detect.py` loads the chain modules only when each stage starts, so a merge or an edit during the
  run mixes code versions. Record the commit in the result.
- Light jobs (scoring, small model training on existing features, plots) do not need the lock.
- A script that imports torch must take the lock or force the CPU (`CUDA_VISIBLE_DEVICES=""`). Any CUDA context
  takes GPU memory: a small model on the GPU next to the ball stage stopped the Igor run with "out of memory"
  (2026-11-26).
- **Full-video runs only at milestones (the user, 2026-11-26).** A run of a full match (the ball stage takes 1.5 to
  4 h) is not a regular check. Regular checks:
  - A change of the chain (the stages after the ball file): the cached bench (`bench.py`: the 4 lab matches and the
    Igor light stages on the existing ball files). It takes about 1 min.
  - A change of a heavy stage (ball detector, decode, court, pose): first only the labeled windows or short clips
    (the ball, bounce, net and hit label windows, as in E80 and E88), not full matches.
  - A full-video run needs the manager's approval. It is for a milestone only: a heavy-stage change that passed on
    the samples and becomes the default, a new held-out video, or the end of the program. The manager collects
    such runs and starts them as one batch.
- - Disk C is almost full. Put all data on D.

## 3. Streams

| Stream | Owner | Gaps | Reviews |
|---|---|---|---|
| S1 bench | session "Chain inventory audit and impact ratings" (already active) | G1, G3 | R0 |
| S2 plumbing | new session `s2-plumbing` | G2, G12, G13, G14, P4 | R1, R8 (part) |
| S3 rally | new session `s3-rally` | G4, G5, G8 (short points) | R2 |
| S4 truth | new session `s4-truth` | G10 (hit, hitter, shot count, faults) | R5, R6 |
| S5 speed and coverage | new session `s5-speed` | P2, P1, G15, G6 | R3, R4, R9 |

### S1 bench (G1, G3)

1. Commit E42 (the uncommitted `chain.py` move) to `auto-detect` first. All other streams start from it.
2. Held-out benchmark of the shipped path: `detect.py` on Igor (m1116), scored with `score.py`. Register the
   test videos with a `testOnly` flag, so that no training script uses them.
3. Frozen inputs: a manifest for each benchmark run (commit, model hashes, input hashes). A second run with
   the same manifest gives the same numbers.
4. One command gives the benchmark report: the lab LOMO set (4 matches) and the held-out set (Igor; later
   Misha). Report rallies, ends, short points, faults, winners, and the noise.
5. Calibration (G3): confidence bins against the accuracy on the held-out rows. A proposal for
   `minWinnerPercent` and the "check first" limit.
6. Service for the other streams: run the benchmark on a branch when a stream asks.
6b. (2026-11-26) The lab set of the bench uses the frozen E38 flights: a change of the bounce or flight code does
   not reach the lab bounce rows (S1). Fix before item 7: the lab flights are made again from the frozen ball files
   when the G12 key of the flights code changes, cached under that key (CPU). Null check: the old key gives the
   frozen flights. A run of the flights stage from cached ball files is not a full-video run.
7. (2026-11-26) `bench.py heavy --stage <ball|court|pose> ...`: the regular check of a heavy-stage change on the
   label windows only (the rule of section 2). (a) Ball candidate recall and false blobs on the labeled frames
   (S5 E80 method). (b) Events on the bounce, net and hit label windows (S5 E88 decode, a decode cache on D).
   First a null check: with the current weights, the window result must equal the full-run files on those windows
   (E84 lost events on windows). Target: minutes. Start after the Igor reference run.
8. (2026-11-26, the user) **Case library**: short video fragments cut from the source videos, each with a known
   error kind, its labels and the history of its detection results. It replaces the window decode of item 7(b) and
   also runs the full `detect.py` chain on a fragment. Design (manager):
   - A fragment is one marked point (or a few) with a pad before and after (warm-up of the tracker and of the rally
     GRU). Cut with stream copy from a keyframe (`-c copy`, no new encode: a new encode changes the pixels and the
     detector output). Record the offset to the source time; the labels of the source move into the fragment time.
   - The court comes from the full video (an input of the fragment), unless the case is a court case.
   - Each fragment enters the library only after a null check: the current code on the fragment gives the same
     events as the full run in the same time span. A fragment that fails the check is not used (or the pad grows).
   - Kinds: one folder or tag for each error kind (for example far serve not seen, depth error of the serve fit,
     net touch seen only in the net, missed short point, false rally, late shot, dead ball, fault as a rally) and
     a **control** group of fragments that are right now (a guard against regressions). About a third controls.
   - History: each run adds one row for each fragment (commit, each event against the labels, solved or not).
   - Limits: the cases come from the lab matches and Igor, so a fix that only solves the library cases is tuned to
     them. The milestone full runs and a clean held-out video stay the final measure.
   S1 builds the tool and the format; each stream gives the case list of its own error kinds (from its journal
   rows). Start with about 30 to 40 fragments (target: under 20 min of video, under 30 min for all stages).
   **Four levels (the user, 2026-11-26).** A level is defined by its content; the length follows. Lab facts (4
   matches): a point every 26 to 32 s, a game 3.2 min (median; p90 6.2), an end change every 6.5 min (median; max 16).
   | Level | Content | Length | Set | Checks | Run when |
   |---|---|---|---|---|---|
   | small | 1 marked point with a pad | 30 to 60 s | 30 to 40 fragments by error kind + controls | events in the rally (hits, hitter, bounces, nets, serve, last shot) | each heavy-stage change |
   | medium | 2 to 3 games across one end change (a changeover pause in it) | 7 to 10 min | 1 for each singles video (lab 4, Igor, m0913) | rally finding between points (pauses, ball pick-up, changeover), fault and second serve, server change, end change, identity | a heavy-stage change before its merge |
   | large | a block of 5 to 9 games | 20 to 30 min | 1 for each singles video | identity and side over time, the statistics of a long part, drift | milestone batch |
   | match | the full match | 48 to 150 min | all marked singles videos | the full app path from start to end | end of the program, release |
   The cost of a level is in the ball stage only. A change after the ball stage runs all levels from the cached files;
   the cached match level (`bench.py`, about 1 to 20 min) is then the right check, not the fragments. The levels are
   for heavy-stage changes. Small and medium fragments take the whole-match context (feature statistics, identity,
   sides) from the full run; a large fragment also runs once without it, to measure how much a part of a match
   differs from the whole. Build order: small, then medium, then large.

Done when: the Igor number is in the journal, the benchmark is one command with a manifest, and the
calibration table exists.

### S2 plumbing (G2, G12, G13, G14, P4)

1. G2: map each 1 fps row (DINO, pose) to its keyframe time in all readers (`rally_model`, `chain`,
   identity, server vote). Measure the effect on m1030 (rally, server, identity). Fix the `kf.fps1` offset.
   Rally times in `result.json` are video seconds.
2. G12: cache keys. The work folder and the ball file name include the video identity and the weights and
   code version. A setting replaces each environment override. The overlay belongs to its result.
3. G13: each fallback (shot chain fault, fault chain fault, missing weights, missing court) is a warning in
   `result.json`. An exception after the rally stage still writes a partial `result.json`. The app shows all
   warnings.
4. G14: the app checks `version` and the video. A test reads a real `detect.py` output.
5. P4: the 1 fps pictures come from the ball decode when the video has no keyframe each second (only if
   cheap after items 1 to 4).

Done when: items 1 to 4 are merged with tests, and the m1030 measure is in the journal.

Phase 2 (from 2026-11-26; items 1 to 4 are merged: E50, E51):

6. Reuse of existing ball files under the new cache keys (G12): an explicit command that records the key of an
   old ball file when the operator states its weights and passes. No automatic reuse. Goal: no 3.5 h ball pass
   again for Igor, the G15 videos and the lab folders.
7. The G14 fixture test (a real `result.json` of the m1020 sample).
8. G11 corrections loop: `corrections.py` reads `swapped`, `minWinnerPercent` and `auto.checked`; it tells "Swap
   players" from a winner fix; the bounds compare with the gap clamp and the rounding; "added" counts only clips
   after the import; the review notes name the result file and the run. Done (E53).
9. P4: the 1 fps pictures from the ball decode, for a video with no keyframe each second. Measured (E54) and
   closed: see the decisions.
10. G15 (moved from S5): the court detection on the new lower and wider camera (the two new clips): cause, a
    small general fix or a clear warning.
11. G15 coverage report (moved from S5): score the S5 runs of m1001 and m1016, report m0913 without a point
    score, and list the conditions that the app must warn about or refuse.
    Changed (the user, 2026-11-26): singles only. The m0913 part is done (E57). The doubles part (m1001, m1016)
    is stopped.
13. (2026-11-26) A G12 key gap (found by S1): `feat_wasb.py` imports `serve_ball`, and the flights stage writes
    `feat_wasb.npy` (a rally-model input), but `serve_ball.py` is not in the flights key. After a serve_ball change
    (E77, E126) the app keeps a stale `feat_wasb.npy`. Fix: the key from the import closure of each stage, or a test
    that fails when a stage imports a module that is not in its key. After item 12.
    Decision (manager, 2026-11-26): flights, court, court2, audio and media get import-closure keys (cheap reruns).
    Ball and pose keep a hand list (ball: + burst_ball, cuda_scale, platform_cfg; not lab) and a test that fails when
    the closure grows (each new import: into the key, or onto a "no effect" list with a reason). Existing ball and
    pose files are adopted under the new key (E52) only when git shows no change of the added modules after the file
    was made; else they are listed as stale. Cause: no 1 h ball rerun for a change of lab.py or other non-detector code.
12. (2026-11-26) The net-touch loss on m0913 (E57: recall 83 to 58 % of 12 labels, false touches 1 to 4; the new
    run has 239 net events against 156). Find the cause of each lost and each new false touch: the new ball file
    (`dim`, E38 weights) or the net code. Then one fix that is right in principle, measured on the lab net labels
    first and m0913 apart. Ask the manager before an edit of `net_events.py` (S5) or `shot_chain.py` (S3).
    Done, no fix (E110, E111; merged 2026-11-26): the cause is the new ball detections (`dim`). With them the track
    goes on through the touch (the ball is seen to fall), so no candidate is at the touch; new short tracks in the net
    band give false touches. A flight end as an extra candidate gains about one touch for each new false touch (lab
    26 to 29 of 35, false 8 to 14 of 70): not applied. The real fix is a candidate from inside a flight (a kink or a
    speed loss at the net plane) with new judge rules, together with S5 item 6 (net-band signal).

15. (2026-11-26) G7, duplicated rules: first a measure, no edits. A table of each copy (the 5 bounce copies, the 3
    winner copies, the good-flight filters, w_old and old_case, netHit against the net case of the chain): file,
    threshold, user stage, effect on the app result. The disagreements of the copies on the cached lab data, and which
    copy is right where the user's event labels exist. Then a proposal of one owner copy for each rule. The manager
    decides; the stream that owns a file makes its edit.

### S3 rally (G4, G5, G8 short points)

1. G4: a rally end detector (E21c) in the seconds around the GRU end. Use the ball flights, the audio hits
   and the shot chain. Measure the end error and the winner evidence against the marked-end result.
   Target: recover at least half of the gap between the detected end and the marked end (+2.4 points of
   evidence, E28c).
2. G8 short points: only about 43 % of the added short points are real (E21b). Target: precision of the added
   short points 70 % or more, with a loss of not more than 10 % of the real short points. If the target is
   not met, the default goes off, and the journal says why.
3. G5: measure `rally_base.pt` (no ball features) on the app path. This is the fallback when the court gate
   fails, and it is the base of a two-pass design (P1, S5).

Done when: the end detector and the short-point rule are merged and measured on the S1 benchmark.

Phase 2 (from 2026-11-26; items 1 to 3 are merged: E60, E61, E62):

4. G8 score layer: done (E63, E64). The user decided: no change of the app score (decision 2026-11-26).
5. G9 identity held out: `identity.p1_near` on Igor (m1116) when the S1 result exists; the effect of a wrong
   color run on the winners.
6. (2026-11-26) Wrong shots in the shot chain (S4 E76, groups D and E): stop the shot list at a dead ball (4
   false hits after the point, 1 wrong winner, 2 wrong last hitters), then the split shots (5 false). Do it
   together with the last-shot check of item 5 (S3 owns `shot_chain.py`). Group A (late "back" shots) later.
   Done (E90, E91; merged 2a3c8578): Igor false hits 21 to 16 with S4's E121; lab rallies the same, winners +1.
7. (2026-11-26) Late shots (E76 group A, and 3 of the 5 group E hits): a shot found 0.4 to 0.9 s late, mostly a
   "back" shot whose hit flight is not seen. Each one is a missed hit and a false hit. Find the cause and fix
   the shot time (for example from the hitter's pose, the audio hit or the back-projected flight). Then the
   10 E69 rallies where the true last shot is not in the chain. Same Igor rule.
   Done (E92, E93; merged 3917a135): the back shot time comes from the meeting point of the ball lines. Igor
   hits found 79 to 83 of 91, false 16 to 12; lab winners +2. The 10 "later shot" rallies are a tracking gap
   (a tracking item, not a chain rule).
8. (2026-11-26) False shots before the serve (E76 group C: 3 false hits; ball handling). When this is fixed,
   `serve_time()` can go back to the first shot only (E123). Then a fresh table of the Igor hit errors on the
   current code (missed, false, shot count), so that the next fix starts from the largest group.
   Done (E94, E95; merged c3d319b1): Igor false hits 12 to 11, shot count right 5 to 6 of 11. Fresh table: 19
   errors; the largest group is T (time error over 0.3 s, 5 shots, 10 errors, mostly a ball first seen after
   the net).
9. (2026-11-26) Group T of E95: the time of a shot whose ball is first seen after the net. Use the same idea
   as `back_time()` (the outgoing line, extended back to the hitter or to the incoming line) where it is right
   in principle. Same Igor rule.
   Done (E96, merged 2aedf89e): Igor hits 83 to 84 of 91, false 11 to 10. The other 4 T shots need better
   tracking or fits (a tracking item).
10. (2026-11-26) Priority 1, rally precision: Igor finds 224 rallies for 197 marked (precision 86.6 %), the lab
    90.9 %. Make a table of the false rallies (Igor and lab) and the missed ones, with a cause for each. Then
    fix the largest group. Same Igor rule.
    Done (E97, E97b, E98, E99; merged 9ef456c4): lab precision 90.9 to 93.0 %, recall 93.6 to 93.5 % (95.1 %
    without the 12 NONE marks); Igor precision 86.6 to 89.0 %. Of the 27 missed lab points (not NONE), 19 have
    the rally GRU below 0.35 for the whole point: the cause is the rally model.
11. (2026-11-26) The missed rallies of the rally GRU (E99: 19 points). First a diagnosis on the CPU: what do
    these points have in common (length, serve, ball features, place in the match)? A new training of the GRU
    is a model change: ask the manager first, with the plan and the GPU time.
12. (2026-11-26, decision of the user) The winners of the chain come from the evidence of each rally; the score
    decoder does not change a winner (a switch, default off; the decoder code stays). Then S1 measures the lab and
    Igor (rallies, events, winners as a check) against the current default. If a result or the app contract needs
    a decoder output (for example a score state), keep it, but it must not change a winner. Journal id from E140 to E149.
    Done (E142, merged 2026-11-26): `CHAIN_DECODER`, default off. Lab, 527 matched points: decided rows 84.1 to 85.6 %
    right; points with no winner 5 to 21 (the app asks the user there); with no winner counted as wrong 83.3 to 82.2 %.
    Igor apart: decided rows 83.6 to 83.9 %. Rows and events the same. G14 tests pass.

### Rule for fixes found on Igor (manager, 2026-11-26)

Igor (m1116) is test only. Its hit and fault labels show the causes, but a threshold value must not come from
the Igor numbers. A fix must be right in principle, and its values come from physics or from the lab matches.
Measure each fix paired on the lab matches (rallies, bounces, nets, winners) and report Igor apart. Each
journal row of such a fix says that it was found on Igor, so that Igor is less clean for that part.

### S4 truth and shots (G10, then G7 and G8 score)

The user marks the labels (decision 2026-11-25). The agent makes the tool and the work lists, and does not
label by eye.

1. A labeling tool: a local HTML page (open with `file://`) that plays a rally clip and records with keys:
   each hit (time, near or far player), the shot count, and a first-serve fault. Output: one JSON file for
   each match, with a version and a source. The clip list comes from the marked points, not from the chain
   output (bias).
2. A work list for the user, in priority order: Igor first, then the 4 lab matches; a stratified sample
   (short, middle, long rallies). Target: 150 rallies or more on 3 matches or more. Measure the user time for
   10 rallies first and give the user an estimate before the full list.
3. Fault labels from the 84 serve-fault clips of Igor (coordinate with S1). Measure the fault candidates
   against them.
4. While the user marks: audio hits (`hits.npy`) as a hit event for the shot chain (H32), checked on the
   existing 13 hits and the 62 bounces; one bounce rule and one winner rule (G7). Paired measure.
5. When the labels arrive: measure the hitter and the shot count of the shot chain.
6. (Moved to S3 phase 2 on 2026-11-26.)
7. (2026-11-26) Serve detection (E73: 21 of the 39 missed faults have no serve event; a serve event at only
   58 % of the Igor starts; E76 group B: the serve shot starts at the toss). Causes: the toss starts at 1.2
   to 1.5 m and `is_toss` needs more than 1.5 m; the fast serve flight is not tracked. Fix the serve event
   first, then the serve shot time. Follow the rule for fixes found on Igor. Ask the manager before an edit
   of `shot_chain.py` (S3).
   Done (E77, E121; merged cb4f9fd5): the toss starts from 1.0 m; the serve shot starts at the contact. Open: the
   serve flight is often not in the ball track (107 of 277 lab starts): a ball-tracker item for later.
8. (2026-11-26) A rally that starts at a first-serve fault (E73: 9 of the 39 missed Igor faults; a priority 1
   error of the rally start). Find the cause, then a fix that starts the rally at the second serve. Measure the
   rally start error and the rally precision on the lab matches; report Igor apart. Ask the manager before an
   edit of `rally_end.py` or `shot_chain.py` (S3).
   Done (E122, merged 8b932ef9): the cause was a fault serve detected as a short rally of its own. The tennis
   rule (after a fault the same side serves again) removes it. Lab precision 90.0 to 90.9 %, recall 93.8 to
   93.6 %. The serve side is known at only about 25 % of the starts.
9. (2026-11-26) The serve flight in the ball track (E77: no serve event at 107 of 277 lab starts; E122: the
   serve side is known at only about 25 % of the starts). First a diagnosis on the existing files (CPU): is
   the ball seen in the blobs but not linked into a flight, or not seen at all? Then a fix of the link step if
   that is the cause. A change of the ball model is a new item: ask the manager first.
   Part done (E124 to E126, merged 2026-11-26): (a) the far serve before its bounce (68 of 228 starts) is not in
   the blobs (61 of 68 have no fast blob run): not a link fault, it needs a detector change (a model item, open).
   The return filter removed 22 serve flights; with a speed limit of 240 km/h (physics) 11 come back. Lab serve
   event at a point start 59.5 to 62.9 %, rallies and events the same, winners inside the noise; Igor apart 123 to
   128 of 197 starts. Next: (b) the 43 depth errors of the fit (tell S5 before an edit of `ball_3d.py`).

10. (2026-11-26, the user) **Label page v2 for the case library.** The user labels all significant events of the
    library fragments, small level first, then medium. This replaces the 180-rally hit work list (the 11 Igor
    rallies stay). Events for each fragment, in source time:
    - serve: the racket contact of each serve (server near or far), and a first-serve fault flag;
    - shot: the racket contact of each other shot (near or far);
    - bounce: time and pixel of the ball at the ground; the court place comes from the court of the fragment (the
      ball is at height 0 there); in or out; **seen** or **inferred** (the bounce is hidden, but the flight before and
      after shows where and when it is; the user puts the mouse there). Inferred bounces are later training data;
    - net: a net touch that stops the ball, or a net cord that goes over.
    Tools for the user: frame step, slow play, sound, a zoom near the far baseline, undo, a list of the marks.
    The page reads the fragment manifest of S1 (clip, source offset, court, the marked points) and writes one
    label file for each fragment, with a version and the labeler. Measure the user time on the first 5 fragments
    and give an estimate before the rest. Before item 9 (b); S1 and S4 agree on the manifest format first.

11. (2026-11-26, the user) **Sides step in the label page.** The reference of "who is near" is not checked by a
    person: `ends.json` comes from the end-change rule, and players do not always follow it (m1021 points 99 and
    100 after a long break; Igor at a set break). The user marks the near player once at the start and once after
    each possible end change. Candidates for each singles video with point marks (4 lab, Igor): each point where
    the rule (`ends.json` nearPlayer) or the chain identity (`p1Near` of the current bench rows) changes, plus about
    5 random points as controls. The page shows one frame from the middle of the first rally after the change (with
    the player names of the project); the user presses 1 or 2. Output: `sides_<match>.json` (times, near player,
    labeler, version). Then the benchmark takes the near player from these marks (the rule only between marks).
    Target: 2 to 3 min for each match. A reference is never corrected from the detector.

Done when: the tool and the work list are with the user, the fault measure and the audio-hit measure are in
the journal, and the hitter measure follows the labels.

### S5 speed and coverage (P2, P1, G15, G6)

1. P2: the E38 weights with one pass against the `dim` pass, on the labels of `ball_labels_e27.json` (label
   windows only, no full runs). Decide the default.
2. G15: `detect.py` on the marked doubles matches m1001 and m1016, on Vlad (m0913) and the two new clips.
   Report each condition apart from the main numbers. List the conditions that the app must warn about.
3. G6: a held-out court test (leave one video out on the 165 marks).
4. P1: a two-pass design (rally candidates with high recall first, then the ball pass only in the windows).
   Coordinate with S3 (G5). Target: 35 % less run time with a rally recall loss of not more than 0.5 points.

Done when: P2 is decided, the coverage report is in the journal, and P1 has a measured prototype.

Status 2026-11-26 (manager):
- P2 closed (E80, E83): `dim` stays the default. One pass gives 58 to 64 % ball recall on m0913, against 92 to
  94 % with `dim`. Batching gives no gain.
- P1 closed (E84): the cheap first pass finds the rallies as well as the full ball pass (recall 93.8 % against
  93.5 %). But the ball pass in the windows only loses 118 events in 35 rallies. Most of them are not at a window
  edge. With a +2 s pad, it still loses 90 events, and the time gain is about 11 %. Events come before speed,
  so the two-pass design is not adopted.
- G6 done earlier (E82): held out 0.70 px against 0.67 px in sample. The first court on the lower camera is S2 (E55).
- G15 (the user, 2026-11-26): singles only. m0913 is done (S2 E57). The doubles runs (m1001, m1016) are stopped.

5. (2026-11-26) Bounce and net-touch errors (user priority 2, events). Take each miss and each false event of
   the E75 measure (bounces 37 of 42, net touches 28 of 35) and the user's hit labels. Give each one a cause:
   a gap in the ball track, a 3D height or place error, a court error, a time offset, or a threshold. Then
   try one fix that is right in principle and measure it with `score_events` (events first, then rallies, then
   winners). S5 owns the bounce and net-touch detection code for this item. Ask the manager before an edit of
   `shot_chain.py` or `rally_end.py` (S3), `score_events.py` (S4) or `bench.py` (S1).
   Done (E85 to E87, merged db0c5f82): the bounce test fits again without a far sample (bounces 37 to 38 of
   42, rallies and winners the same). Not done, single cases: a minimum speed for the weak hit rule (1 case),
   and two events at one kink for a half-volley (1 case, S3 chain). Open them only when more cases exist.
6. (2026-11-26) Net touches that only the net shows (E87: 3 to 4 of the 11 misses; the ball track does not
   change). Make a prototype of a signal from the picture of the net band (motion of the net in a short window
   around each candidate time). Use the label windows only, under the GPU lock if it decodes video. Measure net
   touches found and false. Start now (the doubles runs are stopped, 2026-11-26).

   Measured (E88, E89): reject (S < 0.08) and confirm (S >= 0.15 with no motion in a control band above the cord):
   lab 26 to 30 of 35 touches, false 8 to 4; m0913 10 of 12, false 0 to 1. Decision (manager): adopt in principle,
   with conditions. (a) Thresholds held out: fit on 3 lab matches, test on the 4th (each match once); the gain must
   hold. (b) The signal is a new output with its own cache key, made in the decode loop of the ball stage; the ball
   detections, the ball file and its key do not change, so no ball rerun. For an existing folder, a fill-in that
   decodes only the windows of the net candidates. S5 owns the edit; S2 reviews the stage keys. (c) Order: (a), then
   item 7 (plan), then the edit.

7. (2026-11-26, approved by the user) **Plan for the ball detector.** The ball tracking is the base of all
   detection, so it must be as solid as possible. First a written plan, no training yet:
   `docs/auto-analysis/ball-detector-plan.md`. It holds: the target errors with counts (the far serve before its
   bounce was target A, from E125, E126 and E129; E150 found it wrong: 58 of 61 far serves are in the blobs, so
   target A leaves the plan, see the decision of 2026-11-26 in section 6; the track through a net touch and the short tracks in the net band,
   E110; shots first seen after the net, E95, E96; the 4 T shots); the cause of each one (small far ball, motion
   blur, resolution of the input, the dim pass, occlusion); the options (for example new training data at the far
   end, a higher input resolution or tiles at the far end, a model with more frames, a change of the link step);
   the training data and the labels it needs (say clearly if the user must mark ball positions, and how many); the
   GPU time on the 6 GB card; the measure (S1 `bench.py heavy` ball recall on the labeled frames, then the small
   level of the case library, then the medium level; full videos only at a milestone); and the risks (the false
   blobs, the bounces and nets that depend on the current track, see E110). Input from S4 (serve diagnosis) and S1
   (tools). The manager and the user approve the plan before any training.

## 4. Rules for all streams

- **Branches.** Each new stream works in its own git worktree, on a branch from `auto-detect`. The manager
  merges a finished piece into `auto-detect`. Do not merge into `master`.
- **Lab data.** Do not replace a file in `D:\bananashot-lab\data\<match>\` or in an app work folder that
  another stream can read. Write new outputs to `D:\bananashot-lab\ws\<stream>\`, or to new files with the
  stream tag in the name. S1 decides when a new input becomes the default input of the benchmark.
- **Journal ids.** To prevent collisions, each stream takes its experiment ids from its own block:
  S1 E43 to E49, S2 E50 to E59, S3 E60 to E69, S4 E70 to E79, S5 E80 to E89. Second blocks (2026-11-26): S1 E100 to E109, S2 E110 to E119, S3 E90 to E99, S4 E120 to E129, S5 E130 to E139. Third block: S3 E140 to E149, S4 E150 to E159.
  Add each experiment to the journal at once, also a failure.
- **Measure.** Report the paired result of the part first. Then the S1 benchmark result with its noise.
  Apply a change that is right in principle and improves its target cases, also when the full-chain number
  stays inside the noise (decision 2026-11-23).
- **Shared files.** `chain.py`, `detect.py` and `shot_chain.py` are shared. Keep each change small and in
  its own commit. Tell the manager before a large change of a shared file.
- **Interfaces between streams.** Each shared interface has one owner, and only the owner changes it, after a
  message to the manager. The other stream adapts. Fixed now: `score_events.measures(mid)` (flat list, for
  `bench.py`), `score(mid)` = `detail(mid)` (the dict), `total()`, `lines()`; owner S4. `bench.py`: owner and
  only editor S1, also for the manager; send S1 a change request. Cause: crossed changes of S1, S4 and the
  manager on 2026-11-26.
- **Live status (2026-11-27, the user).** The worker list on the lab dashboard must be current at all times.
  Each stream writes its status with `python C:\w\banana-shot\tools\auto-analysis\lab_status.py` (the main
  checkout path, no rebase needed): `action <id> --who S<n> --status running|waiting|done|dropped` at each change
  (waiting needs `--on user|manager|queue|S<n>`, who must act, and `--waiting-for`; done needs `--real 0-10
  --outcome`). Each entry is a full state (note and waiting fields do not carry over). The top line of a card is
  the newest entry: write a running action or `now --text` when a turn starts work, and `idle` at the end of
  each turn. `now` is only for work that runs now, never for a wait. The log is `D:\bananashot-lab\status\status.jsonl` (append only).
- **Done when (2026-11-27, the user).** Each action states its target result so that anyone can check it
  objectively: `doneWhen` in lab-plan.json, a list of checks, each with the place to check it (a count on the
  dashboard, a file and its content, a journal row with named numbers). "Analyse X" is not a target; "a cause
  table of all N cases, 0 without a cause, with a fix proposal" is. An action is done only when all its checks
  pass; the outcome names the result of each check. An unplanned action that a stream starts gets
  `--title` and `--done-when` in `lab_status.py`. The dashboard warns for an open action without it.
- **Reports.** At the end of each turn: 2 to 5 lines. What is done, the numbers, what is next, what blocks
  you. Put a question to the user on its own line.
- **Questions to the user** go through the manager, unless only the user can do the step (marking a video).

## 5. Decisions

| Date | Decision |
|---|---|
| 2026-11-25 | Program set up by the manager: 5 streams, the rules of section 4, one heavy job at a time through `gpu_lock.py`. |
| 2026-11-25 | Branches (the user): each new stream works in its own worktree, on a branch from `auto-detect`. The manager merges into `auto-detect`, commits and pushes to `origin/auto-detect`. |
| 2026-11-25 | Labels (the user): the user marks the new labels (hit, hitter, shot count, fault). An agent makes the tool and the work list, and does not label by eye. |
| 2026-11-26 | Code snapshots (S1 proposal, manager): heavy jobs and benchmarks run from a snapshot of one commit (section 2). Cause: a merge into the main checkout during the Igor run would have mixed code versions. |
| 2026-11-26 | Short points off by default (S3, E61): only 18 of 62 added short points were real; no rule reached the 70 % target. Precision of the rallies 82.1 to 87.8 %; the real aces and double faults are lost until a better rule exists. |
| 2026-11-26 | S3 phase 2: G8 score layer (moved from S4) and G9. S4 keeps the labels, the fault measure and the hitter measure. |
| 2026-11-26 | G2 to G14 merged (S2, E50, E51) by the rule of 2026-11-23: rally recall 90.3 to 93.8 %, precision 87.8 to 90.0 %, end to end 78.3 to 78.9 % (inside the noise). Open: on m1030 the winner on matched points goes 88.0 to 80.7 % (13 of 18 changed winners become wrong) because the GRU ends move; S3 investigates. |
| 2026-11-26 | S2 phase 2: reuse of old ball files, the G14 fixture, then G11. |
| 2026-11-26 | Score (the user, on the S3 options of E63): the score is not the goal; it is only the result of the points. Priority 1: detect the correct rallies. Priority 2: detect the correct outcome of each rally. No change of the app score now (G8 score part closed, option A stays). The benchmarks do not use score measures (score right before a point, games right): a score error drags along and tells little about a single point. Report the rally measures and the winner of each rally. (Correction 2026-11-26: the role of the decoder in the winner choice is open; the user did not decide it, see E66.) The start score is not asked: it does not change a winner (E63). Later item (out of the current scope): consistency hints from the score (the serve side does not match the score; the server changes inside a game) that show the user where a point is probably missing or false, on review or later, or that correct the detection. |
| 2026-11-26 | Event measures (the user): priority 2 is measured by the events (shots, hitter, bounces, in/out, net touches) against labels, not by the outcome. A right outcome for a wrong reason does not count as a gain. Section 1 has the rules. |
| 2026-11-26 | P4 closed (manager, E54): the gain is 6 to 7.5 % of a run, and only for a video with no keyframe each second; the user's own videos have one each second. The design changes the stage order of `detect.py`. Open it again only if such videos become common. |
| 2026-11-26 | Vlad (the user): the user marks the points and winners of m0913 in its project. Then S5 scores m0913 as the low-camera, 30 fps condition of G15. Until then, S5 reports m0913 without a point score. |
| 2026-11-26 | P1 and P2 closed with no change (S5, E80, E83, E84): `dim` stays; the two-pass design loses events in found rallies. |
| 2026-11-26 | Net-touch input of the benchmark (S1, after S5 E85): the lab set uses net events made the way `detect.py` makes them (`bench.py nets`, hash in the manifest). The old lab file stays for the old journal rows. The first run after the switch starts a new baseline. |
| 2026-11-26 | First held-out result (S1, Igor m1116, code 995fc8c8): rally recall 98.5 %, precision 86.6 %; hits found 77 of 91, hitter right 77 of 77, shot count right in 3 of 11 rallies, last-shot end right 64 %; winners 83.9 % (a check only). |
| 2026-11-26 | G3 (S1 proposal, manager): "check first" stays at 70 % and minWinnerPercent stays at 50. Held out, the winners under 70 % are about 55 % right (107 points in the pool). A calibration map of the shown percent is a later item (n is small). No app change. |
| 2026-11-26 | Igor identity (S3, E69): the chain identity (colors) has 0 wrong of 194 rallies, so the E68 L weight holds held out. The score-replay rules (`near_identity`, `sides.json`) miss an end change at the set break after an even set (5-7); the players changed ends there. These rules are a check only; the chain does not use them. Keep this case for the later score-consistency hints. |
| 2026-11-26 | Scope of the videos (the user): singles only. Doubles can need other methods; do not spread the effort now (the m1001 and m1016 runs stop). No new videos or new conditions (courts, camera angles, the moving-camera clips) until the chain is stable and the quality is good on the current videos. The user then gets more videos. Thus G15 ends with m0913 (E57); the work goes to the clear failures on the current videos. |
| 2026-11-26 | Full-video runs (the user): only at milestones, not for regular checks (section 2). Cause: on 2026-11-26 about 10 h of full runs (Igor, m0913, m1001) held the GPU, and short jobs waited behind them for hours. |
| 2026-11-26 | Case library (the user): short fragments of the source videos, grouped by error kind, with labels and the history of their detection results; the regular check of heavy-stage changes. Design in S1 item 8. |
| 2026-11-26 | Four control levels of the case library (the user): small (one point), medium (2 to 3 games across an end change, 7 to 10 min), large (20 to 30 min), match (full). By content, not by a fixed length. Levels are for heavy-stage changes; a chain change uses the cached full matches. |
| 2026-11-26 | Decoder (the user): a merge of the event evidence and the score evidence into one stronger confidence is the wish, but there is no clear way now. The decoder is that merge in principle, and the tests failed: decoder on all rallies +1.3 / -1.4 points (old / new rows, E66); decoder only where the evidence is weak +1.5 / -0.8; it spreads one wrong point to its game. Thus the decoder winners go off now (S3 item 12). Later item: score-consistency checks from the evidence (hints, and later maybe a confidence), when the events are reliable. |
| 2026-11-26 | Labels (the user): label all significant events (serve contact, each shot, each bounce with in/out and seen or inferred, each net touch) on the case library fragments, small level first, in a new label page (S4 item 10). The controls of the small level are a random stratified sample of points (no bias to easy or hard cases), next to the error cases. |
| 2026-11-26 | Identity reference (the user): the user marks the near player at the start and after each possible end change (S4 item 11, sides step). The reference of the near player then comes from a person, not from the end-change rule; a disagreement of the detector and the rule is never solved with the detector. |
| 2026-11-26 | Igor hit labels, rally 3 (the user): the labels stay as marked. The fast far flight at 5512.21 s, 0.25 s before the first mark (a near serve at 5512.46 s), is a detection error (a far serve cannot reach the near player in 0.25 s); it belongs to the wrong-depth fit group of S4 item 9. |
| 2026-11-26 | NONE marks (the user): a NONE point has different causes (a point played again because its outcome was not clear, a no-point kept only for the video, a point played by mistake). The benchmark ignores them in both directions: a NONE point is not a missed rally, and a detected rally on a NONE point is not a false rally. The app detects the whole match with best effort; nothing changes there. The case library does not use NONE points. |
| 2026-11-26 | Misha (m1113, the user): the user's project "Dima-Misha" (230 clips, 173 with a winner) is registered as the second test-only video. No stream looks at its results before the milestone batch; its first run there gives one clean held-out number. Igor stays a test video, but it is less clean: about 10 fixes were found on its errors. |
| 2026-11-26 | Ball detector (the user): approved to open. The ball tracking is the base of the detection and must be as solid as possible. S5 writes the plan first (S5 item 7); training starts only after the user approves the plan. |
| 2026-11-26 | Far serve (S4, E150, image space): the ball detector finds the far serve. In [a-1, a+2.5] of the 61 found far starts, 55 have a down run of 5 or more blobs across the net row, 3 a run only above it, 3 no run. Of the 136 far starts with no serve event, 113 have such a run (median 10.2 px/frame); the run starts at median a+1.78 s, after the end (a+1.5) of the E126 and E129 windows. Thus E126, E129 and the S5 "no blobs" answer are wrong, and the error is after the detector (flights, serve event). Decision (manager): target A leaves the ball-detector plan; S4 finds why these runs give no serve event (image first), then changes serve_prior behind a switch. S5 revises the plan to targets B and C before it goes to the user. |
| 2026-11-26 | Labeling method (the user): the user labels each fragment from zero. The page does not propose marks from the detection now (more complex to handle); the user looks at ways to label faster later. Measured time on s001 to s005: about 5 min for a point of 10 to 12 marks after the first fragment. Estimate: small level (30 to 40 fragments) 2.5 to 3.5 h; medium level about 1.5 h for each fragment. S1 cuts the rest of the small level. |
| 2026-11-26 | Test videos in the case library (manager): the small and medium levels use the lab matches only. No Igor (m1116) and no Misha (m1113) fragments, also not as random controls: the streams look at the case-library results at each check, so a test video there becomes a tuning video. The random controls are drawn again from the lab strata (same seed rule). Igor and Misha stay for the milestone runs. |
| 2026-11-26 | Ball detector plan, revised (S5, 633bb1e7): in image space the ball is in the blobs for all three targets (A far serve: E150; B net band: the lost touches have blobs down to the net base, the false touches are a real moving ball; C near shots: a flight starts a median 0.16 s after a near hit). The errors are after the detector, and their owners fix them there (A S4, B S5 item 6, C S3). No training now. Step 2: a diagnosis of the labeled frames that the detector misses (m1021 66 %, ball on the court 57 %; other videos 4 to 8 %), with no user labels. Accepted by the manager; the user is told. C used the Igor hit labels for a diagnosis only; no threshold comes from it. |
| 2026-11-26 | Medium level (manager, S1 spec ws/s1/cases/specs/medium.json): one fragment for each lab match, 3 games across an end change, 60 s pad, no NONE points (m001 m1015 9.2 min, m002 m1020 7.9 min, m003 m1021 9.9 min, m004 m1030 8.4 min; 69 points). Cut all 4 now (stream copy, cheap). The user labels them after the small level, one at a time. The user's own small-level marks are mapped into the medium fragment (the same source time; they stay the user's marks, and the user can move or delete them); this is not a proposal from the detection. Estimate of the user's time: about 5 h for the 4. |
| 2026-11-26 | Milestone batch (S2 list, manager): (1) data\m1116 Igor, a normal run with no --force: audio, court, court2 and flights run again (about 40 min, CPU); court.json stays, so the ball tag stays and there is no ball run (E114: a court start moves the court less than 1 px). Add the S5 net band fill-in when detect.py has it. (2) Misha m1113, all stages (about 4.5 h; ball 3.3 h on the GPU). Both under the GPU lock, at the end of the week. No new court.json on Igor. |
| 2026-11-26 | G7 owner copies (manager, after S2 E115). Winner: the chain (shot_chain) is right in 61 of the 75 points where it and winner_sweep.decide differ; it owns the winner rule. (b) S2 moves the landing test of the old rule into the chain with the same decisions (as E74), then deletes winner_sweep.decide, match_decode.case_key, w_old, old_case and the FIRST_DT override; S3 reviews each shot_chain edit and does not edit shot_chain meanwhile. Then a paired test with the bounce event in place of the kink rule, measured on the events (bounce and hit labels), winners as a check only. (d) S2: one flight filter (shot_chain.usable + pos_ok) also for chain.py shots and the overlay. (a) Bounce fact = ball_bounce.events; kinks stay the candidates, the ball_3d anchor stays inside the fit; serve_ball.landing reads the bounce event: S4, after E151. (c) netHit from the net case of the chain: S3 with S5, after the net band merge. Limit: bounce_labels.json came from kink candidates, and the user's labels are few (17 bounces); re-measure on the case library when more fragments are labeled. |
| 2026-11-26 | Fault serves (the user): by the rules a point starts at the first serve. The detection always finds first-serve faults and writes them as fault clips (the advanced-mode model: a dimmed fault clip next to the point; the export option "Include serve faults and lets" decides the video, off by default; the statistics count the first serves). The detection has no setting for this. The benchmark does not count a detected fault serve before a marked point as a false rally; the case-library labels (serve with the fault flag) measure the fault detection; the old lab marks have no faults, so a fault there is not checked. App rule to remember (no action now): what the user sees, the user must be able to make again; if a simple-mode project ever gets fault clips from the detection, the user must be able to add and remove them there too. Today the import gives fault clips only to advanced mode (DetectImport.plan). A project setting such as "I mark first-serve faults" is an idea of the user, not a decision. |
| 2026-11-26 | GPU lock deadlock (manager): S4 E151 ran bench.py inside a gpu_lock wrapper (15:50). bench.py takes the lock itself when it must make lab flights (bench.py, the "remake" path), so its ticket waited behind its own wrapper; all streams waited until 21:20 (also PC standby 16:15 to 20:56). Rule: never run bench.py inside gpu_lock.py run; bench.py takes the lock when it needs it. S4 stops the job and runs the bench parts without a wrapper. |
| 2026-11-26 | Ball step 2 result (S5 E133c, 4f506f97; manager): no single cause. (1) m1021, 53 misses: a detector loss (small far ball in front of gray, median peak 0.10); only training can fix it. No training now: S5 first writes the step 3 plan (the event loss in the missed flight parts from stored data, the options with labels, user time, GPU time and risks, a recommendation); then the manager takes it to the user. (2) 19 near-threshold misses (median peak 0.45) on m1015, m1020, m1030: S5 may test a lower threshold where a path supports it (no training), measured with bench.py heavy and a paired event bench, off by default. (3) m1020 night far serves: mostly a late serve (net crossing at a + 2.5 to 4.2 s), not a detector loss; S4 measures a window to a + 4.5 s and the 6 points with no serve event, before G7 (a). |
| 2026-11-26 | Ball step 3 plan (S5 E135, 1cb68fea; manager): option 0, no training now. The m1021 frame loss (34 %) does not show as an event loss in the stored data (rally-row flight coverage 0.63 against 0.64 to 0.72; rows with a flight gap over 2 s 6 of 158 against 6 of 152 on m1020; E38d winners 78.8 % against 75.8 to 87.1 %). Limit: the bounce and net labels came from detector candidates, so a far-end event loss is not measured directly. Open again when a medium-level fragment or a new video shows an event loss; then option 1 (far-end fine-tune, E38 recipe; about 400 to 600 far frames for the user to check, 30 to 45 min; about 4.5 h training + 6 h held-out runs) comes before option 2 (far-end tile, 30 to 50 % slower ball stage). The user is told; no approval is needed because nothing trains. |
| 2026-11-26 | SERVE_PRIOR_WIDE on by default (manager, after S4 E151, 9faee3ac). Right in principle (the serve anchor is also tried when a plausible free fit flies to the near end from another depth), and the measures go up or stay inside the noise: far serve events at a + 2.5 s 170 to 197 of 291, near 216 to 212 of 263, false events inside points 64 to 61, lab rally precision 92.9 to 93.1 %, winners unchanged, Igor starts 137 to 141 of 197. S4 sets the default to 1 (ball_3d.py) and keeps the switch for one paired test. The m1021 4536 toss fit as a 142 km/h serve stays a case for the serve step (S4 items 1 and 2). |
| 2026-11-26 | Serve step fixes (manager, after S4 E152, 464fb199). A wider serve window (a + 4.5 s) is a measure only, not a change: it finds 201 of 291 far starts but also more starts with only a near-end event (3 to 14). Approved as two switches, each off by default, each with a paired bench (frozen flights) and the 23 night serves: (a) is_return ignores a flight whose fit starts more than 16 m from the net (|y0| over 16 m is not a real place; m1020 p59, p141); (b) serve_prior also tries the flight part after the last kink (toss and serve in one flight; m1020 p5, m1030 p127). Default on only if the measures go up or stay inside the noise. |
| 2026-11-26 | Serve step fix (c) approved as a third switch (manager, after S4 E153, 137ded73): a far serve flight must move down the image over the flight (net movement from the first to the last point, with a margin; not each step, because a kick serve can rise for a few frames). The case: m1021 4536, the rising toss (rows 396 to 295) has an anchored fit that passes all serve_prior checks, and the serve event comes 1.0 s early; it is in the frozen file too, so it is not from SERVE_PRIOR_WIDE. Far serve flights that move up the image: 19 of 207 frozen, 33 of 443 patched. Same rules as (a) and (b). |
| 2026-11-26 | G7 (b) and (d) done (manager, S2 E116, merge 3b3270c6). The old rule now lives in shot_chain (winner_sweep.decide, match_decode.case_key and the FIRST_DT override deleted); the old rule takes its seen bounce from the bounce event (CHAIN_OLD_BOUNCE=kink gives the old way); one flight filter (shot_chain.usable + pos_ok); the overlay keeps "good" and gets "used". Bench (frozen flights, lab LOMO 569 rows): rallies and events unchanged; winnerMatched 84.82 to 84.44 % (2 points of 527, inside the noise), end to end 80.69 to 80.32 %. Accepted: the change is right in principle (one bounce fact for all rules), and the winners are a check only (G7 decision). A bug in the series (9c71189d deleted the line "same", ecb14c8c puts it back) was found by the bench, not by a test: with CHAIN=0, chain.py caught the NameError, and 79 rows lost their chain case with no message. S2 makes such a caught error visible. Tests: lab unittest 31 OK, DetectContractTest 5 OK. Next G7 owners: (a) S4, (c) S3 with S5. |
| 2026-11-26 | S5 merge (manager, 325e754c): the net band in two levels (net_band_mask.py level 1 with THR 4 after E134; net_band.py level 2) and the detect.py stages netmask and netband. netBand stays off by default: it holds the held-out E131 result (29 of 35, 4 false) but not the fixed-value 31 of 35. Condition before it goes on: bench.py adds net_band.npz to the inputs of the flights key and to the manifest (S1). The path threshold (E136, E136b) is not applied: it finds 11 to 16 of the 19 near-threshold misses, but the lab false blobs go from 0.50 to 0.77 to 0.99 for each frame, and m1021 gains nothing; the code stays in ws\s5\code\57b3a70e. Cache effect: the flights closure key changes once (net_events imports net_band); the ball tag does not change. Tests: lab unittest 35 OK. |
| 2026-11-26 | Silent short points, group 1 (manager, S3 after E151): no rule. The serve event is on the serve flight at only 3 of 7 silent far-server points, before and after SERVE_PRIOR_WIDE, so "serve event + little play = a point" has no basis. Open again only when the S4 serve fixes (a), (b), (c) go on; then S3 measures it once more. |
| 2026-11-26 | Net band item 6 (d) approved (manager, S5 proposal): the full-chain test that decides the netBand default. Netmask fill-in of the 4 lab matches under the lock (about 1 h GPU, new files only in ws\s5\nb), net_band.build (CPU), then a paired lab bench netBand off against on (frozen flights): net touches first, then rallies, winners as a check; m0913 as a test. S1 first adds the band file to the bench (flights-key inputs, manifest, a switch to read the S5 band files). The default changes only on a manager decision. |
| 2026-11-26 | G7 (c) netHit (manager, S3 E146, merge 0d12bca9): variant B on by default (netHit from the net case of the last chain shot; winner_hint only when the chain has no shot decision). Reason: one fact for the user that agrees with the winner. On the net labels it is inside the noise (lab 14 to 13 of 33, m0913 7 to 7 of 12, false at no/cord 4 to 4); the app warnings "the winner hit the net" go from 12 to 2; the hitter of netHit loses at 88.5 % against 85.1 % (a check). Cost: the case-library net of s002 is not found (no chain shot owns that net event). Both variants find only 13 to 14 of 33 net labels: the next S3 chain item is the loss class "a false shot after the net touch hides the net touch" (m1015 1424.68, m1030 3632.93; case kind 4), measured on the net labels and s002. |
| 2026-11-26 | E147 plan approved (manager, S3 image facts: m1015 1424.68, s002 m1020 2653.95, m1030 3632.93). Two causes: the net shot is dropped by the continuity test and its net event is lost by the direction conflict; a flight that is not our ball becomes the last shot (the roll at the net; the ball of the next court). Switch CHAIN_NET_DEAD (off): (a) a conflicting net event after the last sample of the shot in the air starts an alone net shot of the other player; (b) after a net shot, a slow flight (< 40 km/h) that starts within 1.5 m of the net is the ball at the net. A separate switch for (c) a flight that starts more than 8 m from the court center line is not a ball of this court: it acts on all shots, so it is decided alone; S3 counts the rejected flights with image examples first (risks: wide balls, wrong-depth starts). Each part is measured alone and together; netBand off. |
| 2026-11-26 | E147 result (manager, S3, merge of 0aa2099f): CHAIN_NET_DEAD part (b) on by default (the slow flight within 1.5 m of the net after a net shot is the ball at the net): 1 chain case wrong to right (m1030 3627), none right to wrong, net labels 13 to 14 of 33, rallies unchanged. Part (a) stays off: 1 right to wrong (m1020 3287, a net event of the next shot of the same hitter) and m1015 1424.68 lost (the new net shot has no time before the touch); S3 makes these two fixes and measures (a) again. Part (c) (a flight far to the side is not our ball) is held: of 387 flights with fit |x| over 8 m, many are real wide balls with a wrong depth (m1015 2116.98), so no rule on the fit x; an image-x rule needs a measure of the next courts in the image first. |
| 2026-11-26 | Net label miss table (manager, S3 E148, 4e7c2842; defaults netHit B and NET_DEAD b): of 19 missed lab net labels, only 5 are chain items. No row 8 (5 are first-serve faults into the net: they need fault rows, the fault item); no net event 6 (4 with "net moves": S5 item 6 (d) measures these times); net event not owned by a chain shot 4; owned but not the decided shot 1. m0913: 5 missed (3 no row, 1 no net event, 1 other). Fix (a) can help 2 at most, so it goes after the rally items (priority 1): S3 next makes the same miss table for the rallies (missed and false rows with the defaults now). |
| 2026-11-26 | CHAIN_NET_DEAD ab on by default (manager, S3 E149, b54c60dd and 43107995). With the two fixes (the net shot starts at the last hit event before the touch; no net shot when the next seen shot owns the net event), ab against b: 2 chain cases wrong to right (m1015 1422, m1021 3303), none right to wrong; the m1020 3287 fault is gone; net labels 14 to 14 of 33, m0913 7 to 7; netHit at a "no"/"cord" label 4 to 5 (m1020 1855.39); winners 84.6 to 85.0 % (a check). Right in principle (the dropped net shot is kept), and the differences are inside the noise. s002: the net shot is in the chain but the time cut of last_shot drops it; a later item. Journal ids: S3 gets the new block E160 to E169 (E140 to E149 are used). |
| 2026-11-26 | Rally miss table (manager, S3 E160; defaults b54c60dd, frozen flights): missed 27 of 554 (the same 27 as E99): 23 GRU silent (A; 20 are 7.3 s or shorter; 19 have a serve event), 2 a fault row with the second-serve point lost (B), 2 dropped by a row rule (C). False rows 11 (one point in two rows 3, B 2, no ball 3, next court 1, play between points 1, before the first mark 1). Decisions: (1) join gap 2.5 s approved as a switch, on if the paired bench gives the count (-2 false, -1 fault row, 0 lost); (2) "drop rows with 0 good flights and no server end" held: it loses 1 real point for 3 false rows, and a missed point costs the user more than a false row; (3) GRU retraining for class A: not now; S3 writes a plan (the difference from E141 and E143, GPU time, measure, a real gain estimate); the user decides on any training. |
| 2026-11-26 | Join gap 2.5 s on by default (manager, S3 E161, 7638c26a): recall 95.1 to 95.3 % (527 to 528), precision 92.9 to 93.6 %, fault rows 29 to 27, 2 split points fixed, 1 missed mark found (m1030 3974.9). Side effects accepted: 1 winner right to wrong (m1021 6623, the ball after the point becomes the last shot; a winner check, not a rally error) and 2 starts earlier (3 s and 8 s, the winners still right). Rallies are priority 1. GRU: the loss-weight plan is withdrawn (the same idea as E141 recipe A). The new-input test (a ball-evidence channel each second: serve event, good flights, audio hits) is approved: about 6 min GPU, LOMO, 3 seeds against 3 base seeds, a win only above the seed spread; Igor and Misha tests only. This is the rally GRU, not the ball detector, so no user approval is needed for the test; a default change comes to the manager. |
| 2026-11-26 | Milestone batch check after the evening defaults (S2, keys at 5e63ab6b, read only): Igor still runs audio, court, court2 and flights again (about 40 min, CPU), no ball run (the ball tag is the same); the new chain defaults are in the winners stage, which always runs; netBand off, so no net band stages. Misha all stages (about 4.5 h). S2 item 16 approved (manager): G12 upstream keys (audio, image, court and pose keys get the media key; the image stage gets a key), existing files adopted with the E113 git check, the Igor rerun list must stay the same, Igor's court.json stays. |
| 2026-11-26 | Serve switches after S4 E154 (manager, merge of b8b67b03): (c) SERVE_DOWN_IMAGE on by default (a far serve or fast flight must move down the image, dRow >= 10 px; all 33 up flights are at -1 px or less, 4 of 410 down flights under 10 px): rallies and winners unchanged; far starts at a + 2.5 s 204 to 198, but the 6 lost were toss events 1 s or more early, so these are corrected times, not new misses; m1021 4536 gets the real serve at a + 1.40. (a) RETURN_REAL_START stays off: 45 more false events inside points (Igor +24). (b) SERVE_PRIOR_SPLIT stays off: far +12 but near -4 and rallies 538 to 534. Group 1 of the silent points (S3) is measured again now that (c) goes on. |
| 2026-11-26 | S2 item 16 merged (manager, 95b27b41, E117): G12 upstream keys (audio, image, court and pose keys include the media key; the image stage has a key). Milestone batch change: run "detect.py --adopt-keys" on data\m1116 before the normal run; then Igor still runs only audio, court, court2 and flights (without it, image and pose also run again, about 25 min more). Cost for app work folders made before item 16: audio, image, court and pose run again once (about 40 min); court.json and the ball tag stay. Follow-up for S2: a safe automatic adoption in detect.py, so that the app does not pay this. |
| 2026-11-26 | G7 (a) done (manager, S4 E155, fd4eb5bc): serve_ball.landing reads the bounce event (SERVE_LAND_EVENT); on by default (one bounce fact, the G7 owner rule). Measure on 7 labeled serve bounces: place error median 0.25 to 0.36 m, p90 0.90 to 0.59 m, in/out right 6 of 6 to 7 of 7; inside the noise. Lab only, no app change. S4 next: item 4, the audio hits as a hit event for the shot chain, measure first (audio hit candidates against the hit labels; how many missed or false chain hits an audio hit within 0.1 s explains); then a proposal for S3. Igor labels are a test: no threshold from them. |
| 2026-11-26 | S2 item 16b merged (manager, 5da7f215, E118): detect.py adopts the files of a stage by itself (no git) when the media key is current, the recorded key equals the pre-item-16 key of the current code, and the outputs are newer than the media files they read; else the stage runs. The image stage (no key before item 16) keeps the old rule: the media key and the picture times; accepted (feat_dino.py has no change since 2026-11-23). App work folders from before item 16 no longer pay about 40 min. Igor: a normal run gives audio, court, court2 and flights; the --adopt-keys step of the milestone row is no longer needed (harmless). |
| 2026-11-26 | Audio hits stopped for now (manager, S4 E156 and E157, 0661ecf3). On the 6 user fragments (27 hits), the best rule (audio hit + ball kink or flight start within 0.1 s, offset by end near +0.039 s, far +0.106 s; no chain hit within 0.3 s; no bounce event within 0.1 s) adds 4 true hits and 5 false ones: chain hits found 21 to 25 of 27, false 6 to 11. A false hit can change the last hitter and the winner, so no switch now. Open again when the user has labeled more fragments (enough hits for a real count). |
| 2026-11-26 | S2 item 17 approved (manager): a resume of a stopped ball stage. detect.py runs the ball pass in parts of the video (about 600 s), with a part file for each (the ball tag and the range in the name; temp name, then rename), and joins them into the same ball file as now. Gain for the app: a stop (Stop, app closed, standby, GPU out of memory: both seen, m1116 oom log and the 16:15 to 20:56 standby) loses one part, not up to 3 h. Conditions: the joined file equals the one-pass file on the m1020 sample (an overlap at the part starts, measured); the ball tag does not change; the progress counts the parts done; the 30 fps path too; a ball_wasb.py change needs the manager first. About 10 min GPU on the sample. |
| 2026-11-26 | Faults step approved (manager, S4 proposal): a step in the label page, like the Sides step, for the 27 hidden fault rows of the current head. One clip each; the user presses F (first-serve fault), P (a real point with no mark, as m1015 3328.5), N (not a fault and not a point) or Q (not sure); about 5 min in all. Output faults_<match>.json with the row hash. The bench (S1) then counts F as fault clips, P as found points with a missing mark, N as false rallies. Gain: the true rally precision (now 93.6 % with them false, 98.3 % with all hidden) and the precision of the fault clips the user sees. |
| 2026-11-26 | Fault flag in the event labels (the user): the user no longer presses X. Where a serve has no X, the scripts infer the flag from the marks: a serve with an out bounce or a net stop as its next event, followed by a serve of the same end, is a first-serve fault if the next serve is the second serve (the same service box when a bounce shows it, else the time gap: about 5 to 15 s for a second serve, about 15 to 30 s for a new point). The existing X marks test the rule. Unsure serves (no box and a time gap between the limits, a cord serve that lands in = a let) go to a short list for the user. Owner: S4 (label tools); S1 uses it in the history and the bench. |
| 2026-11-26 | S4 next item (manager): gap item 5, the measure on the user labels, as functions in label_measure.py that S1's case_history.py calls (no second measure tool): the row start and end against the user's serve contact and last event, the fault clips against infer_faults, and the event details that case_history does not report yet (time and place errors, in/out, net stop/cord, shot count). The match windows of score_events.py are used. Counts, not only rates (8 fragments now). Item 9 (b), the serve depth errors, waits. |
| 2026-11-26 | Journal ids (manager): S4 gets the new block E170 to E179 (E120 to E129 and E150 to E159 are used). |
| 2026-11-27 | Pause (the user, 00:01): all streams stop; the running S5 netmask job stops (finished mask files stay), the waiting lock tickets (S3 E162 seed 1, S2 E119, S1 history entry) are taken out; nothing starts until the user says go. |
| 2026-11-27 | Go after the pause (the user). S4 E170 merged (label_measure.py; 10 labeled fragments, 17 matched points, counts only): row start 1.40 s before the serve in play, row end 2.52 s after the last event (medians); fault clips 8: 4 right, 2 wrong, 2 with no serve, and they find 4 of the 11 labeled faults; event time errors small (hits 0.027 s, bounces 0.007 s, nets 0.031 s); bounce place 0.10 m (p90 0.34 m); in/out 52 of 54; shot count right 9 of 17 (6 off by 1). Next for S4: a cause table of the fault clip errors and the shot count errors (as E148), then fix proposals to the owners (fault rows S3; shot chain S3). |
| 2026-11-27 | Unsure fault flags (the user): the user is not asked about the fault flag. A serve that infer_faults marks "unsure" is counted apart (faultUnsure in case_history) and is not used as truth; there is no list of unsure serves for the user. This replaces the "short list for the user" of the 2026-11-26 fault flag row. |
| 2026-11-27 01:20 | User: until 11:00 the manager can start all runs, also the large ones (the milestone batch). After 11:00 the manager asks the user before a large run. | Manager starts the milestone batch tonight. |
| 2026-11-27 | netBand default on (manager, E137; rule 'apply sound improvements'). Lab paired, fixed values: net touches 26 to 28 of 35, false 8 to 3 of 70; held out 30 of 35, false 5; rallies the same; winners inside the noise (84.8 to 85.0 %); m0913 10 of 12 the same, false 0 to 1 of 18. S5 makes the edit of the detect.py default. S1 makes the lab band folder the bench default (the bench shows the default chain). The milestone batch (Igor, Misha) runs with netBand on: Igor gets the netmask fill-in (a decode of the candidate windows only), Misha makes the mask in the ball stage. The 5 target misses (4 blocked by the control rule) go to the S5 case list. | S5 edit, S1 bench default, S2 batch on the new commit |
| 2026-11-27 | Item 17 (S2, E119): the ball parts are not identical (m1020 60 fps: the part decode starts 1 frame early; m0913 30 fps: the fps filter takes other frames after a seek). Misha and Igor run on 897fd28d without item 17. S2 finds the exact seek on the CPU, then one short GPU measure (it waits in the queue behind Misha). The mask for each part (netBand on) waits until the parts are identical; S2 asks before an edit of net_band_mask (S5). | S2 |
| 2026-11-27 | E162 (S3): the rally GRU with the ball channel gives no win (3 seeds against 3; no measure gains more than the base spread). The production rally model stays. E163: group 1 stays closed (4 of 7 with SERVE_DOWN_IMAGE, the other 3 have a wrong depth). The frozen lab flights have no dRow, so the lab bench does not show the serve fixes; S1's flights by code key are the measure of these fixes (the S1 history entry and the S4 E172 recount). | S3: the E171 plan next |
| 2026-11-27 | Bench flights (manager, after S1): the bench default is already the flights by code key (item 6b). Rule: the number of a merge request comes from a run without --frozen-flights (the flights of its code key). --frozen-flights is only for a fast try during the work. A paired test of a flights-code change uses two code keys (before and after), not frozen against new. Cause: the frozen E38 flights have no dRow, so they do not show the serve fixes (S3 E163). | all streams |
| 2026-11-27 | S3 E171 plan (ws\s3\e171plan\plan.md): rank 1 = items 5 + 7 + 1 (the serve contact: a first chain shot out or net in 43 of 528 lab rows). Step 1 approved: a read-only table of the 43 rows on S1's head flights (stored data only: the serve event, flights, blobs, the time to the next serve; no labels by eye; the user labels are the truth where they exist). Then one switch, off by default, and a paired bench on two code keys (E164). Item 6 (dead ball) only after rank 1, with a right/wrong list for each changed row. Item 8 goes to the ball-stage list (no work now). | S3 |
| 2026-11-27 | Bench key bug (S1, fixed in 0d39fd4e): before it, the flights key had only FLIGHT_ENV. A remake run with SERVE_PRIOR_WIDE, SERVE_PRIOR_KEEP, SERVE_PRIOR_SPLIT, LAND_AGREE or BOUNCE_OUTLIER set read the cache of the default flights. Each stream checks its paired results of these switches on remade flights before 0d39fd4e and reports the ones to measure again. Frozen-flight runs are not affected. | S3, S4, S5 check |
| 2026-11-27 | S3 E164 step 1 (ws\s3\e164\step1.md): of the 43 rows, 17 are the serve in play, 18 a toss or a flight before the serve, 8 a serve before the serve in play. Approved (manager): code both parts behind CHAIN_SERVE_START, values a, b, ab, off by default. (a) a first shot that ends out or net, then the next shot by the same end 2.0 s or more later with no other-end shot between: the first shot leaves the list and gets a fault clip; no clip with a cord event (tennis rule: the same end serves again after a fault). (b) item 7: the first shot moves to a same-end serve event 0.5 to 1.3 s later (the toss comes before the contact). Paired bench on the remade flights, each part apart; a right/wrong list for each changed row and label row. The default decision comes after that. | S3 |
| 2026-11-27 | Queue order tonight (manager): Igor, E119 re-measure, the S4 and S1 flights remakes (CPU, about 25 min each), then Misha (about 4.5 h, ends about 09:00 to 09:30). Cause: S3, S4 and S1 wait for the head flights, and Misha would hold them for 4.5 h. S3 uses the S4 0d39fd4e-base cache (the same flights key; CHAIN_SERVE_START is chain code only) and makes no remake of its own. | S2 queue |
| 2026-11-27 | Flights key too wide (S3): the static import closure puts shot_chain.py into the flights key (through match_decode, score_decode, winner_ball, winner_sweep), so a chain-only edit gives a new flights key. A too-wide key is safe (an extra rerun, not a wrong result). Exception for E164 (manager): S3 runs the paired bench on the existing 0d39fd4e flights (S1's head folders, read only), because its diff is chain.py and shot_chain.py only. Condition: a null check, the off side must give the same numbers as the 0d39fd4e base. Later item for S1 (low): a 'no effect' list with a test for modules that the flights stage imports but does not run, or a precise closure. | S3 E164; S1 later |
| 2026-11-27 | Bench flights key (S1, approved): the key is the closure of what the bench flights stage runs (ball_full, net_events, lab.py), with a test that no chain module is in it. One remake for all keys. Side finding (S1): the lab rally GRU reads the frozen E38 feat_wasbf.npy, not features of the remade flights, so the bench does not show a flights change in the rally input. Next (S1): measure it once (feat_wasb from the head flights, the GRU fold models, rallies against the frozen features) with the CPU time; then the manager decides if the GRU input follows the flights by default. When it does, feat_wasb and its closure go back into the key. | S1 |
| 2026-11-27 | CHAIN_SERVE_START default ab (manager, E164; rule 'apply sound improvements'). Null check passes. Rallies, events and winners the same. (a): 11 lab rows change, 9 right, 1 wrong (m1021 6350), 1 cord with no clip; label fault clips right 6 of 11 to 7 of 12. (b): 26 serve shots move, 21 nearer the serve time, 4 farther; the 4 in labeled fragments all come nearer. S3 makes the default edit. Measure gap (S1): the case-history hit measure builds its own chain without serve events, so it does not show (b); it must measure the row chain as the app runs it. | S3 default edit; S1 measure gap |
| 2026-11-27 | Item 6, dead ball (S3 E165): closed, no switch. No safe time gap: a strict stop gives more right->wrong than wrong->right at each gap (0.9 / 1.5 / 2.0 s: 19 / 17 / 13 against 2 / 1 / 1); the variant without the first shot is even at 2.0 s. Cause: the net, out and own ends of the shots are false on most of the 119 pairs. Next (S3, read only): the cause of each false end (the event that made it), as input for S5 E139. | S3 |
| 2026-11-27 | S5 E139 proposals (manager): (a) approved for S1: case_history reports the chain events after the last labeled event of a point apart ('after the point'), not as false, and shows precision with and without them (0 of 26 points lose the deciding event; the winner effect of s016 3035 stays in the in/out and winner measures). (b) approved as a test: the near-hitter mirror of the far 'passed' rule in net_events.judge, first on the lab net labels (CPU), then a bench with two code keys (one remake, queued after the batch is fine); off by default until the result. (c) approved as a measure only: '5 or more samples on each side' of the bounce test on the lab bounce labels; no change before it. Journal ids: S5 gets E180 to E189. | S5, S1 |
| 2026-11-27 | S3 E166 (false dead ends, 102): chain side 35 (A own-side bounce before the serve given to the serve shot 18, B a bounce not on the dead shot's flight 10, C a net shot with no flight just before the same end serves 7), event side 58 (to S5), not known 9. Approved for coding (manager), one switch off by default: (A) an event before the contact of a shot is not an event of that shot (time order); (C) a net event with no flight is no shot when the same end serves less than 1.2 s later. Paired bench on S1's head folders (netBand on), off / A / C / AC, a right/wrong list for each changed row. B is a missed shot (a tracking item), no rule. | S3 |
| 2026-11-27 | S5 E180, E181 (manager): NET_PASSED_NEAR merged, off by default; it goes on if the bench (netBand on, two code keys) keeps rallies and events (CPU: no right touch lost; fragments false 5 to 4; full matches 6 events fewer). E181, the 5-sample bounce rule: not applied now (lab labels the same; fragments found 79 to 78, false 117 to 105, in play 23 to 19). S5 checks the lost bounce and the 4 in-play false against the S3 E166 groups first. | S5 |
| 2026-11-27 | E181/E182 (S5): the 5-sample bounce rule is not applied (12 false bounces removed, but only 4 in play, against 1 real out bounce and 1 real B bounce lost). Next (S5, read only, approved): measure the ground test of the vertex (the incoming fit must reach the ground at the vertex; S3 class D has the ball 0.5 m or more above it), on the lab bounce labels and the 16 fragments. NET_PASSED_NEAR key check passed. | S5 |
| 2026-11-27 | Item 17 with netBand on (S2 design s2-item17-mask-parts-design.md): proposal A approved (manager): each ball part writes its mask part, a small fill-in decode at each part bound makes the missing k records, and a join in frame order gives the same npz arrays as the one pass. The join goes into net_band_mask.py (the format owner S5): S2 writes it, S5 reviews the diff before the merge. Condition: the same arrays as the one pass on the m1020 and m0913 samples (GPU, after Misha). Item 17 merges only after the 30 fps GPU check and the one-pass frame check. Proposal B is not used (not identical). | S2, S5 review |
| 2026-11-27 | Item 17 join file (manager, change of the row before): the join goes into a new S5-owned file net_band_join.py, not into net_band_mask.py, so the netmask key and the existing masks (Igor, Misha) stay valid. net_band_join.py goes on the 'no effect' list of the key test with the reason 'the same arrays as the one pass', guarded by a unit test of identical arrays (one pass against parts) and the GPU sample check. | S2, S5 review |
| 2026-11-27 | SERVE_PRIOR_NEAR (S4 E172b): fails, and the switch code is removed (the journal row and snapshot eb9ca43c keep it). Far serve events 210 to 190 of 291, false near events in points 47 to 75, rallies 537 to 534. Cause: the near anchor turns the near receiver's return into a serve event (E10d risk). Item 9 (b) is closed for the fit; the 47 far starts of case list F and the 14 forced-anchor far starts stay as cases. | S4 |
| 2026-11-27 | S4 E173 (missed hits on the 16 labeled fragments, the row chain as the app runs it; head flights, netBand off): found 77 of 131 (85 with the fault-clip serves). Misses 54: outside the rows 27 (first-serve faults 13, of which 6 have a fault clip; second serves of a double fault 5; points with no row 9), time error over 0.3 s 8, unusable flight 5, chain rule 5, track gap 4, no shot 3, hitter 2. The shot chain is not the main gap. Next (manager): S3 looks at the double faults and the points with no row (priority 1, rallies); the case-history hit measure moves to the row chain (S1). | S3, S1 |
| 2026-11-27 | S3 E169 (7 labeled points with no row; 4 double faults, 3 in play): the rally GRU is below the threshold in 6 (max 0.13 or less), FAULT_SIDE takes 1 double fault as a first fault. No chain proposal (the short point rule stays off, E61). CHAIN_TIME_ORDER merged off by default (its bench E168 waits). Next (manager): a double fault has no rally, so the GRU is the wrong detector for it. S3 measures a double-fault row from the serve events (two serves of the same end, each with a fault end, the second 5.5 to 12.6 s after the first, no rally) on the lab doubleFault marks (S1 a09045a8) and the labels, read only. The FAULT_SIDE case (a same-end serve 3 to 12 s before, no row between) goes to the case list. S3 ids: E190 to E199. | S3 |
| 2026-11-27 | S5 E183 (measure): the one-side ground test of the bounce vertex (z_in <= 0.51 m, the max of the 38 lab bounces) keeps the lab bounces (38 of 42), fragments found 79 to 78, false in play 23 to 12, after the point 94 to 69; S3 class D 9 of 18 removed. Approved (manager): a switch in ball_bounce, off by default, then a bench with two code keys (netBand on) and the case history. It also removes S3 class A bounces, which CHAIN_TIME_ORDER (a) handles in the chain: before either goes on, one paired run with both. | S5 |
| 2026-11-27 | S3 E190 (measure): no double-fault rule. The lab has no doubleFault marks (gt.json has no point ends; a09045a8 is a class of the fault-clip measure). The serve-event rule finds 1 of 36 lab marks with no row, with 4 false rows (first faults), and 0 of the 3 labeled double faults with no row. The limits are the serve event recall (17 of 24 labeled fault serves have one) and the fault end of the serve, not the time limits. Closed until the serve events improve. | none |
| 2026-11-27 | S1 E101 (the rally GRU input): the app makes feat_wasb from its own flights, the lab bench read the frozen E38 file. On the head flights: matched 528 to 524 of 554 (recall 95.3 to 94.6 %), precision 98.3 to 97.8 % (fault rows hidden). Decision (manager): (a) the bench makes feat_wasbf from the flights of the code key by default (G1: measure the app path; under 2 s); the bench numbers from then on are the new reference, and each report names it. (b) A test for S3 after Misha: the GRU fold models trained on the head features (3 seeds against 3, as E162), paired; the production model changes only after that result and the manager's OK. The production model has the same train/test difference. | S1 (a), S3 (b) |
| 2026-11-27 | CHAIN_TIME_ORDER (S3 E168, head folders, netBand on): rallies, fault rows and events the same in all variants. Decision (manager, against the S3 advice for a; rule 'apply sound improvements' and the event rule of section 1): default 'a'. Its 27 changed shot ends are right facts where checked (19 first shots 'own' to 'none': the removed bounce is before the serve contact); the 1 winner lost (m1030 1817) came from a wrong fact before. Igor last-shot end +1. Part c is removed: it takes any next shot of the same end, not a serve event, and is wrong on a labeled net stop (s002, m1020 2634). | S3 default edit |
| 2026-11-27 | Milestone batch result (S1, held out, 897fd28d, netBand on). Misha m1113 (clean, first run): rallies recall 94.2 % (162 of 172), precision 92.6 % (91.0 % with fault rows), start/end median error 0.31/0.60 s, short points 9 of 14, winners on matched points 84.6 % (check), end to end 79.7 %, marked faults found 30 of 51, last shot end right 57 %. Igor (E47, not clean): recall 97.4 %, precision 96.0 %. Stage times Misha: ball 10823 s, court 2835 s (Igor 267 s: a speed item to look at), all 16090 s. The head code numbers of both follow. | S1 |
| 2026-11-27 | Item 17, 30 fps path (manager): option A. A video that needs the fps filter (30 fps output) runs the ball stage as one part, as before item 17 (exact by construction); the parts and the resume are for the path with no fps filter (60 fps, all lab matches). Option B (cut the end after a seek with start_time) is not done: more code and a rounding risk that few videos can test. Note: many user phone videos are 30 fps, so they get no resume; open again if a user needs it. | S2 |
| 2026-11-27 | New references (S1). Held out on 897fd28d, netBand on: Misha recall 94.2 %, precision 92.6 %; Igor 96.4 / 97.9 %; both 95.4 / 94.1 %, winners matched 84.9 % (n 350, check), end to end 80.9 %; marked faults found 72 of 121. Lab reference at head 186e4244 (netBand on, the GRU input from the code-key flights, no --frozen-flights): recall 94.6 %, precision 93.2 % (fault rows hidden: 2 shots or less 95.1 %, all 97.8 %), winners matched 85.3 % (n 524), end to end 80.7 %; m1030 recall 89.9 % is the weakest match. All later bench rows compare against this lab reference. The head held-out number follows. | S1 |
| 2026-11-27 | Held out on the head 186e4244 (S1): Misha and Igor give the same rallies and winners as on 897fd28d (Misha 94.2 / 92.6 %, winners 84.6 %; Igor 96.4 / 97.9 %, winners 85.1 %); faults +1 Misha, -1 Igor; last shot +1 Igor. Tonight's chain changes are neutral on the held-out rallies and winners; their gain is in the events (lab and labels). | S1 |
| 2026-11-27 | Misha stays clean (manager): no stream bench uses m1113 between milestones; the default held-out set of bench.py is Igor only, and Misha runs only with an explicit milestone flag, reported by S1. | S1 bench change; S5 run without m1113 |
| 2026-11-27 | Item 17 merged (S2 E119, b1be243b): a stopped ball stage goes on from the last finished part (60 fps path, no fps filter): the parts and the stop test give the same ball file and net band mask as one pass (m1020, fresh ball stage); the 30 fps path stays one part (identical rows and masks on m0913). The app progress estimate counts only the part of this run (DetectProgressTest 6 OK). S5 approved the join. | done |
| 2026-11-27 | Rally GRU on the head input (S3 E191; manager decision): train the production fold models and the production rally model on the features of the app's own flights (recipe H, seed 0, the same seed as the production model now). Reason: right in principle (train on the input that the app uses); it takes back the E101 loss on the app path (H s0: recall 95.1 %, precision with faults 95.1 %, fault rows 21, against prod on the head input 94.6 / 93.2 / 26) and the seeds are more stable; the paired gains are inside the seed spread. Conditions: keep the old model files beside the new ones; Igor held-out measure on the head after it (no Misha); the 4 S1 check fragments in the report. A later flights change that moves the features much needs a new training (S1 reports the feature drift with each reference). | S3 |
| 2026-11-27 | App run failed on a user video (D:\20251019_121702.mp4: HEVC Main 10, 4K, 10-bit, 60 fps): the CUDA ball decode chain (scale_cuda, hwdownload, format=nv12) cannot download 10-bit frames, the ball stage gave 0 frames with no error, and net_events crashed on the empty track. S2 item (G15, G13): 10-bit input in all CUDA chains (8-bit output byte-identical, so the ball tag stays), an HDR warning, and a hard error when the ball stage decodes no frames. S2 ids: E200 to E209. | S2 |
| 2026-11-27 | 10-bit video (S2 proposal, approved): no key change for 8-bit videos. A new decode10.py (a copy of ball_wasb.frames with scale_cuda format=nv12) serves videos with more than 8 bits; their ball tag adds '10bit' and decode10.py. HDR (HLG, PQ): a result.json warning that the colors are not converted (the detector is trained on SDR). G13: detect.py counts the decoded pictures, stops with an @error that quotes ffmpeg on a short count, writes no ball file, and skips net_events on an empty track. prep's fps1 chain and burst_ball get the fix at their next keyed change; fault_sheet and label_clips now. The copy in decode10.py goes back into ball_wasb at its next keyed change (G7). | S2 |
| 2026-11-27 | Defaults on (manager, E184 and E180; rule 'apply sound improvements'): BOUNCE_GROUND_UP 0.51 (fragments false bounces 117 to 81, in play 39 to 26; 1 right bounce lost, m1021 2315.22; lab rallies 94.6 to 94.8 %, bounces the same, false 2 to 1) and NET_PASSED_NEAR (the mirror of the far rule; fragments false nets 5 to 4, no right touch lost; lab the same). One commit for both, so the flights key changes once. | S5 |
| 2026-11-27 | Lab dashboard (the user's request; manager MVP): `python tools/auto-analysis/lab_dash.py` (http://localhost:8793/, launch config "lab-dash"). It shows the GPU lock (holder, progress, queue with estimates from the job history or the "about N min" text), the milestones with goals, issues and actions (expected and real impact 0 to 10), the workers, the label status and links. The plan is `docs/auto-analysis/lab-plan.json`: the manager updates it with each decision, as this table. The M2 goal targets are a manager draft for the user to confirm. Pause controls (the user): `gpu_lock.py pause --mode queue` (the running job ends, no new job starts), `--mode all` (the running job is stopped at once), `unpause`; also as buttons on the page. A pause is a queue ticket named `0000000000.000000_<pid>.json`, so also the older gpu_lock copies in the code snapshots wait. Rule for all streams: never remove a pause ticket; a job stopped by a pause ("KILLED by pause" in heavy.log) is started again by its owner after the unpause. | all streams |
| 2026-11-27 | Serve in the service box (the user, yes to the manager proposal): the shot chain tests a serve against the full singles court, so a serve that lands in the court but behind the service line, or in the wrong box, is "in" (E76 Igor rally 1, E170 s007 1892.079 and s008 1413.246, E171 (e), E190 s007 1891.1). Only the fault candidate of chain.evidence tests the service line now (E39). S3 item: a switch in shot_chain, off by default: the first bounce of a serve shot (a shot at a serve event, or the first shot of a row) is judged against the service line (depth, always: |y| over SL = out) and against the box side only when the side is known (the x of the serve flight start 0.3 m or more from the middle, as chain.serve_side; the serve goes cross-court). An out serve then feeds E164 (a) (fault clip) and the winner rule. Measure: the labeled serve bounces (E170: 23) and the fault clips against infer_faults, then a paired bench on two code keys (chain-only diff: the existing head flights, null check as E164); a right/wrong list for each changed row. The bench waits until the user unpauses the lab queue (the pause of 12:34 keeps the PC free for the user's app run). | S3 |
| 2026-11-27 | Label window of a fragment (the user): the clip start of 39 small fragments shows a part of the previous point: 13 rally-case fragments have a 60 s pad (E100, the GRU context of a later fragment run; they start 15 to 53 s before the end of the previous point), and 26 fault-check fragments start 0 to 2 s after the marked end of the previous point. The previous point is of no use to the labels or the measure, and the app does not need to work on a video that starts in a point. Decision: no new cut (ids and labels stay). S1 adds a label window to each fragment (one rule, in case_lib, written into the manifest by index()): start = the later of (the end of the previous marked point + 3 s) and (the span start - 25 s) and the clip start; end = the clip end. The measures (case_history, label_measure) use only events inside the window, also the chain events. S4: the label page starts each fragment at the window start, dims the part before it, and asks for no marks there; marks there stay in the files and are not used. Check (S1): each labeled serve of the point (also a first-serve fault) is inside the window, on all labeled fragments. The 60 s context stays in the clip files for a later fragment run. | S1, then S4 |
| 2026-11-27 | Label window, S1 check (A32, manager): the serves of each fragment's own point are all inside the window (0 lost). But in 5 E100 fragments (s007, s008, s009, s014, s016) the user labeled the full clip: 10 complete earlier points, 80 marks, 14 serves; the window would take them out of the measures (hits found 80/131 to 54/90). Decision: the window leaves out only the leftovers of a point (a point that starts before the clip or before the user's first mark), not complete labeled points. Rule: when the user's marks start before the rule start, the window starts at the user's first mark, but not inside a marked point that starts before the clip start. Unlabeled fragments keep the short window, so the user labels no extra points. index() is written once, together with the S4 page change (A33), so the open page asks for one reload only. | S1, S4 |
| 2026-11-27 | Live worker status (the user: the worker list and its state must be current, with no manual page refresh): the streams write their status to `D:\bananashot-lab\status\status.jsonl` with `lab_status.py` (rule in section 4). lab_dash.py puts the last entry of each action over lab-plan.json (status, waiting for, note, start and end, real impact); an id that is not in the plan shows as an unplanned action of its worker. Each worker card shows its now line and the age of its last update (gray after 60 min). The page gets new data each 5 s, reloads itself when lab_dash.html changes, and the server loads changed code of lab_dash.py, lab_status.py and gpu_lock.py at the next request (no restart). The manager still keeps the plan (milestones, issues, expected impact, order). | all streams |
| 2026-11-27 | A32 and A33 merged (manager review): the label window is in case_lib (225ab2ab, 7c68b83b; start rounded down to the ms) and in the label page (merge 4f5a6a43). On the 16 labeled small fragments: 0 labeled serves outside a window, the label counts are the same. The chain counts in the window go up because the window now ends at the clip end (before: the last mark): chain bounces 196 to 249, so "chain bounces right" 40 % to 32 %. Accepted as a change of the measure, not of the chain; the new value is the base from now on. S1 runs index() and S4 copies the page in the same step; the open label page asks for one reload (F5). | S1, S4 |
| 2026-11-27 | Status model (the user: A33 went from On hold to gone, and S4 worked while the board showed nothing in progress). Cause: A33 went done at 13:26:59, and the board hid done actions; S4 worked after that with no entry. Changes: a waiting entry names who must act (`--on`); the board shows "waits for <who>" and lists the action on the user or manager card ("Waits for you"); done actions stay 3 h under "Done recently" with their real impact; the top line of a card is the newest entry with a state word (Working, Waiting, Idle, Finished); a "Last changes" list shows the last entries; note and waiting fields do not carry over. Streams write a start entry when a turn starts work and `idle` at its end. | all streams |
| 2026-11-27 | Session hooks and worker overview (the user). A Claude Code hook in the user settings (`~/.claude/settings.json`, async) runs `lab_status.py hook` at UserPromptSubmit, PreToolUse (working) and Stop (idle). The session id (`D:\bananashot-lab\status\sessions.json`: S1 and the manager, who share the main folder) or the worktree folder (`.claude/worktrees/s<N>-`) names the worker; other sessions do nothing. Only a change is written. The board shows it as \"session active / idle\", next to the report line that the streams still write. The Workers section starts with an overview table: session, now, updated, the counts of in progress, on hold and pending, net time, and a total row; a click opens the card. The work area of a stream is now a tooltip on its name. The server routes reload with the code (no restart for a new endpoint). A new stream session in the main folder must be added to sessions.json. | all streams |
| 2026-11-27 | A31 result (E193, S3; manager review): CHAIN_SERVE_BOX stays "0". The rule is right in principle, but the chain facts are not good enough: with "e" 31 serve ends go from in to out, and 16 of them are surely wrong (the serve was in play): 4 bounces 0.0 to 0.46 m behind the service line (inside the place error), 5 deep bounces that are not the serve bounce, 7 box sides with x0 near the middle. Winners net 0; "ef" is worse. Labels: no verdict changes with "e"; fault clips: 1 right change (s007 31:31.079). Null check: 0 differences. Merged (370fd311, journal 73bd41bc). Next (A34, S3, approved by the manager, small CPU run): depth out only beyond the place error, the side only when abs(x0) >= 1.0 m, and only the first bounce after the serve contact. | S3 |
| 2026-11-27 | A34 result (E194, S3; manager review): the margins version ("m": out only beyond the place error, box side only from abs(x0) >= 1.0 m, only the first bounce after the contact) stays off too. "em": 17 serve ends go out, 9 surely wrong (5 deep bounces on the far half, 3 misses of 0.12 to 0.46 m with a smaller bounce sigma, 1 box side); winners net 0; labels no change; fault clips 1 right (s007 31:31.079). Null check 0. Merged (f7b9c5b2, journal 82ea1674). A31 and A34 are closed. The weak fact is the bounce depth on the far half: a later serve-box rule needs a measured place error by the distance from the camera (M2-I9 stays open). | S3 |
| 2026-11-27 | A20 result (E106, S1; the user's OK): the new reference is d1cb16c7 (netBand on; 27 min in one lock turn). Lab vs 186e4244: recall 94.6 to 95.1 %, precision (fault rows false) 93.2 to 95.1 % (fault rows 26 to 21), winner 85.3 to 84.6 %, end to end 80.7 to 80.5 %. Igor m1116 vs 897fd28d: recall 96.4 to 95.9 %, precision 97.9 to 98.4 %, winner 85.1 to 85.0 %, end to end 82.1 to 81.5 %, hits 83 to 84 of 91. The winner and Igor changes are inside the spread. All later M2 rows compare against d1cb16c7. Note: Igor recall 95.9 % is under the draft M2 line (96.4 % or more); the user confirms the M2 targets. | S1 |
| 2026-11-27 | Done when (the user: the A23 text did not say what result to reach). Each action now has `doneWhen`: the checkable target results with the place to check them (rule in section 4). Written for A21 to A30. The dashboard shows them on the action rows and the worker cards and warns for an open action without them; `lab_status.py` takes `--done-when` for unplanned actions. | all streams |
| 2026-11-27 | Next work (the user: the workers go on). S4 A25 (cause table of the 7 fault serves with no serve event), S3 A27 (m1030 miss table on d1cb16c7), S5 A26 (Misha court stage time), S1 A35 (new: the 4 M2 label measures again on d1cb16c7 with the label window, about 30 min CPU). All are analysis or CPU work; a GPU run goes through the lock, and a large run needs the user's OK. S2 has no item. The M2 targets stay as drafts until the user confirms them. | S1, S3, S4, S5 |
| 2026-11-27 | A35 result (E107, S1; manager review): the new M2 base on d1cb16c7 with the label window, 24 small fragments: hits 120 of 190 (63 %), shot count 20 of 40 (50 %), in/out 114 of 116 (98 %), fault clips 6 of 23 (26 %). On the same fragments each change is 2 or less; the new fragments move the counts, not the code. Fault clips stay the weakest M2 measure (17 of 23 labeled faults have no fault clip), so A25 (the fault serves with no serve event) stays first for S4. | S1, S4 |
| 2026-11-27 | A25 result (E174, S4; manager review): 20 of 32 labeled fault serves on E107 have no serve event, each with one cause (toss-start time 7, rally filter 5, depth error of the fit 5, no detection after the contact 2, fit too slow 1). Merged (7c51466f). Decision: TJ (a joined toss+serve event gets the time of the serve flight) is right in principle and adds no serve events inside the points, so S4 builds it as A36 with a switch, a unit test, the E107 label measures and a CPU lab bench check on d1cb16c7. RETURN_REAL_START (RS, RS3) stays off: 1 more fault clip costs 38 to 75 more serve events inside the points. The rally filter cause (far toss fits with a wrong depth) goes with M2-I9 (the depth error on the far half). | S4 |
| 2026-11-27 | A27 result (E195, S3; manager review): the 22 m1030 rows that differ on d1cb16c7 each have one cause; the largest is a silent fold GRU on a real point (8), and it is the variance of one GRU (the all-data GRU finds all 8; other seeds leave 3 to 4). Merged (2849ed6f). Decision: S3 tests the 3-seed mean GRU as A37: 8 small trainings (about 3 min GPU, under the lock) and chain-only checks on cached flights for the 4 lab matches and Igor (CPU); a new switch, off by default. A run with new detection or a long lock turn waits for the user. Cause B (no GRU after a first-serve fault, 6 rows) is the next S3 candidate. | S3 |
| 2026-11-27 | A26 interim (E185, S5): the stored Misha log has only the court total (2835 s), no time per step, so check 1 cannot pass from the logs. Cause from 8 sample windows: court_auto.detect runs in 55 or more of 146 windows (Igor 0), 30 to 80 s each. Decision (manager): the full timed replay (1 h CPU lock turn) waits for the user's OK. Meanwhile S5 builds the same-result speed-up (a) with a test, the low-camera skip (b) behind a switch, and per-step timers in the court log, and checks them on the 8 sample windows. | S5 |
| 2026-11-27 | A36 result (E175, S4; manager review): SERVE_JOIN_CONTACT (TJ) is in serve_ball.py with 5 unit tests and is on by default. Labels: the 7 cause-1 serves found, fault clips 6 to 7 of 23, no new serve events in the points, rows and lab rallies unchanged (95.1 % / 95.1 %). Merged (51bf53c6). The serve event time also goes into the GRU input (feat_wasb) and match_decode; the lab bench with the input made again shows no rally change. The rally reference stays d1cb16c7 for S3 A37; S3 adds TJ at its rebase after A37. | S4, S3 |
| 2026-11-27 | A37 result (E196, S3; manager review): the 3-seed mean rally GRU. Lab recall 95.1 to 95.5 %, precision 95.1 to 95.0 %, winners 434 to 442 (over the spread); Igor recall 95.9 to 97.4 %, precision 98.4 to 97.9 %. No measure loses more than the spread. Merged (731dafd2). Decision (manager, by the rule 'apply sound improvements'): SEEDS = 3 goes on in auto-detect. S3 copies the seed files to the models folder, sets SEEDS = 3 and adds the 2 production seed files to pack_for_mac, in one small commit; the next rally reference is the head with SEEDS 3 (S3 names it). The user can turn it off before a merge into master. | S3 |
| 2026-11-27 | SEEDS 3 is on (7d71d687, S3; manager review). The new rally reference is 7d71d687, E197 (lab bench entry 20261127-231153-7d71d687-s3-seeds3-ref): lab 95.5 % / 95.0 %, 23 fault rows; Igor on its E106 keys 97.4 % / 97.9 %. All later rally rows compare against it. Merged. | all streams |
| 2026-11-27 | A26 step (E186, S5; manager review): court_auto.detect is faster with the same results (unit test against the old code on synthetic frames; 8 Misha windows with the same corners, costs and kinds): 332 to 162 s, 140 s with COURT_LOW_SKIP (off). The court log now has the time of each step. Merged (3fd5c412). The estimate on Misha, about 1190 to 1380 s, is still over the 600 s target, so S5 tests rules that can change results (fewer hypotheses, a smaller sorted search, no detect after a lost window) on the sample windows, with the corner change of each rule. The full timed replay (now about 20 to 25 min CPU) still waits for the user's OK. | S5 |
| 2026-11-27 | A38 result (E198, S3; manager review): 5 B cases (the GRU stays silent on the second-serve point after a fault), one cause. CHAIN_SECOND_SERVE gets 2 back with 0 false and 0 lost; lab 95.5 to 95.7 % / 95.0 to 95.2 %; Igor recall 97.4 to 97.9 %. Merged (01c22c34). Decision: on (right in principle, no loss; S3 sets the default in one small commit). Found in the review: the S5 court speed-up (3fd5c412) changed court_auto.py, which is in the import closure of the flights stage, so the head has no cached flights and each check would need new detection. A39 (S2) fixes the keys so a same-result change keeps the caches. Until A39 is merged, S5 keeps further court_auto changes in its branch. | S2, S3, S5 |
| 2026-11-27 | A28 result (E176, S4; manager review): the 20 wrong shot counts each have one cause: a false first shot before the serve in play 7, shots after the point end 6, a missed contact 4, the point goes on after the label window 3. Merged. Decisions: (1) A41 (S4, now): the measure counts what the app shows: the shots that the chain marks as not used do not count, and a point cut by the label window is not comparable; (2) A40 (S3, after A39): c1 in shot_chain.serve_start behind a switch, with a lab check on the cached flights. The 5 other A points need the serve event (the E174 causes, M2-I9). | S3, S4 |
| 2026-11-27 | A39 step (E202, S2; manager review): code keys now have only the modules that the stage can reach (code_closure.reach, a static analysis; dynamic uses such as globals or eval reach the full module). The flights key has no court_auto.py now. The 7d71d687 bench caches are adopted with a proof; the head bench gives E197 again. Merged (ed0a900b). Next: S2 adds a detect.py adopt with a dry run for the app folders (flights and netband), then A39 is done. A40 (S3) starts now. | S2, S3 |
| 2026-11-27 | A41 result (E177, S4; manager review): the shot count measure counts what the app shows (no late shots) and leaves out the points that go on after the label window. Shot count right 20 of 40 to 24 of 36 (67 %). The other label measures do not change. Merged. S4 changed one line of case_history.py (owner S1) to pass the label window; accepted, and S1 is told. | S1, S4 |
| 2026-11-28 | A40 result (E199, S3; manager review): c1 (CHAIN_SERVE_START letter c): shot count right +1 on the labels, the lab the same (95.7 % / 95.2 %, 443 winners). Merged. Decision: c goes on (default 'abc'; S3, one small commit). The new head reference is da42753d, bench entry 20261127-235555-da42753d-s3-e199-ab (SEEDS 3, second serve on, TJ; c off). Journal ids: S3 gets E210 to E219. | S3 |
| 2026-11-28 | A42 result (E178, S4; manager review): RETURN_FIT in serve_ball.is_return (a return candidate over 16 m from the net at 180 km/h or more is a wrong fit, not a return). Labels: 4 of 5 cause-2 serves back, fault clips 8 to 10 of 23, no new serve events in the points; the lab the same. Merged. Decision: on (S4, one line in serve_ball.py). Next: A44, the fault clip miss table. Journal ids: S4 gets E220 to E229. | S4 |

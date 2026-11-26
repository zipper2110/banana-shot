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
- Disk C is almost full. Put all data on D.

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
- Open: G15 runs from snapshot 39c93725.

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
   touches found and false. Start after the G15 runs and S2's coverage report.

## 4. Rules for all streams

- **Branches.** Each new stream works in its own git worktree, on a branch from `auto-detect`. The manager
  merges a finished piece into `auto-detect`. Do not merge into `master`.
- **Lab data.** Do not replace a file in `D:\bananashot-lab\data\<match>\` or in an app work folder that
  another stream can read. Write new outputs to `D:\bananashot-lab\ws\<stream>\`, or to new files with the
  stream tag in the name. S1 decides when a new input becomes the default input of the benchmark.
- **Journal ids.** To prevent collisions, each stream takes its experiment ids from its own block:
  S1 E43 to E49, S2 E50 to E59, S3 E60 to E69, S4 E70 to E79, S5 E80 to E89. Second blocks (2026-11-26): S1 E100 to E109, S2 E110 to E119, S3 E90 to E99, S4 E120 to E129, S5 E130 to E139.
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

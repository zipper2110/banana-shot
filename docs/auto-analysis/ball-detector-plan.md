# Plan for the ball detector (gap program item 7)

Status: revised 2026-11-26 after the image-space checks (E150 of S4, and the checks of section 2). For approval by
the manager and the user. This is a plan only. No training starts before the approval.

The ball track is the base of all events: hits, bounces, net touches, serves, and the winner. The first step of
this plan was to find the errors where the detector loses the ball. The check in image space (the stored blobs, not
the 3D fits) shows that in each of the three target errors the ball is in the blobs. The errors come later in the
chain. So this plan proposes **no detector training now**, and a diagnosis of the frames that the detector really
misses.

## 1. The detector now

- Model: WASB HRNet (permissive license), fine-tuned by us (E38: `ball_e38.pth`, and one fold weight file for each
  lab match). Input: 3 frames, the court region of the 1920 x 1080 frame at scale 0.35 (about 670 x 380 px).
- Two passes for each frame (`dim`, E27): the normal picture and a picture where white and gray become dark. The
  second pass finds a ball in front of a bright wall or sky. It doubles the time of the ball stage.
- Recall on the hand-labeled frames (E80, `ball_labels_e27.json`, 2842 frames): m0913 92 %, lab 88 % (held out:
  m1015 96 %, m1020 94 %, m1030 96 %, m1021 66 %). False blobs: about 0.2 to 0.5 for each frame.
- Cost: the ball stage is the largest stage (m0913, a 96 min video: ball 4214 s of 6739 s for all stages, G15).
  Training one weight file: about 1 h on the 6 GB card (E38: 3 epochs, about 1 s for each step).

## 2. The target errors, in image space

| Target | Error in the events | Image space (the stored blobs, peak 0.5 or more) | Where the error is | Owner |
|---|---|---|---|---|
| A, far serve | 63 lab serve starts have no serve event (E125) | 58 of 61 have a run of blobs down the picture to the bounce; of all 136 far starts with no serve event, 113 have one. The run starts at a median of 1.78 s after the point start, after the end of the old check window (E150) | the flights and the serve event (a wrong depth of the 3D fit; `serve_prior` returns early) | S4 |
| B, net band | m0913 net touches 10 to 7 of 12, false 1 to 4 of 18; lab 28 to 26 of 35 (E110, E85) | At the 3 lost touches, the blobs follow the ball down to the net base and up again (for example 1130.07 s: z 0.83 to 0.0 to 0.86 m). At the 3 false touches, the blobs in the net band move a median of 8 px and 26 px in each frame (at 264.33 s 119 px: more than one object), and none stand still: a real ball (for example a player who bounces the ball near the net at 3589.57 s), not a false blob | the net rules (made on tracks with a gap at the touch) | S5 item 6: the net band motion confirms or rejects a touch (E89 to E132: lab 26 to 31 of 35 held out, false 8 to 4) |
| C, near shots | Igor: 5 shots with a hit time error of more than 0.3 s, 3 missed hits after a bounce (E95) | For all 91 Igor hit labels, a flight starts soon after the hit: median 0.16 s for a near hit, 0.07 s for a far hit; only 2 of 91 after more than 0.3 s. In the 8 E95 cases the first flight sample is 0.03 to 0.26 s after the hit (one case 1.25 s), and 86 to 99 % of the frames between have a blob (two cases 40 % and 50 %) | the hit time and the shot rules of the chain | S3 (E96: `meet_time`) |

Scripts of the checks: `D:\bananashot-lab\ws\s5\plan\b_image.py`, `c_image.py` (read only, CPU); E150 of S4.

## 3. What the detector really misses

The only measured detector loss is on the labeled frames (E80, E38). S1 split the misses of the bench run
`20261126-143623-709bbfad-heavy-ball-7a-heldout-dim` (`lab_label_hits.json`; dim, held-out fold weights):
- m1021 misses 53 of 155 labels (34 %); the other lab matches miss 3 to 10 %. The m1021 misses are whole flight
  parts (2 windows: frames 61 to 86, and 1 to 14 and 44 to 62), not single frames. All are seen balls.
- 49 of the 53 are at the far end (the far end misses 35 %). In the net band (|y| < 2 m): 0 of 9 missed.
- Small and slow, not blurred: all 53 misses have a ball length below 20 px (41 % of those are missed; 0 of 26 at
  20 px or more). Median missed against found: area 8 px against 98.5 px, length 5 px against 12 px.
- Background: a background saturation (bgS) below 60 misses 59 % (47 of 80); 120 or more misses 0. m1030 shows the
  same at a lower level (bgS below 60: 24 %, 5 of 21).
- m1015 is different: its 6 misses are long blur (length 50 px or more, median 120 px).
So the main detector loss is a small, slow ball at the far end in front of a gray background (low saturation). A
possible cause (not tested): at scale 0.35 the few pixels of a small ball mix with the gray background, so they lose
saturation, and the `dim` pass darkens them with the background.

**Step 2 result (E133b, E133c).** The `dim` hypothesis is not supported: the normal pass gives the higher peak in
51 of the 53 m1021 misses.

| Missed set | Count | Cause | Fix in reach |
|---|---|---|---|
| m1021 labels | 53 | The model hardly sees a small far ball in front of gray: weak peak 0.1 to 0.5: 34, no heat (below 0.1): 19; median peak 0.10. Another object in the same frame has a median peak of 0.60. | Not a threshold. A fine-tune at the far end (or a far-end tile at a larger scale, which also needs training: scale 0.5 on the full picture finds 5 of 53). User approval first. |
| m1015, m1020, m1030 labels | 19 | Near the threshold: all weak, median peak 0.45. | A lower threshold where a path supports it (no training). Tested (E136, E136b): 11 to 16 of the 19 found, but the false blobs go up by half or more (lab 0.50 to 0.77 or 0.99 for each frame). Not applied. |
| m1020 night far serves (S4 E150), with m1021 1 and m1030 3 | 23 | Mostly not a detector loss: the far serve comes 2.5 to 4.2 s after the point start label, after the windows of S4 (a + 1.5 s for the serve event, a + 2.5 s for the run). 15: a down run of 5 or more blobs crosses the net row, with no up crossing before it; in 11 of them the return (up) follows 0.7 to 1.1 s later. 4: only the held-out heat map has the down crossing (3), or the blob file has only the return (1). 4: no crossing found. S4 E151 (the run rule of `serve_img.py`, a - 1 .. a + 4.5 s) finds a down run in the blob file in 20 of 23 (also m1020 p5, p7, p56, p141, m1030 p127); 3 have none (m1020 p1, p134, m1030 p28). | Not a detector change. Of the 15, 9 have a serve event in a - 1 .. a + 4 s and 6 have none (S4 E151: no far serve event in a - 1 .. a + 4.5 s for 13 of 23): the serve event step (S4). |

So one cause does not cover m1021 and the m1020 night serves.

## 4. Proposal

1. **No training now.** The three target errors are in the steps after the detector, and their owners fix them
   there (S4: serve, S5: net band, S3: hit time).
2. **Finish the diagnosis of section 3** (a few GPU minutes on the label windows): the heat map peak at each missed
   label (is there a weak peak below 0.5?), and how often a far ball in front of a gray background occurs in the
   rallies of each video (is this loss large for the events?). No user work: the labels exist.
3. **Only if the diagnosis shows a large and clear cause**, a new plan with options, for example: a fine-tune with
   new labels at that cause (the E38 recipe), a larger scale on a part of the picture, or a lower threshold where a
   path supports it. Each option then states its labels, its user time, and its GPU time.

### Step 3 plan for the m1021 loss (E135)

**(a) The event loss.** Stored data only: the full-video blob files of E38d (dim pass, held-out fold weights), the
lab flight files (`fullflights_s0.35_ev.json`), the rally rows of `gt.json`.

| Measure | m1015 | m1020 | m1021 | m1030 |
|---|---|---|---|---|
| Labeled events with a blob (30 px, ±3 frames): bounce, hit, net | 26 of 26 | 19 of 19 | 18 of 18 | 22 of 22 |
| Rally rows (outcome not NONE) | 96 | 152 | 158 | 148 |
| Median flight coverage of a rally row | 0.69 | 0.64 | 0.63 | 0.72 |
| Median time from the last flight to the rally end | 1.24 s | 1.48 s | 1.03 s | 0.75 s |
| Rally rows with a flight gap of more than 2 s | 3 | 6 | 6 | 4 |
| Winner right on matched points (E38d) | 78.3 % | 87.1 % | 78.8 % | 75.8 % |

- The 2 label windows of each match (3 s each) hold no labeled event. So no labeled event is in a missed flight
  part of the labels.
- The bounce and net labels come from candidates of the detector track (bounce: `cand`; net: batch A and B), so they
  cannot show a detector loss. Their 100 % is not evidence.
- On the full video, the m1021 rally rows have the same flight coverage and the same number of long flight gaps as
  m1020. The rally end and the winners of m1021 are not worse than those of the other matches.
- Result: the 34 % loss on the m1021 labeled frames does not show as an event loss in the stored data. The loss is
  probably small for the events. The stored data cannot measure an event loss in the far flight parts directly;
  that needs new labels.

**(b) The options.** Both need new labels: a small far ball in front of gray (m1021, also m1030 and the other
matches). Agents do not label by eye: the path method of E27a proposes the positions, and the user checks each one.

| Option | Labels | User time | GPU time | Run time of the app | Risks (section 7) |
|---|---|---|---|---|---|
| 0. No training (the m1021 loss stays) | none | none | none | none | none |
| 1. Fine-tune at the far end, E38 recipe (more weight on far small balls) | about 400 to 600 far frames, 10 to 20 windows of 3 s in 4 lab matches | about 30 to 45 min (estimate: 4 to 5 s for each frame on a tile sheet) | 4 folds × 53 min + all × 65 min (about 4.5 h); full held-out detector runs of the 4 lab matches for the event measure (about 6 h at 0.82 × real time) | none | false blobs (the far end has the most static objects); the net and bounce rules were made on the old tracks (re-measure E110); fit to 4 matches (fold weights, m0913 and Igor as tests) |
| 2. Far-end tile: a second pass on the far half at scale 0.5 to 0.7 | the same as 1, at the tile scale | the same as 1 | the same as 1, and a tile training (scale 0.5 on the full picture without training: 5 of 53) | the ball stage gets slower (estimate: +30 to 50 %); the 6 GB card is full at batch 1 (E83) | the same as 1, and more false blobs from a second pass (E27d: `dim` +0.27 for each frame); cost and memory |

**(c) Recommendation: option 0 now.** The event loss of (a) is not measurable, and the full-video chain of m1021 is
as good as the other matches. A training costs the user 30 to 45 min, about 10 h of GPU time, and a re-measure of
the net and bounce rules. Open it again when an event loss is measured, for example in a fragment of the medium level
(section 6) or in a new video with a far ball in front of gray. If the user wants it then, option 1 comes first:
it does not change the run time of the app.

## 5. Labels and user work

- Now: **none**. The checks of section 2 use the stored blobs. The diagnosis of step 2 uses the existing hand
  labels (`ball_labels_e27.json`).
- Later, only if step 3 starts: new labels at the cause that the diagnosis finds. Agents do not label by eye
  (the user's decision, 2026-11-25), so the user checks each position. The count and the time come with that plan.

## 6. Measure

1. `bench.py heavy --stage ball` (S1, item 7(a)): ball recall and false blobs on the labeled frames, with the fold
   weights; each video must keep or improve its recall.
2. The small level of the case library (S1 item 8): the event cases and the controls, old code against new code on
   the same fragment.
3. The medium level (2 to 3 games for each singles video) before a merge.
4. Full videos only at a milestone, with the manager's approval.

## 7. Risks of a detector change (for step 3)

- **False blobs.** More recall gives more false blobs (E27d: `dim` +0.27 for each frame); they make false tracks,
  bounces, and net touches.
- **Rules made on the old tracks.** The net and bounce rules depend on the gaps of the current track (E110: a better
  detector lost net touches). After a detector change, re-measure the events at once.
- **Fit to the lab matches.** Few videos. Use the fold weights, keep m0913 and Igor as tests.
- **Cost and memory.** The ball stage is the largest stage; the 6 GB card is full at batch 1 (E83).

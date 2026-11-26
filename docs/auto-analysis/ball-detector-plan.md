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

## 4. Proposal

1. **No training now.** The three target errors are in the steps after the detector, and their owners fix them
   there (S4: serve, S5: net band, S3: hit time).
2. **Finish the diagnosis of section 3** (a few GPU minutes on the label windows): the heat map peak at each missed
   label (is there a weak peak below 0.5?), and how often a far ball in front of a gray background occurs in the
   rallies of each video (is this loss large for the events?). No user work: the labels exist.
3. **Only if the diagnosis shows a large and clear cause**, a new plan with options, for example: a fine-tune with
   new labels at that cause (the E38 recipe), a larger scale on a part of the picture, or a lower threshold where a
   path supports it. Each option then states its labels, its user time, and its GPU time.

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

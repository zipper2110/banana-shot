# Plan for the ball detector (gap program item 7)

Status: draft for approval by the manager and the user. This is a plan only. No training starts before the approval.

The ball track is the base of all events: hits, bounces, net touches, serves, and the winner. This plan says
where the detector loses the ball now, why, what we can do, what each option costs, and how we measure it.

## 1. The detector now

- Model: WASB HRNet (permissive license), fine-tuned by us (E38: `ball_e38.pth`, and one fold weight file for each
  lab match). Input: 3 frames, the court region of the 1920 x 1080 frame at scale 0.35 (about 670 x 380 px).
- Two passes for each frame (`dim`, E27): the normal picture and a picture where white and gray become dark. The
  second pass finds a ball in front of a bright wall or sky. It doubles the time of the ball stage.
- Recall on the hand-labeled frames (E80, `ball_labels_e27.json`, 2842 frames): m0913 92 %, lab 88 % (held out).
  m1021 is the weak lab match (66 %: a second ball on the court, a bright wall). False blobs: about 0.2 to 0.5 for
  each frame.
- Cost: the ball stage is the largest stage (m0913, a 96 min video: ball 4214 s of 6739 s for all stages, G15). Training one weight file:
  about 1 h on the 6 GB card (E38: 3 epochs, about 1 s for each step).

## 2. Target errors

| # | Error | Count now | Journal | Effect |
|---|---|---|---|---|
| A | The far serve before its bounce is not in the blobs | 61 of 68 lab serve starts whose track starts only after the bounce | E125, E126 | no serve event; the server and the fault are not known (E73: 21 of 39 missed faults have no serve event) |
| B | The track goes on through a net touch, and short tracks stay in the net band | m0913: net touches 10 to 7 of 12, false 1 to 4 of 18; lab 28 to 26 of 35 | E110, E85 | lost and false net touches; a false net touch can change a winner |
| C | A shot is first seen after the net, or the ball is lost after the bounce before the shot | Igor: 5 shots with a time error of more than 0.3 s, 3 missed hits after a bounce | E95, E96 | a late or early hit time; a missed hit |

## 3. Causes

- **A, far serve.** The far ball is small: about 3.5 px in the 1920 frame at the far baseline (court model of each
  lab match), so about 1.2 px at scale 0.35. A serve ball moves 6 px or more in each frame down the picture (E126),
  so the ball is a weak streak (motion blur). E126 found no fast blob run
  before the bounce in 61 of 68 cases (4 have a run of 3 to 4 samples, too short for a track). The link step is not
  the cause. Main cause: the input resolution at the far end, with the motion blur.
  [S4 input: open. Are there weak peaks below the threshold, and can a person see the ball in those frames?]
- **B, net band.** This is not a detector fault. The better detector (`dim`) sees the ball fall at the net, so the
  track has no gap at the touch, and the net rules (made on tracks with a gap) find no candidate. Some new short
  tracks in the net band are false blobs (net mesh, posts, a ball that lies at the net). The fix is mostly in the net
  code: E89 (the motion of the net band confirms or rejects a touch: lab net touches 26 to 30 of 35, false 8 to 4).
- **C, near shots.** Not diagnosed per frame yet. Likely causes: a near ball just after the hit is large and very
  blurred, or the player or the racket covers it; a ball after a bounce near the player is lost in the same way.
  The `low` pass (half scale, E27d) was made for a large near ball (+4 points recall, more cost).

## 4. Options

| Option | What | Cost | Expected gain | Needs labels |
|---|---|---|---|---|
| 1. Check the weak peaks | read the heat map peaks below 0.35 at the 68 far serves; if the ball is there, accept a run of weak blobs only when it fits a serve path (3D) | CPU, 1 day of work | part of A, if weak peaks exist | the A labels (to measure) |
| 2. Far tile pass | one more pass on a crop around the far baseline (the serve and the far ball) at scale 0.7 to 1.0, so the far ball is 2.5 to 3.5 px (now 1.2 px) | ball stage +30 to 60 % (to measure); no training | A, and far balls in rallies | the A labels |
| 3. Far tile only at serve times | option 2, but only in the 1.5 s before the first flight of each rally (a second ball step after the rally step) | ball stage +1 to 2 %; a change of the stage order | A only | the A labels |
| 4. Link step | accept a fast run of 3 to 4 samples at the far end when it fits a serve path | CPU, small | 4 of 68 | no |
| 5. Fine-tune with new labels | the E38 recipe, with new labeled frames: far serves (A), near shots (C), and net band frames with no ball (B, hard negatives) | labels (section 5); GPU about 6 h (4 fold runs and 1 run with all, about 1 h each) + 1 h measure | A, C, fewer false blobs in the net band | yes |
| 6. Model with more frames | a new model input (5 or more frames) for a weak, fast ball | days of GPU; a new design; high risk | uncertain | yes, many |

Proposal: options 1 and 4 first (cheap), then option 2 or 3 (no training), then option 5 if the gain is too small.
Option 6 is not in this plan.

## 5. Labels and training data

The A labels are needed for each option, also only to measure. **The user must check ball positions.**

- How: the method of E27a. A script proposes the ball position in each frame from the image difference (4K frame)
  and from a path fit between the serve toss and the first sample after the bounce. The check is a sheet of small
  tiles (20 tiles for each sheet): the user (or Claude first, then the user on a sample) accepts, moves, or
  rejects each position. Frames where the script has no proposal need a click.
- How many: A: 68 lab serves x about 12 frames (60 fps, stride 2, from the hit to the bounce) = about 800 frames.
  C: about 30 lab shots x 10 frames = about 300 frames (the shots are found first by a script, as in E95). B: net band
  frames with no ball come from the current labels and need no user work.
- User time: about 40 sheets for A and 15 for C, about 30 s each = **about 30 min**, plus clicks where the script
  has no proposal (estimate: 20 % of the frames, 5 s each = **about 20 min**). If Claude checks first and the user
  checks a 10 % sample, the user time is about 10 min. Bias risk: proposals from the image difference miss balls
  that the difference does not show (E27a). A tool for the check does not exist yet (S1: no click tool for ball
  positions; `label_hits.html` is for hits). Building it is part of step 1.
- Held out: m0913 and Igor (m1116) stay test only. The lab matches use one fold weight file for each match.

## 6. Measure

1. `bench.py heavy --stage ball` (S1, item 7(a); not built yet): ball recall and false blobs on the labeled frames,
   with the fold weights. The E27 labels must not lose recall (each video), and the A and C labels must gain.
   Time: about 1 min for the lab windows, about 15 min for the m0913 windows.
2. The small level of the case library (S1 item 8): the event cases of A, B, C and the controls (one marked point
   with a pad, old code against new code on the same fragment). About 40 s of ball stage for each 30 s fragment.
3. The medium level (2 to 3 games for each singles video) before a merge.
4. Full videos only at a milestone (all lab matches, m0913, Igor), with the manager's approval.

Order of the measure for the events: serve events (A), net touches (B), hit times (C); then rallies, then winners.

## 7. Risks

- **False blobs.** More recall gives more false blobs (E27d: `dim` +0.27 false blobs for each frame). False blobs
  make false tracks, bounces, and net touches. Each option reports the false blobs for each frame and the false
  events on the "no" labels.
- **Rules made on the old tracks.** The net and bounce rules depend on the gaps of the current track (E110: a better
  detector lost net touches). After a detector change, re-measure the net touches and the bounces at once, and fix
  the rules (E89) before a merge.
- **Label bias.** The labels come from proposals; a ball that the proposal method does not see is not labeled
  (E27a). The user check of a sample measures this.
- **Fit to the lab matches.** Few videos (4 lab matches, m0913, Igor). Use the fold weights, keep m0913 and Igor as
  tests, and treat a gain on only one video as noise.
- **Cost.** The ball stage is already the largest stage. Option 2 can add 30 to 60 %; option 3 is cheap but changes
  the stage order.
- **GPU memory.** The 6 GB card is full at batch 1 (E83). A larger crop (option 2) or a larger model can run out of
  memory; measure on the label windows first.

## 8. Steps and time

| Step | Work | GPU | User |
|---|---|---|---|
| 1 | A labels: proposal script, check tool, Claude check | decode only (label windows) | about 10 to 50 min |
| 2 | Options 1 and 4 (CPU) | none | none |
| 3 | Option 2 or 3 on the label windows | about 30 min | none |
| 4 | C labels and option 5, if needed | about 7 h | about 15 min |
| 5 | Net and bounce rules on the new tracks (E89), case library small and medium | about 1 h | none |
| 6 | Milestone full run | about 6 h | approval |

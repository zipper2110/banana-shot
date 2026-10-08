# AI Points Detection: Brainstorm and Trial Plan

Written on 2026-10-08. This file is a brainstorm, not a decision. It has
the possible shapes of the feature, the expected results, and a cheap test
of the approach. There is no backlog item for it yet.

## 1. The idea

- The user now marks the start and the end of each point by hand. A 2-hour
  match can take 20 to 40 minutes of marking (estimate from
  `docs/marketing/strategy.md`).
- The app sends frames of the video to a classifier model, for example 1
  frame for each second. The model gives the confidence that a point is in
  play.
- The app changes the confidence values into suggested points. The user
  accepts them with no edits, or with few and easy edits.
- The AI is a helper. The user still decides each cut.

## 2. Facts that change the idea

### 2.1 Jev reads text only

- Jev (TypeSafe AI, early access since 2026-09-15) is a "decision model". It
  returns one choice from a fixed list, with a probability for each choice.
  This output is a good fit for "point in play: yes or no".
- But Jev takes text input only. It does not accept images, audio, or video.
  We found no published plan to add image input. Thus we cannot send frames
  to Jev itself.
- Price, for reference: about $0.042 for 1 million input tokens, and the
  output is free.
- The "Jev style" works with vision models too. The LLM2Jev paper reads the
  probability of each answer from a normal multimodal model (for example
  Qwen3.5-4B), with no extra training. Image classification works through
  the same interface.

Thus "Jev for frames" has three real forms:

| Form | What it is | Runs where |
|---|---|---|
| A. Vision LLM with a fixed answer | A cloud vision model (Gemini, GPT, Claude class). The prompt asks for one word from a list. Read the token probabilities if the API gives them. | Cloud |
| B. Jev-style small vision model | An open model (for example Qwen3.5-4B). Read the probability of each answer. | Local GPU, or our own server |
| C. Small trained classifier | A frozen image encoder (CLIP or SigLIP class) and a small linear model on top, trained on our marked matches. | Local CPU |

### 2.2 The project already has labelled data

- Each marked project has `edl.json` with `startMs` and `endMs` of each
  point (`PointV1`). Each second of the video is thus "in a point" or "not
  in a point".
- The author's own projects are a free set of labelled matches. We do not
  need to label frames by hand for the trial.
- This data is enough to measure forms A and B, and to train form C.

### 2.3 Product limits

- The marketing message is "no AI to fix" and "the video stays on your PC"
  (`docs/marketing/strategy.md`). A cloud model sends frames off the PC. A
  wrong suggestion is "AI to fix". The feature must keep both promises, or we
  must change the message on purpose.
- The app is free. A cloud model costs money for each match. The author pays
  this cost, not the user.
- B-17 rejected a model of 2.4 GB that needs a GPU with 6-8 GB of video
  memory. A local 4B vision model (form B) has the same problem.
- A competitor already does this: Rallytics (desktop, Windows and Mac)
  finds the start and the end of each rally locally, from a low-resolution
  copy of the video. Its accuracy is not published. Try it with one marked
  match as a benchmark.

## 3. Possible shapes of the feature

The shapes are in the order of risk. A wrong prediction costs little in the
first shapes and much in the last shapes.

| # | Shape | What the user sees | Cost of a wrong prediction | Accuracy needed |
|---|---|---|---|---|
| S1 | Confidence strip | A new track on the timeline shows the confidence curve. The user marks by hand, but sees where the points are. | Almost none | Low |
| S2 | Jump to next point | A key moves the playhead to about 2 s before the next likely point. Optional: playback skips the dead time while the user marks. | One extra key press | Low to medium |
| S3 | Snap | The user presses Point start near the serve. The mark snaps to the nearest detected boundary. A modifier key turns the snap off. | A wrong snap that the user must undo | Medium |
| S4 | Suggested points | The app adds all points as "suggested" (a hatched range, as a pending point). The user plays each one and presses Enter to accept, Delete to reject, or nudges the start or the end. | One key for each wrong point | High |
| S5 | Automatic, then review | The app accepts all points. A review mode plays only the points at 2x speed and lists the uncertain ones first. | A missed point is invisible | Very high |

Recommendation:

- Start with S1 and S2 together. They help at once, with low accuracy. They
  keep the promise "you decide each cut".
- Go to S4 only when the trial shows a high recall (see section 6).
- S4 needs a clear state "suggested, not accepted". Do not write suggestions
  into `edl.json` as points. Keep them in a separate file, for example
  `detection.json`. Scoring and export use only accepted points.
- Do not build S5. A missed point is invisible in the review, and the user
  loses trust.

## 4. How to get from frames to points

### 4.1 Sample rate and the window of 3 frames

- 1 frame for each second gives a boundary error of up to about 1 s. This
  is acceptable for S1, S2, and S4 with a padding rule (see 4.3).
- One frame is often ambiguous. A player at the baseline can wait for the
  serve or walk to the towel. Motion tells the difference. Thus a window of
  frames is better than one frame.
- Two ways to send a window of 3 frames:
  - Sliding window, step 1 s: each request has frames t-1, t, t+1 and gives
    the class of second t. It costs 3 images for each second, or 1 image if
    we tile the 3 frames into one image (see below).
  - Blocks of 3 s, step 3 s: each request gives one class for 3 seconds. It
    costs one third as much. The boundary error grows to about 1.5 s.
- Tiling: put the 3 frames side by side in one image (a 1x3 strip). The
  model sees the motion for the price of one image. Each frame has less
  resolution, but the players are large enough at 512 px width for each
  frame.
- Test all three variants in the trial: 1 frame, 3 separate frames, and a
  tiled strip.

### 4.2 Classes

- Use more than two classes in the prompt. Then map them to "in point" and
  "not in point". This gives the model clear choices:
  - `SERVE` (the toss and the serve motion)
  - `RALLY` (the ball is in play)
  - `BETWEEN_POINTS` (players walk, pick up balls, wait)
  - `CHANGEOVER` (players sit or drink, or nobody is on the court)
  - `OTHER` (warm-up, the camera moves, no court)
- Warm-up looks like a rally. The model cannot always tell the difference
  from 3 seconds. A rule helps: rallies before the first serve are warm-up.

### 4.3 From confidence values to points

Raw values per second are noisy. Add simple rules after the model:

1. Smooth the curve, for example with a median of 3 to 5 seconds.
2. Use two thresholds (hysteresis): a point starts above 0.7 and ends below
   0.3.
3. Drop segments shorter than 3 s. Join segments with a gap shorter than
   3 s. (The badminton paper in "Sources" uses the same 3 s rules.)
4. Add padding: start = first "in point" second minus N seconds; end = last
   "in point" second plus M seconds. Learn N and M from the user's own marks
   in old projects (the median offset). Each user marks with a different
   lead-in.
5. Optional later: use the audio to place the boundary more exactly. The
   first ball hit is the serve. The last hit is the end of the rally. Ball
   hits are short, loud peaks that ffmpeg can find without a model.

### 4.4 Hard cases

- A fault on the first serve, then the second serve. This is one point. The
  gap between the serves is short, so rule 3 joins them.
- A let on the serve: the same.
- Players on the next court. The camera sees them in the background.
- Ball pickups that look like play. Players hit balls to each other.
- A camera at the side of the court, a camera on the fence, night light,
  rain.
- Doubles and padel: four players, faster rallies at the net, glass walls.
- Practice sessions and drills: no clear points. Out of scope for the first
  version.

## 5. Cost and time

### 5.1 Count of frames

A 2-hour match at 1 frame per second is 7,200 frames. With blocks of 3 s,
it is 2,400 requests.

### 5.2 Cloud vision model (form A)

- Cost for each match = requests × (image tokens + prompt tokens) × price.
- Example: one small image is about 250 to 1,000 tokens, by the model and
  the resolution. With a tiled strip, 2,400 requests, and about 1,000 tokens
  for each request, a match is about 2.4 million input tokens.
- At an example price of $0.10 to $0.40 for 1 million input tokens (the
  "flash" or "mini" class), this is about $0.25 to $1 for each match. Check
  the current prices before the trial. The trial needs only a few matches, so
  it costs a few dollars.
- At 100 active users with 4 matches each month, this is about $100 to $400
  each month. The free app cannot carry this cost for a long time.
- A cloud form needs our own proxy (a Cloudflare Worker, as for feedback) to
  hide the API key and to limit the use. It also needs an opt-in, a consent
  text, and a change to the privacy notice.

### 5.3 Local classifier (form C)

- Cost for each match: zero.
- Size: an image encoder of the CLIP or SigLIP class is about 100 to 400 MB.
  The linear model on top is a few KB. This is smaller than the B-17 limit,
  but it still makes the installer larger. A download at the first use is an
  option.
- Time: ffmpeg must decode the video to take 1 frame each second. For a
  2-hour 4K video, this can take some minutes. The encoder on the CPU needs
  about 20 to 100 ms for each frame, so 7,200 frames take 2 to 12 minutes.
  The detection can run in the background while the user starts to mark.
- License: check the license of the weights (section "Dependency rules" in
  `docs/architecture-rules.md`).

### 5.4 Summary

| Form | Cost for each match | Privacy | Size in the app | Expected quality |
|---|---|---|---|---|
| A. Cloud vision LLM | $0.25 to $1 (estimate) | Frames leave the PC | None | Good with no training. Unknown on bad cameras. |
| B. Local Jev-style 4B VLM | Zero | Stays on the PC | Several GB, needs a GPU | Good, but too heavy for our users |
| C. Local encoder + linear model | Zero | Stays on the PC | 100 to 400 MB | Good on a fixed camera, if trained on enough matches |
| Audio only | Zero | Stays on the PC | None (ffmpeg) | Good for boundaries. Weak alone (noise, next courts). |

Our guess: form A is the fastest way to learn if the idea works. Form C (or
C with audio) is the probable product, because it keeps the promises in
2.3. The trial must show this, not our guess.

## 6. What to expect as a result

These are expectations, not measurements. The trial replaces them.

- A fixed camera behind the baseline is the easy case. The court is always
  at the same place, so the classifier has a simple job.
- Points found (recall): probably 95% or more on a good camera. The missed
  points are often very short points (an ace, a return error).
- False points (precision): probably 85 to 95%. The extra points are warm-up,
  ball pickups, and play on the next court.
- Boundary error: median about 1 s, some errors of 2 to 3 s. With the learned
  padding, most starts and ends can need no edit.
- Edits for each match: maybe 5 to 15 edits for a match of 100 to 150 points.
  An edit is an add, a delete, a join, a split, or a boundary nudge.
- Time saved: marking can go from 20 to 40 minutes to about 5 to 10 minutes
  of review (S4). With S1 and S2 only, maybe a 30 to 50% saving.

### 6.1 Metrics

Measure the result against the user's own marks in `edl.json`.

| Metric | How to compute it |
|---|---|
| Frame accuracy and AUC | Each sampled second: predicted class against "inside a marked point". |
| Point recall | Share of marked points that overlap a suggested point (overlap of 50% or more). |
| Point precision | Share of suggested points that overlap a marked point. |
| Start error and end error | Median and 90th percentile, in seconds, for matched points. |
| Edits for each match | Adds + deletes + joins + splits + boundary errors larger than a tolerance (for example 1 s). |
| Marking time | Minutes to finish a match, with and without the helper. The real goal. |

### 6.2 Go and stop rules (proposal)

- Go to an in-app prototype (S1 + S2) if point recall is 90% or more on at
  least 3 matches.
- Go to suggested points (S4) if point recall is 97% or more, precision is
  90% or more, and the median boundary error is 1 s or less after padding.
- Stop if frame AUC is below 0.85 with the best variant. Then try audio,
  or drop the idea.

## 7. Trial with low effort and low cost

All steps of phases 0 to 2 run outside the app, as scripts. They do not
change the app. Use the author's own projects.

### Phase 0: one evening, about $1

1. Select 3 marked projects with different cameras or light. Copy their
   `edl.json` files.
2. Take 1 frame each second, at 512 px width, with ffmpeg:
   `ffmpeg -i match.mp4 -vf fps=1,scale=512:-2 frames/%05d.jpg`.
3. Give each frame its label from `edl.json` (inside a point or not).
4. Select 300 frames: half inside points, half outside, from all 3 matches.
   Include some frames near the boundaries and some warm-up frames.
5. Send them to one cheap cloud vision model with a fixed-answer prompt (the
   classes of 4.2). Send three variants: 1 frame, 3 separate frames, and a
   tiled strip of 3 frames.
6. Compute the frame accuracy and the AUC for each variant. Look at 20 wrong
   answers to learn the types of error.

Result: we know if a model can see "point in play" at all, and which
variant is best.

### Phase 1: one or two days, a few dollars

1. Run the best variant on one full match (2,400 to 7,200 requests).
2. Add the rules of 4.3. Learn the padding from the two other matches.
3. Compute all metrics of 6.1.
4. Make one HTML page with three tracks: the user's marks, the suggested
   points, and the confidence curve. This shows the errors faster than the
   numbers.

### Phase 2: one or two days, no cost

1. Local baseline (form C): compute the image embeddings of all frames with
   an open CLIP or SigLIP model. Train a logistic regression on two matches.
   Test on the third match. Repeat for each match (leave one match out).
2. Audio baseline: find the ball hits with ffmpeg (short peaks of energy).
   Group hits into rallies with the 3 s rules.
3. Compare A, C, audio, and C with audio on the same metrics.

Result: we know if the product needs the cloud at all.

### Phase 3: about one week

Do this phase only if the go rules of 6.2 pass.

1. Add the detection to the app behind a hidden flag, as a background job
   (as an export). Store the result in `detection.json` in the project
   folder, with the model version.
2. Show S1 (the confidence track) and S2 (the "next point" key) in the
   Points tab.
3. Mark one new match with the helper and one without. Compare the times.
4. Optional: give the build to 2 or 3 friendly users. Ask them for their
   time and the number of edits.

### What the trial needs

- 3 to 5 marked matches of the author. More matches with different
  cameras are better.
- An API key for one cloud vision model. Use a provider that gives token
  probabilities, or ask for a confidence number from 0 to 100.
- Python with ffmpeg, a vision API client, and numpy or scikit-learn. Keep
  the scripts in a separate folder, for example `tools/ai-points/`, or out of
  the repository.
- About 3 to 5 days of work for phases 0 to 2.

## 8. Open questions

| # | Question |
|---|---|
| Q1 | Is a cloud model acceptable, as an opt-in, or must the video stay on the PC? |
| Q2 | Do we change the message "no AI to fix" to "AI suggests, you decide"? |
| Q3 | Who pays for the cloud use: the author, the user with their own API key, or a paid tier? |
| Q4 | Which shape do we build first: S1 + S2 (recommended), or S4? |
| Q5 | Can we use the projects of users for training? Now the app does not send them. This needs a separate, explicit opt-in. |
| Q6 | Is a model download of 100 to 400 MB acceptable at the first use? |
| Q7 | Do padel (B-14) and doubles need their own training data? |

## Sources

- Wikipedia: [Jev (AI model)](https://en.wikipedia.org/wiki/Jev_(AI_model))
- Hackaday: [A new type of LLM on the block: decision-making models](https://hackaday.com/2026/10/06/a-new-type-of-llm-on-the-block-decision-making-models/)
- innFactory: [Jev by TypeSafe: the AI model that writes no text](https://innfactory.ai/en/blog/jev-system-one-model-classifier-not-llm/)
- Flavio Copes: [How much does Jev cost?](https://flaviocopes.com/jev-pricing/)
- OpenTweet: [Jev API access](https://opentweet.io/jev/api-access)
- arXiv: [LLM2Jev: LLMs are already Jev-style decision models](https://arxiv.org/html/2610.02076v1)
- arXiv: [From text decisions to pixels: a study of a Jev-style visual choice model](https://arxiv.org/pdf/2609.29283)
- arXiv: [Towards real-time analysis of broadcast badminton videos](https://arxiv.org/pdf/2308.12199) (frame classifier and the 3 s rules)
- arXiv: [SmartTennisTV: automatic indexing of tennis videos](https://arxiv.org/pdf/1801.01430) (rally segmentation)
- arXiv: [TennisExpert](https://arxiv.org/pdf/2603.13397) (rallies from the sound of ball hits)
- AlternativeTo: [Rallytics](https://alternativeto.net/software/rallytics/about/)

# Ball tracking, court detection, bounce and hit detection, 3D trajectory, ball speed

Date: 2026-11-16

Scope: local (desktop) analysis of amateur tennis video. One fixed phone camera behind the baseline. 4K 60 fps HEVC. Targets: RTX 3060 6 GB, MacBook Air M1, CPU-only if possible.

Evidence labels:
- "Verified" means the source was opened in this research session.
- "Not verified" means the claim comes from a search snippet, from memory, or from a source that could not be read.

Method note: some pages were read through a summarizing fetch tool. Numbers should be re-checked in the paper before they go into a decision document.

---

## 1. Summary and recommendations

### 1.1 Main findings

1. No public tennis ball tracker has been shown on amateur 4K 60 fps video from a low camera behind the baseline. All published tennis numbers use broadcast video at 1280x720 and 30 fps. The domain gap is the largest risk.
2. The best permissive baseline is WASB-SBDT (MIT code). It has a tennis model, a model zoo, and evaluation code. Verified: https://github.com/nttcom/WASB-SBDT. Its tennis F1 is 94.0 (step 3) to 95.6 (step 1) on the TrackNet tennis dataset. Verified: https://arxiv.org/html/2311.05237.
3. TrackNet-family code has license problems. TrackNetV3 is MIT (verified, badminton only). The original tennis TrackNet repository by yastrebksv shows no license on its page. TrackNetV4 has no official code (not found).
4. Ultralytics YOLO is AGPL-3.0. This is a problem for a closed commercial desktop app unless you buy an enterprise license. Not verified in this session (the search did not return the Ultralytics page), but Roboflow states it: https://roboflow.com/model-licenses/rf-detr. Use RF-DETR (Apache 2.0 for Nano to Large), RT-DETR or D-FINE (Apache 2.0, not verified) as the detector family.
5. CoTracker is mostly CC-BY-NC. It is not usable in a commercial product. Verified: https://github.com/facebookresearch/co-tracker.
6. The court detector TennisCourtDetector (yastrebksv) has a pretrained model and a 8,841-image dataset. The page shows no license. Verified: https://github.com/yastrebksv/TennisCourtDetector. Treat the code and weights as "license unknown". You must contact the author or retrain.
7. A fixed camera lets you detect the court once (or once per minute) and reuse the homography. Court detection cost is almost zero. This is a major advantage over broadcast work.
8. SwingVision does not publish a speed accuracy figure. Its speed is the average speed from contact to bounce, about 20% below peak speed. Verified: https://prod.tennis.com/news/articles/swingvision-delivers-pro-level-insights-for-recreational-players. A reviewer also says some mph readings did not match the video. Same source. Competitor line-calling claims are 97-99% (company claim, https://www.tennisnerd.net/news/swingvision-smart-court-complete-ai-system-for-tennis/45328).
9. SwingVision itself tells users to mount the phone high on the fence so both baselines show. Same Tennis.com source. A low camera is outside the vendor's own recommended setup. Expect the near baseline to be a weak area for every method.
10. No open tool gives a verified, camera-calibrated ball speed error for amateur video. You must measure it yourself with a radar gun.

### 1.2 Recommended options for a local prototype

Option A (first): WASB-SBDT tennis model as a heatmap tracker, fine-tuned on your own labeled amateur frames.
- Why: MIT code, pretrained tennis weights, 288x512 input, 3 frames, small model (HRNet-based, about 1.5 M parameters in the BlurBall variant), reported 58 fps at step 3 on a research GPU. Verified in https://arxiv.org/html/2311.05237 and https://arxiv.org/html/2509.18387v2.
- Run on crops, not full frames. A fixed camera lets you crop the court area (and rejects other courts in the background).
- Export to ONNX yourself. No ready ONNX export is documented (not verified). The network is plain convolutions, so export should be simple. Check the HRNet ops in ONNX Runtime and CoreML.

Option B (second, as a comparison and fallback): a small modern detector (RF-DETR Nano/Small, Apache 2.0) trained for one class "ball", run on tiles or crops at high resolution, with a tracker (Kalman filter or a simple physics-aware association) on top.
- Why: clean license, easy ONNX export (not verified for RF-DETR in this session), single frame input (no temporal stack), so frame rate does not matter.
- Risk: a ball is only a few pixels (see 2.3). Single-frame detectors lose the ball at motion blur. Heatmap multi-frame models use motion cues and usually win.
- Avoid Ultralytics YOLO for shipping (AGPL). You may use it for quick experiments only.

Option C (third): your own court calibration (fixed camera) plus physics-based 3D fitting.
- Court: use TennisCourtDetector-style keypoints for a first guess, then refine with a manual or semi-automatic line fit on one frame. Fixed camera means you can ask the user to confirm four corners once. This is robust and cheap.
- Events: bounce and hit from the 2D track using rule-based features first (vertical kink in image y), then a small gradient-boosting classifier. A public example exists: https://huggingface.co/kjfk/tennis-bounce-detector (MIT, F1 0.915 on held-out clips, trained on 30 fps broadcast video; verified).
- 3D and speed: fit one ballistic arc (gravity, optional drag) per shot between a hit and a bounce, using the court homography for the bounce point. See section 5.

### 1.3 Suggested prototype test plan

1. Label 500-1,000 frames of your own video (ball center, visibility flag). Include low-camera and indoor-hall clips.
2. Run WASB-SBDT pretrained as is. Measure F1 at 4 px and 10 px (at the model input scale).
3. Fine-tune on your frames. Measure again.
4. Test frame rates: 60 fps native, 30 fps (every second frame), 15 fps. Test crops against full frame.
5. Measure bounce F1 with a manual bounce label set (about 200 bounces).
6. Measure speed against a radar gun on 30+ serves.

### 1.4 Things to avoid

- CoTracker and other CC-BY-NC research code (non-commercial).
- Ultralytics YOLO in the shipped product (AGPL-3.0, or a paid enterprise license).
- Any code or weights that show no license (TrackNet tennis repo, TennisCourtDetector, TTNet repo) until the author gives a written license.

---

## 2. Ball tracking models

### 2.1 Comparison table

License risk legend: OK = permissive; CHECK = no license seen or unclear; BAD = AGPL or non-commercial.

| Model | Sport | Paper / code | Code license | Weights license | Input | Reported accuracy | Reported speed | Tennis weights | ONNX | Risk |
|---|---|---|---|---|---|---|---|---|---|---|
| TrackNet (V1) | Tennis (broadcast) | Paper: not opened. Dataset/code: https://github.com/yastrebksv/TrackNet (verified) | Not stated on page | Google Drive model, no license stated | 3 frames (frame count not stated on page) | Not reported on page | Not reported | Yes (Drive link) | No | CHECK |
| TrackNetV2 | Badminton | Code tied to badminton data (stated in WASB paper, https://arxiv.org/html/2311.05237) | Not verified | Not verified | 3 frames | Tennis re-implementation by WASB: F1 89.4, acc 81.4 | 55.3 fps | No official tennis | No | CHECK |
| TrackNetV3 | Badminton | https://github.com/qaz812345/TrackNetV3 (verified) | MIT | MIT (code and checkpoints, per page) | 8-frame sequences for training; resolution not stated | Badminton: F1 98.56, acc 97.51 | 25.11 fps (TrackNetV2: 27.70) | No (badminton) | No | OK |
| TrackNetV4 | Tennis, badminton | https://arxiv.org/abs/2409.14543 ; project page https://tracknetv4.github.io | No official repo found | n/a | Frame difference (motion attention) added to V2/V3 | Tennis F1 97.2 vs 96.5 baseline (search snippet, not verified in paper); an independent student reproduction reports no gain | Not verified | No | No | CHECK (no code) |
| WASB-SBDT | Tennis, badminton, volleyball, soccer, basketball | Paper https://arxiv.org/abs/2311.05237 ; code https://github.com/nttcom/WASB-SBDT | MIT (verified) | MODEL_ZOO.md lists weights; license of weights not read | 288x512, 3 frames (verified) | Tennis F1 94.0 (step 3), 95.6 (step 1); acc 89.0 / 91.8; 4 px threshold | 58.2 fps (step 3), 35.2 fps (step 1) on tennis (GPU not named in the fetched text) | Yes (model zoo, not opened) | Not documented | OK for code; CHECK weights |
| BlurBall | Table tennis | https://arxiv.org/abs/2509.18387 ; project page https://cogsys-tuebingen.github.io/blurball | Code license not found (only dataset on project page) | Not found | 288x512, 1-step or 3-step window (verified) | Table tennis F1 97.17 vs WASB 96.00 | 79 fps (3-step, SE model) | No | No | CHECK |
| MonoTrack | Badminton | https://arxiv.org/abs/2204.01899 (CVPRW 2022) | Code link not confirmed | n/a | Shuttle tracker plus court, pose, hit net | Badminton; WASB paper lists MonoTrack tennis F1 92.1, 64.1 fps | 64.1 fps | No | No | CHECK |
| TTNet | Table tennis | https://github.com/AugustRushG/TTNet-Real-time-Analysis-System-for-Table-Tennis-Pytorch (community re-implementation, verified) | No license file visible | n/a | Global + local stage, not stated | PCE 0.89, SPCE 0.98 (bounce/net event spotting) | Not reported | No | No | CHECK |
| YOLOv5/8/11 family | General | Ultralytics | AGPL-3.0 (not verified in session) | AGPL-3.0 | Single frame, any size | Hawk-Eye-style paper: custom YOLOv5, 89% mAP on a 3,000-image augmented set (https://arxiv.org/html/2511.04126v1) | Not reported | Community weights only | Yes (built in) | BAD for closed app |
| RT-DETR / D-FINE | General | Not opened | Apache 2.0 (not verified) | Not verified | Single frame | Not ball-specific | Not verified | No | Not verified | OK (probably) |
| RF-DETR | General | https://roboflow.com/model-licenses/rf-detr | Apache 2.0 for Nano, Small, Medium, Large; Platform Model License for Atto, Femto, Pico, XL, 2XL (verified from vendor page) | Same as code (vendor page) | Single frame | COCO small-object AP 25.3 to 37.4 for N to L (vendor numbers, not ball-specific) | Not verified | No | Not verified | OK for N-L |
| CoTracker (2/3) | Point tracking | https://github.com/facebookresearch/co-tracker | Mostly CC-BY-NC (verified) | Not stated | Video windows, GPU recommended | Not ball-specific | No numbers on page | n/a | No | BAD |
| TAPIR / TAPIR family | Point tracking | Not opened | Not verified (believed Apache 2.0) | Not verified | Video | Not ball-specific | Not verified | n/a | No | CHECK |

### 2.2 Details and failure modes

WASB-SBDT (best baseline)
- Source: https://arxiv.org/html/2311.05237 (verified through a fetch tool).
- It predicts at most one ball per frame. It cannot handle two balls in the image. On a court with other courts in the background, a second ball can confuse it. Crop to your own court.
- It was only validated at HD and FHD and about 25-30 fps. The authors say there is no theoretical limit (Section 5.5, same source).
- Failure cases named by the authors: noisy backgrounds and blurry balls (Appendix B).
- Tennis train/test data: TrackNet tennis dataset, 1280x720, 30 fps, 7 games for train and 3 games for test. Same source.

TrackNet family
- The tennis TrackNet repo gives a dataset of 19,835 labeled frames. The clips are 10 broadcast videos at 1280x720 and 30 fps. Verified: https://github.com/yastrebksv/TrackNet.
- V4 adds frame-difference "motion attention". An independent reproduction did not confirm the gain. See https://hackmd.io/@XDU5dRI5RJOkP6SWYyGUQA/HkQA5OZaJg (not opened; known only from a snippet).

BlurBall (relevant to your 4K 60 fps blur issue)
- It predicts ball position and the blur streak (half length and angle). The ball position is the midpoint of the blur streak.
- About 62% of its table tennis frames show blur.
- Position-plus-blur cuts the median trajectory prediction error from 28.4 px to 19.9 px.
- It is a table tennis paper. Tennis balls blur less at 60 fps than a table tennis ball at the same fps, but a serve at 150+ km/h can still smear over many pixels at 4K. Not verified for tennis.
- Source: https://arxiv.org/html/2509.18387v2.
- It matters for speed: the blur length gives a speed cue in a single frame. This is a research idea, not a proven tennis method.

Detectors (YOLO, RF-DETR, D-FINE)
- A single-frame detector sees only a small yellow-green blob. The 2025 Hawk-Eye-style paper reports problems with small, blurred, or occluded balls and with poor labels. The 89% mAP is on an augmented set of 3,000 images made from 578 images. Source: https://arxiv.org/html/2511.04126v1. Treat that number as weak evidence.
- Single-frame detectors work at any frame rate. They also work on 4K tiles.

Point trackers (CoTracker, TAPIR family)
- They track a chosen point through video. They need a first ball position and fail when the ball flies fast or goes out of the frame. They are heavy. CoTracker is non-commercial. No tennis ball results found. Low priority.

### 2.3 Ball size at 4K (calculation, not from a source)

- A tennis ball is 6.7 cm in diameter.
- With a phone camera behind the baseline, the far baseline is about 24 m to 30 m away. At 4K width (3840 px) with a horizontal field of view that just covers the court (about 10 m wide at the far baseline), one pixel is about 2.6 mm. The ball is then about 25 px wide at the far baseline.
- At 1280x720 broadcast scale, a similar ball is about 6 to 10 px. So 4K video is better for small ball detection than the data these models were trained on. But you must reduce the cost.
- The WASB model input is 288x512. If you resize a 4K frame to 512 px wide, the far ball is about 3 px wide. A crop of the court area at higher scale keeps the pixels. A tile approach (for example 2 to 4 tiles of 512x288 at 2x downscale) is a better trade.

---

## 3. Minimum requirements: frame rate, resolution, crops

### 3.1 What papers say

| Question | Answer | Source |
|---|---|---|
| Input size used by WASB and BlurBall | 288x512, 3 frames stacked on channels | https://arxiv.org/html/2311.05237 ; https://arxiv.org/html/2509.18387v2 |
| Frame rates tested | Tennis data: 30 fps. Other datasets: about 25-30 fps. No 60 fps test in WASB. | https://arxiv.org/html/2311.05237 |
| TrackNet tennis data | 1280x720, 30 fps | https://github.com/yastrebksv/TrackNet |
| TrackNetV3 | 8-frame sequences, badminton, resolution not stated on page | https://github.com/qaz812345/TrackNetV3 |
| Step size | WASB step 3 (no overlap) gives 58 fps; step 1 gives 35 fps and about 1.6 F1 points more | https://arxiv.org/html/2311.05237 |

### 3.2 Implications for your pipeline (analysis, not verified by a source)

- Frame rate: the models learn motion over 3 consecutive frames. If you decode at 30 fps (every second frame of the 60 fps video), the ball moves twice as far between frames. This matches the training data (30 fps). So 30 fps input is the natural setting for the pretrained weights. A 60 fps input has a smaller step. The model may still work, but the pretrained weights have not seen it. You must test both.
- Decode cost: you can skip half the frames at 30 fps. HEVC decoders cannot skip P-frames cheaply, but a hardware decoder (NVDEC, VideoToolbox) can decode all frames and drop half after decode. Decode cost is then still 0.5x of the video duration. Check hardware decode plus GPU downscale (for example NVDEC with scaling on the GPU) so that only a small image leaves the GPU. Not verified.
- Crop: a fixed camera allows a fixed crop of the court area. Decoders cannot crop before decode. The crop saves compute in the network and removes background courts. It does not save decode time.
- Event windows: bounce and hit logic needs only the ball track. You can run the heavy tracker only in "rally windows". Find rally windows with a cheap detector (player motion at low resolution, or audio hit sounds). This is an idea; audio hit detection is outside this topic.
- Low camera: the near baseline may be out of the frame. The near-side hit and the near-side bounce are then not visible. The tracker still sees the ball after the hit. Bounce position on the near side is not available. Use the far-side bounce and the 3D fit only.

### 3.3 Hardware (not verified)

- WASB reports 58 fps at step 3 on a research GPU. On an RTX 3060 and 288x512 input, the speed should be in the same range. For a 90 minute match at 30 fps, there are 162,000 frames. At 100 triplets per second, that is about 27 minutes. A CPU or an M1 will be slower. Measure it.
- Run only on rally windows to cut this by 50 to 70%.

---

## 4. Court detection and homography

### 4.1 Options

| Option | Type | Code / weights | License | Input | Accuracy | Notes |
|---|---|---|---|---|---|---|
| TennisCourtDetector (yastrebksv) | Heatmap keypoints (TrackNet-like), 14 points plus court center, then line-based refinement and homography | https://github.com/yastrebksv/TennisCourtDetector (verified) | Not stated on page | 640x360 | Precision 0.936, median error 2.83 px (base); 0.963 and 1.83 px with refinement and homography (7 px threshold) | Dataset 8,841 images, 1280x720, 14 keypoints, hard, clay, grass; from YouTube broadcasts. No speed given. |
| ResNet50 keypoint model in the Hawk-Eye-style paper | 14 keypoints | No code link | Not stated | Not stated | No numbers | Far net posts and far baseline corners are the most occluded. https://arxiv.org/html/2511.04126v1 |
| MonoTrack court module | Court recognition for badminton | https://arxiv.org/abs/2204.01899 | Not verified | n/a | "Improved court recognition" (no numbers read) | Badminton. |
| Classical line detection (Hough, line fitting, template match) | Classical | Many open examples | Depends | Any | Not verified | Works well on a fixed camera when you add a user-confirmed first guess. |
| Manual 4-point or 6-point click calibration | Manual | Your own code | Yours | Any | Sub-pixel when clicked well | Fixed camera makes this a good fallback. |
| Newer sports-field registration (for example work on soccer and basketball, "SoccerNet calibration" challenge) | Learned | Not opened | Not verified | n/a | Not verified | Not tennis-specific. Not researched in detail in this session. |

### 4.2 Assessment for amateur video

- The public court dataset is broadcast video from a high camera. Your video is a low phone camera in a hall. Expect a domain gap. Hall lines of other sports (badminton, basketball lines on the same floor) may confuse a line-based method. Not verified; this is a known practical issue.
- A fixed camera makes the problem easy. You need one good homography per video (or per camera move). A user can confirm it with four clicks.
- If the near baseline is out of the frame, you cannot see the near baseline corners. Use the visible parts: far baseline, both sideline segments, service lines, net posts. A homography needs four point correspondences. You can use line intersections from the service box and the far baseline. Fit the homography to lines instead of points.
- For a 3D fit, you need more than a homography. You need a full camera model (intrinsics and pose). With a known court plane you can get the pose by PnP from 4 or more court points plus a focal length guess from the phone camera (EXIF or metadata). See section 5.

### 4.3 Recommendation

- First guess: TennisCourtDetector weights run on one frame (or several frames) with a median over the results. Ask the user to confirm and fix the points in the UI.
- Do not ship its weights until you have clear permission. The repo page shows no license. Alternative: train your own keypoint model on its public dataset (its license is also not stated) or on your own labeled frames. Your own labeling of 200 to 300 amateur frames is cheap for a fixed-camera setup.

---

## 5. Bounce detection and hit detection

### 5.1 Methods

| Method | Description | Reported result | Source |
|---|---|---|---|
| Rule-based kink in image y | Bounce = local maximum of image y (lowest image point) of the ball track, with a change of vertical direction | "Bounce as sudden change in trajectory"; 99.4% on 394 tagged samples; 81.8% on 11 samples with bounce points (abstract wording unclear) | https://arxiv.org/abs/2107.09255 (monocular line calling, abstract only) |
| Gradient boosting on trajectory features | 16 scale-free features (image kink, height curvature, speed ratio, direction change); candidate proposals from the kink | Held-out clips: precision 0.888, recall 0.944, F1 0.915. Leave-one-match-out F1 0.951. Proposal recall 99.3%. Trained on 30 fps broadcast tennis. | https://huggingface.co/kjfk/tennis-bounce-detector (MIT; model card of one user; verified) |
| CatBoost on 20-frame windows after TrackNet | Bounce classifier over a 20 frame window | Not given for bounce in the snippet. Ball tracking F1 89.9 vs 84.3 for the original benchmark. | Search snippet only; not verified. Paper link not identified. |
| GRU event detector | Detects hits and bounces to cut video into shots (SynthNet) | No numbers visible on the abstract page | https://researcher.itu.dk/en/publications/synthnet-leveraging-synthetic-data-for-3d-trajectory-estimation-f/ ; paper https://dl.acm.org/doi/pdf/10.1145/3689061.3689073 (not opened) |
| Learned event spotting inside the tracker (TTNet) | Multi-task net spots bounce and net events | PCE 0.89, SPCE 0.98 (table tennis re-implementation) | https://github.com/AugustRushG/TTNet-Real-time-Analysis-System-for-Table-Tennis-Pytorch |
| Hit net (MonoTrack) | Recurrent network on pose, shuttle, and court features to segment shots | "Improved hit recognition" (no numbers read) | https://arxiv.org/abs/2204.01899 |

### 5.2 Notes

- Bounce vs hit: a bounce changes the vertical velocity sign only. A hit changes the horizontal velocity (direction toward the other side) and speed. A rule-based hit detector can use the change of the ball direction near a player (racket) position. Player position from a person detector helps. Not verified as a published tennis rule set.
- At 60 fps, the bounce frame is better resolved than at 30 fps. The ball covers less distance per frame. A bounce feature classifier trained on 30 fps data needs retraining for 60 fps (features scale with time step). Not verified.
- In/out from a single camera: no peer-reviewed, amateur-camera accuracy figure was found.
  - Monocular line-calling paper: 99.4% on 394 samples. The abstract does not say what the metric measures. Source: https://arxiv.org/abs/2107.09255.
  - Company claims: 97-99% line calls (SwingVision, a company claim, https://www.tennisnerd.net/news/swingvision-smart-court-complete-ai-system-for-tennis/45328).
  - A reviewer says SwingVision is better than human eyes for shots within 10 cm of a line. Source: https://prod.tennis.com/news/articles/swingvision-delivers-pro-level-insights-for-recreational-players.
  - The vendor says the phone must show both baselines and alleys.
- Physical limit (analysis): the ball is on the ground for a few milliseconds and compresses. At 60 fps, the frame interval is 16.7 ms. A ball at 25 m/s moves 42 cm between frames. A bounce is mostly between two frames. The bounce point must be interpolated from the trajectory fit. Image-space error of one pixel at 4K is about 3 mm near the far baseline (see 2.3), but the ball track error and the timing error dominate. Expect an in/out error of several centimeters at best on a far-side bounce, and worse on a near-side bounce. This is an estimate, not a source.
- Line calls for amateurs: treat calls near a line (about 10 cm) as "uncertain" instead of in or out. This is a product decision, not a research result.

---

## 6. Monocular 3D trajectory and ball speed

### 6.1 Papers and methods

| Work | Sport | Method | Training data | Reported accuracy | Code | Source |
|---|---|---|---|---|---|---|
| SynthNet (2024) | Tennis | GRU hit and bounce detection plus a fully connected net that estimates initial conditions; physics-based loss | Purely synthetic | No numbers on the abstract page; "outperforms baseline" | None listed | https://researcher.itu.dk/en/publications/synthnet-leveraging-synthetic-data-for-3d-trajectory-estimation-f/ |
| Where Is The Ball (2025) | General sports | LSTM with a camera-independent canonical 3D representation; reprojection consistency | Simulation only | "State of the art" on 4 synthetic and 3 real datasets; no numbers on the abstract page | Project page https://where-is-the-ball.github.io (code not confirmed) | https://arxiv.org/abs/2506.05763 |
| MonoTrack (2022) | Badminton | Nonlinear optimization with court size and physics; shot segmentation net | Real and 10k synthetic trajectories | "State of the art" (no numbers read) | Not confirmed | https://arxiv.org/abs/2204.01899 |
| TT3D (2025) | Table tennis | Physics model; picks the bounce state that minimizes the reprojection error | n/a | Not read | Not read | https://arxiv.org/html/2504.10035v1 |
| Uplifting Table Tennis (2025) | Table tennis | Ball and table keypoint detectors plus 3D and spin | n/a | Not read | Not read | https://arxiv.org/abs/2511.20250 |

All entries marked "not read" are known only from search snippets.

### 6.2 What to implement (analysis)

Physics fit for one shot with a fixed, calibrated camera:
1. Calibrate: court plane to image homography, then full camera pose by PnP with a focal length guess.
2. Segment shots: hit to bounce, bounce to next hit.
3. Fit a ballistic arc with state (x0, y0, z0, vx, vy, vz) plus drag coefficient. Minimize the reprojection error between the projected arc and the 2D track points.
4. Add the bounce constraint: z = ball radius at the bounce frame, and the bounce point lies in the court plane. This fixes the depth ambiguity of a single camera.
5. Speed = norm of the velocity at hit (arc start) or the average over the arc.

Why the constraint matters: a single 2D track of a free-flying ball has a depth ambiguity. Gravity gives scale (the vertical acceleration is known), so a calibrated camera plus gravity gives a metric trajectory. The bounce on the court plane gives an anchor. This is the same idea as in MonoTrack and TT3D.

Speed error sources (estimates, not from a source):
- Calibration error (focal length and pose).
- 2D track noise.
- Timing: 60 fps helps. A 30 fps track doubles the frame step.
- Serve speed at the hit: the arc starts at an unknown height, so the first arc segment is poorly constrained. The ball leaves the racket near the top of the toss, and the camera may not see it well.
- Drag: a tennis ball loses a large part of its speed in flight. SwingVision states that its speed is an average from contact to bounce and is about 20% below the top speed. Source: https://prod.tennis.com/news/articles/swingvision-delivers-pro-level-insights-for-recreational-players. A physics fit with drag can report the speed at contact instead. This could be an improvement over that definition. Not verified as an accuracy gain.

### 6.3 SwingVision statements

- Speed method: average over the flight from contact to bounce, about 20% below top speed (Tennis.com, verified).
- The same article says some mph readings did not match the video (reviewer view).
- No published speed error figure. The App Store text mentions "improved serve speed accuracy" without numbers (search snippet, not verified).
- Setup: phone mounted high and far behind the baseline, ideally on the fence, both baselines and alleys visible (Tennis.com, verified).
- Line call accuracy claims: 97-99% (TennisNerd, vendor claim).

### 6.4 Product guidance

- Do not claim radar-grade speed. Show speed as an estimate with a range. Use the same "average from contact to bounce" definition as SwingVision for comparison, and optionally add a "peak at contact" value.
- Validate against a radar gun before launch. No literature value can replace this.

---

## 7. Public datasets

| Dataset | Content | Size | License | Camera | Source |
|---|---|---|---|---|---|
| TrackNet tennis dataset | Ball positions per frame, visibility, trajectory pattern | 10 broadcast videos, 19,835 labeled frames, 1280x720, 30 fps | Not stated on page | Broadcast | https://github.com/yastrebksv/TrackNet (verified) |
| TennisCourtDetector dataset | 14 court keypoints per image | 8,841 images, 1280x720; hard, clay, grass; 75/25 split | Not stated on page | Broadcast (YouTube highlights) | https://github.com/yastrebksv/TennisCourtDetector (verified) |
| WASB-SBDT data and splits | Evaluation setups for tennis, badminton, volleyball, soccer, basketball | Tennis: 7 games train, 3 games test (65 + 30 clips, 14,160 + 5,675 frames) | MIT for the repository; data license not read | Mixed (tennis from TrackNet) | https://github.com/nttcom/WASB-SBDT ; https://arxiv.org/html/2311.05237 |
| BlurBall dataset | Table tennis ball center, blur angle and length, calibration | 26 recordings (amateur and professional), 51,423 train and 12,696 test frames | Not stated (paper is CC BY-SA 4.0) | Fixed cameras, varied views | https://arxiv.org/html/2509.18387v2 |
| RacketVision (2025) | Ball positions and racket pose for table tennis, tennis, badminton | Not read | Not read | Not read | https://arxiv.org/html/2511.17045v3 (search result only) |
| OpenTTGames | Table tennis with bounce, net, and empty events | Not read | Not read | Fixed | Search snippet only (not verified) |
| Ball dataset from Hawk-Eye-style paper | Tennis ball boxes | 8,763 / 584 / 390 images (abandoned by the authors: mixed angles, poor labels); refined 428 / 100 / 50 | Not stated | Broadcast, mixed | https://arxiv.org/html/2511.04126v1 |
| Badminton Shuttlecock Trajectory dataset (used by TrackNetV3) | Shuttle positions | Not stated | Not stated | Broadcast | https://github.com/qaz812345/TrackNetV3 |
| PadelTracker100 | Padel ball, player, shot labels, pose | Two matches | Not read | Not read | Search snippet only (not verified) |
| Bounce training data of the HF bounce model | Per-frame ball coordinates and bounce labels | Derived from TrackNet tennis data | MIT (model) | 30 fps broadcast | https://huggingface.co/kjfk/tennis-bounce-detector |

Gaps:
- No public tennis dataset with amateur video, a low phone camera, or 60 fps.
- No public dataset with tennis bounce labels in court coordinates and measured ball speed.
- You must build a small private set: ball positions, bounce frames, court points, and radar speeds for 30 serves.
- No dataset license was checked in detail. Read each license before you train a shipped model.

---

## 8. Open questions

1. What F1 does the pretrained WASB tennis model reach on your amateur 4K video, at 30 fps and 60 fps, on a court crop?
2. Does fine-tuning with 500 to 1,000 labeled frames close the domain gap? How many frames do you need?
3. What is the license of the WASB model-zoo weights and of the TrackNet and TennisCourtDetector weights? The repository pages show MIT (WASB code) and no license (the others). Ask the authors if needed.
4. Does an ONNX export of the WASB HRNet work in ONNX Runtime on Windows (CUDA and DirectML) and on the M1 (CoreML)? Is it fast enough on CPU only?
5. Can you find rally windows cheaply (low-resolution player motion or audio), so that the tracker runs on 30 to 50% of the video?
6. How large is the speed error against a radar gun with a phone-based focal length guess? Is a manual court calibration needed?
7. What is the bounce position error against a hand-marked bounce (ball mark or slow-motion video), for far-side and near-side bounces?
8. How does the near-baseline-out-of-frame case change the hit and bounce detection? Which events do you give up?
9. Do doubles and other courts in the hall cause false ball tracks? Does a court mask fix it?
10. Do newer 2025-2026 tennis-specific trackers exist that this research did not find? The search found no tennis-specific successor to WASB. Run one more search on arXiv ("tennis ball tracking" and "amateur") before you freeze the design.
11. Is the "Where Is The Ball" code released? It could replace a hand-written physics fit. Check https://where-is-the-ball.github.io.
12. SynthNet: are code, data, and accuracy numbers available in the ACM paper? Check https://dl.acm.org/doi/pdf/10.1145/3689061.3689073.

---

## 9. Sources

Opened and used (verified, some through a summarizing fetch tool):
- WASB-SBDT repository (MIT): https://github.com/nttcom/WASB-SBDT
- WASB paper (BMVC 2023), HTML: https://arxiv.org/html/2311.05237 (abstract page: https://arxiv.org/abs/2311.05237)
- TrackNetV3 repository (MIT): https://github.com/qaz812345/TrackNetV3
- TrackNet tennis repository: https://github.com/yastrebksv/TrackNet
- TennisCourtDetector repository: https://github.com/yastrebksv/TennisCourtDetector
- BlurBall paper: https://arxiv.org/html/2509.18387v2 ; project page: https://cogsys-tuebingen.github.io/blurball
- SynthNet record: https://researcher.itu.dk/en/publications/synthnet-leveraging-synthetic-data-for-3d-trajectory-estimation-f/
- Where Is The Ball: https://arxiv.org/abs/2506.05763
- Monocular line-calling paper: https://arxiv.org/abs/2107.09255
- Hawk-Eye-style tennis system paper: https://arxiv.org/html/2511.04126v1
- Tennis bounce detector (Hugging Face, MIT): https://huggingface.co/kjfk/tennis-bounce-detector
- TTNet re-implementation: https://github.com/AugustRushG/TTNet-Real-time-Analysis-System-for-Table-Tennis-Pytorch
- CoTracker repository (CC-BY-NC): https://github.com/facebookresearch/co-tracker
- SwingVision review (speed definition, setup): https://prod.tennis.com/news/articles/swingvision-delivers-pro-level-insights-for-recreational-players
- SwingVision Smart Court (accuracy claims): https://www.tennisnerd.net/news/swingvision-smart-court-complete-ai-system-for-tennis/45328

Seen only in search results (not verified):
- TrackNetV4 paper and project page: https://arxiv.org/abs/2409.14543 ; https://tracknetv4.github.io ; https://time.griffith.edu.au/paper-sites/tracknet-v4
- TrackNetV4 reproduction: https://hackmd.io/@XDU5dRI5RJOkP6SWYyGUQA/HkQA5OZaJg
- MonoTrack: https://arxiv.org/abs/2204.01899 (CVPRW 2022; https://openaccess.thecvf.com/content/CVPR2022W/CVSports/papers/Liu_MonoTrack_Shuttle_Trajectory_Reconstruction_From_Monocular_Badminton_Video_CVPRW_2022_paper.pdf)
- RF-DETR licensing (vendor): https://roboflow.com/model-licenses/rf-detr ; https://blog.roboflow.com/rf-detr-is-free-to-use-commercially/
- TT3D: https://arxiv.org/html/2504.10035v1
- Uplifting Table Tennis: https://arxiv.org/abs/2511.20250
- RacketVision: https://arxiv.org/html/2511.17045v3
- SwingVision App Store page: https://apps.apple.com/US/app/id989461317

Claims without a source in this session (from general knowledge; check before use):
- Ultralytics YOLO is AGPL-3.0 with an enterprise license option.
- RT-DETR and D-FINE are Apache 2.0.
- TAPIR is Apache 2.0.
- ONNX export of RF-DETR and RT-DETR works.

# Research: rally segmentation, event spotting, stroke recognition, pose, audio, foundation models

Date: 2026-11-16

Scope: local (on-device) automatic analysis of amateur tennis video.
Input: one fixed phone camera behind the baseline, 4K 60 fps HEVC, stereo AAC, 90-120 minutes.
Targets: Windows laptop with RTX 3060 (6 GB), MacBook Air M1, ideally CPU-only too.

Rules for this report:
- Each claim has a URL. "Not verified" means that I could not open a source for the claim.
- Many search results came as summaries. For key facts (license, numbers), I opened the primary page. These facts are marked "opened".
- License notes: AGPL, GPL and non-commercial licenses are a problem for a closed commercial desktop app. A GPL or AGPL library in the app forces the app to be open source (or needs a commercial license).
- This report is not legal advice.

---

## 1. Summary and recommendations

### 1.1 Main findings

1. No public work solves our exact task (amateur, fixed rear camera, point start/end, winner, fault) end to end.
   The closest open items are RallyClip (pose + BiLSTM, GPL-3.0, no accuracy number) and TactiPlay (a VLM on badminton, 75% rally segmentation, 68% winner).
2. All strong event-spotting work (E2E-Spot, T-DEED, F3Set/F3ED, UMEG-Net) trains on broadcast video at 25-30 fps and 224 px frames.
   Broadcast views differ from our view. We must retrain. Pretrained tennis weights will transfer only in part.
3. Our data (about 1100 marked points with start, end, winner) is enough to train a small point-segmentation model.
   It is not enough to train a hit-spotting model, because it has no per-shot labels.
4. Audio is the cheapest strong signal for hit times. Prior work uses audio alone, or audio with video, for hit detection.
   Other courts in the hall are the main risk. Stereo level and phase differences may help. This is untested in the literature that I found.
5. Pose and player detection have good permissive options: RTMPose/RTMO/RTMW (Apache-2.0 code), YOLOX (Apache-2.0), RF-DETR Nano to Large (Apache-2.0), D-FINE (Apache-2.0), ViTPose (Apache-2.0).
   Avoid Ultralytics YOLO (AGPL), Sapiens pose weights (CC BY-NC 4.0), and RF-DETR XL/2XL (platform license).
6. VLMs are poor at "who won this point". TennisTV reports 56.3% for the best model (GPT-4o) on the point-winner task. Small local VLMs will do worse.
   A trained small head on frozen features, plus score logic, is more promising.
7. The score grammar of tennis gives strong constraints (serve side, server changes). Use it as a decoder over noisy per-point predictions.
   I found no paper that does this from video. It is an open opportunity.
8. First-serve fault detection has no direct published work for tennis. The signals exist: a second serve follows a first serve with no rally between, and the same player serves again from the same side.
   We can learn it from our own marks (see 1.3).

### 1.2 What to try first (prototype order)

| Step | Task | Method | Cost | Why first |
|---|---|---|---|---|
| P1 | Point start/end | Audio hit events + low-rate pose/player-position features at 1-5 fps, then a small temporal model (BiGRU or TCN) trained on our 1100 points | Low | Uses cheap signals. Our labels fit this directly. |
| P2 | Audio hit detector | Log-mel features at 16 kHz + small CNN (EfficientAT mn04/mn10 or PANNs CNN14 features) trained on our marked time ranges. Energy peak detector as a baseline. | Low | Hit times give rally structure (hit counts, gaps) with no video decode. |
| P3 | Player features | RTMPose/RTMO via rtmlib (ONNX) on 1-2 fps downscaled frames of the court region. YOLOX or RF-DETR for person boxes. | Medium | Pose motion shows serve toss and ready position. |
| P4 | Winner | Features: last-hit side, who served next (server change), score grammar decoder. Optional: frozen DINOv2/DINOv3/V-JEPA 2 features of the last seconds. | Medium | Rule-based decoding may beat any visual model. |
| P5 | Fault / second serve | Sequence rule + classifier: short point (1 hit) followed by a second serve from the same player and side. | Low | See section 8. |
| P6 | Stroke type | Pose sequence classifier around each audio hit. Pretrain on public data, fine-tune on a small hand-labeled set from our matches. | Medium | Needs new labels. |
| P7 | Precise hit spotting in video | T-DEED or E2E-Spot style model on 2-pixel-small crops at 25-30 fps. Defer. | High | Needs per-shot labels and a full decode. |

Rationale: P1-P5 use only our existing labels and cheap inputs (audio plus 1-5 fps frames). They also run on CPU.
P6-P7 need new labels and more compute.

### 1.3 Fast experiments with our own data

- E1: Hit-audio proxy. Compute energy-peak candidates in the first 5 seconds after each marked point start. Check how many are within the rally. No new labels needed.
- E2: Point-segmentation from audio only (inter-hit gap model). A rally is a chain of hits with gaps below about 4 seconds. Compare with our marks. This gives a baseline in a day.
- E3: Add 1 fps frame features from a frozen small image model (DINOv2 ViT-S, DINOv3 ViT-S, or SigLIP 2) plus a BiGRU. Compare to E2.
- E4: Fault: from our marks, find point pairs that share the server and have a short first point. Check if our marks already contain this pattern (needs a "fault" flag; if absent, add the flag to a few matches).
- E5: Run RTMPose-s/m on 2 fps crops of the near court. Check keypoint quality for the far player at 4K crop versus a 1080p downscale.

### 1.4 License summary of recommended components

| Component | License | Commercial use | Note |
|---|---|---|---|
| rtmlib + RTMPose/RTMO/RTMW code | Apache-2.0 | Yes | Weights may carry dataset terms. Check the weights per model (see 4). |
| YOLOX | Apache-2.0 | Yes | Opened. |
| RF-DETR Nano/Small/Medium/Large | Apache-2.0 | Yes | XL/2XL, Atto-Pico: platform license. Opened. |
| D-FINE | Apache-2.0 | Yes | Objects365 checkpoints are not cleared. Opened. |
| ViTPose | Apache-2.0 | Yes | From Hugging Face model card (search result). |
| EfficientAT (audio) | MIT | Yes | Opened. Check weight terms. |
| PANNs code | MIT | Yes for code | Weight license not stated. Opened. |
| E2E-Spot | BSD-3 | Yes | Opened. |
| T-DEED | GPL-3.0 (page says so; also a separate E2E-Spot license file) | Problem for closed source | Opened. Use as an idea, re-implement. |
| F3Set/F3ED | No license in the repo page | Unclear. Treat as all rights reserved | Opened. Ask the authors. |
| V-JEPA 2 | MIT (most files) | Yes | Opened. |
| DINOv3 | DINOv3 License (custom, gated, no commercial ban, field-of-use limits) | Probably yes. Read it with a lawyer | Opened. |
| Qwen3-VL | Apache-2.0 | Yes | Opened (8B card). |
| SmolVLM2 | Apache-2.0 | Yes | Opened. |
| SigLIP 2 | Apache-2.0 | Yes | Opened (base model). |
| Ultralytics YOLO | AGPL-3.0 | No (without commercial license) | Known fact, not opened here. |
| Sapiens pose | CC BY-NC 4.0 | No | From model cards (search). |
| RallyClip | GPL-3.0 | No | Opened. |

---

## 2. Rally / point segmentation in racket sports

### 2.1 Methods and projects

| Work | Sport | Signals | Method | Reported accuracy | License / code | Source |
|---|---|---|---|---|---|---|
| RallyClip | Tennis (amateur) | Pose (YOLOv8 pose, 15 fps), court mask | BiLSTM on 20 s chunks, hysteresis smoothing | None reported. Trained on about 8 h of marked matches. Confuses serve/return motion with the start of a point. | GPL-3.0 | https://iroblesrazzaq.github.io/RallyClip/ (opened) |
| RallyClip speed | - | - | M2 MacBook Pro: YOLO nano about 0.8x video length on GPU (MPS), about 3x on CPU. YOLO small about 1.5x on GPU, about 7x on CPU. | - | - | same page |
| SmartTennisTV | Tennis (broadcast) | Frame classifier (camera view) | Rally = overhead camera view. Plus scoreboard reading. | Not verified (number not seen) | Not verified | https://arxiv.org/pdf/1801.01430 |
| Unsupervised temporal feature aggregation | Table tennis (arbitrary camera) | Player-level features | Window classifier | F1 0.79 (global frame features) to 0.89 (player features) | Not verified | https://arxiv.org/pdf/2002.08097 |
| TactiPlay | Badminton (amateur, fixed high camera) | Video at 1 fps, 1-minute segments | VLM (Doubao-seed-1.6, closed API) with overlap | 75% segmentation, 68% winner/reason (109 test rallies) | Not verified | https://arxiv.org/html/2607.27125v1 (opened) |
| Broadcast badminton real-time analysis | Badminton (broadcast) | CNN rally/non-rally classifier; rallies grouped by gaps of at least 3 s | CNN | Beats HOG+SVM (numbers not seen) | Not verified | https://arxiv.org/pdf/2308.12199 |
| Padel multi-modal hit detection | Padel (fixed camera) | Audio hit detection + player positions | Sound event detection + video | Numbers not verified (page blocked, HTTP 403) | Dataset: highlights + 2 full matches | https://openaccess.thecvf.com/content/CVPR2024W/CVsports/papers/Decorte_Multi-Modal_Hit_Detection_and_Positional_Analysis_in_Padel_Competitions_CVPRW_2024_paper.pdf |
| Open pickleball/padel pipelines | Pickleball/padel | Court detection, pose, hits | Heuristics | No evaluation | Not verified | search result only (not verified) |

### 2.2 Observations

- Rally segmentation in amateur video has little peer-reviewed work. The reported numbers are weak or absent.
- RallyClip's author rejected sound because of other courts and quiet shots. Our data has stereo audio, so we can test this claim ourselves.
- The main failure of pose-only models is the "serve prep and return" phase. It looks like play but is not part of the point.
  Our labeled start times let us learn the exact boundary (for example, start = toss or racket-ball contact of the serve).
- Typical structure that works across sports: per-frame (or per-second) "in play" probability, smoothing with hysteresis, then threshold on duration.
  Add audio hit events as a second channel.
- 1 fps input is probably too coarse for the start time (serve contact lasts a fraction of a second). Use audio for the precise time, video for the coarse window.

### 2.3 Recommended design for us

- Input channels at 1 s step: (a) hit-audio probability, (b) hit count in the last 10 s, (c) pose-motion energy of the near player and far player, (d) player positions (behind baseline versus in court), (e) frozen image embedding (optional).
- Model: BiGRU or temporal convolution over a 60-120 s window. Output: in-play probability and start/end boundary heatmaps.
- Loss: frame-wise BCE plus boundary regression (Gaussian targets). Evaluate by matching start/end within 1 s, 2 s and 5 s tolerance.
- Training: leave-one-match-out over our 7 matches (7 folds). Do not mix frames of one match across train and test.

---

## 3. Precise temporal event spotting (hit, bounce, serve)

### 3.1 Comparison

| Method | Venue | Core idea | Tennis support | Code and license | Weights | Input | Compute |
|---|---|---|---|---|---|---|---|
| E2E-Spot | ECCV 2022 | End-to-end CNN (RegNetY + GSM) + GRU, per-frame classification | Yes ("Tennis" dataset) | BSD-3 (opened): https://github.com/jhong93/spot | Weights repo exists: https://github.com/jhong93/e2e-spot-models/ (tennis weights not confirmed) | Frames pre-extracted, about 224 px high | Trains on one GPU. No numbers on the page. |
| T-DEED | CVPRW 2024 | Encoder-decoder with Scalable-Granularity Perception layers | FigureSkating, FineDiving, FineGym, Tennis, SoccerNet(Ball) listed in the repo | GPL-3.0 (opened): https://github.com/arturxe2/T-DEED | Google Drive checkpoints | Fixed-size clips, same resolution as training | Not stated on the page |
| ASTRA | 2024 | Transformer for SoccerNet action spotting, balanced mixup, displacement head, optional audio | No | Not verified (repo URL not found) | Not verified | Not verified | Not verified. Paper: https://arxiv.org/abs/2404.01891 |
| ASPERA | 2025 | ASTRA + audio + commentary text | No | Not verified | Not verified | - | https://scitepress.org/PublishedPapers/2025/133007 |
| F3ED (F3Set) | ICLR 2025 | Event localizer + multi-label classifier + context module on TSM/RegNetY-200MF + BiGRU | Yes, the main case study | No license shown (opened): https://github.com/F3Set/F3Set | Checkpoints folder exists in repo | Pre-extracted frames, 224 px, 25-30 fps | Linux tested, no hardware info |
| UMEG-Net | AAAI 2026 / arXiv 2511.14186 | Graph of pose + ball + court keypoints, with distillation, few-shot | Yes (F3Set tennis) | Not verified | Not verified | Skeleton input | Not verified. https://arxiv.org/pdf/2511.14186 |
| Skeletons to Pixels | arXiv 2604.22839 (Apr 2026) | Few-shot PES, representation and prediction distillation | Yes (F3Set tennis subset) | Not verified | Not verified | - | https://arxiv.org/pdf/2604.22839 |
| SoccerNet 2025 ball action spotting winner | 2025 | T-DEED with one unified head for action-team classes. Pre-train on 500+ broadcast matches, then fine-tune. | No | Not verified | Not verified | - | Team-mAP@1 60.03 vs baseline 51.72. https://arxiv.org/html/2508.19182v1 |

### 3.2 F3Set facts (opened)

Source: https://arxiv.org/html/2504.08222v1
- 114 broadcast singles matches (2012-2023) from YouTube. 11,584 rallies, mean 8.4 s. 42,846 shots. About 1,108 event types.
- Frame rate 25-30 fps. Event is the ball-racket contact frame.
- Each shot has up to 8 labels: near/far player, court position (deuce/middle/ad), forehand/backhand, serve/return/stroke, direction, technique (ground stroke, slice, volley, lob, drop, smash), approach, outcome (in, winner, forced error, unforced error).
- F3ED (TSM encoder, "high" setting): event F1 40.3, element F1 75.2, edit score 74.0. Best baseline (TSM + E2E-Spot): 31.4 / 71.4 / 68.7.
- The event F1 for the full 8-part label is low (40%). The element F1 is high (75%). Single parts (such as "serve vs. stroke") work much better than the full combination.
- Camera: broadcast, including some low views behind one player. Our camera is a fixed phone view behind the baseline. The far player is small. The domain gap is large.
- Dataset license: not stated on the paper page or the repo page. The paper is CC BY 4.0, which does not cover data. The videos come from YouTube. Treat the data as research-only. Not verified further.
- Annotation tool is in the repo (https://github.com/F3Set/F3Set). It could help us label shots.

### 3.3 Practical reading for our case

- Frame rate: these models spot an event at the exact frame. At 25-30 fps the tolerance is 1-2 frames (about 40-80 ms). For us, audio gives sub-frame time for hits. Use video only for "who hit" and "what type".
- Our 1100 labeled points have no hit labels. To train E2E-Spot/T-DEED style models we need new labels. Pseudo-labels from audio peaks could replace most manual work.
- E2E-Spot (BSD-3) is the only legally clean model here. T-DEED (GPL-3.0) and F3ED (no license) need re-implementation or permission.
- Compute: these models run 224 px clips with small backbones (RegNetY-200MF is tiny). A 100-minute match at 30 fps is 180,000 frames. This is feasible on an RTX 3060. On CPU it is slow but possible with a stride. The 4K decode is the main cost. A crop around the court at lower resolution is better than a full-frame resize because the far player is small.
- Bounce spotting needs the ball. Ball tracking is a different model family (TrackNetV3, WASB). See 3.4.

### 3.4 Ball tracking (related, for events and in/out)

| Work | License | Tennis result | Source |
|---|---|---|---|
| WASB-SBDT (BMVC 2023) | MIT (README, not fully verified) | Tennis: accuracy 89.0, AP 91.0, F1 94.0 (WASB, step 3) | https://github.com/nttcom/WASB-SBDT , https://arxiv.org/pdf/2311.05237 |
| TrackNetV3 | MIT per mirrors (not verified on the original repo) | RacketVision tennis: precision 0.904, recall 0.725 (single frame) | https://arxiv.org/pdf/2511.17045 |

- WASB beat TrackNetV3 on tennis in RacketVision (precision 0.937, recall 0.803 versus 0.904, 0.725).
- For a fixed rear camera at 4K, the ball is tiny. Ball tracking at full resolution needs a full decode. Treat it as phase 2.

---

## 4. Stroke type recognition

### 4.1 Methods

| Approach | Example | Input | Strokes | Accuracy reported | Source |
|---|---|---|---|---|---|
| Pose + LSTM | Int. J. ICT 2025 paper | MediaPipe keypoints | backhand, forehand, ready, serve | Not verified (number not seen) | https://journals.telkomuniversity.ac.id/ijoict/article/download/9622/3161/33946 |
| ST-GCN, cross-dataset | 2026 study (THETIS, Tennis-Mocap, 3DTennisDS) | 2D/3D skeleton | multiple | Not verified | via https://www.emergentmind.com/topics/thetis-dataset (secondary) |
| Skeleton transformer (badminton) | BST | Skeleton | badminton stroke types | Not verified | https://arxiv.org/html/2502.21085v3 |
| SlowFast on THETIS | Survey data | RGB | 12 classes | 74% generalization (secondary) | https://www.emergentmind.com/topics/thetis-dataset |
| F3ED | F3Set | RGB | forehand/backhand, serve/return/stroke, technique, direction | Element F1 75.2 (all elements) | https://arxiv.org/html/2504.08222v1 |
| MLLM benchmark | TennisTV | Video | 8 tasks, stroke to rally level | Best model about 56% on point winner | https://arxiv.org/abs/2509.15602 |
| TennisExpert | 2026 | Parser + Qwen3-VL-8B memory model | Scores, shot sequences, bounces, locations | No number on the abstract page | https://arxiv.org/abs/2603.13397 |

### 4.2 Datasets and licenses

| Dataset | Content | License | Source |
|---|---|---|---|
| THETIS (2013) | 1,980 videos (per a 2025 survey), 12 stroke classes, RGB, depth, 2D/3D skeleton, Kinect, indoor volunteers | Not found. Not verified. Treat as research-only. | https://openaccess.thecvf.com/content_cvpr_workshops_2013/W08/papers/Gourgari_THETIS_Three_Dimensional_2013_CVPR_paper.pdf |
| TenniSet (2017) | 5 Olympic full matches from YouTube, 6 event types with time intervals | Not found. YouTube source. Likely restricted (my inference). | https://www.emergentmind.com/topics/tennis-action-recognition (secondary) |
| F3Set tennis | 114 matches, 8 labels per shot | Not stated. YouTube source. | https://arxiv.org/html/2504.08222v1 |
| Tennis player actions pose dataset (Data in Brief 2024) | About 2,010 COCO-format images: forehand, backhand, ready, serve | Journal data article. License not checked. | https://pmc.ncbi.nlm.nih.gov/articles/PMC11282921/ |
| 3DTennisDS / Tennis-Mocap | Motion-capture strokes incl. volley, smash, kick serve | Not verified | https://yadda.icm.edu.pl/yadda/element/bwmeta1.element.baztech-05d82f2e-2615-4071-a4f4-5ce1d985065e/c/Skublewska-Paszkowska_Powroznik_Lukasik_Smolka_2024.pdf |
| TennisVL (TennisExpert) | 200+ pro matches, 471.9 h, 40k+ rally clips, commentary | Not stated for data. | https://arxiv.org/abs/2603.13397 |
| Roboflow Universe tennis sets | Detection sets, various licenses | Per set. Check each. | https://universe.roboflow.com/thestars/tennis-lx0lw |
| OpenTT extended (table tennis) | Shot type + point outcome | Not verified | https://arxiv.org/html/2512.19327v2 |

Rule: public tennis datasets are mostly research-only or have no clear license. Do not ship models trained on them without legal review. Use them only to pre-train or to compare. Our own labels are the clean source.

### 4.3 Recommendation

- A pose-sequence classifier is the cheapest route: crop of 1-1.5 s around each audio hit, keypoints of the near player (17 or 26 points), small transformer or GRU. It works on CPU.
- For the far player, pose is noisy because the person is small. Use a high-resolution crop of the far baseline area. Test this first (E5).
- For serve vs. rally shot, use the timing context (first hit of a point from a player behind the baseline = serve). This needs no model.
- Smash and volley: use player position (net zone) and arm height. Expect low accuracy with amateur data and few examples.
- Plan to label about 500-1000 shots in our matches. An annotation tool exists in F3Set (license unclear). A simple custom tool (audio hit + stroke label) is faster for us.

---

## 5. Pose estimation and person detection

### 5.1 Pose models

| Model | License (code) | Weights note | Speed (opened data) | Notes |
|---|---|---|---|---|
| RTMPose (t/s/m/l) | Apache-2.0 | Weights trained on datasets with their own terms (rtmlib issue #65 on whole-body weights). Check before shipping. | ORT CPU i7-11700, pose only, 256x192: t 3.2 ms, s 4.5 ms, m 11.1 ms, l 18.9 ms. TRT-FP16 GTX 1660 Ti: 1.1 / 1.4 / 2.3 / 3.5 ms. COCO AP: 68.5 / 72.2 / 75.8 / 76.5. Source: https://github.com/open-mmlab/mmpose/tree/main/projects/rtmpose | Top-down. Needs a person detector. |
| RTMPose + RTMDet-nano pipeline | Apache-2.0 | - | Detector every 5 frames: CPU 12.4 / 16.7 / 26.6 / 36.3 ms (t/s/m/l). | same page |
| RTMO | Apache-2.0 | - | Faster than RTMPose with more than 4 people. No numbers opened. | https://cdn05042023.gitlink.org.cn/OpenMMLab/mmpose/src/branch/main/projects/rtmo |
| RTMW (133 whole-body) | Apache-2.0 | Whole-body weights use COCO-WholeBody (non-commercial concern per rtmlib) | Not opened | https://github.com/Tau-J/rtmlib |
| rtmlib | Apache-2.0 | - | ONNX Runtime, OpenCV, OpenVINO, TensorRT backends. Devices cpu, cuda, mps. Modes performance/lightweight/balanced. | https://github.com/Tau-J/rtmlib (opened) |
| ViTPose (HF) | Apache-2.0 | COCO CC-BY-4.0 | Not opened | https://huggingface.co/usyd-community/vitpose-plus-small |
| Sapiens pose | CC BY-NC 4.0 on model cards | Non-commercial | Large models | https://huggingface.co/facebook/sapiens-pose-0.6b/blob/main/README.md . Do not use. |
| MediaPipe Pose | Apache-2.0 (not verified here) | - | Single-person, fast on CPU | No source opened. Not verified. Weak for small far people (no benchmark found). |
| RF-DETR Keypoint (preview) | Apache-2.0 | - | COCO AP 71.8, 9.7 ms on T4 (TensorRT FP16) | https://github.com/roboflow/rf-detr |
| YOLO pose (Ultralytics) | AGPL-3.0 | - | - | Not allowed for closed source. RallyClip uses it. |

### 5.2 Person detectors

| Model | License | COCO AP | Latency / size | Source |
|---|---|---|---|---|
| YOLOX-Nano / Tiny / S | Apache-2.0 | 25.8 / 32.8 / 40.5 | 0.91 / 5.06 / 9.0 M params. ONNX supported. | https://github.com/Megvii-BaseDetection/YOLOX (opened) |
| RF-DETR Nano / Small / Medium / Large | Apache-2.0 | 48.4 / 53.0 / 54.7 / 56.5 | T4 TensorRT FP16: 2.3 / 3.5 / 4.4 / 6.8 ms | https://github.com/roboflow/rf-detr (opened) |
| RF-DETR XL / 2XL / Atto / Femto / Pico | Roboflow PML 1.0 | 58.6 / 60.1 / 30.5 / 37.8 / 41.6 | Commercial self-hosting needs an Enterprise add-on | same page |
| D-FINE N / S / M / L / X | Apache-2.0 | 42.8 / 48.5 / 52.3 / 54.0 / 55.8 | 2.1 / 3.5 / 5.6 / 8.1 / 12.9 ms (T4, TRT FP16). ONNX export tool in repo. | https://github.com/Peterande/D-FINE (opened) |
| RT-DETRv2 | Apache-2.0 (secondary source) | about 53 (R50) | - | https://blog.roboflow.com/rf-detr-vs-alternatives/ |
| Ultralytics YOLO | AGPL-3.0 | - | - | Avoid. |

Notes:
- No source reported small-object AP for RF-DETR or D-FINE. Test on our footage.
- Latency numbers use a T4 with TensorRT. An RTX 3060 is in the same class (for planning only). Not verified on 3060.
- On Mac M1, use ONNX Runtime CoreML provider or OpenVINO-like CPU paths. rtmlib supports "mps" device. Real speed on M1: not verified.
- We need only 2-4 people per frame. Court-region cropping and tracking (detect every N frames) reduce cost a lot. RTMPose detector-every-5-frames design (above) follows this idea.
- Far player: crop the far half of the court at native 4K and run the pose model on the crop. Do not downscale the whole frame to 640 px.

---

## 6. Audio: hit detection

### 6.1 Prior work

| Work | Task | Method | Result | Source |
|---|---|---|---|---|
| TennisExpert (2026) | Racket-ball hit tagging in broadcast audio | Lightweight audio-only model to tag hundreds of hours | Not verified (number not seen; abstract page has no audio detail) | https://arxiv.org/pdf/2603.13397 |
| Padel multi-modal hit detection (CVPRW 2024) | Hit detection | Audio sound-event detection + positions | Numbers not verified | https://openaccess.thecvf.com/content/CVPR2024W/CVsports/papers/Decorte_Multi-Modal_Hit_Detection_and_Positional_Analysis_in_Padel_Competitions_CVPRW_2024_paper.pdf |
| Tennis audio-visual hit detection (Surrey) | Hit times | Vision gives coarse hits, audio refines, inter-hit timing used | About 30% absolute F-score gain for audio (women's doubles match) | https://openresearch.surrey.ac.uk/permalink/44SUR_INST/15d8lgh/alma99512922402346 (search summary; not opened) |
| Amazon A/V sync | Ball-hit audio detector and video detector for sync errors | Two networks | High precision needed | https://cdn.amazon.science/f2/d9/f482f9f6407694338db069ed4803/detection-of-audio-video-synchronization-errors-via-event-detection.pdf (not opened) |
| Tennis timing assessment (2025) | Racket and ground impact sound classification | ML acoustic system | Pilot study | https://pmc.ncbi.nlm.nih.gov/articles/PMC11843912 |
| Spin from sound | Spin type from impact sound | Classifier | Over 70% (secondary) | https://arxiv.org/html/2409.11760v2 |
| RallyClip (negative view) | - | Author rejected sound for multiple courts and quiet shots | - | https://iroblesrazzaq.github.io/RallyClip/ |

Common pattern (from the search summaries): a peak detector proposes candidates, then a small CNN classifies them. Fusion with video removes false positives.

### 6.2 Small audio encoders

| Model | Size | License | Notes | Source |
|---|---|---|---|---|
| EfficientAT (MobileNetV3-based) | mn04: 0.98 M params, 0.11 GMACs, AudioSet mAP 43.2. mn10: 4.9 M, 0.54 G, 47.1. dymn04: 1.97 M, 0.12 G, 45.0. | MIT (code, opened). Weight terms not stated. | Best fit for CPU. | https://github.com/fschmid56/EfficientAT |
| PANNs CNN14 | About 81 M params (secondary), AudioSet mAP 0.431 (16 kHz variant 0.438) | Code MIT. Weights on Zenodo, license not stated (opened). | Frame-wise SED variants exist (DecisionLevelMax, 0.385). | https://github.com/qiuqiangkong/audioset_tagging_cnn |
| BEATs | About 90 M (base) | License is in the repo LICENSE file. I could not read it. Not verified. (The unilm repo is commonly MIT. Not verified.) | 16 kHz input. | https://github.com/microsoft/unilm/tree/master/beats |
| AST | About 87 M (secondary) | Not verified | Heavy for CPU | https://codesota.com/audio/classification (secondary) |
| CLAP | About 86 M (secondary) | Not verified | Language-audio. Not needed. | same |
| YAMNet | Small (about 4 M, not verified) | Apache-2.0 (not verified) | No source opened. | not verified |

Recommendation: do not use a large encoder. A hit is a short click. Train a small CNN on 16 kHz log-mel patches (for example 64 ms window, 10 ms hop), or fine-tune EfficientAT mn04. Start with an energy/onset detector as a baseline.

### 6.3 Stereo and other courts

- No source I found tests stereo separation of near and far courts. This is an open question for us.
- Physical idea: a phone has 2 microphones close together (a few cm). Level difference and phase delay give little direction information. Direction may still work for sources far to the left or right, but the near court is in front and other courts are to the sides. Test with cross-correlation between channels (inter-channel time difference) and with level ratio.
- Practical features for a learned classifier: stereo log-mel (2 channels), channel difference, inter-channel delay estimate, loudness relative to the long-term floor. A hit on our court is louder and has sharper onset than a hit on a far court.
- Use video to confirm: a hit is valid when a player's racket-arm motion is near the time. This is the audio-visual fusion in the Surrey work.
- Label source: our 1100 points give the rally spans. Audio peaks inside spans are mostly our court. Peaks outside spans are other courts or noise. Use this as weak labels for a classifier (no extra manual work).

---

## 7. Video and image foundation models, small VLMs

### 7.1 Frozen feature extractors

| Model | Sizes | License | Evidence for temporal tasks | Source |
|---|---|---|---|---|
| DINOv3 | ViT-S/16 distilled 21 M, ViT-S+/16 29 M, ViT-B/16 86 M | DINOv3 License, gated. Allows modification and distribution, no ban on commercial use, but military/weapons exclusion, indemnity, redistribution only under the license, termination on suing Meta. | UCF Sports comparison: DINOv3 good for pose-identifiable actions, V-JEPA 2 steadier across action types (https://aitopics.org/doc/arxivorg:B1488E59) | https://github.com/facebookresearch/dinov3 , https://raw.githubusercontent.com/facebookresearch/dinov3/main/LICENSE.md (opened) |
| V-JEPA 2 | ViT-L 300 M, ViT-H 600 M, ViT-g 1 B. V-JEPA 2.1: ViT-B 80 M, L 300 M, g 1 B, G 2 B | MIT (most files, opened) | Frozen use for surgical phase segmentation with a 2-layer MLP (https://arxiv.org/pdf/2605.15618, search summary) | https://github.com/facebookresearch/vjepa2 |
| SigLIP 2 | Base 0.4 B on the card I opened (includes text tower) | Apache-2.0 | No sports evidence found | https://huggingface.co/google/siglip2-base-patch16-224 |
| DINOv2 | ViT-S/14 about 21 M | Apache-2.0 (not verified here) | Not verified | not opened |
| VideoMAE v2 | - | Not verified | Not verified | not opened |
| InternVideo | - | Not verified | Not verified | not opened |

Notes:
- For a temporal head at 1 fps, an image model (DINOv3 ViT-S or DINOv2 ViT-S) is enough. It runs on CPU. A 100-minute match has 6000 frames at 1 fps. At about 20 ms per frame (GPU) this is about 2 minutes. On CPU, expect minutes to tens of minutes (not measured).
- 1 fps frames come from keyframes. Keyframe-only decode is cheap but the frame positions are not uniform. Check keyframe spacing in our files.
- V-JEPA 2 takes clips of many frames (the repo names suggest 16-64 frames). Cost is much higher than image models. Not tested.
- DINOv3 license needs a read by a lawyer. V-JEPA 2 (MIT) and DINOv2 are cleaner.

### 7.2 Small local VLMs

| Model | Sizes | License | VRAM / memory | Video support | Sports evidence |
|---|---|---|---|---|---|
| Qwen3-VL | 2B, 4B, 8B (and 32B) | Apache-2.0 (8B card opened) | Community 4-bit estimates: 4B about 3.5 GB, 8B about 6 GB (weights only, secondary) | Yes. Timestamp alignment. | TennisExpert builds on Qwen3-VL-8B (https://arxiv.org/abs/2603.13397) |
| SmolVLM2 | 256M, 500M, 2.2B | Apache-2.0 (opened) | 5.2 GB GPU for video (2.2B, opened) | Yes. Video-MME 52.1 (2.2B) | None found |
| Gemma 3n | E2B, E4B | Gemma Terms (custom, prohibited-use policy, flow-down, remote restriction right) (opened) | Not verified | Audio, image, video input (secondary) | None found |
| Gemma 4 | 2.3B to 30.7B (reports) | Apache-2.0 (news, not the license text) | Not verified | Not verified | None found. https://opensource.googleblog.com/2026/03/gemma-4-expanding-the-gemmaverse-with-apache-20.html |

Evidence on tennis:
- TennisTV (17 MLLMs, 2,527 questions). Best point-winner accuracy: 56.3 (GPT-4o), per a search summary. https://arxiv.org/pdf/2509.15602
- TactiPlay (badminton, closed VLM): winner accuracy 68% on correctly segmented rallies. https://arxiv.org/html/2607.27125v1
- A 6 GB card limits us to 4-bit 4B-8B models or smaller. Prompting a small VLM for "who won" will likely be near chance. Do not rely on it.
- Better use of a VLM: scoreboard-free tasks such as "is this a serve?" are also unproven. A trained head on frozen features is more reliable and much cheaper.

---

## 8. Automatic score keeping and winner inference

### 8.1 Published work

| Work | What it does | Source |
|---|---|---|
| SmartTennisTV (2018) | Reads broadcast scoreboards and indexes by score. Not usable for us (no scoreboard). | https://arxiv.org/pdf/1801.01430 |
| Badminton structured analysis (2017) | Infers point winner from the rule "winner serves next", using player positions and strokes | https://arxiv.org/pdf/1712.08714 |
| TennisTV benchmark (2025) | MLLM point-winner task, about 56% best | https://arxiv.org/pdf/2509.15602 |
| TennisExpert (2026) | Video parser extracts score, shot sequence, bounces. Details not verified. | https://arxiv.org/abs/2603.13397 |
| Folio3 (vendor) | Claims winner/error labels from ball tracking and action. Marketing only. | https://www.folio3.ai/ai-in-sports/video-analysis/tennis |
| SwingVision | Rival. Not researched here (no source opened). | - |

I found no paper on HMM or grammar decoding of the full tennis score from video. This is an opportunity.

### 8.2 Rules that constrain the score (tennis rules, no source opened; standard)

- Within a game, one player serves. The server alternates the serve side: first point from the deuce (right) side, second from the ad (left) side, and so on.
- After a game ends, the server changes. After the 1st, 3rd, 5th... game of a set, players change ends (also after a set if the game count is odd).
- In a tiebreak, the first server serves 1 point. Then each player serves 2 points, with the server side starting on the ad side for the 2nd-3rd point. Players change ends every 6 points.
- Doubles: server order is fixed within a set. The serving team alternates by game.
- A first-serve fault is followed by a second serve from the same side by the same server. A point is not complete until the second serve is in or a rally ends.
- A "let" repeats the serve.

### 8.3 How to use the rules (proposal)

State = (set score, game score, point score, server, tiebreak flag, serve side). Each point step adds one observation:
- serve side (deuce/ad) from the server's position: left or right of the center mark.
- server identity from the near/far side and the player's position.
- winner probability from a visual model or from the next-serve cues.

Decoding:
1. Run a Viterbi pass over the whole match. The transition model is the tennis scoring grammar. The emission model uses the noisy observations.
2. Serve side is a strong parity check. The expected side follows from the point count in the game. If the observed side disagrees, a point is missing or extra. This detects missed or false point detections, such as a missed second serve.
3. Server changes tell the game boundary. Per game, the winner of the game is shown by the next server (the old receiver serves).
4. Sum of constraints: each game ends after 4+ points with a 2-point margin. A decoder can pick the most likely winner sequence when the visual winner model has 70-80% accuracy.

Value for us: the 1100 marks with winners let us measure how often the Viterbi decode fixes per-point errors. Run it offline first. No video model needed for the test (use marks with synthetic noise).

### 8.4 Winner cues from video and audio (candidate signals)

- Who serves the next point (game-level winner only at game end).
- Last-hit player and ball-out or net events (needs ball tracking).
- Player behavior after the point: celebration, racket drop, walk to the fence. Unproven. A pose-sequence classifier on the 3 s after the point end could be tested with our labels.
- Rally parity: the player who made the last hit wins on a winner, loses on an error. Last hitter + outcome class (winner vs. error) predicts the winner. F3Set has the outcome label (in, winner, forced error, unforced error), so this idea is supported in the label design. https://arxiv.org/html/2504.08222v1
- A direct classifier "did the near player win?" on frozen features of the last 3-5 seconds is the simplest baseline. Test it before building ball-based logic.

---

## 9. Faulty first serve detection

### 9.1 Literature

- I found no work that detects tennis first-serve faults from video as a rule-based call. The search found only table tennis and badminton serve-rule work (height/toss rules). Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC6316619 (table tennis), https://mdpi.com/1424-8220/23/24/9759 (badminton, search summary).
- A thesis on broadcast tennis event detection discusses faults and double faults. I saw only a table of contents. Not verified. https://norma.ncirl.ie/6618/1/akshaymenon.pdf
- F3Set labels serves with direction (T, body, wide) and outcome, but the repo page does not describe a "fault" label. Not verified.

### 9.2 Signals that make it possible

| Signal | How it helps | Needs |
|---|---|---|
| Short point of 1 audio hit (the serve), no return | First serve fault or ace. Ace has a winner mark for the server. | Audio hit count |
| Two serve-like events in a row from the same player, same side, with a gap of 5-25 s and no rally | Fault, then second serve | Serve detector (toss + hit), player identity |
| Second serve has lower speed and a different motion | Weak signal | Pose |
| Serve side is the same in two consecutive "points" | Strongest sequence cue. A new point changes the side. | Player position at serve |
| Our point marks: a fault shows as a very short segment. The next segment starts soon after. | Direct labels if we mark faults, or we infer them | Marks |
| Ball bounce location and net contact | Direct evidence of the fault type | Ball tracking at 4K. Hard. |
| Short hit sound followed by silence, then the toss/hit again | Audio-only version | Audio |

### 9.3 Proposal

- Treat "fault then second serve" as one point with two serve attempts. Detect serve attempts: a serve = one audio hit with the server in the serve pose at the baseline before any return.
- Count attempts per point. Two attempts means a first-serve fault. A double fault means the point ends with no rally after the second serve.
- Ground truth: our marks need a "first serve fault" flag per point, or we derive it from pairs of short segments. If the marks have no flag, add it for 1-2 matches and test the rule.
- Edge cases: let serves (replayed first serve; rule 8.2), lets in a rally, a fault called out by the opponent, and a player who stops a rally early. These look like faults. Expect some errors.
- The "same side again" cue (9.2) and the score grammar (8.3) make a good consistency check: a serve side that does not alternate between consecutive points indicates a fault.

---

## 10. Open questions

1. Can stereo audio from a phone separate our court from neighbor courts? Test inter-channel delay and level on our matches (6.3).
2. Do we have per-fault flags in the 1100 marks? If not, how many matches need relabeling?
3. What is the keyframe spacing in our HEVC files? 1 fps keyframes may not be exactly 1 s apart.
4. How well does RTMPose work on the far player (small, 4K crop)? Need a test on 20-50 frames.
5. License of the F3Set data and code, and of the T-DEED weights. Ask the authors if we want to use them.
6. DINOv3 License: confirm with a lawyer that bundling the weights in a commercial app is allowed. Or use DINOv2/V-JEPA 2.
7. RTMPose weight terms: some weights come from datasets with non-commercial terms (rtmlib issue #65). Which exact weights are clean for the 17-keypoint body model?
8. Speed on MacBook Air M1: no measurements found for RTMPose, RF-DETR or EfficientAT. Test with ONNX Runtime CoreML.
9. Is a per-shot label set worth building? Audio pseudo-labels plus a short manual pass could give training data for stroke type and hit spotting.
10. Doubles: pose models see 4 players. Tracking identity through the rally is needed for server and winner logic.
11. Pretrained tennis weights for E2E-Spot or F3ED may exist. I did not confirm that tennis weights are in the E2E-Spot models repo (https://github.com/jhong93/e2e-spot-models/).
12. Accuracy of audio-hit detection in indoor halls: no public number for amateur halls.

---

## 11. Sources

Event spotting and datasets
- F3Set paper (ICLR 2025): https://arxiv.org/abs/2504.08222 , full text https://arxiv.org/html/2504.08222v1 (opened)
- F3Set code: https://github.com/F3Set/F3Set (opened)
- T-DEED paper: https://arxiv.org/abs/2404.05392 (search result only)
- T-DEED code: https://github.com/arturxe2/T-DEED (opened)
- E2E-Spot code: https://github.com/jhong93/spot (opened); models: https://github.com/jhong93/e2e-spot-models/
- ASTRA: https://arxiv.org/abs/2404.01891 ; ASPERA: https://scitepress.org/PublishedPapers/2025/133007
- SoccerNet 2025 results: https://arxiv.org/abs/2508.19182
- UMEG-Net: https://arxiv.org/abs/2511.14186 (only metadata read)
- Skeletons to Pixels: https://arxiv.org/pdf/2604.22839
- Sports video event detection survey: https://arxiv.org/pdf/2505.03991
- Ball tracking: https://github.com/nttcom/WASB-SBDT , https://arxiv.org/pdf/2311.05237 , https://arxiv.org/pdf/2511.17045

Rally segmentation
- RallyClip: https://iroblesrazzaq.github.io/RallyClip/ (opened)
- TactiPlay: https://arxiv.org/html/2607.27125v1 (opened)
- SmartTennisTV: https://arxiv.org/pdf/1801.01430
- Unstructured sports video: https://arxiv.org/pdf/2002.08097
- Badminton real time: https://arxiv.org/pdf/2308.12199 ; structured analysis: https://arxiv.org/pdf/1712.08714

Stroke recognition
- THETIS: https://openaccess.thecvf.com/content_cvpr_workshops_2013/W08/papers/Gourgari_THETIS_Three_Dimensional_2013_CVPR_paper.pdf
- Tennis actions pose dataset: https://pmc.ncbi.nlm.nih.gov/articles/PMC11282921/
- Tennis action classification: https://arxiv.org/pdf/2402.02545
- BST badminton: https://arxiv.org/html/2502.21085v3
- Survey of sports action recognition: https://arxiv.org/pdf/2206.01038
- TennisTV: https://arxiv.org/abs/2509.15602
- TennisExpert: https://arxiv.org/abs/2603.13397

Pose and detection
- RTMPose benchmark page: https://github.com/open-mmlab/mmpose/tree/main/projects/rtmpose (opened)
- RTMPose paper: https://arxiv.org/pdf/2303.07399
- RTMO: https://cdn05042023.gitlink.org.cn/OpenMMLab/mmpose/src/branch/main/projects/rtmo
- rtmlib: https://github.com/Tau-J/rtmlib (opened)
- YOLOX: https://github.com/Megvii-BaseDetection/YOLOX (opened)
- RF-DETR: https://github.com/roboflow/rf-detr (opened)
- D-FINE: https://github.com/Peterande/D-FINE (opened)
- ViTPose card: https://huggingface.co/usyd-community/vitpose-plus-small
- Sapiens card: https://huggingface.co/facebook/sapiens-pose-0.6b/blob/main/README.md ; paper https://arxiv.org/abs/2408.12569

Audio
- Padel multi-modal: https://openaccess.thecvf.com/content/CVPR2024W/CVsports/papers/Decorte_Multi-Modal_Hit_Detection_and_Positional_Analysis_in_Padel_Competitions_CVPRW_2024_paper.pdf
- Tennis timing acoustic: https://pmc.ncbi.nlm.nih.gov/articles/PMC11843912
- Spin from sound: https://arxiv.org/html/2409.11760v2
- EfficientAT: https://github.com/fschmid56/EfficientAT (opened)
- PANNs: https://github.com/qiuqiangkong/audioset_tagging_cnn (opened)
- BEATs: https://github.com/microsoft/unilm/tree/master/beats (opened; license text not read)
- BEATs paper: https://arxiv.org/pdf/2212.09058

Foundation models and VLMs
- DINOv3: https://github.com/facebookresearch/dinov3 ; license https://raw.githubusercontent.com/facebookresearch/dinov3/main/LICENSE.md (opened)
- V-JEPA 2: https://github.com/facebookresearch/vjepa2 (opened)
- DINOv3 vs V-JEPA 2: https://aitopics.org/doc/arxivorg:B1488E59
- SigLIP 2: https://huggingface.co/google/siglip2-base-patch16-224 (opened)
- Qwen3-VL-8B: https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct (opened)
- SmolVLM2: https://huggingface.co/HuggingFaceTB/SmolVLM2-2.2B-Instruct (opened)
- Gemma terms: https://ai.google.dev/gemma/terms (opened); Gemma 4 license: https://opensource.googleblog.com/2026/03/gemma-4-expanding-the-gemmaverse-with-apache-20.html (not opened; news only)
- Local VLM overview (secondary): https://tinyweights.dev/posts/best-local-vision-language-models-2026/

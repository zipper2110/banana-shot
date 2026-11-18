# Automatic tennis analysis: rival products, open-source projects, and local runtimes

Date: 2026-11-16

Scope: research for a local (desktop) analysis pipeline.
Input: one fixed phone camera behind the baseline, 4K 60 fps HEVC, 90 to 120 minutes.
Targets: Windows laptop with RTX 3060 (6 GB), MacBook Air M1, and CPU-only machines.

Rules for this report:
- "Verified" means the source page opened and showed the fact.
- "Not verified" means the page did not open, or it did not show the fact.
- Company claims are marked as claims. No independent test was found for any of them.
- Star counts and dates come from the GitHub API on 2026-11-16.

---

## Summary and recommendations

### What the products tell us

1. The only primary technical source for SwingVision is the ITF approval report (Oct 2025).
   It says: iPhone rear camera, 60 fps, "a series of machine learning models" for ball, court, and players.
   It lists the outputs: 3D ball position at hit, at net contact, and at bounce; shot type (forehand, backhand, serve); spin (topspin, slice); ball speed.
   SwingVision does not publish model names, input resolution, or on-device timing.
2. SwingVision stores one 3D ball event list. Everything else (speed, landing position, line calls, shot type) comes from that list. Our pipeline should use the same idea: one event list, many views.
3. SwingVision auto-scoring is not perfect. One App Store reviewer reports 10+ wrong points per match. SwingVision lets the user edit each point. In 2023 the CEO described a beta that asks for the final score and then infers the points. We should plan for: automatic score proposal + a fast edit tool + a "final score" constraint.
4. Cheap rally-cutting products (Rallytics, TennisCut, PerfectSwing, OverRally) do not track the ball. They cut rallies from a low-resolution proxy. PerfectSwing claims F1 0.84 against a human rater F1 of 0.88 (claim, internal test). TennisCut users report cuts that start too early and end 1 to 2 seconds early.
5. Rallytics runs on Windows and Mac, needs no camera geometry, and says analysis takes "a few minutes per hour". It also says a small proxy copy "leaves" the machine. So its analysis is not fully local (unclear wording).
6. No product that we could open publishes first-serve fault detection, rally winner logic, or the landing-position diagram for amateur single-camera video as a local desktop feature. SwingVision (iPhone only) is the one product that does most of it.

### What the open-source projects tell us

1. No open-source project does the full chain for amateur 4K video.
   - RallyClip (AGPL) cuts points only. It uses court detection + YOLOv8-pose + BiLSTM.
   - tennis_analysis, TennisProject, and ArtLabss tennis-tracking are broadcast-video demos with no license or an unrestricted one, and old code (last push 2024 or earlier).
   - philippdubach/tennis-vision (MIT, 11 stars) is the closest full chain: ball (TrackNetV2) + bounces (CatBoost) + pose + F3ED shot model + OCR score. It reads the broadcast scoreboard. Amateur video has no scoreboard. So its score step does not transfer.
2. The proven small building blocks are:
   - Ball: heatmap networks of the TrackNet family (TrackNetV3 is MIT per its README; WASB-SBDT is MIT and covers tennis).
   - Bounce: a gradient-boosted classifier on the ball path (CatBoost or similar) plus court homography.
   - Court: keypoint network (14 points) or classical lines.
   - Player: pose model (RTMPose is Apache-2.0 through MMPose) or any detector.
   - Rally: sequence model on player pose and motion (RallyClip), or rule-based gaps on ball visibility (tennis-vision).
3. The first-serve fault trick in tennis-vision is useful and cheap: a single-shot rally with no score change means first-serve fault. It needs a score signal. Without a scoreboard, we must get the score signal from our own point logic.

### Licensing warnings (commercial closed-source app)

| Item | License | Verdict |
|---|---|---|
| Ultralytics YOLO (code and trained models) | AGPL-3.0, or paid Enterprise license | Problem. Fine-tuned models also fall under AGPL by default. We need the Enterprise license or we must avoid it. |
| RallyClip | AGPL-3.0 (GitHub API). Project site says GPL-3.0. | Problem. Do not copy code. Ideas only. |
| tennis_analysis (abdullahtarek) | None stated | No right to reuse. Ideas only. Uses YOLO. |
| TennisProject (yastrebksv) | None stated | No right to reuse. |
| tennis-tracking (ArtLabss) | Unlicense (public domain) | Code is free to use. It needs YOLOv3 and TrackNet weights; weight terms are not verified. |
| TrackNetV3 | README says MIT for code and checkpoints; GitHub API shows "NOASSERTION" | Likely usable. Confirm the LICENSE file. Weights are badminton-trained. |
| WASB-SBDT | MIT (code) | Code usable. Weight and dataset terms not verified. |
| philippdubach/tennis-vision | MIT; upstream phase-1 code has no license | Own code usable. Phase 1 is not redistributable. |
| F3Set / F3ED | No license in GitHub API | No right to reuse. |
| RallyOS | MIT (README); GitHub API shows none | New, 1 star. Not mature. |
| MMPose (RTMPose) | Apache-2.0 | Usable. Check each weight file license. |
| RF-DETR | Apache-2.0 | Usable detector alternative. |
| ONNX Runtime | MIT (not verified in this run; widely known) | Usable. |
| ncnn | BSD-3-Clause | Usable. |
| llama.cpp, MLX | MIT | Usable. |

### Recommended pipeline design (what the products point to)

1. Stage A: cheap whole-video pass. Decode at low resolution, 10 to 15 fps. Find court, players, and "in point" time spans. Use player pose/motion + court mask. This is the RallyClip and Rallytics approach.
2. Stage B: precise pass only inside the rally spans. Decode at 60 fps, crop around the court, run a heatmap ball tracker. Extract bounces, hits, net contacts.
3. Stage C: logic on the event list. Serve detection, first-serve fault (no bounce in the service box), double bounce, out, net, winner, score. Show the result with confidence. Let the user fix points fast.
4. Use the final score as an optional constraint (SwingVision beta did this).
5. Keep one event list as the single data source for all views (shot type, speed, landing diagram).

Why two stages: 2 hours at 60 fps is 432,000 frames. Running a ball model on all frames costs hours on a laptop. Rally spans are a fraction of the match (the fraction is not verified; estimate it from our own sample videos).

### Runtime recommendation (short)

1. Primary: ONNX Runtime with the Java API. Verified: the Maven jar `onnxruntime:1.32.0` is 55.7 MB. The CUDA jar `onnxruntime_gpu:1.32.0` is 399 MB. The CUDA jar does not remove the need for the CUDA and cuDNN runtime libraries (these libraries are separate; not verified in this run).
2. Windows without NVIDIA GPU: DirectML is in "sustained engineering". Windows ML is the new path. Windows ML lists C#, C++/WinRT, C, and Python. It does not list Java. So a JVM app cannot use Windows ML directly (via JNI or a helper process it can; not verified).
3. Apple Silicon: ONNX Runtime CoreML provider. The Java API has `addCoreML()`. The CoreML page says official macOS arm64 Java packaging passes `--use_coreml`. Test this first on the M1.
4. A bundled sidecar process (native or Python) is the fallback. It avoids Java binding gaps. It costs installer size.
5. Do not use a vision-language model for per-frame work. Use it only for rare, small tasks, if at all.

### Biggest surprises

1. The ITF report shows SwingVision records at 60 fps with no stated 4K use. A reviewer reports that SwingVision limits video to HD even on 4K phones. The Pro bundle in 2022 listed "60fps, 1080p". So the market leader works at 1080p60. Our 4K input has more pixels than SwingVision uses (reviewer claim; not confirmed by the company).
2. RallyClip speed: nano model needs about 0.8x video length on an M2 GPU and about 3x video length on CPU (author figures). That is slow for 2-hour matches on CPU-only machines.
3. RallyClip's own author says the model learned that serve and return motion marks "in point". A dedicated rally model is still planned. So even the best open rally cutter has no ball logic.
4. The only open project that detects first-serve faults (tennis-vision) needs a broadcast scoreboard.
5. A 2025 audit in tennis-vision found that F3ED labels 73% of 11 single-shot serve rallies wrongly (small sample, author hypothesis). F3ED has no class for ace, double fault, or first-serve fault.
6. Ultralytics treats any fine-tuned model as AGPL by default. Most GitHub tennis demos use YOLO.
7. Windows ML "includes" only CPU and DirectML (legacy) in the box. Vendor GPU providers (NVIDIA TensorRT RTX, AMD, Intel OpenVINO) download on demand and need Windows 11 24H2 or newer.

---

## 1. SwingVision

Company: SwingVision, Inc. Founded 2019 by Swupnil Sahai (CEO, ex-Tesla Autopilot) and Richard Hsu (ex-Cisco, LinkedIn).
Source: https://pulse2.com/swingvision-6-million-funding/ (verified through search snippet).

### 1.1 Technical facts

| Topic | Fact | Source and status |
|---|---|---|
| Capture | iPhone or iPad rear camera, 60 fps. One or two devices at the back of the court. | ITF approval report PAT-25-037, https://www.world.tennis/media/15294/pat-25-037-approval-report-swingvision.pdf (verified, read in full) |
| Models | "A series of machine learning models" output tracking for ball, court, and players. No names given. | Same report (verified) |
| Outputs | 3D ball location at racket hit, net contact, and bounce. Shot type (forehand, backhand, serve). Spin (topspin, slice). Ball speed per trajectory. | Same report (verified) |
| Data flow | Tracking data goes to a backend server by WiFi to be logged. Ball trajectory data goes to the on-court tablet for line calls. Cellular can replace WiFi. | Same report (verified) |
| Line call UI | The last four shots can be challenged. Three wrong challenges per set by default. | Same report (verified) |
| Report limit | ITF states that approval does not establish accuracy of the data. | Same report (verified) |
| Frame rate need | Sahai: at lower than 60 fps the bounce is not visible. About 2 million pixels at 60 times a second (1080p). Models had to be made "lean". The app is "basically not possible without Neural Engine". | 2023 interview as quoted in search result; primary page not opened (not verified). Search result: https://athletechnews.com/?p=91620 |
| Founder statement | One iPhone replaces 10-camera Hawk-Eye or 8-camera PlaySight setups. 60 fps versus about 30 fps for human vision. | Podcast transcript, https://www.buzzsprout.com/1045900/13734760/transcript (verified) |
| Core ML | Models are trained on a computer, then converted to Core ML for iPhone. A conversion bug was fixed with a one-line Python change. | Apple developer story, https://apps.apple.com/no/story/id1565190205 (verified) |
| Resolution | Pro bundle in 2022: 30 hours per month of "60fps, 1080p". A 2025 App Store reviewer says video is limited to HD on 4K phones. Free tier records HD. | https://tennisnerd.net/tennis-tools/swingvision-update-9-5-7/27206 (verified); https://apps.apple.com/us/app/swingvision-tennis-pickleball/id989461317 (verified, reviewer claim) |
| Processing time | The app shows an estimated time remaining on the processing screen (v11.9.69). No numbers published. | App Store page (verified) |
| App size, OS | 768.9 MB. iOS 18+ on A12 Bionic or newer. Also macOS 15, watchOS 10, visionOS 2. | App Store page (verified) |
| Patent | CEO says single-camera tracking is patented. 2023 article says "patent-pending". No patent number found. | https://pickleballunion.com/?p=14029 (search snippet only; not verified) |
| Smart Court | Fixed install: 1 camera, 1 kiosk, 2 power cables. "High-frame-rate cameras". Two-camera option planned for 2025. Indoor possible. | https://www.tennisnerd.net/news/swingvision-smart-court-complete-ai-system-for-tennis/45328 (verified) |

### 1.2 Camera placement rules

- The ITF report says: devices mounted at the back of the court with "full view of the court".
- A review says SwingVision recommends a top-of-the-fence mount, and a camera angle that shows both baselines and alleys. Source: https://liveblogging-dapi.tennis.com/baseline/articles/swingvision-delivers-pro-level-insights-for-recreational-players (search snippet; not verified).
- The company sells the "Swing Stick" mount. It extends about 2 feet above the fence and has a sunshade. Source: Buzzsprout transcript above (verified).
- The official help-center article on placement did not open. Doubles and indoor rules are not verified.
- A second phone, one for each end of the court, is optional for the best accuracy (transcript, verified).

### 1.3 Accuracy claims

| Claim | Value | Status |
|---|---|---|
| Line calls within 10 cm of a line | 97% correct (human eye about 90%) | Company claim reported by https://www.tennis.com/news/articles/fair-play-swingvision-puts-its-electronic-line-calling-to-the-test-in-tournament-play (verified as a report; no independent test) |
| Line calls (other source) | "97-99%" | https://www.tennisnerd.net/?p=43158 (search snippet; not verified) |
| Shot type, speed, spin, landing | No number published. | Not found |
| Auto-score | No number published. One reviewer: 1 to 2 games wrong and 10+ points wrong per match over 20+ sets. | App Store reviews (anecdote) |

### 1.4 Score keeping

- Automatic scoring exists in the Pro tier.
- The user can edit who won a point and how ("Edit Point", "Point Details"). Source: App Store page (verified).
- Reviewers report wrong awards after a called-out second serve (App Store page, verified).
- 2023 podcast: a beta asks for the final score after the match and works out each point from it. It then builds stats such as break-point conversion. Source: https://www.buzzsprout.com/1045900/13802829/transcript (verified).
- The CEO states the goal is a live "AI umpire". It depends on reliable line calls (same transcript).

### 1.5 Known limits and user complaints

| Limit or complaint | Source |
|---|---|
| Wrong auto-scores (10+ points per match, one reviewer) | App Store page |
| Sessions stuck on the processing screen; recordings stopped early (fixed in later updates) | App Store page |
| Instability and recording failures are the top complaint in an aggregator summary | https://marlvel.ai/apps/com-mangolytics-swing/reviews (search snippet; not verified) |
| Single camera "sometimes can't make a call" in line-call mode | tennis.com article above |
| Smart Court is not ITF-certified for line calling (status in 2024 article). The 2025 ITF report is a PAT approval, not an accuracy certificate. | tennis.com article; ITF report |
| iOS only for recording. Footage from other cameras can be imported (one review). | https://www.tennisnerd.net/?p=25702 (verified) |
| Pickleball: spin labels often "flat"; player identification by shirt color often wrong | App Store page |
| Subscription price: Plus $14.99 per month, Pro $24.99 per month, Max $39.99 per month (listed) | App Store page |

### 1.6 What we can take from SwingVision

- 60 fps is the minimum for bounce detection. Our 4K 60 fps input meets this.
- Use a single 3D event list as the data model.
- Plan score editing as a first-class feature.
- The market leader needs a phone with a neural engine. A local desktop pipeline must be much cheaper per frame, or it must skip frames.

---

## 2. Other products with automatic analysis

| Product | What it does | Local or cloud | Hardware | Claims | Status |
|---|---|---|---|---|---|
| Rallytics (macOS arm64, Windows via Microsoft Store) | Finds rally start and end. Timeline editor. Local export. | Mixed: full file stays local. A low-resolution copy is made locally; the page says "what leaves is a description of your video". Exact data sent is unclear. | No minimum specs listed. | "A few minutes per hour of footage". 2 h free trial. Packs $4.99 to $89.99. Does not catch every point; no accuracy number. | https://rallytics-app-landing.pages.dev/ (verified) |
| TennisCut (iPhone, by Arc Labs) | Rally trim, ball trajectory overlay, live scoring, shot type and spin and speed (v2.1.1). | Says all processing on the phone. Version 2.1.0 added accounts and cloud storage. Privacy label lists linked data. Conflicting. | iOS 17+. 81 MB app. | Detection tuned for "stable, elevated phone footage behind the baseline". Users report cuts that start early and end 1 to 2 s short. 4.2 stars from 14 ratings. | https://apps.apple.com/app/id6754323620 (verified) |
| PerfectSwing (iOS, Android, web) | Cuts a match into point clips. Stats are on the roadmap. | Not stated. | Runs on budget phones such as Galaxy A13. | F1 0.84 for rally detection, human rater F1 0.88 (internal test). 1 hour yields 80 to 120 clips. Best camera: behind the baseline, 8 to 10 feet high. | https://perfectswing-web.pages.dev/faq (verified) |
| OverRally (iPhone) | Cuts rallies; zoomed clips that follow the user after one tap. About 10 minutes processing (listing). | Not stated. | Not stated. | Few users. v1.2.3 on 2026-08-04. | https://www.producthunt.com/products/overrally (search snippet; not verified) |
| SportAI Open (web) | Technique analysis for tennis, padel, pickleball (beta). No tactical stats yet. | Not stated; web app. | None. | No accuracy numbers. Uses uploaded videos to train models. | https://sportai.com/faq (verified) |
| Baseline Vision (Israel) | Net-post camera, 60 fps, in/out calls on built-in speaker. About $2,000 per unit (CB Insights). ATP Challenger data deal. | Edge device | Own hardware | Claims only. | https://www.jpost.com/business-and-innovation/article-840925 (search snippet; not verified) |
| PB Vision (pickleball) | Upload stationary-camera video. Tracks players, ball, court. Shot database and per-player reports. | Cloud (upload) | None | No published accuracy. Camera at least 4 ft high. | https://elderresearch.com/resource/podcasts/computer-vision-and-pickleball-game-changing-ai-insights-mining-your-own-business-podcast (search snippet; not verified) |
| Belmont University student project "See Every Rally" | Dashboard of court coverage and rally trends. Uses DINOv2 and YOLO. | Not stated | Smartphone footage | No accuracy numbers. | https://repository.belmont.edu/spark_presentations/1097 (verified) |
| Vidio.ai | Browser tool for highlight videos with "smart rally detection". | Cloud | None | Not verified. | https://www.vidio.ai/ (search snippet; not verified) |

Products that I could not find or open: MatchCut Pro, RallyFlow, 40Love.ai, Covelant, PlaySight, Wingfield, Zenniz.
The searches returned nothing for MatchCut Pro, RallyFlow, 40Love.ai, and Covelant. Treat the existence of a "local AI rally detection beta" in MatchCut Pro as not verified.
PlaySight and Wingfield are fixed multi-camera court systems. Only the SwingVision founder's "about eight cameras" statement for PlaySight was verified (Buzzsprout transcript).

### 2.1 Lessons from the rival products

- Rally cutting works from a small proxy video. It needs no camera calibration (Rallytics, PerfectSwing).
- A tuned angle matters. TennisCut's developer says detection fails on YouTube downloads (compression, camera movement, low angle).
- Edit tools are a must. Rallytics has split, join, and nudge, and shows gaps sorted by length so the user can add missed rallies.
- Cut padding matters: users complain about points that start too early or end too early. Use asymmetric padding, and let the user set it.
- No competitor except SwingVision shows ball speed, shot type, and landing position from a single camera. TennisCut added speed and shot type in November 2026 (v2.1.1, claim; no accuracy).

---

## 3. Open-source end-to-end projects

Data from GitHub API on 2026-11-16 unless noted.

### 3.1 Comparison table

| Project | URL | Stars | Last push | Code license | Weights license | Features that work (per README) | Models | How it finds rallies |
|---|---|---|---|---|---|---|---|---|
| RallyClip (iroblesrazzaq) | https://github.com/iroblesrazzaq/RallyClip | 45 | 2026-11-15 (created 2025-11-24; 342 commits) | AGPL-3.0 (API). Site says GPL-3.0: conflict. | Not stated. YOLO weights download on first run (Ultralytics AGPL). | Removes dead time. Segmented video, point clips, highlights, CSV. macOS app (Apple Silicon, macOS 14+). Windows and Linux via uv (cpu or gpu extra). | HRNet court heatmaps (5 heatmaps), YOLOv8-pose nano or small, BiLSTM | Per-frame "in point" probability from pose features in 20 s chunks, then smoothing and hysteresis. |
| tennis_analysis (abdullahtarek) | https://github.com/abdullahtarek/tennis_analysis | 912 | 2024-08-12 | None | Weights on Google Drive. Terms unknown. | Player speed, ball shot speed, shot count on broadcast video. | YOLOv8 (players), fine-tuned YOLO (ball), CNN court keypoints | No rally logic. Counts shots from ball positions. |
| TennisProject (yastrebksv) | https://github.com/yastrebksv/TennisProject | 243 | 2024-08-26 | None | Drive links; terms unknown | Ball detection, bounce detection, court detection. 1280x720 input. | TrackNet, CatBoostRegressor, 14-keypoint court network | None |
| tennis-tracking (ArtLabss) | https://github.com/ArtLabss/tennis-tracking | 724 | 2024-02-14 | Unlicense | Not stated; YOLOv3 weights downloaded separately | Ball, court lines, players, mini-map, bounce (README: 83% true-positive, 98% true-negative). | TrackNet, YOLOv3 (README also says ResNet50: inconsistent), sktime time-series forest for bounce | None |
| tennis-vision (philippdubach) | https://github.com/philippdubach/tennis-vision | 11 | not fetched | MIT (phase 1 is a patched fork of an unlicensed project) | Not stated | Overlay video; shots, rallies, scoreboard, game JSON. Aces, double faults, first-serve faults via score delta. | TrackNetV2, YOLOv8x, CatBoost, RTMPose-m (ONNX), F3ED, EasyOCR | Gap-based segmenter. Winner from bounce projection. |
| open-tennis (StanlyHardy) | https://github.com/StanlyHardy/open-tennis | not fetched | not fetched | Not stated | Not stated | Score detector (YOLOv5 + CRNN or Tesseract), court edges, server. Linux, CUDA 10. Experimental. | YOLOv5, CRNN, ONNX Runtime/TensorRT | None |
| Tennis-Analysis-System (Sh-31) | https://www.github.com/Sh-31/Tennis-Analysis-System | 1 | 2024-08-06 | None | n/a | Detection and visualization (YOLOv8x, YOLOv5, ResNet50 keypoints) | as listed | None |
| F3Set / F3ED | https://github.com/F3Set/F3Set | 22 | 2024-10-02 | None | n/a | NeurIPS 2024 fine-grained shot detector | Video model | Shot events, not rallies |
| RallyOS (nakuldagade) | https://github.com/nakuldagade/RallyOS | 1 | 2026-11-05 (created 2026-10-15) | MIT (README); API shows none | Uses YOLO11n-pose weights (AGPL) | Hackathon project, six racket sports. Court fingerprinting, self-calibration, pose, ball tracking with drag model, audio-visual hit detection, line calls. Self-reported F1: ball 0.82, hits 0.74. Only badminton and pickleball clips are real. | YOLO11n-pose, optional TrackNetV3 plugin | Not described |
| TrackNetV3 (qaz812345) | https://github.com/qaz812345/TrackNetV3 | 333 | 2026-09-03 | README: MIT, commercial use allowed. API: NOASSERTION. | README says MIT covers checkpoints. Trained on badminton shuttlecock data. | Shuttle tracking. Reported 97.51% accuracy, 25 FPS (hardware not stated). | TrackNetV3 + InpaintNet | n/a |
| WASB-SBDT (nttcom) | https://github.com/nttcom/WASB-SBDT | 212 | 2023-11-23 | MIT | Model zoo exists; terms not verified | BMVC 2023 baseline for ball tracking across sports including tennis | HRNet-based (paper; not verified on page) | n/a |
| MonoTrack (jhwang7628) | https://github.com/jhwang7628/monotrack | 61 | not fetched | A LICENSE file exists; type not shown | No weights links in README | Badminton: court, pose, shuttle, hit detection (HitNet), 2D and 3D trajectories. CVPRW 2022. Unclear if this is the authors' official repo. | Modified TrackNet, MMPose, HitNet | n/a |
| PadelVision (Duggineniakhil) | https://github.com/Duggineniakhil/PadelVision | 0 | 2026-10-13 | None | n/a | Padel: ball, court keypoints, shot map, highlights | YOLO | Long-rally grouping |
| pickleball-rally-detection (vinod-polinati) | https://github.com/vinod-polinati/pickleball-rally-detection | 18 | 2025-11-30 | None in API (search result says MIT) | n/a | Segments pickleball videos into rally clips | YOLOv8 ball and players | Not read |
| UVA 3D serve pipeline | https://github.com/jasnwag/tennis_serve_dataset | not fetched | not fetched | Not verified | Not verified | 5,966 US Open serves; 3D pose; DTW serve detection | RTMDet, RTMPose, MotionBERT | Serve found by DTW against 200 labeled serves |

"TRACE", "Tennis-Vision projects" other than the above, and CoachAI/ShuttleSet repositories: not found in the searches. Not verified. CoachAI 2023 challenge entry: https://github.com/jean50621/Badminton_Challenge (search snippet only).

### 3.2 Key details read from the sources

RallyClip (site https://iroblesrazzaq.github.io/RallyClip/ and README):
- Court detection: HRNet backbone, five heatmaps (near and far baselines, doubles sidelines, net). The site also describes a classical line detector with angle heuristics. The README says the deep model replaced it.
- Pose: YOLOv8-pose at 15 fps. Nano is about 3.1 M parameters. Small is about 11.6 M parameters.
- Features: limb lengths, joint velocity, joint acceleration. Players are filtered by the court mask.
- Sequence model: bidirectional LSTM on overlapping 20 s chunks. Chunk outputs are averaged. Smoothing and hysteresis give start and end times.
- Training data: about 8 hours of manually labeled matches. No accuracy numbers.
- Speed on MacBook Pro M2 (ratio of processing time to video time): nano 0.8x on GPU (MPS), 3x on CPU. Small 1.5x on GPU, 7x on CPU.
- Known failure: low light or bad angle makes court detection fail. Then pose tracks the wrong people.
- Known flaw: serve and return motion marks the start of a point. A dedicated rally model is planned.
- Doubles support is planned. This is a singles tool today.

tennis-vision (README read in full):
- Phase 1 on a GPU (Colab L4): about 15 minutes for a 20-minute 720p clip. Phase 2 on CPU (M-series Mac): about 10 minutes.
- Score reading: EasyOCR at about 1 Hz on a scoreboard region, with a tennis grammar to reject illegal transitions.
- Bounce filtering by temporal de-duplication and court locality removes about 21% to 27% of false bounces.
- The F3ED audit (https://philippdubach.com/posts/f3ed-cant-call-an-ace-fixing-a-neurips-2024-tennis-model/): on TenniSet V006 F3ED reached F1 0.542. Resolution matters: YOLOv8x pose coverage for the top player rose from 70.0% to 97.6% at 1080p but not at 720p.

ArtLabss tennis-tracking: README says about 28 minutes to process a 15-second clip (the text also says 16 minutes). Old TensorFlow code. Works on "official match videos" only.

TrackNetV3: 25 FPS on unstated hardware. Input size not stated. A background-image estimate helps on cluttered scenes.

RallyOS: audio onset detection (spectral flux at 44.1 kHz) gives about 1 ms hit timing. Hits are matched to video trajectory inflections. Self-reported F1 0.74 on real badminton and pickleball clips only. Hit sound is a cheap extra signal that our pipeline could use for rally and serve timing. This idea is unproven for tennis at distance.

### 3.3 Ready-to-reuse versus ideas only

| Use | Items |
|---|---|
| Can reuse code (license allows) | WASB-SBDT (MIT), TrackNetV3 (MIT per README), tennis-tracking (Unlicense), tennis-vision own code (MIT) |
| Ideas only | RallyClip (AGPL), tennis_analysis, TennisProject, F3Set, all unlicensed repos |
| Needs license purchase | Anything built on Ultralytics YOLO |
| Data is the real gap | No public dataset of amateur, behind-baseline, 4K 60 fps tennis was found. We must label our own. Labels for TrackNet-type models: ball center per frame. |

---

## 4. Local inference runtimes for a JVM desktop app

### 4.1 Comparison table

| Runtime | License | JVM access | Windows + NVIDIA | Windows Intel/AMD iGPU | Apple Silicon | Installer cost | Notes |
|---|---|---|---|---|---|---|---|
| ONNX Runtime (Java API) | MIT (not verified here) | Official Maven artifact. Java 8+. Latest on Central: 1.32.0. | CUDA provider via `onnxruntime_gpu` jar (399 MB). `addCUDA(deviceId)` in docs. | DirectML (legacy), OpenVINO for Intel (Java packaging not mentioned), WebGPU experimental through Windows ML | CoreML provider. Java `addCoreML()` exists. | CPU jar 55.7 MB (all natives in one jar; platform list not verified). CUDA jar 399 MB, plus CUDA and cuDNN libraries. | Main choice. |
| Windows ML (ORT-based, Microsoft) | Microsoft | None listed (C#, C++/WinRT, C, Python only) | NvTensorRtRtx provider (RTX 30 and newer, downloaded on demand) | DirectML (legacy, included), OpenVINO (Intel), AMD GPU provider (Adrenalin 24.12.1+). Vendor providers need Windows 11 24H2 (build 26100) or newer. | No | Small if framework-dependent. Needs Windows App SDK. | Not usable from plain Java without a bridge. |
| TensorRT-RTX (ORT provider) | NVIDIA terms (not verified) | C/C++ and Python only | RTX 30 and newer. Under 200 MB. Engines build on the user device "in seconds". | No | No | About 200 MB | Faster than CUDA on NVIDIA; JVM access not available. |
| NCNN | BSD-3-Clause | No Java binding in README (C API, Python). Android JNI exists (not verified). | Vulkan | Vulkan (works on any Vulkan GPU; vendor list not stated) | Vulkan via MoltenVK ("macos-vulkan" build 17 MB) | Windows shared build 16.7 MB (vs2022); macOS 13 to 17 MB | Small and portable. Needs a JNI wrapper. Convert with `pnnx`. Active: release 2026-10-20. |
| ExecuTorch | Not verified (NOASSERTION) | Docs list a Java API reference | Not verified | Not verified | Not verified (CoreML/MPS backends not shown on the page) | Not stated | Mobile-first. Weak fit. |
| CoreML via helper | Apple | None (needs a native helper) | n/a | n/a | Best Neural Engine use. | Small helper binary | Use ORT's CoreML provider first. |
| Python sidecar | Varies | Process boundary | Works (PyTorch CUDA) | Works with extra packages | Works (MPS) | Large: Python + torch is hundreds of MB to GB (not verified) | RallyClip ships this way for non-Mac. Security and AV scan cost. |
| llama.cpp | MIT | Server over HTTP | CUDA | Vulkan | Metal | Small binary + model GB | For VLM only. |
| MLX | MIT | None | No | No | Apple only | Python or Swift | Not for JVM. |

### 4.2 ONNX Runtime details

- Java docs list Windows x64, Linux x64, macOS x64 for CPU and Windows x64, Linux x64 for GPU (https://onnxruntime.ai/docs/get-started/with-java.html). The doc page is old on macOS arm64. The CoreML page says official macOS arm64 Java packaging passes `--use_coreml` (https://onnxruntime.ai/docs/execution-providers/CoreML-ExecutionProvider.html). Test on the M1 before we depend on this.
- The Java docs show only CUDA enablement. Javadoc lists `addDirectML(int)` and `addCoreML()` (https://onnxruntime.ai/docs/api/java/index-all.html, search result). Whether the official Windows jar contains the DirectML provider is not verified. A third-party artifact `io.github.eduramiba:onnxruntime-directml` exists (https://mvnrepository.com/artifact/io.github.eduramiba/onnxruntime-directml; search result only; not verified).
- CUDA requirements: ORT 1.27 to 1.30 default builds use CUDA 13.0 + cuDNN 9. CUDA 12.8 builds also exist. cuDNN 8 and 9 builds are not compatible (https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html). The Java package is not offered for every release (1.17.x and 1.18.1 listed with no Java package). Check each release before we upgrade.
- CoreML provider options: `ModelFormat` (NeuralNetwork or MLProgram), `MLComputeUnits` (ALL, CPUOnly, CPUAndNeuralEngine, CPUAndGPU), `ModelCacheDirectory`. Operator limits exist (examples: Conv 1D/2D with constant weights in NeuralNetwork format). Test each model for partial CPU fallback.
- OpenVINO provider: the built-in provider is "deprecated". The page recommends the standalone plugin (pip `onnxruntime-ep-openvino`, NuGet `Intel.ML.OnnxRuntime.EP.OpenVINO`, or Windows ML). Java and Maven packaging is not mentioned. So the Intel path from Java is uncertain (https://onnxruntime.ai/docs/execution-providers/OpenVINO-ExecutionProvider.html).

### 4.3 DirectML and Windows ML status (2026)

- DirectML is in "sustained engineering". It stays supported. New features move to Windows ML. Source: https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html (verified).
- DirectML limits: DirectML 1.15.2, ONNX opset up to 20, no GridSample 20 (5D), no DeformConv, no memory-pattern optimization, no parallel execution, one `Run` thread per session, fixed tensor shapes preferred.
- DirectML hardware: any DirectX 12 GPU (NVIDIA Kepler+, AMD GCN 1+, Intel Haswell HD+, Qualcomm Adreno 600+). Windows 10 1903+. This covers Intel and AMD integrated GPUs.
- Windows ML (https://learn.microsoft.com/en-us/windows/ai/new-windows-ml/overview, updated 2026-11-07): "Windows-supported and maintained copy of ONNX Runtime". Self-contained or framework-dependent. x64 and ARM64.
- Windows ML includes CPU and DirectML (legacy). Other providers download on demand and need Windows 11 24H2 or newer: NvTensorRtRtx (RTX 30+), OpenVINO, AMD GPU, VitisAI, QNN, WebGPU (experimental) (https://learn.microsoft.com/en-us/windows/ai/new-windows-ml/supported-execution-providers).
- Windows ML languages: C#, C++/WinRT, C/C++, Python (https://learn.microsoft.com/en-us/windows/ai/new-windows-ml/get-started). No Java.
- Consequence: for a JVM app, the safe order on Windows is CUDA (NVIDIA), then DirectML or OpenVINO (if the Java binary has it), then CPU. For CPU-only we must size the model so it finishes in reasonable time.

### 4.4 Small vision-language models (llama.cpp, MLX)

- llama.cpp supports image, audio, and video input. Model list includes SmolVLM2 (256M and 500M video variants), Qwen2.5-VL (3B, 7B), Gemma 3 and 4, InternVL, Moondream2 (https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md, verified).
- llama.cpp is MIT; 135 k stars; active (API). MLX is MIT; 29 k stars; active.
- Speed on M1 or RTX 3060 6 GB: not verified. We found no source for frame throughput.
- Judgment (no source): a VLM cannot read a ball at 60 fps in 4K. It is too slow and too coarse. Possible small use: classify a few cropped frames per point (for example, to confirm a serve motion), or to read a handwritten or on-screen score. Treat as optional research, not a core stage.

### 4.5 Hardware video decode for 4K 60 fps HEVC

| Item | Fact | Status |
|---|---|---|
| RTX 3060 NVDEC | HEVC 4:2:0 10-bit supported. One NVDEC engine. 5th generation. Max resolution and throughput not listed. | https://developer.nvidia.com/video-encode-and-decode-gpu-support-matrix-new (verified) |
| FFmpeg hardware decode APIs | Not verified: the FFmpeg wiki page was blocked. From general knowledge (not verified): NVDEC/CUVID, D3D11VA/DXVA2, QSV, VideoToolbox on macOS. | Not verified |
| JavaCV | Wraps FFmpeg. Optional `ffmpeg-platform-gpl` build exists. Default build license and hwaccel options not confirmed. GPL builds are a licensing problem for a closed app. | https://github.com/bytedeco/javacv (partly verified) |
| Phone footage format | 4K 60 fps HEVC from iPhone is often 10-bit (HLG). Hardware decoders must support Main10. Our decoder test must use real phone files. | Not verified (general knowledge) |

Planning numbers (calculation, not measured):
- One 4K RGB frame is 3840 x 2160 x 3 = 24.9 MB. 432,000 frames (2 hours at 60 fps) cannot go through system memory at full size in useful time.
- Stage A decode at 15 fps and 640 to 960 px wide (scale on GPU or with the decoder) reduces memory traffic by more than 50 times.
- For Stage B, decode full 60 fps but crop to the court region and, if possible, keep frames on the GPU.
- Test on the target machines: seek speed (to jump to rally spans), HEVC 10-bit support, and Intel/AMD hardware decode.

---

## 5. Pipeline implications by feature

| Feature | Best evidence from sources | Risk for our input |
|---|---|---|
| Rally detection | RallyClip: pose + BiLSTM at 15 fps. Rallytics/PerfectSwing: proxy video. PerfectSwing F1 0.84 (claim). | Doubles and bystanders on adjacent courts. Far player is small. Court mask needed. |
| Serve detection | UVA paper: DTW on pose against 200 labeled serves, for broadcast video. | Camera behind the baseline sees the near server's back; far server is small. Not proven for amateur video. |
| First-serve fault | tennis-vision: score delta (no change after a single-shot rally means fault). F3ED has no class for it. | No scoreboard in amateur video. Use ball: first serve bounce outside the service box, or no bounce, then a second serve by the same player. |
| Rally winner and score | tennis-vision: bounce projection for the winner. SwingVision: automatic score with user edits; final-score beta. | Errors add up over a match. Use tennis score grammar and let the user confirm. |
| Shot type | SwingVision gives forehand, backhand, serve, topspin, slice. F3ED has low F1 (0.54) on broadcast. Pose-based and CNN methods exist (arXiv 2507.02906: CNNs beat pose methods). | Needs labeled amateur data. |
| Ball speed | SwingVision: from 3D trajectory. tennis_analysis: from pixel motion (crude). | Need court calibration and a 3D or planar model. Accuracy unknown. |
| Landing position | SwingVision: 3D bounce location. tennis-vision: bounce heatmap on a minimap through court homography. | Far-end ball is a few pixels; 4K helps. Needs reliable court keypoints. |
| Court calibration | RallyClip: HRNet heatmaps. TennisProject: 14 keypoints. RallyOS: self-calibration (synthetic tests only). | Camera behind fence or net may hide lines. A one-time manual 4-point click is a good fallback (RallyOS does this). |

---

## Open questions

1. Does the official `onnxruntime` Maven jar for Windows include the DirectML provider? Does the macOS arm64 jar load CoreML at run time? Test with a small model on both OS.
2. Is there an official Java route to the OpenVINO plugin provider for Intel iGPU? Not mentioned in the OpenVINO page.
3. Which ball tracker has permissive weights and works on far-end tennis balls at 4K? We found code licenses (TrackNetV3 MIT, WASB MIT) but not weight or dataset terms for tennis. Check LICENSE files and dataset licenses before training on them.
4. What fraction of an amateur match is "ball in play"? It sets the cost of Stage B. Measure it on our sample videos.
5. Can we get score and first-serve faults without a scoreboard at useful accuracy? SwingVision's own auto-score is imperfect (reviewer reports). No number exists. We need our own benchmark.
6. What is the RallyClip license: AGPL-3.0 (GitHub API) or GPL-3.0 (project site)? Either way we cannot reuse code. Matters only if we want to cite or contribute.
7. How fast is YOLOv8-pose-class pose at 15 fps on an RTX 3060 and M1? Only M2 figures for RallyClip exist. Our own test is needed. Memory rule: do not run heavy local benchmarks only to prove a slowdown; read data files first.
8. Which products run fully local? Rallytics wording is unclear on what data leaves the machine. TennisCut says on-device but adds cloud storage in 2.1.0. Check network traffic if we want a clear claim.
9. Does the ITF report's "iOS 17 and later, version 11.9.6" imply anything about the resolution used? No. The report names no resolution.
10. Is hit sound (RallyOS idea) strong enough at behind-baseline distance on a phone microphone? Unproven for tennis.
11. Which Ultralytics Enterprise license price applies to us? No price is published. Avoiding YOLO is the safer plan.
12. MatchCut Pro, RallyFlow, 40Love.ai, Covelant, Zenniz, Wingfield, PlaySight: no technical pages found. A direct search by the user may find them.

---

## Sources

SwingVision and product pages (verified = opened):
- ITF approval report (verified): https://www.world.tennis/media/15294/pat-25-037-approval-report-swingvision.pdf
- App Store, SwingVision (verified): https://apps.apple.com/us/app/swingvision-tennis-pickleball/id989461317
- Apple developer story (verified): https://apps.apple.com/no/story/id1565190205
- Podcast transcript, line calling and Swing Stick (verified): https://www.buzzsprout.com/1045900/13734760/transcript
- Podcast transcript, post-match scoring beta (verified): https://www.buzzsprout.com/1045900/13802829/transcript
- tennis.com tournament test (verified): https://www.tennis.com/news/articles/fair-play-swingvision-puts-its-electronic-line-calling-to-the-test-in-tournament-play
- TennisNerd Smart Court (verified): https://www.tennisnerd.net/news/swingvision-smart-court-complete-ai-system-for-tennis/45328
- TennisNerd update 9.5.7 (verified): https://tennisnerd.net/tennis-tools/swingvision-update-9-5-7/27206
- TennisNerd review and interview (verified): https://www.tennisnerd.net/?p=25702
- Company funding article (search snippet): https://pulse2.com/swingvision-6-million-funding/
- Athletechnews interview (search snippet): https://athletechnews.com/?p=91620
- Patent claim (search snippet): https://pickleballunion.com/?p=14029
- Rallytics (verified): https://rallytics-app-landing.pages.dev/
- TennisCut (verified): https://apps.apple.com/app/id6754323620
- PerfectSwing FAQ (verified): https://perfectswing-web.pages.dev/faq
- SportAI FAQ (verified): https://sportai.com/faq
- OverRally (search snippet): https://www.producthunt.com/products/overrally
- Baseline Vision (search snippet): https://www.jpost.com/business-and-innovation/article-840925
- PB Vision podcast (search snippet): https://elderresearch.com/resource/podcasts/computer-vision-and-pickleball-game-changing-ai-insights-mining-your-own-business-podcast
- Belmont project (verified): https://repository.belmont.edu/spark_presentations/1097

Open-source (GitHub API values from 2026-11-16):
- https://github.com/iroblesrazzaq/RallyClip and https://iroblesrazzaq.github.io/RallyClip/
- https://github.com/abdullahtarek/tennis_analysis
- https://github.com/yastrebksv/TennisProject
- https://github.com/ArtLabss/tennis-tracking
- https://github.com/philippdubach/tennis-vision
- https://philippdubach.com/posts/f3ed-cant-call-an-ace-fixing-a-neurips-2024-tennis-model/
- https://github.com/StanlyHardy/open-tennis/blob/experimental/README.md
- https://github.com/nakuldagade/RallyOS
- https://github.com/qaz812345/TrackNetV3
- https://github.com/nttcom/WASB-SBDT
- https://github.com/jhwang7628/monotrack and https://arxiv.org/abs/2204.01899
- https://github.com/F3Set/F3Set
- https://github.com/Duggineniakhil/PadelVision
- https://github.com/vinod-polinati/pickleball-rally-detection
- https://github.com/Sh-31/Tennis-Analysis-System
- UVA serve paper (PDF read): https://cvbw2026.github.io/assets/papers/11.pdf ; code https://github.com/jasnwag/tennis_serve_dataset (not opened)
- Doubles framework paper (abstract read): https://arxiv.org/abs/2507.02906
- Ultralytics license (verified): https://www.ultralytics.com/license
- RF-DETR (API: Apache-2.0): https://github.com/roboflow/rf-detr
- MMPose (API: Apache-2.0): https://github.com/open-mmlab/mmpose

Runtimes and decode:
- ORT Java: https://onnxruntime.ai/docs/get-started/with-java.html
- ORT Maven listing: https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.32.0/ and .../onnxruntime_gpu/1.32.0/
- ORT CUDA: https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html
- ORT DirectML: https://onnxruntime.ai/docs/execution-providers/DirectML-ExecutionProvider.html
- ORT CoreML: https://onnxruntime.ai/docs/execution-providers/CoreML-ExecutionProvider.html
- ORT OpenVINO: https://onnxruntime.ai/docs/execution-providers/OpenVINO-ExecutionProvider.html
- ORT TensorRT-RTX: https://onnxruntime.ai/docs/execution-providers/TensorRTRTX-ExecutionProvider.html
- Windows ML overview: https://learn.microsoft.com/en-us/windows/ai/new-windows-ml/overview
- Windows ML providers: https://learn.microsoft.com/en-us/windows/ai/new-windows-ml/supported-execution-providers
- Windows ML get started: https://learn.microsoft.com/en-us/windows/ai/new-windows-ml/get-started
- ncnn: https://github.com/Tencent/ncnn (API release 20261020)
- ExecuTorch: https://docs.pytorch.org/executorch/stable/index.html
- llama.cpp multimodal: https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md
- MLX: https://github.com/ml-explore/mlx
- JavaCV: https://github.com/bytedeco/javacv
- NVDEC matrix: https://developer.nvidia.com/video-encode-and-decode-gpu-support-matrix-new

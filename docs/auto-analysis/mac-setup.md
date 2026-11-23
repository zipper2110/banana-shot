# Automatic point detection on a Mac with Apple Silicon

Status: 2026-11-23. The code is ready. **No test on a Mac was done.** The Mac run is not verified until
`selfcheck.py` passes on the Mac (section 5).

The detection chain (`tools/auto-analysis/detect.py`) was made on a Windows PC with an NVIDIA GPU. This
document tells how the chain runs on a computer with no NVIDIA GPU, what was measured on the PC, and what the
user must do on the Mac.

## 1. What changed in the chain

`platform_cfg.py` decides each platform-dependent value in one place:

| Item | PC with NVIDIA (as before) | Mac with Apple Silicon | Other computer |
|---|---|---|---|
| Lab root (`BANANASHOT_LAB`) | `D:/bananashot-lab` | `~/bananashot-lab` | `~/bananashot-lab` (Windows: `D:/bananashot-lab`) |
| PyTorch device (`BANANASHOT_DEVICE`) | `cuda`, 16-bit floats | `mps`, 32-bit floats | `cpu`, 32-bit floats |
| Rally model | `cuda` | `cpu` | `cpu` |
| ONNX Runtime provider (`BANANASHOT_ORT`) | `cuda` | `coreml`, then `cpu` | `cpu` |
| Video decode (`BANANASHOT_DECODE`) | `cuda` (GPU decode, `scale_cuda`) | `software` | `software` |
| ffmpeg, ffprobe (`BANANASHOT_FFMPEG`) | from `PATH` | from `PATH`, then `/opt/homebrew/bin`, `/usr/local/bin` | from `PATH` |

Each environment variable in the table sets its value by hand. `detect.py` prints the selected values in its
first line (`platform: ...`), and the app writes this line into `run.log`.

The picture path with no NVIDIA GPU is `cuda_scale.py`. The GPU filter `scale_cuda` has no anti-alias filter,
and the software scaler of ffmpeg has one. So the software scaler gives other pixels to the ball detector. To
prevent this, `cuda_scale.py` calculates the `scale_cuda` kernel on the CPU with the same 32-bit float
operations (bicubic with parameter 0, fused multiply-add). The chain is: ffmpeg (decode) -> `cuda_scale.py`
(scale) -> ffmpeg (NV12 to RGB, or JPEG).

## 2. Inventory of the platform-dependent places

Scripts of the chain (ported):

| File | Place | Now |
|---|---|---|
| `lab.py`, `prep.py` | `labRoot` of `matches.json` (`D:/bananashot-lab/data`) | `platform_cfg.data_root` |
| `prep.py` | keyframes at 1 fps: `-hwaccel cuda`, `scale_cuda=1920:-2` | CUDA as before; else `cuda_scale.keyframes_1fps` |
| `prep.py` | keyframes of a video with a keyframe each second: `-hwaccel cuda`, software `scale` | `platform_cfg.hwaccel`; with no GPU the filter `format=nv12` comes before `scale` |
| `prep.py`, `frame_times.py` | `ffmpeg`, `ffprobe` from `PATH` | `platform_cfg.tool` |
| `ball_wasb.py` | weights folder `D:/bananashot-lab/ext` | `platform_cfg.weights_dir` |
| `ball_wasb.py` | `-hwaccel cuda`, `scale_cuda`, `hwdownload` | CUDA as before; else `cuda_scale.rgb_frames` |
| `ball_wasb.py` | `.cuda().half()` for the model and the tensors | `platform_cfg.to_device`, `platform_cfg.fp` |
| `feat_dino.py` | `HF_HOME` = `D:/bananashot-lab/hf` | `platform_cfg.hf_home` (set when `platform_cfg` is imported) |
| `feat_dino.py`, `detect.py` | `.cuda()`, `torch.autocast("cuda")`, `torch.cuda.empty_cache()` | `platform_cfg.to_device`, autocast only on CUDA, `platform_cfg.empty_cache` |
| `pose_extract.py`, `detect.py` | `device="cuda"` for YOLOX and RTMPose; model files from a URL | `platform_cfg.ort_device`, `platform_cfg.pose_model` (a local file if it is in the weights folder) |
| `rally_model.py` (`predict`) | `.cuda()`, `torch.load` with no `map_location` | `platform_cfg.small_device`, `map_location="cpu"` |

Files that are not in git: the venv (`requirements-mac.txt`), the weights and models (`pack_for_mac.py`).

Not ported (training and experiment scripts, they run only on the lab PC):

- `ball_train.py`, `ball_labels.py`: `D:/bananashot-lab/data/train`, `.cuda()`, CUDA autocast.
- `rally_model.py` (`train`), `seq_eval.py`: `.cuda()`, `dev = "cuda"`.
- `burst_ball.py` (`frames`), `fault_sheet.py`: `-hwaccel cuda`, `scale_cuda`.
- `prep.py` with a marked match: `%APPDATA%/BananaShot/projects`. `matches.json`: video paths on `D:/`.
- `demo_clip.py`, `scan_sheet.py`, `demo_report.py`: no CUDA, but they use the lab match folders. Not tested.

App side (`src/main/kotlin/org/litvin/autodetect`): no Windows-only code found. The paths come from
`autodetect.json`, and the app data folder on a Mac is `~/Library/Application Support/BananaShot`. One risk
was found and solved in Python: an app that starts from the Finder has no Homebrew folder on `PATH`, so
`platform_cfg.tool` also looks for ffmpeg in `/opt/homebrew/bin` and `/usr/local/bin`.

## 3. What was measured on the lab PC (E24)

Test videos: two clips of 14 s, cut with no encode from lab videos (4K HEVC). Clip A: 30 fps, a keyframe each
2 s (the path with 1 fps keyframes and the 30 fps ball path). Clip B: 60 fps, a keyframe each second. The
reference is the result of the CUDA chain.

| Test | Result |
|---|---|
| New code on CUDA against the code before the change | All 62 output files are byte-identical (keyframes, audio, features, ball detections, poses, rally probabilities) |
| Software decode against GPU decode (full 4K frames, 10 frames) | Bit-exact |
| `cuda_scale.py` against `scale_cuda` (to 672 x 378 and to 1920 x 1080) | Bit-exact |
| Software picture path, models on CUDA (`BANANASHOT_DECODE=software`) | Keyframes: all pixels equal (clip B: the JPEG files are byte-identical; clip A: the pixels are equal, the file bytes are not). Ball pictures: all 1232 pictures equal. Ball detections, poses, image features, rally probabilities: all equal |
| Full path with no GPU (`software`, PyTorch on the CPU with 32-bit floats, ONNX Runtime on the CPU) | See the next table |

Full path with no GPU against CUDA. The pictures are the same, so each difference comes from the calculation
of the models (32-bit floats on the CPU against 16-bit floats on the GPU):

| Result | Clip A | Clip B |
|---|---|---|
| Image features (DINO) | cosine similarity 0.999998 minimum, relative difference 0.17 % | 0.999999, 0.16 % |
| Ball detections | 130 -> 131 (130 matched, 1 new); position: median 0.0007 px, 99 % 0.26 px, max 0.45 px; best detection of each frame the same in 128 of 128 frames | 508 -> 508, all matched; median 0.0008 px, 99 % 0.89 px, max 1.40 px; the same in 431 of 431 frames |
| Ball score (blob sum) | median 0.02 %, max 15 % | median 0.03 %, max 49 % (a small blob near the threshold) |
| Poses | 26 persons, all matched; keypoints: median 0.003 px, 99 % 1.6 px, max 3.2 px; confidence max 0.006 | 55 persons, all matched; median 0.002 px, 99 % 0.74 px, max 13.8 px (one keypoint); confidence max 0.006 |
| Rally model (same features, 600 s) | probability difference max 0.0010 (ball model), 0.0015 (base model); no second crosses a threshold | the same test |

Not measured: the effect of these differences on the result of a full match (rallies, winners). A full-video
run was not permitted on the working PC.

## 4. CRITICAL: where the Mac can change the detection quality

1. **Model arithmetic (measured on the PC CPU, not on MPS).** The Mac uses 32-bit floats; the PC uses 16-bit
   floats. Size on the PC CPU: the table above. A ball position moves by less than 1 px for 99 % of the
   detections. A blob near the threshold can appear or go (1 of 638). MPS can give other numbers than the CPU:
   not measured.
2. **CoreML for the pose models (not measured).** CoreML can calculate with 16-bit floats and can give other
   person boxes near the score limit of 0.7. If the self-check shows DIFF for the poses, set
   `BANANASHOT_ORT=cpu`: the CPU provider was measured (table above).
3. **ffmpeg on arm64 (not measured).** The picture path is bit-exact on the PC, but three steps stay in ffmpeg:
   the NV12 to RGB conversion of the ball pictures, the software `scale` of the keyframes (videos with a
   keyframe each second), and the JPEG encoder. If the arm64 code of ffmpeg rounds in another way, the pixels
   can change by about 1 level. The self-check compares the keyframes and the ball pictures pixel by pixel.
   Use ffmpeg 8.x (the PC has 8.1).
4. **VideoToolbox decode (not measured, off by default).** `BANANASHOT_DECODE=videotoolbox` is faster. Use it
   only if the self-check with this setting shows the same keyframes and ball pictures.
5. **Audio (not measured).** The AAC decoder and the resampler use floats. The features can change in the last
   digits. The self-check compares them.
6. **Package versions.** `requirements-mac.txt` has the PC versions. If pip has no arm64 wheel for a version
   and the user installs another one, the results can change. The self-check shows it.

Not a quality risk, but a limit: a video with 10-bit color does not run on the PC chain (NV12 download). The
software path converts it to 8 bits; that path was not tested.

## 5. Steps on the Mac

On the lab PC (one time):

1. Make the reference (done on 2026-11-23, in `D:\bananashot-lab\mac-port\ref`). To make it again:
   `python selfcheck.py make-ref --name <name> --video <clip> --court <court.json> --out <folder> --rally-from <work folder of a full run>`.
2. Pack the files: `D:\bananashot-lab\venv\Scripts\python.exe pack_for_mac.py --out D:\bananashot-lab\mac-pack --ref D:\bananashot-lab\mac-port\ref`
   (add `--zip` for one archive). The result is `D:\bananashot-lab\mac-pack\bananashot-lab`.
   A pack of 2026-11-23 is ready in `D:\bananashot-lab\mac-port\pack\bananashot-lab` (518 MB, 83 files).
   `MANIFEST.txt` in it lists each file with size, SHA-256, source and license.
3. Copy the folder `bananashot-lab` to the home folder of the Mac (`~/bananashot-lab`). The clips are private
   video: do not put them on a public service.

On the Mac:

1. Install Homebrew, then `brew install ffmpeg python@3.13`.
2. Get the repository (branch with this document).
3. `cd tools/auto-analysis`, then `bash setup_mac.sh`. The script makes `~/bananashot-lab/venv`, installs
   `requirements-mac.txt`, and prints the text of `autodetect.json`.
4. Run the self-check: `~/bananashot-lab/venv/bin/python selfcheck.py check`. It runs the stages on the two
   clips, compares each result with the reference, and prints the speed of each stage. The report is
   `~/bananashot-lab/selfcheck/work/report.json`. Send this file back for the journal.
5. Write `~/Library/Application Support/BananaShot/autodetect.json`:

   ```json
   {
     "python": "/Users/<name>/bananashot-lab/venv/bin/python",
     "script": "/Users/<name>/<repository>/tools/auto-analysis/detect.py",
     "workRoot": "/Users/<name>/bananashot-lab/app",
     "environment": { "BANANASHOT_LAB": "/Users/<name>/bananashot-lab" }
   }
   ```

6. Start the app. "Detect points" is on the Points tab.

### If the self-check shows DIFF

| Line with DIFF | Do this |
|---|---|
| keyframes, ball pictures | The ffmpeg of the Mac gives other pixels (section 4, item 3). Record the numbers. A mean difference below 0.5 level shows as OK |
| poses | Run again with `BANANASHOT_ORT=cpu`. If the result is then OK, add `"BANANASHOT_ORT": "cpu"` to `environment` in `autodetect.json` |
| image features, ball detections | Run again with `BANANASHOT_DEVICE=cpu`. If the result is then OK, MPS is the cause. The CPU is much slower: decide with the speed numbers |
| audio features, mel features | Record the numbers (section 4, item 5) |

The tolerances of OK are in `selfcheck.py` (for example: 99 % of the ball positions within 1.5 px, the best
detection of 99 % of the frames the same, no person lost, 99 % of the keypoints within 3 px).

## 6. Speed

Not known for the Mac: not measured. The self-check prints the speed of each stage on the Mac.

Measured on the lab PC (laptop, RTX 3060, another GPU job was active, so the times are not reliable; clips of
14 s, so the load time of the models is a large part):

| Stage | CUDA | Software pictures, models on CUDA | All on the CPU |
|---|---|---|---|
| Ball (video seconds for each second of run time) | 1.4 to 2.0 | 0.3 to 0.7 | 0.05 to 0.08 |
| Pose | 2.6 to 2.9 | the same | 1.3 |

The software picture path decodes 4K video on the CPU and scales it in numpy (4 threads): about 17 to 31
frames each second on the PC. On the CPU, the ball model is the limit (2 to 3 frames each second). On the Mac,
the ball stage will be the slow stage. If it is too slow, the setting "ballPass: false" gives the rally
boundaries only (no ball stage, no pose stage).

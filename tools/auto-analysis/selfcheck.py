"""Self-check of the detection chain on a computer (docs/auto-analysis/mac-setup.md).

The lab PC (NVIDIA GPU) makes reference results from short video clips. Another computer (a Mac)
runs the same stages on the same clips and compares its results with the reference.

  python selfcheck.py make-ref --name <name> --video <clip> --court <court.json> --out <ref folder>
                               [--rally-from <work folder of a full run>]
  python selfcheck.py check [--ref <ref folder>] [--work <folder>] [--only <name>]

Defaults: --ref <lab root>/selfcheck/ref, --work <lab root>/selfcheck/work.
A reference "<name>" is a folder: the clip, court.json, and the results of the stages
  kf/*.jpg, kf_times.csv, pts.npy       media (prep.py, frame_times.py)
  feat_audio.npy, feat_mel.npy          audio
  feat_dino.npy, feat_dinogrid.npy      image features
  ball_rgb.npy, ball_sums.npy           the first pictures of the ball detector, and the pixel sum of each picture
  ball.npy                              ball detections
  pose.npy                              poses
  rally/                                feature files of a long video part, and the rally probability of each model
The check prints one line for each result and a verdict: SAME (bit-exact), OK (in the tolerance), or DIFF.
The stages run alone, with no court detection and no flights: those stages have only CPU code.
"""
import argparse, glob, json, os, platform, shutil, sys, time

MID, RALLY = "job", "rallyref"
RALLY_SECONDS = 600
SAMPLE = 6


def run_chain(video, work, rally_dir):
    """Runs the stages for one clip. work must contain court.json. Returns the run time of each stage (s)."""
    import numpy as np
    import platform_cfg
    import torch
    import lab
    lab.MATCHES[MID] = {"id": MID, "video": os.path.abspath(video)}
    lab.mdir = lambda m, _o=lab.mdir: work if m == MID else rally_dir if m == RALLY else _o(m)
    W = lambda *p: os.path.join(work, *p)
    times = {}

    def timed(name, fn):
        t = time.time()
        fn()
        times[name] = round(time.time() - t, 2)
        print(f"  {name}: {times[name]} s", flush=True)

    import prep, frame_times
    timed("media", lambda: (prep.prep(lab.MATCHES[MID], work), frame_times.load(MID)))
    n = len(np.loadtxt(W("kf_times.csv"), ndmin=1))
    import feat_audio, feat_mel
    timed("audio", lambda: (feat_audio.run(MID), feat_mel.run(MID)))

    def image():
        import timm, feat_dino
        model = platform_cfg.to_device(timm.create_model("vit_small_patch14_dinov2.lvd142m", pretrained=True,
                                                         img_size=(feat_dino.H, feat_dino.W))).eval()
        feat_dino.run(MID, model)
    timed("image", image)

    def ball():
        import ball_wasb
        sample, sums, orig = [], [], ball_wasb.frames

        def record(*a):
            for f in orig(*a):
                if len(sample) < SAMPLE:
                    sample.append(f.copy())
                sums.append(int(f.sum(dtype=np.int64)))
                yield f
        ball_wasb.frames = record
        pts = np.load(W("pts.npy"))
        if np.percentile(np.diff(pts), 90) < 0.02:                  # as detect.py
            d = ball_wasb.run(MID, 0, n, save=False)
        else:
            ball_wasb.FPS = 30
            d = ball_wasb.run(MID, 0, n, stride=1, save=False)
        ball_wasb.frames, ball_wasb.FPS = orig, None
        np.save(W("ball.npy"), d)
        np.save(W("ball_rgb.npy"), np.stack(sample))
        np.save(W("ball_sums.npy"), np.array(sums, np.int64))
        times["ball_frames"] = len(sums)
    timed("ball", ball)

    def pose():
        import pose_extract
        from rtmlib import YOLOX, RTMPose
        dev = platform_cfg.ort_device()
        det = YOLOX(platform_cfg.pose_model(pose_extract.DET), model_input_size=(640, 640), backend="onnxruntime", device=dev)
        ps = RTMPose(platform_cfg.pose_model(pose_extract.POSE), model_input_size=(192, 256), backend="onnxruntime", device=dev)
        pose_extract.run(MID, det, ps)
    timed("pose", pose)

    def rally():
        import rally_model
        for name in ("ball", "base"):
            np.save(W(f"rally_prob_{name}.npy"), rally_model.predict(RALLY, name).astype(np.float32))
    if os.path.exists(os.path.join(rally_dir, "kf_times.csv")):
        timed("rallies", rally)
    times["video_s"] = n
    return times


def environment():
    import platform_cfg, torch, onnxruntime, numpy, cv2, PIL, subprocess
    ff = subprocess.run([platform_cfg.tool("ffmpeg"), "-version"], capture_output=True, text=True).stdout.splitlines()[:1]
    return {"platform": platform.platform(), "machine": platform.machine(), "python": platform.python_version(),
            "summary": platform_cfg.summary(), "torch": torch.__version__, "onnxruntime": onnxruntime.__version__,
            "numpy": numpy.__version__, "opencv": cv2.__version__, "pillow": PIL.__version__, "ffmpeg": ff[0] if ff else "?"}


def make_ref(a):
    import numpy as np
    out = os.path.join(a.out, a.name)
    rally = os.path.join(out, "rally")
    os.makedirs(rally, exist_ok=True)
    clip = os.path.join(out, "clip" + os.path.splitext(a.video)[1])
    if os.path.abspath(a.video) != os.path.abspath(clip):
        shutil.copy(a.video, clip)
    shutil.copy(a.court, os.path.join(out, "court.json"))
    if a.rally_from:
        for f in ("feat_audio.npy", "feat_mel.npy", "feat_dino.npy", "feat_wasbf.npy"):
            np.save(os.path.join(rally, f), np.load(os.path.join(a.rally_from, f))[:RALLY_SECONDS])
        lines = [ln for ln in open(os.path.join(a.rally_from, "kf_times.csv")) if ln.strip()][:RALLY_SECONDS]
        open(os.path.join(rally, "kf_times.csv"), "w").write("".join(lines))
    times = run_chain(clip, out, rally)
    for f in glob.glob(os.path.join(out, "rally_prob_*.npy")):
        shutil.move(f, os.path.join(rally, os.path.basename(f)))
    json.dump({"clip": os.path.basename(clip), "environment": environment(), "times": times}, open(os.path.join(out, "ref.json"), "w"), indent=1)
    print("reference:", out)


# ---- comparison

def pix(a, b):
    import numpy as np
    d = np.abs(a.astype(np.int16) - b.astype(np.int16))
    return f"mean abs difference {d.mean():.4f}, max {int(d.max())}, equal pixels {100 * (d == 0).mean():.2f} %", bool(d.max() == 0), float(d.mean())


def cmp_keyframes(ref, work):
    import cv2
    fr, fw = sorted(glob.glob(os.path.join(ref, "kf", "*.jpg"))), sorted(glob.glob(os.path.join(work, "kf", "*.jpg")))
    if len(fr) != len(fw):
        return f"count {len(fw)}, reference {len(fr)}", "DIFF"
    same_bytes = all(open(x, "rb").read() == open(y, "rb").read() for x, y in zip(fr, fw))
    import numpy as np
    text, same, mean = pix(np.stack([cv2.imread(x) for x in fr]), np.stack([cv2.imread(y) for y in fw]))
    return f"{len(fr)} files, files equal: {same_bytes}, {text}", "SAME" if same else "OK" if mean < 0.5 else "DIFF"


def cmp_array(name, tol):
    def f(ref, work):
        import numpy as np
        x, y = np.load(os.path.join(ref, name)).astype(np.float64), np.load(os.path.join(work, name)).astype(np.float64)
        if x.shape != y.shape:
            return f"shape {y.shape}, reference {x.shape}", "DIFF"
        d = np.abs(np.nan_to_num(x) - np.nan_to_num(y))
        scale = np.abs(np.nan_to_num(x)).max() + 1e-12
        return f"max abs difference {d.max():.3g} (largest value {scale:.3g})", "SAME" if d.max() == 0 else "OK" if d.max() <= tol * scale else "DIFF"
    return f


def cmp_dino(ref, work):
    import numpy as np
    x, y = np.load(os.path.join(ref, "feat_dino.npy")).astype(np.float64), np.load(os.path.join(work, "feat_dino.npy")).astype(np.float64)
    if x.shape != y.shape:
        return f"shape {y.shape}, reference {x.shape}", "DIFF"
    cos = (x * y).sum(1) / (np.linalg.norm(x, axis=1) * np.linalg.norm(y, axis=1) + 1e-12)
    d = np.abs(x - y)
    rel = np.linalg.norm(x - y) / np.linalg.norm(x)
    return (f"cosine similarity min {cos.min():.6f}, relative difference {rel:.5f}, max abs difference {d.max():.4f}",
            "SAME" if d.max() == 0 else "OK" if cos.min() > 0.999 else "DIFF")


def cmp_ball_rgb(ref, work):
    import numpy as np
    x, y = np.load(os.path.join(ref, "ball_rgb.npy")), np.load(os.path.join(work, "ball_rgb.npy"))
    sx, sy = np.load(os.path.join(ref, "ball_sums.npy")), np.load(os.path.join(work, "ball_sums.npy"))
    if x.shape != y.shape or len(sx) != len(sy):
        return f"pictures {len(sy)} of shape {y.shape[1:]}, reference {len(sx)} of shape {x.shape[1:]}", "DIFF"
    text, same, mean = pix(x, y)
    eq = int((sx == sy).sum())
    return (f"{len(sx)} pictures, equal pixel sum {eq} of {len(sx)}; first {len(x)} pictures: {text}",
            "SAME" if same and eq == len(sx) else "OK" if mean < 0.5 else "DIFF")


def cmp_ball(ref, work):
    """Matches the detections frame by frame (nearest position, at most 3 px)."""
    import numpy as np
    x, y = np.load(os.path.join(ref, "ball.npy")), np.load(os.path.join(work, "ball.npy"))
    if x.shape == y.shape and np.array_equal(x, y):
        return f"{len(x)} detections, all equal", "SAME"
    dist, dscore, hit = [], [], 0
    for fr in np.unique(x[:, 0]):
        a, b = x[x[:, 0] == fr], y[y[:, 0] == fr]
        used = set()
        for r in a:
            if not len(b):
                break
            dd = np.hypot(b[:, 1] - r[1], b[:, 2] - r[2])
            j = int(dd.argmin())
            if dd[j] <= 3 and j not in used:
                used.add(j); hit += 1
                dist.append(dd[j]); dscore.append(abs(b[j, 3] - r[3]) / max(r[3], 1e-6))

    def top(d):                                              # the best detection of each frame, as the flight code uses it
        d = d[np.argsort(d[:, 0], kind="stable")]
        keep = np.ones(len(d), bool); keep[1:] = d[1:, 0] != d[:-1, 0]
        return {int(r[0]): r for r in d[keep]}
    tx, ty = top(x), top(y)
    same_top = sum(1 for k, r in tx.items() if k in ty and np.hypot(ty[k][1] - r[1], ty[k][2] - r[2]) <= 3)
    dist, dscore = np.array(dist or [0.0]), np.array(dscore or [0.0])
    lost, new = len(x) - hit, len(y) - hit
    text = (f"detections {len(y)}, reference {len(x)}: matched {hit}, not found {lost}, new {new}; position difference of the matched: "
            f"median {np.median(dist):.4f} px, 99 % {np.percentile(dist, 99):.4f} px, max {dist.max():.4f} px; score difference median "
            f"{100 * np.median(dscore):.3f} %, max {100 * dscore.max():.2f} %; frames with the same best detection {same_top} of {len(tx)} "
            f"(frames with a detection now: {len(ty)})")
    ok = lost + new <= 0.02 * len(x) and np.percentile(dist, 99) < 1.5 and same_top >= 0.99 * len(tx)
    return text, "OK" if ok else "DIFF"


def cmp_pose(ref, work):
    """Matches the persons of each keyframe by box overlap (IoU > 0.5)."""
    import numpy as np
    x, y = np.load(os.path.join(ref, "pose.npy")), np.load(os.path.join(work, "pose.npy"))
    if x.shape == y.shape and np.array_equal(x, y):
        return f"{len(x)} persons, all equal", "SAME"
    hit, dbox, dkp, dconf, flips = 0, [], [], [], 0
    for fr in np.unique(x[:, 0]):
        a, b = x[x[:, 0] == fr], y[y[:, 0] == fr]
        for r in a:
            best, bi = 0.0, -1
            for j, q in enumerate(b):
                iw, ih = min(r[3], q[3]) - max(r[1], q[1]), min(r[4], q[4]) - max(r[2], q[2])
                inter = max(0.0, iw) * max(0.0, ih)
                iou = inter / ((r[3] - r[1]) * (r[4] - r[2]) + (q[3] - q[1]) * (q[4] - q[2]) - inter + 1e-9)
                if iou > best:
                    best, bi = iou, j
            if best > 0.5:
                hit += 1
                q = b[bi]
                dbox.append(np.abs(r[1:5] - q[1:5]).max())
                kr, kq = r[6:].reshape(17, 3), q[6:].reshape(17, 3)
                sure = kr[:, 2] >= 0.3
                if sure.any():
                    dkp.extend(np.hypot(kr[sure, 0] - kq[sure, 0], kr[sure, 1] - kq[sure, 1]).tolist())
                dconf.append(np.abs(kr[:, 2] - kq[:, 2]).max())
                flips += int(((kr[:, 2] >= 0.3) != (kq[:, 2] >= 0.3)).sum())
    dbox, dkp, dconf = np.array(dbox or [0.0]), np.array(dkp or [0.0]), np.array(dconf or [0.0])
    lost, new = len(x) - hit, len(y) - hit
    text = (f"persons {len(y)}, reference {len(x)}: matched {hit}, not found {lost}, new {new}; box difference max {dbox.max():.3f} px; "
            f"keypoint difference (confidence >= 0.3): median {np.median(dkp):.4f} px, 99 % {np.percentile(dkp, 99):.3f} px, max {dkp.max():.3f} px; "
            f"confidence difference max {dconf.max():.4f}; keypoints that cross the 0.3 confidence limit: {flips}")
    ok = lost + new == 0 and np.percentile(dkp, 99) < 3.0 and dconf.max() < 0.02
    return text, "OK" if ok else "DIFF"


def cmp_rally(name):
    def f(ref, work):
        import numpy as np
        x, y = np.load(os.path.join(ref, "rally", f"rally_prob_{name}.npy")), np.load(os.path.join(work, f"rally_prob_{name}.npy"))
        d = np.abs(x - y)
        flips = int(((x >= 0.5) != (y >= 0.5)).sum() + ((x >= 0.35) != (y >= 0.35)).sum())
        return (f"{len(x)} s, max abs difference of the probability {d.max():.2e}, seconds that cross a threshold (0.5 or 0.35): {flips}",
                "SAME" if d.max() == 0 else "OK" if d.max() < 5e-3 and flips == 0 else "DIFF")
    return f


CHECKS = [("keyframes", cmp_keyframes), ("frame times", cmp_array("pts.npy", 0)), ("audio features", cmp_array("feat_audio.npy", 1e-3)),
          ("mel features", cmp_array("feat_mel.npy", 1e-3)), ("image features (DINO)", cmp_dino), ("ball pictures", cmp_ball_rgb),
          ("ball detections", cmp_ball), ("poses", cmp_pose), ("rally model, ball", cmp_rally("ball")), ("rally model, base", cmp_rally("base"))]


def check(a):
    import platform_cfg
    ref_root = a.ref or os.path.join(platform_cfg.lab_root(), "selfcheck", "ref")
    work_root = a.work or os.path.join(platform_cfg.lab_root(), "selfcheck", "work")
    names = sorted(d for d in os.listdir(ref_root) if os.path.exists(os.path.join(ref_root, d, "ref.json")))
    if a.only:
        names = [n for n in names if n == a.only]
    if not names:
        raise SystemExit(f"No reference in {ref_root}")
    env = environment()
    print("this computer:", json.dumps(env, indent=1), flush=True)
    bad, report = 0, {"environment": env, "clips": {}}
    for name in names:
        ref, work = os.path.join(ref_root, name), os.path.join(work_root, name)
        meta = json.load(open(os.path.join(ref, "ref.json")))
        shutil.rmtree(work, ignore_errors=True)
        os.makedirs(work)
        shutil.copy(os.path.join(ref, "court.json"), os.path.join(work, "court.json"))
        print(f"\n== {name}: run the stages (reference made with: {meta['environment']['summary']})", flush=True)
        times = run_chain(os.path.join(ref, meta["clip"]), work, os.path.join(ref, "rally"))
        print(f"== {name}: compare", flush=True)
        rows = {}
        for title, fn in CHECKS:
            try:
                text, verdict = fn(ref, work)
            except FileNotFoundError as e:
                text, verdict = f"no file {os.path.basename(e.filename or '?')}", "SKIP"
            bad += verdict == "DIFF"
            rows[title] = {"verdict": verdict, "text": text}
            print(f"  [{verdict:4}] {title}: {text}", flush=True)
        v = times["video_s"]
        speed = {k: round(v / max(times[k], 1e-6), 2) for k in ("media", "image", "ball", "pose") if k in times}
        print(f"  speed (video seconds for each second of run time; the first run of a model includes its load time): {speed}; "
              f"reference: { {k: round(meta['times']['video_s'] / max(meta['times'][k], 1e-6), 2) for k in speed} }", flush=True)
        report["clips"][name] = {"checks": rows, "times": times, "speed": speed}
    json.dump(report, open(os.path.join(work_root, "report.json"), "w"), indent=1)
    print(f"\nreport: {os.path.join(work_root, 'report.json')}")
    print("RESULT: " + ("all results are the same as the reference, or in the tolerance." if not bad else
                        f"{bad} result(s) are different from the reference (DIFF). Read docs/auto-analysis/mac-setup.md, section \"If the self-check shows DIFF\"."))
    return 1 if bad else 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    m = sub.add_parser("make-ref")
    m.add_argument("--name", required=True); m.add_argument("--video", required=True); m.add_argument("--court", required=True)
    m.add_argument("--out", required=True); m.add_argument("--rally-from", default="")
    c = sub.add_parser("check")
    c.add_argument("--ref", default=""); c.add_argument("--work", default=""); c.add_argument("--only", default="")
    a = ap.parse_args()
    if a.cmd == "make-ref":
        make_ref(a)
    else:
        sys.exit(check(a))

"""Automatic point detection for one video with no marks (the entry of the app integration,
docs/auto-analysis/app-integration.md). The app starts this script as an external process.

Stages (each one is skipped when its output is in the work folder, so a stopped run continues):
  media     audio, one keyframe for each second, frame times           (prep.py, frame_times.py)
  audio     audio and mel features                                      (feat_audio.py, feat_mel.py)
  image     DINOv2 features of the keyframes                            (feat_dino.py)
  court     court model each 60 s                                       (court_track.py)
  ball      ball detection on all frames, the slow stage                (ball_wasb.py)
  flights   3D flights and the ball features                            (ball_full.py, feat_wasb.py)
  pose      player poses on the keyframes                               (pose_extract.py)
  rallies   rally model, short points from serve events                 (rally_model.py, pipeline.py)
  winners   winner, server end, fault candidate, end changes, decoder   (pipeline.py, match_decode.py)
Settings (JSON file, all optional):
  ballPass     true: all stages. false: no ball, no pose: only the rally boundaries (fast)
  threshold    rally probability threshold, default 0.5 (lower = more rallies)
  shortPoints  true: add short points (ace, double fault) from serve events
Output: <work>/result.json. Lines on stdout that start with "@" are for the app:
  @stage <name> start | @stage <name> done <seconds> | @stage <name> skip
  @progress <name> <0..1> | @result <file> | @error <text>
Usage: python detect.py --video <file> --work <folder> [--settings <json file>] [--force]
"""
import argparse, json, os, sys, time, traceback

MID = "job"
STAGE_S = {}


def say(*a):
    print(*a, flush=True)


def stage(name, outputs, fn, force=False):
    """Runs fn if one of the output files is missing. Records the run time."""
    if not force and all(os.path.exists(o) for o in outputs):
        say("@stage", name, "skip")
        return
    say("@stage", name, "start")
    t = time.time()
    fn()
    STAGE_S[name] = round(time.time() - t, 1)
    say("@stage", name, "done", STAGE_S[name])


def court_problem(track_file):
    """A text if the court model of the video is missing or not plausible, else None. Plausible: the corners
    of the doubles court are not far outside the frame, and the near baseline is below the far baseline. The
    court detector was made for a camera high behind the court; from a low camera it can give a wrong model
    with a good fit cost (seen on a video of 2026-09-13)."""
    import numpy as np
    import court_auto
    if not os.path.exists(track_file):
        return "No tennis court found in the video."
    ok = [w for w in json.load(open(track_file)) if w["ok"]]
    if not ok:
        return "No tennis court found in the video."
    good = 0
    for w in ok:
        q = court_auto.to_img(np.array(w["H"]), court_auto.QUAD)        # near left, near right, far right, far left
        inside = (q[:, 0] > -1.5 * 1920).all() and (q[:, 0] < 2.5 * 1920).all() and (q[:, 1] > -0.2 * 1080).all() and (q[:, 1] < 2.0 * 1080).all()
        good += bool(inside and min(q[0, 1], q[1, 1]) > max(q[2, 1], q[3, 1]) + 100 and q[1, 0] > q[0, 0] and q[2, 0] > q[3, 0])
    if good < 0.7 * len(ok):
        return "The court model is not plausible (the camera position is different from the lab matches)."
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--settings", default="")
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    work = os.path.abspath(a.work)
    os.makedirs(work, exist_ok=True)
    cfg = {"ballPass": True, "threshold": 0.5, "shortPoints": True}
    if a.settings:
        cfg.update(json.load(open(a.settings, encoding="utf-8")))
    times_file = os.path.join(work, "stage_times.json")
    if os.path.exists(times_file) and not a.force:
        STAGE_S.update(json.load(open(times_file)))
    t_all = time.time()

    import numpy as np
    import torch                                    # before ONNX Runtime (CUDA libraries)
    import lab
    lab.MATCHES[MID] = {"id": MID, "video": os.path.abspath(a.video)}
    lab.mdir = lambda m, _o=lab.mdir: work if m == MID else _o(m)
    W = lambda *p: os.path.join(work, *p)

    def save_times():
        json.dump(STAGE_S, open(times_file, "w"))

    # ---- media
    import prep, frame_times
    stage("media", [W("audio.flac"), W("kf_times.csv"), W("kf.done"), W("pts.npy")],
          lambda: (prep.prep(lab.MATCHES[MID], work), frame_times.load(MID)), a.force)
    save_times()
    kt = np.loadtxt(W("kf_times.csv"), ndmin=1)
    n = len(kt)
    step = float(np.median(np.diff(kt))) if n > 2 else 0.0
    if not 0.9 < step < 1.1:
        raise RuntimeError(f"The video has a keyframe each {step:.2f} s. The lab models need a keyframe each second.")

    # ---- audio and image features
    import feat_audio, feat_mel
    stage("audio", [W("feat_audio.npy"), W("feat_mel.npy")], lambda: (feat_audio.run(MID), feat_mel.run(MID)), a.force)

    def image():
        import timm, feat_dino
        model = timm.create_model("vit_small_patch14_dinov2.lvd142m", pretrained=True, img_size=(feat_dino.H, feat_dino.W)).cuda().eval()
        feat_dino.run(MID, model)
        del model
        torch.cuda.empty_cache()
    stage("image", [W("feat_dino.npy")], image, a.force)
    save_times()

    model_name, warnings = "base", []
    ball_ok = bool(cfg["ballPass"])
    if ball_ok:
        # ---- court
        def court():
            import court_track
            tr = court_track.run(MID)
            ok = [w for w in (tr or []) if w["ok"]]
            if ok:
                m = min(ok, key=lambda w: abs(w["t0"] - n / 2))
                json.dump({"H": m["H"], "chamfer_px": max(1.0, m["cost"])}, open(W("court.json"), "w"))
        stage("court", [W("court_track.json")], court, a.force)
        save_times()
        problem = court_problem(W("court_track.json"))
        if problem:
            # The ball stages need a correct court model. Without it, the result has only the rally boundaries.
            warnings.append(problem + " The result has only the rally boundaries: no winners, no serve faults, no short points.")
            say("@warning", warnings[-1])
            ball_ok = False

    if ball_ok:
        # ---- ball (the slow stage)
        import ball_wasb, ball_full, feat_wasb
        ball_full.WINDOWS[MID] = (0, n)
        blob = W(f"wasb_0_{n}_s0.35_k2.npy")

        def ball():
            pts = np.load(W("pts.npy"))
            if np.percentile(np.diff(pts), 90) < 0.02:               # a 60 fps video, as the lab matches
                ball_wasb.PROGRESS = lambda fr: say("@progress ball %.3f" % min(1.0, fr / (n * 60.0)))
                ball_wasb.run(MID, 0, n)
            else:
                # Another frame rate, or a mix of 30 and 60 fps (E23b). The ball detector learned frames that
                # are 1/30 s apart, and the flight code counts in frames of 1/60 s. So: decode at a constant
                # 30 fps, use each frame (stride 1), and put the detections on each second frame of a
                # 60 fps time line. pts.npy becomes that time line (the times of the video: pts_video.npy).
                ball_wasb.FPS = 30
                ball_wasb.PROGRESS = lambda fr: say("@progress ball %.3f" % min(1.0, fr / (n * 30.0)))
                d = ball_wasb.run(MID, 0, n, stride=1, save=False)
                d[:, 0] *= 2
                np.save(W("pts_video.npy"), pts)
                np.save(W("pts.npy"), np.arange(n * 60) / 60.0)
                np.save(blob, d)
            torch.cuda.empty_cache()
        stage("ball", [blob], ball, a.force)
        save_times()

        def flights():
            ball_full.run(MID)
            feat_wasb.run(MID)
            np.save(W("feat_wasbf.npy"), np.load(W("feat_wasb.npy"))[:, [4, 5, 6]])
        stage("flights", [W("fullflights_s0.35.json"), W("feat_wasbf.npy")], flights, a.force)
        save_times()

        # ---- pose
        def pose():
            import pose_extract
            from rtmlib import YOLOX, RTMPose
            det = YOLOX(pose_extract.DET, model_input_size=(640, 640), backend="onnxruntime", device="cuda")
            ps = RTMPose(pose_extract.POSE, model_input_size=(192, 256), backend="onnxruntime", device="cuda")
            pose_extract.run(MID, det, ps)
        stage("pose", [W("pose.npy")], pose, a.force)
        save_times()
        model_name = "ball"

    # ---- rallies
    import rally_model
    say("@stage rallies start")
    t = time.time()
    prob = rally_model.predict(MID, model_name)
    np.save(W(f"rally_prob_{model_name}.npy"), prob.astype(np.float32))
    hi = float(cfg["threshold"])
    lo = max(0.05, hi - 0.15)
    if ball_ok:
        import pipeline as pl, match_decode as md
        from serve_ball import serve_events
        full = json.load(open(W("fullflights_s0.35.json")))
        serves = serve_events(full["flights"], 0, n)
        seg = pl.rallies(MID, serves, prob, hi, lo, bool(cfg["shortPoints"]))
    else:
        seg = [(float(x), float(y)) for x, y in lab.segment(prob, smooth=1, hi=hi, lo=lo, min_len=2.0)]
    STAGE_S["rallies"] = round(time.time() - t, 1)
    say("@stage rallies done", STAGE_S["rallies"])

    # ---- winners
    out = []
    if ball_ok and seg:
        say("@stage winners start")
        t = time.time()
        md.PARAMS[MID] = {"margin": -0.15, "net_fwd": 2.0, "net_back": 4.0, "min_speed": 8}     # the value of 3 of the 4 lab matches
        rows = pl.evidence(MID, seg, full, serves)
        # start state: 0-0; player 1 = the player at the near end in the first rally; the server is not known
        st0 = {(0, 0, 0, 0, -1, srv, True, 0): 0.5 for srv in (1, 2)}
        # The player at the near end comes from the shirt colors (identity.py, E22). The decoder gets it as
        # evidence for each rally (E22b). Its own state is not usable for the names: right for 30 to 66 %.
        import identity
        near1 = identity.p1_near([r["color"] for r in rows])
        md.FLIP, md.P_ID = None, pl.ID_ABS
        states = []
        for i, r in enumerate(rows):
            r["pNull"] = pl.P_NULL_SHORT if r["prob"] < 0 else pl.p_null(r["prob"], r["shots"])
            r["pMiss"] = pl.p_miss(r["start"] - rows[i - 1]["end"]) if i else 0.02
        post = md.decode([r["pBall"] for r in rows], [(r["serverNear"], 0 if n1 else 1) for r, n1 in zip(rows, near1)], st0, states,
                         [r["pMiss"] for r in rows], [r["pNull"] for r in rows])
        for r, pp, p1_near in zip(rows, post, near1):
            near_wins = None if abs(pp - 0.5) < 0.02 else bool(pp > 0.5)
            out.append({
                "start": r["start"], "end": r["end"], "kind": "short" if r["prob"] < 0 else "rally",
                "prob": None if r["prob"] < 0 else r["prob"], "pNotPoint": r["pNull"],
                "p1Near": p1_near, "pNearWins": pp, "pBall": r["pBall"],
                "winner": None if near_wins is None else ("P1" if near_wins == p1_near else "P2"),
                "confidence": max(pp, 1 - pp),
                "server": None if r["serverNear"] is None else ("P1" if r["serverNear"] == p1_near else "P2"),
                "serverNear": r["serverNear"], "serveSpeedKmh": r["serveSpeed"], "fault": r["fault"],
                "shots": r["shots"], "lastShot": r["lastShot"], "endChangeBefore": r["flip"]})
        STAGE_S["winners"] = round(time.time() - t, 1)
        say("@stage winners done", STAGE_S["winners"])
    else:
        for x, y in seg:
            out.append({"start": x, "end": y, "kind": "rally", "prob": float(prob[int(x):int(y) + 1].mean())})

    save_times()
    res = {"version": 1, "video": os.path.abspath(a.video), "videoSeconds": n, "settings": cfg, "rallyModel": model_name,
           "stageSeconds": STAGE_S, "thisRunSeconds": round(time.time() - t_all, 1),
           "created": time.strftime("%Y-%m-%dT%H:%M:%S"), "warnings": warnings, "rallies": out}
    json.dump(res, open(W("result.json"), "w"), indent=1, default=float)
    say("@result", W("result.json"))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        traceback.print_exc()
        say("@error", f"{type(e).__name__}: {e}".replace("\n", " "))
        sys.exit(1)

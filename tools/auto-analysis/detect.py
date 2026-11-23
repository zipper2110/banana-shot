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
  @progress <name> <0..1> | @result <file> | @overlay <file> | @warning <text> | @error <text>
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


def add_views(work, rallies, n):
    """Gives each rally a "view": the second of a 1 fps keyframe that shows the two players, with their boxes in
    px of the keyframe ("near", "far": x1, y1, x2, y2). The app shows this picture for the question "who is this
    player?". The second is the one where the pose detector is most sure of the two persons. A rally with no such
    picture gets no view."""
    import numpy as np
    from server_end import near_far
    f = os.path.join(work, "pose.npy")
    if not os.path.exists(f):
        return
    pose = np.load(f)
    pose = pose[np.argsort(pose[:, 0], kind="stable")]
    st = np.searchsorted(pose[:, 0], np.arange(n + 1))
    for r in rallies:
        best = None
        for s in range(max(0, int(r["start"])), min(n, int(r["end"]) + 1)):
            near, far = near_far(pose[st[s]:st[s + 1]])
            if near is None or far is None:
                continue
            score = float(min(near[5], far[5]))
            if best is None or score > best[0]:
                best = (score, s, near, far)
        if best:
            box = lambda row: [round(float(v), 1) for v in row[1:5]]
            r["view"] = {"second": int(best[1]), "near": box(best[2]), "far": box(best[3])}


def overlay_poses(work, min_box=0.3, min_point=0.3):
    """The body poses for overlay.json: one row for each person on each 1 fps keyframe,
    [t, x0, y0, ... x16, y16] with the 17 COCO keypoints in px. A keypoint with a low confidence is -1, -1."""
    import numpy as np
    f = os.path.join(work, "pose.npy")
    if not os.path.exists(f):
        return []
    kt = np.loadtxt(os.path.join(work, "kf_times.csv"), ndmin=1)
    # A 1 fps picture (ffmpeg "fps=1:round=down") is the LAST video frame of its second, and not the first one:
    # picture k shows the video at about k + 1 s minus one frame. The overlay needs the true time.
    pts_file = os.path.join(work, "pts.npy")
    if os.path.exists(os.path.join(work, "kf.fps1")) and os.path.exists(pts_file):
        pts = np.load(pts_file)
        last = np.clip(np.searchsorted(pts, kt + 1 - 1e-4) - 1, 0, len(pts) - 1)
        kt = np.maximum(kt, pts[last])
    rows = []
    for r in np.load(f):
        k = int(r[0])
        if r[5] < min_box or k >= len(kt):
            continue
        pts = r[6:].reshape(17, 3)
        row = [round(float(kt[k]), 3)]
        for x, y, c in pts:
            row += [int(round(float(x))), int(round(float(y)))] if c >= min_point else [-1, -1]
        rows.append(row)
    rows.sort(key=lambda row: row[0])
    return rows


def write_overlay(work, n, full, serves):
    """Writes <work>/overlay.json: the data that the app draws on the preview (decision 19 of
    app-integration.md). Image points are px of a 1920 x 1080 frame. Times are seconds of the video.
      court    for each 60 s window with a trusted model: the court lines and the net line
      ball     the best detection of each frame: [t, x, y]
      flights  the fitted flights: time range, landing point in the image, speed at the start, direction, start
               point and landing point on the court in m (x across, y along, 0 at the net, + = far end), net height.
               E17d: landT, land, landM are the bounce event of the flight if it has one. If not, they are the
               landing of the fit, or null if that landing is before the last detection of the flight. Also: ev0, ev1 (index in events of the event at the start and at the end, or null), bounceEv (= ev1
               if that event is a bounce, else null), landSigma (m, 1-sigma error of an extrapolated landing),
               curve (the fitted path in the image: [t, x, y], one sample each 1/30 s from t0 to the bounce or,
               with no bounce, to the landing of the fit; the last sample is at that end), curveObsEnd (the time
               of the last detection of the flight: the curve after it is an extrapolation)
      events   E17d (ball_bounce.py), sorted by time: t, kind ("bounce", "hit", "other", "end"), px (ball center),
               court (m, or null), src ("vertex", "kink", "fit"), conf (1-sigma error of court in m, or null),
               and for "end" (an extrapolated landing): tLast
      serves   the serve events: time, end of the server, kind, speed
      poses    the body poses of the persons on the 1 fps keyframes (overlay_poses)"""
    import numpy as np
    import court_auto, ball_full, ball_3d
    W = lambda *p: os.path.join(work, *p)
    track = [w for w in json.load(open(W("court_track.json"))) if w["ok"]]
    court = []
    for w in track:
        H = np.array(w["H"])
        lines = [court_auto.to_img(H, [a, b]).round(1).ravel().tolist() for a, b in court_auto.SEGS]
        net = court_auto.to_img(H, [(-court_auto.DW, 0), (court_auto.DW, 0)]).round(1).ravel().tolist()
        court.append({"t0": w["t0"], "t1": w["t1"], "lines": lines, "net": net})

    def H_at(t):
        return np.array(min(track, key=lambda w: abs((w["t0"] + w["t1"]) / 2 - t))["H"])
    d = np.load(W(f"wasb_0_{n}_s0.35_k2.npy"))
    d = d[np.argsort(d[:, 0], kind="stable")]
    top = np.ones(len(d), bool); top[1:] = d[1:, 0] != d[:-1, 0]          # the first blob of a frame has the best score
    d = d[top]
    tt = ball_full.clock(MID, 0)                                         # the true time of each frame (E17d)
    t = tt[np.minimum(d[:, 0].astype(int), len(tt) - 1)]
    ball = [[round(float(a), 3), round(float(x), 1), round(float(y), 1)] for a, x, y in zip(t, d[:, 1], d[:, 2])]
    events = full.get("events", [])

    def curve(f, land_t):
        """The fitted path of a flight in the image, from t0 to its bounce or its landing."""
        be = f.get("bounceEv")
        end = events[be]["t"] if be is not None else (land_t if land_t is not None else f["t1"])
        end = min(max(end, f["t0"]), f["t0"] + 4.0)
        ts = np.append(np.arange(0.0, end - f["t0"] - 1e-6, 1 / 30.0), end - f["t0"])
        P = ball_3d.camera(H_at(f["t0"]))[0]
        fit = f.get("evFit", f)
        xy = ball_3d.project(P, ball_3d.simulate(np.array(fit["p0"], float), np.array(fit["v0"], float), ts))
        return [[round(float(f["t0"] + a), 3), round(float(x), 1), round(float(y), 1)] for a, (x, y) in zip(ts, xy)
                if np.isfinite(x) and np.isfinite(y)]
    flights = []
    for f in full["flights"]:
        # A flight that does not come down has no landing: its landing time is NaN, and JSON has no NaN.
        land_t = f.get("landT")
        if land_t is not None and not np.isfinite(land_t):
            land_t = None
        land_m = f.get("land")
        e1 = events[f["ev1"]] if f.get("ev1") is not None else None
        if e1 is not None and e1["kind"] == "bounce":                    # E17d: the flight ends at a bounce event: that is its landing
            land_t, land_m = e1["t"], e1["court"]
        elif e1 is not None and e1["kind"] == "end":                     # E17d: the landing of the event fit, or none (the fit comes down before the last detection)
            land_t, land_m = (e1["t"], e1["court"]) if e1["court"] is not None else (None, None)
        land = court_auto.to_img(H_at(f["t0"]), [land_m])[0] if land_m and land_t is not None else None
        flights.append({"t0": round(f["t0"], 3), "t1": round(f["t1"], 3), "landT": None if land_t is None else round(float(land_t), 3),
                        "land": None if land is None else [round(float(land[0]), 1), round(float(land[1]), 1)],
                        "speedKmh": round(3.6 * float(np.linalg.norm(f["v0"])), 1), "good": bool(f["n"] >= 8 and f["rms"] < 5),
                        "toFar": bool(f["v0"][1] > 0), "start": [round(float(v), 2) for v in f["p0"]],
                        "landM": None if land is None else [round(float(v), 2) for v in land_m],
                        "netZ": round(float(f["netZ"]), 2) if f.get("netZ") is not None and np.isfinite(f["netZ"]) else None})
        if "events" in full:
            sg = f.get("landSigma")
            flights[-1].update(ev0=f.get("ev0"), ev1=f.get("ev1"), bounceEv=f.get("bounceEv"),
                               landSigma=round(float(sg), 2) if sg is not None and np.isfinite(sg) else None,
                               curve=curve(f, land_t), curveObsEnd=round(f["t1"], 3))
    ev = []
    for e in events:
        o = {"t": round(float(e["t"]), 3), "kind": e["kind"], "px": [round(float(v), 1) for v in e["px"]],
             "court": None if e["court"] is None else [round(float(v), 2) for v in e["court"]], "src": e["src"],
             "conf": None if e["conf"] is None or not np.isfinite(e["conf"]) else round(float(e["conf"]), 2)}
        if "tLast" in e:
            o["tLast"] = round(float(e["tLast"]), 3)
        ev.append(o)
    sv = [{"t": round(float(e[0]), 3), "near": bool(e[1]), "kind": e[2], "speedKmh": round(3.6 * float(np.linalg.norm(e[3]["v0"])), 1)}
          for e in serves]
    json.dump({"version": 2 if "events" in full else 1, "width": 1920, "height": 1080, "court": court, "ball": ball, "flights": flights,
               "events": ev, "serves": sv, "poses": overlay_poses(work)},
              open(W("overlay.json"), "w"), default=float)
    say("@overlay", W("overlay.json"))


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
    import platform_cfg
    import torch                                    # before ONNX Runtime (CUDA libraries)
    import lab
    say(platform_cfg.summary())
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
        model = platform_cfg.to_device(timm.create_model("vit_small_patch14_dinov2.lvd142m", pretrained=True, img_size=(feat_dino.H, feat_dino.W))).eval()
        feat_dino.run(MID, model)
        del model
        platform_cfg.empty_cache()
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
            platform_cfg.empty_cache()
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
            dev = platform_cfg.ort_device()
            det = YOLOX(platform_cfg.pose_model(pose_extract.DET), model_input_size=(640, 640), backend="onnxruntime", device=dev)
            ps = RTMPose(platform_cfg.pose_model(pose_extract.POSE), model_input_size=(192, 256), backend="onnxruntime", device=dev)
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

    if ball_ok:
        try:
            add_views(work, out, n)
        except Exception as e:        # with no view, the app shows the picture of the rally start
            traceback.print_exc()
            say("@warning", f"No player pictures: {type(e).__name__}: {e}")
        try:
            write_overlay(work, n, full, serves)
        except Exception as e:        # the overlay is a debug view: the result is more important
            traceback.print_exc()
            say("@warning", f"No overlay data: {type(e).__name__}: {e}")
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

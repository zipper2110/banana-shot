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
  rallies   rally model, short points from serve events                 (rally_model.py, chain.py)
  winners   winner, server end, fault candidate, end changes, decoder   (chain.py, match_decode.py)
Settings (JSON file, all optional):
  ballPass     true: all stages. false: no ball, no pose: only the rally boundaries (fast)
  threshold    rally probability threshold, default 0.5 (lower = more rallies)
  shortPoints  true: add short points (ace, double fault) from serve events. Default false: only about 30 % of them
               are real points (E61)
  ballPasses, ballWeights, chain, endModel, courtTrack: see main()
The run reads no lab environment variable (LAB_ENV): a setting replaces each one (G12).
Cache (G12): video.json holds the identity of the video of the work folder; a work folder of another video stops
the run. The ball file name has the weights, the passes and a key of the code; stage_keys.json holds the key of
the inputs of each keyed stage, and a stage runs again when its key changes.
Output: <work>/result.json. "warnings" lists each fallback of the run (G13). An exception after the rally stage
gives a partial result ("partial": true, the rallies that are ready, a warning) and no error. Lines on stdout that
start with "@" are for the app:
  @stage <name> start | @stage <name> done <seconds> | @stage <name> skip
  @progress <name> <0..1> | @result <file> | @overlay <file> | @warning <text> | @error <text>
Adoption of an old ball file (G12, E52): the ball stage takes hours. A ball file of a run before G12 has an old name,
so a new run makes it again. The operator can state the weights and passes of that file:
  python detect.py --adopt-ball <old file> --weights <file in the weights folder> --passes <dim or ""> --video <file> --work <folder>
The command checks the video identity, the time line and the frame range of the file, copies it to the
new name, and writes the adoption to stage_keys.json ("ballAdopted"). The old file stays. A later run gives a warning
that the ball candidates come from the adopted file. There is no automatic reuse.
Usage: python detect.py --video <file> --work <folder> [--settings <json file>] [--force]
"""
import argparse, json, os, sys, time, traceback

MID = "job"
STAGE_S = {}
STAGE_KEYS = {}           # G12: the key of the inputs of each keyed stage (stage_keys.json of the work folder)
KEYS_FILE = None
HERE = os.path.dirname(os.path.abspath(__file__))
# G12: the lab switches of the chain. An app run does not read them from the environment: an inherited value made
# a result with no record of it. The settings ballPasses, ballWeights, chain, endModel and courtTrack replace the
# ones that the app can change; the run computes the ball file tag (BLOB_TAG); the others keep the lab default.
LAB_ENV = ("BALL_PASSES", "BALL_WEIGHTS", "CHAIN", "END_MODEL", "COURT_TRACK", "BLOB_TAG", "BALL_TAG", "EV_TAG",
           "FULLFLIGHTS", "FPS_ADAPT", "SERVE_FILTER", "FAR_FAULT_M", "SELECT_TRIM", "TURN", "LEN_TIME", "OUT_LINE",
           "NET_NEAR", "CONT_GAP", "CONT_M", "CHAIN_NET_STOP", "SERVE_PRIOR_KEEP", "SERVE_PRIOR_WIDE")    # and each CHAIN_* switch of shot_chain.py


def say(*a):
    print(*a, flush=True)


def stage(name, outputs, fn, force=False, key=None):
    """Runs fn if one of the output files is missing, or if key (the key of its inputs) is not the key of the last
    run (G12). Records the run time."""
    if not force and (key is None or STAGE_KEYS.get(name) == key) and all(os.path.exists(o) for o in outputs):
        say("@stage", name, "skip")
        return
    say("@stage", name, "start")
    t = time.time()
    fn()
    STAGE_S[name] = round(time.time() - t, 1)
    if key is not None:
        STAGE_KEYS[name] = key
        json.dump(STAGE_KEYS, open(KEYS_FILE, "w"), indent=1)
    say("@stage", name, "done", STAGE_S[name])


def content_key(*parts, files=()):
    """8 hex digits of SHA-1 over the parts and the bytes of the files (line ends of a text file do not count)."""
    import hashlib
    h = hashlib.sha1()
    for p in parts:
        h.update(repr(p).encode() + b"\0")
    for f in files:
        b = open(f, "rb").read() if os.path.exists(f) else b"-"
        h.update(b.replace(b"\r\n", b"\n") + b"\0")
    return h.hexdigest()[:8]


def video_id(path):
    """The identity of a video file: SHA-1 of its size (decimal text), its first MiB and its last MiB. The app names
    the work folder with the first 8 hex digits (DetectRunner.workName)."""
    import hashlib
    size = os.path.getsize(path)
    h = hashlib.sha1(str(size).encode())
    with open(path, "rb") as f:
        h.update(f.read(1 << 20))
        if size > 1 << 20:
            f.seek(max(1 << 20, size - (1 << 20)))
            h.update(f.read(1 << 20))
    return h.hexdigest()


def check_video(work, video):
    """Stops the run if the work folder has the files of another video. A work folder with no record gets one."""
    vid, f = video_id(video), os.path.join(work, "video.json")
    if os.path.exists(f):
        old = json.load(open(f))
        if old.get("id") != vid:
            raise RuntimeError(f"The work folder {work} has the files of another video ({old.get('video')}). "
                               "Use another work folder.")
    else:
        json.dump({"id": vid, "video": os.path.abspath(video), "size": os.path.getsize(video)}, open(f, "w"), indent=1)
    return vid


# Item 13 (decision 90f2389d). The stages media, audio, court, court2 and flights: the key has the import closure of
# the entry modules (code_closure.files). The ball and pose stages (1 h and more): a hand list, and test_code_closure
# fails when their closure gets a module that is not in the list or on its "no effect" list.
FLIGHTS_ENTRIES = ("ball_full", "net_events", "feat_wasb")
STAGE_ENTRIES = {"media": ("prep", "frame_times"), "audio": ("feat_audio", "feat_mel"), "court": ("court_track",),
                 "court2": ("court_refine",), "flights": FLIGHTS_ENTRIES, "netband": ("net_band",)}
BALL_CODE_OLD = ("ball_wasb.py", "wasb_hrnet.py")          # the ball tag before item 13 (adopt_keys)
BALL_CODE = BALL_CODE_OLD + ("burst_ball.py", "cuda_scale.py", "platform_cfg.py")
POSE_CODE = ("pose_extract.py", "platform_cfg.py")         # pose_extract.py has the URLs of the pose models
# Item 6 (b): level 1 of the net band (net_band_mask.py: the masks of the moved pixels, a decode of the candidate
# windows, about 40 % of a video). A hand list: ball_wasb and its imports are in the ball tag.
NETMASK_CODE = ("net_band_mask.py",)
# The code files of the flights key before item 13 (a hand list; adopt_keys)
FLIGHTS_CODE_OLD = ("ball_full.py", "ball_3d.py", "ball_bounce.py", "ball_events.py", "ball_tracks.py", "net_events.py",
                    "net_lab.py", "feat_wasb.py", "frame_times.py", "court_refine.py")


def code_key(name, *parts, files=()):
    """The key of a stage with an import-closure key: the parts, the input files, and the code of STAGE_ENTRIES[name]."""
    import code_closure
    return content_key(*parts, files=list(files) + code_closure.files(*STAGE_ENTRIES[name]))


def pose_key():
    return content_key(files=[os.path.join(HERE, f) for f in POSE_CODE])


def netmask_key(tag):
    """The key of net_band_mask.npz: the ball tag (the pictures and the time line of the ball stage) and NETMASK_CODE."""
    return tag + "_" + content_key(files=[os.path.join(HERE, f) for f in NETMASK_CODE])


def ball_tag(weights, passes, n, work, code=BALL_CODE):
    """The tag of the ball file name (lab.BLOB_TAG): the weights, the passes, and a key of the inputs: the detector
    code, the weights bytes, the court model (the crop), the decode path and n. New weights or new code give a new
    file (G12)."""
    import platform_cfg
    return "_" + weights.split(".")[0] + ("_" + passes if passes else "") + "_" + content_key(
        weights, passes, n, platform_cfg.decode(), files=[os.path.join(HERE, f) for f in code] + [
                                                       os.path.join(platform_cfg.weights_dir(), weights), os.path.join(work, "court.json")])


def adopt_ball(work, video, old, weights, passes):
    """E52: gives an old ball file (a run before G12) the name of the new cache key. The operator states the weights
    and the passes of the old file; the command checks what it can check. Returns the record of the adoption.
    Raises RuntimeError when a check fails. The new name is a copy; the old file stays."""
    import re, shutil
    import numpy as np
    import platform_cfg, lab
    passes = (passes or "").replace(",", "-").replace("+", "-")
    old = old if os.path.isabs(old) else os.path.join(work, old)
    if not os.path.isfile(old):
        raise RuntimeError(f"No ball file {old}.")
    for f in ("pts.npy", "kf_times.csv", "court.json"):
        if not os.path.exists(os.path.join(work, f)):
            raise RuntimeError(f"The work folder has no {f}: the media and court stages of the old run are not there.")
    if not os.path.exists(os.path.join(platform_cfg.weights_dir(), weights)):
        raise RuntimeError(f"The weights {weights} are not in {platform_cfg.weights_dir()}.")
    m = re.fullmatch(r"wasb_0_(\d+)_s0\.35.*_k2\.npy", os.path.basename(old))
    if not m:
        raise RuntimeError(f"{os.path.basename(old)} is not a ball file of a full run (wasb_0_<seconds>_s0.35..._k2.npy).")
    n_old, n = int(m.group(1)), lab.video_seconds(work)
    pts = np.load(os.path.join(work, "pts.npy"))
    if np.percentile(np.diff(pts), 90) >= 0.02 and not os.path.exists(os.path.join(work, "pts_video.npy")):
        # A video that is not 60 fps: the ball stage writes the 60 fps time line (pts.npy) and keeps the video times
        # in pts_video.npy. Without pts_video.npy the old ball stage did not end.
        raise RuntimeError("The video is not 60 fps and the work folder has no pts_video.npy: the old ball stage did not end.")
    d = np.load(old, mmap_mode="r")
    if d.ndim != 2 or d.shape[1] != 5 or len(d) == 0:
        raise RuntimeError(f"{os.path.basename(old)} has the shape {d.shape}, not (rows, 5).")
    fr = np.asarray(d[:, 0])
    if (np.diff(fr) < 0).any():
        raise RuntimeError(f"The frames of {os.path.basename(old)} are not in order.")
    last = float(fr[-1]) / 60.0                    # the detections are on the 60 fps time line
    if last > n + 1.0:
        raise RuntimeError(f"The last detection is at {last:.0f} s, after the end of the video ({n} s): another video.")
    if last < 0.8 * n:
        raise RuntimeError(f"The last detection is at {last:.0f} s of {n} s: the old ball stage did not end.")
    # The video must be the video of the work folder: its length against the frame times of the folder, then its
    # identity (video.json; a folder of a run before G12 has none and gets this video).
    import subprocess
    r = subprocess.run([platform_cfg.tool("ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                        video], capture_output=True, text=True)
    dur = float(r.stdout.strip() or "nan")
    vt = np.load(os.path.join(work, "pts_video.npy")) if os.path.exists(os.path.join(work, "pts_video.npy")) else pts
    if not abs(dur - float(vt[-1])) < 2.0:
        raise RuntimeError(f"The video is {dur:.1f} s long, but the frames of the work folder end at {float(vt[-1]):.1f} s: another video.")
    vid = check_video(work, video)
    lab.BLOB_TAG = ball_tag(weights, passes, n, work)
    new = lab.blob_file(work, 0, n)
    if os.path.exists(new):
        raise RuntimeError(f"{os.path.basename(new)} is already there: nothing to adopt.")
    # A copy, not a hard link: np.save truncates an existing path in place, so a later ball run that writes the new
    # name would also change the old file through a link (the lab scripts read the old file).
    shutil.copy2(old, new)
    rec = {"file": os.path.basename(new), "from": os.path.basename(old), "weights": weights, "passes": passes,
           "statedBy": "operator", "videoId": vid, "seconds": n, "oldSeconds": n_old,
           "lastDetectionS": round(last, 1), "time": time.strftime("%Y-%m-%dT%H:%M:%S")}
    keys_file = os.path.join(work, "stage_keys.json")
    keys = json.load(open(keys_file)) if os.path.exists(keys_file) else {}
    keys["ballAdopted"] = rec
    json.dump(keys, open(keys_file, "w"), indent=1)
    return rec



def git_changes(files, since):
    """The commits of this folder after the time since (s, a file time) that change one of the files, as text lines;
    None when git does not answer."""
    import subprocess
    try:
        r = subprocess.run(["git", "log", "--format=%h %cI %s", "--since=" + time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(since)),
                            "--"] + [os.path.join(HERE, f) for f in files], cwd=HERE, capture_output=True, text=True)
    except OSError:
        return None
    return None if r.returncode else [x for x in r.stdout.splitlines() if x]


def adopt_keys(work, weights, passes, n, court_track=""):
    """Item 13 (decision 90f2389d): a ball file of the tag before item 13 (BALL_CODE_OLD) gets the name of the new tag,
    and an existing pose.npy gets the pose key, only when git shows no change of the added code files after the file
    was made. The flights get the new key when their recorded key is the key before item 13 (the old ball tag, the old
    hand list, the court files of now), the ball file is adopted or current, and git shows no change of the files of
    the import closure after fullflights_s0.35.json was made. The media, audio, court and court2 stages had no key
    before item 13: they get the key of now when they have no recorded key, their outputs exist, and git shows no
    change of the files of their import closure after the newest output was made. Else the file is stale: the next
    run makes it again. Returns {"ball": ..., "pose": ..., "media": ..., "audio": ..., "court": ..., "court2": ...,
    "flights": ...}."""
    import shutil
    import lab
    out, tag = {}, lab.BLOB_TAG
    try:
        old_tag = lab.BLOB_TAG = ball_tag(weights, passes, n, work, BALL_CODE_OLD)
        old = lab.blob_file(work, 0, n)
        new_tag = lab.BLOB_TAG = ball_tag(weights, passes, n, work)
        new = lab.blob_file(work, 0, n)
    finally:
        lab.BLOB_TAG = tag
    added = [f for f in BALL_CODE if f not in BALL_CODE_OLD]
    if os.path.exists(new):
        out["ball"] = {"state": "current", "file": os.path.basename(new)}
    elif not os.path.exists(old):
        out["ball"] = {"state": "none", "file": os.path.basename(old)}
    else:
        ch = git_changes(added, os.path.getmtime(old))
        if ch is None or ch:
            out["ball"] = {"state": "stale", "file": os.path.basename(old), "changes": ch if ch else "no git"}
        else:
            shutil.copy2(old, new)                 # a copy, not a link (E52)
            out["ball"] = {"state": "adopted", "file": os.path.basename(new), "from": os.path.basename(old)}
            rec = STAGE_KEYS.get("ballAdopted")
            if rec and rec.get("file") == os.path.basename(old):
                rec["file"] = os.path.basename(new)  # the E52 warning stays with the file under its new name
    pose = os.path.join(work, "pose.npy")
    if STAGE_KEYS.get("pose") == pose_key() and os.path.exists(pose):
        out["pose"] = {"state": "current"}
    elif not os.path.exists(pose):
        out["pose"] = {"state": "none"}
    else:
        ch = git_changes(POSE_CODE, os.path.getmtime(pose))
        if ch is None or ch:
            out["pose"] = {"state": "stale", "changes": ch if ch else "no git"}
        else:
            STAGE_KEYS["pose"] = pose_key()
            out["pose"] = {"state": "adopted"}
    import code_closure
    W = lambda f: os.path.join(work, f)
    stages = {"media": ([W("audio.flac"), W("kf_times.csv"), W("kf.done"), W("pts.npy")], code_key("media")),
              "audio": ([W("feat_audio.npy"), W("feat_mel.npy")], code_key("audio")),
              "court": ([W("court_track.json")], code_key("court")),
              "court2": ([W("court_track_refined.json")], code_key("court2", files=[W("court_track.json")]))}
    for name, (outputs, key) in stages.items():
        if STAGE_KEYS.get(name) == key:
            out[name] = {"state": "current"}
        elif not all(os.path.exists(o) for o in outputs):
            out[name] = {"state": "none"}
        elif name in STAGE_KEYS:
            out[name] = {"state": "stale", "changes": "the recorded key is not the key of now"}
        else:
            ch = git_changes([os.path.basename(f) for f in code_closure.files(*STAGE_ENTRIES[name])],
                             max(os.path.getmtime(o) for o in outputs))
            if ch is None or ch:
                out[name] = {"state": "stale", "changes": ch if ch else "no git"}
            else:
                STAGE_KEYS[name] = key
                out[name] = {"state": "adopted"}
    court = [W("court_track.json"), W("court_track_refined.json")]
    flights_old = old_tag + "_" + content_key(court_track, files=court + [os.path.join(HERE, f) for f in FLIGHTS_CODE_OLD])
    flights_new = new_tag + "_" + code_key("flights", court_track, files=court)
    full = W("fullflights_s0.35.json")
    if STAGE_KEYS.get("flights") == flights_new:
        out["flights"] = {"state": "current"}
    elif not os.path.exists(full) or STAGE_KEYS.get("flights") != flights_old:
        out["flights"] = {"state": "none" if not os.path.exists(full) else "stale",
                          "changes": None if not os.path.exists(full) else "the recorded key is not the key before item 13"}
    elif out["ball"]["state"] not in ("adopted", "current"):
        out["flights"] = {"state": "stale", "changes": "the ball file is not adopted"}
    else:
        ch = git_changes([os.path.basename(f) for f in code_closure.files(*FLIGHTS_ENTRIES)], os.path.getmtime(full))
        if ch is None or ch:
            out["flights"] = {"state": "stale", "changes": ch if ch else "no git"}
        else:
            STAGE_KEYS["flights"] = flights_new
            out["flights"] = {"state": "adopted"}
    STAGE_KEYS["keysAdopted"] = dict(out, time=time.strftime("%Y-%m-%dT%H:%M:%S"))
    json.dump(STAGE_KEYS, open(KEYS_FILE, "w"), indent=1)
    return out

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
    """Gives each rally a "view": a 1 fps picture that shows the two players, with their boxes in px of the picture
    ("near", "far": x1, y1, x2, y2). "second" is the second of the video, "picture" is the number k of the picture
    file kf/<k+1>.jpg (G2: k is not the second). The app shows this picture for the question "who is this player?".
    The second is the one where the pose detector is most sure of the two persons. A rally with no such picture
    gets no view."""
    import lab
    from server_end import near_far
    if not os.path.exists(os.path.join(work, "pose.npy")):
        return
    pose = lab.pose_by_second(work, n)
    kf = lab.kf_of_second(work, n)
    for r in rallies:
        best = None
        for s in range(max(0, int(r["start"])), min(n, int(r["end"]) + 1)):
            near, far = near_far(pose[s])
            if near is None or far is None:
                continue
            score = float(min(near[5], far[5]))
            if best is None or score > best[0]:
                best = (score, s, near, far)
        if best:
            box = lambda row: [round(float(v), 1) for v in row[1:5]]
            r["view"] = {"second": int(best[1]), "picture": int(kf[best[1]]), "near": box(best[2]), "far": box(best[3])}


def overlay_poses(work, min_box=0.3, min_point=0.3):
    """The body poses for overlay.json: one row for each person on each 1 fps keyframe,
    [t, x0, y0, ... x16, y16] with the 17 COCO keypoints in px. A keypoint with a low confidence is -1, -1."""
    import numpy as np
    import lab
    f = os.path.join(work, "pose.npy")
    if not os.path.exists(f):
        return []
    kt = lab.kf_times(work)                # the true time of each picture (also for a video with kf.fps1)
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


def load_net_events(work):
    """The net events of the video (net_events.py, E16f): the moments at which the ball touches the net."""
    import net_events
    path = os.path.join(work, "net_events.json")
    if not os.path.exists(path):
        net_events.run_folder(work)
    return json.load(open(path))["events"]


def add_net_hits(out, net):
    """Adds "netHit" to each rally that ends with a ball into the net: time, end of the hitter, confidence of
    the touch and of the hitter, and "serve" (the net ball came less than 3 s after the rally start).
    The score model does not use it (E16g: the full chain became worse). It is a fact for the user."""
    import net_events
    for r in out:
        h = net_events.winner_hint(net, r["end"], t_start=r["start"])
        if h:
            e = h["event"]
            r["netHit"] = {"t": e["t"], "hitterNear": bool(e["toFar"]), "conf": e["conf"], "dirConf": e["dirConf"],
                           "serve": bool(h["sinceStart"] is not None and h["sinceStart"] < 3.0)}


BALL_NEAR_PX = 25.0        # a detection is the ball of a flight if it is this near to the fitted path


BALL_WEIGHTS = "ball_e38.pth"   # the default weights of the ball detector (E38)


def write_overlay(work, n, full, serves, net_ev=()):
    """Writes <work>/overlay.json: the data that the app draws on the preview (decision 19 of
    app-integration.md). Image points are px of a 1920 x 1080 frame. Times are seconds of the video.
      court    for each 60 s window with a trusted model: the court lines and the net line
      ball     the ball of the flights: [t, x, y]. For each frame in the time range of a flight, the detection
               nearest to the fitted path, if it is not farther than BALL_NEAR_PX. The detections that no flight
               uses are not here: most of them are false (lamps, shoes, a ball on the next court).
      flights  the fitted flights: time range, landing point in the image, speed at the start, direction, start
               point and landing point on the court in m (x across, y along, 0 at the net, + = far end), net height.
               E17d: landT, land, landM are the bounce event of the flight if it has one. If not, they are the
               landing of the fit, or null if that landing is before the last detection of the flight. Also: ev0, ev1 (index in events of the event at the start and at the end, or null), bounceEv (= ev1
               if that event is a bounce, else null), landSigma (m, 1-sigma error of an extrapolated landing),
               curve (the fitted path in the image: [t, x, y, court x, court y], one sample each 1/30 s from t0 to the bounce or,
               with no bounce, to the landing of the fit; the last sample is at that end), curveObsEnd (the time
               of the last detection of the flight: the curve after it is an extrapolation)
      events   E17d (ball_bounce.py), sorted by time: t, kind ("bounce", "hit", "other", "end"), px (ball center),
               court (m, or null), src ("vertex", "kink", "fit"), conf (1-sigma error of court in m, or null),
               and for "end" (an extrapolated landing): tLast. After these events come the net events (E16f,
               net_events.py): kind "net" (the ball stays at the net) or "cord" (the ball goes over), px, toFar
               (true = the hitter is at the near end), conf (0 to 1, 0.7 or more = the ball was seen to drop at
               the net), src. The event indices of the flights do not point to them.
      serves   the serve events: time, end of the server, kind, speed
      poses    the body poses of the persons on the 1 fps keyframes (overlay_poses)"""
    import numpy as np
    import court_auto, ball_full, ball_3d, ball_events, lab
    import shot_chain as sc    # G7 (d): "used" = the one filter of the shot chain (usable and pos_ok)
    W = lambda *p: os.path.join(work, *p)
    track = ball_events.court_windows(work)
    court = []
    for w in track:
        H = np.array(w.get("ground", w)["H"])                             # the refined model, if the video uses it (E26f)
        lines = [court_auto.to_img(H, [a, b]).round(1).ravel().tolist() for a, b in court_auto.SEGS]
        net = court_auto.to_img(H, [(-court_auto.DW, 0), (court_auto.DW, 0)]).round(1).ravel().tolist()
        court.append({"t0": w["t0"], "t1": w["t1"], "lines": lines, "net": net})

    def H_at(t):                                                         # the first model: the 3D camera of the flights
        return np.array(min(track, key=lambda w: abs((w["t0"] + w["t1"]) / 2 - t))["H"])

    def G_at(t):                                                         # the model of the ground positions
        w = min(track, key=lambda w: abs((w["t0"] + w["t1"]) / 2 - t))
        return np.array((w.get("ground", w) if full.get("groundFix") else w)["H"])
    d = np.load(lab.blob_file(work, 0, n))
    d = d[np.argsort(d[:, 0], kind="stable")]
    tt = ball_full.clock(MID, 0)                                         # the true time of each frame (E17d)
    bt = tt[np.minimum(d[:, 0].astype(int), len(tt) - 1)]
    events = full.get("events", [])

    def curve(f, land_t):
        """The fitted path of a flight in the image, from t0 to its bounce or its landing."""
        be = f.get("bounceEv")
        end = events[be]["t"] if be is not None else (land_t if land_t is not None else f["t1"])
        end = min(max(end, f["t0"]), f["t0"] + 4.0)
        ts = np.append(np.arange(0.0, end - f["t0"] - 1e-6, 1 / 30.0), end - f["t0"])
        P = ball_3d.camera(H_at(f["t0"]))[0]
        fit = f.get("evFit", f)
        X = ball_3d.simulate(np.array(fit["p0"], float), np.array(fit["v0"], float), ts)
        h = (P @ np.concatenate([X, np.ones((len(X), 1))], 1).T).T
        out = []
        for a, (u, v, w), q in zip(ts, h, X):
            # The path stops where the fit goes behind the camera or far out of the picture. A bad fit of a short
            # flight can pass the plane of the camera: there the projection goes to infinity and comes back from the
            # other side, and the overlay shows lines across the full picture.
            if w * h[0][2] <= 0 or not (-1920 < u / w < 2 * 1920 and -1080 < v / w < 2 * 1080):
                break
            # The place of the ball over the court in m (x across, y along) is for the ball on the court diagram.
            out.append([round(float(f["t0"] + a), 3), round(float(u / w), 1), round(float(v / w), 1), round(float(q[0]), 2), round(float(q[1]), 2)])
        return out
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
        land = court_auto.to_img(G_at(f["t0"]), [land_m])[0] if land_m and land_t is not None else None
        flights.append({"t0": round(f["t0"], 3), "t1": round(f["t1"], 3), "landT": None if land_t is None else round(float(land_t), 3),
                        "land": None if land is None else [round(float(land[0]), 1), round(float(land[1]), 1)],
                        "speedKmh": round(3.6 * float(np.linalg.norm(f["v0"])), 1), "good": bool(f["n"] >= 8 and f["rms"] < 5),
                        # "good": the app draws the flight. "used": the decision can use the flight (G7 d; the app does not read it yet)
                        "used": bool(sc.usable(f) and sc.pos_ok(f)),
                        "toFar": bool(f["v0"][1] > 0), "start": [round(float(v), 2) for v in f["p0"]],
                        "landM": None if land is None else [round(float(v), 2) for v in land_m],
                        "netZ": round(float(f["netZ"]), 2) if f.get("netZ") is not None and np.isfinite(f["netZ"]) else None})
        if "events" in full:
            sg = f.get("landSigma")
            flights[-1].update(ev0=f.get("ev0"), ev1=f.get("ev1"), bounceEv=f.get("bounceEv"),
                               landSigma=round(float(sg), 2) if sg is not None and np.isfinite(sg) else None,
                               curve=curve(f, land_t), curveObsEnd=round(f["t1"], 3))
    # The ball: for each frame, the detection nearest to the path of a flight of that time.
    best = {}
    for f in flights:
        c = np.array(f.get("curve") or np.zeros((0, 3)))
        if len(c) < 2:
            continue
        i0, i1 = np.searchsorted(bt, f["t0"] - 0.02), np.searchsorted(bt, f["t1"] + 0.02)
        px = np.interp(bt[i0:i1], c[:, 0], c[:, 1]); py = np.interp(bt[i0:i1], c[:, 0], c[:, 2])
        dist = np.hypot(d[i0:i1, 1] - px, d[i0:i1, 2] - py)
        for k in np.nonzero(dist <= BALL_NEAR_PX)[0]:
            fr = int(d[i0 + k, 0])
            if fr not in best or dist[k] < best[fr][0]:
                best[fr] = (float(dist[k]), i0 + k)
    ball = [[round(float(bt[i]), 3), round(float(d[i, 1]), 1), round(float(d[i, 2]), 1)] for _, i in (best[fr] for fr in sorted(best))]
    # The candidates of the detector that are not the ball of a flight, for the debug view: the user can see a real
    # ball that the track or the flight fit rejected. Rows: time, x, y, peak of the detector.
    used = {i for _, i in best.values()}
    ball_other = [[round(float(bt[i]), 3), round(float(d[i, 1]), 1), round(float(d[i, 2]), 1), round(float(d[i, 4]), 2)]
                  for i in range(len(d)) if i not in used]
    ev = []
    for e in events:
        o = {"t": round(float(e["t"]), 3), "kind": e["kind"], "px": [round(float(v), 1) for v in e["px"]],
             "court": None if e["court"] is None else [round(float(v), 2) for v in e["court"]], "src": e["src"],
             "conf": None if e["conf"] is None or not np.isfinite(e["conf"]) else round(float(e["conf"]), 2)}
        if "tLast" in e:
            o["tLast"] = round(float(e["tLast"]), 3)
        ev.append(o)
    for e in net_ev:
        ev.append({"t": e["t"], "kind": e["kind"], "px": e["px"], "court": None, "src": e["src"], "conf": e["conf"], "toFar": e["toFar"]})
    sv = [{"t": round(float(e[0]), 3), "near": bool(e[1]), "kind": e[2], "speedKmh": round(3.6 * float(np.linalg.norm(e[3]["v0"])), 1)}
          for e in serves]
    json.dump({"version": 2 if "events" in full else 1, "width": 1920, "height": 1080, "court": court, "ball": ball, "ballOther": ball_other, "flights": flights,
               "events": ev, "serves": sv, "poses": overlay_poses(work)},
              open(W("overlay.json"), "w"), default=float)
    say("@overlay", W("overlay.json"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--settings", default="")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--adopt-ball", default="", help="an old ball file to adopt (E52); needs --weights and --passes")
    ap.add_argument("--weights", default=None, help="with --adopt-ball: the weights of the old file")
    ap.add_argument("--passes", default=None, help='with --adopt-ball: the passes of the old file ("dim" or "")')
    ap.add_argument("--adopt-keys", action="store_true", help="item 13: adopt the ball and pose files under the new keys (git check)")
    a = ap.parse_args()
    work = os.path.abspath(a.work)
    if a.adopt_ball:
        if a.weights is None or a.passes is None:
            raise RuntimeError("--adopt-ball needs --weights and --passes: state the weights and passes of the old file.")
        rec = adopt_ball(work, a.video, a.adopt_ball, a.weights, a.passes)
        say("@adopted", json.dumps(rec))
        return
    os.makedirs(work, exist_ok=True)
    # ballPasses (E27): "dim" = a second pass of the ball detector on a dimmed picture, for a ball in front of a
    # bright background (about 2 times the ball stage). "" = one pass.
    # ballWeights (E38): the weights file of the ball detector in the weights folder. The default is our fine-tune of
    # the WASB tennis weights (ball_train2.py): it also finds a ball that is not brighter than its background.
    # "" = the WASB tennis weights. If the file is not there, the run uses the WASB tennis weights and gives a warning.
    # chain (E29): true = the shot chain gives the winner evidence; false = the old ball rule (lab: CHAIN=0).
    # endModel (E60): true = the end for the winner evidence comes from the rally end detector (lab: END_MODEL).
    # courtTrack (E26f): "" = the court model rule of ball_events.court_windows; a file name = that court file only.
    # netBand (item 6 (b), E88 to E133): true = the motion of the net picture rejects or confirms the net touches
    # (net_band_mask.py, net_band.py). Off until the test on the label windows holds the E131 result.
    # Before it goes on: bench.py (S1) must add net_band.npz to the inputs of the flights key and to the manifest.
    cfg = {"ballPass": True, "threshold": 0.5, "shortPoints": False, "ballPasses": "dim", "ballWeights": BALL_WEIGHTS,
           "chain": True, "endModel": True, "courtTrack": "", "netBand": False}
    if a.settings:
        cfg.update(json.load(open(a.settings, encoding="utf-8")))
    warnings = []

    def warn(text):
        """G13: each fallback of the run goes to result.json, not only to run.log."""
        warnings.append(text)
        say("@warning", text)
    gone = sorted(k for k in list(os.environ) if k.upper() in LAB_ENV or k.upper().startswith("CHAIN_"))
    for k in gone:
        del os.environ[k]
    if gone:
        warn(f"The run does not use the lab environment variables {', '.join(gone)}. Use the settings file.")
    os.environ["BALL_PASSES"] = cfg["ballPasses"] or ""              # ball_wasb reads it at import
    weights = "wasb_tennis.pth.tar"
    if cfg["ballWeights"]:
        import platform_cfg
        if os.path.exists(os.path.join(platform_cfg.weights_dir(), cfg["ballWeights"])):
            weights = os.environ["BALL_WEIGHTS"] = cfg["ballWeights"]
        else:
            warn(f"The ball weights {cfg['ballWeights']} are not there. The run uses the WASB tennis weights.")
    global KEYS_FILE
    KEYS_FILE = os.path.join(work, "stage_keys.json")
    if os.path.exists(KEYS_FILE) and not a.force:
        STAGE_KEYS.update(json.load(open(KEYS_FILE)))
    vid = check_video(work, a.video)
    if a.adopt_keys:
        import lab
        rec = adopt_keys(work, weights, (cfg["ballPasses"] or "").replace(",", "-").replace("+", "-"), lab.video_seconds(work),
                         cfg["courtTrack"])
        say("@adopted", json.dumps(rec))
        return
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
    import ball_events
    ball_events.COURT_TRACK = cfg["courtTrack"] or None

    def save_times():
        json.dump(STAGE_S, open(times_file, "w"))

    # ---- media
    import prep, frame_times
    stage("media", [W("audio.flac"), W("kf_times.csv"), W("kf.done"), W("pts.npy")],
          lambda: (prep.prep(lab.MATCHES[MID], work), frame_times.load(MID)), a.force, code_key("media"))
    save_times()
    kt = lab.kf_times(work)
    n = lab.video_seconds(work)            # G2: the seconds of the video, not the count of pictures
    step = float(np.median(np.diff(kt))) if n > 2 else 0.0
    if not 0.9 < step < 1.1:
        raise RuntimeError(f"The video has a keyframe each {step:.2f} s. The lab models need a keyframe each second.")

    # ---- audio and image features
    import feat_audio, feat_mel
    stage("audio", [W("feat_audio.npy"), W("feat_mel.npy")], lambda: (feat_audio.run(MID), feat_mel.run(MID)), a.force, code_key("audio"))

    def image():
        import timm, feat_dino
        model = platform_cfg.to_device(timm.create_model("vit_small_patch14_dinov2.lvd142m", pretrained=True, img_size=(feat_dino.H, feat_dino.W))).eval()
        feat_dino.run(MID, model)
        del model
        platform_cfg.empty_cache()
    stage("image", [W("feat_dino.npy")], image, a.force)
    save_times()

    model_name = "base"
    ball_ok = bool(cfg["ballPass"])
    if ball_ok:
        # ---- court
        def court():
            import court_track
            tr = court_track.run(MID)
            ok = [w for w in (tr or []) if w["ok"]]
            # court.json is an input of the ball tag (the crop of the ball stage). A new court key (a code change)
            # must not force a ball run (item 13): an existing court.json stays; --force makes it again. court_track
            # starts from it, so each court run starts from the same court.
            if ok and (a.force or not os.path.exists(W("court.json"))):
                m = min(ok, key=lambda w: abs(w["t0"] - n / 2))
                json.dump({"H": m["H"], "chamfer_px": max(1.0, m["cost"])}, open(W("court.json"), "w"))
        stage("court", [W("court_track.json")], court, a.force, code_key("court"))
        # The check comes first: with no court model (court_track.json is not written), the steps below fail.
        problem = court_problem(W("court_track.json"))
        if not problem:
            def refine():
                # E26: the paint centre of each line at sub-pixel accuracy. ball_events.court_windows uses the result
                # only when the first model has a clear fault at the far end (E26f).
                import court_refine
                court_refine.run(work, log=False)
            try:
                stage("court2", [W("court_track_refined.json")], refine, a.force, code_key("court2", files=[W("court_track.json")]))
            except Exception as e:        # the first model stays
                traceback.print_exc()
                warn(f"No refined court model ({type(e).__name__}: {e}). The ground points use the first court model.")
            ball_events.court_windows(work, say)
            track = json.load(open(W("court_track.json")))
            if any(w.get("onePicture") for w in track):
                # E55 (G15): the camera moves; court_track found the court only on single pictures.
                import court_auto
                Hs = [np.array(w["H"]) for w in track if w["ok"]]
                move = max([court_auto.model_dist(a, b) for a, b in zip(Hs, Hs[1:])] or [0.0])
                warn(f"The camera moves during the video (the court lines move up to {move:.0f} px from one minute to "
                     "the next). The court model is less exact, and so are the ball positions, the serve faults and the "
                     "winners. A fixed camera gives better results.")
        save_times()
        if problem:
            # The ball stages need a correct court model. Without it, the result has only the rally boundaries.
            warn(problem + " The result has only the rally boundaries: no winners, no serve faults, no short points.")
            ball_ok = False

    if ball_ok:
        # ---- ball (the slow stage)
        import ball_wasb, ball_full, feat_wasb
        ball_full.WINDOWS[MID] = (0, n)
        # G12: the ball file name has the weights, the passes, and a key of the inputs (ball_tag).
        passes = (cfg["ballPasses"] or "").replace(",", "-").replace("+", "-")
        lab.BLOB_TAG = ball_tag(weights, passes, n, work)
        blob = lab.blob_file(work, 0, n)
        adopted = STAGE_KEYS.get("ballAdopted")
        if adopted and adopted.get("file") == os.path.basename(blob) and os.path.exists(blob) and not a.force:
            warn(f"The ball candidates come from the older file {adopted['from']}: the operator stated the weights "
                 f"{adopted['weights']} and the passes \"{adopted['passes']}\" ({adopted['time']}). This run did not make them.")

        net_on = bool(cfg["netBand"])
        mask_file = W("net_band_mask.npz")
        made = []
        frames0 = ball_wasb.frames

        def mask_writer(step, index):
            """Item 6 (b): the ball stage also gives its pictures to the level-1 writer of the net band."""
            import net_band_mask
            crop = ball_wasb.crop_box(MID, 0.35, bool(ball_wasb.PASSES))[:4]
            Hm = json.load(open(W("court.json")))["H"]
            w = net_band_mask.MaskWriter(Hm, crop, 0.35, step, index, tmp_dir=work)
            ball_wasb.frames = w.wrap(frames0)
            return w

        def ball():
            if STAGE_KEYS.pop("ballAdopted", None):    # the stage makes the file itself: the adoption is no longer true
                json.dump(STAGE_KEYS, open(KEYS_FILE, "w"), indent=1)
            try:
                ball_run()
            finally:
                ball_wasb.frames = frames0

        def ball_run():
            # The frame times of the video. A run before this one on a video that is not 60 fps put them in
            # pts_video.npy, and pts.npy is then the 60 fps time line of the detections (see below). Without this,
            # a second run of the ball stage reads the time line, decides "60 fps", and each detection gets half of its time.
            pts = np.load(W("pts_video.npy") if os.path.exists(W("pts_video.npy")) else W("pts.npy"))
            if np.percentile(np.diff(pts), 90) < 0.02:               # a 60 fps video, as the lab matches
                ball_wasb.PROGRESS = lambda fr: say("@progress ball %.3f" % min(1.0, fr / (n * 60.0)))
                mw = mask_writer(2, lambda i: i) if net_on else None
                np.save(blob, ball_wasb.run(MID, 0, n, save=False))
            else:
                # Another frame rate, or a mix of 30 and 60 fps (E23b). The ball detector learned frames that
                # are 1/30 s apart, and the flight code counts in frames of 1/60 s. So: decode at a constant
                # 30 fps, use each frame (stride 1), and put the detections on each second frame of a
                # 60 fps time line. pts.npy becomes that time line (the times of the video: pts_video.npy).
                ball_wasb.FPS = 30
                ball_wasb.PROGRESS = lambda fr: say("@progress ball %.3f" % min(1.0, fr / (n * 30.0)))
                mw = mask_writer(1, lambda i: 2 * i) if net_on else None
                d = ball_wasb.run(MID, 0, n, stride=1, save=False)
                d[:, 0] *= 2
                np.save(W("pts_video.npy"), pts)
                np.save(W("pts.npy"), np.arange(n * 60) / 60.0)
                np.save(blob, d)
            if mw is not None:                                       # the masks of all pictures: no fill-in decode
                mw.save(mask_file)
                STAGE_KEYS["netmask"] = netmask_key(lab.BLOB_TAG)
                json.dump(STAGE_KEYS, open(KEYS_FILE, "w"), indent=1)
                made.append("netmask")
            platform_cfg.empty_cache()
        stage("ball", [blob], ball, a.force)
        save_times()

        if net_on:
            # ---- net band (item 6 (b)). Level 1: the masks of the moved pixels near the net. A folder with a ball file
            # and no current mask file decodes only the windows around the net candidates of the ball file (not of the
            # flights: the flights key has the net band key). A candidate outside these windows gets no net band rule.
            def netmask():
                import net_band_mask, net_events
                d = np.load(blob)
                d = d[np.argsort(d[:, 0], kind="stable")]
                pts = np.load(W("pts.npy"))
                t = pts[np.minimum(d[:, 0].astype(int), len(pts) - 1)]
                rows = np.column_stack([t, d[:, 1:5]])
                track = ball_events.court_windows(work)

                def H(c):
                    w = min(track, key=lambda w: 0 if w["t0"] <= c < w["t1"] else min(abs(c - w["t0"]), abs(c - w["t1"])))
                    return np.array(w["H"], np.float64)
                times, c, step = [], float(t[0]) if len(t) else 0.0, 30.0
                while len(t) and c < t[-1]:                          # the chunks of net_events.run_folder
                    i0, i1 = np.searchsorted(t, c - 2), np.searchsorted(t, c + step + 2)
                    if i1 > i0:
                        times += [q["t"] for q in net_events.candidates(rows[i0:i1], H(c + step / 2)) if c <= q["t"] < c + step]
                    c += step
                say("@netmask candidates", len(times))
                net_band_mask.fill_in(MID, pts, times, json.load(open(W("court.json")))["H"], mask_file,
                                      fps30=os.path.exists(W("pts_video.npy")),
                                      progress=lambda j, m: say("@progress netmask %.3f" % (j / max(1, m))))
                platform_cfg.empty_cache()
            if "netmask" not in made:
                stage("netmask", [mask_file], netmask, a.force, netmask_key(lab.BLOB_TAG))
                save_times()

            # Level 2: the bins of the net and the ball mask (cheap: no decode).
            def netband():
                import net_band
                net_band.build(mask_file, np.load(blob), json.load(open(W("court.json")))["H"], W("net_band.npz"))
            stage("netband", [W("net_band.npz")], netband, a.force, STAGE_KEYS["netmask"] + "_" + code_key("netband"))
            save_times()

        def flights():
            ball_full.run(MID)
            import net_events
            net_events.run_folder(work, band_file=W("net_band.npz") if net_on else None)
            feat_wasb.run(MID)
            np.save(W("feat_wasbf.npy"), np.load(W("feat_wasb.npy"))[:, [4, 5, 6]])
        # G12: the flights run again when the ball file, the court files or the code of the flights (import closure) change.
        flights_key = lab.BLOB_TAG + "_" + code_key("flights", cfg["courtTrack"], files=[W("court_track.json"), W("court_track_refined.json")])
        if net_on:
            flights_key += "_nb" + STAGE_KEYS["netband"]
        stage("flights", [W("fullflights_s0.35.json"), W("feat_wasbf.npy")], flights, a.force, flights_key)
        save_times()

        # ---- pose
        def pose():
            import pose_extract
            from rtmlib import YOLOX, RTMPose
            dev = platform_cfg.ort_device()
            det = YOLOX(platform_cfg.pose_model(pose_extract.DET), model_input_size=(640, 640), backend="onnxruntime", device=dev)
            ps = RTMPose(platform_cfg.pose_model(pose_extract.POSE), model_input_size=(192, 256), backend="onnxruntime", device=dev)
            pose_extract.run(MID, det, ps)
        stage("pose", [W("pose.npy")], pose, a.force, pose_key())
        save_times()
        model_name = "ball"

    # ---- rallies (chain.py: the same chain as the lab benchmark, pipeline.py)
    import rally_model, chain
    chain.CHAIN, chain.END_MODEL = bool(cfg["chain"]), bool(cfg["endModel"])     # G12: the settings, not the environment
    say("@stage rallies start")
    t = time.time()
    prob = rally_model.predict(MID, model_name)
    np.save(W(f"rally_prob_{model_name}.npy"), prob.astype(np.float32))
    full = json.load(open(W("fullflights_s0.35.json"))) if ball_ok else None
    seg, serves = chain.segments(prob, full, cfg)
    STAGE_S["rallies"] = round(time.time() - t, 1)
    say("@stage rallies done", STAGE_S["rallies"])

    # ---- winners. G13: from here an exception gives a partial result with the rallies that are ready.
    out, partial = chain.rally_rows(seg), False
    try:
        if ball_ok and seg:
            say("@stage winners start")
            t = time.time()
            chain.FAULTS.clear()
            out = chain.winners(MID, seg, full, serves, prob=prob)
            for text in chain.fault_texts():
                warn(text)
            STAGE_S["winners"] = round(time.time() - t, 1)
            say("@stage winners done", STAGE_S["winners"])
        if ball_ok:
            try:
                add_views(work, out, n)
            except Exception as e:        # with no view, the app shows the picture of the rally start
                traceback.print_exc()
                warn(f"No player pictures ({type(e).__name__}: {e}). The player question shows the picture of the rally start.")
            try:
                net = load_net_events(work)
                add_net_hits(out, net)
                write_overlay(work, n, full, serves, net)
            except Exception as e:        # the overlay is a debug view: the result is more important
                traceback.print_exc()
                warn(f"No overlay data ({type(e).__name__}: {e}).")
    except Exception as e:
        traceback.print_exc()
        partial = True
        has_winners = any("winner" in r for r in out)
        warn(f"The run failed after the rally stage ({type(e).__name__}: {e})." +
             ("" if has_winners else " The result has only the rally boundaries: no winners, no serve faults."))
    save_times()
    # version (G14): the contract with the app (DetectResult.CONTRACT_VERSION). 2 = video seconds (G2), videoId, work,
    # cache, partial, view.picture. Increase it when the app cannot read the file in the old way.
    res = {"version": 2, "video": os.path.abspath(a.video), "videoId": vid, "work": work, "videoSeconds": n, "settings": cfg,
           "rallyModel": model_name, "cache": {"ballFile": os.path.basename(lab.blob_file(work, 0, n)) if ball_ok else None,
                                               "stageKeys": dict(STAGE_KEYS)},
           "stageSeconds": STAGE_S, "thisRunSeconds": round(time.time() - t_all, 1),
           "created": time.strftime("%Y-%m-%dT%H:%M:%S"), "partial": partial, "warnings": warnings, "rallies": out}
    json.dump(res, open(W("result.json"), "w"), indent=1, default=float)
    say("@result", W("result.json"))


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        traceback.print_exc()
        say("@error", f"{type(e).__name__}: {e}".replace("\n", " "))
        sys.exit(1)

"""Case library (gap program S1 item 8): short fragments of the source videos, cut by stream copy, with their marks.

A fragment is a folder <lab>/ws/s1/cases/frags/<id>/ with:
  clip.mp4        the source video from a keyframe, stream copy (-c copy): the packets are the source packets, so a
                  decoder gives the same pixels as in the full video (no new encode);
  fragment.json   the manifest (format "case-fragment/1", see FORMAT below);
  court.json, court_track.json, court_track_refined.json
                  the court of the full run, the track windows moved to fragment time (the court is an input, unless
                  the case is a court case).
The time of the fragment: t_frag = t_source - offset, where offset is the source time of the first video frame of
clip.mp4 (a keyframe). All times in the manifest are fragment times. A reader of the clip timestamps (a player, the
media stage) gets t_frag + videoStartInClip (the audio can start a few ms before the video). The cut is checked: the
first decoded frame of the clip must equal the source frame at offset.

Levels (the user, gap-program 8a): small (1 point with a pad, 30 to 60 s), medium (2 to 3 games across one end
change, 7 to 10 min), large (5 to 9 games, 20 to 30 min), match (the full video). A fragment can hold many points.

Usage:
  python case_lib.py controls --n 2 [--seed 20261126] [--matches m1015,...]   a stratified random sample of points
  python case_lib.py cut <spec.json>        cut the fragments of a spec list (see cut()); writes the manifests
  python case_lib.py list                   the fragments of the library
  python case_lib.py index                  <lab>/ws/s1/cases/manifest.json for the event label page (S4); cut also writes it
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, time
import numpy as np
import lab, platform_cfg

ROOT = os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "ws", "s1", "cases")
FRAGS = os.path.join(ROOT, "frags")
FORMAT = "case-fragment/1"
SINGLES = ["m1015", "m1020", "m1021", "m1030", "m1116"]      # the singles videos with marks in the lab folders
PAD = {"small": (8.0, 4.0)}                                  # s before the first point, s after the last point
# The lab marks start at the second serve; the label page asks the user for the first-serve fault before it (5 to
# 20 s earlier). On these matches the pad before is up to FAULT_PAD s, but it does not go into the previous point.
FAULT_CHECK = ("m1015", "m1020", "m1021", "m1030")
FAULT_PAD = 25.0


def run(cmd):
    r = subprocess.run(cmd, capture_output=True)
    if r.returncode:
        raise RuntimeError(f"{os.path.basename(cmd[0])} rc={r.returncode}: {r.stderr.decode(errors='replace')[-400:]}")
    return r.stdout


def points(mid):
    """The marked points of a match: [{id, start, end, outcome, winnerNear}], source seconds."""
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))["points"]
    ef = os.path.join(lab.mdir(mid), "ends.json")
    ends = {e["id"]: e for e in json.load(open(ef))} if os.path.exists(ef) else {}
    return [{"id": p["id"], "start": p["startMs"] / 1000, "end": p["endMs"] / 1000, "outcome": p["outcome"],
             "winnerNear": ends.get(p["id"], {}).get("winnerNear")} for p in g]


def controls(n, seed=20261126, mids=None, exclude=()):
    """A stratified random sample of n marked points (manager, 2026-11-26: controls are random, not hand-picked):
    the strata are (video, length third of the points of that video); each draw takes a random stratum that was not
    used yet, then a random point of it. exclude: (mid, start) of points that are error cases."""
    rng = np.random.RandomState(seed)
    strata = []
    for m in (mids or SINGLES):
        ps = [p for p in points(m) if p["outcome"] != "NONE"            # the user: NONE points are out of the bench
              and not any(m == em and abs(p["start"] - es) < 1.0 for em, es in exclude)]
        ln = np.array([p["end"] - p["start"] for p in ps])
        cut = np.quantile(ln, [1 / 3, 2 / 3])
        for k, name in enumerate(("short", "middle", "long")):
            sel = [p for p, x in zip(ps, ln) if (k == 0 and x <= cut[0]) or (k == 1 and cut[0] < x <= cut[1]) or (k == 2 and x > cut[1])]
            strata.append((m, name, sel))
    out = []
    for i in rng.permutation(len(strata))[:n]:
        m, name, sel = strata[i]
        p = sel[rng.randint(len(sel))]
        out.append({"mid": m, "start": p["start"], "end": p["end"], "stratum": f"{m} {name} third", "seed": int(seed)})
    return out


def keyframe_before(video, t, not_before=None):
    """The source time of the last keyframe at or before t. not_before: if that keyframe is earlier than this time
    (the end of the previous point), the first keyframe at or after it instead."""
    out = run([platform_cfg.tool("ffprobe"), "-v", "error", "-select_streams", "v:0", "-skip_frame", "nokey",
               "-read_intervals", f"{max(0.0, t - 15):.3f}%{t + 3:.3f}", "-show_entries", "frame=pts_time", "-of", "csv=p=0", video])
    ks = sorted(float(x) for x in out.decode().split() if x.strip() and x.strip() != "N/A")
    before = [k for k in ks if k <= t + 1e-6]
    if not before:
        raise RuntimeError(f"no keyframe in the 15 s before {t:.2f} s of {video}")
    k = max(before)
    if not_before is not None and k < not_before:
        after = [x for x in ks if x >= not_before]
        k = min(after) if after else k
    return k


def frame_md5(video, t=None):
    """md5 of the first decoded video frame (at or after t; gray, full size)."""
    cmd = [platform_cfg.tool("ffmpeg"), "-v", "error"] + (["-ss", f"{t:.6f}"] if t is not None else []) + \
          ["-i", video, "-map", "0:v:0", "-frames:v", "1", "-pix_fmt", "gray", "-f", "rawvideo", "-"]
    return hashlib.md5(run(cmd)).hexdigest()


def video_id(video):
    """A cheap identity of a source file: size and the md5 of its first 4 MB."""
    with open(video, "rb") as f:
        return f"{os.path.getsize(video)}-{hashlib.md5(f.read(4 << 20)).hexdigest()[:16]}"


def shift_track(src, dst, offset, dur):
    """A court track file of the full run with the windows that overlap the fragment, moved to fragment time."""
    if not os.path.exists(src):
        return False
    ws = [dict(w) for w in json.load(open(src)) if float(w["t1"]) > offset and float(w["t0"]) < offset + dur]
    for w in ws:
        w["t0"], w["t1"] = float(w["t0"]) - offset, float(w["t1"]) - offset
    json.dump(ws, open(dst, "w"))
    return True


def cut(spec):
    """One fragment. spec: {id, level, kinds: [..], control: bool, mid, start, end (source s of the labeled span),
    cases: [{stream, journal, case, solved}], pad: [before, after] (optional)}. Returns the manifest."""
    mid, level = spec["mid"], spec.get("level", "small")
    video = lab.MATCHES[mid]["video"]
    if level == "small" and any(p["outcome"] == "NONE" and p["end"] > spec["start"] and p["start"] < spec["end"] for p in points(mid)):
        raise RuntimeError(f"{spec['id']}: the span has a NONE point; NONE points are not cases or controls (the user, 2026-11-26)")
    before, after = spec.get("pad") or PAD[level]
    prev_end = None
    if mid in FAULT_CHECK and not spec.get("pad"):
        ends = [p["end"] for p in points(mid) if p["end"] <= spec["start"] + 1e-6]
        prev_end = max(ends) if ends else 0.0
        before = max(0.0, min(FAULT_PAD, spec["start"] - prev_end))
    t_in = max(0.0, spec["start"] - before)
    kf = keyframe_before(video, t_in, not_before=prev_end)
    t_out = spec["end"] + after
    d = os.path.join(FRAGS, spec["id"])
    if os.path.exists(os.path.join(d, "fragment.json")):
        raise RuntimeError(f"{spec['id']} is already in the library")
    os.makedirs(d, exist_ok=True)
    clip = os.path.join(d, "clip.mp4")
    t0 = time.time()
    # Input seek to just after the keyframe: in stream copy, ffmpeg starts at the keyframe at or before the seek point.
    run([platform_cfg.tool("ffmpeg"), "-v", "error", "-ss", f"{kf + 0.0005:.6f}", "-i", video, "-t", f"{t_out - kf:.3f}",
         "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy", "-avoid_negative_ts", "make_zero", "-y", clip])
    # The check of the cut: the first clip frame is the source frame at the keyframe (decode from the keyframe before).
    same = frame_md5(clip) == frame_md5(video, kf - 0.0005)
    if not same:
        raise RuntimeError(f"{spec['id']}: the first frame of the clip is not the source frame at {kf:.3f} s")
    probe = json.loads(run([platform_cfg.tool("ffprobe"), "-v", "error", "-show_entries", "stream=codec_type,start_time,duration,nb_frames",
                            "-of", "json", clip]))["streams"]
    vdur = next(float(s["duration"]) for s in probe if s["codec_type"] == "video")
    # The first audio packet can be earlier than the first video frame: then the clip timestamps of the video start
    # a little after 0 (9 ms on the lab videos). Fragment time = clip video timestamp - videoStartInClip.
    vstart = next(float(s.get("start_time") or 0) for s in probe if s["codec_type"] == "video")
    for f in ("court.json",):
        if os.path.exists(os.path.join(lab.mdir(mid), f)):
            shutil.copy2(os.path.join(lab.mdir(mid), f), d)
    court = [f for f in ("court_track.json", "court_track_refined.json")
             if shift_track(os.path.join(lab.mdir(mid), f), os.path.join(d, f), kf, vdur)]
    pts = [dict(p, start=round(p["start"] - kf, 3), end=round(p["end"] - kf, 3), sourceStart=p["start"], sourceEnd=p["end"])
           for p in points(mid) if p["end"] > kf and p["start"] < kf + vdur]
    man = {"format": FORMAT, "id": spec["id"], "level": level, "kinds": spec.get("kinds", []), "control": bool(spec.get("control")),
           "source": {"mid": mid, "video": video, "videoId": video_id(video)},
           "clip": "clip.mp4", "offset": round(kf, 6), "videoStartInClip": round(vstart, 6), "duration": round(vdur, 3),
           "labelSpan": [round(spec["start"] - kf, 3), round(spec["end"] - kf, 3)],
           "pad": {"before": round(spec["start"] - kf, 3), "after": after,
                   "rule": f"fault check: up to {FAULT_PAD:.0f} s, not into the previous point (ends {prev_end})" if prev_end is not None else "level default"},
           "points": pts, "court": ["court.json"] + court, "courtFrom": "full run (lab folder)",
           "cases": spec.get("cases", []), "stratum": spec.get("stratum"),
           "cut": {"tool": "ffmpeg -c copy from a keyframe", "firstFrameSame": same, "seconds": round(time.time() - t0, 1),
                   "time": time.strftime("%Y-%m-%dT%H:%M:%S")},
           "context": None, "history": []}
    json.dump(man, open(os.path.join(d, "fragment.json"), "w"), indent=1)
    return man


def index():
    """<ROOT>/manifest.json: one row for each fragment, in the format of the event label page (S4 item 10, agreed
    2026-11-26). Times are source times: source time = t0 + clip time (the player's currentTime), so
    t0 = offset - videoStartInClip. courtH: image px of a 1920-wide frame -> court m (the H of the court files), from
    the court window at the middle of the span (the refined track if it is there). Returns the path."""
    rows = []
    for f in sorted(os.listdir(FRAGS)) if os.path.exists(FRAGS) else []:
        p = os.path.join(FRAGS, f, "fragment.json")
        if not os.path.exists(p):
            continue
        m = json.load(open(p))
        mid, off = m["source"]["mid"], m["offset"]
        span = [round(off + m["labelSpan"][0], 3), round(off + m["labelSpan"][1], 3)]
        mid_t = (m["labelSpan"][0] + m["labelSpan"][1]) / 2
        H, cfile = None, None
        for cf in ("court_track_refined.json", "court_track.json"):
            fp = os.path.join(FRAGS, f, cf)
            if os.path.exists(fp):
                ws = [w for w in json.load(open(fp)) if str(w.get("ok", True)) != "False" and w["t0"] <= mid_t < w["t1"]]
                if ws:
                    H, cfile = ws[0]["H"], cf
                    break
        if H is None:
            H, cfile = json.load(open(os.path.join(FRAGS, f, "court.json")))["H"], "court.json"
        probe = json.loads(run([platform_cfg.tool("ffprobe"), "-v", "error", "-select_streams", "v:0", "-show_entries",
                                "stream=width,height,avg_frame_rate", "-of", "json", os.path.join(FRAGS, f, m["clip"])]))["streams"][0]
        num, den = (float(x) for x in probe["avg_frame_rate"].split("/"))
        rows.append({"id": m["id"], "match": mid, "testOnly": mid in lab.TEST_ONLY,
                     "clip": f"frags/{f}/{m['clip']}", "t0": round(off - m.get("videoStartInClip", 0.0), 6),
                     "firstFrame": off, "fps": round(num / den, 3), "size": [probe["width"], probe["height"]],
                     "courtH": H, "courtImageWidth": 1920, "courtFile": f"frags/{f}/{cfile}",
                     "points": [{"id": q["id"], "startMs": round(q["sourceStart"] * 1000), "endMs": round(q["sourceEnd"] * 1000),
                                 "ending": q["outcome"]} for q in m["points"]],
                     "span": span, "level": m["level"], "kinds": m["kinds"], "control": m["control"],
                     "faultCheck": mid in ("m1015", "m1020", "m1021", "m1030")})
    out = os.path.join(ROOT, "manifest.json")
    json.dump({"version": 1, "fragments": rows}, open(out, "w"), indent=1)
    return out


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("controls")
    c.add_argument("--n", type=int, default=2); c.add_argument("--seed", type=int, default=20261126); c.add_argument("--matches", default="")
    k = sub.add_parser("cut"); k.add_argument("spec")
    sub.add_parser("list")
    sub.add_parser("index")
    a = ap.parse_args()
    if a.cmd == "controls":
        print(json.dumps(controls(a.n, a.seed, a.matches.split(",") if a.matches else None), indent=1))
    elif a.cmd == "cut":
        for s in json.load(open(a.spec)):
            m = cut(s)
            print(f"{m['id']}: {m['source']['mid']} offset {m['offset']:.3f} s, {m['duration']:.1f} s, points {len(m['points'])}, "
                  f"label span {m['labelSpan']}, first frame same {m['cut']['firstFrameSame']} ({m['cut']['seconds']} s)", flush=True)
        print("index:", index())
    elif a.cmd == "index":
        print(index())
    else:
        for f in sorted(os.listdir(FRAGS)) if os.path.exists(FRAGS) else []:
            p = os.path.join(FRAGS, f, "fragment.json")
            if os.path.exists(p):
                m = json.load(open(p))
                print(f"{m['id']}  {m['level']}  {m['source']['mid']}  {m['duration']:.0f} s  kinds {','.join(m['kinds']) or '-'}"
                      f"{'  control' if m['control'] else ''}  points {len(m['points'])}")


if __name__ == "__main__":
    main()

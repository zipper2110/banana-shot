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

Fixed ids (manager, 2026-11-26): an id is fixed once a fragment is in a published index (the label page reads it).
`published.json` records each id that index() wrote, with its t0. A cut refuses an id that is published, has a
label file (labels/<id>.events.json), or has a folder: a new cut gets a new id (for example s005b). A fragment that
must leave the page is retired (fragment.json "retired"); index() leaves it out, and its id stays published. Cause:
s005 was cut again under the same id while the page was open; the page kept the old row (old t0) with the new clip.

Levels (the user, gap-program 8a): small (1 point with a pad, 30 to 60 s), medium (2 to 3 games across one end
change, 7 to 10 min), large (5 to 9 games, 20 to 30 min), match (the full video). A fragment can hold many points.

Usage:
  python case_lib.py controls --n 2 [--seed 20261126] [--matches m1015,...]   a stratified random sample of points
  python case_lib.py cut <spec.json>        cut the fragments of a spec list (see cut()); writes the manifests
  python case_lib.py list                   the fragments of the library
  python case_lib.py index                  <lab>/ws/s1/cases/manifest.json for the event label page (S4); cut also writes it
  python case_lib.py retire <id> --reason <text>   take a fragment out of the page; its id stays used
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, time
import numpy as np
import lab, platform_cfg

ROOT = os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "ws", "s1", "cases")
FRAGS = os.path.join(ROOT, "frags")
# The fault clips of the label page (S4 E158, the Faults step): not event cases, so they are not in FRAGS, index() and
# the history. clips.json: {row id: fragment folder}.
FAULT_CLIPS = os.path.join(ROOT, "faults")
LABELS = os.path.join(ROOT, "labels")
PUBLISHED = os.path.join(ROOT, "published.json")
FORMAT = "case-fragment/1"
SINGLES = ["m1015", "m1020", "m1021", "m1030", "m1116"]      # the singles videos with marks in the lab folders
PAD = {"small": (8.0, 4.0), "medium": (60.0, 60.0)}         # s before the first point, s after the last point (medium: the GRU context)
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


def cut(spec, frags=FRAGS):
    """One fragment in the folder frags (default FRAGS; the fault clips: FAULT_CLIPS). spec: {id, level, kinds: [..], control: bool, mid, start, end (source s of the labeled span),
    cases: [{stream, journal, case, solved}], pad: [before, after] (optional)}. Returns the manifest."""
    mid, level = spec["mid"], spec.get("level", "small")
    video = lab.MATCHES[mid]["video"]
    if level in ("small", "medium") and any(p["outcome"] == "NONE" and p["end"] > spec["start"] and p["start"] < spec["end"] for p in points(mid)):
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
    d = os.path.join(frags, spec["id"])
    if spec["id"] in published() or os.path.exists(os.path.join(LABELS, spec["id"] + ".events.json")) or os.path.exists(d):
        raise RuntimeError(f"the id {spec['id']} is in use (published, labeled, or a folder): give the new cut a new id")
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


def published():
    return json.load(open(PUBLISHED)) if os.path.exists(PUBLISHED) else {}


def labels(warn=print):
    """{id: the user's event labels} of the fragments in the current index. The one reader of the label files: it skips
    a retired or unknown id (s005), a file with another name (for example *.bak), and a label whose t0 is not the t0 of
    its fragment (marks made with another cut)."""
    rows = {r["id"]: r for r in json.load(open(os.path.join(ROOT, "manifest.json")))["fragments"]}
    pub = published()
    out = {}
    for f in sorted(os.listdir(LABELS)) if os.path.exists(LABELS) else []:
        if not f.endswith(".events.json"):
            continue
        fid = f[:-len(".events.json")]
        if fid not in rows:
            if fid not in pub or not pub[fid].get("retired"):
                warn(f"labels: {f} has no fragment in the index; skipped")
            continue
        L = json.load(open(os.path.join(LABELS, f), encoding="utf-8"))
        if abs(L.get("t0", -1) - rows[fid]["t0"]) > 1e-4:
            warn(f"labels: {f} has t0 {L.get('t0')}, the fragment {rows[fid]['t0']}; skipped")
            continue
        out[fid] = L
    return out


PAGE = os.path.join(os.path.dirname(ROOT), "..", "s4", "events")     # the folder of the label page (case_manifest.py output)


def page_row_hash(fid, page=PAGE):
    """The rowHash that case_manifest.py gives the row of fid when it writes the page list into page: sha256 of the row
    with the clip path relative to page and courtH filled, without rowHash and clipDur (S4's rule)."""
    import hashlib as _h
    r = next(x for x in json.load(open(os.path.join(ROOT, "manifest.json")))["fragments"] if x["id"] == fid)
    r = {k: v for k, v in r.items() if k not in ("rowHash", "clipDur")}
    if not os.path.isabs(r["clip"]):
        r["clip"] = os.path.relpath(os.path.join(ROOT, r["clip"]), os.path.abspath(page)).replace("\\", "/")
    if "courtH" not in r and r.get("match"):
        import case_manifest
        r["courtH"] = case_manifest.court(r["match"], r["span"][0] if r.get("span") else r["t0"])
    return _h.sha256(json.dumps(r, sort_keys=True).encode()).hexdigest()[:16]


MAPPED = "bananashot-event-marks-mapped"


def map_marks(fid, warn=print):
    """The user's small-level marks inside the label span of the fragment fid (a medium or large fragment), at the same
    source time (manager, gap-program.md: the user's marks are reused; never detection results). Reads only labels():
    no retired id, no stale t0, no *.bak file. Each mark is copied as it is, with "from": the small id. A small label
    with "skip" is not used; an "unsure" one gives "fromUnsure": true (the page shows "?" on the mark). "manifest.row" is the
    rowHash of the page row of fid: the page does not load a mapped file made for another cut. Writes labels/<fid>.mapped.json; the label page
    shows these marks as the user's own until the first save of <fid>.events.json (the page then keeps the marks that
    the user did not move or delete, with their "from"). Returns the number of marks."""
    rows = {r["id"]: r for r in json.load(open(os.path.join(ROOT, "manifest.json")))["fragments"]}
    r = rows[fid]
    if r["level"] == "small":
        raise RuntimeError(f"{fid} is a small fragment: no marks are mapped into it")
    s0, s1 = r["span"]
    ev, src = [], {}
    for sid, L in labels(warn).items():
        if sid == fid or rows[sid]["match"] != r["match"] or rows[sid]["level"] != "small":
            continue
        if L.get("skip"):
            warn(f"map {fid}: {sid} is marked skip; not used")
            continue
        got = [dict(e, **{"from": sid}, **({"fromUnsure": True} if L.get("unsure") else {}))
               for e in L.get("events", []) if s0 - 1e-6 <= e["t"] <= s1 + 1e-6]
        if got:
            ev += got
            src[sid] = {"savedAt": L.get("savedAt"), "row": (L.get("manifest") or {}).get("row"), "marks": len(got),
                        "labeler": (L.get("source") or {}).get("labeler")}
    ev.sort(key=lambda e: e["t"])
    out = {"version": 1, "kind": MAPPED, "fragment": fid, "match": r["match"], "t0": r["t0"], "span": [s0, s1],
           "manifest": {"version": 1, "row": page_row_hash(fid)},           # the stale check of the page (S4)
           "rule": "the user's small-level marks (case_lib.labels) with t inside the span, copied at the same source time",
           "sources": src, "events": ev, "madeAt": time.strftime("%Y-%m-%dT%H:%M:%S")}
    with open(os.path.join(LABELS, fid + ".mapped.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    return len(ev)


def retire(fid, reason):
    """Takes a fragment out of the page index. Its folder and its id stay; the id is never used again."""
    p = os.path.join(FRAGS, fid, "fragment.json")
    m = json.load(open(p))
    m["retired"] = {"reason": reason, "time": time.strftime("%Y-%m-%dT%H:%M:%S")}
    json.dump(m, open(p, "w"), indent=1)


def index():
    """<ROOT>/manifest.json: one row for each fragment, in the format of the event label page (S4 item 10, agreed
    2026-11-26). Times are source times: source time = t0 + clip time (the player's currentTime), so
    t0 = offset - videoStartInClip. courtH: image px of a 1920-wide frame -> court m (the H of the court files), from
    the court window at the middle of the span (the refined track if it is there). Returns the path."""
    rows = []
    # the page lists the rows in this order: small first, then medium, large, match (the user labels the small level first)
    lv = {"small": 0, "medium": 1, "large": 2, "match": 3}

    def order(f):
        p = os.path.join(FRAGS, f, "fragment.json")
        return (lv.get(json.load(open(p)).get("level"), 9) if os.path.exists(p) else 9, f)
    for f in sorted(os.listdir(FRAGS), key=order) if os.path.exists(FRAGS) else []:
        p = os.path.join(FRAGS, f, "fragment.json")
        if not os.path.exists(p):
            continue
        m = json.load(open(p))
        if m.get("retired"):
            continue
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
    pub = published()
    for r in rows:
        if r["id"] in pub and abs(pub[r["id"]]["t0"] - r["t0"]) > 1e-6:
            raise RuntimeError(f"{r['id']} was published with t0 {pub[r['id']]['t0']}, now {r['t0']}: a changed fragment needs a new id")
        pub.setdefault(r["id"], {"t0": r["t0"], "match": r["match"], "span": r["span"], "time": time.strftime("%Y-%m-%dT%H:%M:%S")})
    out = os.path.join(ROOT, "manifest.json")
    json.dump({"version": 1, "fragments": rows}, open(out, "w"), indent=1)
    json.dump(pub, open(PUBLISHED, "w"), indent=1)
    return out


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("controls")
    c.add_argument("--n", type=int, default=2); c.add_argument("--seed", type=int, default=20261126); c.add_argument("--matches", default="")
    k = sub.add_parser("cut"); k.add_argument("spec")
    k.add_argument("--faults", action="store_true", help="fault clips (S4 E158): into FAULT_CLIPS, no index, clips.json {row: folder}")
    sub.add_parser("list")
    sub.add_parser("index")
    r = sub.add_parser("retire"); r.add_argument("id"); r.add_argument("--reason", required=True)
    mp = sub.add_parser("map", help="the user's small marks into the medium and large fragments (labels/<id>.mapped.json)")
    mp.add_argument("ids", nargs="*")
    a = ap.parse_args()
    if a.cmd == "controls":
        print(json.dumps(controls(a.n, a.seed, a.matches.split(",") if a.matches else None), indent=1))
    elif a.cmd == "cut":
        folder = FAULT_CLIPS if a.faults else FRAGS
        out = os.path.join(FAULT_CLIPS, "clips.json")
        clips = json.load(open(out)) if a.faults and os.path.exists(out) else {}
        for s in json.load(open(a.spec)):
            m = cut(s, folder)
            print(f"{m['id']}: {m['source']['mid']} offset {m['offset']:.3f} s, {m['duration']:.1f} s, points {len(m['points'])}, "
                  f"label span {m['labelSpan']}, first frame same {m['cut']['firstFrameSame']} ({m['cut']['seconds']} s)", flush=True)
            if a.faults:
                clips[s["row"]] = os.path.normpath(os.path.join(folder, s["id"]))
                json.dump(clips, open(out, "w"), indent=1)
        print("clips:", out) if a.faults else print("index:", index())
    elif a.cmd == "index":
        print(index())
    elif a.cmd == "map":
        rows = json.load(open(os.path.join(ROOT, "manifest.json")))["fragments"]
        for fid in a.ids or [r["id"] for r in rows if r["level"] != "small"]:
            print(f"{fid}: {map_marks(fid)} marks mapped")
    elif a.cmd == "retire":
        retire(a.id, a.reason)
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

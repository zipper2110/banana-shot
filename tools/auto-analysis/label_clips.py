"""Work list and rally clips for the hit labels of the user (gap program S4, G10; journal E70).

The user marks the hits (time, near or far player), the shot count and the first-serve faults with the page
`label_hits.html`. This script makes the input of that page:

  1. The work list. The rallies come from the marked points of the projects (gt.json), not from the chain
     output, so that the sample has no bias of the detector. For each match: a stratified random sample of
     the points with a winner (P1 or P2), by the marked length: short, middle and long are the thirds of the
     marked lengths of that match (the marked end includes some seconds after the rally, so fixed limits give
     almost no short points). Order: Igor (m1116) first, then the 4 lab matches in turns of 6 rallies. The first 10 rallies
     are the timing batch (Igor, mixed lengths).
  2. One clip for each rally: H.264, 1920 x 1080, the source frame times (no new frame rate), with the
     sound, a keyframe each 15 frames (fast frame steps in the browser). The first frame of the clip is at
     clip time 0. Its source time `t0` (pts, s) is in the work list, so: video time = t0 + clip time.
  3. The page and `worklist.js` in the label folder (a page on file:// cannot read a JSON file).

Clip window: from the marked start - 1.5 s to the marked end + 0.5 s. The marks of the lab matches skip a
first-serve fault (the point starts at the second serve). So a clip of a lab match also has a pre-roll of up
to 12 s before that window (not into the previous point), for the fault check. Igor has its first-serve
faults as clips of their own (gt.json "faults", E43), so its clips have no pre-roll.

Usage:
  python label_clips.py list                  write the work list (tools/auto-analysis/hit_worklist.json)
  python label_clips.py cut [--cpu] [--start n] [--first N] [--match m1116]
                                              cut the clips that do not exist yet, update worklist.js
  python label_clips.py page                  copy label_hits.html into the label folder

`cut` without --cpu uses the GPU decoder and encoder: start it through gpu_lock.py. `--cpu` decodes and
encodes on the CPU at a low priority (for a small batch while the GPU is busy).
"""
import argparse, json, os, random, shutil, subprocess, sys, time
import lab

HERE = os.path.dirname(os.path.abspath(__file__))
WORKLIST = os.path.join(HERE, "hit_worklist.json")
OUT = os.environ.get("LABEL_DIR", r"D:\bananashot-lab\ws\s4\label")
ORDER = ["m1116", "m1015", "m1020", "m1021", "m1030"]
PER_MATCH = {"m1116": 60, "m1015": 30, "m1020": 30, "m1021": 30, "m1030": 30}
STRATA = ["short", "middle", "long"]
PRE, POST, PREROLL, GAP = 1.5, 0.5, 12.0, 0.3     # s
SEED = 70
VERSION = 1


def sample(mid, n, rng):
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))
    clips = sorted(g["points"] + g.get("faults", []) + g.get("lets", []), key=lambda p: p["startMs"])
    has_faults = "faults" in g
    lens = sorted((p["endMs"] - p["startMs"]) / 1000.0 for p in g["points"] if p.get("outcome") in ("P1", "P2"))
    lim = [lens[len(lens) // 3], lens[2 * len(lens) // 3]]
    rows = []
    for i, p in enumerate(clips):
        if p.get("outcome") not in ("P1", "P2") or p not in g["points"]:
            continue
        a, b = p["startMs"] / 1000.0, p["endMs"] / 1000.0
        prev_end = clips[i - 1]["endMs"] / 1000.0 if i else 0.0
        begin = max(0.0, a - PRE, prev_end + GAP) if has_faults else max(0.0, min(a - PRE, max(prev_end + GAP, a - PRE - PREROLL)))
        rows.append({"match": mid, "point": p["id"], "startMs": p["startMs"], "endMs": p["endMs"], "outcome": p["outcome"],
                     "ending": p.get("ending"), "stratum": STRATA[(b - a >= lim[0]) + (b - a >= lim[1])], "clipBegin": round(begin, 3),
                     "clipEnd": round(b + POST, 3), "faultCheck": not has_faults})
    by = {s: [r for r in rows if r["stratum"] == s] for s in STRATA}
    for v in by.values():
        rng.shuffle(v)
    k = n // len(STRATA)
    pick = []
    for s in STRATA:
        pick += by[s][:k]
    rest = [r for s in STRATA for r in by[s][k:]]
    rng.shuffle(rest)
    pick += rest[:n - len(pick)]      # a stratum with too few points: fill from the others
    rng.shuffle(pick)
    return pick, {"limits": [round(x, 2) for x in lim], **{s: len(v) for s, v in by.items()}}


def make_list():
    rng = random.Random(SEED)
    per, pool = {}, {}
    for mid in ORDER:
        per[mid], pool[mid] = sample(mid, PER_MATCH[mid], rng)
    items = per["m1116"][:]
    lab_ids = ORDER[1:]
    while any(per[m] for m in lab_ids):
        for m in lab_ids:
            items += per[m][:6]
            per[m] = per[m][6:]
    for i, r in enumerate(items):
        r["n"] = i + 1
        r["clip"] = f"clips/{r['match']}/{i + 1:03d}_{r['point'][:8]}.mp4"
        r["timing"] = i < 10
    doc = {"version": VERSION, "kind": "bananashot-hit-worklist", "seed": SEED, "created": time.strftime("%Y-%m-%d"),
           "source": "marked points of the projects (gt.json, prep.py E43), not the chain output",
           "strata": "thirds of the marked lengths of each match (limits in pool)", "window": {"pre": PRE, "post": POST, "preroll": PREROLL},
           "videos": {m: lab.MATCHES[m]["video"] for m in ORDER}, "testOnly": [m for m in ORDER if m in lab.TEST_ONLY],
           "pool": pool, "items": items}
    json.dump(doc, open(WORKLIST, "w", encoding="utf-8"), indent=1)
    print(f"{len(items)} rallies: " + ", ".join(f"{m} {sum(r['match'] == m for r in items)}" for m in ORDER))
    print("pool by stratum: " + "; ".join(f"{m} {pool[m]}" for m in ORDER))


def first_pts(video, t):
    """Source time of the first video frame at or after t (the first frame of a cut from t)."""
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-read_intervals", f"{max(0.0, t - 2):.3f}%{t + 1:.3f}",
                          "-show_entries", "packet=pts_time", "-of", "csv=p=0", video], capture_output=True, text=True, check=True).stdout
    ts = sorted(float(x.strip(",")) for x in out.split() if x.strip(","))
    return next(x for x in ts if x >= t - 1e-4)


def cut_one(video, r, cpu):
    dst = os.path.join(OUT, r["clip"])
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    a, d = r["clipBegin"], r["clipEnd"] - r["clipBegin"]
    tmp = dst + ".part.mp4"
    if cpu:
        vin, vf, venc = [], "setpts=PTS-STARTPTS,scale=1920:1080:flags=bicubic", ["-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-threads", "4"]
    else:
        vin = ["-hwaccel", "cuda", "-hwaccel_output_format", "cuda"]
        vf = "setpts=PTS-STARTPTS,scale_cuda=1920:1080:format=yuv420p"
        venc = ["-c:v", "h264_nvenc", "-preset", "p5", "-rc", "vbr", "-cq", "21", "-b:v", "0"]
    cmd = (["ffmpeg", "-v", "error", "-y"] + vin + ["-ss", f"{a:.3f}", "-i", video, "-t", f"{d:.3f}", "-map", "0:v:0", "-map", "0:a:0?",
           "-vf", vf] + venc + ["-g", "15", "-bf", "0", "-pix_fmt", "yuv420p", "-fps_mode", "passthrough",
           "-af", "asetpts=PTS-STARTPTS", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", tmp])
    if not cpu:
        i = cmd.index("-pix_fmt")
        del cmd[i:i + 2]     # the format comes from scale_cuda
    flags = 0x4000 if (cpu and os.name == "nt") else 0     # BELOW_NORMAL_PRIORITY_CLASS
    subprocess.run(cmd, check=True, creationflags=flags)
    os.replace(tmp, dst)
    return first_pts(video, a)


def write_js(doc):
    """worklist.js for the page: the work list with the source time t0 of each clip that exists."""
    meta = os.path.join(OUT, "clips.json")
    t0 = json.load(open(meta, encoding="utf-8")) if os.path.exists(meta) else {}
    items = []
    for r in doc["items"]:
        x = dict(r)
        if r["clip"] in t0:
            x["t0"] = t0[r["clip"]]
        items.append(x)
    js = dict(doc, items=items)
    js.pop("pool", None)
    with open(os.path.join(OUT, "worklist.js"), "w", encoding="utf-8") as f:
        f.write("window.WORKLIST = " + json.dumps(js) + ";\n")


def cut(args):
    doc = json.load(open(WORKLIST, encoding="utf-8"))
    os.makedirs(OUT, exist_ok=True)
    meta = os.path.join(OUT, "clips.json")
    t0 = json.load(open(meta, encoding="utf-8")) if os.path.exists(meta) else {}
    todo = [r for r in doc["items"] if (not args.match or r["match"] == args.match)]
    todo = [r for r in todo if r["n"] >= args.start]
    todo = todo[:args.first] if args.first else todo
    t_start = time.time()
    for k, r in enumerate(todo):
        t0 = json.load(open(meta, encoding="utf-8")) if os.path.exists(meta) else {}
        if r["clip"] in t0 and os.path.exists(os.path.join(OUT, r["clip"])):
            continue
        try:
            t = cut_one(doc["videos"][r["match"]], r, args.cpu)
        except subprocess.CalledProcessError:
            if args.cpu:
                raise
            print("GPU cut failed, CPU cut", flush=True)
            t = cut_one(doc["videos"][r["match"]], r, True)
        t0 = json.load(open(meta, encoding="utf-8")) if os.path.exists(meta) else {}   # another cut can run at the same time
        t0[r["clip"]] = round(t, 6)
        json.dump(t0, open(meta, "w", encoding="utf-8"), indent=0)
        write_js(doc)
        print(f"{k + 1}/{len(todo)} {r['clip']} {r['clipEnd'] - r['clipBegin']:.1f} s  ({time.time() - t_start:.0f} s)", flush=True)
    write_js(doc)
    page()


def page():
    os.makedirs(OUT, exist_ok=True)
    shutil.copyfile(os.path.join(HERE, "label_hits.html"), os.path.join(OUT, "label_hits.html"))
    if os.path.exists(WORKLIST):
        write_js(json.load(open(WORKLIST, encoding="utf-8")))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["list", "cut", "page"])
    ap.add_argument("--cpu", action="store_true")
    ap.add_argument("--first", type=int, default=0)
    ap.add_argument("--start", type=int, default=1, help="first work list number (n) to cut")
    ap.add_argument("--match", default="")
    a = ap.parse_args()
    {"list": lambda: make_list(), "cut": lambda: cut(a), "page": lambda: page()}[a.cmd]()

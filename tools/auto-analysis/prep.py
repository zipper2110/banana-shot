"""Stage 1 data preparation for the auto-analysis lab.

For each match in matches.json this script writes into <labRoot>/<id>/:
  audio.flac     stereo, 24 kHz
  kf/%05d.jpg    one keyframe for each second, 1920 px wide
  kf_times.csv   the time (s) of each keyframe, line n = frame n
  gt.json        the hand-marked points and outcomes (if a project exists)

Run one match at a time: the GPU decodes only one video well.
Usage: python prep.py [--gt-only] [match id ...]
"""
import json, os, subprocess, sys, glob, time
import platform_cfg

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, "matches.json"), encoding="utf-8"))
ROOT = platform_cfg.data_root(CFG["labRoot"])
FFMPEG, FFPROBE = platform_cfg.tool("ffmpeg"), platform_cfg.tool("ffprobe")
PROJECTS = os.path.expandvars(CFG["projectsRoot"].replace("%APPDATA%", os.environ.get("APPDATA", "")))


def run(cmd):
    t = time.time()
    subprocess.run(cmd, check=True)
    return time.time() - t


def prep(m, out=None):
    out = out or os.path.join(ROOT, m["id"])
    os.makedirs(os.path.join(out, "kf"), exist_ok=True)
    v = m["video"]
    log = {}
    write_gt(m, out)
    return media(m, out, v, log)


def write_gt(m, out=None):
    """<out>/gt.json: the marked points and outcomes of the project of match m (if it has a project)."""
    out = out or os.path.join(ROOT, m["id"])
    if m.get("project"):
        pdir = os.path.join(PROJECTS, m["project"])
        edl = json.load(open(os.path.join(pdir, "edl.json"), encoding="utf-8"))
        score = json.load(open(os.path.join(pdir, "score.json"), encoding="utf-8"))
        outcomes = score.get("outcomes", {})
        details = score.get("details") or {}
        # E43: a project in the advanced scoring mode also has clips that are not points. A first-serve fault is a
        # clip with the ending SERVE_FAULT and no winner (a double fault has a winner: it is a point). A let is not
        # a point. These clips go to "faults" and "lets", not to "points": the first-serve faults are fault labels.
        pts, faults, lets = [], [], []
        for p in sorted(edl["points"], key=lambda p: p["startMs"]):
            o = outcomes.get(p["id"], "NONE")
            ending = (details.get(p["id"]) or {}).get("ending")
            row = {"id": p["id"], "startMs": p["startMs"], "endMs": p["endMs"], "outcome": o}
            if ending:
                row["ending"] = ending
            if ending == "SERVE_FAULT" and o not in ("P1", "P2"):
                faults.append(row)
            elif ending == "LET":
                lets.append(row)
            else:
                pts.append(row)
        # A server mark on a fault or let clip belongs to the next point: the same server serves it.
        marks = score.get("serverMarks") or {}
        kept = {p["id"] for p in pts}
        order = sorted(edl["points"], key=lambda p: p["startMs"])
        for i, p in enumerate(order):
            if p["id"] in marks and p["id"] not in kept:
                nxt = next((q for q in order[i + 1:] if q["id"] in kept), None)
                srv = marks.pop(p["id"])
                if nxt is not None and nxt["id"] not in marks:
                    marks[nxt["id"]] = srv
        score.pop("outcomes", None)
        score.pop("details", None)
        g = {"points": pts, "score": score}
        if faults or lets:
            g.update(faults=faults, lets=lets)
        json.dump(g, open(os.path.join(out, "gt.json"), "w", encoding="utf-8"), indent=1)


def media(m, out, v, log):
    """Audio, the keyframe times, and one keyframe for each second."""
    if not os.path.exists(os.path.join(out, "audio.flac")):
        log["audio_s"] = run([FFMPEG, "-v", "error", "-y", "-i", v, "-map", "0:a:0", "-ac", "2", "-ar", "24000",
                              os.path.join(out, "audio.flac")])
    if not os.path.exists(os.path.join(out, "kf_times.csv")):
        r = subprocess.run([FFPROBE, "-v", "error", "-select_streams", "v:0", "-show_entries",
                            "packet=pts_time,flags", "-of", "csv=p=0", v], capture_output=True, text=True, check=True)
        times = [ln.split(",")[0] for ln in r.stdout.splitlines() if ",K" in ln]
        ts = sorted(float(t) for t in times)
        gaps = sorted(b - a for a, b in zip(ts, ts[1:]))
        if gaps and not 0.9 < gaps[len(gaps) // 2] < 1.1:
            # No keyframe each second (other camera settings): take one frame each second with a full decode.
            end = max(float(ln.split(",")[0]) for ln in r.stdout.splitlines() if ln[:1].isdigit())
            times = [str(k) for k in range(int(end))]
            open(os.path.join(out, "kf.fps1"), "w").write("ok")
        open(os.path.join(out, "kf_times.csv"), "w").write("\n".join(times) + "\n")
        log["keyframes"] = len(times)
    if not os.path.exists(os.path.join(out, "kf.done")) and os.path.exists(os.path.join(out, "kf.fps1")):
        n = sum(1 for ln in open(os.path.join(out, "kf_times.csv")) if ln.strip())
        if platform_cfg.decode() == "cuda":
            # The frames stay on the GPU until the 1 fps selection and the scale (6x real time; with a CPU scale: 0.5x).
            log["kf_s"] = run([FFMPEG, "-v", "error", "-y", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda", "-i", v,
                               "-vf", "fps=1:round=down,scale_cuda=1920:-2,hwdownload,format=nv12",
                               "-frames:v", str(n), "-q:v", "3", os.path.join(out, "kf", "%05d.jpg")])
        else:
            # No NVIDIA GPU: the same pictures, with the scale_cuda kernel on the CPU (cuda_scale.py).
            import cuda_scale
            t = time.time()
            cuda_scale.keyframes_1fps(v, n, os.path.join(out, "kf", "%05d.jpg"))
            log["kf_s"] = time.time() - t
        have = len(glob.glob(os.path.join(out, "kf", "*.jpg")))
        if have < n:                                   # keep the count of the files and of the times equal
            open(os.path.join(out, "kf_times.csv"), "w").write("\n".join(str(k) for k in range(have)) + "\n")
        open(os.path.join(out, "kf.done"), "w").write("ok")
    if not os.path.exists(os.path.join(out, "kf.done")):
        # The GPU decoder gives NV12 frames to the software scaler. The software decoder gives yuv420p: the
        # filter format=nv12 keeps the input of the scaler the same.
        log["kf_s"] = run([FFMPEG, "-v", "error", "-y", "-skip_frame", "nokey"] + platform_cfg.hwaccel() + ["-i", v,
                           "-vf", "scale=1920:-2" if platform_cfg.decode() == "cuda" else "format=nv12,scale=1920:-2", "-fps_mode", "passthrough", "-q:v", "3",
                           os.path.join(out, "kf", "%05d.jpg")])
        log["kf_files"] = len(glob.glob(os.path.join(out, "kf", "*.jpg")))
        open(os.path.join(out, "kf.done"), "w").write("ok")
    print(m["id"], log, flush=True)


if __name__ == "__main__":
    gt_only = "--gt-only" in sys.argv                  # only gt.json: no decode
    ids = [a for a in sys.argv[1:] if not a.startswith("--")]
    for m in CFG["matches"]:
        if not ids or m["id"] in ids:
            write_gt(m) if gt_only else prep(m)

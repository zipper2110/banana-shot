"""Stage 1 data preparation for the auto-analysis lab.

For each match in matches.json this script writes into <labRoot>/<id>/:
  audio.flac     stereo, 24 kHz
  kf/%05d.jpg    one keyframe for each second, 1920 px wide
  kf_times.csv   the time (s) of each keyframe, line n = frame n
  gt.json        the hand-marked points and outcomes (if a project exists)

Run one match at a time: the GPU decodes only one video well.
Usage: python prep.py [match id ...]
"""
import json, os, subprocess, sys, glob, time

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, "matches.json"), encoding="utf-8"))
ROOT = CFG["labRoot"]
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
    if m.get("project"):
        pdir = os.path.join(PROJECTS, m["project"])
        edl = json.load(open(os.path.join(pdir, "edl.json"), encoding="utf-8"))
        score = json.load(open(os.path.join(pdir, "score.json"), encoding="utf-8"))
        outcomes = score.get("outcomes", {})
        pts = [{"id": p["id"], "startMs": p["startMs"], "endMs": p["endMs"], "outcome": outcomes.get(p["id"], "NONE")}
               for p in sorted(edl["points"], key=lambda p: p["startMs"])]
        score.pop("outcomes", None)
        json.dump({"points": pts, "score": score}, open(os.path.join(out, "gt.json"), "w", encoding="utf-8"), indent=1)
    if not os.path.exists(os.path.join(out, "audio.flac")):
        log["audio_s"] = run(["ffmpeg", "-v", "error", "-y", "-i", v, "-map", "0:a:0", "-ac", "2", "-ar", "24000",
                              os.path.join(out, "audio.flac")])
    if not os.path.exists(os.path.join(out, "kf_times.csv")):
        r = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
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
        # The frames stay on the GPU until the 1 fps selection and the scale (6x real time; with a CPU scale: 0.5x).
        log["kf_s"] = run(["ffmpeg", "-v", "error", "-y", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda", "-i", v,
                           "-vf", "fps=1:round=down,scale_cuda=1920:-2,hwdownload,format=nv12",
                           "-frames:v", str(n), "-q:v", "3", os.path.join(out, "kf", "%05d.jpg")])
        have = len(glob.glob(os.path.join(out, "kf", "*.jpg")))
        if have < n:                                   # keep the count of the files and of the times equal
            open(os.path.join(out, "kf_times.csv"), "w").write("\n".join(str(k) for k in range(have)) + "\n")
        open(os.path.join(out, "kf.done"), "w").write("ok")
    if not os.path.exists(os.path.join(out, "kf.done")):
        log["kf_s"] = run(["ffmpeg", "-v", "error", "-y", "-skip_frame", "nokey", "-hwaccel", "cuda", "-i", v,
                           "-vf", "scale=1920:-2", "-fps_mode", "passthrough", "-q:v", "3",
                           os.path.join(out, "kf", "%05d.jpg")])
        log["kf_files"] = len(glob.glob(os.path.join(out, "kf", "*.jpg")))
        open(os.path.join(out, "kf.done"), "w").write("ok")
    print(m["id"], log, flush=True)


if __name__ == "__main__":
    ids = sys.argv[1:]
    for m in CFG["matches"]:
        if not ids or m["id"] in ids:
            prep(m)

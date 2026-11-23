"""Short sample videos for a first, quick test of a detection change (E31).

For each video: the 3 minutes with the most play from the middle half of the match. The cut is a stream copy from
a keyframe (no new encoding, so the pictures are the same as in the full video).

Output folder: <lab root>/../samples (D:/bananashot-lab/samples):
  <id>_sample.mp4    the sample video
  samples.json       for each sample: the source video, the start in the source (s), the length, and the hand marks
                     of the lab match inside the sample with times of the sample (start, end, outcome)

Usage: python make_samples.py [match id ...]      default: the 4 marked singles matches and m0913
The play of a lab match comes from its hand marks. For a video with no marks, give the result file of detect.py:
  python make_samples.py m0913=D:/bananashot-lab/app/m0913/result.json
"""
import json, os, subprocess, sys
import lab

LEN, STEP = 180.0, 10.0
OUT = os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "samples")
DEFAULT = ["m1015", "m1020", "m1021", "m1030", "m0913=D:/bananashot-lab/app/m0913/result.json"]


def play_ranges(mid, result):
    """The ranges of play (start, end, outcome or None) of a video, in s."""
    if result:
        return [(r["start"], r["end"], None) for r in json.load(open(result, encoding="utf-8"))["rallies"]]
    return lab.gt_points(mid)


def densest(ranges):
    """Start of the window of LEN s with the most play. The centre of the window is in the middle half of the play."""
    a, b = ranges[0][0], ranges[-1][1]
    lo, hi = a + 0.25 * (b - a) - LEN / 2, a + 0.75 * (b - a) - LEN / 2
    best, t = (-1.0, lo), lo
    while t <= hi:
        play = sum(max(0.0, min(e, t + LEN) - max(s, t)) for s, e, _ in ranges)
        best = max(best, (play, t))
        t += STEP
    return best[1], best[0]


def keyframe_before(video, t):
    """Time of the last keyframe at or before t."""
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-skip_frame", "nokey", "-read_intervals", f"{max(0.0, t - 6):.3f}%{t + 0.5:.3f}",
                          "-show_entries", "frame=pts_time", "-of", "csv=p=0", video], capture_output=True, text=True, check=True).stdout
    ks = [float(x.strip().strip(",")) for x in out.split() if x.strip().strip(",")]
    return max([k for k in ks if k <= t + 1e-3] or [min(ks)])


def run(spec):
    mid, _, result = spec.partition("=")
    video = lab.MATCHES[mid]["video"]
    ranges = sorted(play_ranges(mid, result))
    t, play = densest(ranges)
    k = keyframe_before(video, t)
    name = f"{mid}_sample.mp4"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{k:.3f}", "-i", video, "-t", f"{LEN:.3f}", "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy",
                    "-avoid_negative_ts", "make_zero", os.path.join(OUT, name)], check=True)
    marks = [{"start": round(s - k, 3), "end": round(e - k, 3), "outcome": o} for s, e, o in ranges if s >= k and e <= k + LEN] if not result else None
    print(f"{mid}: {name} from {k:.2f} s, play {play:.0f} s of {LEN:.0f} s" + (f", {len(marks)} marked points" if marks is not None else ""), flush=True)
    return {"id": mid, "file": name, "source": video, "sourceStart": k, "seconds": LEN, "playSeconds": round(play, 1), "marks": marks}


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "samples.json")
    old = {s["id"]: s for s in json.load(open(path))} if os.path.exists(path) else {}
    for spec in (sys.argv[1:] or DEFAULT):
        s = run(spec)
        old[s["id"]] = s
    json.dump(list(old.values()), open(path, "w"), indent=1)

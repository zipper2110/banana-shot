"""Presentation times of all video frames of a match (ffprobe, no decode).

The phone videos are not exactly 60 fps (m1030: 59.88 fps average, dropped frames). A long
decode pass (ball_wasb.py over a full match) numbers the frames; frame / 60 then drifts by up to
11 s. Use times(mid, start)[frame] for the time of a frame of a pass that starts at "start" s.
Writes <match>/pts.npy (sorted times, s). Usage: python frame_times.py [match id ...]
"""
import os, subprocess, sys
import numpy as np
import lab
import platform_cfg


def load(mid):
    p = os.path.join(lab.mdir(mid), "pts.npy")
    if not os.path.exists(p):
        out = subprocess.run([platform_cfg.tool("ffprobe"), "-v", "error", "-select_streams", "v:0", "-show_entries", "packet=pts_time",
                              "-of", "csv=p=0", lab.MATCHES[mid]["video"]], stdout=subprocess.PIPE).stdout.decode()
        t = np.sort(np.array([float(x.strip(",")) for x in out.split() if x.strip(",")], np.float64))
        np.save(p, t)
    return np.load(p)


def times(mid, start):
    """Times of the frames of a decode pass that starts at "start" s (ffmpeg -ss start)."""
    t = load(mid)
    return t[np.searchsorted(t, start - 1e-4):]


if __name__ == "__main__":
    for m in (sys.argv[1:] or ["m1015", "m1020", "m1021", "m1030"]):
        t = load(m)
        d = np.diff(t)
        print(m, "frames", len(t), "mean fps %.3f" % (1 / d.mean()), "gaps > 1.5 frames:", int((d > 0.025).sum()), "max gap %.3f s" % d.max(), flush=True)

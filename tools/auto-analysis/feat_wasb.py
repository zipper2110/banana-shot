"""E20: per-second ball features from the learned detector (WASB) and the 3D flights.
Writes <match>/feat_wasb.npy (NaN outside the full-match window).

Columns: share of frames with a blob; blobs for each frame; mean peak; flights that start;
share of the second inside a good flight; max flight speed (km/h, max 200); flights over
the net; serve event (serve_ball.serve_events) in this second.
Usage: python feat_wasb.py [match id ...]
"""
import json, os, sys
import numpy as np
import lab
import frame_times
from ball_full import WINDOWS
from serve_ball import serve_events


def good(f):
    return f["n"] >= 8 and f["rms"] < 5


def run(mid):
    start, dur = WINDOWS[mid]
    n = lab.n_seconds(mid)
    F = np.full((n, 8), np.nan, np.float32)
    s0, s1 = start, min(n, start + dur)
    F[s0:s1] = 0
    d = np.load(os.path.join(lab.mdir(mid), f"wasb_{start}_{dur}_s0.35_k2.npy"))
    tt = frame_times.times(mid, start)
    sec = np.clip(tt[np.minimum(d[:, 0].astype(int), len(tt) - 1)].astype(int), 0, n - 1)
    top = np.ones(len(d), bool); top[1:] = d[1:, 0] != d[:-1, 0]          # first blob of each frame
    F[s0:s1, 0] = np.bincount(sec[top], minlength=n)[s0:s1] / 60.0
    F[s0:s1, 1] = np.bincount(sec, minlength=n)[s0:s1] / 60.0
    pk = np.bincount(sec[top], weights=d[top, 4], minlength=n)[s0:s1]
    F[s0:s1, 2] = pk / np.maximum(1, F[s0:s1, 0] * 60)
    full = json.load(open(os.path.join(lab.mdir(mid), "fullflights_s0.35.json")))
    for f in full["flights"]:
        if not good(f):
            continue
        a, b = f["t0"], f["t1"]
        sp = min(200.0, 3.6 * float(np.linalg.norm(f["v0"])))
        over = f["p0"][1] * f["p1"][1] < 0
        if s0 <= int(a) < s1:
            F[int(a), 3] += 1
        for s in range(max(s0, int(a)), min(s1, int(b) + 1)):
            F[s, 4] += max(0.0, min(b, s + 1) - max(a, s))
            F[s, 5] = max(F[s, 5], sp)
            F[s, 6] += over
    ev = serve_events(full["flights"], start, start + dur)
    for t, near, kind, f in ev:
        if s0 <= int(t) < s1:
            F[int(t), 7] = 1
    np.save(os.path.join(lab.mdir(mid), "feat_wasb.npy"), F)
    print(f"{mid}: seconds {s1 - s0}, in a flight {100 * (F[s0:s1, 4] > 0).mean():.0f}%, serve events {len(ev)}", flush=True)


if __name__ == "__main__":
    for m in (sys.argv[1:] or list(WINDOWS)):
        run(m)

"""E20: 3D flights over the full marked part of a match.

Input: the full-match blob file of ball_wasb.py (wasb_<start>_<dur>_s0.35_k2.npy,
windows in WINDOWS). The script cuts the file into chunks of CHUNK s. Each chunk
is fitted with a margin of PAD s on each side (ball_3d.fit_window). A flight is
kept in the chunk where it starts.
Output: <match>/fullflights_s0.35.json: {"start", "dur", "flights", "kinks"}.
Times are in seconds of the video (true frame times, frame_times.py).
Usage: python ball_full.py [match id ...]
"""
import json, os, sys, time
import numpy as np
import lab
import ball_3d
import frame_times

WINDOWS = {"m1015": (832, 2931), "m1020": (54, 4923), "m1021": (873, 5999), "m1030": (700, 4675)}
CHUNK, PAD = 30.0, 2.0


def run(mid):
    start, dur = WINDOWS[mid]
    d = np.load(os.path.join(lab.mdir(mid), f"wasb_{start}_{dur}_s0.35_k2.npy"))
    d = d[np.argsort(d[:, 0], kind="stable")]
    tt = frame_times.times(mid, start)                  # true time of each frame (the video is not exactly 60 fps)
    ft = tt[np.minimum(d[:, 0].astype(int), len(tt) - 1)]
    fls, kks, t0 = [], [], time.time()
    c = 0.0
    while c < dur:
        a, b = max(0.0, c - PAD), min(float(dur), c + CHUNK + PAD)
        i0, i1 = np.searchsorted(ft, start + a), np.searchsorted(ft, start + b)
        rows = d[i0:i1].copy()
        k0 = int(np.searchsorted(tt, start + a))
        rows[:, 0] -= k0
        a = float(tt[min(k0, len(tt) - 1)]) - start      # time of chunk frame 0
        if len(rows) >= 8:
            try:
                r = ball_3d.fit_window(mid, rows, start + a, start + b, start + c)
            except Exception as e:                       # a chunk with no usable path
                print(f"{mid} chunk {c:.0f}: {type(e).__name__} {e}", flush=True)
                r = {"flights": [], "kinks": []}
            lo, hi = start + c, start + c + CHUNK
            fls += [f for f in r["flights"] if lo <= f["t0"] < hi]
            kks += [k for k in r["kinks"] if lo <= k[0] < hi]
        c += CHUNK
    json.dump({"start": start, "dur": dur, "flights": fls, "kinks": kks},
              open(os.path.join(lab.mdir(mid), "fullflights_s0.35.json"), "w"), default=float)
    print(f"{mid}: flights {len(fls)}, kinks {len(kks)}, {time.time() - t0:.0f} s", flush=True)


if __name__ == "__main__":
    for m in (sys.argv[1:] or list(WINDOWS)):
        run(m)

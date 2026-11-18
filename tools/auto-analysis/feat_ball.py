"""Ball-in-flight features from the burst tracklets (burst_ball.py), one
row for each second. Writes <match>/feat_ball.npy.

Columns: count of tracklets; count with speed > 5 px/frame; count with 4
frames and speed > 5; count with ball color (yellow > 0.2); count with
ball color and speed > 5; max speed; max speed of the ball-colored ones;
mean image y of the ball-colored ones (0 = none).
Usage: python feat_ball.py [match id ...]
"""
import os, sys
import numpy as np
import lab


def run(mid):
    tr = np.load(os.path.join(lab.mdir(mid), "tracklets.npy"))
    n = lab.n_seconds(mid)
    f = np.zeros((n, 8), dtype=np.float32)
    sp = np.hypot(tr[:, 3], tr[:, 4])
    for r, v in zip(tr, sp):
        s = int(r[0])
        if s >= n:
            continue
        yel = r[7] > 0.2
        f[s, 0] += 1; f[s, 1] += v > 5; f[s, 2] += (v > 5) and (r[5] >= 4)
        f[s, 3] += yel; f[s, 4] += yel and v > 5
        f[s, 5] = max(f[s, 5], v)
        if yel:
            f[s, 6] = max(f[s, 6], v); f[s, 7] += r[2]
    f[:, 7] = np.where(f[:, 3] > 0, f[:, 7] / np.maximum(f[:, 3], 1), 0)
    np.save(os.path.join(lab.mdir(mid), "feat_ball.npy"), f)
    print(mid, f.shape, "seconds with a ball-colored tracklet: %.0f%%" % (100 * (f[:, 3] > 0).mean()), flush=True)


if __name__ == "__main__":
    for mid in (sys.argv[1:] or lab.LABELED):
        if os.path.exists(os.path.join(lab.mdir(mid), "tracklets.npy")):
            run(mid)

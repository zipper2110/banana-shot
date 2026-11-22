"""One ball path for each point, and its "kinks" (sudden velocity changes
= bounces and hits). Input: blobs from ball_wasb.py.

Steps:
  1. ball_tracks.link + moving: chains of blobs that move.
  2. select: one path. Longest tracks first; a track is dropped if it
     overlaps a selected track in time.
  3. Scale: pixels for 1 m at each image row, from the court model. The
     ball velocity in px/s divided by this value = "local meters" per
     second. (A direct map of the ball to court meters fails in flight:
     the ball is above the ground, often above the horizon.)
  4. kinks: velocity before and after each frame (line fits on 5 frames).
     A kink = a local maximum of the velocity change above KINK (local
     m/s). A gap between two tracks is also a kink.
"""
import json, os, sys
import numpy as np
import cv2
import lab
from ball_tracks import link, moving

KINK, HALF = 6.0, 5


_TRACK = {}


def court_H(mid, t=None):
    """Homography of the 1920-wide frame to court meters at time t. Uses court_track.json
    (court_track.py, one model each 60 s): the window of t if it is ok, else the nearest ok
    window in time. Falls back to court.json (one model at mid-match)."""
    p = os.path.join(lab.mdir(mid), "court_track.json")
    if t is not None and os.path.exists(p):
        if mid not in _TRACK:
            _TRACK[mid] = [w for w in json.load(open(p)) if w.get("ok", True)]
        ws = _TRACK[mid]
        if ws:
            w = min(ws, key=lambda w: 0 if w["t0"] <= t < w["t1"] else min(abs(t - w["t0"]), abs(t - w["t1"])))
            return np.array(w["H"], np.float64)
    return np.array(json.load(open(os.path.join(lab.mdir(mid), "court.json")))["H"], np.float64)


def to_court(xy, Hm):
    return cv2.perspectiveTransform(np.asarray(xy, np.float64).reshape(1, -1, 2), Hm)[0]


def select(tracks):
    """Returns one path: array of (frame, x, y, peak, track id), sorted by frame."""
    chosen, busy = [], []
    for t in sorted(tracks, key=lambda t: -len(t)):
        a, b = t[0, 0], t[-1, 0]
        if all(b < c + 3 or a > d - 3 for c, d in busy):
            busy.append((a, b)); chosen.append(t)
    chosen.sort(key=lambda t: t[0, 0])
    out = [np.concatenate([t, np.full((len(t), 1), i, np.float32)], 1) for i, t in enumerate(chosen)]
    return np.concatenate(out) if out else np.zeros((0, 5), np.float32)


class Scale:
    """Image geometry of the court for the normalization of ball positions.
    m(y) = pixels for 1 m of court width at image row y (on the ground).
    Rows above the far baseline use the far-baseline value.
    depth(y): 0 = near baseline row, 0.5 = net row, 1 = far baseline row
    (linear in the image between these rows; can be < 0 or > 1)."""

    def __init__(self, Hm):
        inv = np.linalg.inv(Hm)
        ys = np.linspace(-lab_BL, lab_BL, 49)
        c = cv2.perspectiveTransform(np.array([[(0.0, y) for y in ys]]), inv)[0]
        r = cv2.perspectiveTransform(np.array([[(1.0, y) for y in ys]]), inv)[0]
        self.rows, self.ppm, self.cx = c[:, 1][::-1], np.hypot(*(r - c).T)[::-1], c[:, 0][::-1]   # rows grow: far -> near
        self.y_nb, self.y_net, self.y_fb = c[0, 1], c[24, 1], c[-1, 1]
        self.Hm = Hm

    def m(self, y):
        return np.interp(y, self.rows, self.ppm)

    def center(self, y):
        return np.interp(y, self.rows, self.cx)

    def depth(self, y):
        y = np.asarray(y, np.float64)
        return np.where(y > self.y_net, 0.5 * (self.y_nb - y) / (self.y_nb - self.y_net),
                        0.5 + 0.5 * (self.y_net - y) / (self.y_net - self.y_fb))


lab_BL = 11.885


def kinks(path, sc, thr=None):
    """Returns rows: frame, x, y (px), dvx, dvy (after - before), vbx, vby, vax, vay, gap.
    Velocities are in "local meters" per second: px/s divided by sc.m(y). y grows downward.
    gap = 1: the kink is between two tracks."""
    rows = []
    if len(path) < 2 * HALF:
        return np.zeros((0, 10), np.float32)
    f, tid, x, y = path[:, 0], path[:, 4], path[:, 1], path[:, 2]
    ppm = sc.m(y)

    def vel(idx, at):
        if len(idx) < 3:
            return None
        return np.polyfit(f[idx], x[idx], 1)[0] * 60 / ppm[at], np.polyfit(f[idx], y[idx], 1)[0] * 60 / ppm[at]

    score, cand = np.zeros(len(path)), {}
    for i in range(len(path)):
        b = np.nonzero((tid == tid[i]) & (f <= f[i]) & (f >= f[i] - HALF))[0]
        a = np.nonzero((tid == tid[i]) & (f >= f[i]) & (f <= f[i] + HALF))[0]
        vb, va = vel(b, i), vel(a, i)
        if vb is None or va is None:
            continue
        score[i] = np.hypot(va[0] - vb[0], va[1] - vb[1])
        cand[i] = (vb, va)
    for i in cand:
        lo, hi = max(0, i - HALF), min(len(path), i + HALF + 1)
        if score[i] >= (thr or KINK) and score[i] == score[lo:hi].max():
            vb, va = cand[i]
            rows.append((f[i], x[i], y[i], va[0] - vb[0], va[1] - vb[1], *vb, *va, 0))
    for t in np.unique(tid)[:-1]:                      # gaps between tracks
        b = np.nonzero(tid == t)[0][-HALF:]
        a = np.nonzero(tid == t + 1)[0][:HALF]
        m = b[-1]
        vb, va = vel(b, m), vel(a, m)
        if vb is None or va is None:
            continue
        rows.append(((f[b[-1]] + f[a[0]]) / 2, x[m], y[m], va[0] - vb[0], va[1] - vb[1], *vb, *va, 1))
    rows.sort()
    return np.array(rows, np.float32).reshape(-1, 10)


if __name__ == "__main__":
    # test on one window file: python ball_events.py <match> <start> <dur> <tag>
    mid, start, dur, tag = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
    d = np.load(os.path.join(lab.mdir(mid), f"wasb_{int(start)}_{int(dur)}_{tag}.npy"))
    path = select(moving(link(d)))
    sc = Scale(court_H(mid, start))
    k = kinks(path, sc)
    print("rows near baseline, net, far baseline:", round(sc.y_nb), round(sc.y_net), round(sc.y_fb), "| path frames", len(path), "kinks", len(k))
    for r in k:
        print(f"t {start + r[0] / 60:8.2f}  px ({r[1]:5.0f}, {r[2]:5.0f}) depth {float(sc.depth(r[2])):5.2f}  v before ({r[5]:6.1f}, {r[6]:6.1f})  after ({r[7]:6.1f}, {r[8]:6.1f})  gap {int(r[9])}")

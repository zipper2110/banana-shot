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


# E26f, E29: the refined court model (court_refine.py) corrects the ground positions of a video if the far court
# points move more than FAR_FAULT_M (median of the windows that have 2 refined far lines). E29: the limit is 0, so
# each video with a refined model uses it (line error 13 cm -> 4 cm; full chain with the shot chain 80.1 -> 80.3 %).
# On the 4 marked matches the two models agree within 0.15 m. On the low-camera video the first model has no fit of
# the far half, and the far points move 1.8 m.
# The 3D camera of the flights always comes from the first model (H): with the camera of the refined model, only
# 145 of 351 bounce events of the low-camera video passed the bounce test of ball_bounce.py. The refined model then
# gives the place on the court of each ground point (window key "ground", function ground_fix).
FAR_FAULT_M = float(os.environ.get("FAR_FAULT_M", "0"))      # E29: 0 = each video with a refined model uses it for the ground points (before: 0.5)
_FAR_PTS = np.array([[-4.115, 11.885], [4.115, 11.885], [-4.115, 6.4], [4.115, 6.4]])


def court_windows(folder, log=None):
    """The ok windows of the court model of a folder: court_track.json, with the H of court_track_refined.json
    where the rule above selects it. The environment variable COURT_TRACK selects one file for a comparison."""
    name = os.environ.get("COURT_TRACK")
    ws = [w for w in json.load(open(os.path.join(folder, name or "court_track.json"))) if w.get("ok", True)]
    rp = os.path.join(folder, "court_track_refined.json")
    if name or not os.path.exists(rp):
        return ws
    ref = {w["t0"]: w for w in json.load(open(rp)) if w.get("refine", {}).get("done") and w["refine"].get("far_lines", 0) >= 2}
    shift = {}
    for w in ws:
        r = ref.get(w["t0"])
        if r is not None:
            px = cv2.perspectiveTransform(_FAR_PTS.reshape(1, -1, 2), np.linalg.inv(np.array(w["H"], np.float64)))
            shift[w["t0"]] = float(np.abs(cv2.perspectiveTransform(px, np.array(r["H"], np.float64))[0] - _FAR_PTS).max())
    use = len(shift) >= len(ws) / 2 and float(np.median(list(shift.values()))) > FAR_FAULT_M
    if log:
        log(f"court model: {'refined' if use else 'first'} (far points move {np.median(list(shift.values())) if shift else float('nan'):.2f} m, "
            f"{len(shift)} of {len(ws)} windows)")
    if use:
        ws = [dict(w, ground=ref[w["t0"]]) if w["t0"] in shift else w for w in ws]
    return ws


def window_at(ws, t):
    return min(ws, key=lambda w: 0 if w["t0"] <= t < w["t1"] else min(abs(t - w["t0"]), abs(t - w["t1"])))


def ground_fix(ws, t, xy):
    """A ground point (court m of the first model, at time t) -> court m of the refined model, through its place
    in the picture. No change for a window with no refined model."""
    w = window_at(ws, t)
    if "ground" not in w or xy is None or not np.all(np.isfinite(np.asarray(xy[:2], np.float64))):
        return xy
    import court_refine
    px = cv2.perspectiveTransform(np.asarray(xy[:2], np.float64).reshape(1, 1, 2), np.linalg.inv(np.array(w["H"], np.float64)))[0]
    q = court_refine.to_court(w["ground"], px)[0]
    return [float(q[0]), float(q[1])] + [float(v) for v in xy[2:]]


def court_H(mid, t=None):
    """Homography of the 1920-wide frame to court meters at time t. Uses the court model of each 60 s window
    (court_windows): the window of t if it is ok, else the nearest ok window in time. Falls back to court.json
    (one model at mid-match)."""
    p = os.path.join(lab.mdir(mid), os.environ.get("COURT_TRACK", "court_track.json"))
    if t is not None and os.path.exists(p):
        if mid not in _TRACK:
            _TRACK[mid] = court_windows(lab.mdir(mid))
        ws = _TRACK[mid]
        if ws:
            return np.array(window_at(ws, t)["H"], np.float64)
    return np.array(json.load(open(os.path.join(lab.mdir(mid), "court.json")))["H"], np.float64)


def to_court(xy, Hm):
    return cv2.perspectiveTransform(np.asarray(xy, np.float64).reshape(1, -1, 2), Hm)[0]


SELECT_TRIM = os.environ.get("SELECT_TRIM", "1") == "1"


def select(tracks):
    """Returns one path: array of (frame, x, y, peak, track id), sorted by frame.
    The longest track is first. A track that is at the same time as a chosen track loses only the samples of that
    time (E41). Before E41 the full track was rejected: a ball track of 3.8 s was lost because its first 0.2 s were
    at the same time as the end of the track before it. SELECT_TRIM=0 in the environment gives the old rule."""
    chosen, busy = [], []
    for t in sorted(tracks, key=lambda t: -len(t)):
        a, b = t[0, 0], t[-1, 0]
        if all(b < c + 3 or a > d - 3 for c, d in busy):
            busy.append((a, b)); chosen.append(t)
        elif SELECT_TRIM:
            free = np.ones(len(t), bool)
            for c, d in busy:
                free &= (t[:, 0] <= c - 3) | (t[:, 0] >= d + 3)
            # the parts of the track in the free time: each part must be a moving track of its own
            edges = np.flatnonzero(np.diff(np.concatenate([[0], free.astype(np.int8), [0]])))
            for i0, i1 in zip(edges[::2], edges[1::2]):
                for part in moving([t[i0:i1]]):
                    busy.append((part[0, 0], part[-1, 0])); chosen.append(part)
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

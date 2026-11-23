"""E16f: lab tools for the net events (net_events.py): data sources, pictures, hand labels.

A source is a folder with the cached files of one video: the 4 singles lab matches
(D:/bananashot-lab/data/<match>) and the app work folder of the low-camera video (m0913).
  python net_lab.py pic <source> <t0> <t1> <out.jpg>      one picture: blobs, kinks, net band
  python net_lab.py strip <source> <t> <out.jpg> [x y]    6 video frames around t (crop at x, y)
"""
import json, os, subprocess, sys
import numpy as np
import cv2
import lab
import ball_3d
from ball_full import WINDOWS

OUT = "D:/bananashot-lab/e16f"
APP = "D:/bananashot-lab/app/PXL_20260913_070045619"
SOURCES = ["m1015", "m1020", "m1021", "m1030", "m0913"]
NET_POST_X, NET_POST_Z, NET_MID_Z = 6.40, 1.07, 0.914


class Source:
    """Blobs with true times, flights, kinks, and the court model of one video."""

    def __init__(self, sid):
        self.sid = sid
        if sid == "m0913" or os.path.isdir(sid):             # a work folder of the app
            self.dir, self.video, start = (APP, "D:/PXL_20260913_070045619.mp4", 0) if sid == "m0913" else (sid, None, 0)
            name = [x for x in os.listdir(self.dir) if x.startswith("wasb_") and x.endswith("_s0.35_k2.npy")][0]
            tt = np.load(os.path.join(self.dir, "pts.npy"))
        else:
            self.dir, self.video = lab.mdir(sid), lab.MATCHES[sid]["video"]
            start, dur = WINDOWS[sid]
            name = f"wasb_{start}_{dur}_s0.35_k2.npy"
            tt = np.load(os.path.join(self.dir, "pts.npy"))
            tt = tt[np.searchsorted(tt, start - 1e-4):]
        d = np.load(os.path.join(self.dir, name))
        d = d[np.argsort(d[:, 0], kind="stable")]
        self.frame_t = tt
        self.t = tt[np.minimum(d[:, 0].astype(int), len(tt) - 1)]
        self.d = d                                           # frame, x, y, score, peak
        self.full = json.load(open(os.path.join(self.dir, "fullflights_s0.35.json")))
        self.track = [w for w in json.load(open(os.path.join(self.dir, "court_track.json"))) if w.get("ok", True)]

    def H(self, t):
        w = min(self.track, key=lambda w: 0 if w["t0"] <= t < w["t1"] else min(abs(t - w["t0"]), abs(t - w["t1"])))
        return np.array(w["H"], np.float64)

    def rows(self, t0, t1):
        """Blobs of a time range as rows (t, x, y, score, peak)."""
        i0, i1 = np.searchsorted(self.t, t0), np.searchsorted(self.t, t1)
        return np.column_stack([self.t[i0:i1], self.d[i0:i1, 1:5]])

    def flights(self, t0, t1):
        return [f for f in self.full["flights"] if f["t1"] >= t0 and f["t0"] <= t1]

    def kinks(self, t0, t1):
        return [k for k in self.full["kinks"] if t0 <= k[0] <= t1]

    def points(self):
        """Marked points (start, end, winner is the near player or None). Empty for m0913."""
        if self.sid not in lab.MATCHES:
            return []
        ends = json.load(open(os.path.join(self.dir, "ends.json")))
        return [(a, b, e["winnerNear"]) for (a, b, _), e in zip(lab.gt_points(self.sid), ends)]


def frame_at(video, t):
    cmd = ["ffmpeg", "-v", "error", "-ss", f"{t:.3f}", "-i", video, "-frames:v", "1", "-vf", "scale=1920:-2",
           "-f", "image2pipe", "-vcodec", "mjpeg", "-q:v", "3", "-"]
    data = subprocess.run(cmd, stdout=subprocess.PIPE).stdout
    return cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)


def net_px(P, z_scale=1.0, n=17):
    """Image points of the top cord of the net (post to post) and of the net base line."""
    xs = np.linspace(-NET_POST_X, NET_POST_X, n)
    hz = NET_MID_Z + (NET_POST_Z - NET_MID_Z) * np.abs(xs) / NET_POST_X
    top = ball_3d.project(P, np.column_stack([xs, 0 * xs, hz * z_scale]))
    base = ball_3d.project(P, np.column_stack([xs, 0 * xs, 0 * xs]))
    return top, base


def draw(src, t0, t1, out=None, zoom=2.0, events=(), frame_t=None, crop=None):
    """Picture: the video frame at t1 (or frame_t), all blobs of [t0, t1] (blue = old, green = new; a small
    number = the time, s modulo 100, at each 6th blob), the kinks (magenta), the net cord and the net base line
    (yellow), the projected flight fits (white), and the net events (cyan diamond)."""
    import net_events
    img = frame_at(src.video, t1 if frame_t is None else frame_t)
    P = ball_3d.camera(src.H(t0))[0]
    top, base = net_px(P)
    cv2.polylines(img, [np.int32(top)], False, (0, 255, 255), 1, cv2.LINE_AA)
    cv2.polylines(img, [np.int32(base)], False, (0, 200, 255), 1, cv2.LINE_AA)
    r = src.rows(t0, t1)
    pts = []
    for f in src.flights(t0, t1):
        ts = np.arange(0, f["t1"] - f["t0"] + 1e-6, 1 / 120)
        px = ball_3d.project(P, ball_3d.simulate(f["p0"], f["v0"], ts))
        for i in range(1, len(px)):
            if np.all(np.abs(px[i]) < 5000) and np.all(np.abs(px[i - 1]) < 5000):
                cv2.line(img, tuple(np.int32(px[i - 1])), tuple(np.int32(px[i])), (255, 255, 255), 1, cv2.LINE_AA)
    for i, (t, x, y, s, pk) in enumerate(r):
        a = (t - t0) / max(1e-6, t1 - t0)
        cv2.circle(img, (int(x), int(y)), 3, (int(255 * (1 - a)), 255, int(60 * a)), -1, cv2.LINE_AA)
        if i % 6 == 0:
            cv2.putText(img, f"{t % 100:.2f}", (int(x) + 5, int(y) - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.3, (255, 255, 255), 1, cv2.LINE_AA)
        pts.append((x, y))
    for k in src.kinks(t0, t1):
        cv2.drawMarker(img, (int(k[1]), int(k[2])), (255, 0, 255), cv2.MARKER_TILTED_CROSS, 12, 1, cv2.LINE_AA)
    for e in events:
        if t0 <= e["t"] <= t1:
            c = (int(e["px"][0]), int(e["px"][1]))
            cv2.drawMarker(img, c, (255, 255, 0), cv2.MARKER_DIAMOND, 22, 2, cv2.LINE_AA)
            cv2.putText(img, f"{e['kind']} {e['t'] % 100:.2f} {e['conf']:.2f}", (c[0] + 12, c[1] - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 0), 1, cv2.LINE_AA)
    if crop is None and pts:
        p = np.array(pts)
        crop = (max(0, int(p[:, 0].min()) - 80), max(0, int(p[:, 1].min()) - 80), min(1920, int(p[:, 0].max()) + 80), min(img.shape[0], int(p[:, 1].max()) + 80))
    if crop is not None:
        img = img[crop[1]:crop[3], crop[0]:crop[2]]
        z = min(zoom, 1500 / max(1, img.shape[1]), 900 / max(1, img.shape[0]))
        img = cv2.resize(img, None, fx=z, fy=z, interpolation=cv2.INTER_CUBIC)
    if out:
        cv2.imwrite(out, img, [cv2.IMWRITE_JPEG_QUALITY, 85])
    return img


def strip(src, t, out=None, xy=None, dts=(-0.2, -0.1, 0.0, 0.1, 0.25, 0.6), half=(200, 130)):
    """Video frames around t, cropped at xy, side by side."""
    tiles = []
    for dt in dts:
        img = frame_at(src.video, t + dt)
        if xy is not None:
            x, y = int(np.clip(xy[0], half[0], 1920 - half[0])), int(np.clip(xy[1], half[1], img.shape[0] - half[1]))
            img = img[y - half[1]:y + half[1], x - half[0]:x + half[0]]
            img = cv2.resize(img, None, fx=1.25, fy=1.25, interpolation=cv2.INTER_CUBIC)
        cv2.putText(img, f"{dt:+.2f}", (6, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 255, 255), 2, cv2.LINE_AA)
        tiles.append(img)
    n = len(tiles) // 2
    img = np.vstack([np.hstack(tiles[:n]), np.hstack(tiles[n:2 * n])])
    if out:
        cv2.imwrite(out, img, [cv2.IMWRITE_JPEG_QUALITY, 85])
    return img


if __name__ == "__main__":
    a = sys.argv
    if a[1] == "pic":
        draw(Source(a[2]), float(a[3]), float(a[4]), a[5])
    elif a[1] == "strip":
        strip(Source(a[2]), float(a[3]), a[4], (float(a[5]), float(a[6])) if len(a) > 6 else None)


def tile(src, q, w=250, h=160, zoom=1.6, name=""):
    """One row for the check by eye of a candidate q (needs "t", "px"): the ball blobs of t - 1.2 s
    to t + 1.5 s (blue to white = before the touch, yellow to red = after it) on the frame at t + 0.5 s,
    and the video frames at t - 0.1, t + 0.1 and t + 0.5 s."""
    t, (x, y) = q["t"], q["px"]
    frames = {dt: frame_at(src.video, t + dt) for dt in (-0.1, 0.1, 0.5)}
    hh = frames[0.5].shape[0]
    x0, y0 = int(np.clip(x - w, 0, 1920 - 2 * w)), int(np.clip(y - h, 0, hh - 2 * h))
    P = ball_3d.camera(src.H(t))[0]
    top, base = net_px(P)
    tr = frames[0.5].copy()
    cv2.polylines(tr, [np.int32(top)], False, (0, 255, 255), 1, cv2.LINE_AA)
    cv2.polylines(tr, [np.int32(base)], False, (0, 200, 255), 1, cv2.LINE_AA)
    for (bt, bx, by, _, _) in src.rows(t - 1.2, t + 1.5):
        a = (bt - t) / 1.2 if bt <= t else (bt - t) / 1.5
        col = (255, int(255 * (1 + a)), int(255 * (1 + a))) if bt <= t else (0, int(255 * (1 - a)), 255)
        cv2.circle(tr, (int(bx), int(by)), 2, col, -1, cv2.LINE_AA)
    cv2.drawMarker(tr, (int(x), int(y)), (255, 0, 255), cv2.MARKER_TILTED_CROSS, 10, 1, cv2.LINE_AA)
    out = []
    for i, im in enumerate([tr] + [frames[dt] for dt in (-0.1, 0.1, 0.5)]):
        c = cv2.resize(im[y0:y0 + 2 * h, x0:x0 + 2 * w], None, fx=zoom / 2, fy=zoom / 2, interpolation=cv2.INTER_AREA if zoom < 2 else cv2.INTER_LINEAR)
        if i:
            cv2.putText(c, ("-0.1", "+0.1", "+0.5")[i - 1], (4, 14), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)
        out.append(c)
    row = np.hstack(out)
    cv2.putText(row, name, (4, row.shape[0] - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 0), 1, cv2.LINE_AA)
    return row


def sheet(src, cands, out, names=None):
    rows = [tile(src, q, name=(names[i] if names else f"{src.sid} {q['t']:.2f}")) for i, q in enumerate(cands)]
    cv2.imwrite(out, np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 82])

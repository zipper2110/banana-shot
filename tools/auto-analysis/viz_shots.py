"""Debug pictures of the ball data for a check by eye (E17d).

Work: the data of one video. A lab match folder (D:/bananashot-lab/data/<match id>) or a work folder of
detect.py. All times are true video times (packet times). For a video that detect.py decoded at a constant
30 fps, true_times() gives the video frame that the ffmpeg "fps" filter put into each slot.

Commands:
  python viz_shots.py <work folder> <video> <t0> <t1> <out.jpg> [flights file]
      One video frame (the frame at t1) with: the best ball detection of each frame (dots, blue = old to
      green = new), the kinks (magenta crosses), each fitted flight (white = the time range of the
      detections, gray = after the last detection), the fit landing (red ring), a bounce anchor (orange
      ring), and the events of the flights file (bounce = yellow square, hit = white triangle, net = cyan
      diamond, end = gray circle).
  python viz_shots.py strip <work folder> <video> <t> <x> <y> <out.jpg> [frames] [flights file]
      A strip around the time t and the pixel (x, y) of the 1920-wide frame. Top: a strobe picture (each
      pixel comes from the frame where it differs most from the median of the frames) with a number at each
      ball detection. Below: one crop for each video frame, with the frame number, the time, and marks at
      the detection. Use it to find the frame of a bounce by eye.
"""
import json, os, subprocess, sys
import numpy as np
import cv2
import ball_3d

LAB = "D:/bananashot-lab/data"


from ball_full import slot_frames as true_times      # the ffmpeg fps filter: the video frame in each output slot


class Work:
    def __init__(self, folder, video=None, flights_file=None):
        self.dir = folder
        mid = os.path.basename(os.path.normpath(folder))
        if video is None:
            import lab
            video = lab.MATCHES[mid]["video"]
        self.video, self.mid = video, mid
        fs = [x for x in os.listdir(folder) if x.startswith("wasb_") and x.endswith("_s0.35_k2.npy")]
        f = max(fs, key=lambda x: int(x.split("_")[2]))                 # the full-match file is the longest
        self.start = int(f.split("_")[1])
        d = np.load(os.path.join(folder, f))
        self.blobs = d[np.argsort(d[:, 0], kind="stable")]
        pv = os.path.join(folder, "pts_video.npy")
        if os.path.exists(pv):                                          # detect.py, constant 30 fps decode
            self.tv = np.load(pv)
            slot = true_times(self.tv, int(self.blobs[:, 0].max()) // 2 + 1)
            self.frame_t = lambda fr: self.tv[slot[np.asarray(fr, int) // 2]]
            self.lab_t = lambda fr: np.asarray(fr) / 60.0
        else:
            self.tv = np.load(os.path.join(folder, "pts.npy"))
            tt = self.tv[np.searchsorted(self.tv, self.start - 1e-4):]
            self.frame_t = lambda fr: tt[np.minimum(np.asarray(fr, int), len(tt) - 1)]
            self.lab_t = self.frame_t
        self.bt = self.frame_t(self.blobs[:, 0])                        # true time of each blob
        self.track = [w for w in json.load(open(os.path.join(folder, "court_track.json"))) if w.get("ok", True)]
        self.flights_file = flights_file or os.path.join(folder, "fullflights_s0.35.json")
        self._F = None

    @property
    def F(self):
        if self._F is None:
            self._F = json.load(open(self.flights_file))
        return self._F

    def H(self, t):
        w = min(self.track, key=lambda w: 0 if w["t0"] <= t < w["t1"] else min(abs(t - w["t0"]), abs(t - w["t1"])))
        return np.array(w["H"], np.float64)

    def best(self):
        """The best blob of each frame: true time, x, y, lab time."""
        d = self.blobs
        top = np.ones(len(d), bool); top[1:] = d[1:, 0] != d[:-1, 0]
        return self.bt[top], d[top, 1], d[top, 2], self.lab_t(d[top, 0])

    def frames(self, t, n, box=None, zoom=1.0):
        """n successive video frames from the first frame at or after t. box = (x, y, w, h) in px of the
        1920-wide frame. Returns the frame times and the pictures (px of the 1920-wide frame * zoom)."""
        i0 = int(np.searchsorted(self.tv, t - 1e-4))
        ts = self.tv[i0:i0 + n]
        vf = "scale=1920:-2"
        if box:
            x, y, w, h = box
            vf = f"crop=iw*{w}/1920:iw*{h}/1920:iw*{x}/1920:iw*{y}/1920,scale={int(w * zoom)}:{int(h * zoom)}:flags=bicubic"
        cmd = ["ffmpeg", "-v", "error", "-ss", f"{ts[0] - 0.004:.4f}", "-i", self.video, "-frames:v", str(len(ts)), "-vf", vf,
               "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "bgr24", "-"]
        data = subprocess.run(cmd, stdout=subprocess.PIPE).stdout
        w, h = (int(box[2] * zoom), int(box[3] * zoom)) if box else (1920, 1080)
        k = len(data) // (w * h * 3)
        return ts[:k], [np.frombuffer(data[i * w * h * 3:(i + 1) * w * h * 3], np.uint8).reshape(h, w, 3).copy() for i in range(k)]


def path_near(wk, t, half=2.0):
    """The selected ball path (ball_events.select) near the time t: rows true time, x, y."""
    from ball_tracks import link, moving
    from ball_events import select
    m = np.abs(wk.bt - t) < half
    if m.sum() < 8:
        return np.zeros((0, 3))
    p = select(moving(link(wk.blobs[m])))
    return np.stack([wk.frame_t(p[:, 0]), p[:, 1], p[:, 2]], 1) if len(p) else np.zeros((0, 3))


def strip(wk, t, x, y, out, n=12, before=None, tile=110, tz=3.0):
    """See the module text. Returns the frame times."""
    i_mid = int(np.searchsorted(wk.tv, t))
    i0 = max(0, i_mid - (n // 2 if before is None else before))
    ts = wk.tv[i0:i0 + n]
    path = path_near(wk, t)
    path = path[(path[:, 0] > ts[0] - 0.004) & (path[:, 0] < ts[-1] + 0.004)]
    pts = np.concatenate([path[:, 1:3], [[x, y]]])
    x0, y0, x1, y1 = pts[:, 0].min() - 70, pts[:, 1].min() - 70, pts[:, 0].max() + 70, pts[:, 1].max() + 70
    bw = int(np.clip(max(x1 - x0, (y1 - y0) * 4 / 3, 200), 200, 640)) // 4 * 4
    bh = bw * 3 // 4
    bx = int(np.clip((x0 + x1) / 2 - bw / 2, 0, 1920 - bw)); by = int(np.clip((y0 + y1) / 2 - bh / 2, 0, 1080 - bh))
    src_zoom = 2.0                                                      # the pictures come at the 4K resolution
    ts, big = wk.frames(ts[0], n, (bx, by, bw, bh), src_zoom)
    st = np.stack(big).astype(np.int16)
    med = np.median(st, 0)
    dif = np.abs(st - med).sum(3)
    arg = dif.argmax(0)
    strobe = np.take_along_axis(st, arg[None, :, :, None], 0)[0]
    strobe = np.where((dif.max(0) > 40)[:, :, None], strobe, med).astype(np.uint8)
    z = 800 / strobe.shape[1]
    strobe = cv2.resize(strobe, (800, 600), interpolation=cv2.INTER_CUBIC)
    Z = src_zoom * z
    tiles, ks = [], []
    for k, (ft, im) in enumerate(zip(ts, big)):
        m = np.abs(path[:, 0] - ft) < 0.004
        if m.any():
            px, py = path[m][0, 1:3]
            c = (int((px - bx) * Z), int((py - by) * Z))
            cv2.circle(strobe, c, 9, (0, 0, 255), 1, cv2.LINE_AA)
            side = 1 if k % 2 else -1
            cv2.line(strobe, (c[0] + 10 * side, c[1]), (c[0] + 26 * side, c[1]), (0, 255, 255), 1, cv2.LINE_AA)
            cv2.putText(strobe, str(k), (c[0] + (28 if side > 0 else -44), c[1] + 5), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1, cv2.LINE_AA)
            ks.append((k, px, py))
        cx, cy = (path[m][0, 1:3] if m.any() else (x, y))               # the crop follows the ball path
        tw = int(tile * src_zoom)
        tx = int(np.clip((cx - bx) * src_zoom - tw / 2, 0, im.shape[1] - tw))
        ty = int(np.clip((cy - by) * src_zoom - tw * 3 / 8, 0, im.shape[0] - tw * 3 // 4))
        tl = cv2.resize(im[ty:ty + tw * 3 // 4, tx:tx + tw], (int(tile * tz), int(tile * tz * 3 / 4)), interpolation=cv2.INTER_CUBIC)
        if m.any():
            c = (int(((cx - bx) * src_zoom - tx) * tz / src_zoom), int(((cy - by) * src_zoom - ty) * tz / src_zoom))
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):           # 4 ticks around the detection: the ball stays visible
                cv2.line(tl, (c[0] + 14 * dx, c[1] + 14 * dy), (c[0] + 24 * dx, c[1] + 24 * dy), (0, 0, 255), 1, cv2.LINE_AA)
        cv2.putText(tl, f"{k} {ft % 100:.3f}", (3, 13), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)
        tiles.append(tl)
    for e in wk.F.get("events", []):
        if ts[0] - 0.02 <= e["t"] <= ts[-1] + 0.02 and e.get("px"):
            c = (int((e["px"][0] - bx) * Z), int((e["px"][1] - by) * Z))
            cv2.drawMarker(strobe, c, (0, 255, 255) if e["kind"] == "bounce" else (255, 255, 255), cv2.MARKER_SQUARE, 22, 1, cv2.LINE_AA)
            cv2.putText(strobe, f"{e['kind']} {np.interp(e['t'], ts, np.arange(len(ts))):.1f}", (c[0] - 30, c[1] + 28), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)
    for k in wk.F["kinks"]:
        if ts[0] <= k[0] <= ts[-1] + 0.02:
            c = (int((k[1] - bx) * Z), int((k[2] - by) * Z))
            cv2.drawMarker(strobe, c, (255, 0, 255), cv2.MARKER_TILTED_CROSS, 10, 1, cv2.LINE_AA)
    for gx in range(bx // 50 * 50 + 50, bx + bw, 50):                   # px scale of the 1920-wide frame at the picture edge
        cv2.putText(strobe, str(gx), (int((gx - bx) * Z) - 12, 12), cv2.FONT_HERSHEY_SIMPLEX, 0.35, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.line(strobe, (int((gx - bx) * Z), 14), (int((gx - bx) * Z), 20), (255, 255, 255), 1)
    for gy in range(by // 50 * 50 + 50, by + bh, 50):
        cv2.putText(strobe, str(gy), (8, int((gy - by) * Z) + 4), cv2.FONT_HERSHEY_SIMPLEX, 0.35, (255, 255, 255), 1, cv2.LINE_AA)
        cv2.line(strobe, (0, int((gy - by) * Z)), (6, int((gy - by) * Z)), (255, 255, 255), 1)
    # chart: image y (yellow) and image x (cyan) of the path against the frame number
    ch = np.full((600, 520, 3), 30, np.uint8)
    if ks:
        a = np.array(ks)
        X = lambda k: int(30 + k * 480 / max(1, n - 1))
        for k in range(n):
            cv2.line(ch, (X(k), 585), (X(k), 22), (70, 70, 70), 1)
            cv2.putText(ch, str(k), (X(k) - 5, 598), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1, cv2.LINE_AA)
        for col, color, name in ((2, (0, 255, 255), "y"), (1, (255, 255, 0), "x")):
            v = a[:, col]
            lo, hi = v.min() - 2, v.max() + 2
            Y = lambda q: int(30 + (q - lo) * 540 / max(1e-6, hi - lo))
            for (k1, q1), (k2, q2) in zip(a[:-1, [0, col]], a[1:, [0, col]]):
                cv2.line(ch, (X(k1), Y(q1)), (X(k2), Y(q2)), color, 1, cv2.LINE_AA)
            for k1, q1 in a[:, [0, col]]:
                cv2.circle(ch, (X(k1), Y(q1)), 3, color, -1, cv2.LINE_AA)
            cv2.putText(ch, f"{name} {lo:.0f}..{hi:.0f} px", (40 + (col - 1) * 200, 14), cv2.FONT_HERSHEY_SIMPLEX, 0.45, color, 1, cv2.LINE_AA)
    top = np.concatenate([strobe, ch], 1)
    cols = 4
    while len(tiles) % cols:
        tiles.append(np.zeros_like(tiles[0]))
    grid = np.concatenate([np.concatenate(tiles[i:i + cols], 1) for i in range(0, len(tiles), cols)], 0)
    grid = cv2.resize(grid, (top.shape[1], int(grid.shape[0] * top.shape[1] / grid.shape[1])), interpolation=cv2.INTER_CUBIC)
    cv2.imwrite(out, np.concatenate([top, grid], 0), [cv2.IMWRITE_JPEG_QUALITY, 85])
    return ts


def draw(work, video, t0, t1, out, flights_file=None, zoom=2.0):
    wk = Work(work, video, flights_file)
    img = wk.frames(t1, 1)[1][0]
    F = wk.F
    P = ball_3d.camera(wk.H(t0))[0]
    bt, bx, by, _ = wk.best()
    m = (bt >= t0) & (bt <= t1)
    pts = []
    for f in F["flights"]:
        if f["t1"] < t0 or f["t0"] > t1:
            continue
        lt = f["landT"] if f["landT"] == f["landT"] else f["t1"] + 0.3
        ts = np.arange(0, max(lt, f["t1"]) - f["t0"] + 1e-6, 1 / 120)
        X = ball_3d.simulate(f["p0"], f["v0"], ts)
        X = X[X[:, 2] > -0.05]
        px = ball_3d.project(P, X)
        n_obs = int((f["t1"] - f["t0"]) * 120) + 1
        good = f["n"] >= 10 and f["rms"] < 5
        for i in range(1, len(px)):
            c = ((255, 255, 255) if good else (140, 140, 255)) if i < n_obs else (150, 150, 150)
            cv2.line(img, tuple(np.int32(px[i - 1])), tuple(np.int32(px[i])), c, 1, cv2.LINE_AA)
        if f["land"][0] == f["land"][0]:
            g = ball_3d.project(P, np.array([[f["land"][0], f["land"][1], 0.0]]))[0]
            cv2.circle(img, tuple(np.int32(g)), 9, (0, 0, 255), 2, cv2.LINE_AA)
            pts.append(g)
        if f.get("bounce"):
            g = ball_3d.project(P, np.array([[f["bounce"][0], f["bounce"][1], 0.0]]))[0]
            cv2.circle(img, tuple(np.int32(g)), 12, (0, 140, 255), 2, cv2.LINE_AA)
        cv2.putText(img, f"{f['t0'] % 100:.2f} n{f['n']} r{f['rms']:.1f}", tuple(np.int32(px[0]) + (6, -6)), cv2.FONT_HERSHEY_SIMPLEX, 0.4,
                    (255, 255, 255), 1, cv2.LINE_AA)
    for t, x, y in zip(bt[m], bx[m], by[m]):
        a = (t - t0) / max(1e-6, t1 - t0)
        cv2.circle(img, (int(x), int(y)), 3, (int(255 * (1 - a)), 255, int(60 * a)), -1, cv2.LINE_AA)
        pts.append((x, y))
    for k in F["kinks"]:
        if t0 <= k[0] <= t1:
            c = (int(k[1]), int(k[2]))
            cv2.drawMarker(img, c, (255, 0, 255), cv2.MARKER_TILTED_CROSS, 14, 2, cv2.LINE_AA)
            cv2.putText(img, f"{k[0] % 100:.2f}", (c[0] + 8, c[1] + 14), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 0, 255), 1, cv2.LINE_AA)
    for e in F.get("events", []):
        if t0 <= e["t"] <= t1 and e.get("px"):
            c = (int(e["px"][0]), int(e["px"][1]))
            if e["kind"] == "bounce":
                cv2.rectangle(img, (c[0] - 8, c[1] - 8), (c[0] + 8, c[1] + 8), (0, 255, 255), 2, cv2.LINE_AA)
            elif e["kind"] == "net":
                cv2.drawMarker(img, c, (255, 255, 0), cv2.MARKER_DIAMOND, 22, 2, cv2.LINE_AA)
            elif e["kind"] == "hit":
                cv2.drawMarker(img, c, (255, 255, 255), cv2.MARKER_TRIANGLE_UP, 16, 2, cv2.LINE_AA)
            else:
                cv2.circle(img, c, 7, (170, 170, 170), 1, cv2.LINE_AA)
            cv2.putText(img, f"{e['kind']} {e['t'] % 100:.2f}", (c[0] + 10, c[1] - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (0, 255, 255), 1, cv2.LINE_AA)
            pts.append(c)
    if pts:
        p = np.array(pts)
        x0, y0 = max(0, int(p[:, 0].min()) - 80), max(0, int(p[:, 1].min()) - 80)
        x1, y1 = min(1920, int(p[:, 0].max()) + 80), min(1080, int(p[:, 1].max()) + 80)
        img = img[y0:y1, x0:x1]
        z = min(zoom, 1600 / max(1, img.shape[1]), 1000 / max(1, img.shape[0]))
        img = cv2.resize(img, None, fx=z, fy=z, interpolation=cv2.INTER_CUBIC)
    cv2.imwrite(out, img, [cv2.IMWRITE_JPEG_QUALITY, 88])


if __name__ == "__main__":
    a = sys.argv
    if a[1] == "strip":
        wk = Work(a[2], a[3], a[9] if len(a) > 9 else None)
        strip(wk, float(a[4]), float(a[5]), float(a[6]), a[7], int(a[8]) if len(a) > 8 else 12)
    else:
        draw(a[1], a[2], float(a[3]), float(a[4]), a[5], a[6] if len(a) > 6 else None)

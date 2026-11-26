"""Net band motion, level 1 (E88 to E133): the masks of the moved pixels in the net region of the ball-stage pictures.

The pictures are the pictures of the ball stage (ball_wasb.frames: the court crop at scale 0.35, one pass). For each
picture: gray, a 3 x 3 blur, and the pixels that changed by more than THR gray steps against the picture 1/30 s
before, in a fixed region around the net (x within REGION_X m, z from REGION_Z[0] m to the cord + REGION_Z[1] m).
Each mask is packed and compressed. Level 2 (net_band.py: the bins, the ball mask, the signal) reads this file, so a
change of level 2 needs no new decode. The cache key of this file is the ball tag and the content of this module only
(a hand list, as for the ball stage): this module imports no module of the folder at the top, and camera() and
project() are copies of ball_3d.camera() and ball_3d.project() (test_net_band.py checks that they stay equal), because
ball_3d imports the fit modules and lab, which change often. fill_in imports ball_wasb; the ball tag covers it.

  w = MaskWriter(Hm, crop, scale, step)          before the ball stage (crop = ball_wasb.crop_box(...)[:4])
  ball_wasb.frames = w.wrap(ball_wasb.frames)    each picture also goes to w.feed
  w.save(path)                                    after the ball stage
  fill_in(...)                                    an old folder: decode only the windows around the net candidates
                                                  (candidates of the ball file only, flights=(): no cycle with the flights key)

File (npz): frame and prev (the frame index of the picture and of the picture 1/30 s before, on the time line of the
blobs), off and size (bytes of each mask in data), data, box (rows a..b, columns c..d of the crop), crop, scale, thr.
"""
import zlib, tempfile
import numpy as np
import cv2

THR = 3.0                         # gray steps (0..255) at scale 0.35 (E131)
REGION_X = 7.0                    # m from the center of the net
REGION_Z = (-0.3, 1.0)            # m: from below the net base to above the cord
NET_POST_X, NET_POST_Z, NET_MID_Z = 6.40, 1.07, 0.914
VERSION = 1                       # the file format number only; the cache key is the code of the import closure


def cord_z(x):
    return NET_MID_Z + (NET_POST_Z - NET_MID_Z) * np.minimum(np.abs(x), NET_POST_X) / NET_POST_X


def camera(Hm, w=1920, h=1080):
    """A copy of ball_3d.camera: P (3 x 4, court m -> px of the 1920 frame), f (px), the camera center (m)."""
    Gm = np.linalg.inv(Hm)
    T = np.array([[1, 0, w / 2], [0, 1, h / 2], [0, 0, 1.0]])
    A = np.linalg.inv(T) @ Gm
    h1, h2, h3 = A[:, 0], A[:, 1], A[:, 2]
    a = np.array([h1[0] * h2[0] + h1[1] * h2[1], h1[0] ** 2 + h1[1] ** 2 - h2[0] ** 2 - h2[1] ** 2])
    b = np.array([-h1[2] * h2[2], h2[2] ** 2 - h1[2] ** 2])
    u = float(a @ b / (a @ a))
    f = 1.0 / np.sqrt(u) if u > 0 else float("nan")
    Ki = np.diag([1 / f, 1 / f, 1.0])
    lam = 2.0 / (np.linalg.norm(Ki @ h1) + np.linalg.norm(Ki @ h2))
    r1, r2, t = lam * Ki @ h1, lam * Ki @ h2, lam * Ki @ h3
    if t[2] < 0:
        r1, r2, t = -r1, -r2, -t
    U, _, Vt = np.linalg.svd(np.stack([r1, r2, np.cross(r1, r2)], 1))
    R = U @ Vt
    C = -R.T @ t
    if C[2] < 0:
        R[:, 2] = -R[:, 2]
        C = -R.T @ t
    P = T @ np.diag([f, f, 1.0]) @ np.concatenate([R, t[:, None]], 1)
    return P, f, C


def project(P, X):
    """A copy of ball_3d.project: court m (n x 3) -> px of the 1920 frame (n x 2)."""
    x = (P @ np.concatenate([X, np.ones((len(X), 1))], 1).T).T
    return x[:, :2] / x[:, 2:3]


def region_box(Hm, crop, scale):
    """The box (a, b, c, d: rows a..b, columns c..d of the crop) of the net region, or None."""
    P = camera(np.asarray(Hm, float))[0]
    xs = np.linspace(-REGION_X, REGION_X, 15)
    pts = np.concatenate([project(P, np.column_stack([xs, 0 * xs, np.full(len(xs), REGION_Z[0])])),
                          project(P, np.column_stack([xs, 0 * xs, cord_z(xs) + REGION_Z[1]]))])
    x0, y0, cw, ch = crop
    q = pts * scale - [x0, y0]
    a, b = int(max(0, np.floor(q[:, 1].min()))), int(min(ch, np.ceil(q[:, 1].max()) + 1))
    c, d = int(max(0, np.floor(q[:, 0].min()))), int(min(cw, np.ceil(q[:, 0].max()) + 1))
    return (a, b, c, d) if b > a and d > c else None


class MaskWriter:
    """Keeps the compressed mask of each picture. step: pictures for 1/30 s (2 at 60 fps, 1 at 30 fps).
    index(i): the frame index (time line of the blobs) of picture i of the decode."""

    def __init__(self, Hm, crop, scale, step, index=lambda i: i, tmp_dir=None):
        self.crop, self.scale, self.k = tuple(int(v) for v in crop), scale, step
        self.box = region_box(Hm, crop, scale)
        self.index = index
        self.ring, self.i = [], 0
        self.f = tempfile.TemporaryFile(dir=tmp_dir)
        self.rec = []

    def feed(self, rgb):
        if self.box is None:
            return
        a, b, c, d = self.box
        g = cv2.cvtColor(np.ascontiguousarray(rgb[a:b, c:d]), cv2.COLOR_RGB2GRAY).astype(np.float32)
        g = cv2.GaussianBlur(g, (3, 3), 0)
        self.ring.append(g)
        if len(self.ring) > self.k + 1:
            self.ring.pop(0)
        if len(self.ring) == self.k + 1:
            z = zlib.compress(np.packbits(np.abs(g - self.ring[0]) > THR).tobytes(), 1)
            self.rec.append((self.index(self.i), self.index(self.i - self.k), self.f.tell(), len(z)))
            self.f.write(z)
        self.i += 1

    def gap(self, index):
        """A new window of pictures (fill-in): the next picture is not the next frame. index: its frame index map."""
        self.ring, self.i, self.index = [], 0, index

    def wrap(self, frames):
        def gen(*a, **kw):
            for fr in frames(*a, **kw):
                self.feed(fr)
                yield fr
        return gen

    def save(self, path):
        self.f.seek(0)
        data = np.frombuffer(self.f.read(), np.uint8)
        self.f.close()
        r = np.array(self.rec, np.int64).reshape(-1, 4)
        np.savez(path, frame=r[:, 0].astype(np.int32), prev=r[:, 1].astype(np.int32), off=r[:, 2], size=r[:, 3],
                 data=data, box=np.array(self.box or (0, 0, 0, 0)), crop=np.array(self.crop), scale=self.scale,
                 thr=THR, version=VERSION)
        return path


class Masks:
    """A level-1 file: masks(j) gives the bool mask (box size) of record j."""

    def __init__(self, path):
        z = np.load(path)
        self.frame, self.prev, self.off, self.size, self.data = z["frame"], z["prev"], z["off"], z["size"], z["data"]
        self.box, self.crop, self.scale = tuple(int(v) for v in z["box"]), tuple(int(v) for v in z["crop"]), float(z["scale"])
        a, b, c, d = self.box
        self.shape = (b - a, d - c)

    def __len__(self):
        return len(self.frame)

    def mask(self, j):
        o, s = int(self.off[j]), int(self.size[j])
        n = self.shape[0] * self.shape[1]
        return np.unpackbits(np.frombuffer(zlib.decompress(self.data[o:o + s].tobytes()), np.uint8))[:n].reshape(self.shape).astype(bool)


def windows(times, lo=-1.0, hi=1.1):
    """The union of [t + lo, t + hi] for the candidate times."""
    out = []
    for t in sorted(times):
        a, b = t + lo, t + hi
        if out and a <= out[-1][1]:
            out[-1][1] = max(out[-1][1], b)
        else:
            out.append([a, b])
    return out


def fill_in(mid, frame_t, cand_times, Hm, path, scale=0.35, fps30=False, progress=None):
    """An old folder (a ball file, no level-1 file): decode only the windows around the net candidates with the
    pictures of the ball stage (ball_wasb.frames) and write the level-1 file. frame_t: the time line of the blobs
    (pts.npy of the folder). fps30: the ball stage of this video ran at 30 fps (the blobs are on each second frame)."""
    import ball_wasb
    crop = ball_wasb.crop_box(mid, scale, bool(ball_wasb.PASSES))[:4]
    frame_t = np.asarray(frame_t)
    w = MaskWriter(Hm, crop, scale, 1 if fps30 else 2)
    old_fps = ball_wasb.FPS
    ball_wasb.FPS = 30 if fps30 else None
    try:
        ws = windows(cand_times)
        for j, (a, b) in enumerate(ws):
            a = max(0.0, a)
            if fps30:
                w.gap(lambda i, a=a: int(round((a + i / 30.0) * 60.0)) // 2 * 2)
            else:
                i0 = int(np.searchsorted(frame_t, a - 1e-4))
                w.gap(lambda i, i0=i0: i0 + i)
            for fr in ball_wasb.frames(mid, a, b - a, scale):
                w.feed(fr)
            if progress:
                progress(j + 1, len(ws))
    finally:
        ball_wasb.FPS = old_fps
    return w.save(path)

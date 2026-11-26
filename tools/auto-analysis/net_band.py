"""Net band motion (E88 to E132): a signal from the picture of the net band for the net events (net_events.py).

The net moves after a touch, also when the ball track does not show the touch. For each frame and each bin of
0.5 m along the net: the part of the pixels that changed by more than THR gray steps in 1/30 s, with no pixel near
a ball blob (the ball itself moves). Two bands: the net (base to cord + 0.12 m) and a control band above the cord
(0.25 to 0.85 m; a player moves there, the net does not).

The pictures are the pictures of the ball stage (ball_wasb.frames: the court crop at scale 0.35), so a new run makes
the signal with no extra decode:
  rec = Recorder(work, mid, scale, stride)      before the ball stage
  ball_wasb.frames = rec.wrap(ball_wasb.frames)  each picture also goes to rec.feed
  rec.finish(blobs, path)                       after the ball stage: the ball mask from the blobs, the file
An old work folder (a ball file, no net band file): fill_in() decodes only the windows around the net candidates.

The file net_band.npz: frame (index on the time line of the blobs), band and above (n x bins, float16), edges (m).
signal() gives S (the rise of the band motion after a time, near the touch x) and SA (the same for the control band).
The values of the rules (judge_band) come from the held-out test on the lab labels (E130, E131).
"""
import os, zlib, tempfile
import numpy as np
import cv2

BIN_EDGES = np.arange(-6.5, 6.51, 0.5)          # m along the net (x), post to post and 0.1 m more
Z_LO, Z_HI = -0.05, 0.12                        # the band: from the net base to the cord, with a margin (m)
ABOVE = (0.25, 0.85)                            # m above the cord: the control band
NET_POST_X, NET_POST_Z, NET_MID_Z = 6.40, 1.07, 0.914
THR = 3.0                                       # gray steps (0..255) at scale 0.35 (E131)
BALL_R = 16.0                                   # px of the 1920 frame: no pixel near a ball blob counts
BASE_W = (-0.7, -0.2)                           # s after the candidate time: the baseline window
AFTER_W = (0.0, 0.8)                            # s: the net moves in this window after a touch
SLIDE = 0.3                                     # s: the mean over this time, its maximum in AFTER_W
NEAR_M = 1.25                                   # m: the bins near the touch x
R_MIN, C_MIN, A_MAX = 0.15, 0.25, 0.02          # reject below R_MIN; confirm at C_MIN or more with SA at A_MAX or less (E131)
WIN = (-1.0, 1.1)                               # s around a candidate: the window of the fill-in decode
NET_CONF = 0.4                                  # the confidence from which a net event counts (shot_chain.NET_CONF)
VERSION = 1


def cord_z(x):
    return NET_MID_Z + (NET_POST_Z - NET_MID_Z) * np.minimum(np.abs(x), NET_POST_X) / NET_POST_X


def polys(P, lo=None, hi=None):
    """Image polygons (px of the 1920 frame) of the bins. lo, hi: m above the cord; None: the net band."""
    import ball_3d
    out = []
    for x0, x1 in zip(BIN_EDGES[:-1], BIN_EDGES[1:]):
        if lo is None:
            q = [[x0, 0, Z_LO], [x1, 0, Z_LO], [x1, 0, cord_z(x1) + Z_HI], [x0, 0, cord_z(x0) + Z_HI]]
        else:
            q = [[x0, 0, cord_z(x0) + lo], [x1, 0, cord_z(x1) + lo], [x1, 0, cord_z(x1) + hi], [x0, 0, cord_z(x0) + hi]]
        out.append(ball_3d.project(P, np.array(q, float)))
    return out


class Geometry:
    """The bins in the pictures of the ball stage: a label image (bin id 0..2*nb-1, -1 = none) in a box of the crop."""

    def __init__(self, Hm, crop, scale):
        import ball_3d
        P = ball_3d.camera(np.asarray(Hm, float))[0]
        x0, y0, cw, ch = crop
        self.scale, self.x0, self.y0 = scale, x0, y0
        self.nb = len(BIN_EDGES) - 1
        lab = np.full((ch, cw), -1, np.int16)
        for b, p in enumerate(polys(P) + polys(P, *ABOVE)):
            m = np.zeros((ch, cw), np.uint8)
            cv2.fillPoly(m, [np.int32(np.round(p * scale - [x0, y0]))], 1)
            lab[(m > 0) & (lab < 0)] = b
        ys, xs = np.nonzero(lab >= 0)
        if len(ys) == 0:
            self.box = None
            return
        self.box = (max(0, ys.min() - 2), min(ch, ys.max() + 3), max(0, xs.min() - 2), min(cw, xs.max() + 3))
        a, b, c, d = self.box
        self.lab = lab[a:b, c:d]
        self.inside = self.lab >= 0
        self.area = np.bincount(self.lab[self.inside], minlength=2 * self.nb).astype(np.float64)

    def gray(self, rgb):
        a, b, c, d = self.box
        g = cv2.cvtColor(np.ascontiguousarray(rgb[a:b, c:d]), cv2.COLOR_RGB2GRAY).astype(np.float32)
        return cv2.GaussianBlur(g, (3, 3), 0)


class Recorder:
    """Keeps a compressed mask of the moved pixels of each picture (in a temp file); finish() applies the ball mask.
    index(i) maps the picture number of the decode to the frame index of the blobs (60 fps time line)."""

    def __init__(self, Hm, crop, scale, step, index=lambda i: i, tmp_dir=None):
        self.g = Geometry(Hm, crop, scale)
        self.k = step                                 # pictures for 1/30 s: 2 at 60 fps, 1 at 30 fps
        self.index = index
        self.ring, self.i = [], 0
        self.f = tempfile.TemporaryFile(dir=tmp_dir)
        self.rec = []                                  # (frame index, frame index of the picture k before, offset, size)

    def feed(self, rgb):
        if self.g.box is None:
            return
        cur = self.g.gray(rgb)
        self.ring.append(cur)
        if len(self.ring) > self.k + 1:
            self.ring.pop(0)
        if len(self.ring) == self.k + 1:
            d = (np.abs(cur - self.ring[0]) > THR) & self.g.inside
            z = zlib.compress(np.packbits(d).tobytes(), 1)
            self.rec.append((self.index(self.i), self.index(self.i - self.k), self.f.tell(), len(z)))
            self.f.write(z)
        self.i += 1

    def gap(self):
        """A break in the pictures (the next one is not the next frame): the fill-in step starts a new window."""
        self.ring = []

    def wrap(self, frames):
        def gen(*a, **kw):
            for fr in frames(*a, **kw):
                self.feed(fr)
                yield fr
        return gen

    def finish(self, blobs, path):
        """blobs: rows (frame, x, y, score, peak) of the ball stage (px of the 1920 frame). Writes path (npz)."""
        g, nb = self.g, len(BIN_EDGES) - 1
        n = len(self.rec)
        band = np.zeros((n, nb), np.float16)
        above = np.zeros((n, nb), np.float16)
        frame = np.zeros(n, np.int32)
        if n and g.box is not None:
            a, b, c, d = g.box
            shape = g.lab.shape
            by = {}
            if len(blobs):
                bl = np.asarray(blobs)
                for f in np.unique(bl[:, 0]).astype(int):
                    by[f] = bl[bl[:, 0] == f]
            r = max(1, int(round(BALL_R * g.scale)))
            self.f.seek(0)
            for j, (fi, fk, off, size) in enumerate(self.rec):
                self.f.seek(off)
                m = np.unpackbits(np.frombuffer(zlib.decompress(self.f.read(size)), np.uint8))[:shape[0] * shape[1]].reshape(shape).astype(bool)
                area = g.area
                near = [p for f in (fi, fk) for p in by.get(f, ())]
                if near:
                    keep = np.ones(shape, np.uint8)
                    for p in near:
                        cv2.circle(keep, (int(round(p[1] * g.scale - g.x0 - c)), int(round(p[2] * g.scale - g.y0 - a))), r, 0, -1)
                    keep = keep.astype(bool)
                    m &= keep
                    area = np.bincount(g.lab[g.inside & keep], minlength=2 * nb).astype(np.float64)
                cnt = np.bincount(g.lab[m], minlength=2 * nb)
                with np.errstate(all="ignore"):
                    v = np.where(area > 20 * g.scale * g.scale, cnt / np.maximum(area, 1), np.nan)
                band[j], above[j], frame[j] = v[:nb], v[nb:], fi
        self.f.close()
        np.savez(path, frame=frame, band=band, above=above, edges=BIN_EDGES, version=VERSION)
        return path


class Band:
    """The net band file of a folder, with the frame times (pts.npy of the folder, the time line of the blobs)."""

    def __init__(self, path, frame_t):
        z = np.load(path)
        f = z["frame"].astype(int)
        ok = f < len(frame_t)
        self.t = np.asarray(frame_t)[f[ok]]
        o = np.argsort(self.t, kind="stable")
        self.t = self.t[o]
        self.band = z["band"][ok][o].astype(np.float32)
        self.above = z["above"][ok][o].astype(np.float32)
        e = z["edges"]
        self.xc = 0.5 * (e[:-1] + e[1:])

    def _rise(self, M, near, rt):
        with np.errstate(all="ignore"):
            e = np.nanmean(M[:, near], axis=1)
        b = e[(rt >= BASE_W[0]) & (rt < BASE_W[1])]
        b = b[np.isfinite(b)]
        if len(b) < 3:
            return None
        base = float(np.median(b))
        best = None
        for a in np.arange(AFTER_W[0], AFTER_W[1] - SLIDE + 1e-6, 1 / 60):
            w = e[(rt >= a) & (rt < a + SLIDE)]
            w = w[np.isfinite(w)]
            if len(w) >= 3:
                best = float(w.mean()) if best is None else max(best, float(w.mean()))
        return None if best is None else best - base

    def signal(self, t, x):
        """(S, SA) at the time t (s) near the net x (m), or None when the file has no frames there."""
        i0, i1 = np.searchsorted(self.t, t + BASE_W[0] - 0.02), np.searchsorted(self.t, t + AFTER_W[1] + 0.02)
        if i1 - i0 < 20:
            return None
        rt = self.t[i0:i1] - t
        near = np.abs(self.xc - np.clip(x, -NET_POST_X, NET_POST_X)) <= NEAR_M
        s = self._rise(self.band[i0:i1], near, rt)
        sa = self._rise(self.above[i0:i1], near, rt)
        return None if s is None or sa is None else (s, sa)


def judge_band(q, r, band):
    """The net band rules on one candidate q of net_events and its judge result r (None = rejected).
    Reject: an accepted touch with S < R_MIN (the net does not move). Confirm: a rejected candidate with S >= C_MIN and
    SA <= A_MAX (the net moves, a player does not) is a touch. Returns the new result (None = no touch)."""
    if band is None:
        return r
    sg = band.signal(q["t"], q["xN"])
    if sg is None:
        return r
    s, sa = sg
    confirm = s >= C_MIN and sa <= A_MAX
    if r is not None:
        if s < R_MIN:
            return None
        if r["conf"] < NET_CONF and confirm:           # a weak touch that the net confirms (E89: as a rejected one)
            return dict(r, conf=NET_CONF + 0.1, src=r["src"] + "+band", band=round(s, 3))
        return dict(r, band=round(s, 3))
    if confirm and abs(q["xN"]) <= NET_POST_X:
        import net_events
        to_far, dconf, dsrc = net_events.direction(q)
        kind = "cord" if q["n_cont"] > 1 else "net"     # the ball goes on along its path after the touch: over the cord
        return {"kind": kind, "toFar": to_far, "conf": 0.5, "src": "band", "dirConf": round(dconf, 2),
                "dirSrc": "+".join(dsrc), "band": round(s, 3)}
    return None


def windows(times, lo=WIN[0], hi=WIN[1]):
    """The union of [t + lo, t + hi] for the candidate times."""
    out = []
    for t in sorted(times):
        a, b = t + lo, t + hi
        if out and a <= out[-1][1]:
            out[-1][1] = max(out[-1][1], b)
        else:
            out.append([a, b])
    return out


def fill_in(mid, blobs, frame_t, cand_times, Hm, path, scale=0.35, fps30=False, progress=None):
    """An old folder (a ball file, no net band file): decode only the windows around the net candidates, with the
    pictures of the ball stage (ball_wasb.frames), and write the net band file. frame_t: the time line of the blobs
    (pts.npy of the folder). fps30: the ball stage of this video ran at 30 fps (the blobs are on each second frame)."""
    import ball_wasb
    crop = ball_wasb.crop_box(mid, scale, bool(ball_wasb.PASSES))[:4]
    frame_t = np.asarray(frame_t)
    rec = Recorder(Hm, crop, scale, 1 if fps30 else 2)
    old_fps = ball_wasb.FPS
    ball_wasb.FPS = 30 if fps30 else None
    try:
        ws = windows(cand_times)
        for j, (a, b) in enumerate(ws):
            a = max(0.0, a)
            rec.gap()
            rec.i = 0
            if fps30:
                rec.index = lambda i, a=a: int(round((a + i / 30.0) * 60.0)) // 2 * 2
            else:
                i0 = int(np.searchsorted(frame_t, a - 1e-4))
                rec.index = lambda i, i0=i0: i0 + i
            for fr in ball_wasb.frames(mid, a, b - a, scale):
                rec.feed(fr)
            if progress:
                progress(j + 1, len(ws))
    finally:
        ball_wasb.FPS = old_fps
    return rec.finish(blobs, path)

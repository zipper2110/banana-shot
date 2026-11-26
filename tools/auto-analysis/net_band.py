"""Net band motion, level 2 (E88 to E133): the signal of the net picture for the net events (net_events.py).

The net moves after a touch, also when the ball track does not show the touch. Level 1 (net_band_mask.py) keeps the
masks of the moved pixels of the ball-stage pictures. This level: for each frame and each bin of 0.5 m along the net,
the part of the pixels that moved, with no pixel near a ball blob (the ball itself moves). Two bands: the net (base
to cord + 0.12 m) and a control band above the cord (0.25 to 0.85 m; a player moves there, the net does not).

  build(mask_file, blobs, Hm, path)   the file net_band.npz: frame (index on the time line of the blobs), band and
                                      above (n x bins, float16), edges (m). Cheap: no decode.
  Band(path, frame_t).signal(t, x)    S (the rise of the band motion after t near the net x) and SA (the same for the
                                      control band)
  judge_band(q, r, band)              the reject and confirm rules for a candidate of net_events
The values of the rules come from the held-out test on the lab labels (E130, E131).
"""
import os
import numpy as np
import cv2

BIN_EDGES = np.arange(-6.5, 6.51, 0.5)          # m along the net (x), post to post and 0.1 m more
Z_LO, Z_HI = -0.05, 0.12                        # the band: from the net base to the cord, with a margin (m)
ABOVE = (0.25, 0.85)                            # m above the cord: the control band
NET_POST_X, NET_POST_Z, NET_MID_Z = 6.40, 1.07, 0.914
BALL_R = 16.0                                   # px of the 1920 frame: no pixel near a ball blob counts
BASE_W = (-0.7, -0.2)                           # s after the candidate time: the baseline window
AFTER_W = (0.0, 0.8)                            # s: the net moves in this window after a touch
SLIDE = 0.3                                     # s: the mean over this time, its maximum in AFTER_W
NEAR_M = 1.25                                   # m: the bins near the touch x
R_MIN, C_MIN, A_MAX = 0.15, 0.25, 0.02          # reject below R_MIN; confirm at C_MIN or more with SA at A_MAX or less (E131)
NET_CONF = 0.4                                  # the confidence from which a net event counts (shot_chain.NET_CONF)
VERSION = 1                                     # the file format number only; the cache key is the code of the import closure


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
    """The bins in the box of the level-1 masks: a label image (bin id 0..2*nb-1, -1 = none)."""

    def __init__(self, Hm, crop, scale, box):
        import ball_3d
        P = ball_3d.camera(np.asarray(Hm, float))[0]
        x0, y0 = crop[:2]
        a, b, c, d = box
        self.scale, self.x0, self.y0, self.a, self.c = scale, x0, y0, a, c
        self.nb = len(BIN_EDGES) - 1
        lab = np.full((b - a, d - c), -1, np.int16)
        for k, p in enumerate(polys(P) + polys(P, *ABOVE)):
            m = np.zeros(lab.shape, np.uint8)
            cv2.fillPoly(m, [np.int32(np.round(p * scale - [x0 + c, y0 + a]))], 1)
            lab[(m > 0) & (lab < 0)] = k
        self.lab = lab
        self.inside = lab >= 0
        self.area = np.bincount(lab[self.inside], minlength=2 * self.nb).astype(np.float64)

    def px(self, x, y):
        """Pixel of the 1920 frame -> column, row in the box."""
        return int(round(x * self.scale - self.x0 - self.c)), int(round(y * self.scale - self.y0 - self.a))


def build(mask_file, blobs, Hm, path):
    """Level 2 from a level-1 file and the ball blobs (rows frame, x, y, score, peak; px of the 1920 frame)."""
    import net_band_mask
    M = net_band_mask.Masks(mask_file)
    nb = len(BIN_EDGES) - 1
    n = len(M)
    band = np.zeros((n, nb), np.float16)
    above = np.zeros((n, nb), np.float16)
    if n and M.shape[0] > 0 and M.shape[1] > 0:
        g = Geometry(Hm, M.crop, M.scale, M.box)
        by = {}
        if len(blobs):
            bl = np.asarray(blobs)
            fr = bl[:, 0].astype(int)
            o = np.argsort(fr, kind="stable")
            fr, bl = fr[o], bl[o]
            cut = np.flatnonzero(np.diff(fr)) + 1
            for part in np.split(bl, cut):
                by[int(part[0, 0])] = part
        r = max(1, int(round(BALL_R * M.scale)))
        for j in range(n):
            m = M.mask(j) & g.inside
            area = g.area
            near = [p for f in (int(M.frame[j]), int(M.prev[j])) for p in by.get(f, ())]
            if near:
                keep = np.ones(m.shape, np.uint8)
                for p in near:
                    cv2.circle(keep, g.px(p[1], p[2]), r, 0, -1)
                keep = keep.astype(bool)
                m &= keep
                area = np.bincount(g.lab[g.inside & keep], minlength=2 * nb).astype(np.float64)
            cnt = np.bincount(g.lab[m], minlength=2 * nb)
            with np.errstate(all="ignore"):
                v = np.where(area > 20 * M.scale * M.scale, cnt / np.maximum(area, 1), np.nan)
            band[j], above[j] = v[:nb], v[nb:]
    np.savez(path, frame=np.asarray(M.frame, np.int32), band=band, above=above, edges=BIN_EDGES, version=VERSION)
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


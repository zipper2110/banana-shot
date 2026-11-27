"""Automatic court detection in one frame. No user input.

Method:
1. White-line mask (top-hat). Line segments (Hough) are merged into long
   lines. Each line goes to the "horizontal" family (baselines, service
   lines) or to the "side" family (sidelines, center line).
2. Hypotheses: 2 horizontal lines take two of {far baseline, far service
   line, near service line, near baseline}, 2 side lines take two of
   {left doubles, left singles, center, right singles, right doubles}. The 4
   intersections give a homography. Folded, mirrored or absurd models are
   rejected. A coarse chamfer score of the full court model ranks the rest.
3. The best hypotheses get a chamfer refinement. The best refined model wins.
   The confidence signals are the chamfer cost, the fraction of model points
   on white pixels, and the margin to the best different model.

Court coordinates as in court_fit.py: origin at the net center, x to the
right, y positive on the far side. H maps image px -> court meters.

Usage:
  python court_auto.py <match> <time s>     detect at that time, write an overlay
  python court_auto.py --eval [match ...]   compare with the reference court.json
"""
import json, os, sys, time
import numpy as np
import cv2
from scipy.optimize import minimize
import lab
from court_fit import SW, DW, BL, SL

CAP = 14.0
CLUTTER_W = 3.0                           # score px for each unit of clutter()
UNEXPL_W = 4.0                            # score px if no near-half horizontal line is explained
OUT_W = 0.25 * CAP                        # score px if all model points are outside the frame
HID_CAP = 1.0                             # largest cost px of a model point that the camera sees through the net (E23b)
LOW_RULE = True                           # False: the rule is off (the second pass of detect())
NET_H = 0.95                              # net height m for that rule (0.914 at the center, 1.07 at the posts)
# limits of a trusted detection: (cost, margin) pairs, and the clutter limit.
# A clear court: cost under 1 px. A dusk or faint court: cost up to 2 px, but
# then the next different model must be clearly worse.
OK_RULES, OK_CLUTTER = ((1.0, 0.5), (2.0, 1.0)), 1.0
ROWS = [BL, SL, -SL, -BL]                 # horizontal court lines, image top to bottom
COLS = [-DW, -SW, 0.0, SW, DW]            # side court lines, image left to right
COURT4 = np.array([(-SW, -SL), (SW, -SL), (-SW, SL), (SW, SL)], np.float32)   # refinement parameters
SEGS = [((-DW, -BL), (DW, -BL)), ((-DW, BL), (DW, BL)), ((-DW, -BL), (-DW, BL)), ((DW, -BL), (DW, BL)),
        ((-SW, -BL), (-SW, BL)), ((SW, -BL), (SW, BL)), ((-SW, -SL), (SW, -SL)), ((-SW, SL), (SW, SL)),
        ((0, -SL), (0, SL))]
SAMPLES = np.concatenate([np.linspace(a, b, 80) for a, b in SEGS]).astype(np.float32)
COARSE = np.concatenate([np.linspace(a, b, 24) for a, b in SEGS]).astype(np.float32)
QUAD = np.array([(-DW, -BL), (DW, -BL), (DW, BL), (-DW, BL)], np.float32)     # full doubles court


def line_mask(img, thr=16):
    """White-line mask. Top-hat of the smallest color channel: a white line is
    bright in all channels, green trees and a blue court are not. Pixels in
    textured areas (trees, the net mesh, crowds) are removed: there the
    gradient directions are not coherent. The low threshold floor also works
    in a dark frame."""
    th = cv2.morphologyEx(img.min(2), cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_RECT, (17, 17))).astype(np.float32)
    mask = th > thr
    gx, gy = cv2.Sobel(th, cv2.CV_32F, 1, 0, ksize=3), cv2.Sobel(th, cv2.CV_32F, 0, 1, ksize=3)
    jxx, jyy, jxy = (cv2.boxFilter(a, -1, (25, 25)) for a in (gx * gx, gy * gy, gx * gy))
    coherence = np.sqrt((jxx - jyy) ** 2 + 4 * jxy ** 2) / (jxx + jyy + 1e-6)
    return mask & (coherence > 0.5)


def to_img(H, pts):
    """Court meters -> image px."""
    return cv2.perspectiveTransform(np.asarray(pts, np.float32)[None], np.linalg.inv(H))[0]


_QUAD1, _COURT41, _R1 = np.c_[QUAD, np.ones(4)], np.c_[COURT4, np.ones(4)], [1, 2, 3, 0]   # E186: constants of valid_H


def valid_H(H, w=1920, h=1080):
    """True if the full court quad is a sane, not folded image of the court:
    convex, near side below the far side, left side left of the right side,
    the near baseline wider than the far baseline, a sane size."""
    try:
        Hi = np.linalg.inv(H)
    except np.linalg.LinAlgError:
        return False
    q = _QUAD1 @ Hi.T                                  # E186: the same values with less overhead (no np.c_, np.roll)
    if not ((q[:, 2] > 1e-9).all() or (q[:, 2] < -1e-9).all()):
        return False                                  # the court crosses the horizon
    p = q[:, :2] / q[:, 2:]
    nl, nr, fr, fl = p
    if not (nl[0] < nr[0] and fl[0] < fr[0] and nl[1] > fl[1] + 20 and nr[1] > fr[1] + 20):
        return False
    if not (nr[0] - nl[0]) > (fr[0] - fl[0]):
        return False
    e = p[_R1] - p
    z = e[:, 0] * e[_R1][:, 1] - e[:, 1] * e[_R1][:, 0]
    if not ((z > 0).all() or (z < 0).all()):
        return False                                   # folded ("bow-tie") or not convex
    fw = fr[0] - fl[0]
    depth = (nl[1] + nr[1]) / 2 - (fl[1] + fr[1]) / 2          # image height of the court at its center
    area = 0.5 * abs(np.dot(p[:, 0], p[_R1, 1]) - np.dot(p[:, 1], p[_R1, 0]))
    # the camera is behind the near baseline: the far baseline is (nearly) in the frame
    far_in = -0.15 * w < fl[0] and fr[0] < 1.15 * w
    if not (area > 0.12 * w * h and 0.08 * w < fw < 1.2 * w and depth > 0.2 * h and far_in
            and -0.1 * h < min(fl[1], fr[1]) < 0.8 * h and max(nl[1], nr[1]) > 0.35 * h):
        return False
    # the 4 service-line corners are inside the frame in all lab matches, and
    # most of the model is visible: this rejects models that hide almost all
    # of their lines outside the frame and fit only the net tape
    s = _COURT41 @ Hi.T
    s = s[:, :2] / s[:, 2:]
    if not ((s[:, 0] >= 0) & (s[:, 0] < w) & (s[:, 1] >= 0) & (s[:, 1] < h)).all():
        return False
    # the service boxes have a real image height, and the near baseline does
    # not run off to "infinity" (a nearly flat model)
    if (s[0, 1] + s[1, 1]) / 2 - (s[2, 1] + s[3, 1]) / 2 < 0.08 * h or not (-3 * w < nl[0] and nr[0] < 4 * w):
        return False
    pts = cv2.perspectiveTransform(SAMPLES[None], Hi)[0]          # visible(H, w, h), with the inverse of above
    return int(((pts[:, 0] >= 0) & (pts[:, 0] < w - 1) & (pts[:, 1] >= 0) & (pts[:, 1] < h - 1)).sum()) >= 0.6 * len(SAMPLES)


def _gt(a, b):
    """a > b with a small slack (a superset of a > b for float noise of a different evaluation order)."""
    return a > b - 1e-6 * (np.abs(a) + np.abs(b)) - 1e-9


def valid_pre(Hs, w=1920, h=1080):
    """E186: valid_H for a batch, as a pre-check. True for each H that valid_H can accept (the same tests with a small
    slack, without the visible() count); False: valid_H rejects it. The caller still calls valid_H for each True, so
    the result does not change. Hs: (N, 3, 3)."""
    Hs = np.asarray(Hs, float).reshape(-1, 3, 3)
    out = np.ones(len(Hs), bool)
    if not len(Hs):
        return out
    with np.errstate(all="ignore"):
        det = np.linalg.det(Hs)
        good = np.isfinite(det) & (np.abs(det) > 1e-300)
        try:
            Hi = np.linalg.inv(np.where(good[:, None, None], Hs, np.eye(3)))
        except np.linalg.LinAlgError:
            return out
        q = np.c_[QUAD, np.ones(4)][None] @ Hi.transpose(0, 2, 1)
        z = q[..., 2]
        ok = _gt(z, 1e-9).all(1) | _gt(-z, 1e-9).all(1)
        pp = q[..., :2] / q[..., 2:]
        nl, nr, fr, fl = pp[:, 0], pp[:, 1], pp[:, 2], pp[:, 3]
        ok &= _gt(nr[:, 0], nl[:, 0]) & _gt(fr[:, 0], fl[:, 0]) & _gt(nl[:, 1], fl[:, 1] + 20) & _gt(nr[:, 1], fr[:, 1] + 20)
        ok &= _gt(nr[:, 0] - nl[:, 0], fr[:, 0] - fl[:, 0])
        e = np.roll(pp, -1, 1) - pp
        e2 = np.roll(e, -1, 1)
        zz = e[..., 0] * e2[..., 1] - e[..., 1] * e2[..., 0]
        ok &= _gt(zz, 0).all(1) | _gt(-zz, 0).all(1)
        fw = fr[:, 0] - fl[:, 0]
        depth = (nl[:, 1] + nr[:, 1]) / 2 - (fl[:, 1] + fr[:, 1]) / 2
        area = 0.5 * np.abs((pp[..., 0] * np.roll(pp[..., 1], -1, 1)).sum(1) - (pp[..., 1] * np.roll(pp[..., 0], -1, 1)).sum(1))
        lo, hi = np.minimum(fl[:, 1], fr[:, 1]), np.maximum(nl[:, 1], nr[:, 1])
        ok &= (_gt(area, 0.12 * w * h) & _gt(fw, 0.08 * w) & _gt(1.2 * w, fw) & _gt(depth, 0.2 * h) & _gt(fl[:, 0], -0.15 * w)
               & _gt(1.15 * w, fr[:, 0]) & _gt(lo, -0.1 * h) & _gt(0.8 * h, lo) & _gt(hi, 0.35 * h))
        sq = np.c_[COURT4, np.ones(4)][None] @ Hi.transpose(0, 2, 1)
        sp = sq[..., :2] / sq[..., 2:]
        ok &= (_gt(sp[..., 0], 0) & _gt(w, sp[..., 0]) & _gt(sp[..., 1], 0) & _gt(h, sp[..., 1])).all(1)
        ok &= _gt((sp[:, 0, 1] + sp[:, 1, 1]) / 2 - (sp[:, 2, 1] + sp[:, 3, 1]) / 2, 0.08 * h) & _gt(nl[:, 0], -3 * w) & _gt(4 * w, nr[:, 0])
    return np.where(good, ok, True)


# ---------------------------------------------------------------- lines

def find_lines(mask):
    """Long straight lines in the mask: list of (point, unit direction, support px, (end1, end2))."""
    segs = cv2.HoughLinesP(mask.astype(np.uint8) * 255, 1, np.pi / 720, threshold=60, minLineLength=60, maxLineGap=10)
    if segs is None:
        return []
    segs = segs.reshape(-1, 4).astype(float)
    segs = segs[np.argsort(-np.hypot(segs[:, 2] - segs[:, 0], segs[:, 3] - segs[:, 1]))]
    clusters = []                                      # [point, direction, endpoints list, length]
    for x1, y1, x2, y2 in segs:
        a, b = np.array([x1, y1]), np.array([x2, y2])
        L = np.linalg.norm(b - a)
        d = (b - a) / L
        for c in clusters:
            p, e = c[0], c[1]
            n = np.array([-e[1], e[0]])
            if abs(d @ e) > np.cos(np.radians(2.0)) and abs((a - p) @ n) < 6 and abs((b - p) @ n) < 6:
                c[2] += [a, b]
                c[3] += L
                break
        else:
            clusters.append([(a + b) / 2, d, [a, b], L])
    ys, xs = np.nonzero(mask)
    pix = np.stack([xs, ys], 1).astype(float)
    hh, ww = mask.shape
    fitted = []
    for p, d, ends, L in clusters:
        if L < 120:
            continue
        E = np.array(ends)
        c = E.mean(0)
        _, _, vt = np.linalg.svd(E - c)
        d = vt[0]
        lo, hi = ((E - c) @ d).min(), ((E - c) @ d).max()
        rel = pix - c
        sel = (np.abs(rel @ np.array([-d[1], d[0]])) < 3) & (rel @ d > lo - 5) & (rel @ d < hi + 5)
        if sel.sum() > 50:                             # refit on the mask pixels
            P = pix[sel]
            c = P.mean(0)
            _, _, vt = np.linalg.svd(P - c)
            d = vt[0]
        # a court line is thin: few mask pixels beside it (rejects the net mesh, trees, text)
        t = np.linspace(lo, hi, 60)
        n = np.array([-d[1], d[0]])
        on = side = 0
        for o in (-1, 0, 1):
            q = (c + np.outer(t, d) + o * n).round().astype(int)
            k = (q[:, 0] >= 0) & (q[:, 0] < ww) & (q[:, 1] >= 0) & (q[:, 1] < hh)
            on += mask[q[k, 1], q[k, 0]].sum()
        for o in (-10, -7, 7, 10):
            q = (c + np.outer(t, d) + o * n).round().astype(int)
            k = (q[:, 0] >= 0) & (q[:, 0] < ww) & (q[:, 1] >= 0) & (q[:, 1] < hh)
            side += mask[q[k, 1], q[k, 0]].sum()
        if side / 4 > 0.35 * max(on / 3, 1):
            continue
        fitted.append([c, d, L, E])
    # merge lines that are the same line (two clusters of one court line)
    fitted.sort(key=lambda l: -l[2])
    out = []
    for c, d, L, E in fitted:
        for l2 in out:
            n = np.array([-l2[1][1], l2[1][0]])
            if abs(d @ l2[1]) > np.cos(np.radians(1.5)) and abs((c - l2[0]) @ n) < 5:
                l2[2] += L
                l2[3] = np.vstack([l2[3], E])
                break
        else:
            out.append([c, d, L, E])
    res = []
    for c, d, L, E in out:
        t = (E - c) @ d
        res.append((c, d, L, (c + t.min() * d, c + t.max() * d)))
    return res


def homog_batch(src, dst):
    """Batched 4-point homographies. src, dst: (N, 4, 2). Returns (N, 3, 3), src -> dst."""
    N = len(src)
    A = np.zeros((N, 8, 8))
    bvec = np.zeros((N, 8))
    x, y, u, v = src[..., 0], src[..., 1], dst[..., 0], dst[..., 1]
    A[:, 0::2, 0], A[:, 0::2, 1], A[:, 0::2, 2] = x, y, 1
    A[:, 0::2, 6], A[:, 0::2, 7] = -x * u, -y * u
    A[:, 1::2, 3], A[:, 1::2, 4], A[:, 1::2, 5] = x, y, 1
    A[:, 1::2, 6], A[:, 1::2, 7] = -x * v, -y * v
    bvec[:, 0::2], bvec[:, 1::2] = u, v
    ok = np.abs(np.linalg.det(A)) > 1e-12
    h = np.zeros((N, 8))
    h[ok] = np.linalg.solve(A[ok], bvec[ok][..., None])[..., 0]
    return np.concatenate([h, np.ones((N, 1))], 1).reshape(N, 3, 3), ok


def intersect(l1, l2):
    (p, d), (q, e) = l1, l2
    A = np.array([d, -e]).T
    if abs(np.linalg.det(A)) < 1e-6:
        return None
    t = np.linalg.solve(A, q - p)
    return p + t[0] * d


# ---------------------------------------------------------------- camera

def camera(Hs, w=1920, h=1080):
    """Focal length (px) and camera center (court meters, the third value is the height with any sign) of
    homographies image -> court. Batch: Hs is (N, 3, 3) or (3, 3). Assumptions: square pixels, the
    principal point at the image center. The two constraints on the focal length (the court axes are
    orthogonal and have the same scale) get a least-squares solution."""
    G = np.linalg.inv(np.asarray(Hs, float).reshape(-1, 3, 3))
    G = np.array([[1, 0, -w / 2], [0, 1, -h / 2], [0, 0, 1.0]]) @ G
    g1, g2, g3 = G[:, :, 0], G[:, :, 1], G[:, :, 2]
    a1, b1 = g1[:, 0] * g2[:, 0] + g1[:, 1] * g2[:, 1], g1[:, 2] * g2[:, 2]
    a2, b2 = g1[:, 0] ** 2 + g1[:, 1] ** 2 - g2[:, 0] ** 2 - g2[:, 1] ** 2, g1[:, 2] ** 2 - g2[:, 2] ** 2
    wv = -(a1 * b1 + a2 * b2) / (a1 ** 2 + a2 ** 2 + 1e-12)
    f = np.clip(np.where(wv > 0, 1 / np.sqrt(np.maximum(wv, 1e-12)), 1.2 * w), 0.4 * w, 4 * w)
    k = np.stack([1 / f, 1 / f, np.ones_like(f)], 1)
    r1, r2, t = g1 * k, g2 * k, g3 * k
    lam = (np.linalg.norm(r1, axis=1) + np.linalg.norm(r2, axis=1)) / 2 * np.where(t[:, 2] < 0, -1.0, 1.0) + 1e-12
    r1, r2, t = r1 / lam[:, None], r2 / lam[:, None], t / lam[:, None]
    return f, -np.stack([(r1 * t).sum(1), (r2 * t).sum(1), (np.cross(r1, r2) * t).sum(1)], 1)


def hidden(Hs, samples=SAMPLES, w=1920, h=1080):
    """For each model point: True if the camera sees it through the net. Batch: (N, P) for Hs (N, 3, 3).
    Such a point is on the far half, and the ray from the camera to it goes through the net plane below
    the net tape. The detection mask removes the net mesh, so a court line there is mostly not in the mask.
    From a low camera (2 m) the full far half is behind the net. The rule is only for that case."""
    if not LOW_RULE:                                  # E186: the rule is off, so no point is hidden (as before)
        return np.zeros((np.asarray(Hs).reshape(-1, 3, 3).shape[0], len(samples)), bool)
    _, C = camera(Hs, w, h)
    cy, hc = C[:, 1:2], np.abs(C[:, 2:3])
    y = np.asarray(samples)[None, :, 1]
    with np.errstate(divide="ignore", invalid="ignore"):
        z = hc * y / (y - cy)
    # Only for a low camera, where the net hides the far half up to the far baseline. With the rule for
    # each camera height, 3 of the 6 lab matches (camera 3.1 to 3.7 m high) got wrong models.
    low = (cy < 0) & (hc * BL / (BL - cy) < NET_H) & LOW_RULE
    return (y > 0.05) & low & (z < NET_H)


# ---------------------------------------------------------------- scoring

def visible(H, w, h, samples=SAMPLES):
    pts = to_img(H, samples)
    return int(((pts[:, 0] >= 0) & (pts[:, 0] < w - 1) & (pts[:, 1] >= 0) & (pts[:, 1] < h - 1)).sum())


def make_cost(dt, anchor, cap=CAP, limit=60, samples=SAMPLES):
    """Chamfer cost of the model given by the 4 service-line corners.
    Limits against a collapse of the model: the corners stay within "limit"
    px of "anchor", the full court stays valid, and the visible part of the
    model must not shrink much."""
    hh, ww = dt.shape
    Ha = cv2.getPerspectiveTransform(anchor.astype(np.float32), COURT4)
    a_vis = visible(Ha, ww, hh, samples)
    lim = np.where(hidden(Ha, samples, ww, hh)[0], min(HID_CAP, cap), cap)       # the points do not change during the refinement

    def cost(c):
        if np.abs(c.reshape(4, 2) - anchor).max() > limit:
            return cap
        Hm = cv2.getPerspectiveTransform(c.reshape(4, 2).astype(np.float32), COURT4)
        if not valid_H(Hm, ww, hh):
            return cap
        pts = to_img(Hm, samples)
        ok = (pts[:, 0] >= 0) & (pts[:, 0] < ww - 1) & (pts[:, 1] >= 0) & (pts[:, 1] < hh - 1)
        if ok.sum() < max(0.4 * len(samples), 0.8 * a_vis):
            return cap
        d = cv2.remap(dt, pts[ok, 0].reshape(-1, 1), pts[ok, 1].reshape(-1, 1), cv2.INTER_LINEAR).ravel()
        return float(np.minimum(d, lim[ok]).mean())
    return cost


def refine(cost, start, iters=3000):
    r = minimize(cost, start.ravel(), method="Powell", options={"xtol": 0.05, "ftol": 1e-4, "maxiter": iters})
    r = minimize(cost, r.x, method="Nelder-Mead", options={"xatol": 0.05, "fatol": 1e-4, "maxiter": iters})
    return r.x.reshape(4, 2), r.fun


def stats(H, dt):
    """Mean capped distance and the fraction of visible model points on white (< 3 px)."""
    hh, ww = dt.shape
    pts = to_img(H, SAMPLES)
    ok = (pts[:, 0] >= 0) & (pts[:, 0] < ww - 1) & (pts[:, 1] >= 0) & (pts[:, 1] < hh - 1)
    d = cv2.remap(dt, pts[ok, 0].reshape(-1, 1), pts[ok, 1].reshape(-1, 1), cv2.INTER_LINEAR).ravel()
    lim = np.where(hidden(H, SAMPLES, ww, hh)[0], HID_CAP, CAP)[ok]
    return float(np.minimum(d, lim).mean()), float((d < 3).mean()), int(ok.sum())


def clutter(H, mask):
    """White pixels inside the court and its run-off but away from the model
    lines, divided by the white pixels on the model lines. The true court is
    clean between its lines; a model placed on trees or stands is not, and a
    model of the wrong size leaves a real court line unexplained."""
    hh, ww = mask.shape
    s = 4                                              # work at 1/4 size
    small = cv2.resize(mask.astype(np.uint8), (ww // s, hh // s), interpolation=cv2.INTER_AREA) > 0
    quad = np.zeros_like(small, np.uint8)
    z = np.c_[QUAD, np.ones(4)] @ np.linalg.inv(H)[2]
    if not ((z > 0).all() or (z < 0).all()):
        return 10.0
    q = to_img(H, QUAD) / s
    if np.abs(q).max() > 1e5:
        return 10.0
    cv2.fillPoly(quad, [q.round().astype(np.int32)], 1)
    lines = np.zeros_like(quad)
    for a, b in SEGS + [((-DW, 0), (DW, 0))]:          # the net is a line here too
        pp = (to_img(H, [a, b]) / s).round().astype(int)
        cv2.line(lines, tuple(pp[0]), tuple(pp[1]), 1, 4)
    on = (small & (lines > 0)).sum()
    off = (small & (quad > 0) & (lines == 0)).sum()
    return float(off / max(on, 1))


def unexplained(H, hor):
    """Part (by support) of the detected horizontal lines in the near half of
    the image (below the net) that no model line explains. In the near half
    the only horizontal lines are the near service line and the near
    baseline, so a model of the wrong size leaves a strong line unexplained."""
    Hi = np.linalg.inv(H)
    rows = []
    for r in (-SL, -BL):                               # model lines as image lines a*x + b*y + c = 0
        p = np.c_[np.array([(-DW, r), (DW, r)]), np.ones(2)] @ Hi.T
        rows.append(np.cross(p[0], p[1]))
    net = np.cross(*(np.c_[np.array([(-DW, 0.0), (DW, 0.0)]), np.ones(2)] @ Hi.T))
    tot = bad = 0.0
    for c, d, L, _ in hor:
        pt = np.array([c[0], c[1], 1.0])
        # below the net line in the image: the sign test uses a point far below
        below = np.sign(net @ pt) == np.sign(net @ np.array([c[0], 1e5, 1.0]))
        if not below:
            continue
        tot += L
        dist = min(abs(l @ pt) / np.hypot(l[0], l[1]) for l in rows)
        if dist > 8:
            bad += L
    return bad / tot if tot else 0.0


def tophat(img):
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    return cv2.morphologyEx(g, cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_RECT, (17, 17)))


def polish(img, H, band=14, th=None):
    """Last small refinement. It uses all bright thin pixels (gray top-hat,
    no texture filter) but only in a band around the model lines, so that
    no clutter can pull the model. This also uses the line parts behind the
    net mesh, which the detection mask removes. th: tophat(img) (E186: detect gives it once for all its polish calls)."""
    th = tophat(img) if th is None else th
    near = np.zeros(th.shape, np.uint8)
    for a, b in SEGS:
        pp = to_img(H, [a, b]).round().astype(int)
        if np.abs(pp).max() < 1e5:
            cv2.line(near, tuple(pp[0]), tuple(pp[1]), 1, 2 * band)
    mask = (th > 14) & (near > 0)
    dt = cv2.distanceTransform((~mask).astype(np.uint8), cv2.DIST_L2, 3)
    c0 = to_img(H, COURT4)
    c, v = refine(make_cost(dt, c0, cap=band, limit=band), c0, iters=1500)
    return cv2.getPerspectiveTransform(c.astype(np.float32), COURT4), v


def model_dist(H1, H2, w=1920, h=1080):
    """Mean px distance between two models over the model points inside the frame (of H1)."""
    a, b = to_img(H1, SAMPLES), to_img(H2, SAMPLES)
    ok = (a[:, 0] >= 0) & (a[:, 0] < w) & (a[:, 1] >= 0) & (a[:, 1] < h)
    if ok.sum() == 0:
        return 1e9
    return float(np.linalg.norm(a[ok] - b[ok], axis=1).mean())


def detect(img, n_h=10, n_s=14, n_refine=8, prior=None):
    """Find the court in one BGR frame. Two passes (E23b):
    1. with the rule for a low camera (hidden()). The result counts only if it is a low-camera model and
       it passes the confidence limits. In the lab matches (camera 3.1 to 3.7 m high) this pass gave
       wrong low-camera models in 11 of 24 frames, but none of them passed the limits;
    2. the search with the rule off, as before E23b.
    Returns as detect_pass(). E186: the two passes share the work that does not depend on the rule (prepare())."""
    global LOW_RULE
    prep = prepare(img, n_h, n_s)
    H, score, info = detect_pass(img, n_h, n_s, n_refine, prior, prep)
    if H is not None and info["ok"] and hidden(H, SAMPLES, img.shape[1], img.shape[0]).any():
        return H, score, dict(info, low=True)
    LOW_RULE = False
    try:
        return detect_pass(img, n_h, n_s, n_refine, prior, prep)
    finally:
        LOW_RULE = True


def lines_hs(lines, n_h, n_s, ww, hh):
    """(hor, side, hor_all) of detect_pass: the horizontal and the side lines, with the pseudo lines, in image order."""
    ang = [abs(np.degrees(np.arctan2(d[1], d[0]))) % 180 for _, d, _, _ in lines]
    hor = [l for l, a in zip(lines, ang) if a < 20 or a > 160]
    side = [l for l, a in zip(lines, ang) if 20 <= a <= 160]
    hor_all = hor
    hor = sorted(hor, key=lambda l: -l[2])[:n_h]
    # A faint court can show only one horizontal line. Then the near end of
    # a steep side line (the center service line ends on the near service
    # line) gives an extra "pseudo" horizontal line, parallel to the
    # strongest horizontal line. The refinement corrects its small error.
    if hor:
        d0 = hor[0][1]
        for c, d, L, ends in lines:
            a = abs(np.degrees(np.arctan2(d[1], d[0]))) % 180
            if 55 <= a <= 125:
                e = max(ends, key=lambda q: q[1])
                if 20 < e[0] < ww - 20 and 20 < e[1] < hh - 20 and all(
                        abs((e - h[0]) @ np.array([-h[1][1], h[1][0]])) > 15 for h in hor):
                    hor.append((e, d0, 0.0, (e, e)))
    side = sorted(side, key=lambda l: -l[2])[:n_s]
    hor.sort(key=lambda l: l[0][1] + (ww / 2 - l[0][0]) * l[1][1] / l[1][0])          # y at the image center column
    yref = np.median([l[0][1] for l in hor]) if hor else hh / 2

    def x_at(l):
        (p, d) = l[0], l[1]
        return p[0] + (yref - p[1]) * d[0] / d[1]
    side.sort(key=x_at)
    return hor, side, hor_all


_D60 = np.array([[(COLS[ci], ROWS[ri]), (COLS[cj], ROWS[ri]), (COLS[ci], ROWS[rj]), (COLS[cj], ROWS[rj])]
                 for ri in range(4) for rj in range(ri + 1, 4) for ci in range(5) for cj in range(ci + 1, 5)], float)


def hypotheses(hor, side, ww, hh):
    """All hypotheses (src image points, dst court points), (N, 4, 2) each, in the order of the old loops: 2
    horizontal lines i < j, 2 side lines a < b, then the 60 choices of 2 court rows and 2 court columns. E186: numpy;
    each intersection once."""
    nh, ns = len(hor), len(side)
    X, good = np.zeros((nh, ns, 2)), np.zeros((nh, ns), bool)
    for r in range(nh):
        for c in range(ns):
            q = intersect(hor[r][:2], side[c][:2])
            if q is not None and not (abs(q[0]) > 6 * ww or abs(q[1]) > 6 * hh):
                X[r, c], good[r, c] = q, True
    ph = np.array([(i, j) for i in range(nh) for j in range(i + 1, nh)], int).reshape(-1, 2)
    ps = np.array([(a, b) for a in range(ns) for b in range(a + 1, ns)], int).reshape(-1, 2)
    I, J = np.repeat(ph[:, 0], len(ps)), np.repeat(ph[:, 1], len(ps))
    A, B = np.tile(ps[:, 0], len(ph)), np.tile(ps[:, 1], len(ph))
    keep = good[I, A] & good[I, B] & good[J, A] & good[J, B]
    I, J, A, B = I[keep], J[keep], A[keep], B[keep]
    P = np.stack([X[I, A], X[I, B], X[J, A], X[J, B]], 1)
    return np.repeat(P, len(_D60), 0), np.tile(_D60, (len(P), 1, 1))


def prepare(img, n_h=10, n_s=14):
    """The part of detect_pass that does not depend on LOW_RULE: the line mask, its distance map, the lines and the
    hypotheses with their homographies."""
    hh, ww = img.shape[:2]
    mask = line_mask(img)
    dt = cv2.distanceTransform((~mask).astype(np.uint8), cv2.DIST_L2, 3)
    hor, side, hor_all = lines_hs(find_lines(mask), n_h, n_s, ww, hh)
    out = {"mask": mask, "dt": dt, "hor": hor, "side": side, "hor_all": hor_all, "th": None}
    if len(hor) >= 2 and len(side) >= 2:
        src, dst = hypotheses(hor, side, ww, hh)
        Hs, okh = homog_batch(src, dst)
        out.update(Hs=Hs, okh=okh, Hi=np.linalg.inv(np.where(okh[:, None, None], Hs, np.eye(3))))
    return out


def detect_pass(img, n_h=10, n_s=14, n_refine=8, prior=None, prep=None):
    """Find the court in one BGR frame. Returns (H, score, info).
    H: 3x3 image px -> court meters, or None.
    score: chamfer cost in px of the polished model (see polish(); lower is
    better; a clear court gives under 0.5 px, a wrong model mostly over 1.3 px).
    info: line counts, clutter, margin (score px) to the best different
    model, and ok = True if the confidence limits pass (OK_RULES,
    OK_RULES, OK_CLUTTER). A caller must not use H when ok is False.
    prior: optional H; hypotheses near it get a small bonus (for a tracker).
    prep: prepare(img, n_h, n_s) (detect gives it to both passes); None: made here."""
    t_start = time.time()
    hh, ww = img.shape[:2]
    prep = prepare(img, n_h, n_s) if prep is None else prep
    mask, dt, hor, side, hor_all = prep["mask"], prep["dt"], prep["hor"], prep["side"], prep["hor_all"]
    info = {"n_hor": len(hor), "n_side": len(side)}
    if len(hor) < 2 or len(side) < 2:
        return None, CAP, dict(info, ok=False, why="too few lines")

    Hs, okh, Hi = prep["Hs"], prep["okh"], prep["Hi"]                     # all hypotheses (prepare)
    # coarse score: model points -> image, nearest pixel lookup in the distance map. E186: the first pass (rule on)
    # also makes the score with the rule off (the same operations as the second pass) and gives it to that pass.
    if not LOW_RULE and "score_off" in prep:
        score = prep["score_off"]
    else:
        M = np.c_[COARSE, np.ones(len(COARSE))]
        score = np.full(len(Hs), np.inf)
        score_off = np.full(len(Hs), np.inf) if LOW_RULE else None
        for k in range(0, len(Hs), 20000):
            q = np.einsum("pj,nij->npi", M, Hi[k:k + 20000])
            z = q[..., 2]
            with np.errstate(divide="ignore", invalid="ignore"):
                x, y = q[..., 0] / z, q[..., 1] / z
            front = (z > 0).all(1) | (z < 0).all(1)
            vis = (x >= 0) & (x < ww - 1) & (y >= 0) & (y < hh - 1) & np.isfinite(x)
            d0 = dt[np.clip(np.nan_to_num(y), 0, hh - 1).astype(int), np.clip(np.nan_to_num(x), 0, ww - 1).astype(int)]
            nv = vis.sum(1)
            bad = (nv < 0.4 * len(COARSE)) | ~front | ~okh[k:k + 20000]
            for out, rule in ((score, LOW_RULE), (score_off, False)):
                if out is None:
                    continue
                hid = hidden(Hs[k:k + 20000], COARSE, ww, hh) if rule else np.zeros(d0.shape, bool)
                d = np.where(vis, np.minimum(d0, np.where(hid, HID_CAP, CAP)), 0)
                sc = d.sum(1) / np.maximum(nv, 1) + OUT_W * (1 - nv / len(COARSE))
                sc[bad] = np.inf
                out[k:k + 20000] = sc
        if score_off is not None:
            prep["score_off"] = score_off
    order = np.argsort(score)
    # keep the best distinct, valid hypotheses
    top = order[:30000]
    fin = top[np.isfinite(score[top])]
    pre = np.zeros(len(Hs), bool)
    pre[fin] = valid_pre(Hs[fin], ww, hh)                 # E186: the batch pre-check; valid_H decides as before
    cands = []
    for k in top:
        if not np.isfinite(score[k]):
            break
        if not pre[k] or not valid_H(Hs[k], ww, hh):
            continue
        if any(model_dist(Hs[k], c) < 15 for _, c in cands):
            continue
        cands.append((score[k] + CLUTTER_W * clutter(Hs[k], mask) + UNEXPL_W * unexplained(Hs[k], hor_all), Hs[k]))
        if len(cands) >= 4 * n_refine:
            break
    info["n_hyp"] = int(len(Hs))
    if not cands:
        return None, CAP, dict(info, ok=False, why="no valid hypothesis")
    cands.sort(key=lambda r: r[0])
    res = []
    if prep["th"] is None:
        prep["th"] = tophat(img)
    for _, Hc in cands[:n_refine]:
        c0 = to_img(Hc, COURT4)
        c, _ = refine(make_cost(dt, c0), c0, iters=1500)
        Hr, v = polish(img, cv2.getPerspectiveTransform(c.astype(np.float32), COURT4), th=prep["th"])
        cl = clutter(Hr, mask)
        un = unexplained(Hr, hor_all)
        # model points outside the frame are unknown, not free: a model that
        # leaves the frame must not win only because it shows fewer lines
        total = v + CLUTTER_W * cl + UNEXPL_W * un + OUT_W * (1 - visible(Hr, ww, hh) / len(SAMPLES))
        if prior is not None and model_dist(prior, Hr, ww, hh) < 40:
            total -= 0.3
        res.append((total, v, cl, Hr, un))
    res.sort(key=lambda r: r[0])
    info["_res"] = res
    best_t, cost_px, cl, H, un = res[0]
    # the best model that differs from the winner: a small margin = an ambiguous frame
    others = [r[0] for r in res[1:] if model_dist(H, r[3], ww, hh) > 15]
    margin = (others[0] - best_t) if others else CAP
    _, inl, nvis = stats(H, dt)
    ok = bool(valid_H(H, ww, hh) and cl < OK_CLUTTER and any(cost_px < c and margin > m for c, m in OK_RULES))
    info.update(inliers=round(inl, 3), clutter=round(cl, 2), unexplained=round(float(un), 2), visible=nvis,
                margin=round(float(margin), 2), ok=ok, seconds=round(time.time() - t_start, 1))
    return H, float(cost_px), info


# ---------------------------------------------------------------- CLI

def frame_at(mid, t, n_frames=9, spread=8):
    """Median of keyframes around time t (s): removes the players."""
    n = lab.n_seconds(mid)
    ims, kf = [], lab.kf_of_second(lab.mdir(mid), n)
    for k in range(-(n_frames // 2), n_frames // 2 + 1):
        s = int(np.clip(t + k * spread, 0, n - 1))
        im = cv2.imread(lab.kf_file(lab.mdir(mid), s, kf))
        if im is not None:
            ims.append(im)
    return np.median(np.stack(ims), 0).astype(np.uint8)


def draw(img, H, color=(0, 0, 255), th=2):
    vis = img.copy()
    if H is None:
        return vis
    for a, b in SEGS + [((-DW, 0), (DW, 0))]:
        pp = to_img(H, [a, b])
        cv2.line(vis, tuple(int(v) for v in pp[0]), tuple(int(v) for v in pp[1]), color, th)
    return vis


def montage(tiles, cols=4, w=480, h=270):
    tiles = [cv2.resize(t, (w, h)) for t in tiles]
    while len(tiles) % cols:
        tiles.append(np.zeros((h, w, 3), np.uint8))
    return np.vstack([np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)])


def evaluate(mids):
    """Mid-match error against the reference court.json, plus 3 more times."""
    from court_fit import clean_frame
    tiles = []
    for mid in mids:
        Href = np.array(json.load(open(os.path.join(lab.mdir(mid), "court.json")))["H"])
        img = clean_frame(mid)
        t = time.time()
        H, sc, info = detect(img)
        info.pop("_res", None)
        err = model_dist(Href, H) if H is not None else float("nan")
        print(f"{mid} mid  err {err:6.2f} px  cost {sc:.2f}  {info}  {time.time() - t:.1f}s", flush=True)
        a, b = lab.eval_span(mid)
        for f in (0.0, 0.25, 0.75):
            tt = int(a + f * (b - a)) + 30
            im = frame_at(mid, tt)
            H2, sc2, info2 = detect(im)
            e2 = model_dist(Href, H2) if H2 is not None else float("nan")
            print(f"{mid} t={tt:5d}  dist to mid ref {e2:6.2f} px  cost {sc2:.2f}  ok {info2['ok']}  inl {info2.get('inliers')}  margin {info2.get('margin')}", flush=True)
            v = draw(im, H2, (0, 0, 255) if info2["ok"] else (0, 255, 255), 3)
            cv2.putText(v, f"{mid} {tt}s c{sc2:.1f}", (20, 60), cv2.FONT_HERSHEY_SIMPLEX, 2, (0, 255, 0), 4)
            tiles.append(v)
    cv2.imwrite(os.path.join(lab.ROOT, "court", "auto_eval.jpg"), montage(tiles, 6, 320, 180), [cv2.IMWRITE_JPEG_QUALITY, 80])


if __name__ == "__main__":
    if sys.argv[1] == "--eval":
        evaluate(sys.argv[2:] or ["m1001", "m1015", "m1016", "m1020", "m1021", "m1030"])
    else:
        mid, t = sys.argv[1], int(sys.argv[2])
        img = frame_at(mid, t)
        H, sc, info = detect(img)
        print(mid, t, "cost %.2f" % sc, info)
        out = os.path.join(lab.ROOT, "court", f"{mid}_auto_{t}.jpg")
        cv2.imwrite(out, cv2.resize(draw(img, H), (960, 540)), [cv2.IMWRITE_JPEG_QUALITY, 80])
        print("overlay:", out)

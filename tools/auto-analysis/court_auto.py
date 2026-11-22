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


def valid_H(H, w=1920, h=1080):
    """True if the full court quad is a sane, not folded image of the court:
    convex, near side below the far side, left side left of the right side,
    the near baseline wider than the far baseline, a sane size."""
    try:
        Hi = np.linalg.inv(H)
    except np.linalg.LinAlgError:
        return False
    q = np.c_[QUAD, np.ones(4)] @ Hi.T
    if not ((q[:, 2] > 1e-9).all() or (q[:, 2] < -1e-9).all()):
        return False                                  # the court crosses the horizon
    p = q[:, :2] / q[:, 2:]
    nl, nr, fr, fl = p
    if not (nl[0] < nr[0] and fl[0] < fr[0] and nl[1] > fl[1] + 20 and nr[1] > fr[1] + 20):
        return False
    if not (nr[0] - nl[0]) > (fr[0] - fl[0]):
        return False
    e = np.roll(p, -1, 0) - p
    z = e[:, 0] * np.roll(e, -1, 0)[:, 1] - e[:, 1] * np.roll(e, -1, 0)[:, 0]
    if not ((z > 0).all() or (z < 0).all()):
        return False                                   # folded ("bow-tie") or not convex
    fw = fr[0] - fl[0]
    depth = (nl[1] + nr[1]) / 2 - (fl[1] + fr[1]) / 2          # image height of the court at its center
    area = 0.5 * abs(np.dot(p[:, 0], np.roll(p[:, 1], -1)) - np.dot(p[:, 1], np.roll(p[:, 0], -1)))
    # the camera is behind the near baseline: the far baseline is (nearly) in the frame
    far_in = -0.15 * w < fl[0] and fr[0] < 1.15 * w
    if not (area > 0.12 * w * h and 0.08 * w < fw < 1.2 * w and depth > 0.2 * h and far_in
            and -0.1 * h < min(fl[1], fr[1]) < 0.8 * h and max(nl[1], nr[1]) > 0.35 * h):
        return False
    # the 4 service-line corners are inside the frame in all lab matches, and
    # most of the model is visible: this rejects models that hide almost all
    # of their lines outside the frame and fit only the net tape
    s = np.c_[COURT4, np.ones(4)] @ Hi.T
    s = s[:, :2] / s[:, 2:]
    if not ((s[:, 0] >= 0) & (s[:, 0] < w) & (s[:, 1] >= 0) & (s[:, 1] < h)).all():
        return False
    # the service boxes have a real image height, and the near baseline does
    # not run off to "infinity" (a nearly flat model)
    if (s[0, 1] + s[1, 1]) / 2 - (s[2, 1] + s[3, 1]) / 2 < 0.08 * h or not (-3 * w < nl[0] and nr[0] < 4 * w):
        return False
    return visible(H, w, h) >= 0.6 * len(SAMPLES)


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
    a_vis = visible(cv2.getPerspectiveTransform(anchor.astype(np.float32), COURT4), ww, hh, samples)

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
        return float(np.minimum(d, cap).mean())
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
    return float(np.minimum(d, CAP).mean()), float((d < 3).mean()), int(ok.sum())


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


def polish(img, H, band=14):
    """Last small refinement. It uses all bright thin pixels (gray top-hat,
    no texture filter) but only in a band around the model lines, so that
    no clutter can pull the model. This also uses the line parts behind the
    net mesh, which the detection mask removes."""
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    th = cv2.morphologyEx(g, cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_RECT, (17, 17)))
    near = np.zeros(g.shape, np.uint8)
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
    """Find the court in one BGR frame. Returns (H, score, info).
    H: 3x3 image px -> court meters, or None.
    score: chamfer cost in px of the polished model (see polish(); lower is
    better; a clear court gives under 0.5 px, a wrong model mostly over 1.3 px).
    info: line counts, clutter, margin (score px) to the best different
    model, and ok = True if the confidence limits pass (OK_RULES,
    OK_RULES, OK_CLUTTER). A caller must not use H when ok is False.
    prior: optional H; hypotheses near it get a small bonus (for a tracker)."""
    t_start = time.time()
    hh, ww = img.shape[:2]
    mask = line_mask(img)
    dt = cv2.distanceTransform((~mask).astype(np.uint8), cv2.DIST_L2, 3)
    lines = find_lines(mask)
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
    info = {"n_hor": len(hor), "n_side": len(side)}
    if len(hor) < 2 or len(side) < 2:
        return None, CAP, dict(info, ok=False, why="too few lines")

    # all hypotheses: 4 image points -> 4 court points
    src, dst = [], []
    for i in range(len(hor)):
        for j in range(i + 1, len(hor)):
            for a in range(len(side)):
                for b in range(a + 1, len(side)):
                    P = [intersect(hor[r][:2], side[s][:2]) for r in (i, j) for s in (a, b)]
                    if any(p is None or abs(p[0]) > 6 * ww or abs(p[1]) > 6 * hh for p in P):
                        continue
                    for ri in range(4):
                        for rj in range(ri + 1, 4):
                            for ci in range(5):
                                for cj in range(ci + 1, 5):
                                    src.append(P)
                                    dst.append([(COLS[ci], ROWS[ri]), (COLS[cj], ROWS[ri]), (COLS[ci], ROWS[rj]), (COLS[cj], ROWS[rj])])
    src, dst = np.array(src, float), np.array(dst, float)
    Hs, okh = homog_batch(src, dst)
    # coarse score: model points -> image, nearest pixel lookup in the distance map
    Hi = np.linalg.inv(np.where(okh[:, None, None], Hs, np.eye(3)))
    M = np.c_[COARSE, np.ones(len(COARSE))]
    score = np.full(len(Hs), np.inf)
    for k in range(0, len(Hs), 20000):
        q = np.einsum("pj,nij->npi", M, Hi[k:k + 20000])
        z = q[..., 2]
        with np.errstate(divide="ignore", invalid="ignore"):
            x, y = q[..., 0] / z, q[..., 1] / z
        front = (z > 0).all(1) | (z < 0).all(1)
        vis = (x >= 0) & (x < ww - 1) & (y >= 0) & (y < hh - 1) & np.isfinite(x)
        d = dt[np.clip(np.nan_to_num(y), 0, hh - 1).astype(int), np.clip(np.nan_to_num(x), 0, ww - 1).astype(int)]
        d = np.where(vis, np.minimum(d, CAP), 0)
        nv = vis.sum(1)
        s = d.sum(1) / np.maximum(nv, 1) + OUT_W * (1 - nv / len(COARSE))
        s[(nv < 0.4 * len(COARSE)) | ~front | ~okh[k:k + 20000]] = np.inf
        score[k:k + 20000] = s
    order = np.argsort(score)
    # keep the best distinct, valid hypotheses
    cands = []
    for k in order[:30000]:
        if not np.isfinite(score[k]):
            break
        if not valid_H(Hs[k], ww, hh):
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
    for _, Hc in cands[:n_refine]:
        c0 = to_img(Hc, COURT4)
        c, _ = refine(make_cost(dt, c0), c0, iters=1500)
        Hr, v = polish(img, cv2.getPerspectiveTransform(c.astype(np.float32), COURT4))
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
    ims = []
    for k in range(-(n_frames // 2), n_frames // 2 + 1):
        s = int(np.clip(t + k * spread, 0, n - 1))
        im = cv2.imread(os.path.join(lab.mdir(mid), "kf", f"{s + 1:05d}.jpg"))
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

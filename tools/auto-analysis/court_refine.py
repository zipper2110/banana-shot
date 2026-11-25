"""E26: sub-pixel refinement of the court model.

Input: the picture of one time window (median of some keyframes) and the court model of court_track.py.
The start model is good to some px in the near half. It can be wrong by 10 px in the far half of a low camera,
because court_auto.py and court_track.py do not use the lines behind the net mesh.

Method:
1. Stations. Each court line gets stations at equal steps in court meters. A station is on the centre of the paint
   (the rules measure the court to the outer edge of each line; the paint is PAINT wide).
2. Bars. At each station the picture is read across the model line (search range R px). A painted line is a
   bright bar: brighter than the ground on the two sides. The bar centre is the middle of its two half-height
   edges (sub-pixel). For the far half the picture gets a grey closing first: the closing removes the dark net
   mesh and keeps a bright line, so the lines behind the net are usable.
3. Tests. A bar is not a painted line if it is wider than a line can be at that place (the net tape, a wall, a
   reflection), or if it is at the place where the camera model puts the net tape, the centre strap, or a post.
4. Fit. The model moves to make the distance between the bar centres and the model lines small. Robust weights
   (an outlier counts little), equal total weight for each court line, search range 16 -> 4 px in steps.
   Model: a free homography (8 numbers), or a camera with square pixels and the principal point at the frame
   centre (7 numbers; with a given focal length: 6 numbers).
5. Quality. Residual in px, count of lines used, and the standard error of the model at the far baseline and at
   the near baseline in m (from the fit covariance).

6. Half models. Real courts are not always one plane (E26: the outdoor courts of the lab have a ridge at the
   net; the side lines have a kink of 6 to 8 px there), and a lens has some distortion. So each half of the court
   also gets its own homography (refine_halves). The two models agree at the net line.

Output file court_track_refined.json: the same list as court_track.json (t0, t1, H, cost, ok, corners), and for
each window:
  "H_near", "H_far": the half models (if "refine"."halves" is true). Use to_court(window, px) for px -> court m.
  "H": the one homography that is nearest to the two half models (for code that can use only one model). If
       there are no half models: the refined one-plane model.
  "refine": {"done", "rms_px", "lines", "far_lines", "n", "far_sigma_m", "near_sigma_m", "moved_px", "line_n":
       {line: count of stations used}, "line_rms", "base_paint_m", "f_px", "cam_m", "halves", "near_rms_px",
       "far_rms_px", "near_n", "far_n", "kink_px"}. rms_px, lines, far_lines: the one-plane fit. far_lines = how
       many of the far service line and the far baseline have 8 or more stations: 0 = do not trust the far end.
A window that is not ok, or where the refinement fails its checks, keeps its model ("done": false).

Usage:
  python court_refine.py <work or match folder> [--frames 9]     write court_track_refined.json
Hook for detect.py after the court stage:  court_refine.run(W(""))
"""
import json, os, sys, time
import numpy as np
import cv2
from scipy.optimize import least_squares
import court_auto
import lab
from court_fit import SW, DW, BL, SL

PAINT = 0.05                              # m: width of the paint of a line
WIDE = {"near_base": 0.11, "far_base": 0.11}   # m: largest paint width of a line (default 0.06); a baseline can be 10 cm
BLUR = 3.0                                # px: a bar can be this much wider than the paint (blur, half-height edges)
MIN_CON = 8.0                             # grey levels: smallest contrast of a bar
NET_POST, NET_C, NET_P = DW + 0.914, 0.914, 1.07   # m: post position (x), net height at the centre and at the posts
NET_TOL = 4.0                             # px: a bar this near to the net tape, the strap or a post is not a line
SCHEDULE = (16, 10, 6, 4, 4)              # px: search range of each round
PAD = 8                                   # px: the profile is read this far beyond the search range
STEP = 0.5                                # px: sample step across the line
ROBUST = 3.0                              # px: scale of the robust weight (soft L1). Real court lines are not exactly straight in the picture (E26)
OVERLAP = 3.0                             # m: a half model also uses the stations of the other half up to this distance from the net
ONE_PLANE_SKIP = ("near_base",)           # lines that the one-plane model does not use (E26: it cannot hold the near baseline)
MAX_MOVE = 45.0                           # px: largest move of a service-line corner
W, HGT = 1920, 1080
COURT4 = court_auto.COURT4

LINES = {"near_base": ((-DW, -BL), (DW, -BL)), "far_base": ((-DW, BL), (DW, BL)),
         "near_serv": ((-SW, -SL), (SW, -SL)), "far_serv": ((-SW, SL), (SW, SL)),
         "center": ((0, -SL), (0, SL)),
         "left_dbl": ((-DW, -BL), (-DW, BL)), "right_dbl": ((DW, -BL), (DW, BL)),
         "left_sgl": ((-SW, -BL), (-SW, BL)), "right_sgl": ((SW, -BL), (SW, BL))}
NAMES = list(LINES)
XS, YS = (-DW, -SW, 0.0, SW, DW), (-BL, -SL, SL, BL)       # coordinates of the lines that cross


def centre_ends(name, paint=PAINT):
    """The two ends of the paint centre line of a court line, in court meters."""
    (x1, y1), (x2, y2) = LINES[name]
    if x1 == x2:
        x = x1 - np.sign(x1) * paint / 2
        return np.array([(x, y1), (x, y2)], float)
    y = y1 - np.sign(y1) * paint / 2
    return np.array([(x1, y), (x2, y)], float)


def stations(paint=PAINT, step=0.15, clear=0.2):
    """Court points on the paint centre of all lines: (points (N, 2), line index (N,)). No station within
    "clear" m of a crossing with another line."""
    P, L = [], []
    for li, name in enumerate(NAMES):
        a, b = centre_ends(name, paint)
        n = int(round(np.linalg.norm(b - a) / step))
        for t in (np.arange(n) + 0.5) / n:
            p = a + t * (b - a)
            if a[0] == b[0]:
                near = min(abs(p[1] - y) for y in YS if not (name == "center" and abs(y) > SL))
            else:
                near = min(abs(p[0] - x) for x in XS if not (name.endswith("serv") and abs(x) > SW))
            if near > clear:
                P.append(p)
                L.append(li)
    return np.array(P), np.array(L)


ST_P, ST_L = stations()


def proj(G, pts):
    """Court meters -> px with the 3x3 matrix G (court -> picture)."""
    q = np.c_[pts, np.ones(len(pts))] @ G.T
    return q[:, :2] / q[:, 2:3]


def camera_P(H, w=W, h=HGT):
    """Full camera from the homography (picture -> court): (P 3x4, f px, centre m), or None. Assumptions: square
    pixels, principal point at the frame centre, no distortion. The same method as ball_3d.camera."""
    A = np.linalg.inv(np.array([[1, 0, w / 2], [0, 1, h / 2], [0, 0, 1.0]])) @ np.linalg.inv(H)
    h1, h2, h3 = A[:, 0], A[:, 1], A[:, 2]
    a = np.array([h1[0] * h2[0] + h1[1] * h2[1], h1[0] ** 2 + h1[1] ** 2 - h2[0] ** 2 - h2[1] ** 2])
    b = np.array([-h1[2] * h2[2], h2[2] ** 2 - h1[2] ** 2])
    u = float(a @ b / (a @ a))
    if not u > 0:
        return None
    f = 1.0 / np.sqrt(u)
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
    K = np.array([[f, 0, w / 2], [0, f, h / 2], [0, 0, 1.0]])
    return K @ np.concatenate([R, t[:, None]], 1), f, C, R, t


def net_parts(H, poly=False):
    """Picture segments of the net parts that look like a line: the tape (2 segments), the strap, the 2 posts.
    Returns a list of (a, b) in px, or [] if the camera model fails. poly: return the outline of the net in the
    picture (5 points) in place of the segments."""
    cam = camera_P(H)
    if cam is None:
        return []
    P = cam[0]

    def pr(x, y, z):
        q = P @ np.array([x, y, z, 1.0])
        return q[:2] / q[2]
    tl, tc, tr = pr(-NET_POST, 0, NET_P - 0.03), pr(0, 0, NET_C - 0.03), pr(NET_POST, 0, NET_P - 0.03)
    if poly:
        return np.array([pr(-NET_POST, 0, 0), pr(NET_POST, 0, 0), tr, tc, tl], np.float32)
    return [(tl, tc), (tc, tr), (pr(0, 0, 0), pr(0, 0, NET_C)), (pr(-NET_POST, 0, 0), pr(-NET_POST, 0, NET_P)),
            (pr(NET_POST, 0, 0), pr(NET_POST, 0, NET_P))]


def seg_dist(p, a, b):
    """Distance of the points p (N, 2) to the segment a-b."""
    d = b - a
    t = np.clip(((p - a) @ d) / max(1e-9, d @ d), 0, 1)
    return np.linalg.norm(p - (a + t[:, None] * d), axis=1)


def bars(sm, min_con=MIN_CON):
    """Bright bars in one profile across a line (samples each STEP px, already smooth). Returns a list of
    (centre index (float), width in samples, contrast): the local maxima that are brighter than the profile at a
    distance h on the two sides, for the best h."""
    n = len(sm)
    resp = np.zeros(n, np.float32)
    best_h = np.zeros(n, int)
    for h in (3, 4, 6, 8, 12, 16, 22):
        if 2 * h + 1 >= n:
            break
        r = np.minimum(sm[h:-h] - sm[:-2 * h], sm[h:-h] - sm[2 * h:])
        up = r > resp[h:-h] * 1.15
        resp[h:-h][up] = r[up]
        best_h[h:-h][up] = h
    out = []
    idx = np.nonzero(resp >= min_con)[0]
    used = np.zeros(n, bool)
    for k in idx[np.argsort(-resp[idx])]:
        if used[k]:
            continue
        h = best_h[k]
        a, b = max(0, k - h), min(n - 1, k + h)
        k2 = a + int(sm[a:b + 1].argmax())                 # the top of the bar
        lo_l, lo_r = sm[max(0, k2 - 2 * h):k2 + 1].min(), sm[k2:k2 + 2 * h + 1].min()
        e = []
        for side, lo in ((-1, lo_l), (1, lo_r)):
            half = (sm[k2] + lo) / 2
            i = k2
            while 0 < i < n - 1 and sm[i] > half:
                i += side
            if sm[i] > half:
                e = None
                break
            j = i - side
            e.append(j + side * (sm[j] - half) / max(1e-6, sm[j] - sm[i]))
        used[max(0, k - h):k + h + 1] = True
        if e is None:
            continue
        out.append(((e[0] + e[1]) / 2, e[1] - e[0], float(resp[k])))
        if len(out) >= 4:
            break
    return out


def observe(gray, closed, H, R, avg=2):
    """Bar centres near the model lines. Returns (points px (N, 2), station index (N,), width m (N,): the bar
    width less 1.5 px of blur, as a length on the court). For each
    station: the strongest bar within R px of the model line that passes the width test and the net test."""
    G = np.linalg.inv(H)
    c = proj(G, ST_P)
    # direction of each line at each station, and the px length of 1 m across the line
    along = np.where((np.array([LINES[n][0][0] == LINES[n][1][0] for n in NAMES])[ST_L])[:, None], (0.0, 0.2), (0.2, 0.0))
    d = proj(G, ST_P + along) - c
    d /= np.linalg.norm(d, axis=1, keepdims=True) + 1e-9
    nrm = np.stack([-d[:, 1], d[:, 0]], 1)
    scale = np.linalg.norm(proj(G, ST_P + along[:, ::-1]) - c, axis=1) / 0.2          # px for each m across the line
    offs = np.arange(-R - PAD, R + PAD + STEP / 2, STEP)       # read more than the search range: a wide bar needs its two sides
    ok = (c[:, 0] > 3) & (c[:, 0] < W - 4) & (c[:, 1] > 3) & (c[:, 1] < HGT - 4)
    idx = np.nonzero(ok)[0]
    if len(idx) == 0:
        return np.zeros((0, 2)), np.zeros(0, int), np.zeros(0)
    prof = np.zeros((len(idx), len(offs)), np.float32)
    far = ST_P[idx, 1] > 0.3                                # far half: read the picture with the closing
    for a in range(-avg, avg + 1):
        xs = (c[idx, 0:1] + a * d[idx, 0:1] + offs[None] * nrm[idx, 0:1]).astype(np.float32)
        ys = (c[idx, 1:2] + a * d[idx, 1:2] + offs[None] * nrm[idx, 1:2]).astype(np.float32)
        pr = cv2.remap(gray, xs, ys, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
        if far.any():
            pr[far] = cv2.remap(closed, xs[far], ys[far], cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
        prof += pr
    prof /= 2 * avg + 1
    prof = (prof[:, :-2] + prof[:, 1:-1] + prof[:, 2:]) / 3          # smooth
    o2 = offs[1:-1]
    net = net_parts(H)
    poly = net_parts(H, poly=True) if net else None
    ci = NAMES.index("center")
    pts, sid, wid = [], [], []
    for row, i in enumerate(idx):
        # The centre strap of the net is a bright bar next to the centre line, and it is often not exactly at
        # the court centre. So the centre line is not used where the camera sees it through the net.
        if poly is not None and ST_L[i] == ci and ST_P[i, 1] > 0 and cv2.pointPolygonTest(poly, (float(c[i, 0]), float(c[i, 1])), True) > -6:
            continue
        wmax = WIDE.get(NAMES[ST_L[i]], 0.06) * scale[i] + BLUR
        best = None
        for ctr, wd, con in bars(prof[row]):
            if wd * STEP > wmax:
                continue
            o = o2[0] + ctr * STEP
            if abs(o) > R:
                continue
            p = c[i] + o * nrm[i]
            if net and min(seg_dist(p[None], a, b)[0] for a, b in net) < NET_TOL:
                continue
            if best is None or con > best[2]:
                best = (p, wd * STEP, con)
        if best is not None:
            pts.append(best[0])
            sid.append(i)
            wid.append(max(0.0, best[1] - 1.5) / scale[i])
    return np.array(pts).reshape(-1, 2), np.array(sid, int), np.array(wid)


# ---------------------------------------------------------------- model parameters

def G_of(p, mode, f=None, w=W, h=HGT):
    """Court -> picture matrix of the parameters. mode "H": 8 numbers, the px of the 4 service-line corners.
    mode "cam": focal length, rotation vector, translation (7 numbers); with f given: 6 numbers."""
    if mode == "H":
        return court_auto.homog_batch(COURT4[None].astype(float), np.asarray(p, float).reshape(1, 4, 2))[0][0]
    if f is None:
        f, p = p[0] * 1000.0, p[1:]
    R = cv2.Rodrigues(np.asarray(p[:3], float))[0]
    K = np.array([[f, 0, w / 2], [0, f, h / 2], [0, 0, 1.0]])
    return K @ np.stack([R[:, 0], R[:, 1], np.asarray(p[3:6], float)], 1)


def start_params(H, mode, f=None):
    if mode == "H":
        return court_auto.to_img(H, COURT4).astype(float).ravel()
    cam = camera_P(H)
    if cam is None:
        return None
    _, f0, _, R, t = cam
    if f is not None:                                  # keep the picture size of the court: move the camera along its axis
        t = t * np.array([1, 1, f / f0])
    p = np.r_[cv2.Rodrigues(R)[0].ravel(), t]
    return p if f is not None else np.r_[f0 / 1000.0, p]


def residuals(p, mode, f, pts, ends_a, ends_b, wts, tie=None):
    """Signed px distance of each bar centre to its model line, times its weight. tie: (court points, px points,
    weight): the model must also put these court points at these px (2 more residuals for each point)."""
    try:
        G = G_of(p, mode, f)
    except (np.linalg.LinAlgError, cv2.error):
        return np.full(len(pts) + (2 * len(tie[0]) if tie else 0), 1e3)
    a, b = proj(G, ends_a), proj(G, ends_b)
    d = b - a
    ln = np.linalg.norm(d, axis=1) + 1e-9
    r = wts * ((pts[:, 0] - a[:, 0]) * d[:, 1] - (pts[:, 1] - a[:, 1]) * d[:, 0]) / ln
    return r if tie is None else np.r_[r, (tie[2] * (proj(G, tie[0]) - tie[1])).ravel()]


def all_ends(base_paint=PAINT):
    """Paint centre lines of all court lines (9, 2, 2). The baselines can have a wider paint."""
    return np.array([centre_ends(n, base_paint if n.endswith("base") else PAINT) for n in NAMES])


ENDS = all_ends()


def base_paint(sid, wid):
    """Paint width of the baselines (m) from the bars of the near baseline: 0.05 to 0.10. Default 0.05."""
    m = ST_L[sid] == NAMES.index("near_base")
    return float(np.clip(np.median(wid[m]), 0.05, 0.10)) if m.sum() >= 10 else PAINT


def fit(pts, sid, p0, mode, f=None, rounds=4, c=ROBUST, tie=None, ends=ENDS):
    """Robust fit of the parameters to the bar centres. Each court line has the same total weight (a line with
    fewer than 12 stations has less). Returns (parameters, distances px, weights of the last round, Jacobian)."""
    li = ST_L[sid]
    cnt = np.bincount(li, minlength=len(NAMES))
    base = 1.0 / np.sqrt(np.maximum(cnt[li], 12))
    ea, eb = ends[li, 0], ends[li, 1]
    p, rob = np.array(p0, float), np.ones(len(pts))
    for _ in range(rounds):
        r = least_squares(residuals, p, args=(mode, f, pts, ea, eb, base * rob, tie), method="lm", x_scale="jac", max_nfev=200)
        p = r.x
        dist = residuals(p, mode, f, pts, ea, eb, np.ones(len(pts)))
        rob = (1 + (dist / c) ** 2) ** -0.25              # soft L1
    return p, dist, base * rob, r.jac[:len(pts)]


def refine(img, H0, mode="H", f=None, schedule=SCHEDULE, skip=ONE_PLANE_SKIP):
    """Refine the model H0 (picture px -> court m) on the picture img (BGR, 1920 wide).
    Returns (H, quality dict). If the refinement fails a check: (H0, {"done": False, "why": ...})."""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32)
    closed = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5)))
    H = np.array(H0, float)
    p = start_params(H, mode, f)
    if p is None:
        return np.array(H0, float), {"done": False, "why": "no camera model"}
    c0 = court_auto.to_img(H, COURT4).astype(float)
    out = None
    for R in schedule:
        pts, sid, wid = observe(gray, closed, H, R)
        bp = base_paint(sid, wid)
        use = ~np.isin(ST_L[sid], [NAMES.index(k) for k in skip])
        pts, sid, wid = pts[use], sid[use], wid[use]
        lines_n = np.bincount(ST_L[sid], minlength=len(NAMES)) if len(sid) else np.zeros(len(NAMES), int)
        if (lines_n >= 8).sum() < 4 or len(pts) < 60:
            return np.array(H0, float), {"done": False, "why": "too few line points"}
        try:
            p, dist, wts, jac = fit(pts, sid, p, mode, f, ends=all_ends(bp))
            H = np.linalg.inv(G_of(p, mode, f))
            H /= H[2, 2]
        except (np.linalg.LinAlgError, cv2.error, ValueError):
            return np.array(H0, float), {"done": False, "why": "fit failed"}
        c1 = court_auto.to_img(H, COURT4).astype(float)
        if np.abs(c1 - c0).max() > MAX_MOVE or not court_auto.valid_H(H):
            return np.array(H0, float), {"done": False, "why": "model moved too far"}
        out = (pts, sid, dist, wts, jac)
    pts, sid, dist, wts, jac = out
    inl = np.abs(dist) < 2.5
    li = ST_L[sid]
    line_n = {NAMES[k]: int((inl & (li == k)).sum()) for k in range(len(NAMES))}
    line_rms = {NAMES[k]: round(float(np.sqrt((dist[inl & (li == k)] ** 2).mean())), 2) for k in range(len(NAMES)) if line_n[NAMES[k]] >= 8}
    rms = float(np.sqrt((dist[inl] ** 2).mean()))
    # covariance of the parameters -> standard error of the court place of two fixed picture points
    sig = {}
    try:
        s2 = max(rms, 0.3) ** 2 * (wts[inl] ** 2).mean()
        cov = np.linalg.inv(jac.T @ jac) * s2
        for key, y in (("far_sigma_m", BL), ("near_sigma_m", -BL)):
            px = court_auto.to_img(H, [(0.0, y)]).astype(np.float64)

            def court_y(q):
                Hq = np.linalg.inv(G_of(q, mode, f))
                return cv2.perspectiveTransform(px[None], Hq)[0, 0]
            eps = 1e-4 * np.maximum(1.0, np.abs(p))
            J = np.array([(court_y(p + eps[k] * np.eye(len(p))[k]) - court_y(p - eps[k] * np.eye(len(p))[k])) / (2 * eps[k]) for k in range(len(p))]).T
            sig[key] = round(float(np.sqrt(max(0.0, np.trace(J @ cov @ J.T)))), 3)
    except np.linalg.LinAlgError:
        sig = {"far_sigma_m": None, "near_sigma_m": None}
    q = {"done": True, "rms_px": round(rms, 2), "n": int(inl.sum()), "lines": int(sum(v >= 8 for v in line_n.values())),
         "far_lines": int(sum(line_n[k] >= 8 for k in ("far_serv", "far_base"))), "line_n": line_n, "line_rms": line_rms,
         "moved_px": round(float(np.abs(court_auto.to_img(H, COURT4) - c0).max()), 1), "base_paint_m": round(bp, 3), **sig}
    cam = camera_P(H)
    if cam is not None:
        q["f_px"], q["cam_m"] = round(float(cam[1]), 1), [round(float(v), 2) for v in cam[2]]
    return H, q


def refine_halves(img, H):
    """Two models for a court that is not one plane (a court with a ridge at the net, E26): one for the near
    half and one for the far half. Each model uses the stations of its half and of the first OVERLAP m of the
    other half, and it must agree with the one-plane model H at 5 points of the net line.
    Returns (H near, H far, {"near_rms_px", "far_rms_px"}), or None if a half has too few line points."""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY).astype(np.float32)
    closed = cv2.morphologyEx(gray, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5)))
    tie_c = np.array([(x, 0.0) for x in XS])
    res, q = [], {}
    for name in ("near", "far"):
        Hh = H
        # The net line joins the two models: the near model to the one-plane model, the far model to the near
        # model. A half model that has its two cross lines (baseline and service line) is almost free.
        tie_H, tie_w = (H, 0.45) if name == "near" else (res[0], 0.45)
        for R in (14, 8, 4):
            pts, sid, wid = observe(gray, closed, Hh, R)
            sel = (ST_P[sid, 1] < OVERLAP) if name == "near" else (ST_P[sid, 1] > -OVERLAP)
            if sel.sum() < 40:
                return None
            cross = ("near_base", "near_serv") if name == "near" else ("far_base", "far_serv")
            tie_w = 0.05 if all((ST_L[sid] == NAMES.index(k)).sum() >= 10 for k in cross) else 0.45
            tie = (tie_c, proj(np.linalg.inv(tie_H), tie_c), tie_w)
            try:
                p, dist, _, _ = fit(pts[sel], sid[sel], start_params(Hh, "H"), "H", c=1.5, tie=tie, ends=all_ends(base_paint(sid, wid)))
                Hh = np.linalg.inv(G_of(p, "H"))
            except (np.linalg.LinAlgError, cv2.error, ValueError):
                return None
            if np.abs(court_auto.to_img(Hh, COURT4) - court_auto.to_img(H, COURT4)).max() > 40 or not court_auto.valid_H(Hh):
                return None
        own = (ST_P[sid[sel], 1] < 0) if name == "near" else (ST_P[sid[sel], 1] > 0)
        inl = own & (np.abs(dist) < 2.5)
        q[name + "_rms_px"] = round(float(np.sqrt((dist[inl] ** 2).mean())), 2) if inl.any() else None
        q[name + "_n"] = int(inl.sum())
        res.append(Hh / Hh[2, 2])
    return res[0], res[1], q


def blend(H_near, H_far):
    """One homography that is nearest to the two half models: least squares of the court error (m) on a grid of
    court points. For code that can use only one model."""
    gx, gy = np.meshgrid(np.linspace(-DW, DW, 9), np.linspace(-BL, BL, 25))
    c = np.stack([gx.ravel(), gy.ravel()], 1)
    px = np.where(c[:, 1:2] < 0, proj(np.linalg.inv(H_near), c), proj(np.linalg.inv(H_far), c))
    H, _ = cv2.findHomography(px, c, 0)
    return H / H[2, 2]


def to_court(w, xy):
    """Picture px (N, 2) -> court m with one window of court_track_refined.json. Uses the half models if the
    window has them (the near model for a point that it puts in the near half, else the far model)."""
    xy = np.asarray(xy, np.float64).reshape(1, -1, 2)
    if "H_near" not in w:
        return cv2.perspectiveTransform(xy, np.array(w["H"], np.float64))[0]
    a = cv2.perspectiveTransform(xy, np.array(w["H_near"], np.float64))[0]
    b = cv2.perspectiveTransform(xy, np.array(w["H_far"], np.float64))[0]
    return np.where(a[:, 1:2] < 0, a, b)


# ---------------------------------------------------------------- a full track

def median_frame(folder, t0, t1, n=9):
    ts = np.unique(np.linspace(t0 + 1, max(t0 + 1, t1 - 2), n).astype(int))
    kf = lab.kf_of_second(folder)                  # the picture of second t (G2)
    imgs = [cv2.imread(lab.kf_file(folder, t, kf)) for t in ts]
    imgs = [i for i in imgs if i is not None]
    return np.median(np.stack(imgs), 0).astype(np.uint8) if imgs else None


def run(folder, src="court_track.json", dst="court_track_refined.json", frames=9, mode="H", log=True):
    """Refine each ok window of <folder>/court_track.json. Writes <folder>/court_track_refined.json. Returns the track."""
    t_start = time.time()
    track = json.load(open(os.path.join(folder, src)))
    for w in track:
        if not w.get("ok", True):
            w["refine"] = {"done": False, "why": "window not ok"}
            continue
        if w.get("onePicture"):
            # E55: a camera that moves; the median blurs the lines. The same picture as court_track.window_frame.
            s = min(lab.video_seconds(folder) - 1, w["t0"] + 4 + 3 * 8)
            img = cv2.imread(lab.kf_file(folder, s, lab.kf_of_second(folder)))
        else:
            img = median_frame(folder, w["t0"], w["t1"], frames)
        if img is None:
            w["refine"] = {"done": False, "why": "no picture"}
            continue
        if img.shape[1] != W:
            img = cv2.resize(img, (W, HGT))
        H, q = refine(img, np.array(w["H"]), mode)
        w["refine"] = q
        if q["done"]:
            hv = refine_halves(img, H)
            q["halves"] = hv is not None
            if hv is not None:
                w["H_near"], w["H_far"] = hv[0].tolist(), hv[1].tolist()
                q.update(hv[2])
                # px distance of the two half models at the far baseline: large on a court with a ridge at the net
                q["kink_px"] = round(float(np.abs(proj(np.linalg.inv(hv[0]), [(-DW, BL), (DW, BL)]) - proj(np.linalg.inv(hv[1]), [(-DW, BL), (DW, BL)])).max()), 1)
                H = blend(hv[0], hv[1])
            w["H"] = H.tolist()
            w["corners"] = court_auto.to_img(H, COURT4).tolist()
    json.dump(track, open(os.path.join(folder, dst), "w"))
    if log:
        done = [w["refine"] for w in track if w["refine"]["done"]]
        print(os.path.basename(os.path.normpath(folder)), "windows", len(track), "refined", len(done),
              "| rms px median %.2f" % np.median([q["rms_px"] for q in done]) if done else "",
              "| far lines 2/1/0:", [sum(q["far_lines"] == k for q in done) for k in (2, 1, 0)],
              "| half models", sum(q.get("halves", False) for q in done),
              "| moved px median %.1f max %.1f" % (np.median([q["moved_px"] for q in done]), max(q["moved_px"] for q in done)) if done else "",
              "| %.0f s" % (time.time() - t_start), flush=True)
    return track


if __name__ == "__main__":
    a = sys.argv[1:]
    frames = int(a[a.index("--frames") + 1]) if "--frames" in a else 9
    for folder in [x for x in a if not x.startswith("--") and not x.isdigit()]:
        run(folder, frames=frames)

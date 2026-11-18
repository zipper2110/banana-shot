"""Court model for a fixed camera: rough line guesses snap to the white
court lines, then a homography maps image pixels to court meters.

Input: court_guess.json. For each match 5 lines, each as 2 rough points in
pixels of the 1920-wide keyframe (error up to about 15 px is fine):
  NB near baseline, FB far baseline, LS left singles sideline,
  RS right singles sideline, NS near service line.
Output: <match>/court.json with the 3x3 matrix H (image -> court meters),
and <labRoot>/court/<match>_fit.jpg for a visual check.

Court coordinates: origin at the center of the net. x to the right. y is
positive on the far side and negative on the near (camera) side.
Usage: python court_fit.py [match id ...]
"""
import json, os, sys
import numpy as np
import cv2
import lab

SW, DW, BL, SL = 4.115, 5.485, 11.885, 6.40   # singles half width, doubles half width, baseline, service line


def clean_frame(mid, n=9, spread=40):
    """Median of some keyframes around the middle of the match: removes the players."""
    gt = lab.gt_points(mid)
    t0 = int(gt[len(gt) // 2][0])
    ims = [cv2.imread(os.path.join(lab.mdir(mid), "kf", f"{t0 + 1 + k * spread:05d}.jpg")) for k in range(-(n // 2), n // 2 + 1)]
    return np.median(np.stack([i for i in ims if i is not None]), 0).astype(np.uint8)


def line_mask(img):
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    th = cv2.morphologyEx(g, cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_RECT, (17, 17)))
    return th > max(18, np.percentile(th, 96))


def snap(mask, p, q, band=16):
    """Fit a line to the mask pixels near the guessed line p-q (RANSAC)."""
    p, q = np.array(p, float), np.array(q, float)
    d = (q - p) / np.linalg.norm(q - p)
    nrm = np.array([-d[1], d[0]])
    ys, xs = np.nonzero(mask)
    pts = np.stack([xs, ys], 1).astype(float)
    rel = pts - p
    along, off = rel @ d, rel @ nrm
    L = np.linalg.norm(q - p)
    sel = (np.abs(off) < band) & (along > -0.15 * L) & (along < 1.15 * L)
    pts = pts[sel]
    if len(pts) < 30:
        return p, d, 0
    rng = np.random.default_rng(0)
    best, bi = None, 0
    for _ in range(300):
        a, b = pts[rng.choice(len(pts), 2, replace=False)]
        if np.linalg.norm(b - a) < 0.3 * L:
            continue
        dd = (b - a) / np.linalg.norm(b - a)
        if abs(dd @ d) < 0.995:
            continue
        r = np.abs((pts - a) @ np.array([-dd[1], dd[0]]))
        inl = r < 2.0
        if inl.sum() > bi:
            bi, best = inl.sum(), inl
    if best is None:
        return p, d, 0
    P = pts[best]
    c = P.mean(0)
    _, _, vt = np.linalg.svd(P - c)
    return c, vt[0], int(bi)


def cross(l1, l2):
    (p, d), (q, e) = l1, l2
    A = np.array([d, -e]).T
    t = np.linalg.solve(A, q - p)
    return p + t[0] * d


def fit(mid, guess):
    img = clean_frame(mid)
    mask = line_mask(img)
    lines, support = {}, {}
    for k, (p, q) in guess.items():
        c, d, n = snap(mask, p, q)
        lines[k], support[k] = (c, d), n
    # Stage 1: the 4 guessed corners of the singles court, from the snapped lines.
    for k in guess:
        if support[k] < 100:                       # weak snap: keep the raw guess
            p, q = np.array(guess[k][0], float), np.array(guess[k][1], float)
            lines[k] = (p, (q - p) / np.linalg.norm(q - p))
    corners0 = np.array([cross(lines["NB"], lines["LS"]), cross(lines["NB"], lines["RS"]),
                         cross(lines["FB"], lines["LS"]), cross(lines["FB"], lines["RS"])])
    court4 = np.array([(-SW, -BL), (SW, -BL), (-SW, BL), (SW, BL)], np.float32)
    # Stage 2: chamfer refinement. Move the 4 corners so that all model lines
    # lie on white pixels. Cost = mean distance (capped) from the model lines
    # to the nearest line pixel.
    dt = cv2.distanceTransform((~mask).astype(np.uint8), cv2.DIST_L2, 3)
    CAP = 14.0
    segs_m = [((-DW, -BL), (DW, -BL)), ((-DW, BL), (DW, BL)), ((-DW, -BL), (-DW, BL)), ((DW, -BL), (DW, BL)),
              ((-SW, -BL), (-SW, BL)), ((SW, -BL), (SW, BL)), ((-SW, -SL), (SW, -SL)), ((-SW, SL), (SW, SL)),
              ((0, -SL), (0, SL))]
    samples = np.concatenate([np.linspace(a, b, 80) for a, b in segs_m]).astype(np.float32)
    hh, ww = mask.shape

    def cost(c):
        Hm = cv2.getPerspectiveTransform(c.reshape(4, 2).astype(np.float32), court4)
        try:
            pts = cv2.perspectiveTransform(samples[None], np.linalg.inv(Hm))[0]
        except np.linalg.LinAlgError:
            return CAP
        ok = (pts[:, 0] >= 0) & (pts[:, 0] < ww - 1) & (pts[:, 1] >= 0) & (pts[:, 1] < hh - 1)
        if ok.sum() < 200:
            return CAP
        d = cv2.remap(dt, pts[ok, 0].reshape(-1, 1), pts[ok, 1].reshape(-1, 1), cv2.INTER_LINEAR).ravel()
        return float(np.minimum(d, CAP).mean())

    from scipy.optimize import minimize
    best = None
    for start in (corners0,):
        r = minimize(cost, start.ravel(), method="Powell", options={"xtol": 0.05, "ftol": 1e-4, "maxiter": 4000})
        r = minimize(cost, r.x, method="Nelder-Mead", options={"xatol": 0.05, "fatol": 1e-4, "maxiter": 4000})
        if best is None or r.fun < best.fun:
            best = r
    err0, err = cost(corners0.ravel()), best.fun
    H = cv2.getPerspectiveTransform(best.x.reshape(4, 2).astype(np.float32), court4)
    Hi = np.linalg.inv(H)
    use = "chamfer"
    # draw the full court model
    segs = [((-DW, -BL), (DW, -BL)), ((-DW, BL), (DW, BL)), ((-DW, -BL), (-DW, BL)), ((DW, -BL), (DW, BL)),
            ((-SW, -BL), (-SW, BL)), ((SW, -BL), (SW, BL)), ((-SW, -SL), (SW, -SL)), ((-SW, SL), (SW, SL)),
            ((0, -SL), (0, SL)), ((-DW, 0), (DW, 0))]
    vis = img.copy()
    for a, b in segs:
        pp = cv2.perspectiveTransform(np.array([[a, b]], np.float32), Hi)[0]
        cv2.line(vis, tuple(int(v) for v in pp[0]), tuple(int(v) for v in pp[1]), (0, 0, 255), 2)
    os.makedirs(os.path.join(lab.ROOT, "court"), exist_ok=True)
    cv2.imwrite(os.path.join(lab.ROOT, "court", f"{mid}_fit.jpg"), cv2.resize(vis, (960, 540)), [cv2.IMWRITE_JPEG_QUALITY, 80])
    json.dump({"H": H.tolist(), "image_width": 1920, "support": support, "lines_used": use, "chamfer_px": err, "chamfer_start_px": err0},
              open(os.path.join(lab.mdir(mid), "court.json"), "w"), indent=1)
    print(mid, "support", support, "chamfer cost %.2f px (start %.2f)" % (err, err0), flush=True)


if __name__ == "__main__":
    G = json.load(open(os.path.join(lab.HERE, "court_guess.json")))
    for mid in (sys.argv[1:] or list(G)):
        fit(mid, G[mid])

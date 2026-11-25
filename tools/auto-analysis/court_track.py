"""Court model over time. The camera can move during a match (a bump, a new
position after a break). This script fits the court again each 60 s.

Start: <match>/court.json (court_fit.py, fitted at the middle of the
match). If that file does not exist, court_auto.detect finds the court at
the middle. The fit goes forward and backward in time. Each window starts
from the fit of its neighbor window. If the chamfer cost stays high, the
script also tries shifted starts. If that also fails (a large camera move),
court_auto.detect finds the court again in that window.
E55 (G15): a camera that moves all the time (a hand or a loose mount) moves
the court lines by 20 to 100 px between the pictures of a median, and the
median blurs the thin lines: no start model. Then the start comes from one
picture, and each window uses one picture too ("onePicture" in each window).
A video whose median gives a start model does not change.
A model must always be valid (court_auto.valid_H: the full court quad is
convex and not folded). A window with no trusted model gets ok = False and
keeps the model of its neighbor.
Each window model gets a check: court_auto.polish fits it to the bright
thin pixels near the model lines. "cost" = that check cost in px (a clear
court: under 0.5; a wrong model: mostly over 1.3; a faint court: 1-3).
Output: <match>/court_track.json: list of {t0, t1, H, cost, ok, corners},
and <labRoot>/court/<match>_track.jpg (8 frames with the model in red).
Usage: python court_track.py [match id ...]
"""
import json, os, sys, time
import numpy as np
import cv2
from scipy.optimize import minimize
import lab
from court_fit import SW, DW, BL, SL, line_mask
import court_auto

STEP, CAP = 60, 14.0
COURT4 = np.array([(-SW, -SL), (SW, -SL), (-SW, SL), (SW, SL)], np.float32)   # service-line corners: always in the frame
SEGS = [((-DW, -BL), (DW, -BL)), ((-DW, BL), (DW, BL)), ((-DW, -BL), (-DW, BL)), ((DW, -BL), (DW, BL)),
        ((-SW, -BL), (-SW, BL)), ((SW, -BL), (SW, BL)), ((-SW, -SL), (SW, -SL)), ((-SW, SL), (SW, SL)),
        ((0, -SL), (0, SL))]
SAMPLES = np.concatenate([np.linspace(a, b, 80) for a, b in SEGS]).astype(np.float32)


def window_frame(mid, t0, n, one=False):
    """Median of 7 keyframes of the window: removes the players. one (E55): only the middle picture of the 7, for a
    camera that moves."""
    kf = lab.kf_of_second(lab.mdir(mid))
    if one:
        return cv2.imread(lab.kf_file(lab.mdir(mid), min(n - 1, t0 + 4 + 3 * 8), kf))
    ims = []
    for k in range(7):
        s = min(n - 1, t0 + 4 + k * 8)
        im = cv2.imread(lab.kf_file(lab.mdir(mid), s, kf))
        if im is not None:
            ims.append(im)
    return np.median(np.stack(ims), 0).astype(np.uint8)


def track_mask(img):
    """Line mask that also works in a dark frame."""
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    th = cv2.morphologyEx(g, cv2.MORPH_TOPHAT, cv2.getStructuringElement(cv2.MORPH_RECT, (17, 17)))
    return th > max(8, np.percentile(th, 95.5))


def to_H(c):
    return cv2.getPerspectiveTransform(np.asarray(c, np.float32).reshape(4, 2), COURT4)


def valid(c):
    """The 4 points must make a convex quad in the start orientation (near
    points below far points, left points left of right points), and the
    full court quad must also be valid: not folded, not degenerate."""
    nl, nr, fl, fr = c.reshape(4, 2)
    if not (nl[0] < nr[0] and fl[0] < fr[0] and nl[1] > fl[1] + 10 and nr[1] > fr[1] + 10 and (nr[0] - nl[0]) > (fr[0] - fl[0])):
        return False
    return court_auto.valid_H(to_H(c))


def make_cost(mask, anchor, cap=CAP, limit=120):
    """Chamfer cost. Limits against a collapse of the model: the points
    stay within "limit" px of "anchor", the quad stays valid, and the
    visible part of the model must not shrink much."""
    dt = cv2.distanceTransform((~mask).astype(np.uint8), cv2.DIST_L2, 3)
    hh, ww = mask.shape
    Ha = cv2.getPerspectiveTransform(anchor.astype(np.float32), COURT4)
    a_pts = cv2.perspectiveTransform(SAMPLES[None], np.linalg.inv(Ha))[0]
    # a low camera sees the far half only through the net: these points must not pull the model (E23b)
    lim = np.where(court_auto.hidden(Ha, SAMPLES, ww, hh)[0], min(court_auto.HID_CAP, cap), cap)
    a_vis = int(((a_pts[:, 0] >= 0) & (a_pts[:, 0] < ww - 1) & (a_pts[:, 1] >= 0) & (a_pts[:, 1] < hh - 1)).sum())

    def cost(c):
        if np.abs(c.reshape(4, 2) - anchor).max() > limit or not valid(c):
            return cap
        Hm = cv2.getPerspectiveTransform(c.reshape(4, 2).astype(np.float32), COURT4)
        try:
            pts = cv2.perspectiveTransform(SAMPLES[None], np.linalg.inv(Hm))[0]
        except np.linalg.LinAlgError:
            return cap
        ok = (pts[:, 0] >= 0) & (pts[:, 0] < ww - 1) & (pts[:, 1] >= 0) & (pts[:, 1] < hh - 1)
        if ok.sum() < 0.8 * a_vis:
            return cap
        d = cv2.remap(dt, pts[ok, 0].reshape(-1, 1), pts[ok, 1].reshape(-1, 1), cv2.INTER_LINEAR).ravel()
        return float(np.minimum(d, lim[ok]).mean())
    return cost


def refine(cost, start):
    r = minimize(cost, start.ravel(), method="Powell", options={"xtol": 0.05, "ftol": 1e-4, "maxiter": 3000})
    r = minimize(cost, r.x, method="Nelder-Mead", options={"xatol": 0.05, "fatol": 1e-4, "maxiter": 3000})
    return r.x.reshape(4, 2), r.fun


TRUST, LOOSE = 1.0, 3.0      # check cost (court_auto.polish px): trusted, and still acceptable if nothing is better


def check(img, c):
    """Polish the model with court_auto.polish and return (points, check cost px)."""
    H, v = court_auto.polish(img, to_H(c))
    c2 = court_auto.to_img(H, COURT4)
    return (c2, v) if valid(c2) else (c, v)


def fit_window(mid, t0, n, start, ref_cost, one=False):
    """Returns (points, track cost, check cost, ok, how). ok = False: no
    trusted fit. how: "track", "detect", "weak" (a faint court: only the
    tracker found a model and no better model exists) or "lost"."""
    img = window_frame(mid, t0, n, one)
    # A low camera (E23b) sees much of the hall above the court. There the threshold of track_mask is
    # too high for the court lines (120 on the test video; 35 to 96 in the lab matches), so use the
    # mask of the detector.
    low = court_auto.hidden(to_H(start), SAMPLES, img.shape[1], img.shape[0]).any()
    mask = court_auto.line_mask(img) if low else track_mask(img)
    fine = make_cost(mask, start)
    best = None
    if fine(start.ravel()) < ref_cost * 1.15 + 0.2:           # no change: only a small refinement
        c, v = refine(fine, start)
        if valid(c):
            best = (c, v)
    if best is None:
        # the camera moved: wide search first (cap 45 px), then the fine cost
        wide = make_cost(mask, start, cap=45.0)
        for dx, dy in ((0, 0), (-30, 0), (30, 0), (0, -30), (0, 30)):
            s0 = start + np.array([dx, dy], np.float32)
            c1, _ = refine(wide, s0)
            c2, v2 = refine(make_cost(mask, c1, limit=25), c1)
            if valid(c2) and (best is None or v2 < best[1]):
                best = (c2, v2)
            if best is not None and best[1] < ref_cost * 1.3 + 0.5:
                break
    if best is not None:
        c, chk = check(img, best[0])
        if chk < TRUST:
            return c, best[1], chk, True, "track"
    # not trusted: find the court again, with no start
    H, score, info = court_auto.detect(img, prior=to_H(start))
    if H is not None and info["ok"]:
        c = court_auto.to_img(H, COURT4)
        return c, fine(c.ravel()), score, True, "detect"
    if best is not None and chk < LOOSE and (H is None or score > chk - 0.3 or court_auto.model_dist(to_H(c), H) < 10):
        return c, best[1], chk, True, "weak"
    if H is not None and court_auto.valid_H(H) and (best is None or score < chk):
        c = court_auto.to_img(H, COURT4)
        return c, fine(c.ravel()), score, False, "lost"          # the best guess, not trusted
    return (c if best is not None else start), (best[1] if best is not None else CAP), (chk if best is not None else CAP), False, "lost"


def montage(mid, track, n, k=8, one=False):
    """k frames spread over the match, each 480x270, with the model in red
    (yellow if the window is not ok)."""
    tiles = []
    for t in np.linspace(30, n - 30, k).astype(int):
        w = track[min(len(track) - 1, t // STEP)]
        im = window_frame(mid, w["t0"], n, one)
        v = court_auto.draw(im, np.array(w["H"]), (0, 0, 255) if w["ok"] else (0, 255, 255), 3)
        cv2.putText(v, f"{w['t0']}s c{w['cost']:.1f}", (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 2.2, (0, 255, 0), 5)
        tiles.append(v)
    cv2.imwrite(os.path.join(lab.ROOT, "court", f"{mid}_track.jpg"), court_auto.montage(tiles, 4, 480, 270),
                [cv2.IMWRITE_JPEG_QUALITY, 80])


def run(mid):
    t_start = time.time()
    out = lab.mdir(mid)
    n = lab.n_seconds(mid)
    has_gt = os.path.exists(os.path.join(out, "gt.json"))
    gt = lab.gt_points(mid) if has_gt else []
    t_mid = int(gt[len(gt) // 2][0]) if gt else n // 2
    one = False                       # E55: True = one picture for each window (a camera that moves)
    if os.path.exists(os.path.join(out, "court.json")):
        cj = json.load(open(os.path.join(out, "court.json")))
        H0, ref0 = np.array(cj["H"]), cj["chamfer_px"]
    else:
        # a video with no marks (the app): the middle can be a break, so try more times. First the median of
        # 9 pictures; if no median gives a court, one picture (E55: the camera moves, the median blurs the lines).
        t_gt, found = t_mid, False
        for one, n_frames in ((False, 9), (True, 1)):
            for frac in ((None,) if gt else (0.5, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8)):
                t_mid = t_gt if frac is None else int(n * frac)
                H0, _, info = court_auto.detect(court_auto.frame_at(mid, t_mid, n_frames=n_frames))
                if H0 is not None and info["ok"]:
                    found = True
                    break
            if found:
                break
        if not found:
            print(mid, "no court found", info, flush=True)
            return None
        ref0 = 2.0
    mid_w = t_mid // STEP
    c0 = court_auto.to_img(H0, COURT4)
    nwin = (n + STEP - 1) // STEP
    res = {}
    for order in (range(mid_w, nwin), range(mid_w - 1, -1, -1)):
        c, ref = c0.copy(), ref0
        for w in order:
            c2, v, chk, ok, how = fit_window(mid, w * STEP, n, c, ref, one)
            if ok:
                c, ref = c2, min(max(v, 1.0), 5.0)
            res[w] = (c2.copy(), chk, ok, how)
    track = []
    for w in range(nwin):
        c, v, ok, how = res[w]
        track.append({"t0": w * STEP, "t1": min(n, (w + 1) * STEP), "H": to_H(c).tolist(), "cost": float(v), "ok": bool(ok), "corners": c.tolist()})
        if one:
            track[-1]["onePicture"] = True
    json.dump(track, open(os.path.join(out, "court_track.json"), "w"))
    # camera moves: the model moves more than 8 px (mean over the model points in the frame)
    Hs = [np.array(t["H"]) for t in track]
    jumps = [(track[i + 1]["t0"], round(court_auto.model_dist(Hs[i], Hs[i + 1]), 1)) for i in range(nwin - 1)
             if track[i]["ok"] and track[i + 1]["ok"]]
    jumps = [j for j in jumps if j[1] > 8]
    if has_gt:
        montage(mid, track, n, one=one)
    hows = [res[w][3] for w in range(nwin)]
    print(mid, "windows", nwin, "not ok", sum(not t["ok"] for t in track),
          "| not ok at s", [t["t0"] for t in track if not t["ok"]][:20],
          "| detect used at s", [w * STEP for w in range(nwin) if hows[w] == "detect"][:20],
          "| weak at s", [w * STEP for w in range(nwin) if hows[w] == "weak"][:20],
          "| cost median %.2f max %.2f" % (np.median([t["cost"] for t in track]), max(t["cost"] for t in track)),
          "| jumps (s, px)", jumps[:12], "| %.0f s" % (time.time() - t_start), flush=True)
    return track


if __name__ == "__main__":
    for mid in (sys.argv[1:] or [m for m in lab.LABELED if os.path.exists(os.path.join(lab.mdir(m), "court.json"))]):
        run(mid)

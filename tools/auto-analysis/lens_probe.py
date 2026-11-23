"""E26: how much lens distortion do the court lines show?

For one time window of a video: the median of some 1 fps keyframes (the players go away), then for each court line
of the court model the real line in the picture (the brightest ridge within SEARCH px across the model line).
A radial model with one number k1 (center = picture center) is fitted with the plumb-line method: after the
correction, the points of each real line must be on a straight line.
Output: the straightness error of the lines before and after (px), k1, the shift at the picture corners (px), and
the distance of the model lines to the real lines (px).
Usage: python lens_probe.py <work or match folder> [window index] [out.jpg]
"""
import json, os, sys
import numpy as np
import cv2
from scipy.optimize import minimize_scalar
import court_auto

SEARCH, STEP_N, FRAMES = 14, 60, 15
W, H = 1920, 1080


def median_frame(folder, t0, t1):
    ts = np.linspace(t0 + 1, t1 - 1, FRAMES).astype(int)
    imgs = [cv2.imread(os.path.join(folder, "kf", f"{t + 1:05d}.jpg")) for t in ts]
    imgs = [i for i in imgs if i is not None]
    return np.median(np.stack(imgs), 0).astype(np.uint8)


def ridge_points(gray, a, b):
    """Points of the real line near the model line a-b (px): for STEP_N places along it, the brightest place across."""
    d = (b - a) / max(1e-6, np.linalg.norm(b - a))
    nrm = np.array([-d[1], d[0]])
    out = []
    for s in np.linspace(0.04, 0.96, STEP_N):
        c = a + s * (b - a)
        offs = np.arange(-SEARCH, SEARCH + 1)
        xs, ys = c[0] + offs * nrm[0], c[1] + offs * nrm[1]
        if xs.min() < 2 or ys.min() < 2 or xs.max() > W - 3 or ys.max() > H - 3:
            continue
        v = cv2.remap(gray, xs.astype(np.float32)[None], ys.astype(np.float32)[None], cv2.INTER_LINEAR)[0].astype(np.float32)
        v = np.convolve(v, np.ones(3) / 3, "same")
        k = int(v.argmax())
        # a line is a clear ridge: brighter than both ends of the search range, and not at the border
        if 2 <= k <= len(v) - 3 and v[k] - max(v[:3].mean(), v[-3:].mean()) > 18:
            w = v[k - 1:k + 2]
            den = w[0] - 2 * w[1] + w[2]
            sub = float(np.clip(0.5 * (w[0] - w[2]) / den, -1, 1)) if den < -1e-3 else 0.0
            out.append((c[0] + (offs[k] + sub) * nrm[0], c[1] + (offs[k] + sub) * nrm[1], offs[k] + sub))
    return np.array(out).reshape(-1, 3)


def undistort(p, k1):
    """Radial model: p_u = c + (p_d - c) * (1 + k1 * r^2), r in units of the half diagonal."""
    c = np.array([W / 2, H / 2])
    q = (p - c) / np.hypot(W / 2, H / 2)
    r2 = (q ** 2).sum(1, keepdims=True)
    return c + (p - c) * (1 + k1 * r2)


def straightness(lines, k1):
    """RMS distance (px) of the points of each line to the best straight line through them."""
    e = []
    for p in lines:
        u = undistort(p, k1)
        m = u.mean(0)
        _, s, vt = np.linalg.svd(u - m)
        e.append(((u - m) @ vt[1]) ** 2)
    return float(np.sqrt(np.concatenate(e).mean()))


def probe(folder, wi=None, out=None):
    track = [w for w in json.load(open(os.path.join(folder, "court_track.json"))) if w.get("ok", True)]
    w = track[len(track) // 2 if wi is None else wi]
    img = median_frame(folder, w["t0"], w["t1"])
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    Hm = np.array(w["H"])
    lines, offs = [], []
    for a, b in court_auto.SEGS:
        pa, pb = court_auto.to_img(Hm, [a, b]).astype(np.float64)
        r = ridge_points(gray, pa, pb)
        if len(r) >= 12:
            lines.append(r[:, :2])
            offs.append(r[:, 2])
            if out:
                for x, y, _ in r:
                    cv2.circle(img, (int(x), int(y)), 2, (0, 0, 255), -1)
        if out:
            cv2.line(img, tuple(np.int32(pa)), tuple(np.int32(pb)), (255, 200, 0), 1, cv2.LINE_AA)
    if not lines:
        return None
    best = minimize_scalar(lambda k: straightness(lines, k), bounds=(-0.3, 0.3), method="bounded")
    k1 = float(best.x)
    corner = float(np.hypot(W / 2, H / 2) * abs(k1))
    o = np.concatenate(offs)
    res = {"window": [w["t0"], w["t1"]], "lines": len(lines), "points": int(sum(len(l) for l in lines)),
           "straight_px_before": round(straightness(lines, 0.0), 2), "straight_px_after": round(float(best.fun), 2),
           "k1": round(k1, 4), "corner_shift_px": round(corner, 1),
           "model_to_line_px_rms": round(float(np.sqrt((o ** 2).mean())), 2), "model_to_line_px_p95": round(float(np.percentile(np.abs(o), 95)), 2)}
    if out:
        cv2.imwrite(out, img, [cv2.IMWRITE_JPEG_QUALITY, 85])
    return res


if __name__ == "__main__":
    a = sys.argv
    print(json.dumps(probe(a[1], int(a[2]) if len(a) > 2 and a[2] != "-" else None, a[3] if len(a) > 3 else None)))

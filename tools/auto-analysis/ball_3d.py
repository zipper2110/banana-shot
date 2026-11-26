"""3D ball flight from one camera.

1. camera(Hm): full camera matrix from the court homography. Assumptions:
   square pixels, principal point at the frame center, no lens distortion.
   The focal length comes from the two constraints on the first two
   columns of the homography (orthogonal, equal length).
2. fit_flight: a flight segment between two kinks is one ballistic path.
   Unknowns: position and velocity at the segment start (6 numbers).
   Start value: linear least squares with gravity only (each frame gives
   2 linear equations). Then a nonlinear fit with air drag.
   One camera is enough because gravity gives the scale.
Court axes: x right, y to the far side, z up, meters, origin at the net center.
"""
import os, sys
import numpy as np
import cv2
from scipy.optimize import least_squares
import lab
from ball_tracks import link, moving
from ball_events import select, kinks, court_H, Scale

G = 9.81
DRAG = 0.5 * 1.2 * 0.55 * (np.pi * 0.0335 ** 2) / 0.0577     # 1/m: deceleration = DRAG * v^2
W, HGT = 1920, 1080


def camera(Hm, w=W, h=HGT):
    """Returns P (3x4, court meters -> px of the 1920-wide frame), f (px), camera center (m)."""
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
    if t[2] < 0:                                   # the court center must be in front of the camera
        r1, r2, t = -r1, -r2, -t
    U, _, Vt = np.linalg.svd(np.stack([r1, r2, np.cross(r1, r2)], 1))
    R = U @ Vt
    C = -R.T @ t
    if C[2] < 0:                                   # camera below the ground: mirror solution
        R[:, 2] = -R[:, 2]
        C = -R.T @ t
    P = T @ np.diag([f, f, 1.0]) @ np.concatenate([R, t[:, None]], 1)
    return P, f, C


def project(P, X):
    x = (P @ np.concatenate([X, np.ones((len(X), 1))], 1).T).T
    return x[:, :2] / x[:, 2:3]


def simulate(p0, v0, ts, drag=DRAG, dt=1 / 60):
    """Positions at the times ts (sorted, s). Midpoint steps with gravity and drag."""
    out, p, v, t = [], np.array(p0, float), np.array(v0, float), 0.0
    gz = np.array([0, 0, G])
    for tt in ts:
        while t < tt - 1e-9:
            h = min(dt, tt - t)
            a1 = -drag * np.sqrt(v @ v) * v - gz
            vm = v + 0.5 * h * a1
            a2 = -drag * np.sqrt(vm @ vm) * vm - gz
            p = p + h * vm
            v = v + h * a2
            t += h
        out.append(p.copy())
    return np.array(out)


def landing(p0, v0, tmax=3.0):
    """Follows the flight to the ground. Returns (x, y, t) of the landing (nan if none in tmax)
    and the height at the net plane y = 0 (nan if the flight does not cross it before the landing)."""
    ts = np.arange(0, tmax, 1 / 60)
    q = simulate(p0, v0, ts)
    net_z = float("nan")
    for i in range(1, len(q)):
        if np.isnan(net_z) and q[i - 1, 1] * q[i, 1] <= 0 and q[i - 1, 1] != q[i, 1]:
            w = q[i - 1, 1] / (q[i - 1, 1] - q[i, 1])
            net_z = float(q[i - 1, 2] + w * (q[i, 2] - q[i - 1, 2]))
        if q[i, 2] <= 0 < q[i - 1, 2]:
            w = q[i - 1, 2] / (q[i - 1, 2] - q[i, 2])
            x, y = q[i - 1, :2] + w * (q[i, :2] - q[i - 1, :2])
            return float(x), float(y), float(ts[i - 1] + w / 60), net_z
    return float("nan"), float("nan"), float("nan"), net_z


def fit_flight(P, ts, xy, anchors=()):
    """ts: times from the segment start (s), xy: px. anchors: list of (t, x, y, z): known ball
    positions (a bounce on the ground, from the homography). Returns p0, v0, rms px."""
    rows, rhs = [], []
    for t, (x, y) in zip(ts, xy):
        for q, r in ((x, 0), (y, 1)):
            m = P[r] - q * P[2]                    # m . (X, 1) = 0
            rows.append(np.concatenate([m[:3], m[:3] * t]))
            rhs.append(-m[3] + m[2] * 0.5 * G * t * t)
    sol = np.linalg.lstsq(np.array(rows), np.array(rhs), rcond=None)[0]
    at = sorted(set([0.0] + [float(a[0]) for a in anchors if a[0] >= 0]))
    t_all = np.array(sorted(set(list(ts) + at)))
    i_obs = np.searchsorted(t_all, ts)

    def res(q, full=True):
        if anchors and min(a[0] for a in anchors) < 0:       # an anchor before the segment start: go back in time
            raise ValueError("anchor before the segment start")
        X = simulate(q[:3], q[3:], t_all)
        e = (project(P, X[i_obs]) - xy).ravel()
        if not full or not anchors:
            return e
        ea = [ANCHOR_W * (X[np.searchsorted(t_all, a[0])] - np.array(a[1:4])) for a in anchors]
        return np.concatenate([e] + ea)
    r = least_squares(res, sol, loss="soft_l1", f_scale=3.0, max_nfev=25)
    e = res(r.x, False).reshape(-1, 2)
    return r.x[:3], r.x[3:], float(np.sqrt((e ** 2).sum(1).mean()))


ANCHOR_W = 25.0        # px of residual for 1 m of distance to an anchor


def lin_rms(P, ts, xy):
    """Fit error (px) of the fast gravity-only fit."""
    rows, rhs = [], []
    for t, (x, y) in zip(ts, xy):
        for q, r in ((x, 0), (y, 1)):
            m = P[r] - q * P[2]
            rows.append(np.concatenate([m[:3], m[:3] * t]))
            rhs.append(-m[3] + m[2] * 0.5 * G * t * t)
    sol = np.linalg.lstsq(np.array(rows), np.array(rhs), rcond=None)[0]
    X = sol[:3] + np.outer(ts, sol[3:]) - np.outer(0.5 * G * ts ** 2, [0, 0, 1])
    return float(np.sqrt(((project(P, X) - xy) ** 2).sum(1).mean()))


def more_kinks(path, kk, P, sc, bad=5.0, rounds=2):
    """A segment with a bad fit has a kink that the threshold did not find. This adds the
    strongest weak kink (threshold KINK / 3) of each bad segment."""
    for _ in range(rounds):
        cuts = [-1e9] + [k[0] for k in kk] + [1e9]
        add = []
        for a, b in zip(cuts[:-1], cuts[1:]):
            seg = path[(path[:, 0] > a + 1) & (path[:, 0] < b - 1)]
            if len(seg) < 24:
                continue
            if lin_rms(P, (seg[:, 0] - seg[0, 0]) / 60.0, seg[:, 1:3].astype(np.float64)) < bad:
                continue
            k2 = kinks(seg, sc, thr=2.0)
            k2 = k2[(k2[:, 0] > seg[0, 0] + 5) & (k2[:, 0] < seg[-1, 0] - 5)] if len(k2) else k2
            if len(k2):
                add.append(k2[np.argmax(np.hypot(k2[:, 3], k2[:, 4]))])
        if not add:
            break
        kk = np.concatenate([kk.reshape(-1, 10), np.array(add)])
        kk = kk[np.argsort(kk[:, 0])]
    return kk


SERVE_Y = 11.7        # m from the net: contact point of a serve (a little inside the baseline)
# E127: the second pass of flights() (the bounce anchor at the end of a segment) also keeps the serve start anchor
# of serve_prior. Before, the fit with only the bounce anchor put the start of 11 far serves of the lab starts back at
# 18 to 32 m from the net (no serve event). SERVE_PRIOR_KEEP=0 gives the old fit.
SERVE_PRIOR_KEEP = os.environ.get("SERVE_PRIOR_KEEP", "1") == "1"


def serve_prior(P, seg, fl):
    """E10d. A serve that flies along the view direction has a weak depth: the free fit can put the
    start far behind the court. If the free fit is not plausible and the view ray of the first ball
    position passes the serve contact zone of the far end (plane y = SERVE_Y, height 1.8 to 3.3 m, |x| < 5 m),
    fit again with the start anchored there. Keeps the new fit only if it is a plausible serve."""
    sp = 3.6 * float(np.linalg.norm(fl["v0"]))
    p0 = fl["p0"]
    if abs(p0[1]) <= 16 and sp <= 240 and -0.5 <= p0[2] <= 3.6 and fl["rms"] <= 5 and not np.isnan(sp):
        return
    M, C = P[:, :3], -np.linalg.inv(P[:, :3]) @ P[:, 3]
    d = np.linalg.inv(M) @ np.array([seg[0, 1], seg[0, 2], 1.0])
    ts = (seg[:, 0] - seg[0, 0]) / 60.0
    best = None
    for yp in (SERVE_Y,):          # far end only. By eye (E10d): 6 of 6 new far candidates are real serves, 1 of 6 at the near end
        k = (yp - C[1]) / d[1]
        X = C + k * d
        if k <= 0 or not (1.8 <= X[2] <= 3.3) or abs(X[0]) > 5:
            continue
        try:
            q0, v0, rms = fit_flight(P, ts, seg[:, 1:3].astype(np.float64), [(0.0, X[0], yp, X[2])])
        except Exception:
            continue
        s2 = 3.6 * float(np.linalg.norm(v0))
        if rms < 5 and 45 < s2 < 240 and v0[1] * yp < 0 and abs(q0[1] - yp) < 1.5 and (best is None or rms < best[2]):
            best = (q0, v0, rms, (0.0, float(X[0]), yp, float(X[2])))
    if best:
        fl.update(p0=best[0], v0=best[1], rms=best[2], p1=simulate(best[0], best[1], [ts[-1]])[0], prior=1, prior_at=best[3])


def flights(path, kk, P, Hm=None, min_pts=8, free=None):
    """Splits the path at the kinks and fits each segment. Returns a list of dicts.
    With Hm (second pass): a kink that is a bounce gives an anchor on the ground for the
    segment after it (start) and a target for the segment before it (end).
    free: a list. The function adds a copy of the free fits (before the second pass) to it (E17d)."""
    cuts = [-1e9] + [k[0] for k in kk] + [1e9]
    segs = []
    for a, b in zip(cuts[:-1], cuts[1:]):
        seg = path[(path[:, 0] > a + 1) & (path[:, 0] < b - 1)]
        if len(seg) >= min_pts:
            segs.append((a, b, seg))
    out = []
    for a, b, seg in segs:
        ts = (seg[:, 0] - seg[0, 0]) / 60.0
        p0, v0, rms = fit_flight(P, ts, seg[:, 1:3].astype(np.float64))
        end = simulate(p0, v0, [ts[-1]])[0]
        out.append({"f0": float(seg[0, 0]), "f1": float(seg[-1, 0]), "n": len(seg), "p0": p0, "v0": v0, "p1": end, "rms": rms,
                    "cut0": a, "cut1": b, "anchored": 0})
    for (a, b, seg), fl in zip(segs, out):               # E10d: serve flights with a degenerate free fit
        serve_prior(P, seg, fl)
    if free is not None:
        import copy
        free.extend(copy.deepcopy(out))
    if Hm is None:
        return out
    kpos = {float(k[0]): cv2.perspectiveTransform(np.array([[k[1:3]]], np.float64), Hm)[0, 0] for k in kk}
    for i, ((a, b, seg), fl) in enumerate(zip(segs, out)):
        anchors = []
        nxt = out[i + 1] if i + 1 < len(out) and out[i + 1]["cut0"] == b else None
        # bounce at the end of this segment: this flight comes down near the ground, the next goes up, same direction
        if nxt is not None and b in kpos and fl["p1"][2] < 1.0 and nxt["v0"][2] > 0 and nxt["p0"][2] < 1.0                 and fl["v0"][1] * nxt["v0"][1] > 0:
            g = kpos[b]
            if abs(g[0]) < 12 and abs(g[1]) < 22:
                anchors.append(((b - seg[0, 0]) / 60.0, g[0], g[1], 0.0))
        if not anchors:
            continue
        if SERVE_PRIOR_KEEP and fl.get("prior_at"):
            anchors.insert(0, fl["prior_at"])
        ts = (seg[:, 0] - seg[0, 0]) / 60.0
        p0, v0, rms = fit_flight(P, ts, seg[:, 1:3].astype(np.float64), anchors)
        if rms < fl["rms"] * 1.5 + 1.0:
            fl.update(p0=p0, v0=v0, rms=rms, p1=simulate(p0, v0, [ts[-1]])[0], anchored=1,
                      bounce=[float(anchors[0][1]), float(anchors[0][2])])
    return out


EVENTS = True         # E17d: also the events (ball_bounce.py) and, for each flight, the fit that ends at its bounce event (evFit). The old fields do not change


def fit_window(mid, rows, w0, w1, t_ref, debug=None):
    """Blobs of one window (rows: frame, x, y, score, peak; frame 0 = w0) -> dict with flights,
    kinks, events, coverage. t_ref: time for the court model.
    Flight fields: t0, t1 (first and last detection), n, rms, p0, v0 (state at t0), p1 (position at t1),
    land, landT (the landing of the fit), netZ, anchored (1 = the fit has a soft anchor at a kink on the
    ground), bounce, prior. These fields are the same with and without EVENTS.
    With EVENTS (E17d): ev0, ev1 (index of the event at the start and at the end, or None), bounceEv (= ev1
    if that event is a bounce, else None), landSigma (m, 1-sigma error of the bounce event or of the
    extrapolated landing of evFit, or None), evFit (the fit of ball_bounce.py: p0, v0 at t0, rms, anchored:
    1 = the fit ends on the ground at the bounce event, 2 = the fit starts on the ground at a bounce event,
    0 = free; land, landT: the bounce event, or the landing of this fit).
    Event fields: t, kind ("bounce", "hit", "other", "end"), px (x, y of the ball center), court (x, y in m
    for a bounce and for a usable extrapolated landing, else None), src ("vertex" = the two paths meet,
    "kink" = the pixel of the kink sample, "fit" = extrapolation), conf (1-sigma error of court in m, or
    None), and for "end": tLast (the last detection of the flight)."""
    Hm = court_H(mid, t_ref)
    P, f, C = camera(Hm)
    path = select(moving(link(rows)))
    kk = more_kinks(path, kinks(path, Scale(Hm)), P, Scale(Hm))
    free = [] if EVENTS else None
    raw = flights(path, kk, P, Hm, free=free)
    evs = []
    if EVENTS:
        import ball_bounce
        evs = ball_bounce.events(path, kk, free, P, Hm, debug)
    fls = []
    for i, fl in enumerate(raw):
        lx, ly, lt, nz = landing(fl["p0"], fl["v0"])
        land_t = w0 + fl["f0"] / 60 + lt
        o = {"t0": w0 + fl["f0"] / 60, "t1": w0 + fl["f1"] / 60, "n": fl["n"], "rms": fl["rms"],
             "p0": np.asarray(fl["p0"]).tolist(), "v0": np.asarray(fl["v0"]).tolist(), "p1": np.asarray(fl["p1"]).tolist(),
             "land": [lx, ly], "landT": land_t, "netZ": nz,
             "anchored": fl["anchored"], "bounce": fl.get("bounce"), "prior": fl.get("prior", 0)}
        if EVENTS:
            fe = free[i]
            e1 = evs[fe["e1"]] if fe["e1"] is not None else None
            ex, ey, et, _ = landing(fe["p0"], fe["v0"])
            fit = {"p0": np.asarray(fe["p0"]).tolist(), "v0": np.asarray(fe["v0"]).tolist(), "rms": fe["rms"], "anchored": fe["anchored"],
                   "land": [ex, ey], "landT": w0 + fe["f0"] / 60 + et}
            if fe.get("bounce_f") is not None:
                fit.update(land=fe["bounce"], landT=w0 + fe["bounce_f"] / 60)
            o.update(ev0=fe["e0"], ev1=fe["e1"], bounceEv=fe["e1"] if e1 and e1["kind"] == "bounce" else None,
                     landSigma=e1["conf"] if e1 and e1["kind"] in ("end", "bounce") else None, evFit=fit)
        fls.append(o)
    cov = len(np.unique(path[:, 0])) / max(1.0, (w1 - w0) * 60)
    kc = cv2.perspectiveTransform(kk[:, 1:3].astype(np.float64).reshape(1, -1, 2), Hm)[0] if len(kk) else np.zeros((0, 2))
    kinks_out = [[w0 + k[0] / 60, float(k[1]), float(k[2]), float(c[0]), float(c[1]), int(k[9])] for k, c in zip(kk, kc)]   # t, px x, px y, court x, court y (true only for a ball on the ground), gap
    ev_out, ev_map = [], {}
    for i, e in enumerate(evs):
        if e["kind"] == "gap":                              # a long gap between two tracks is not an event
            continue
        ev_map[i] = len(ev_out)
        o = {"t": w0 + e["f"] / 60, "kind": e["kind"], "px": [round(e["px"][0], 1), round(e["px"][1], 1)], "court": e["court"],
             "src": e["src"], "conf": e["conf"]}
        if e["kind"] == "end":
            o["tLast"] = w0 + e["f_last"] / 60
        if e.get("by_landing"):
            o["byLanding"] = True                           # E40: a bounce from the landing of the flight before it
        ev_out.append(o)
    for o in fls:
        for key in ("ev0", "ev1", "bounceEv"):
            if o.get(key) is not None:
                o[key] = ev_map.get(o[key])
    last = path[-1] if len(path) else None
    return {"coverage": cov, "kinks": kinks_out, "flights": fls, "events": ev_out,
            "lastSeen": None if last is None else [w0 + float(last[0]) / 60, float(last[1]), float(last[2])]}


def run_points(mid, scale="0.35", out=None):
    """All marked points. Reads ballpts_s<scale>.npy, writes flights_s<scale>.json:
    for each point a list of flights. Times are in seconds of the video.
    scale may carry a weights tag, for example "0.35_e15b_a". out: another tag for the output file
    (flights_s<out>.json; BALL_TAG=<out> makes the other scripts read it)."""
    import json, time
    from ball_wasb import PRE, POST
    d = np.load(os.path.join(lab.mdir(mid), f"ballpts_s{scale}.npy"))
    pts, res, t0 = lab.gt_points(mid), [], time.time()
    for i, (a, b, _) in enumerate(pts):
        w0 = max(0.0, a - PRE)
        r = fit_window(mid, d[d[:, 0] == i, 1:], w0, b + POST, a)
        r.update(start=a, end=b)
        res.append(r)
    json.dump(res, open(os.path.join(lab.mdir(mid), f"flights_s{out or scale}.json"), "w"), default=float)
    print(f"{mid}: {len(pts)} points, flights {sum(len(o['flights']) for o in res)}, mean coverage "
          f"{np.mean([o['coverage'] for o in res]):.2f}, {time.time() - t0:.0f} s", flush=True)


def run_pre(mid, scale="0.35"):
    """The windows before the marked points (ball_wasb.py pre). Writes preflights_s<scale>.json."""
    import json, time
    from ball_wasb import pre_windows
    p = os.path.join(lab.mdir(mid), f"ballpre_s{scale}.npy")
    d = np.load(p)
    out, t0 = [], time.time()
    for i, (w0, w1) in enumerate(pre_windows(mid)):
        r = fit_window(mid, d[d[:, 0] == i, 1:], w0, w1, w1)
        r.update(w0=w0, w1=w1)
        out.append(r)
    json.dump(out, open(os.path.join(lab.mdir(mid), f"preflights_s{scale}.json"), "w"), default=float)
    print(f"{mid}: {len(out)} pre-windows, flights {sum(len(o['flights']) for o in out)}, {time.time() - t0:.0f} s", flush=True)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[2] in ("points", "pre"):
        (run_points if sys.argv[2] == "points" else run_pre)(sys.argv[1], *(sys.argv[3:5] if sys.argv[2] == "points" else sys.argv[3:4]))
        sys.exit()
    # test on one window file: python ball_3d.py <match> <start> <dur> <tag>
    mid, start, dur, tag = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]
    Hm = court_H(mid, start)
    P, f, C = camera(Hm)
    print(f"{mid}: focal {f:.0f} px, camera at x {C[0]:.1f} y {C[1]:.1f} z {C[2]:.2f} m")
    d = np.load(os.path.join(lab.mdir(mid), f"wasb_{int(start)}_{int(dur)}_{tag}.npy"))
    path = select(moving(link(d)))
    kk = more_kinks(path, kinks(path, Scale(Hm)), P, Scale(Hm))
    for fl in flights(path, kk, P, Hm):
        p0, v0, p1 = fl["p0"], fl["v0"], fl["p1"]
        print(f"t {start + fl['f0'] / 60:7.2f}-{start + fl['f1'] / 60:7.2f} n {fl['n']:3d} rms {fl['rms']:4.1f} px | start ({p0[0]:5.1f},{p0[1]:6.1f},{p0[2]:5.2f})"
              f" end ({p1[0]:5.1f},{p1[1]:6.1f},{p1[2]:5.2f}) | speed {np.linalg.norm(v0) * 3.6:5.0f} km/h vy {v0[1]:6.1f} {'bounce (%.1f, %.1f)' % tuple(fl['bounce']) if fl['anchored'] else ''}")

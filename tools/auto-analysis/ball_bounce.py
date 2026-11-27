"""E17d: the events of the ball path (bounce, hit, end) and flights that end at their bounce.

Facts that are true for each camera position:
  1. A bounce is on the boundary between two paths. It is not a point of one smooth path. So the bounce
     is the vertex of a broken line in the image: the path before and the path after meet there (vertex()).
     The time of the vertex is between two frames (sub-frame).
  2. At the bounce the ball is on the ground. So the pixel of the vertex gives the court position through
     the court homography (ground()). No 3D fit and no depth estimate is necessary for the position.
  3. A bounce keeps the horizontal direction of the ball, makes it slower, and turns the vertical velocity
     from down to up with a loss (restitution). A hit changes the horizontal direction. The test
     (fit_bounce()) fits the samples near the vertex with one physical bounce: the ball is on the ground at
     the vertex, and the speed ratio, the restitution and the turn stay inside wide physical limits. If the
     samples do not agree with the best physical bounce, the vertex is not a bounce. The limits are
     physical ranges, not values tuned to a camera.
  4. A flight that ends in the air (the track ends, no vertex) has no measured bounce. Its landing point
     is an extrapolation of the fit. landing_sigma() gives the 1-sigma error of that point from the fit
     covariance. A landing time before the last detection of the flight is not possible: such a landing is
     rejected.
Output of events(): the list of events and, for each flight, the references to its start and end event.
"""
import numpy as np
import cv2
from scipy.optimize import least_squares
import ball_3d as b3

R_BALL = 0.033          # m: the ball center is above the contact point
MAX_GAP_S = 0.6         # a gap between two tracks longer than this is not one vertex: the track ends and a new one starts (E32: before 0.25)
# Physical limits of a bounce (tennis ball on a court; wide, for all surfaces and spins):
E_MIN, E_MAX = 0.35, 1.05       # vertical restitution: speed up after / speed down before
K_MIN, K_MAX = 0.25, 1.2        # horizontal speed after / before
PHI_MAX = 0.6                   # rad: a bounce does not turn the horizontal direction of the ball by more than about 35 degrees
VZ_MIN = 0.3                    # m/s: the ball must come down before the bounce
HIT_N, HIT_V = 20, 8.0          # samples and m/s along the court of the flight after a vertex: such a flight in the opposite direction is a hit
LOCAL_S = 0.35                  # s: the bounce test uses the samples of this time before and after the vertex (the ball model has no spin: a long path does not fit)
X_MAX, Y_MAX = 12.0, 22.0         # m: a bounce event is inside this area around the court center (E32: 16 x 24 m gave false "out" decisions from balls of the next court)
NEIGHBOR_GAP = True
# E86: a sample far from the fitted bounce path is not this ball (another object or another track: 1000 px away at
# m1030 5196.3 s). One such sample made the plain rms of the bounce test 163 px for a fit of 2 px on the other 37
# samples. The bounce test fits again without the samples that are more than OUT_PX from the path, if there are not
# more than OUT_N of them on each side. More of them: the samples do not agree with a bounce, and the test stays.
OUT_PX, OUT_N = 30.0, 1
BOUNCE_OUTLIER = __import__("os").environ.get("BOUNCE_OUTLIER", "1") == "1"
END_BEFORE = 4.0      # frames of 1/60 s. E32 (before: 1): a track often ends at the bounce, and the time of a fit landing has an error of some frames
WIDE_GAP_S, WIDE_N = 0.25, 8      # E32: a vertex in a gap longer than WIDE_GAP_S needs WIDE_N samples on each side
# E32: the path of a ball with side spin curves sideways, and a lens bends a straight path near the picture edge.
# A fit with no side curve then fails by a small margin, with a perfect height part. So a seen vertex whose fit is
# worse than the limit, but not worse than SIDE_TRY times the limit, gets a second fit with a side acceleration
# within +-SIDE_ACC m/s^2 for each of the two paths. Each path needs SIDE_N samples.
SIDE_ACC, SIDE_TRY, SIDE_N = 8.0, 2.0, 6
RMS_SEEN, SEEN_F = 3.0, 3.0       # E32: the base limit for a vertex with samples not more than SEEN_F sample steps apart around it
V_MAX = 60.0                    # m/s (216 km/h). E35: a bounce fit with a higher speed before or after the bounce is not a bounce: the ball is not on the ground there
# E40: a vertex at the landing of the flight before it is a bounce. The fit of a long flight gives the time and the
# place where the ball comes to the ground. If the path turns at that time and at that place, the ball is on the
# ground there. (A hit in the air is earlier than the landing, and its pixel is not the landing place on the ground.)
# This finds a bounce that the bounce test cannot find: the hit after the bounce made no vertex, and the long flight
# after the vertex goes back (the "sure hit" rule), or the samples after the vertex do not fit one bounce.
LAND_AGREE = __import__("os").environ.get("LAND_AGREE", "1") == "1"
LAND_F, LAND_M, LAND_BACK_S = 4.0, 1.5, 0.5     # frames of 1/60 s, m on the court, and the longest time from the end of the flight to the vertex
# E183/E184 (S5): the ground test. A bounce vertex needs the ball on the ground: the free fit of the flight that ends
# at the vertex (the flight before the second pass) must not be more than GROUND_UP m above the ground there
# (z of the ball center - R_BALL). A fit below the ground is a fit error, not evidence against a bounce (E183: the
# two-side test lost right bounces with z below the ground). 0.51 m = the maximum of the 38 matched lab bounce
# labels (bounce_labels.json); a vertex with no incoming fit is not tested. 0 = off (the default).
GROUND_UP = float(__import__("os").environ.get("BOUNCE_GROUND_UP", "0") or 0)
RMS_BASE, RMS_BALL = 2.0, 0.6   # the bounce fit is accepted if rms < RMS_BASE px + RMS_BALL * radius of the ball image (px)


def sim(p, v, ts, drag=b3.DRAG, h=1 / 60, side=0.0):
    """Positions and velocities at the times ts (any order, also negative) of a ball with the state
    (p, v) at t = 0. Midpoint steps with gravity and drag, forward and backward in time.
    side (m/s^2): a constant horizontal acceleration at a right angle to the horizontal velocity at t = 0 (E32)."""
    ts = np.asarray(ts, float)
    P, V = np.zeros((len(ts), 3)), np.zeros((len(ts), 3))
    gz = np.array([0, 0, b3.G])
    if side:
        hv = max(1e-6, float(np.hypot(v[0], v[1])))
        gz = gz - side * np.array([-v[1], v[0], 0.0]) / hv
    for sign in (1.0, -1.0):
        idx = [i for i in np.argsort(sign * ts) if sign * ts[i] >= 0 and (sign > 0 or ts[i] < 0)]
        q, w, t = np.array(p, float), np.array(v, float), 0.0
        for i in idx:
            tt = sign * ts[i]
            while t < tt - 1e-9:
                s = sign * min(h, tt - t)
                a1 = -drag * np.sqrt(w @ w) * w - gz
                wm = w + 0.5 * s * a1
                a2 = -drag * np.sqrt(wm @ wm) * wm - gz
                q = q + s * wm
                w = w + s * a2
                t += abs(s)
            P[i], V[i] = q, w
    return P, V


def ground(Hm, P, px):
    """Court position (x, y in m) of a ball that touches the ground and has its center at the pixel px.
    The center is R_BALL above the contact point: one correction step with the camera matrix."""
    g = cv2.perspectiveTransform(np.array([[px]], np.float64), Hm)[0, 0]
    up = b3.project(P, np.array([[g[0], g[1], R_BALL]]))[0] - b3.project(P, np.array([[g[0], g[1], 0.0]]))[0]
    return cv2.perspectiveTransform(np.array([[np.asarray(px, float) - up]], np.float64), Hm)[0, 0]


def ground_jac(Hm, px, d=1.0):
    """Meters of court for 1 px at the pixel px: the larger singular value of the Jacobian."""
    f = lambda q: cv2.perspectiveTransform(np.array([[q]], np.float64), Hm)[0, 0]
    px = np.asarray(px, float)
    J = np.stack([(f(px + [d, 0]) - f(px - [d, 0])) / (2 * d), (f(px + [0, d]) - f(px - [0, d])) / (2 * d)], 1)
    return float(np.linalg.svd(J, compute_uv=False)[0])


def vertex(t, xy, lo, hi, step=1 / 600):
    """Broken line through the samples (t s, xy px): x(t) = c + vA (t - T) before T, c + vB (t - T) after T.
    Scans T from lo to hi. Returns (T, c, vA, vB, rms px, sigma of c in px) or None.
    A sample at T belongs to the two lines. Each line needs 2 samples."""
    t, xy = np.asarray(t, float), np.asarray(xy, float)
    best = None
    for T in np.arange(lo, hi + 1e-9, step):
        d = t - T
        if (d < -1e-6).sum() < 2 or (d > 1e-6).sum() < 2:
            continue
        A = np.stack([np.ones_like(d), np.minimum(d, 0), np.maximum(d, 0)], 1)
        sol = np.linalg.lstsq(A, xy, rcond=None)[0]
        sse = float(((A @ sol - xy) ** 2).sum())
        if best is None or sse < best[0]:
            best = (sse, T, sol, A)
    if best is None:
        return None
    sse, T, sol, A = best
    dof = max(1, 2 * len(t) - 7)
    s2 = max(sse / dof, 0.5 ** 2)                                        # px^2 for each coordinate; floor: the detector noise
    sc = float(np.sqrt(2 * s2 * np.linalg.inv(A.T @ A)[0, 0]))
    return T, sol[0], sol[1], sol[2], float(np.sqrt(sse / len(t))), sc


def fit_anchored(P, ts, xy, Xa):
    """A flight that passes the known point Xa (m) at t = 0. ts: sample times relative to the anchor
    (negative = before). Unknown: the velocity at the anchor. Returns v, rms px."""
    ts, xy = np.asarray(ts, float), np.asarray(xy, float)
    rows, rhs = [], []
    for t, (x, y) in zip(ts, xy):
        for q, r in ((x, 0), (y, 1)):
            m = P[r] - q * P[2]
            rows.append(m[:3] * t)
            rhs.append(-m[3] - m[:3] @ Xa + m[2] * 0.5 * b3.G * t * t)
    v0 = np.linalg.lstsq(np.array(rows), np.array(rhs), rcond=None)[0]
    res = lambda v: (b3.project(P, sim(Xa, v, ts)[0]) - xy).ravel()
    if not np.isfinite(res(v0)).all():                # the samples do not fit a flight through the anchor: no fit
        return v0, float("inf")
    r = least_squares(res, v0, loss="soft_l1", f_scale=3.0, max_nfev=20)
    e = res(r.x).reshape(-1, 2)
    rms = float(np.sqrt((e ** 2).sum(1).mean()))
    return r.x, rms if np.isfinite(rms) else float("inf")


def v_after(v_in, k, e, phi):
    """Velocity after a bounce: the horizontal part of v_in turned by phi and scaled by k, the vertical part reversed and scaled by e."""
    c, s = np.cos(phi), np.sin(phi)
    return np.array([k * (c * v_in[0] - s * v_in[1]), k * (s * v_in[0] + c * v_in[1]), -e * v_in[2]])


def fit_bounce(P, ta, xa, tb, xb, Xa, side=0.0):
    """fit_bounce_once, then (E86, BOUNCE_OUTLIER) once more without the samples far from the fitted path, if each side
    has OUT_N or fewer of them and keeps 2 samples (6 in all). Same result format."""
    out = fit_bounce_once(P, ta, xa, tb, xb, Xa, side)
    if out is None or not BOUNCE_OUTLIER:
        return out
    ta, xa, tb, xb = (np.asarray(x, float) for x in (ta, xa, tb, xb))
    pa = b3.project(P, sim(Xa, out[0], ta)[0])
    pb = b3.project(P, sim(Xa, out[1], tb)[0])
    ka, kb = np.hypot(*(pa - xa).T) <= OUT_PX, np.hypot(*(pb - xb).T) <= OUT_PX
    na, nb = int((~ka).sum()), int((~kb).sum())
    if na + nb == 0 or na > OUT_N or nb > OUT_N or ka.sum() < 2 or kb.sum() < 2 or ka.sum() + kb.sum() < 6:
        return out
    again = fit_bounce_once(P, ta[ka], xa[ka], tb[kb], xb[kb], Xa, side)
    return again if again is not None and again[5] < out[5] else out


def fit_bounce_once(P, ta, xa, tb, xb, Xa, side=0.0):
    """Is there a physical bounce at the point Xa that agrees with the samples? One fit of the path before
    (ta < 0, xa) and the path after (tb > 0, xb) with the ball on the ground at t = 0.
    Unknowns: the velocity before (3), the horizontal speed ratio k, the restitution e, the turn phi. The
    unknowns stay inside the physical limits. With side > 0 (E32): 2 more unknowns, the side acceleration of the
    path before and of the path after, each within +-side (spin, and the lens distortion at the picture edge). So the result is the best physical bounce, and its rms tells
    if the samples agree with it. Returns (v_in, v_out, k, e, phi, rms px) or None."""
    try:
        vi, _ = fit_anchored(P, ta, xa, Xa)
        vo, _ = fit_anchored(P, tb, xb, Xa)
    except ValueError:
        return None
    if not (np.isfinite(vi).all() and np.isfinite(vo).all()):
        return None
    hi = max(1e-3, float(np.hypot(vi[0], vi[1])))
    q0 = np.array([vi[0], vi[1], min(vi[2], -VZ_MIN - 0.1), np.clip(np.hypot(vo[0], vo[1]) / hi, K_MIN + 0.01, K_MAX - 0.01),
                   np.clip(vo[2] / max(0.1, -vi[2]), E_MIN + 0.01, E_MAX - 0.01), 0.0])
    lo = [-80, -80, -40, K_MIN, E_MIN, -PHI_MAX]
    up = [80, 80, -VZ_MIN, K_MAX, E_MAX, PHI_MAX]
    q0[:2] = np.clip(q0[:2], -79, 79); q0[2] = max(q0[2], -39)
    xy = np.concatenate([xa, xb])
    if side:
        q0, lo, up = np.concatenate([q0, [0.0, 0.0]]), lo + [-side, -side], up + [side, side]

    def res(q):
        pa = b3.project(P, sim(Xa, q[:3], ta, side=q[6] if side else 0.0)[0])
        pb = b3.project(P, sim(Xa, v_after(q[:3], q[3], q[4], q[5]), tb, side=q[7] if side else 0.0)[0])
        r = (np.concatenate([pa, pb]) - xy).ravel()
        return np.where(np.isfinite(r), r, 1e3)
    try:
        r = least_squares(res, q0, bounds=(lo, up), loss="soft_l1", f_scale=3.0, max_nfev=40 if side else 30)
    except ValueError:
        return None
    e = res(r.x).reshape(-1, 2)
    q = r.x
    return q[:3], v_after(q[:3], q[3], q[4], q[5]), float(q[3]), float(q[4]), float(q[5]), float(np.sqrt((e ** 2).sum(1).mean()))


def landing_sigma(P, ts, xy, p0, v0, rms):
    """1-sigma error (m, the long axis) of the landing point of a free fit, from the fit covariance.
    Returns (sigma m, sigma of the landing time s). nan if the flight does not come down."""
    q0 = np.concatenate([p0, v0])
    f = lambda q: (b3.project(P, b3.simulate(q[:3], q[3:], ts)) - xy).ravel()
    L = lambda q: np.array(b3.landing(q[:3], q[3:])[:3])
    l0 = L(q0)
    if np.isnan(l0[0]):
        return float("nan"), float("nan")
    r0, J, G = f(q0), np.zeros((2 * len(ts), 6)), np.zeros((3, 6))
    for i in range(6):
        d = np.zeros(6); d[i] = 1e-3
        J[:, i] = (f(q0 + d) - r0) / 1e-3
        G[:, i] = (L(q0 + d) - l0) / 1e-3
    try:
        cov = max(rms, 1.0) ** 2 * np.linalg.inv(J.T @ J)
    except np.linalg.LinAlgError:
        return float("inf"), float("inf")
    c = G @ cov @ G.T
    if not np.isfinite(c).all():
        return float("inf"), float("inf")
    return float(np.sqrt(max(0.0, np.linalg.eigvalsh(c[:2, :2])[-1]))), float(np.sqrt(max(0.0, c[2, 2])))


def events(path, kk, fls, P, Hm, debug=None):
    """path: rows frame, x, y, peak, track id. kk: kinks (ball_events.kinks). fls: the free fits of
    ball_3d.flights (with cut0, cut1). Changes fls in place (anchored fits, land, bounce, e0, e1) and
    returns the list of events. Times of the events are in frames of the window (float)."""
    f = path[:, 0].astype(np.float64)
    xy = path[:, 1:3].astype(np.float64)
    tid = path[:, 4]
    step = float(np.median(np.diff(f))) if len(f) > 2 else 1.0           # 1 = a sample each frame, 2 = each second frame
    step = min(max(step, 1.0), 2.0)
    kf = [float(k[0]) for k in kk]
    evs = []
    # 1. the vertex of each kink
    for j, k in enumerate(kk):
        fk, gap = float(k[0]), int(k[9])
        lo_f = kf[j - 1] if j else -1e9
        hi_f = kf[j + 1] if j + 1 < len(kf) else 1e9
        ia = np.nonzero((f < fk - 1e-6) & (f > lo_f + 0.5) & (f >= fk - 5 * step - 1e-6))[0][-4:]
        ib = np.nonzero((f > fk + 1e-6) & (f < hi_f - 0.5) & (f <= fk + 5 * step + 1e-6))[0][:4]
        if gap:
            ia = np.nonzero((f < fk) & (f > lo_f + 0.5))[0][-4:]
            ib = np.nonzero((f > fk) & (f < hi_f - 0.5))[0][:4]
            ia = ia[f[ia] >= f[ia[-1]] - 4 * step - 1e-6] if len(ia) else ia
            ib = ib[f[ib] <= f[ib[0]] + 4 * step + 1e-6] if len(ib) else ib
        im = np.nonzero(np.abs(f - fk) < 1e-6)[0]
        e = {"f": fk, "px": [float(k[1]), float(k[2])], "src": "kink", "gap": gap, "sig_px": 3.0, "long_gap": False}
        if gap and len(ia) and len(ib) and f[ib[0]] - f[ia[-1]] > MAX_GAP_S * 60:
            e["long_gap"] = True
        elif len(ia) >= 2 and len(ib) >= 2:
            idx = np.concatenate([ia, im, ib])
            lo, hi = f[ia[-1]] - step, f[ib[0]] + step
            v = vertex(f[idx] / 60.0, xy[idx], lo / 60.0, hi / 60.0)
            if v is not None:
                T, c, va, vb, rms, sc = v
                e.update(f=T * 60.0, px=[float(c[0]), float(c[1])], src="vertex", sig_px=sc, v_rms=rms,
                         ua=[float(va[0]), float(va[1])], ub=[float(vb[0]), float(vb[1])])
        evs.append(e)
    # 2. bounce or not: is there a physical bounce on the ground at the vertex that agrees with the samples near it?
    by_cut0 = {fl["cut0"]: fl for fl in fls}
    by_cut1 = {fl["cut1"]: fl for fl in fls}
    for j, e in enumerate(evs):
        e["kind"], e["court"], e["conf"] = "other", None, None
        if e["long_gap"]:
            e["kind"] = "gap"
            continue
        fe = e["f"]
        lo_f = evs[j - 1]["f"] if j else -1e9
        hi_f = evs[j + 1]["f"] if j + 1 < len(evs) else 1e9
        if NEIGHBOR_GAP:           # E32: the samples before a gap belong to the path before it, also if the vertex of the gap kink is earlier
            lo_f = min(lo_f, kf[j - 1]) if j and evs[j - 1]["gap"] else lo_f
            hi_f = max(hi_f, kf[j + 1]) if j + 1 < len(evs) and evs[j + 1]["gap"] else hi_f
        # E32: a vertex in a wide gap. The sample time counts from the last sample before the gap and from the first
        # sample after it (else the test has only the samples of the time that the gap leaves), and the test needs
        # more samples on each side.
        ea, eb = f[f < fe - 0.5], f[f > fe + 0.5]
        ea, eb = (ea[-1] if len(ea) else fe), (eb[0] if len(eb) else fe)
        wide = eb - ea > WIDE_GAP_S * 60
        if not wide:
            ea = eb = fe
        sa = np.nonzero((f > max(lo_f + 0.5, ea - LOCAL_S * 60)) & (f < fe - 0.5))[0]
        sb = np.nonzero((f > fe + 0.5) & (f < min(hi_f - 0.5, eb + LOCAL_S * 60)))[0]
        g = ground(Hm, P, e["px"])
        fa, fb = by_cut1.get(kf[j]), by_cut0.get(kf[j])
        e["nA"], e["nB"] = len(sa), len(sb)
        # A long and fast flight in the opposite direction along the court can only come from a racket.
        sure_hit = bool(fa and fb and fa["v0"][1] * fb["v0"][1] < 0 and fb["n"] >= HIT_N and abs(fb["v0"][1]) > HIT_V and fb["rms"] < 5)
        if not sure_hit and np.isfinite(g).all() and abs(g[0]) < X_MAX and abs(g[1]) < Y_MAX and len(sa) >= 2 and len(sb) >= 2 and len(sa) + len(sb) >= 6                 and not (wide and min(len(sa), len(sb)) < WIDE_N):
            Xa = np.array([g[0], g[1], R_BALL])
            r_px = float(np.linalg.norm(b3.project(P, np.array([[g[0], g[1], 2 * R_BALL]]))[0] - b3.project(P, np.array([[g[0], g[1], R_BALL]]))[0]))
            fit = fit_bounce(P, (f[sa] - fe) / 60.0, xy[sa], (f[sb] - fe) / 60.0, xy[sb], Xa)
            if fit is not None:
                seen = f[sb[0]] - f[sa[-1]] <= SEEN_F * step + 1e-6        # the turn itself has samples: no gap at the vertex
                lim = (RMS_SEEN if seen else RMS_BASE) + RMS_BALL * r_px
                if SIDE_ACC and seen and lim <= fit[5] < SIDE_TRY * lim and len(sa) >= SIDE_N and len(sb) >= SIDE_N:
                    fit2 = fit_bounce(P, (f[sa] - fe) / 60.0, xy[sa], (f[sb] - fe) / 60.0, xy[sb], Xa, SIDE_ACC)
                    if fit2 is not None and fit2[5] < fit[5]:
                        fit, e["side"] = fit2, True
                v_in, v_out, kr, er, phi, rms = fit
                e.update(v_in=v_in, v_out=v_out, k=kr, e=er, phi=phi, rms=rms, lim=lim)
                if rms < lim and max(float(np.linalg.norm(v_in)), float(np.linalg.norm(v_out))) < V_MAX:
                    e["kind"] = "bounce"
                    e["court"] = [float(g[0]), float(g[1])]
                    e["conf"] = float(max(0.03, e["sig_px"] * ground_jac(Hm, e["px"])))
        if LAND_AGREE and e["kind"] == "other" and e["src"] == "vertex" and np.isfinite(g).all() and abs(g[0]) < X_MAX and abs(g[1]) < Y_MAX:
            for fl in fls:
                if fl["f1"] < fe + 1 and fe - fl["f1"] <= LAND_BACK_S * 60 and fl["n"] >= HIT_N and fl["rms"] < 5:
                    lx, ly, lt, _ = b3.landing(fl["p0"], fl["v0"])
                    if np.isfinite(lt) and abs(fl["f0"] + lt * 60.0 - fe) <= LAND_F and np.hypot(lx - g[0], ly - g[1]) <= LAND_M:
                        e["kind"], e["court"], e["by_landing"] = "bounce", [float(g[0]), float(g[1])], True
                        e["conf"] = float(max(0.03, e["sig_px"] * ground_jac(Hm, e["px"])))
                        break
        if GROUND_UP > 0 and e["kind"] == "bounce" and fa is not None:
            e["z_in"] = float(sim(np.asarray(fa["p0"], float), np.asarray(fa["v0"], float), [(fe - fa["f0"]) / 60.0])[0][0][2]) - R_BALL
            if e["z_in"] > GROUND_UP:                                # the ball is in the air at the vertex: not a bounce
                e["kind"], e["court"], e["conf"], e["not_ground"] = "other", None, None, True
                e.pop("by_landing", None)
        if e["kind"] == "other" and fa and fb and fa["v0"][1] * fb["v0"][1] < 0:
            e["kind"] = "hit"                                           # the ball changes its direction along the court
    # 3. flights: anchored at the bounce; an end event for a flight with no event at its end
    out = list(evs)
    idx_of = {kf[j]: j for j in range(len(evs))}
    for fl in fls:
        j0, j1 = idx_of.get(fl["cut0"]), idx_of.get(fl["cut1"])
        fl["e0"] = j0 if j0 is not None and evs[j0]["kind"] != "gap" else None
        fl["e1"] = j1 if j1 is not None and evs[j1]["kind"] != "gap" else None
        seg = path[(f > fl["cut0"] + 1) & (f < fl["cut1"] - 1)]
        sf, sxy = seg[:, 0].astype(np.float64), seg[:, 1:3].astype(np.float64)
        e1 = evs[j1] if fl["e1"] is not None else None
        e0 = evs[j0] if fl["e0"] is not None else None
        if e1 is not None and e1["kind"] == "bounce":
            m = sf < e1["f"] - 0.5
            Xa = np.array([e1["court"][0], e1["court"][1], R_BALL])
            fl.update(bounce=[float(Xa[0]), float(Xa[1])], bounce_f=e1["f"])      # the end of the flight is the bounce event
            if m.sum() >= 3:
                v, rms = fit_anchored(P, (sf[m] - e1["f"]) / 60.0, sxy[m], Xa)
                if rms < fl["rms"] * 1.5 + 1.0:                                  # the same test as the old second pass
                    (p0,), (v0,) = sim(Xa, v, [(fl["f0"] - e1["f"]) / 60.0])
                    fl.update(p0=p0, v0=v0, rms=rms, p1=sim(Xa, v, [(fl["f1"] - e1["f"]) / 60.0])[0][0], anchored=1)
        elif e0 is not None and e0["kind"] == "bounce":
            m = sf > e0["f"] + 0.5
            Xa = np.array([e0["court"][0], e0["court"][1], R_BALL])
            if m.sum() >= 3:
                v, rms = fit_anchored(P, (sf[m] - e0["f"]) / 60.0, sxy[m], Xa)
                if rms < fl["rms"] * 1.5 + 1.0:
                    (p0,), (v0,) = sim(Xa, v, [(fl["f0"] - e0["f"]) / 60.0])
                    fl.update(p0=p0, v0=v0, rms=rms, p1=sim(Xa, v, [(fl["f1"] - e0["f"]) / 60.0])[0][0], anchored=2)
        if fl["e1"] is None:                                            # the track ends in the air
            lx, ly, lt, _ = b3.landing(fl["p0"], fl["v0"])
            ts = (sf - sf[0]) / 60.0
            sg, sg_t = landing_sigma(P, ts, sxy, fl["p0"], fl["v0"], fl["rms"]) if fl["anchored"] == 0 else (float("nan"), float("nan"))
            if fl["anchored"] == 2 and not np.isnan(lx):                # the start is exact: the error grows with the path
                sg = 0.15 * float(np.hypot(lx - fl["p0"][0], ly - fl["p0"][1]))
            bad = np.isnan(lx) or fl["f0"] + lt * 60.0 < fl["f1"] - END_BEFORE   # the fit comes down before the last detection
            fe = fl["f0"] + lt * 60.0 if not np.isnan(lt) else fl["f1"]
            px = b3.project(P, np.array([[lx, ly, R_BALL]]))[0] if not np.isnan(lx) else sxy[-1]
            out.append({"f": float(fe), "kind": "end", "px": [float(px[0]), float(px[1])], "src": "fit",
                        "court": None if bad else [float(lx), float(ly)], "conf": None if bad or not np.isfinite(sg) else float(sg),
                        "f_last": float(fl["f1"])})
            fl["e1"] = len(out) - 1
    if debug is not None:
        debug.extend(evs)
    return out

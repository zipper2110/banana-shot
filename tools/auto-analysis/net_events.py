"""E16f: net events from the 2D ball track and the court model.

A net event = the ball touches the net. Kind "net": the ball stays on the side of the hitter.
Kind "cord": the ball touches the top of the net and goes over (a let on a serve).

Principle. The camera matrix P (ball_3d.camera) gives, for each ball pixel, the point where the view
ray goes through the net plane (court y = 0): xN (m across) and zN (m above the ground). No depth
is necessary for this. "over" = zN minus the height of the net cord at xN (0.914 m at the center,
1.07 m at the posts). The net band in the image is 0 <= zN <= cord height.
  - A pixel below the net base line (zN < 0) is always on the near side of the net.
  - A ball that goes over the net is at "over" > 0 at the moment when it is in the net plane.
  - A ball of the near player that touches the net comes from zN < 0, goes up into the band, and
    stops there. A ball of the far player that touches the net comes from above the cord (or
    through the net mesh), stops in the band, and is never seen below the base line after that.
The detector looks at each end and each sudden direction change (kink) of a ball track in the
band, and then at what the ball does after it (see "touch" and "judge").

API:    events = net_events(rows, Hm, flights=(), kinks=())
        rows: blobs (t, x, y, score, peak), t in s of the video, px of the 1920-wide frame
        Hm: court homography px -> m of the time range (ball_events.court_H)
        flights: the fitted flights of the time range (ball_3d.fit_window format), optional:
                 they give the direction when the 2D track does not
        returns a list of {"t", "kind", "px", "toFar", "conf", "src", "dirConf", "dirSrc"} (see net_events())
        hint = winner_hint(events, t_end)     the net event that ends a rally, or None (see winner_hint())
Script: python net_events.py <match id or work folder> ...   writes <folder>/net_events.json
        (match id: m1015, m1020, m1021, m1030, m0913; work folder: a folder of the app with the cached files)
"""
import json, os, sys
import numpy as np
import ball_3d
from ball_tracks import link

NET_POST_X, NET_POST_Z, NET_MID_Z = 6.40, 1.07, 0.914      # m: the posts are 0.914 m outside the doubles lines
BALL_R = 0.033
# Tolerances (m in the net plane). The cord line of the model is not exact: the court model has an error of
# some px (1 px is about 1 cm at the net), the net of a club court is not always at the rule height, and the
# ball center is detected with about 1 to 2 px. E16f measured the model cord 3 to 8 cm above the real cord.
TOL_TOP, TOL_BASE = 0.12, 0.10


def cord_z(x):
    return NET_MID_Z + (NET_POST_Z - NET_MID_Z) * np.minimum(np.abs(x), NET_POST_X) / NET_POST_X


class NetPlane:
    """Maps pixels to the net plane (court y = 0)."""

    def __init__(self, Hm):
        self.P, self.f, self.C = ball_3d.camera(Hm)
        self.G = np.linalg.inv(self.P[:, [0, 2, 3]])            # px -> (x, z, 1) on the plane y = 0

    def xz(self, xy):
        xy = np.asarray(xy, np.float64).reshape(-1, 2)
        q = (self.G @ np.column_stack([xy, np.ones(len(xy))]).T).T
        return q[:, 0] / q[:, 2], q[:, 1] / q[:, 2]

    def px(self, x, z):
        return ball_3d.project(self.P, np.array([[x, 0.0, z]]))[0]


def tracks(rows, max_gap=0.085):
    """Links the blobs into tracks. Returns a list of arrays (t, x, y, peak), 3 points or more.
    The link runs on a frame count of 1/60 s. ball_tracks.link stops a track after 4 frames with
    no blob."""
    if len(rows) == 0:
        return []
    f = np.round((rows[:, 0] - rows[0, 0]) * 60.0)
    d = np.column_stack([f, rows[:, 1:5]])
    out = []
    for tr in link(d):
        if len(tr) >= 3:
            out.append(np.column_stack([rows[0, 0] + tr[:, 0] / 60.0, tr[:, 1:4]]))
    out.sort(key=lambda a: a[0, 0])
    return out


def _fit(t, v, deg):
    return np.polyfit(t, v, min(deg, len(t) - 1))


def touch_points(tr, npl, win=0.12, min_dv=4.0):
    """Candidate touch points of one track: the track end, and each kink (a sudden change of the
    velocity in the net plane of min_dv m/s or more; gravity alone gives about 1 m/s in 0.1 s).
    Returns a list of (index of the last point before the change, dv)."""
    t = tr[:, 0]
    xn, zn = npl.xz(tr[:, 1:3])
    out, score = [], np.zeros(len(tr))
    for i in range(2, len(tr) - 2):
        b = np.nonzero((t <= t[i] + 1e-6) & (t >= t[i] - win))[0]
        a = np.nonzero((t >= t[i] - 1e-6) & (t <= t[i] + win))[0]
        if len(b) < 3 or len(a) < 3:
            continue
        vb = np.array([np.polyfit(t[b] - t[i], xn[b], 1)[0], np.polyfit(t[b] - t[i], zn[b], 1)[0]])
        va = np.array([np.polyfit(t[a] - t[i], xn[a], 1)[0], np.polyfit(t[a] - t[i], zn[a], 1)[0]])
        score[i] = np.hypot(*(va - vb))
    for i in range(2, len(tr) - 2):
        lo, hi = max(0, i - 3), min(len(tr), i + 4)
        if score[i] >= min_dv and score[i] == score[lo:hi].max():
            out.append((i, float(score[i])))
    out.append((len(tr) - 1, 0.0))
    return out


def features(tr, i, dv, rows, npl, flights=()):
    """Quantities of a candidate touch at point i of the track tr. None if it is no candidate."""
    t, xy = tr[:, 0], tr[:, 1:3]
    xn, zn = npl.xz(xy)
    over = zn - cord_z(xn)
    T, X, Z = t[i], xn[i], zn[i]
    # the approach: the points of the track in the 0.25 s before the touch
    a = np.nonzero((t <= T + 1e-6) & (t >= T - 0.25))[0]
    if len(a) < 4:
        return None
    ta = t[a] - T
    vx, vz = np.polyfit(ta, xn[a], 1)[0], np.polyfit(ta, zn[a], 1)[0]
    speed = float(np.hypot(vx, vz))
    # all the track before the touch, back to the last kink-free start (at most 1.5 s)
    b = np.nonzero((t <= T + 1e-6) & (t >= T - 1.5))[0]
    q = {"t": float(T), "px": [float(xy[i, 0]), float(xy[i, 1])], "xN": float(X), "zN": float(Z), "over": float(over[i]),
         "dv": dv, "end": bool(i == len(tr) - 1), "n_app": int(len(b)), "speed": speed, "vx": float(vx), "vz": float(vz),
         "z_start": float(zn[b[0]]), "over_start": float(over[b[0]]), "over_max": float(over[b].max()), "z_min": float(zn[b].min()),
         "t_app": float(T - t[b[0]])}
    # the continued path: the approach, extended as a parabola in the image
    cx, cy = _fit(ta, xy[a, 0], 1), _fit(ta, xy[a, 1], 2 if len(a) >= 6 else 1)
    later = rows[(rows[:, 0] > T + 0.02) & (rows[:, 0] <= T + 1.5)]
    ppm = float(np.hypot(*(npl.px(X + 0.5, Z) - npl.px(X - 0.5, Z))))     # px for 1 m in the net plane
    q.update(n_cont=0, n_stay=0, n_stay_soon=0, n_below=0, n_above=0, n_later=int(len(later)), stay_t0=None, stay_z_min=None,
             stay_t_min=None, dt=float(np.median(np.diff(t[a]))))
    if len(later):
        dt = later[:, 0] - T
        lx, lz = npl.xz(later[:, 1:3])
        lo = lz - cord_z(lx)
        ex, ey = np.polyval(cx, dt), np.polyval(cy, dt)
        dist = np.hypot(later[:, 1] - ex, later[:, 2] - ey) / ppm
        moved = np.hypot(later[:, 1] - xy[i, 0], later[:, 2] - xy[i, 1]) / ppm
        near_x = np.abs(lx - X) < 1.0 + 1.0 * dt
        # the ball continues on the path of the approach
        cont = (dt <= 0.5) & (dist < 0.35 + 0.25 * speed * dt) & (moved > 0.35)
        # the ball is at the net after the touch: in the band, not higher than the touch
        st = near_x & (lz > -0.35) & (lz < Z + 0.20) & ~cont
        # the ball is above the cord soon after (it went on, or a player hit it)
        ab = (lo > 0.30) & (np.abs(lx - X) < 1.5 + 3.0 * dt) & (dt <= 0.5)
        # the ball is low on the near side soon after (it passed the net, or a near player handles it)
        be = (lz < -0.35) & (np.abs(lx - X) < 1.5 + 3.0 * dt) & (dt <= 0.6)
        q.update(n_cont=int(cont.sum()), n_stay=int(st.sum()), n_stay_soon=int((st & (dt <= 0.6)).sum()), n_below=int(be.sum()), n_above=int(ab.sum()))
        if st.any():
            k = int(np.argmin(lz[st]))
            q.update(stay_t0=float(dt[st][0]), stay_z_min=float(lz[st][k]), stay_t_min=float(dt[st][k]))
    # the fitted flight of the approach, if there is one, and the last reliable flight before it
    fl = [f for f in flights if f["t0"] - 0.03 <= T <= f["t1"] + 0.06 and f["t1"] - f["t0"] > 0.05]
    q.update(fit_dir=None, fit_ok=False, prev_dir=None)
    if fl:
        f = max(fl, key=lambda f: f["n"])
        q.update(fit_dir=bool(f["v0"][1] > 0), fit_n=int(f["n"]), fit_rms=float(f["rms"]), fit_end_y=float(f["p1"][1]), fit_end_z=float(f["p1"][2]),
                 fit_start_y=float(f["p0"][1]), fit_netz=None if np.isnan(f["netZ"]) else float(f["netZ"]),
                 fit_speed=3.6 * float(np.linalg.norm(f["v0"])), fit_ok=_reliable(f), fit_t0=float(f["t0"]))
    t_lim = q.get("fit_t0", T) - 0.05
    pv = [f for f in flights if f["t1"] < t_lim and f["t1"] > T - 4.0 and _reliable(f, 15)]
    if pv:
        q.update(prev_dir=bool(pv[-1]["v0"][1] > 0), prev_gap=float(T - pv[-1]["t1"]))
    return q


def _reliable(f, n=12):
    """A flight fit that can give the direction along the court."""
    sp = 3.6 * float(np.linalg.norm(f["v0"]))
    return bool(f["n"] >= n and f["rms"] < 5 and 15 < sp < 230 and abs(f["p0"][1]) < 25 and abs(f["v0"][1]) > 2.0)


def candidates(rows, Hm, flights=()):
    """All candidate touches of a time range, with their quantities (loose: for the study)."""
    npl = NetPlane(Hm)
    out = []
    for tr in tracks(rows):
        for i, dv in touch_points(tr, npl):
            x, z = npl.xz(tr[i:i + 1, 1:3])
            if abs(x[0]) > NET_POST_X + 0.5 or not (-0.35 <= z[0] <= cord_z(x[0]) + 0.35):
                continue
            q = features(tr, i, dv, rows, npl, flights)
            if q is not None:
                out.append(q)
    return out


SPEED_MIN = 2.5        # m/s in the net plane: a slower ball is not a shot
PASS_Y, PASS_Z = 2.0, 1.45   # m: uncertainty of the flight fit in depth; a net height at the net plane that is clearly above the cord
# E180 (S5, after E139): the near-hitter mirror of the far "passed" rule. A reliable fit of a near hitter that goes
# through the net plane higher than PASS_Z and ends more than PASS_Y beyond the net passed the net: it is not a touch,
# also when the ball is seen in the band after it (another ball or a player). On by default (manager after the E180
# bench in E184: fragments false touches 5 to 4, no right touch lost; lab the same with netBand on). "0" gives the old way.
PASS_NEAR = __import__("os").environ.get("NET_PASSED_NEAR", "1") == "1"


def direction(q):
    """Which player hit the ball. Returns (toFar, confidence 0..1, sources). toFar = True: the hitter is at the near end.
    Votes (positive = near hitter):
      below  the approach was below the net base line in the image: the ball was on the near side (sure)
      fit    the direction of a reliable flight fit (12 detections or more)
      after  where the ball is after the touch: below the base line = it fell on the near side; in the band = far side
      above  the approach came down from far above the cord: usual for the far player (a serve of the near player does this too)
      prev   the last reliable flight before the approach flew the other way
      same   that flight ended less than 0.4 s before the touch, and the approach has no fit: it is the same shot"""
    s, src = 0.0, []
    if q["z_start"] < -0.10 and q["over_max"] <= 0.25:
        s += 3.0; src.append("below")
    if q["fit_ok"]:
        s += 2.0 if q["fit_dir"] else -2.0; src.append("fit")
    elif q["fit_dir"] is not None and q["fit_n"] >= 8 and q["fit_rms"] < 3:      # a short flight: a weak vote
        s += 1.0 if q["fit_dir"] else -1.0; src.append("fit8")
    if q["stay_z_min"] is not None:
        if q["stay_z_min"] < -0.08:
            s += 1.5; src.append("after")
        elif q["stay_z_min"] > 0.05 and q["n_stay"] >= 3:
            s -= 1.0; src.append("after")
    if q["over_start"] > 0.5 and q["vz"] < -2.0:
        s -= 1.0; src.append("above")
    if q["prev_dir"] is not None and q.get("prev_gap", 9) < 2.5:
        if q["fit_dir"] is None and q["prev_gap"] < 0.4:      # no time for a new shot: that flight is the approach
            s += 1.0 if q["prev_dir"] else -1.0; src.append("same")
        else:
            s += -1.0 if q["prev_dir"] else 1.0; src.append("prev")
    return bool(s > 0), float(min(1.0, abs(s) / 3.0)), src


def judge(q):
    """Decision for one candidate touch. Returns None or a dict (kind, toFar, conf, src, dirConf, dirSrc)."""
    # a reliable flight fit that ends at the net: less than PASS_Y m from the net plane, below 1.5 m
    fit_net = bool(q["fit_ok"] and abs(q["fit_end_y"]) < PASS_Y and q["fit_end_z"] < 1.5 and (q.get("fit_netz") is None or q["fit_netz"] < PASS_Z))
    # a ball that flies along the view direction is slow in the net plane: then the speed of the fit counts
    if (q["speed"] < SPEED_MIN and not (fit_net and q["fit_speed"] >= 30.0)) or q["n_cont"] > 1 or abs(q["xN"]) > NET_POST_X:
        return None
    slack = abs(q["vz"]) * q["dt"]                 # the touch can be one frame after the last point of the approach
    if q["over"] > TOL_TOP + (slack if q["vz"] < 0 else 0.0) or q["zN"] < -TOL_BASE - (slack if q["vz"] > 0 else 0.0):
        return None
    to_far, dconf, dsrc = direction(q)
    # "drop": the ball is seen at the net after the touch, and it comes down to 0.5 m or less. For a ball that came
    # down to the net, it must also be 0.2 m or more below the last point of the approach.
    stay = bool(q["n_stay"]) and (q["n_stay_soon"] >= 2 or (q["n_stay"] >= 3 and q["stay_t0"] <= 1.1))         and q["stay_z_min"] < 0.5 and (q["vz"] > 1.0 or q["stay_z_min"] < q["zN"] - 0.20)
    src = ["track"]
    if q["vz"] < -1.0 and q["zN"] < 0.15:          # a ball that comes down to the net base: a bounce on the ground, not a touch
        return None
    if q["vz"] > 1.0 and q["z_start"] > -0.10:     # a ball that goes up inside the band: it comes from a bounce near the net
        return None
    if q["n_above"] >= 2 and not (stay and q["n_stay_soon"] >= 3):             # the ball went on above the cord, or it came up again (bounce, hit)
        return None
    if to_far:
        # near hitter: the approach must stay below the cord, and the ball must be seen at the net after the touch
        if PASS_NEAR and q["fit_ok"] and q["fit_end_y"] > PASS_Y and q.get("fit_netz") is not None and q["fit_netz"] > PASS_Z:
            return None
        if q["over_max"] > TOL_TOP + 0.10 and "below" in dsrc:
            return None
        if stay:
            conf = 0.9 if q["n_stay"] >= 4 else 0.75
        elif fit_net and q["end"] and q["n_above"] == 0 and q["z_start"] < -0.10:
            # no ball seen after it, but the track and the fit end at the net, and the approach started below the base line
            conf = 0.5
        else:
            return None
        if "below" in dsrc and q["speed"] < 5.0:   # a slow ball from below: often a player who handles a ball near the net
            conf = 0.6
    else:
        passed = q["fit_ok"] and q["fit_end_y"] < -PASS_Y and (q.get("fit_netz") is None or q["fit_netz"] > PASS_Z)
        if stay:
            conf = 0.9 if q["n_stay"] >= 4 else 0.75
        elif passed:
            return None
        else:
            conf = 0.5
    if stay:
        src.append("drop")
    if fit_net:
        src.append("fit"); conf = min(0.97, conf + 0.05)
    if dconf < 0.34:
        conf -= 0.1
    if q["vz"] < -1.0 and q["zN"] < 0.30:          # low at the net: can be a bounce on the ground
        conf = min(conf, 0.6)
    kind = "net"
    if not to_far and stay and q["fit_ok"] and not q["fit_dir"] and q["stay_z_min"] < -0.15:
        kind = "cord"                              # a ball of the far player that is below the net base line after the touch: it went over
    return {"kind": kind, "toFar": to_far, "conf": round(conf, 2), "src": "+".join(src), "dirConf": round(dconf, 2), "dirSrc": "+".join(dsrc)}


def dedupe(ev, gap=0.8):
    """One net event for one touch: of the candidates less than gap s apart, the last one with the best confidence.
    ev: list of (candidate, decision), any order."""
    ev = sorted(ev, key=lambda e: e[0]["t"])
    out = []
    for q, r in ev:
        if out and q["t"] - out[-1][0]["t"] < gap:
            if r["conf"] >= out[-1][1]["conf"]:
                out[-1] = (q, r)
            continue
        out.append((q, r))
    return out


def net_events(rows, Hm, flights=(), kinks=(), band=None):
    """Net events of a time range.
    rows: blobs (t, x, y, score, peak). Hm: court homography px -> m. flights: fitted flights (optional).
    kinks: not used (the function finds the direction changes in the net plane); the parameter keeps the call general.
    Returns a list, sorted by time, of
      {"t": s of the video (the last ball point before the touch; the touch is less than 1 frame later),
       "kind": "net" (the ball stays on the side of the hitter) or "cord" (the ball goes over after the touch),
       "px": [x, y] of the ball at the touch (1920-wide frame),
       "toFar": True = the hitter is at the near end,
       "conf": 0..1, confidence that the ball touched the net (0.7 or more: the ball was seen to drop at the net),
       "src": "track", "track+drop", "track+fit", "track+drop+fit" (what shows the touch),
       "dirConf": 0..1, confidence of "toFar", "dirSrc": the votes for "toFar" (see direction())}
    band: the net band motion of the video (net_band.Band) or None. With it, the net picture rejects or confirms a
    candidate (net_band.judge_band, E89 to E132)."""
    import net_band
    out = []
    for q, r in dedupe([(q, r) for q in candidates(rows, Hm, flights) for r in [net_band.judge_band(q, judge(q), band)] if r]):
        out.append({"t": round(q["t"], 3), "kind": r["kind"], "px": [round(q["px"][0], 1), round(q["px"][1], 1)], "toFar": r["toFar"],
                    "conf": r["conf"], "src": r["src"], "dirConf": r["dirConf"], "dirSrc": r["dirSrc"]})
    return out


# E16g: a net event decides the winner of a rally when it is the last one in this time window before the END of the
# rally, and the direction is sure enough. The window comes from the marks of the 4 lab matches (the mark of a point
# end is 2 to 4 s after the last touch). Earlier net events are first-serve faults: the rally continues.
HINT_FROM, HINT_TO, HINT_DIR = -4.5, -1.5, 0.3
# P(the hint is right) for dirConf >= 0.7 and below. Measured (E16g): marked ends 0.97 and 0.72 to 0.93 (tables of
# 3 matches each); detected ends of the automatic chain 0.945 and 0.77.
HINT_P = {"hi": 0.95, "lo": 0.8}


def winner_hint(events, t_end, t_from=HINT_FROM, t_to=HINT_TO, min_dir=HINT_DIR, t_start=None):
    """The net event that ends the rally with the end time t_end, or None.
    Returns {"nearWins": bool, "p": probability that this is right, "event": the net event, "sinceStart": s or None}.
    The hitter of a net ball loses the point: nearWins = not toFar. A "cord" event is no hint (the ball is in play).
    "sinceStart" = time of the event after t_start. Less than about 3 s and the hitter is the server: the net ball is
    a serve. Then the rally is a point only if it was the second serve (the caller must decide this)."""
    c = [e for e in events if t_from <= e["t"] - t_end < t_to and e["dirConf"] >= min_dir and e["kind"] == "net"]
    if not c:
        return None
    e = c[-1]
    return {"nearWins": not e["toFar"], "p": HINT_P["hi" if e["dirConf"] >= 0.7 else "lo"], "event": e,
            "sinceStart": None if t_start is None else round(e["t"] - t_start, 2)}


def run_folder(sid, step=30.0, band_file=None):
    """Net events of a full video from the cached files of its folder. Writes <folder>/net_events.json.
    band_file: the net band file of the folder (net_band.py), or None (no net band rules)."""
    import net_lab, net_band
    s = net_lab.Source(sid)
    band = net_band.Band(band_file, s.frame_t) if band_file else None
    out, c = [], float(s.t[0])
    while c < s.t[-1]:
        rows = s.rows(c - 2, c + step + 2)                 # 2 s of overlap: a touch at the border has its approach and its "after"
        if len(rows):
            out += [e for e in net_events(rows, s.H(c + step / 2), s.flights(c - 2, c + step + 2), band=band) if c <= e["t"] < c + step]
        c += step
    path = os.path.join(s.dir, "net_events.json")
    json.dump({"about": "E16f net events (tools/auto-analysis/net_events.py)", "events": out}, open(path, "w"), indent=1)
    return path, out


if __name__ == "__main__":
    for sid in sys.argv[1:]:
        path, ev = run_folder(sid)
        print(f"{sid}: net events {len(ev)} (conf >= 0.7: {sum(e['conf'] >= 0.7 for e in ev)}, cord {sum(e['kind'] == 'cord' for e in ev)}) -> {path}", flush=True)

"""E9: serve detection from the 3D ball flights.

Serve flight: starts behind a baseline (|y| > Y_MIN m), high (z > Z_MIN m),
fast (> V_MIN km/h), and flies to the other side.
Part 1 (this script, "points"): the first flight of each marked point
(window start-1 s to start+1.5 s). The marks start at the serve that was in,
so this measures the recall of the detector and checks the geometry:
  - the server end from the ball against the rules (sides.json server + ends.json nearPlayer),
  - the serve side (start x) against sides.json serveSide,
  - the landing point (bounce kink, else the fit) against the correct service box.
Usage: python serve_ball.py
"""
import json, os
import numpy as np
import lab
import winner_ball as wb

Y_MIN, Z_MIN, V_MIN = 10.0, 1.6, 45.0
# E77 (found on Igor, value from physics and the lab matches): the first sample of a toss is at the release, and the
# hand releases the ball from about hip height (1.0 m) up. The old limit (1.5 m) lost 40 % of the toss flights at
# the serve starts of the lab matches (median start 1.45 to 1.72 m). Far-end toss flights from 1.0 to 1.5 m: 33 at
# serve starts, 5 inside rallies (the band over 1.5 m: 58 and 9). Under 1.0 m the flights are mostly ball bounces.
TOSS_Z = 1.0
SW, SL = 4.115, 6.40


def is_serve(f):
    p0, v0 = f["p0"], f["v0"]
    sp = 3.6 * float(np.linalg.norm(v0))
    return f["n"] >= 8 and f["rms"] < 5 and abs(p0[1]) > Y_MIN and abs(p0[1]) < 16 and p0[2] > Z_MIN and p0[2] < 3.6 \
        and V_MIN < sp < 240 and v0[1] * p0[1] < 0 and abs(p0[0]) < 6


def is_toss(f):
    """Serve toss: a slow, high ball above a baseline area."""
    p0, v0 = f["p0"], f["v0"]
    sp = 3.6 * float(np.linalg.norm(v0))
    return f["n"] >= 8 and f["rms"] < 5 and 9.0 < abs(p0[1]) < 16 and TOSS_Z < p0[2] < 4.0 and abs(p0[0]) < 5.5 and sp < 30


def is_fast_out(f):
    """A fast flight that starts on one half (5 m or more from the net) and flies to the other half."""
    p0, v0 = f["p0"], f["v0"]
    sp = 3.6 * float(np.linalg.norm(v0))
    return f["n"] >= 8 and f["rms"] < 5 and 5.0 < abs(p0[1]) < 16 and 1.2 < p0[2] < 3.6 and 45 < sp < 240         and v0[1] * p0[1] < 0 and abs(p0[0]) < 6


RALLY_FILTER = os.environ.get("SERVE_FILTER", "1") == "1"      # E28e: a flight that returns a ball is not a serve event
RETURN_S, RETURN_V = 3.0, 8.0     # s (time for two flights: one shot in a gap of the ball data is normal), m/s along the court (ball_bounce.HIT_V)
# E126: a ball faster than RETURN_MAX km/h is a wrong 3D fit, not a return (the fastest serves are about 250 km/h;
# is_serve uses the same limit). The limit gives back 11 of the 22 lab starts where is_return removed the serve flight:
# the ball before was a fit at 240 to 1740 km/h, with a start up to 90 m behind the baseline (mostly the toss or the serve
# start with a wrong depth). At the other 11 the wrong fit is slower or the ball is a real one.
RETURN_MAX = 240.0


def is_return(f, flights):
    """True if the ball came from the other side less than RETURN_S s before the flight f: a reliable flight that
    starts on the other half, flies in the opposite direction faster than RETURN_V along the court, and ends in
    that time. Then f is a rally shot.
    Before a serve the ball does not come from the other side (the toss has no speed along the court)."""
    for g in flights:
        if g is not f and f["t0"] - RETURN_S <= g["t1"] <= f["t0"] + 0.05 and g["v0"][1] * f["v0"][1] < 0                 and g["p0"][1] * f["p0"][1] < 0 and abs(g["v0"][1]) >= RETURN_V and g["n"] >= 6 and g["t1"] - g["t0"] >= 0.15 - 1e-6 and g["rms"] < 5 \
                and 3.6 * float(np.linalg.norm(g["v0"])) < RETURN_MAX:
            return True
    return False


def serve_events(flights, t0, t1, rally_filter=None):
    """Serve events in [t0, t1]: list of (time, server near?, kind, flight). A toss and the fast
    flight after it (same end, within 1.5 s) make one event.
    rally_filter (default RALLY_FILTER): no event from a flight that returns a ball (is_return)."""
    ev = []
    rally_filter = RALLY_FILTER if rally_filter is None else rally_filter
    for i, f in enumerate(flights):
        if not (t0 <= f["t0"] <= t1):
            continue
        kind = "serve" if is_serve(f) else "toss" if is_toss(f) else "fast" if is_fast_out(f) else None
        if kind is None:
            continue
        if rally_filter and kind != "toss" and is_return(f, flights[max(0, i - 12):i]):
            continue
        near = f["p0"][1] < 0
        if kind == "toss" and near and not (ev and ev[-1][1] == near and f["t0"] - ev[-1][0] < 1.5):
            ev.append((f["t0"], near, "nearToss", f))    # kept only to join a fast flight after it
            continue
        if ev and ev[-1][1] == near and f["t0"] - ev[-1][0] < 1.5:
            if kind != "toss" and ev[-1][2] in ("toss", "nearToss"):
                ev[-1] = (ev[-1][0], near, "toss+" + kind, f)
            continue
        ev.append((f["t0"], near, kind, f))
    # E10 check by eye (m1015): a toss alone at the near end was never a serve (the player walks
    # or bounces the ball). The 3D height of a ball near the camera is not reliable.
    return [e for e in ev if e[2] != "nearToss"]


def main_events():
    """Serve events at the point starts: recall and server end."""
    tot = [0, 0, 0]
    for mid in wb.SINGLES:
        pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
        n = det = ok = 0
        kinds = {}
        for pt, e, sd in zip(pts, ends, sides):
            n += 1
            ev = serve_events(pt["flights"], pt["start"] - 1.0, pt["start"] + 2.5)
            if not ev:
                continue
            det += 1
            ok += int(ev[0][1] == (sd["server"] == e["nearPlayer"]))
            kinds[ev[0][2]] = kinds.get(ev[0][2], 0) + 1
        tot = [tot[0] + n, tot[1] + det, tot[2] + ok]
        print(f"{mid}: serve event at {100 * det / n:.0f}% of the point starts, server end right {100 * ok / max(1, det):.1f}%, kinds {kinds}")
    print(f"ALL: serve event at {100 * tot[1] / tot[0]:.0f}%, server end right {100 * tot[2] / tot[1]:.1f}%")


def landing(f, kinks):
    """Bounce of a serve flight: an anchored bounce, else a kink near the predicted landing time
    (homography position), else the ballistic landing. Returns (x, y, source)."""
    if f.get("bounce"):
        return f["bounce"][0], f["bounce"][1], "anchor"
    lt = f.get("landT")
    if lt is not None and not np.isnan(f["land"][0]):
        near = [k for k in kinks if abs(k[0] - lt) < 0.12]
        if near:
            k = min(near, key=lambda k: abs(k[0] - lt))
            return k[3], k[4], "kink"
        return f["land"][0], f["land"][1], "fit"
    return np.nan, np.nan, "none"


def box_margin(x, y, server_near, side):
    """Distance (m) inside the correct service box (negative = out)."""
    s = 1.0 if server_near else -1.0        # the box is on the far side for a near server
    yy = y * s
    right_of_server_is_plus_x = server_near
    deuce_box_x_sign = -1.0 if right_of_server_is_plus_x else 1.0   # the receiver's right
    xs = deuce_box_x_sign if side == "deuce" else -deuce_box_x_sign
    xx = x * xs
    return min(yy, SL - yy, xx, SW - xx)


NET_Z, OUT_M = 0.7, -0.3


def serve_result(f, kinks, server_near, side, kind=""):
    """Result of a serve flight: "net", "out", "in" or "unknown".
    Order: a toss alone -> unknown. A bounce anchor (seen bounce) decides in or out. Else net:
    the fitted height at the net plane is below NET_Z m, or the flight stops near the net
    (|end y| < 2.6 m, end z < 1.2 m). Else the landing from a kink or the fit.
    Tolerance: 0.3 m across; along the court 0.3 m for a far server and 1.0 m for a near
    server (the depth of a ball that flies away from the camera is the weak direction, E17a)."""
    if kind == "toss":
        return "unknown"
    tol_y = 1.0 if server_near else 0.3

    def inside(x, y):
        s_ = 1.0 if server_near else -1.0
        yy = y * s_
        xs = (-1.0 if server_near else 1.0) * (1.0 if side == "deuce" else -1.0)
        xx = x * xs
        return min(xx, SW - xx) > -0.3 and min(yy, SL - yy) > -tol_y
    if f.get("bounce"):
        return "in" if inside(*f["bounce"]) else "out"
    if not np.isnan(f["netZ"]) and f["netZ"] < NET_Z:
        return "net"
    if abs(f["p1"][1]) < 2.6 and f["p1"][2] < 1.2 and not np.isnan(f["netZ"]) and f["netZ"] < 1.0:
        return "net"
    x, y, src = landing(f, kinks)
    if np.isnan(x):
        return "unknown"
    if inside(x, y):
        return "in"
    # E10b: for a near server, a kink or fit landing behind the service line is often the
    # return hit of the receiver (the ball is above the ground there). No decision.
    return "unknown" if server_near else "out"


def main_result():
    """E10: serve result for the serves at the point starts (they are in) and for the
    fault candidates before the points (they are serves that were not in)."""
    from fault_ball import MIN_GAP
    tot = {"own": {}, "cand": {}}
    for mid in wb.SINGLES:
        pp = os.path.join(lab.mdir(mid), f"preflights_s{lab.BALL_TAG}.json")
        if not os.path.exists(pp):
            continue
        pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
        pre = json.load(open(pp))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
        res = {"own": {}, "cand": {}}
        for pt, w, e, sd in zip(pts, pre, ends, sides):
            near = sd["server"] == e["nearPlayer"]
            ev = [x for x in serve_events(pt["flights"], pt["start"] - 1.0, pt["start"] + 2.5) if x[1] == near]
            if ev:
                r = serve_result(ev[0][3], pt["kinks"], near, sd["serveSide"], ev[0][2])
                res["own"][r] = res["own"].get(r, 0) + 1
            ev = [x for x in serve_events(w["flights"], w["w0"], pt["start"] - MIN_GAP) if x[1] == near]
            if ev:
                r = serve_result(ev[-1][3], w["kinks"], near, sd["serveSide"], ev[-1][2])
                res["cand"][r] = res["cand"].get(r, 0) + 1
        for k in res:
            for r, c in res[k].items():
                tot[k][r] = tot[k].get(r, 0) + c
        print(f"{mid}: serves at the point start {res['own']} | candidates before the point {res['cand']}")
    print(f"ALL: serves at the point start {tot['own']} | candidates before the point {tot['cand']}")


def main():
    tot = {"n": 0, "det": 0, "end": 0, "side": 0, "inbox": 0, "land": 0}
    for mid in wb.SINGLES:
        pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
        c = {k: 0 for k in tot}
        margins = []
        for pt, e, sd in zip(pts, ends, sides):
            c["n"] += 1
            fl = [f for f in pt["flights"] if pt["start"] - 1.0 <= f["t0"] <= pt["start"] + 1.5 and is_serve(f)]
            if not fl:
                continue
            f = fl[0]
            c["det"] += 1
            server_near = f["p0"][1] < 0
            true_near = sd["server"] == e["nearPlayer"]
            c["end"] += int(server_near == true_near)
            # serve side: the server stands right of the center mark in the deuce court
            right = f["p0"][0] > 0 if server_near else f["p0"][0] < 0
            c["side"] += int((sd["serveSide"] == "deuce") == right)
            x, y, src = landing(f, pt["kinks"])
            if not np.isnan(x):
                c["land"] += 1
                m = box_margin(x, y, true_near, sd["serveSide"])
                margins.append(m)
                c["inbox"] += int(m > -0.3)
        for k in tot:
            tot[k] += c[k]
        mm = np.array(margins)
        print(f"{mid}: points {c['n']}, serve found {100 * c['det'] / c['n']:.0f}%, server end right {100 * c['end'] / max(1, c['det']):.1f}%, "
              f"serve side right {100 * c['side'] / max(1, c['det']):.1f}%, landing found {c['land']}, in the box (0.3 m) {100 * c['inbox'] / max(1, c['land']):.1f}%, "
              f"median margin {np.median(mm) if len(mm) else np.nan:.2f} m")
    t = tot
    print(f"ALL: points {t['n']}, serve found {100 * t['det'] / t['n']:.0f}%, server end right {100 * t['end'] / t['det']:.1f}%, serve side right {100 * t['side'] / t['det']:.1f}%, "
          f"in the box {100 * t['inbox'] / max(1, t['land']):.1f}% of {t['land']}")


if __name__ == "__main__":
    main()
    main_events()
    main_result()

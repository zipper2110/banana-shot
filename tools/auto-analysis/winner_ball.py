"""E16: point winner from the ball flights (flights_s<scale>.json of ball_3d.py).

Rule (no training):
  1. Good flights: n >= 10, fit error < 5 px, speed 8 to 230 km/h, start
     inside a large box around the court.
  2. The last shot = the last run of good flights with the same direction
     (sign of the velocity along the court), that starts before the point
     end + 1 s. Hitter end: near if the ball flies to the far side.
  3. First bounce of the last shot: a kink where the flight before it ends
     near the ground (or the flight after it starts near the ground), with
     no change of direction. Its court position comes from the homography
     of the kink pixel (exact for a ball on the ground). If there is no
     such kink: the ballistic landing point of the first flight.
  4. In = inside the singles court on the far side of the net from the
     hitter (margin MARGIN) and above the net at the net plane.
     In -> the hitter wins. Out or net -> the other player wins.
Also: a gradient-boosting classifier on the same quantities, leave one
match out.
Usage: python winner_ball.py [scale]
"""
import json, os, sys
import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
import lab

SW, BL, MARGIN = 4.115, 11.885, 0.3
SINGLES = ["m1015", "m1020", "m1021", "m1030"]


def good(f):
    sp = 3.6 * float(np.linalg.norm(f["v0"]))
    return f["n"] >= 10 and f["rms"] < 5 and 8 < sp < 230 and abs(f["p0"][1]) < 25 and abs(f["p0"][0]) < 12


T_CUT = -1.5      # use only flights that start before the marked end + T_CUT (s). E16c: later flights are ball handling after the point


def analyse(pt):
    """Returns a dict of quantities of the last shot, or None if there is no usable flight."""
    T = pt["end"]
    fl = [f for f in pt["flights"] if good(f) and f["t0"] < T + T_CUT]
    if not fl:
        return None
    run = [fl[-1]]
    for f in fl[-2::-1]:
        if (f["v0"][1] > 0) == (run[0]["v0"][1] > 0) and run[0]["t0"] - f["t1"] < 0.6:
            run.insert(0, f)
        else:
            break
    to_far = run[0]["v0"][1] > 0
    s = 1.0 if to_far else -1.0
    bounce, src = None, 0
    for k in pt["kinks"]:
        t = k[0]
        if t < run[0]["t0"] + 0.1 or t > run[-1]["t1"] + 0.3:
            continue
        pre = [f for f in run if 0 <= t - f["t1"] < 0.2]
        post = [f for f in run if 0 <= f["t0"] - t < 0.2]
        low = (pre and pre[-1]["p1"][2] < 0.7) or (post and post[0]["p0"][2] < 0.6 and post[0]["v0"][2] > 0)
        if low and abs(k[3]) < 12 and abs(k[4]) < 22:
            bounce, src = (k[3], k[4], t), 2
            break
    if bounce is None and not np.isnan(run[0]["land"][0]) and run[0]["n"] >= 20:
        if run[0]["landT"] < T + 1.5:
            bounce, src = (run[0]["land"][0], run[0]["land"][1], run[0]["landT"]), 1
    net_z = run[0]["netZ"]
    crossed = any(f["p1"][1] * s > 0.5 for f in run) or (bounce is not None and bounce[1] * s > 0)
    q = {"to_far": to_far, "src": src, "n_run": len(run), "n_pts": sum(f["n"] for f in run), "speed": 3.6 * float(np.linalg.norm(run[0]["v0"])),
         "t_rel": run[0]["t0"] - T, "t_end_rel": run[-1]["t1"] - T, "net_z": net_z, "crossed": crossed,
         "start_y": run[0]["p0"][1] * s, "start_z": run[0]["p0"][2], "end_y": run[-1]["p1"][1] * s, "end_z": run[-1]["p1"][2],
         "bx": np.nan, "by": np.nan, "margin": np.nan, "n_flights": len(fl), "coverage": pt["coverage"]}
    if bounce is not None:
        bx, by = bounce[0], bounce[1] * s
        q.update(bx=abs(bx), by=by, margin=min(SW - abs(bx), BL - by, by), b_rel=bounce[2] - T)
    return q


def rule(q):
    """True = the near player wins. None = no decision."""
    if q is None:
        return None
    if q["src"] == 0:
        if not q["crossed"] and abs(q["end_y"]) < 2.5 and q["end_z"] < 1.2:      # stopped at the net
            return not q["to_far"]
        return None
    is_in = q["margin"] > -MARGIN and (np.isnan(q["net_z"]) or q["net_z"] > 0.85)
    return q["to_far"] if is_in else not q["to_far"]


KEYS = ["to_far", "src", "n_run", "n_pts", "speed", "t_rel", "t_end_rel", "net_z", "crossed", "start_y", "start_z", "end_y", "end_z", "bx", "by", "margin", "n_flights", "coverage"]


def main(scale=lab.BALL_TAG):
    X, y, g, r = [], [], [], []
    for mid in SINGLES:
        p = os.path.join(lab.mdir(mid), f"flights_s{scale}.json")
        if not os.path.exists(p):
            continue
        pts = json.load(open(p))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        n = hit = dec = 0
        for pt, e in zip(pts, ends):
            if e["winnerNear"] is None:
                continue
            q = analyse(pt)
            w = rule(q)
            n += 1
            if w is not None:
                dec += 1; hit += int(w == e["winnerNear"])
            X.append([float(q[k]) if q else np.nan for k in KEYS] + [float(e["serverNearRules"]) if e["serverNearRules"] is not None else np.nan])
            y.append(int(e["winnerNear"])); g.append(mid); r.append(w)
        print(f"{mid}: points {n}, rule decides {dec} ({100 * dec / max(1, n):.0f}%), correct of decided {100 * hit / max(1, dec):.1f}%, "
              f"correct of all (coin for the rest) {100 * (hit + 0.5 * (n - dec)) / max(1, n):.1f}%")
    X, y, g = np.array(X), np.array(y), np.array(g)
    dec = np.array([w is not None for w in r]); ok = np.array([w == bool(t) if w is not None else False for w, t in zip(r, y)])
    print(f"ALL rule: points {len(y)}, decided {100 * dec.mean():.0f}%, correct of decided {100 * ok[dec].mean():.1f}%, with coin {100 * (ok.sum() + 0.5 * (~dec).sum()) / len(y):.1f}%")
    mids = sorted(set(g))
    if len(mids) >= 2:
        acc = []
        for m in mids:
            clf = HistGradientBoostingClassifier(max_depth=3, max_iter=120, learning_rate=0.06, random_state=0).fit(X[g != m], y[g != m])
            a = (clf.predict(X[g == m]) == y[g == m]).mean()
            acc.append((a, (g == m).sum()))
            print(f"{m}: classifier {100 * a:.1f}%")
        print(f"ALL classifier: {100 * sum(a * n for a, n in acc) / sum(n for _, n in acc):.1f}%")


if __name__ == "__main__":
    main(*sys.argv[1:2])

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
G7 step 2a: steps 1 to 3 are shot_chain.old_quantities (one copy, in the shot chain).
Usage: python winner_ball.py [scale]
"""
import json, os, sys
import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier
import lab
import shot_chain as sc

SW, BL, MARGIN = 4.115, 11.885, 0.3
SINGLES = ["m1015", "m1020", "m1021", "m1030"]


MIN_N, LAND_N = 10, 20   # samples of a good flight, and of a flight with a usable fit landing. Values for 60 fps (0.15 s, 0.32 s).
                         # E28: for a video with another frame rate use set_fps() (pipeline.FPS_ADAPT).


def set_fps(fps):
    """The sample counts for a ball track with fps samples in each second: the same flight durations as at 60 fps."""
    global MIN_N, LAND_N
    MIN_N, LAND_N = max(5, int(round(10 * fps / 60.0))), max(8, int(round(20 * fps / 60.0)))


def good(f):
    sp = 3.6 * float(np.linalg.norm(f["v0"]))
    return f["n"] >= MIN_N and f["rms"] < 5 and 8 < sp < 230 and abs(f["p0"][1]) < 25 and abs(f["p0"][0]) < 12


T_CUT = -1.5      # use only flights that start before the marked end + T_CUT (s). E16c: later flights are ball handling after the point


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
            q = sc.old_quantities(pt, T_CUT)
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

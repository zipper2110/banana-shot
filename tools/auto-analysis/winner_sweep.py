"""E16 step 2: winner rule with parameters, chosen leave one match out.

Changes to the rule of winner_ball.py, from the wrong cases of m1015:
  - Net: the last flight of the last shot ends near the net plane
    (-NET_BACK < end_y < NET_FWD m, end_y > 0 = past the net) and low
    (end_z < 1.8 m). Then the hitter loses, also when a landing point exists.
  - MARGIN: a bounce that is out by less than MARGIN m counts as in.
    (m1015 suggests that MARGIN 0.3 is too large.)
  - MIN_SPEED: flights slower than this are not shots (ball pick-ups).
For each test match the parameters come from a grid search on the other 3.
Usage: python winner_sweep.py
"""
import itertools, json, os
import numpy as np
import lab
import winner_ball as wb

GRID = {"margin": [-0.3, -0.15, 0.0, 0.15, 0.3], "net_fwd": [0.0, 1.0, 2.0], "net_back": [1.0, 2.5, 4.0], "min_speed": [8, 20, 30]}


def load():
    data = {}
    for mid in wb.SINGLES:
        pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        data[mid] = [(pt, e["winnerNear"]) for pt, e in zip(pts, ends) if e["winnerNear"] is not None]
    return data


def decide(q, p):
    if q is None:
        return None
    if -p["net_back"] < q["end_y"] < p["net_fwd"] and q["end_z"] < 1.8 and not q["src"] == 2:
        return not q["to_far"]                         # in the net: the hitter loses
    if q["src"] == 0:
        return None
    # E17d: an extrapolated landing has a 1-sigma error. Nearer to the line than z * sigma: no decision.
    if q["src"] == 1 and p.get("z", 0) > 0 and not np.isnan(q.get("sigma", np.nan)) and abs(q["margin"] + p["margin"]) < p["z"] * q["sigma"]:
        return None
    is_in = q["margin"] > -p["margin"] and (np.isnan(q["net_z"]) or q["net_z"] > 0.85)
    return q["to_far"] if is_in else not q["to_far"]


def score(items, p):
    hit = dec = 0
    for q, y in items:
        w = decide(q, p)
        if w is not None:
            dec += 1; hit += int(w == y)
    return hit, dec, len(items)


def main():
    import sys
    if "--z" in sys.argv:                              # E17d: also search the factor z of the landing error
        GRID["z"] = [0.0, 0.5, 1.0]
    data = load()
    keys = list(GRID)
    combos = [dict(zip(keys, v)) for v in itertools.product(*GRID.values())]
    qs = {}
    for ms in GRID["min_speed"]:
        orig = wb.good
        wb.good = lambda f, ms=ms, orig=orig: orig(f) and 3.6 * float(np.linalg.norm(f["v0"])) >= ms
        qs[ms] = {m: [(wb.analyse(pt), y) for pt, y in v] for m, v in data.items()}
        wb.good = orig
    tot = [0, 0, 0]
    for test in wb.SINGLES:
        best = max(combos, key=lambda p: sum((lambda h, d, n: h + 0.5 * (n - d))(*score(qs[p["min_speed"]][m], p)) for m in wb.SINGLES if m != test))
        h, d, n = score(qs[best["min_speed"]][test], best)
        tot = [tot[0] + h, tot[1] + d, tot[2] + n]
        print(f"{test}: params {best} -> decides {100 * d / n:.0f}%, correct of decided {100 * h / max(1, d):.1f}%, with coin {100 * (h + 0.5 * (n - d)) / n:.1f}%")
    h, d, n = tot
    print(f"ALL (LOMO params): decided {100 * d / n:.0f}%, correct of decided {100 * h / d:.1f}%, with coin {100 * (h + 0.5 * (n - d)) / n:.1f}%")
    # what the best single setting gives on all 4 (optimistic)
    best = max(combos, key=lambda p: sum((lambda h, d, n: h + 0.5 * (n - d))(*score(qs[p["min_speed"]][m], p)) for m in wb.SINGLES))
    r = [score(qs[best["min_speed"]][m], best) for m in wb.SINGLES]
    h, d, n = (sum(x[i] for x in r) for i in range(3))
    print(f"best on all {best}: decided {100 * d / n:.0f}%, correct of decided {100 * h / d:.1f}%, with coin {100 * (h + 0.5 * (n - d)) / n:.1f}%")
    # by coverage of the point
    q0 = [(q, y, pt["coverage"]) for m in wb.SINGLES for (q, y), (pt, _) in zip(qs[best["min_speed"]][m], data[m])]
    for lo, hi in ((0, 0.3), (0.3, 0.45), (0.45, 1.01)):
        sel = [(q, y) for q, y, c in q0 if lo <= c < hi]
        h, d, n = score(sel, best)
        print(f"  coverage {lo:.2f}-{hi:.2f}: points {n}, decided {100 * d / max(1, n):.0f}%, correct of decided {100 * h / max(1, d):.1f}%")


if __name__ == "__main__":
    main()

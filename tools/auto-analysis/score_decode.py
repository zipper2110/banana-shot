"""E14 first test: the score grammar corrects the point winners.

Input for each point: the probability that P1 wins, from the ball rule of
E16 (winner_sweep.decide with the parameters chosen leave one match out):
decided -> P_DEC for the selected player, not decided -> 0.5.
Game boundaries: from sides.json (the replay of the marks). This is an upper
limit: in a real run the boundaries come from the server change (E12) and
can be wrong.
Inside each game: forward-backward over the score states (a, b). A valid
path ends the game exactly at the last point of the game (4 points and a
lead of 2; tiebreak: 7 and 2). Result: the most probable winner of each
point (max marginal).
Usage: python score_decode.py
"""
import json, os
import numpy as np
import lab
import winner_ball as wb
import winner_sweep as ws

P_DEC = 0.74


def ended(a, b, tb):
    t = 7 if tb else 4
    return max(a, b) >= t and abs(a - b) >= 2


def marginals(p1, tb, a0=0, b0=0):
    """p1: probabilities that P1 wins each point of one game. Returns posterior P1 wins, or None if no valid path."""
    n = len(p1)
    fw = [{(a0, b0): 1.0}]
    for i in range(n):
        nx = {}
        for (a, b), w in fw[-1].items():
            if ended(a, b, tb):
                continue
            for win, pr in ((1, p1[i]), (0, 1 - p1[i])):
                s = (a + win, b + 1 - win)
                nx[s] = nx.get(s, 0.0) + w * pr
        fw.append(nx)
    ok = {s: w for s, w in fw[-1].items() if ended(*s, tb)}
    if not ok or sum(ok.values()) == 0:
        return None
    bw = [None] * (n + 1)
    bw[n] = {s: 1.0 for s in ok}
    for i in range(n - 1, -1, -1):
        bw[i] = {}
        for (a, b) in fw[i]:
            if ended(a, b, tb):
                continue
            v = 0.0
            for win, pr in ((1, p1[i]), (0, 1 - p1[i])):
                v += pr * bw[i + 1].get((a + win, b + 1 - win), 0.0)
            bw[i][(a, b)] = v
    Z = sum(fw[0][s] * bw[0].get(s, 0) for s in fw[0])
    out = []
    for i in range(n):
        w1 = sum(fw[i][(a, b)] * p1[i] * bw[i + 1].get((a + 1, b), 0.0) for (a, b) in fw[i] if not ended(a, b, tb))
        out.append(w1 / Z)
    return out


def main():
    data = ws.load()
    from match_decode import PARAMS as params
    T = {"raw": [0, 0], "dec": [0, 0]}
    for mid in wb.SINGLES:
        p = params[mid]
        orig = wb.good
        wb.good = lambda f, orig=orig: orig(f) and 3.6 * float(np.linalg.norm(f["v0"])) >= p["min_speed"]
        pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
        games, cur, key = [], [], None
        for i, sd in enumerate(sides):
            k = (sd["set"], sd["game"])
            if k != key and cur:
                games.append(cur); cur = []
            key = k
            cur.append(i)
        games.append(cur)
        raw_ok = dec_ok = n = skipped = 0
        for g in games:
            if any(ends[i]["winnerNear"] is None for i in g):
                skipped += len(g); continue
            truth = [ends[i]["winnerNear"] == (ends[i]["nearPlayer"] == "P1") for i in g]       # True = P1 wins
            sd0 = sides[g[0]]
            a0, b0 = sd0["scoreBefore"]["rawPoints"]
            tb = sd0["isTiebreak"]
            if marginals([0.99 if t else 0.01 for t in truth], tb, a0, b0) is None:          # the marks do not form a full game (end of match, manual fix)
                skipped += len(g); continue
            p1 = []
            for i in g:
                w = ws.decide(wb.analyse(pts[i]), p)
                if w is None:
                    p1.append(0.5)
                else:
                    near1 = ends[i]["nearPlayer"] == "P1"
                    p1.append(P_DEC if (w == near1) else 1 - P_DEC)
            post = marginals(p1, tb, a0, b0)
            for t, pr, pp in zip(truth, p1, post):
                n += 1
                raw_ok += (pr > 0.5) == t if pr != 0.5 else 0.5
                dec_ok += (pp > 0.5) == t if abs(pp - 0.5) > 1e-9 else 0.5
        wb.good = orig
        T["raw"][0] += raw_ok; T["dec"][0] += dec_ok; T["raw"][1] += n; T["dec"][1] += n
        print(f"{mid}: points in full games {n} (skipped {skipped}), ball rule {100 * raw_ok / n:.1f}%, with the score grammar {100 * dec_ok / n:.1f}%")
    print(f"ALL: ball rule {100 * T['raw'][0] / T['raw'][1]:.1f}%, with the score grammar {100 * T['dec'][0] / T['dec'][1]:.1f}%")


if __name__ == "__main__":
    main()

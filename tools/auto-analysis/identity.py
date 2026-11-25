"""E22: which player is at the near end in each detected rally, with no marks and no score.

The app needs the winner as "player 1" or "player 2", not as "near end" or "far end". The score
decoder gives a state for each rally, but these states are not one consistent path: its "player 1
is near" value is right for only 30 to 66 % of the rallies (measured on pipeline.json, 2026-11-23).

Method (shirt color only, E11 and E14e):
  1. feature of a rally = median torso color of the near player minus the far player (Lab),
     with half weight for the lightness. Measured (in sample, 4 matches): full lightness and
     update rate 0.5: m1021 (hard sun and shadow) is right for 65 %; no lightness: m1030 62 %;
     weight 0.5 and rate 0.3: 100, 100, 99.4, 100 %;
  2. a running reference (near_identity.flips) gives a sign for each rally: +1 = the same
     player is near as at the reference, -1 = the players changed ends;
  3. a 2-state hidden Markov model smooths the signs: the players change ends rarely, so a
     single different sign between equal neighbors is noise. A rally with no color gets the
     state of its neighbors.
Player 1 = the player at the near end in the first rally.
Usage: python identity.py            (evaluation on the 4 singles matches against ends.json)
"""
import json, os
import numpy as np

P_SWITCH = 0.04        # probability of an end change between two rallies (about one change each 2 games)
W_LAB = np.array([0.5, 1.0, 1.0])     # weight of L, a, b: sun and shadow change the lightness most
ALPHA = 0.3            # update rate of the running reference
P_RIGHT = 0.85         # probability that the sign of one rally is right


def signs(feats, alpha=ALPHA):
    """Sign of each rally against a running reference of the color difference, and its strength
    (cosine to the reference). None for a rally with no color."""
    out, ref = [], None
    for d in feats:
        if d is None:
            out.append(None); continue
        d = np.asarray(d, float) * W_LAB
        if ref is None:
            ref = d.copy(); out.append((1, 1.0)); continue
        c = float(d @ ref) / (np.linalg.norm(d) * np.linalg.norm(ref) + 1e-9)
        s = 1 if c >= 0 else -1
        # E50: a weak sign moves the reference less (the same scale as in p1_near). With the full rate, one rally with
        # a cosine near 0 after a break turned the reference in m1030, and the players were swapped from there.
        a = alpha * min(1.0, abs(c) / 0.5)
        ref = (1 - a) * ref + a * s * d
        out.append((s, abs(c)))
    return out


def p1_near(feats):
    """For each rally: True if player 1 (the near player of the first rally) is at the near end."""
    sg = signs(feats)
    n = len(sg)
    if n == 0:
        return []
    lt = np.log(np.array([[1 - P_SWITCH, P_SWITCH], [P_SWITCH, 1 - P_SWITCH]]))
    score = np.zeros((n, 2)); back = np.zeros((n, 2), int)

    def emit(i):
        if sg[i] is None:
            return np.zeros(2)
        s, c = sg[i]
        p = 0.5 + (P_RIGHT - 0.5) * min(1.0, c / 0.5)       # a weak color difference says less
        return np.log(np.array([p, 1 - p]) if s > 0 else np.array([1 - p, p]))

    score[0] = emit(0)
    for i in range(1, n):
        m = score[i - 1][:, None] + lt
        back[i] = m.argmax(0)
        score[i] = m.max(0) + emit(i)
    st = np.zeros(n, int)
    st[-1] = score[-1].argmax()
    for i in range(n - 1, 0, -1):
        st[i - 1] = back[i][st[i]]
    first = st[0]
    return [bool(s == first) for s in st]


def main():
    import lab
    for mid in ["m1015", "m1020", "m1021", "m1030"]:
        pj = json.load(open(os.path.join(lab.mdir(mid), "pipeline.json")))["rallies"]
        import chain
        cache = json.load(open(os.path.join(lab.mdir(mid), chain.COLOR_CACHE)))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        gt = lab.gt_points(mid)
        feats = []
        for r in pj:
            cols = cache.get(f"{int(r['start'])}_{int(r['end'])}", [])
            feats.append(np.median(np.array(cols), 0) if len(cols) >= 2 else None)
        pn = p1_near(feats)
        n = a = w = wa = 0
        for r, p in zip(pj, pn):
            if "gt" not in r or ends[r["gt"]].get("nearPlayer") is None:
                continue
            n += 1
            a += p == (ends[r["gt"]]["nearPlayer"] == "P1")
            out = gt[r["gt"]][2]
            if out in ("P1", "P2"):
                w += 1
                wa += ("P1" if (r["pNearWins"] > 0.5) == p else "P2") == out
        a, wa = max(a, n - a), max(wa, w - wa)                # player 1 of the marks can be our player 2
        print(f"{mid}: rallies {n}, near player right {100 * a / n:.1f}%, winner as a player right {100 * wa / w:.1f}% "
              f"(no color: {sum(f is None for f in feats)})", flush=True)


if __name__ == "__main__":
    main()

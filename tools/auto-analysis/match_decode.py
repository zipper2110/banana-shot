"""E14: full-match score decoder (hidden Markov model over the score).

Hidden state before each marked point: games (g1, g2), points (a, b; deuce
states merged), tiebreak point count k (mod 12), server, which player is at
the near end, first server of the tiebreak. The state changes by the tennis
rules (advantage games, sets to 6 with a tiebreak at 6-6, ends change after
odd games and every 6 tiebreak points, server change). The decoder does not
know the first server or the first ends (4 start states). It can insert an
unmarked point with probability EPS (missed marks).
Evidence for each point (all relative to the near end, so names are not needed):
  - winner: the ball rule of E16 (LOMO parameters): P(near wins) = P_DEC or 1 - P_DEC, else 0.5.
  - server: the pose vote of E12 (ends.json serverNearVision): P = P_SRV, else no evidence.
Output: posterior P(near wins) for each point, and the game ends.
Usage: python match_decode.py
"""
import json, os
import numpy as np
import lab
import winner_ball as wb
import shot_chain as sc
from score_decode import P_DEC
from serve_ball import serve_events

P_SRV, EPS = 0.95, 0.02
P_ID = 0.85          # near-player identity from the torso color (E11, raw cluster of each point)
FLIP = None         # set by main: flip[i] = E11 says that the near player changed before point i (True / False / None)
FL_HIT, FL_FALSE = 0.9, 0.02   # P(flip seen | ends changed), P(flip seen | no change)
EPS_NULL = 0.0      # a marked point that is not a point (let, outcome NONE): the score does not change
CALIBRATED = True


# Probability that the rule decision is right, by case type (shot_chain.old_case_key; measured on the 4 singles matches,
# E16c, T_CUT -1.5)
CASE_P = {"net_early": 0.77, "net": 0.95, "in_seen": 0.72, "in_early": 0.65, "in": 0.57, "out_early": 0.55, "out_seen": 0.90, "out": 0.88}
CASES_BY_MATCH = None      # E16e: {match: table} with tables measured on the other matches (fit_cases)


def case_prob(q, p, mid=None):
    t = CASES_BY_MATCH[mid] if (CASES_BY_MATCH and mid in CASES_BY_MATCH) else CASE_P
    return t[sc.old_case_key(q, p)]


def fit_cases(mids):
    """Case table from the marked points of the given matches (2 pseudo counts at P_DEC for each case)."""
    cnt = {k: [0.0, 0.0] for k in CASE_P}
    for mid in lab.check_train(mids):
        p = PARAMS[mid]
        pts = sc.lab_points(mid)
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        for pt, e in zip(pts, ends):
            q, w, case = sc.old_rule(pt, wb.T_CUT, p)
            if w is None or e["winnerNear"] is None:
                continue
            c = cnt[case]
            c[0] += 1; c[1] += w == e["winnerNear"]
    return {k: float(np.clip((c[1] + 2 * P_DEC) / (c[0] + 2), 0.5, 0.97)) for k, c in cnt.items()}


def use_lomo_cases():
    """E16e: for each match a case table measured on the other 3 matches."""
    global CASES_BY_MATCH
    CASES_BY_MATCH = {m: fit_cases([x for x in wb.SINGLES if x != m]) for m in wb.SINGLES}


PARAMS = {"m1015": {'margin': -0.15, 'net_fwd': 2.0, 'net_back': 4.0, 'min_speed': 8},     # LOMO, E16c (T_CUT -1.5)
          "m1020": {'margin': -0.15, 'net_fwd': 2.0, 'net_back': 4.0, 'min_speed': 8},
          "m1021": {'margin': -0.15, 'net_fwd': 2.0, 'net_back': 4.0, 'min_speed': 8},
          "m1030": {'margin': 0.0, 'net_fwd': 2.0, 'net_back': 4.0, 'min_speed': 8}}    # winner_sweep.py, LOMO, with court_track.json (2026-11-22)


def step(s, p):
    """State after a point won by player p (1 or 2). s = (g1, g2, a, b, k, srv, near1, tbf); tb = k >= 0."""
    g1, g2, a, b, k, srv, near1, tbf = s
    a, b = (a + 1, b) if p == 1 else (a, b + 1)
    if k < 0:                                            # normal game
        if a >= 3 and b >= 3 and abs(a - b) < 2:        # deuce (3, 3) or advantage (4, 3) / (3, 4)
            a, b = (3 + (a > b), 3 + (b > a))
        if max(a, b) >= 4 and abs(a - b) >= 2:
            g1, g2 = (g1 + 1, g2) if a > b else (g1, g2 + 1)
            srv = 3 - srv
            tot = g1 + g2
            set_end = max(g1, g2) >= 6 and abs(g1 - g2) >= 2
            if tot % 2 == 1:
                near1 = not near1
            if set_end:
                g1 = g2 = 0
            if g1 == 6 and g2 == 6:
                return (g1, g2, 0, 0, 0, srv, near1, srv)
            return (g1, g2, 0, 0, -1, srv, near1, 0)
        return (g1, g2, a, b, -1, srv, near1, tbf)
    k = (k + 1) % 12                                     # tiebreak
    if a >= 6 and b >= 6 and abs(a - b) < 2:            # 6-6 or a one-point lead after 6-6
        a, b = (6 + (a > b), 6 + (b > a))
    if max(a, b) >= 7 and abs(a - b) >= 2:               # set won 7-6: 13 games, ends change
        return (0, 0, 0, 0, -1, 3 - tbf, not near1, 0)
    if k % 6 == 0:
        near1 = not near1
    srv = tbf if ((k + 1) // 2) % 2 == 0 else 3 - tbf
    return (g1, g2, a, b, k, srv, near1, tbf)


def emit(s, p, pw, ps):
    pn = None
    if isinstance(ps, tuple):                    # (server evidence, near-player color cluster of E11)
        ps, pn = ps
    near_wins = (p == 1) == s[6]
    e = pw if near_wins else 1 - pw
    if ps in ("P1", "P2"):                       # server identity (upper-limit test with the marks)
        e *= P_SRV if s[5] == int(ps[1]) else 1 - P_SRV
    elif ps is not None:                         # server end: True = near
        srv_near = (s[5] == 1) == s[6]
        e *= P_SRV if srv_near == ps else 1 - P_SRV
    if pn is not None and pn >= 0:               # cluster 0 = decoder player 1 (a free convention)
        e *= P_ID if s[6] == (pn == 0) else 1 - P_ID
    return e


def trans(i, s, t):
    """Evidence for the step from state s (before point i) to t (before point i + 1): the color flip of E11."""
    if FLIP is None or i + 1 >= len(FLIP) or FLIP[i + 1] is None:
        return 1.0
    p = FL_HIT if t[6] != s[6] else FL_FALSE
    return p if FLIP[i + 1] else 1 - p


def decode(pw, ps, start, states=None, eps=None, null=None):
    """states: an empty list. The function fills it with the most probable state before each point.
    eps, null: optional lists, one value for each point: the probability of a missed point before
    point i, and the probability that point i is not a point (E21). Default: EPS and EPS_NULL."""
    n = len(pw)
    eps = [EPS] * n if eps is None else eps
    null = [EPS_NULL] * n if null is None else null
    alpha = [start]
    alpha_s = []
    for i in range(n):
        a0 = alpha[-1]
        mix = {s: (1 - eps[i]) * w for s, w in a0.items()}
        for s, w in a0.items():
            for p in (1, 2):
                t = step(s, p)
                mix[t] = mix.get(t, 0.0) + eps[i] * 0.5 * w
        alpha_s.append(mix)
        nx = {}
        for s, w in mix.items():
            for p in (1, 2):
                t = step(s, p)
                nx[t] = nx.get(t, 0.0) + (1 - null[i]) * w * emit(s, p, pw[i], ps[i]) * trans(i, s, t)
            if null[i]:
                nx[s] = nx.get(s, 0.0) + null[i] * w * emit(s, 1, 0.5, ps[i]) * trans(i, s, s)
        z = sum(nx.values())
        alpha.append({s: w / z for s, w in nx.items() if w / z > 1e-9})
    beta = {s: 1.0 for s in alpha[-1]}
    post = [0.0] * n
    for i in range(n - 1, -1, -1):
        num = den = 0.0
        bs = {}
        for s, w in alpha_s[i].items():
            v = 0.0
            for p in (1, 2):
                t = step(s, p)
                c = emit(s, p, pw[i], ps[i]) * trans(i, s, t) * beta.get(t, 0.0)
                v += c
                if (p == 1) == s[6]:
                    num += w * c
            den += w * v
            bs[s] = (1 - null[i]) * v + (null[i] * emit(s, 1, 0.5, ps[i]) * trans(i, s, s) * beta.get(s, 0.0) if null[i] else 0.0)
        post[i] = num / den if den > 0 else 0.5
        if states is not None:
            states.insert(0, max(alpha_s[i], key=lambda u: alpha_s[i][u] * bs.get(u, 0.0)))
        nb = {}
        for s in alpha[i]:
            v = (1 - eps[i]) * bs.get(s, 0.0)
            for p in (1, 2):
                v += eps[i] * 0.5 * bs.get(step(s, p), 0.0)
            nb[s] = v
        m = max(nb.values()) or 1.0
        beta = {s: v / m for s, v in nb.items()}
    return post


def replay(post, states, skip=None):
    """A consistent score: starts at the most probable first state and applies the decided winner
    of each point (post > 0.5 = the near player wins) with the tennis rules. skip[i] = True: the
    rally is not a point. Returns the state before each point. The most probable states of
    decode() are not a consistent path (each is a separate maximum)."""
    out, s = [], states[0]
    for i, pp in enumerate(post):
        out.append(s)
        if skip is None or not skip[i]:
            s = step(s, 1 if (pp > 0.5) == s[6] else 2)
    return out


def main(use_server=True, use_id=False, collect=None, quiet=False):
    """collect: an optional dict. The function adds for each match the evidence, the posterior, and the states."""
    tot = {"ball": 0.0, "dec": 0.0, "n": 0}
    for mid in wb.SINGLES:
        p = PARAMS[mid]
        pts = sc.lab_points(mid)
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
        pw = []
        for pt in pts:
            q, w, _ = sc.old_rule(pt, wb.T_CUT, p)
            c = case_prob(q, p, mid) if (CALIBRATED and w is not None) else P_DEC
            pw.append(0.5 if w is None else (c if w else 1 - c))
        ps = []
        for pt, e, sd in zip(pts, ends, sides):
            if use_server == "true_end":
                ps.append(sd["server"] == e["nearPlayer"]); continue
            if use_server == "true_id":
                ps.append(sd["server"]); continue
            v = e["serverNearVision"] if use_server in (True, "pose", "both") else None
            if use_server in ("ball", "both"):
                ev = serve_events(pt["flights"], pt["start"] - 1.0, pt["start"] + 2.5)
                if ev:
                    v = ev[0][1]                     # the ball serve event wins over the pose vote
            ps.append(v)
        global FLIP
        FLIP = None
        if use_id:
            ni = json.load(open(os.path.join(lab.mdir(mid), "near_identity.json")))["points"]
            if use_id == "flip":
                FLIP = [r["flip"] for r in ni]
            else:
                ps = [(v, r["rawCluster"]) for v, r in zip(ps, ni)]
        sb = sides[0]["scoreBefore"]
        g1, g2 = sb["games"]; a, b = sb["rawPoints"]
        start = {}
        for srv in (1, 2):
            for near1 in (True, False):
                start[(g1, g2, a, b, -1, srv, near1, 0)] = 0.25
        states = []
        post = decode(pw, ps, start, states)
        if collect is not None:
            collect[mid] = {"pw": pw, "post": post, "states": states, "ps": ps}
        ball = dec = n = 0.0
        for e, q, r in zip(ends, pw, post):
            if e["winnerNear"] is None:
                continue
            n += 1
            ball += 0.5 if q == 0.5 else float((q > 0.5) == e["winnerNear"])
            dec += float((r > 0.5) == e["winnerNear"])
        tot["ball"] += ball; tot["dec"] += dec; tot["n"] += n
        if not quiet:
            print(f"{mid}: points {int(n)}, server evidence on {sum(x is not None for x in ps)}, ball rule {100 * ball / n:.1f}%, decoder {100 * dec / n:.1f}%")
    if not quiet:
        print(f"ALL (server evidence: {use_server}, near color: {use_id}): ball rule {100 * tot['ball'] / tot['n']:.1f}%, decoder {100 * tot['dec'] / tot['n']:.1f}%")


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1:
        EPS_NULL = float(sys.argv[1])
    for u in (False, "pose", "ball", "both", "true_end", "true_id"):
        main(u)
    for u in (False, "pose", "ball", "both"):
        main(u, True)
    for u in (False, "pose", "ball", "both"):
        main(u, "flip")

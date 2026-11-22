"""E21: end-to-end prototype. No marks are used for the analysis (only for the score at the
start of the marked part and for the evaluation).

Steps for one singles match:
  1. rallies: the rally GRU probabilities (seq_eval.py run with the ball flight features,
     leave one match out) -> segments;
  2. ball: 3D flights of the full match (ball_full.py);
  3. for each rally: winner evidence (ball rule, E16), server end (pose vote E12, ball serve
     event E9), end change (shirt color flip, E11), first-serve fault candidate (E10);
  4. score decoder (match_decode.py) over the detected rallies.
Output: <match>/pipeline.json and an evaluation against the marks.
Usage: python pipeline.py [match id ...]
"""
import json, os, sys
import numpy as np
import cv2
import lab
import winner_ball as wb
import winner_sweep as ws
import match_decode as md
from ball_full import WINDOWS
from serve_ball import serve_events
from server_end import near_far, wrist_up
from near_identity import torso_color, flips

RUN = "e20_wasbf"          # audio + mel + DINO + ball flight features, leave one match out (E20)


def p_null(prob, shots):
    """Probability that a detected rally is not a point, from the mean GRU probability and the
    count of tracked shots (measured on the 4 singles matches, E21)."""
    base = 0.5 if prob < 0.6 else 0.33 if prob < 0.75 else 0.15 if prob < 0.85 else 0.05 if prob < 0.93 else 0.01
    return min(0.7, base * (1.6 if shots < 2 else 1.0 if shots < 4 else 0.6))


def p_miss(gap):
    """Probability of a missed point in the gap (s) before a detected rally (E21)."""
    return 0.02 if gap < 15 else 0.05 if gap < 25 else 0.2 if gap < 40 else 0.4

MIN_GAP = 2.0
PROB = {}
SHORT_WAIT, P_NULL_SHORT = 12.0, 0.5
T_CUT_E2E = -2.5      # time cut of the winner rule for a detected end (E21b): the end is less exact than a mark (-1.5 there)
ID_ABS = 0.97         # E22b: probability that the color identity of a rally is right (None = use the local flips, E21)
END_RULE = None       # optional function (start, end, flights) -> end time for the winner rule


def rallies(mid, serves, p=None, hi=0.5, lo=0.35, short=True):
    """Rally segments from the GRU probabilities. A test (E21) moved the start to a ball serve
    event near it: the start error got larger (0.42 -> 0.6 s), so the start stays as it is.
    E21b: the GRU misses short points (ace, serve winner, double fault: 4 to 7 s in the marks).
    A ball serve event outside the segments with no other serve event and no segment start in
    the SHORT_WAIT s after it is added as a short point (time - 0.5 s to time + 4.5 s). The decoder
    gets P_NULL_SHORT for it (about half of these are real points)."""
    if p is None:
        p = np.load(os.path.join(lab.ROOT, "runs", RUN, f"{mid}_prob.npy"))
    start, dur = WINDOWS[mid]
    out = []
    for a, b in lab.segment(p, smooth=1, hi=hi, lo=lo, min_len=2.0):
        if a >= start and b <= start + dur:
            PROB[(mid, float(b))] = float(p[int(a):int(b) + 1].mean())
            out.append((float(a), float(b)))
    if SHORT_WAIT is None or not short:
        return out
    ts = [e[0] for e in serves]
    add = []
    for t in ts:
        if any(a - 3 <= t <= b + 2 for a, b in out):
            continue
        nxt = min([x for x in ts if x > t + 0.5] + [a for a, _ in out if a > t] + [1e18])
        if nxt - t >= SHORT_WAIT:
            if add and t - add[-1] < 6:
                add[-1] = t
            else:
                add.append(t)
    for t in add:
        PROB[(mid, float(t + 4.5))] = -1.0             # mark: a short point from a serve event
        out.append((float(t - 0.5), float(t + 4.5)))
    return sorted(out)


def evidence(mid, seg, full, serves):
    out = lab.mdir(mid)
    pose = np.load(os.path.join(out, "pose.npy"))
    pose = pose[np.argsort(pose[:, 0], kind="stable")]
    n = lab.n_seconds(mid)
    st = np.searchsorted(pose[:, 0], np.arange(n + 1))
    fl_t0 = np.array([f["t0"] for f in full["flights"]])
    kk_t = np.array([k[0] for k in full["kinks"]])
    p = md.PARAMS[mid]
    rows, feats, prev_end = [], [], -1e9
    cp = os.path.join(out, "color_cache.json")
    cache = json.load(open(cp)) if os.path.exists(cp) else {}
    dirty = False
    for (a, b) in seg:
        fls = [full["flights"][i] for i in np.nonzero((fl_t0 >= a - 1.0) & (fl_t0 <= b + 2.5))[0]]
        kks = [full["kinks"][i] for i in np.nonzero((kk_t >= a - 1.0) & (kk_t <= b + 2.5))[0]]
        t_end = END_RULE(a, b, fls) if END_RULE else b
        pt = {"start": a, "end": t_end, "flights": fls, "kinks": kks, "coverage": 0.0}
        orig = wb.good
        wb.good = lambda f, orig=orig: orig(f) and 3.6 * float(np.linalg.norm(f["v0"])) >= p["min_speed"]
        t_cut, wb.T_CUT = wb.T_CUT, T_CUT_E2E
        q = wb.analyse(pt)
        wb.good, wb.T_CUT = orig, t_cut
        w = ws.decide(q, p)
        pw = 0.5 if w is None else (md.case_prob(q, p, mid) if w else 1 - md.case_prob(q, p, mid))
        # server end: pose vote, then the ball serve event
        nu, fu = [], []
        for s in range(max(0, int(a) - 1), min(n, int(a) + 3)):
            r, fr = near_far(pose[st[s]:st[s + 1]])
            if r is not None:
                nu.append(wrist_up(r))
            if fr is not None:
                fu.append(wrist_up(fr))
        nmax = np.nanmax(nu) if np.isfinite(nu).any() else -9
        fmax = np.nanmax(fu) if np.isfinite(fu).any() else -9
        srv = None if max(nmax, fmax) < 0.5 else bool(nmax > fmax)
        ev = [e for e in serves if a - 1.0 <= e[0] <= a + 2.5]
        speed = None
        if ev:
            srv = ev[0][1]
            if ev[0][2] != "toss":
                speed = 3.6 * float(np.linalg.norm(ev[0][3]["v0"]))
        # fault candidate: the last serve event from the server end before this rally
        fault = None
        if srv is not None:
            c = [e for e in serves if max(prev_end + 1.0, a - 22.0) <= e[0] <= a - MIN_GAP and e[1] == srv]
            if c:
                fault = {"t": c[-1][0], "kind": c[-1][2], "speed": 3.6 * float(np.linalg.norm(c[-1][3]["v0"]))}
        # shirt color: near minus far (cached: the image reads are the slow part)
        key = f"{int(a)}_{int(b)}"
        if key not in cache:
            cols = []
            for s in range(int(a) + 1, min(n, int(b))):
                r, fr = near_far(pose[st[s]:st[s + 1]])
                if r is None or fr is None:
                    continue
                img = cv2.imread(os.path.join(out, "kf", f"{s + 1:05d}.jpg"))
                c1, c2 = torso_color(img, r), torso_color(img, fr)
                if c1 is not None and c2 is not None:
                    cols.append((c1 - c2).tolist())
            cache[key] = cols
            dirty = True
        cols = [np.array(c) for c in cache[key]]
        feats.append(np.median(cols, 0) if len(cols) >= 2 else None)
        last = None
        if q is not None:
            last = {"toFar": bool(q["to_far"]), "speed": q["speed"], "src": int(q["src"]),
                    "margin": None if np.isnan(q["margin"]) else q["margin"],
                    "bx": None if np.isnan(q["bx"]) else q["bx"], "by": None if np.isnan(q["by"]) else q["by"], "tRel": q["t_rel"]}
        rows.append({"start": a, "end": b, "prob": PROB[(mid, b)], "pBall": pw, "serverNear": srv, "serveSpeed": speed, "fault": fault, "lastShot": last,
                     "shots": int(sum(wb.good(f) and a <= f["t0"] <= b for f in fls))})
        prev_end = b
    if dirty:
        json.dump(cache, open(cp, "w"))
    for r, f, c in zip(rows, flips(feats), feats):
        r["flip"] = f
        r["color"] = None if c is None else [float(x) for x in c]      # for identity.py (E22)
    return rows


def run(mid, quiet=False):
    start, dur = WINDOWS[mid]
    full = json.load(open(os.path.join(lab.mdir(mid), "fullflights_s0.35.json")))
    serves = serve_events(full["flights"], start, start + dur)
    seg = rallies(mid, serves)
    rows = evidence(mid, seg, full, serves)
    sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
    sb = sides[0]["scoreBefore"]
    g1, g2 = sb["games"]; a0, b0 = sb["rawPoints"]
    st0 = {(g1, g2, a0, b0, -1, srv, near1, 0): 0.25 for srv in (1, 2) for near1 in (True, False)}
    md.FLIP = [r["flip"] for r in rows]
    states = []
    for i, r in enumerate(rows):
        r["pNull"] = P_NULL_SHORT if r["prob"] < 0 else p_null(r["prob"], r["shots"])
        r["pMiss"] = p_miss(r["start"] - rows[i - 1]["end"]) if i else 0.02
    ps = [r["serverNear"] for r in rows]
    if ID_ABS:                                    # E22b: the near player of each rally from the color identity
        import identity
        near1 = identity.p1_near([r["color"] for r in rows])
        md.FLIP, md.P_ID = None, ID_ABS
        ps = [(s, 0 if n1 else 1) for s, n1 in zip(ps, near1)]
    post = md.decode([r["pBall"] for r in rows], ps, st0, states,
                     [r["pMiss"] for r in rows], [r["pNull"] for r in rows])
    for r, pp, s in zip(rows, post, states):
        r["pNearWins"] = pp
        r["state"] = {"games": [s[0], s[1]], "points": [s[2], s[3]], "tiebreak": s[4] >= 0, "server": s[5], "p1Near": bool(s[6])}
    # evaluation against the marks
    gt = lab.gt_points(mid)
    ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
    pairs = lab.match_points([(r["start"], r["end"]) for r in rows], gt)
    n = ok = okb = dec = 0
    for gi, j in pairs:
        rows[j]["gt"] = gi
        wn = ends[gi]["winnerNear"]
        rows[j]["gtWinnerNear"] = wn
        if wn is None:
            continue
        n += 1
        ok += (rows[j]["pNearWins"] > 0.5) == wn
        if rows[j]["pBall"] != 0.5:
            dec += 1; okb += (rows[j]["pBall"] > 0.5) == wn
    known = sum(e["winnerNear"] is not None for e in ends)
    ds = np.array([rows[j]["start"] - gt[gi][0] for gi, j in pairs]); de = np.array([rows[j]["end"] - gt[gi][1] for gi, j in pairs])
    m = {"match": mid, "marked": len(gt), "detected": len(rows), "matched": len(pairs), "recall": len(pairs) / len(gt),
         "precision": len(pairs) / max(1, len(rows)), "startAbsMed": float(np.median(np.abs(ds))), "endAbsMed": float(np.median(np.abs(de))),
         "winnerMatched": ok / max(1, n), "winnerBallDecided": okb / max(1, dec), "ballDecided": dec / max(1, n),
         "winnerEndToEnd": ok / max(1, known), "n": n, "ok": int(ok), "known": known,
         "faultCandidates": sum(r["fault"] is not None for r in rows)}
    json.dump({"metrics": m, "rallies": rows}, open(os.path.join(lab.mdir(mid), "pipeline.json"), "w"), indent=1, default=float)
    if not quiet:
        print(f"{mid}: rallies {m['detected']} (marked {m['marked']}), recall {100 * m['recall']:.1f}%, precision {100 * m['precision']:.1f}%, "
              f"start |med| {m['startAbsMed']:.2f} s, end |med| {m['endAbsMed']:.2f} s | winner right on matched points {100 * m['winnerMatched']:.1f}% "
              f"(ball rule alone {100 * m['winnerBallDecided']:.1f}% of {100 * m['ballDecided']:.0f}% decided) | end to end "
              f"{100 * m['winnerEndToEnd']:.1f}% of the marked points | fault candidates {m['faultCandidates']}", flush=True)
    return m


if __name__ == "__main__":
    md.use_lomo_cases()                       # winner probabilities measured on the other 3 matches
    ms = [run(m) for m in (sys.argv[1:] or list(WINDOWS))]
    if len(ms) > 1:
        n, ok, kn = (sum(m[k] for m in ms) for k in ("n", "ok", "known"))
        print(f"ALL: recall {100 * sum(m['matched'] for m in ms) / sum(m['marked'] for m in ms):.1f}%, precision "
              f"{100 * sum(m['matched'] for m in ms) / sum(m['detected'] for m in ms):.1f}%, winner on matched points {100 * ok / n:.1f}%, "
              f"end to end {100 * ok / kn:.1f}%")

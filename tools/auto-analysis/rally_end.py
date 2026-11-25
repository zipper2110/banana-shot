"""E60: the rally end detector (chain-inventory G4, plan E21c).

The rally GRU gives the end in steps of 1 s, and its end includes the reaction time of the players (it learned the
marked end). The winner evidence takes the last shot that starts before end + T_CUT (chain.T_CUT_E2E = -2.5 s).
Thus a small error of the end can remove the true last shot, or let ball handling after the point in.

This detector finds the last rally shot directly. Candidates: the shots of the shot chain (shot_chain.build) that
start from 8 s before to 4 s after the GRU end. A logistic model gives for each candidate the probability that it is
a rally shot (not ball handling after the point). Inputs: the time to the GRU end, the GRU probability, the audio hit
at the start of the shot (hits.npy), the end of the shot before (net, out, own side), the duration of the shot and
the time tracked after its bounce, the start place of the ball. The last rally shot is the split of the candidates
with the highest likelihood (rally shots first, then ball handling). Then the end for the winner evidence moves
from the GRU end as little as possible, so that end + T_CUT is after the last rally shot and before the next shot.

Reference (truth) of the fit: the last shot that the marked end selects with the same cut. The marked end has the
reaction time in it (decision 2026-11-23), but the cut removes that time: the evidence with the marked end is the
target of G4 (E28c, E60).

The coefficients are in rally_end.json: "full" (all 4 lab matches) and one fold model for each lab match (the other
3 matches), for the lab benchmark (use_lomo(), pipeline.py).
Usage: python rally_end.py fit
"""
import json, os, sys
import numpy as np
import shot_chain as sc

HERE = os.path.dirname(os.path.abspath(__file__))
PATH = os.path.join(HERE, "rally_end.json")
LO, HI = -8.0, 4.0         # s: the candidates start this much before or after the GRU end
DELTA = 0.1                # s: the least distance of end + T_CUT from a shot start
FEATURES = ["dt_end", "gap_prev", "n_after", "z0", "y0", "dur", "weak", "hit_db", "p_next", "after_bounce",
            "pk_net", "pk_own", "pk_out", "pk_start"]
_model = None
FOLDS = False


def use_lomo():
    """The lab benchmark: each lab match gets the fold model that did not see it."""
    global FOLDS
    FOLDS = True


def model(mid=None):
    global _model
    if _model is None:
        _model = json.load(open(PATH)) if os.path.exists(PATH) else {}
    if FOLDS and mid in _model.get("folds", {}):
        return _model["folds"][mid]
    return _model.get("full")


def start(s):
    return s["fl"][0]["t0"] if s["fl"] else s["t"]


def shots_near(full, nets, a, b):
    """The shot chain of the window from a - 1 s to b + HI (the window of the winner evidence, longer at the end)."""
    fl = [f for f in full["flights"] if a - 1.0 <= f["t0"] <= b + HI]
    return sc.build(fl, full.get("events"), [e for e in nets if a - 1.0 <= e["t"] <= b + HI])


def features(shots, a, b, prob, hits):
    """[(shot start, {feature: value})] for the candidate shots of the rally (a, b)."""
    out = []
    for i, s in enumerate(shots):
        t = start(s)
        if not max(a, b + LO) <= t <= b + HI:
            continue
        pk = sc.shot_end(shots[i - 1])[0] if i else "start"
        f0 = s["fl"][0] if s["fl"] else None
        hh = hits[(hits[:, 0] >= t - 0.3) & (hits[:, 0] <= t + 0.1)] if len(hits) else hits
        si = int(min(len(prob) - 1, max(0, t)))
        x = {"dt_end": t - b, "gap_prev": t - start(shots[i - 1]) if i else 9.0,
             "n_after": sum(1 for z in shots[i + 1:] if start(z) <= b + HI),
             "z0": f0["p0"][2] if f0 else np.nan, "y0": abs(f0["p0"][1]) if f0 else np.nan,
             "dur": (s["fl"][-1]["t1"] - t) if s["fl"] else 0.0, "weak": float(bool(s.get("weak"))),
             "hit_db": float(hh[:, 1].max()) if len(hh) else 0.0,
             "p_next": float(prob[min(len(prob) - 1, si + 2)]), "after_bounce": sc.after_bounce(s)}
        for k in ("net", "own", "out", "start"):
            x["pk_" + k] = float(pk == k)
        out.append((t, x))
    return out


def p_rally(m, cands):
    """The probability of each candidate that it is a rally shot."""
    X = np.array([[x[k] for k in FEATURES] for _, x in cands], float)
    X = np.where(np.isnan(X), np.array(m["fill"]), X)
    z = ((X - np.array(m["mean"])) / np.array(m["scale"])) @ np.array(m["coef"]) + m["intercept"]
    return 1.0 / (1.0 + np.exp(-z))


def last_rally_shot(m, cands):
    """The start of the last rally shot: the split of the candidates (rally shots, then ball handling) with the
    highest likelihood. None: no candidate is a rally shot."""
    if not cands:
        return None
    p = np.clip(p_rally(m, cands), 1e-4, 1 - 1e-4)
    ll = [np.log(p[:k + 1]).sum() + np.log(1 - p[k + 1:]).sum() for k in range(-1, len(cands))]
    k = int(np.argmax(ll)) - 1
    return None if k < 0 else cands[k][0]


def end_for(shots, b, t_last, t_cut):
    """The end for the winner evidence: the GRU end b, moved as little as possible so that end + t_cut is after
    t_last + DELTA and before the next shot - DELTA (the middle when the two shots are nearer than that)."""
    if t_last is None:
        return b
    nxt = [start(s) for s in shots if start(s) > t_last + 1e-6]
    lo = t_last - t_cut + DELTA
    hi = min(nxt) - t_cut - DELTA if nxt else 1e18
    if hi < lo:
        return (t_last + min(nxt)) / 2 - t_cut
    return min(max(b, lo), hi)


def end(mid, a, b, full, nets, prob, hits, t_cut):
    """The end of the rally (a, b) for the winner evidence. b when the model or the ball events are missing."""
    m = model(mid)
    if m is None or prob is None or "events" not in full:
        return b
    shots = shots_near(full, nets, a, b)
    return end_for(shots, b, last_rally_shot(m, features(shots, a, b, prob, hits)), t_cut)


def load_hits(folder):
    p = os.path.join(folder, "hits.npy")
    return np.load(p) if os.path.exists(p) else np.zeros((0, 6), np.float32)


# ---------------------------------------------------------------- fit on the lab matches

def rows(mid, t_cut):
    """Training rows of a lab match: the candidates of each detected rally that matches a marked point, with the label
    1 = the shot starts not later than the last shot that the marked end selects with the cut t_cut."""
    import lab, chain, rally_model
    from ball_full import WINDOWS
    full = json.load(open(os.path.join(lab.mdir(mid), "fullflights_s0.35_ev.json")))
    nets = json.load(open(os.path.join(lab.mdir(mid), "net_events.json")))["events"]
    hits = load_hits(lab.mdir(mid))
    prob = rally_model.predict(mid, "ball", fold=mid)          # held-out GRU probabilities, as on a new video
    s0, dur = WINDOWS[mid]
    seg, _ = chain.segments(prob, full, {"threshold": 0.5, "shortPoints": True}, s0, s0 + dur)
    gt = lab.gt_points(mid)
    X, Y = [], []
    for gi, j in lab.match_points([(a, b) for a, b, _ in seg], gt):
        a, b, _ = seg[j]
        ga, gb, _ = gt[gi]
        rs = shots_near(full, nets, ga, gb)
        li = sc.last_shot(rs, gb, t_cut)
        if li is None:
            continue
        ref = start(rs[li])
        c = features(shots_near(full, nets, a, b), a, b, prob, hits)
        if not any(abs(t - ref) < 1e-6 for t, _ in c):
            continue
        for t, x in c:
            X.append([x[k] for k in FEATURES])
            Y.append(int(t <= ref + 1e-6))
    return np.array(X, float), np.array(Y)


def fit_lr(X, Y):
    from sklearn.linear_model import LogisticRegression
    fill = np.nanmean(X, 0)
    X = np.where(np.isnan(X), fill, X)
    mean, scale = X.mean(0), X.std(0) + 1e-9
    lr = LogisticRegression(C=1.0, max_iter=2000).fit((X - mean) / scale, Y)
    r = lambda v: [round(float(z), 5) for z in v]
    return {"features": FEATURES, "fill": r(fill), "mean": r(mean), "scale": r(scale), "coef": r(lr.coef_[0]),
            "intercept": round(float(lr.intercept_[0]), 5), "rows": int(len(Y))}


def fit():
    import chain, lab
    ms = lab.check_train(["m1015", "m1020", "m1021", "m1030"])
    data = {m: rows(m, chain.T_CUT_E2E) for m in ms}
    cat = lambda xs: (np.concatenate([data[m][0] for m in xs]), np.concatenate([data[m][1] for m in xs]))
    out = {"note": "E60 rally end detector (rally_end.py fit)", "full": fit_lr(*cat(ms)),
           "folds": {m: fit_lr(*cat([x for x in ms if x != m])) for m in ms}}
    json.dump(out, open(PATH, "w"), indent=1)
    print("rows", {m: len(data[m][1]) for m in ms}, "->", PATH)


if __name__ == "__main__":
    if sys.argv[1:] == ["fit"]:
        fit()

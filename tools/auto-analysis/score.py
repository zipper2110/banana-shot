"""Scores the rally rows of the detection chain (chain.py; the "rallies" of result.json) against the marks of a
lab match: <match>/gt.json (the marked points) and <match>/ends.json (the winner end of each marked point).
score() adds "gt" (index of the marked point) and "gtWinnerNear" to each matched row.
"""
import json, os
import numpy as np
import lab


def score(mid, rows):
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
    return {"match": mid, "marked": len(gt), "detected": len(rows), "matched": len(pairs), "recall": len(pairs) / len(gt),
            "precision": len(pairs) / max(1, len(rows)), "startAbsMed": float(np.median(np.abs(ds))), "endAbsMed": float(np.median(np.abs(de))),
            "winnerMatched": ok / max(1, n), "winnerBallDecided": okb / max(1, dec), "ballDecided": dec / max(1, n),
            "winnerEndToEnd": ok / max(1, known), "n": n, "ok": int(ok), "known": known,
            "faultCandidates": sum(r.get("fault") is not None for r in rows)}


def line(m):
    return (f"{m['match']}: rallies {m['detected']} (marked {m['marked']}), recall {100 * m['recall']:.1f}%, precision {100 * m['precision']:.1f}%, "
            f"start |med| {m['startAbsMed']:.2f} s, end |med| {m['endAbsMed']:.2f} s | winner right on matched points {100 * m['winnerMatched']:.1f}% "
            f"(ball rule alone {100 * m['winnerBallDecided']:.1f}% of {100 * m['ballDecided']:.0f}% decided) | end to end "
            f"{100 * m['winnerEndToEnd']:.1f}% of the marked points | fault candidates {m['faultCandidates']}")


def total(ms):
    n, ok, kn = (sum(m[k] for m in ms) for k in ("n", "ok", "known"))
    return (f"ALL: recall {100 * sum(m['matched'] for m in ms) / sum(m['marked'] for m in ms):.1f}%, precision "
            f"{100 * sum(m['matched'] for m in ms) / sum(m['detected'] for m in ms):.1f}%, winner on matched points {100 * ok / n:.1f}%, "
            f"end to end {100 * ok / kn:.1f}%")

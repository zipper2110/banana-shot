"""E10: first-serve fault candidates from the ball, before each marked point.

The marks start at the serve that was in. A first-serve fault comes before
that serve: a serve event from the same server end in the window before the
point (ball_wasb.py pre, ball_3d.py pre -> preflights_s0.35.json).
For each point:
  - server end: from the rules (sides.json + ends.json) for this test;
  - candidate = the last serve event (serve_ball.serve_events) from the
    server end in the window, at least MIN_GAP s before the point start;
  - landing of the candidate (in the box, out, net, unknown).
Writes <match>/faults.json and prints the share of points with a candidate.
Usage: python fault_ball.py
"""
import json, os
import numpy as np
import lab
import winner_ball as wb
from serve_ball import serve_events, landing, box_margin

MIN_GAP = 2.0


def main():
    tot = [0, 0]
    for mid in wb.SINGLES:
        p = os.path.join(lab.mdir(mid), f"preflights_s{lab.BALL_TAG}.json")
        if not os.path.exists(p):
            continue
        pre = json.load(open(p))
        ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
        sides = json.load(open(os.path.join(lab.mdir(mid), "sides.json")))
        gt = lab.gt_points(mid)
        out, n, cand, kinds, lands = [], 0, 0, {}, {}
        for i, (w, e, sd) in enumerate(zip(pre, ends, sides)):
            n += 1
            srv_near = sd["server"] == e["nearPlayer"]
            ev = [x for x in serve_events(w["flights"], w["w0"], gt[i][0] - MIN_GAP) if x[1] == srv_near]
            rec = {"point": i, "start": gt[i][0], "w0": w["w0"], "serverNear": srv_near, "candidate": None}
            if ev:
                t, near, kind, f = ev[-1]
                x, y, src = landing(f, w["kinks"])
                m = box_margin(x, y, srv_near, sd["serveSide"]) if not np.isnan(x) else np.nan
                res = "unknown" if np.isnan(m) else ("in" if m > -0.1 else "out")
                rec["candidate"] = {"t": t, "kind": kind, "speed": 3.6 * float(np.linalg.norm(f["v0"])), "land": [x, y], "landSrc": src, "margin": m, "result": res}
                cand += 1
                kinds[kind] = kinds.get(kind, 0) + 1
                lands[res] = lands.get(res, 0) + 1
            out.append(rec)
        json.dump(out, open(os.path.join(lab.mdir(mid), "faults.json"), "w"), default=float, indent=0)
        tot = [tot[0] + n, tot[1] + cand]
        print(f"{mid}: points {n}, fault candidates {cand} ({100 * cand / n:.0f}%), kinds {kinds}, landing {lands}")
    if tot[0]:
        print(f"ALL: fault candidates at {100 * tot[1] / tot[0]:.0f}% of the points")


if __name__ == "__main__":
    main()

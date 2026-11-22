"""E20: serve events over the full match (no marks used for the detection).

Input: fullflights_s0.35.json (ball_full.py). Events: serve_ball.serve_events.
A "start event" is a serve event with no good flight over the net in the QUIET s before it.
Measures against the marks:
  - recall: share of the marked point starts with an event in [start - 1, start + 2.5];
  - where each event is: at a point start, later in a point, in the gap before a point
    (first-serve fault, let, or practice), or other.
Usage: python serve_full.py [quiet_s]
"""
import json, os, sys
import numpy as np
import lab
from ball_full import WINDOWS
from serve_ball import serve_events

QUIET = float(sys.argv[1]) if len(sys.argv) > 1 else 4.0


def load(mid):
    full = json.load(open(os.path.join(lab.mdir(mid), "fullflights_s0.35.json")))
    start, dur = WINDOWS[mid]
    fl = full["flights"]
    ev = serve_events(fl, start, start + dur)
    over = np.array(sorted(f["t1"] for f in fl if f["n"] >= 8 and f["rms"] < 5 and f["p0"][1] * f["p1"][1] < 0
                           and 3.6 * np.linalg.norm(f["v0"]) > 30))
    out = []
    for t, near, kind, f in ev:
        i = np.searchsorted(over, t - 0.2)
        last = over[i - 1] if i > 0 else -1e9
        out.append((t, near, kind, t - last >= QUIET))
    return out


def main():
    T = {}
    for mid in WINDOWS:
        ev = load(mid)
        pts = lab.gt_points(mid)
        for name, sel in (("all", ev), ("start", [e for e in ev if e[3]])):
            ts = np.array([e[0] for e in sel])
            hit = sum(bool(((ts >= a - 1.0) & (ts <= a + 2.5)).any()) for a, b, _ in pts)
            c = {"start": 0, "in": 0, "gap": 0, "other": 0}
            prev = -1e9
            starts = np.array([p[0] for p in pts]); ends = np.array([p[1] for p in pts])
            for t in ts:
                j = np.searchsorted(starts, t + 1.0)          # points with start <= t + 1
                if j > 0 and t <= starts[j - 1] + 2.5:
                    c["start"] += 1
                elif j > 0 and t <= ends[j - 1]:
                    c["in"] += 1
                elif j < len(starts) and starts[j] - t <= 25:
                    c["gap"] += 1
                else:
                    c["other"] += 1
            k = T.setdefault(name, {"pts": 0, "hit": 0, "start": 0, "in": 0, "gap": 0, "other": 0})
            k["pts"] += len(pts); k["hit"] += hit
            for x in c:
                k[x] += c[x]
            print(f"{mid} {name:5s}: events {len(ts)}, point starts with an event {100 * hit / len(pts):.0f}%, where {c}")
    for name, k in T.items():
        n = k["start"] + k["in"] + k["gap"] + k["other"]
        print(f"ALL {name:5s}: events {n}, recall of point starts {100 * k['hit'] / k['pts']:.1f}%, at a start {100 * k['start'] / n:.0f}%, "
              f"in a point {100 * k['in'] / n:.0f}%, gap before a point {100 * k['gap'] / n:.0f}%, other {100 * k['other'] / n:.0f}%")


if __name__ == "__main__":
    main()

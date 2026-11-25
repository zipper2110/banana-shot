"""Server end from poses (E12) and the player at the near end (P1 or P2).

For each marked point: the highest wrist position (in torso lengths above
the shoulders) of the near player and of the far player in the seconds
start-1 .. start+2. The player with the raised arm is the server.
The script compares this with the server of sides.json, finds which
shirt-color cluster is P1, and writes <match>/ends.json: for each point
"nearPlayer" (P1/P2), "serverNearVision" (true/false/null) and
"winnerNear" (true/false/null).
Usage: python server_end.py [match id ...]
"""
import json, os, sys
import numpy as np
import lab


def wrist_up(row):
    k = row[6:].reshape(17, 3)
    sh, hip, wr = k[[5, 6]], k[[11, 12]], k[[9, 10]]
    if min(sh[:, 2].min(), hip[:, 2].min()) < 0.3 or wr[:, 2].max() < 0.3:
        return np.nan
    torso = np.linalg.norm(sh[:, :2].mean(0) - hip[:, :2].mean(0)) + 1e-3
    return float((sh[:, 1].mean() - wr[wr[:, 2] > 0.3][:, 1].min()) / torso)


def near_far(rows):
    """Near player: tallest person in the lower part. Far player: small person in the upper middle."""
    near = rows[(rows[:, 5] > 0.5) & (rows[:, 4] > 0.45 * 1080)]
    if not len(near):
        return None, None
    r = near[np.argmax(near[:, 4] - near[:, 2])]
    if r[4] - r[2] < 150:
        return None, None
    cx, hgt = (rows[:, 1] + rows[:, 3]) / 2, rows[:, 4] - rows[:, 2]
    far = rows[(rows[:, 5] > 0.4) & (rows[:, 4] < r[2] + 0.5 * (r[4] - r[2])) & (hgt > 40) & (hgt < 0.7 * (r[4] - r[2]))
               & (np.abs(cx - 960) < 620)]
    fr = far[np.argmin(np.abs((far[:, 1] + far[:, 3]) / 2 - 960))] if len(far) else None
    return r, fr


def run(mid):
    out = lab.mdir(mid)
    n = lab.n_seconds(mid)
    pose = lab.pose_by_second(out, n)                # the poses of the picture of each second (G2)
    sides = json.load(open(os.path.join(out, "sides.json")))
    ni = json.load(open(os.path.join(out, "near_identity.json")))["points"]
    gt = lab.gt_points(mid)
    vis = []
    for (a, b, _) in gt:
        nu, fu = [], []
        for s in range(max(0, int(a) - 1), min(n, int(a) + 3)):
            r, fr = near_far(pose[s])
            if r is not None:
                nu.append(wrist_up(r))
            if fr is not None:
                fu.append(wrist_up(fr))
        nmax = np.nanmax(nu) if np.isfinite(nu).any() else -9
        fmax = np.nanmax(fu) if np.isfinite(fu).any() else -9
        vis.append(None if max(nmax, fmax) < 0.5 else bool(nmax > fmax))
    cl = np.array([p["nearCluster"] for p in ni])
    srv_p1 = np.array([s["server"] == "P1" for s in sides])
    known = np.array([v is not None for v in vis]) & (cl >= 0)
    v = np.array([bool(x) for x in vis])
    # hypothesis: cluster 0 is P1 -> P1 is near when cl == 0 -> server near = (srv_p1 == (cl == 0))
    agree0 = (v[known] == (srv_p1[known] == (cl[known] == 0))).mean()
    c_p1 = 0 if agree0 >= 0.5 else 1
    acc = max(agree0, 1 - agree0)
    res = []
    for i, s in enumerate(sides):
        near_p = None if cl[i] < 0 else ("P1" if cl[i] == c_p1 else "P2")
        o = s["outcome"]
        res.append({"id": s["id"], "nearPlayer": near_p, "serverNearVision": vis[i],
                    "serverNearRules": None if near_p is None else bool(s["server"] == near_p),
                    "winnerNear": None if (near_p is None or o not in ("P1", "P2")) else bool(o == near_p)})
    json.dump(res, open(os.path.join(out, "ends.json"), "w"), indent=1)
    wn = [r["winnerNear"] for r in res if r["winnerNear"] is not None]
    print(mid, "points", len(gt), "| server seen by pose at %d points (%.0f%%)" % (known.sum(), 100 * known.mean()),
          "| pose server end agrees with the marks: %.1f%%" % (100 * acc),
          "| near player wins %.0f%% of the points" % (100 * np.mean(wn)), flush=True)


if __name__ == "__main__":
    for mid in (sys.argv[1:] or ["m1015", "m1020", "m1021", "m1030"]):
        run(mid)

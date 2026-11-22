"""Who is at the near end of the court for each marked point?

Vision: the near player is the tallest person in the lower part of the
frame. The torso color (mean Lab color between the shoulders and the hips)
of the near player minus the torso color of the far player is
averaged over the point, and a majority vote in each game removes noise. K-means with 2 clusters
gives a player identity for each point.
Rules: sides.json (score replay) says when the players change ends.
The script compares the two and writes <match>/near_identity.json:
for each point "nearCluster" (0/1, after the vote in each game of the marks),
"rawCluster" (0/1 or -1, no vote: fair input for the score decoder),
"flip" (true = the near player changed since the last point with a color; local, see flips()), "nearPlayer" (P1/P2 from the best
mapping), and "rulesAgree".
Singles only. Usage: python near_identity.py [match id ...]
"""
import json, os, sys
import numpy as np
import cv2
from sklearn.cluster import KMeans
import lab


def torso_color(img, row):
    k = row[6:].reshape(17, 3)
    pts = k[[5, 6, 11, 12]]
    if pts[:, 2].min() < 0.35:
        return None
    x0, x1 = int(pts[:, 0].min()), int(pts[:, 0].max())
    y0, y1 = int(pts[:2, 1].mean()), int(pts[2:, 1].mean())
    if x1 - x0 < 6 or y1 - y0 < 10:
        return None
    dx, dy = int(0.15 * (x1 - x0)), int(0.15 * (y1 - y0))
    patch = img[max(0, y0 + dy):y1 - dy, max(0, x0 + dx):x1 - dx]
    if patch.size == 0:
        return None
    return cv2.cvtColor(patch, cv2.COLOR_BGR2LAB).reshape(-1, 3).mean(0)


def flips(feats, alpha=0.5):
    """Local end-change signal (E14e). A running reference follows the color difference "near minus
    far"; the sign of each point against the reference gives a local cluster. flip[i] = the local
    cluster differs from that of the last point with a color (None = no color). A shirt change
    gives at most one wrong flip; a fixed 2-cluster model fails for the rest of the match."""
    out, ref, last = [], None, None
    for d in feats:
        if d is None:
            out.append(None); continue
        sgn = 1 if ref is None or float(d @ ref) >= 0 else -1
        ref = d.copy() if ref is None else (1 - alpha) * ref + alpha * sgn * d
        out.append(None if last is None else bool(sgn != last))
        last = sgn
    return out


def run(mid):
    out = lab.mdir(mid)
    pose = np.load(os.path.join(out, "pose.npy"))
    pose = pose[np.argsort(pose[:, 0], kind="stable")]
    n = lab.n_seconds(mid)
    starts = np.searchsorted(pose[:, 0], np.arange(n + 1))
    sides = json.load(open(os.path.join(out, "sides.json")))
    gt = lab.gt_points(mid)
    feats = []
    for (a, b, _) in gt:
        cols = []
        for s in range(int(a) + 1, min(n, int(b))):
            allr = pose[starts[s]:starts[s + 1]]
            rows = allr[(allr[:, 5] > 0.5) & (allr[:, 4] > 0.45 * 1080)]      # box bottom in the lower part
            if not len(rows):
                continue
            r = rows[np.argmax(rows[:, 4] - rows[:, 2])]
            if r[4] - r[2] < 150:
                continue
            # far player: a small person in the upper part, near the horizontal center of the frame
            cx = (allr[:, 1] + allr[:, 3]) / 2
            hgt = allr[:, 4] - allr[:, 2]
            far = allr[(allr[:, 5] > 0.4) & (allr[:, 4] < r[2] + 0.5 * (r[4] - r[2])) & (hgt > 40) & (hgt < 0.7 * (r[4] - r[2]))
                       & (np.abs(cx - 960) < 620)]
            if not len(far):
                continue
            fr = far[np.argmin(np.abs((far[:, 1] + far[:, 3]) / 2 - 960))]
            img = cv2.imread(os.path.join(out, "kf", f"{s + 1:05d}.jpg"))
            c, c2 = torso_color(img, r), torso_color(img, fr)
            if c is not None and c2 is not None:
                cols.append(c - c2)             # near minus far: the light of the scene cancels
        feats.append(np.median(cols, 0) if len(cols) >= 2 else None)
    fl = flips(feats)
    ok = [i for i, f in enumerate(feats) if f is not None]
    X = np.array([feats[i] for i in ok])
    km = KMeans(2, n_init=10, random_state=0).fit(X)
    sep = np.linalg.norm(km.cluster_centers_[0] - km.cluster_centers_[1]) / (np.sqrt(km.inertia_ / len(X)) + 1e-6)
    cl = np.full(len(gt), -1); cl[ok] = km.labels_
    raw = cl.copy()
    games = np.array([s["game"] for s in sides])
    for g in np.unique(games):                     # the ends are constant in a game: majority vote
        idx = np.nonzero((games == g) & (cl >= 0))[0]
        if len(idx):
            cl[games == g] = int(round(cl[idx].mean() + 1e-9))
    swapped = np.array([bool(s["endsSwapped"]) for s in sides])
    known = cl >= 0
    agree0 = ((cl[known] == 1) == swapped[known]).mean()
    flip = agree0 < 0.5
    agree = max(agree0, 1 - agree0)
    # cluster c at the near end means: endsSwapped == (c == 1) xor flip. Player identity needs one more bit:
    # use the outcome-independent convention "cluster of the first known point = player A".
    res = []
    for i, s in enumerate(sides):
        rule_cluster = int(swapped[i]) ^ int(flip)
        res.append({"id": s["id"], "nearCluster": int(cl[i]), "rawCluster": int(raw[i]), "flip": fl[i], "rulesCluster": rule_cluster,
                    "rulesAgree": bool(cl[i] == rule_cluster) if cl[i] >= 0 else None})
    json.dump({"clusterSeparation": float(sep), "agreement": float(agree), "points": res},
              open(os.path.join(out, "near_identity.json"), "w"), indent=1)
    dis = [i for i, r in enumerate(res) if r["rulesAgree"] is False]
    print(mid, "points", len(gt), "with color", len(ok), "cluster separation %.1f" % sep,
          "| rules agree with vision on %.1f%%" % (100 * agree), "| disagree at points", dis[:40], flush=True)
    print("   vision before the vote:  ", "".join("?" if c < 0 else str(c) for c in raw))
    print("   vision cluster sequence:", "".join("?" if c < 0 else str(c) for c in cl))
    print("   rules  cluster sequence:", "".join(str(r["rulesCluster"]) for r in res))


if __name__ == "__main__":
    for mid in (sys.argv[1:] or ["m1015", "m1020", "m1021", "m1030"]):
        run(mid)

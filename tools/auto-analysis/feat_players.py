"""Player features in court coordinates, one row for each second.

Input: <match>/pose.npy (pose_extract.py) and <match>/court.json
(court_fit.py). Output: <match>/feat_players.npy and
<match>/players.npy (the selected players, for other scripts).

A person is a player if the foot point is on or near the court: |x| <= 8 m
and |y| <= 17 m. Each half of the court has 2 slots (singles use slot A).
Slot order in a half: the player nearer to the net first.

Columns for each of the 4 slots (near A, near B, far A, far B):
  x, |y| (meters), speed (m/s against the previous second), wrist_up
  (highest wrist above the shoulder line, in torso lengths), arm_out
  (largest wrist distance from the hip center, in torso lengths),
  crouch (box height / torso length), found (0 or 1).
Then global columns: count near, count far, max wrist_up, x_near * x_far,
near |y| - 11.885, far |y| - 11.885.
Missing values are NaN.
Usage: python feat_players.py [match id ...]
"""
import json, os, sys
import numpy as np
import lab

PER = 7
BL = 11.885


def kp(row):
    return row[6:].reshape(17, 3)


def person_feats(row, H):
    k = kp(row)
    x1, y1, x2, y2 = row[1:5]
    ank = k[[15, 16]]
    if ank[:, 2].min() > 0.35:
        fx, fy = ank[:, 0].mean(), ank[:, 1].max()
    else:
        fx, fy = (x1 + x2) / 2, y2
    p = H @ np.array([fx, fy, 1.0])
    cx, cy = p[0] / p[2], p[1] / p[2]
    sh, hip, wr = k[[5, 6]], k[[11, 12]], k[[9, 10]]
    torso = np.linalg.norm(sh[:, :2].mean(0) - hip[:, :2].mean(0)) + 1e-3
    ok = min(sh[:, 2].min(), hip[:, 2].min()) > 0.3
    wrist_up = np.nan
    arm_out = np.nan
    if ok and wr[:, 2].max() > 0.3:
        good = wr[wr[:, 2] > 0.3]
        wrist_up = float((sh[:, 1].mean() - good[:, 1].min()) / torso)
        arm_out = float(np.linalg.norm(good[:, :2] - hip[:, :2].mean(0), axis=1).max() / torso)
    crouch = float((y2 - y1) / torso) if ok else np.nan
    return cx, cy, wrist_up, arm_out, crouch


def run(mid):
    out = lab.mdir(mid)
    pose = np.load(os.path.join(out, "pose.npy"))
    H = np.array(json.load(open(os.path.join(out, "court.json")))["H"])
    n = lab.n_seconds(mid)
    f = np.full((n, 4 * PER + 6), np.nan, dtype=np.float32)
    sel = np.full((n, 4, 6), np.nan, dtype=np.float32)       # x, y, wrist_up, arm_out, crouch, pose row index
    order = np.argsort(pose[:, 0], kind="stable")
    pose = pose[order]
    starts = np.searchsorted(pose[:, 0], np.arange(n + 1))
    prev = {}
    for s in range(n):
        rows = pose[starts[s]:starts[s + 1]]
        halves = {0: [], 1: []}
        for i, r in enumerate(rows):
            if r[5] < 0.3:
                continue
            cx, cy, wu, ao, cr = person_feats(r, H)
            if abs(cx) <= 8 and abs(cy) <= 17:
                halves[0 if cy < 0 else 1].append((abs(cy), cx, cy, wu, ao, cr, starts[s] + i))
        cur = {}
        for h in (0, 1):
            ps = sorted(halves[h])[:2]
            for j, (ay, cx, cy, wu, ao, cr, idx) in enumerate(ps):
                slot = h * 2 + j
                sp = np.nan
                if slot in prev:
                    sp = float(np.hypot(cx - prev[slot][0], cy - prev[slot][1]))
                f[s, slot * PER: slot * PER + PER] = (cx, ay, sp, wu, ao, cr, 1.0)
                sel[s, slot] = (cx, cy, wu, ao, cr, order[idx])
                cur[slot] = (cx, cy)
            for j in range(len(ps), 2):
                f[s, (h * 2 + j) * PER + PER - 1] = 0.0
        prev = cur
        g = 4 * PER
        f[s, g] = len(halves[0]); f[s, g + 1] = len(halves[1])
        wus = f[s, [3, PER + 3, 2 * PER + 3, 3 * PER + 3]]
        f[s, g + 2] = np.nanmax(wus) if np.isfinite(wus).any() else np.nan
        f[s, g + 3] = f[s, 0] * f[s, 2 * PER]
        f[s, g + 4] = f[s, 1] - BL
        f[s, g + 5] = f[s, 2 * PER + 1] - BL
    np.save(os.path.join(out, "feat_players.npy"), f)
    np.save(os.path.join(out, "players.npy"), sel)
    print(mid, f.shape, "near found %.0f%%  far found %.0f%%" % (100 * f[:, PER - 1].mean(), 100 * f[:, 2 * PER + PER - 1].mean()), flush=True)


if __name__ == "__main__":
    for mid in (sys.argv[1:] or lab.LABELED):
        if os.path.exists(os.path.join(lab.mdir(mid), "pose.npy")) and os.path.exists(os.path.join(lab.mdir(mid), "court.json")):
            run(mid)

"""Links ball blobs (ball_wasb.py output) into tracks and gives a coverage
figure. No ball ground truth exists, so the figure is a proxy:
"coverage" = share of the frames of the marked points that are inside a
moving track (length >= MIN_LEN frames, path >= MIN_PATH px, mean speed
>= MIN_SPEED px/frame; all px in the 1920 x 1080 frame).
"noise" = the same share for the frames that are not in a marked point
(a ball can also fly there, so this is an upper limit of the false rate).
Usage: python ball_tracks.py <match id> <start_s> <dur_s> <file tag> [file tag ...]
       (more than one tag: the blobs of all tags are merged)
"""
import os, sys
import numpy as np
import lab

MAX_GAP, MIN_LEN, MIN_PATH, MIN_SPEED = 4, 8, 60.0, 2.5


def link(d, max_disp=90.0):
    """d: rows frame, x, y, score, peak. Returns a list of arrays (frame, x, y, peak)."""
    order = np.argsort(d[:, 0], kind="stable")
    d = d[order]
    open_, done = [], []
    for f in np.unique(d[:, 0]):
        cur = d[d[:, 0] == f]
        used = set()
        keep = []
        for tr in open_:
            lf, lx, ly = tr[-1][:3]
            gap = f - lf
            if gap > MAX_GAP:
                done.append(tr); continue
            if len(tr) >= 2 and tr[-1][0] > tr[-2][0]:
                dt = tr[-1][0] - tr[-2][0]
                vx, vy = (tr[-1][1] - tr[-2][1]) / dt, (tr[-1][2] - tr[-2][2]) / dt
            else:
                vx = vy = 0.0
            px, py = lx + vx * gap, ly + vy * gap
            e = np.hypot(cur[:, 1] - px, cur[:, 2] - py)
            lim = (12.0 + 0.5 * np.hypot(vx, vy) * gap) if len(tr) >= 2 else max_disp
            e[list(used)] = 1e9
            k = int(e.argmin())
            if e[k] < lim:
                used.add(k); tr.append((f, cur[k, 1], cur[k, 2], cur[k, 4]))
            keep.append(tr)
        open_ = keep
        for k in range(len(cur)):
            if k not in used:
                open_.append([(f, cur[k, 1], cur[k, 2], cur[k, 4])])
    return [np.array(t, np.float32) for t in done + open_]


def moving(tracks):
    out = []
    for t in tracks:
        if len(t) < MIN_LEN:
            continue
        path = np.hypot(np.diff(t[:, 1]), np.diff(t[:, 2])).sum()
        if path >= MIN_PATH and path / (t[-1, 0] - t[0, 0]) >= MIN_SPEED:
            out.append(t)
    return out


def merge(d, r=6.0):
    """Blobs of several scales: drop a blob that is within r px of a stronger blob of the same frame."""
    d = d[np.lexsort((-d[:, 4], d[:, 0]))]
    keep = np.ones(len(d), bool)
    f0 = 0
    for f1 in list(np.nonzero(np.diff(d[:, 0]))[0] + 1) + [len(d)]:
        for i in range(f0, f1):
            if keep[i]:
                for j in range(i + 1, f1):
                    if keep[j] and abs(d[i, 1] - d[j, 1]) < r and abs(d[i, 2] - d[j, 2]) < r:
                        keep[j] = False
        f0 = f1
    return d[keep]


def report(mid, start, dur, tags):
    d = np.concatenate([np.load(os.path.join(lab.mdir(mid), f"wasb_{int(start)}_{int(dur)}_{t}.npy")) for t in tags])
    if len(tags) > 1:
        d = merge(d)
    tr = moving(link(d))
    n = int(dur * 60)
    cov = np.zeros(n, bool)
    for t in tr:
        cov[int(t[0, 0]):int(t[-1, 0]) + 1] = True
    inp = np.zeros(n, bool)
    for a, b, _ in lab.gt_points(mid):
        if b > start and a < start + dur:
            inp[max(0, int((a - start) * 60)):max(0, min(n, int((b - start) * 60)))] = True
    print(f"{mid} {'+'.join(tags)}: blobs {len(d)}, moving tracks {len(tr)}, coverage in points {100 * cov[inp].mean():.0f}%"
          f", outside points {100 * cov[~inp].mean():.0f}%, median track {np.median([len(t) for t in tr]) if tr else 0:.0f} frames")
    return tr


if __name__ == "__main__":
    a = sys.argv[1:]
    report(a[0], float(a[1]), float(a[2]), a[3:])

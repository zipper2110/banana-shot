"""E136 (gap program, ball plan step 2): a lower blob threshold where a path supports it. No training.

The detector keeps a heat-map component as a blob when its peak is above 0.5 (ball_wasb.blobs). E133b: 19 missed
labels of m1015, m1020 and m1030 have a weak peak just below 0.5 (median 0.45). This module adds "weak blobs":
components above LOW whose peak stays below 0.5. A weak blob is kept only on a moving path of strong blobs:
a strong blob a in the GAP frames before it and a strong blob b in the GAP frames after it, with a speed of VMIN px
or more for each frame, and the weak blob near the line from a to b at its frame. A weak blob only fills a gap of
the path: no strong blob of its frame is near the path, and each frame gets at most one weak blob. The strong blobs do not change
(the weak components have no pixel above 0.5). A kept weak blob is a normal row (frame, x, y, score, peak) with a
peak below 0.5.

Use: ball_wasb.WEAK = ball_path.Weak(0.2) before ball_wasb.run(); None (the default) = off, the old blob file.
The environment variable BALL_PATH_LOW (a number) sets it at the import of ball_wasb (bench.py heavy)."""
import numpy as np
import cv2

STRONG = 0.5          # the threshold of ball_wasb.blobs
TOP = 3               # weak blobs for each heat map
SAME_R = 12.0         # px of the 1920 frame: a weak blob this near a strong blob of its frame is the same ball
GAP_S = 1 / 6.0       # s: a strong blob at most this far before and after the weak blob (10 frames at 60 fps)
VMIN, VMAX = 2.0, 60.0    # px for each 60 fps frame: the speed of the path (not a static object; not a jump)
R0, R1 = 10.0, 0.2    # the weak blob is within R0 + R1 * speed * (frames to the nearer strong blob) px of the line


class Weak:
    def __init__(self, low):
        if not 0.0 < low < STRONG:
            raise ValueError("BALL_PATH_LOW: a number between 0 and 0.5")
        self.low = float(low)
        self.rows = []

    def blobs(self, hm, top=TOP):
        """Heat map -> weak blobs (x, y, score, peak) in heat-map pixels: the components above low with a peak below
        STRONG. Same center rule as ball_wasb.blobs."""
        if hm.max() <= self.low:
            return []
        n, lab_, st, _ = cv2.connectedComponentsWithStats((hm > self.low).astype(np.uint8), connectivity=8)
        out = []
        for i in range(1, n):
            x, y, w, h, _ = st[i]
            sub = hm[y:y + h, x:x + w] * (lab_[y:y + h, x:x + w] == i)
            pk = float(sub.max())
            if pk > STRONG:
                continue
            s = float(sub.sum())
            yy, xx = np.mgrid[0:h, 0:w]
            out.append((x + float((xx * sub).sum()) / s, y + float((yy * sub).sum()) / s, s, pk))
        return sorted(out, key=lambda b: -b[2])[:top]

    def add(self, frame, blobs):
        """The weak blobs (x, y, score, peak; px of the 1920 frame) of one frame of the run."""
        for b in blobs:
            self.rows.append((frame, b[0], b[1], b[2], b[3]))

    def finish(self, arr, fps=60.0):
        """arr: the strong rows of the run (frame, x, y, score, peak). Returns arr with the kept weak rows, sorted by
        frame (stable: the strong rows of a frame first). Clears the weak rows for the next run."""
        weak, self.rows = self.rows, []
        keep = path_keep(arr, np.array(weak, np.float32).reshape(-1, 5), fps)
        if not len(keep):
            return arr
        out = np.concatenate([arr, keep]).astype(np.float32)
        return out[np.argsort(out[:, 0], kind="stable")]


def path_keep(strong, weak, fps=60.0):
    """The weak rows that fill a gap of a moving path of strong rows (see the module text). For each weak blob: the
    pair of strong blobs (a before, b after) with the smallest error. The weak blob fills a gap only when no strong
    blob of its frame is near the point of the path (else the path has its blob there). At most one weak blob for
    each frame: the one with the smallest error for its radius."""
    if not len(weak) or not len(strong):
        return weak[:0]
    f60 = 60.0 / fps                                     # frames of the run -> 60 fps frames (speeds in px / 60 fps frame)
    gap = max(1, int(round(GAP_S * fps)))
    s = strong[np.argsort(strong[:, 0], kind="stable")]
    fr = s[:, 0].astype(np.int64)
    best = {}                                            # frame -> (error / radius, weak row)
    for w in weak:
        k = int(w[0])
        lo, mid0, mid1, hi = np.searchsorted(fr, [k - gap, k, k + 1, k + gap + 1])
        A, B, S = s[lo:mid0], s[mid1:hi], s[mid0:mid1]
        if not len(A) or not len(B):
            continue
        if (np.hypot(S[:, 1] - w[1], S[:, 2] - w[2]) < SAME_R).any():
            continue                                     # the same ball as a strong blob of the frame
        da = (k - A[:, 0])[:, None]                      # frames from a to the weak blob
        db = (B[:, 0] - k)[None, :]
        n = da + db
        vx = (B[None, :, 1] - A[:, None, 1]) / n
        vy = (B[None, :, 2] - A[:, None, 2]) / n
        sp = np.hypot(vx, vy) / f60
        px, py = A[:, None, 1] + vx * da, A[:, None, 2] + vy * da
        rad = R0 + R1 * sp * np.minimum(da, db) * f60
        q = np.hypot(px - w[1], py - w[2]) / rad
        q[(sp < VMIN) | (sp > VMAX)] = np.inf
        j = np.unravel_index(np.argmin(q), q.shape)
        if not q[j] <= 1.0:
            continue
        if len(S) and (np.hypot(S[:, 1] - px[j], S[:, 2] - py[j]) <= rad[j]).any():
            continue                                     # the path has a strong blob in this frame: no gap
        if k not in best or q[j] < best[k][0]:
            best[k] = (float(q[j]), w)
    return np.array([best[k][1] for k in sorted(best)], np.float32).reshape(-1, 5)

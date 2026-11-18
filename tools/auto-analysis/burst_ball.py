"""Ball-in-flight candidates from T2 bursts: 6 successive frames (100 ms of
60 fps video) each second. No model: frame differences and linking.

Decode: the bitstream filter drops all packets of a GOP after the first 6,
so the decoder gets the keyframe and 5 P-frames. Needs a video with no
B-frames and a keyframe each 60 frames.

Steps for each burst (frames f0..f5, 1920 x 1080):
  1. For t = 1..4: m_t = min(|f_t - f_t-1|, |f_t+1 - f_t|) > threshold.
     This keeps things that are at a place only in frame t (small fast
     objects), and drops slow large motion.
  2. Small blobs of m_t are candidates (area 2 to 160 px).
  3. Link candidates of successive frames with a near-constant velocity.
     A chain of 3 or 4 = a "tracklet".
Writes <match>/tracklets.npy: second, x, y (frame 1 position), vx, vy
(px/frame), length, mean area, yellow (share of ball-colored pixels).
Usage: python burst_ball.py <match id> [start_s duration_s]
"""
import os, subprocess, sys, time
import numpy as np
import cv2
import lab

W, H, K = 1920, 1080, 6
THR = 16


def roi_mask(mid):
    """Court area with a margin (3 m at the sides, 5 m behind the baselines),
    extended upward by 30 % of the frame height for a ball in the air."""
    import json
    Hm = np.array(json.load(open(os.path.join(lab.mdir(mid), "court.json")))["H"])
    g = np.array([[(-8.5, -17), (8.5, -17), (8.5, 17), (-8.5, 17)]], np.float32)
    p = cv2.perspectiveTransform(g, np.linalg.inv(Hm))[0]
    p = np.clip(p, [-4000, -4000], [6000, 6000])
    pts = np.concatenate([p, p - [0, 0.3 * H]]).astype(np.int32)
    m = np.zeros((H, W), np.uint8)
    cv2.fillConvexPoly(m, cv2.convexHull(pts), 1)
    return m


BIG = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))


def candidates(prev, cur, nxt, bgr, roi, thr):
    """Returns the candidates of frame "cur" and the difference image.
    All images are crops to the bounding box of the court region."""
    d1 = cv2.absdiff(cur, prev)
    d = cv2.min(d1, cv2.absdiff(nxt, cur))
    m = cv2.bitwise_and((d > thr).astype(np.uint8), roi)
    # large moving things (players): no candidates on them. Quarter size for speed.
    q = cv2.resize((d1 > 20).astype(np.uint8) * 255, None, fx=0.25, fy=0.25, interpolation=cv2.INTER_AREA)
    q = cv2.dilate((q > 12).astype(np.uint8), BIG)
    nb, lab_b, st_b, _ = cv2.connectedComponentsWithStats(q, connectivity=8)
    big_ids = np.nonzero(st_b[:, cv2.CC_STAT_AREA] > 90)[0]
    big_ids = big_ids[big_ids > 0]
    if len(big_ids):
        lut = np.zeros(nb, np.uint8); lut[big_ids] = 1
        bigm = cv2.resize(lut[lab_b], (m.shape[1], m.shape[0]), interpolation=cv2.INTER_NEAREST)
        m[bigm > 0] = 0
    n, _, stats, cent = cv2.connectedComponentsWithStats(m, connectivity=8)
    out = []
    if n > 300:                      # camera shake or a scene change: no use
        return out, d
    for i in range(1, n):
        x, y, w, h, a = stats[i]
        if a < 3 or a > 160 or max(w, h) > 36:
            continue
        patch = bgr[y:y + h, x:x + w].reshape(-1, 3).astype(np.int16)
        b, g, r = patch[:, 0], patch[:, 1], patch[:, 2]
        yellow = float(((g > 110) & (r > 90) & (g - b > 35) & (r - b > 15)).mean())
        out.append((cent[i][0], cent[i][1], a, yellow))
    return out, d


def link(cs):
    """cs: 4 candidate lists (t = 1..4). Returns tracklets."""
    tr = []
    A = [np.array(c, dtype=np.float32).reshape(-1, 4) for c in cs]
    used = [set() for _ in cs]
    for t0 in (0, 1):
        for i, a in enumerate(A[t0]):
            if i in used[t0] or not len(A[t0 + 1]):
                continue
            d = A[t0 + 1][:, :2] - a[:2]
            dist = np.hypot(d[:, 0], d[:, 1])
            for j in np.nonzero((dist > 1.5) & (dist < 110))[0]:
                if j in used[t0 + 1]:
                    continue
                chain, v, pos, t = [(t0, i), (t0 + 1, j)], d[j], A[t0 + 1][j, :2], t0 + 2
                while t < 4 and len(A[t]):
                    e = A[t][:, :2] - (pos + v)
                    err = np.hypot(e[:, 0], e[:, 1])
                    k = int(err.argmin())
                    if err[k] > 0.3 * np.hypot(*v) + 2.5 or k in used[t]:
                        break
                    v = A[t][k, :2] - pos; pos = A[t][k, :2]; chain.append((t, k)); t += 1
                if len(chain) >= 3:
                    for (tt, kk) in chain:
                        used[tt].add(kk)
                    pts = np.array([A[tt][kk] for tt, kk in chain])
                    vel = (pts[-1, :2] - pts[0, :2]) / (len(chain) - 1)
                    tr.append((pts[0, 0], pts[0, 1], vel[0], vel[1], len(chain), pts[:, 2].mean(), pts[:, 3].mean()))
                    break
    return tr


def run(mid, start=None, dur=None):
    v = lab.MATCHES[mid]["video"]
    roi_full = roi_mask(mid)
    ys, xs = np.nonzero(roi_full)
    x0, y0 = int(xs.min()) // 2 * 2, int(ys.min()) // 2 * 2
    cw, ch = (int(xs.max()) + 1 - x0) // 2 * 2, (int(ys.max()) + 1 - y0) // 2 * 2
    roi = np.ascontiguousarray(roi_full[y0:y0 + ch, x0:x0 + cw])
    cmd = ["ffmpeg", "-v", "error", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda",
           "-bsf:v", r"noise=drop='gte(mod(n\,60)\,%d)'" % K]
    if start is not None:
        cmd += ["-ss", str(start), "-t", str(dur)]
    cmd += ["-i", v, "-vf", f"scale_cuda={W}:{H},hwdownload,format=nv12,crop={cw}:{ch}:{x0}:{y0},format=bgr24",
            "-fps_mode", "passthrough", "-f", "rawvideo", "-"]
    size = cw * ch * 3 * K
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=size)
    rows, s, t0 = [], int(start or 0), time.time()
    noise = np.full((ch, cw), 2.0, np.float32)
    while True:
        buf = p.stdout.read(size)
        if len(buf) < size:
            break
        fr = np.frombuffer(buf, np.uint8).reshape(K, ch, cw, 3)
        g = [cv2.GaussianBlur(cv2.cvtColor(f, cv2.COLOR_BGR2GRAY), (3, 3), 0) for f in fr]
        thr = np.clip(3.5 * noise, THR, 255).astype(np.uint8)
        cs = []
        for t in range(1, 5):
            c, d = candidates(g[t - 1], g[t], g[t + 1], fr[t], roi, thr)
            cs.append(c)
        cv2.accumulateWeighted(d, noise, 0.03)     # slow average: places that always flicker
        for tr in link(cs):
            rows.append((s, tr[0] + x0, tr[1] + y0) + tuple(tr[2:]))
        s += 1
        if (s - int(start or 0)) % 600 == 0:
            print(mid, s, "s, tracklets", len(rows), "%.0f s" % (time.time() - t0), flush=True)
    arr = np.array(rows, dtype=np.float32).reshape(-1, 8)
    name = "tracklets.npy" if start is None else f"tracklets_{int(start)}_{int(dur)}.npy"
    np.save(os.path.join(lab.mdir(mid), name), arr)
    print(mid, "seconds", s - int(start or 0), "tracklets", len(arr), "crop", (cw, ch), "%.0f s" % (time.time() - t0), flush=True)


if __name__ == "__main__":
    a = sys.argv[1:]
    run(a[0], *(float(x) for x in a[1:3])) if len(a) >= 3 else run(a[0])

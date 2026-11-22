"""E15b step 1: pseudo-labels for the ball detector from our own video.

Source: the 3D flights of ball_3d.py (flights_s0.35.json). A flight with
many points and a small fit error is a reliable ball path. Its projection
gives the ball position in each frame of the flight, also in frames where
the detector missed the ball (a gap inside the flight).
For each point: up to K_PER_POINT label groups. A group = 3 frames 2 apart
(the model input), all 3 inside one reliable flight. The script decodes the
point window again (T3, scale 0.35, court crop) and stores the 5 frames of
each group as JPEG, with the label positions.
After a tracking gap (no next flight soon) the flight is extended by up to
EXT frames, but not through a bounce: this adds positives for the ball near
the camera, where the detector fails.
Output: D:/bananashot-lab/data/train/<match>/<point>_<frame>.jpg (one JPEG
for each frame) and labels.json.
Usage: python ball_labels.py <match id> [...]
"""
import json, os, sys, time
import numpy as np
import cv2
import lab
from ball_wasb import frames, crop_box, PRE, POST
from ball_3d import camera, simulate, project
from ball_events import court_H

SCALE, K_PER_POINT, N_MIN, RMS_MAX, EXT = 0.35, 24, 20, 2.0, 6
OUT = "D:/bananashot-lab/data/train"


def reliable(f):
    sp = 3.6 * float(np.linalg.norm(f["v0"]))
    return f["n"] >= N_MIN and f["rms"] < RMS_MAX and 8 < sp < 230 and abs(f["p0"][1]) < 20 and -0.2 < f["p0"][2] < 5


def run(mid, seed=0):
    rng = np.random.default_rng(seed)
    pts = json.load(open(os.path.join(lab.mdir(mid), "flights_s0.35.json")))
    x0, y0, cw, ch, _, _ = crop_box(mid, SCALE)
    od = os.path.join(OUT, mid)
    os.makedirs(od, exist_ok=True)
    labels, t0 = [], time.time()
    for i, pt in enumerate(pts):
        w0 = max(0.0, pt["start"] - PRE)
        dur = pt["end"] - pt["start"] + PRE + POST
        P, _, _ = camera(court_H(mid, pt["start"]))
        pos = {}                                         # frame -> (x, y) in crop px at SCALE
        fls = sorted(pt["flights"], key=lambda f: f["t0"])
        for j, f in enumerate(fls):
            if not reliable(f):
                continue
            fa, fb = int(round((f["t0"] - w0) * 60)), int(round((f["t1"] - w0) * 60))
            # tracking gap after the flight (no next flight soon): extend by up to EXT frames
            nxt = int(round((fls[j + 1]["t0"] - w0) * 60)) if j + 1 < len(fls) else fb + 99
            ext = max(0, min(EXT, nxt - fb - 4))
            ts = (np.arange(fa, fb + ext + 1) - fa) / 60.0
            X = simulate(f["p0"], f["v0"], ts)
            keep = np.ones(len(ts), bool)
            keep[fb - fa + 1:] = X[fb - fa + 1:, 2] > 0.3          # no extension through a bounce
            xy = project(P, X)
            for k, (x, y), ok in zip(range(fa, fb + ext + 1), xy, keep):
                if not ok:
                    break
                cx, cy = x * SCALE - x0, y * SCALE - y0
                if 2 <= cx < cw - 2 and 2 <= cy < ch - 2:
                    pos[k] = (float(cx), float(cy))
        cand = [k for k in pos if all(k + d in pos for d in (2, 4))]
        if not cand:
            continue
        pick = sorted(rng.choice(cand, size=min(K_PER_POINT, len(cand)), replace=False).tolist())
        need = {k + d for k in pick for d in (0, 2, 4)}
        for n, img in enumerate(frames(mid, w0, dur, SCALE)):
            if n in need:
                cv2.imwrite(os.path.join(od, f"{i:03d}_{n:05d}.jpg"), cv2.cvtColor(img, cv2.COLOR_RGB2BGR), [cv2.IMWRITE_JPEG_QUALITY, 93])
            if n > max(need):
                break
        written = set(n for n in need if os.path.exists(os.path.join(od, f"{i:03d}_{n:05d}.jpg")))
        for k in pick:
            if not all(k + d in written for d in (0, 2, 4)):
                continue
            labels.append({"point": i, "frames": [f"{i:03d}_{k + d:05d}.jpg" for d in (0, 2, 4)], "xy": [pos[k + d] for d in (0, 2, 4)]})
        if i % 25 == 0:
            print(f"{mid} point {i}/{len(pts)} groups {len(labels)} {time.time() - t0:.0f} s", flush=True)
    json.dump({"match": mid, "scale": SCALE, "crop": [x0, y0, cw, ch], "groups": labels}, open(os.path.join(od, "labels.json"), "w"))
    print(f"{mid} done: groups {len(labels)} in {time.time() - t0:.0f} s", flush=True)


if __name__ == "__main__":
    for m in sys.argv[1:]:
        run(m)

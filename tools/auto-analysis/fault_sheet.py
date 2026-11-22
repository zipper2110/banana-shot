"""Visual check of serve events: frame sheets around the server.

Each row: one event. Columns: frames at t + OFFS s, cropped to the region
around the server's baseline (court box x -6..6 m, 4 m around the baseline,
height 0..3.6 m, projected with the camera of ball_3d.camera).
Usage: python fault_sheet.py <match id> <out.jpg> <time:near> [<time:near> ...]
       near = 1 for the near end, 0 for the far end.
"""
import os, subprocess, sys
import numpy as np
import cv2
import lab
from ball_3d import camera, project
from ball_events import court_H

OFFS = [-0.5, -0.25, 0.0, 0.25, 0.5]
RH = 220


def grab(mid, t, n_frames=70):
    """Frames from t - 0.6 s, 1920 x 1080 BGR, 60 fps."""
    w, h = 1920, 1080
    cmd = ["ffmpeg", "-v", "error", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda", "-ss", f"{max(0, t - 0.6):.3f}",
           "-i", lab.MATCHES[mid]["video"], "-frames:v", str(n_frames),
           "-vf", f"scale_cuda={w}:{h},hwdownload,format=nv12,format=bgr24", "-f", "rawvideo", "-"]
    buf = subprocess.run(cmd, stdout=subprocess.PIPE).stdout
    return np.frombuffer(buf, np.uint8).reshape(-1, h, w, 3)


def box(mid, t, near):
    P, _, _ = camera(court_H(mid, t))
    y0, y1 = (-15.5, -9.5) if near else (9.5, 15.5)
    pts = np.array([(x, y, z) for x in (-6.5, 6.5) for y in (y0, y1) for z in (0, 3.6)], float)
    xy = project(P, pts)
    x0, x1 = np.clip([xy[:, 0].min() - 20, xy[:, 0].max() + 20], 0, 1919).astype(int)
    ya, yb = np.clip([xy[:, 1].min() - 20, xy[:, 1].max() + 20], 0, 1079).astype(int)
    return x0, ya, x1, yb


def main(mid, out, items):
    rows = []
    for it in items:
        t, near = it.split(":")
        t, near = float(t), near == "1"
        fr = grab(mid, t)
        x0, y0, x1, y1 = box(mid, t, near)
        cells = []
        for o in OFFS:
            k = int(round((0.6 + o) * 60)) if t >= 0.6 else int(round((t + o) * 60))
            im = fr[min(k, len(fr) - 1)][y0:y1, x0:x1]
            im = cv2.resize(im, (int(im.shape[1] * RH / im.shape[0]), RH))
            cv2.putText(im, f"{t + o:.2f}", (4, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1, cv2.LINE_AA)
            cells.append(im)
        row = np.hstack(cells)
        cv2.putText(row, f"t {t:.1f} {'near' if near else 'far'}", (4, RH - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2, cv2.LINE_AA)
        rows.append(row)
    w = max(r.shape[1] for r in rows)
    rows = [np.pad(r, ((0, 4), (0, w - r.shape[1]), (0, 0))) for r in rows]
    cv2.imwrite(out, np.vstack(rows), [cv2.IMWRITE_JPEG_QUALITY, 85])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])

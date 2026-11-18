"""Visual check of ball detections (ball_wasb.py output). Uses OpenCV only.

For each file tag: x and y of all blobs against time, marked points shaded
orange. A real ball path shows as smooth arcs; false detections show as
scatter or as horizontal lines (a static object). Dot color: green = high
peak, red = peak near the threshold.
Usage: python ball_view.py <match id> <start_s> <dur_s> <out.png> <file tag> [file tag ...]
"""
import os, sys
import numpy as np
import cv2
import lab

PW, PH, PX = 2600, 300, 1920.0     # panel size; x and y share the 1920 scale


def main(mid, start, dur, out, tags):
    pts = [(a, b) for a, b, _ in lab.gt_points(mid) if b > start and a < start + dur]
    panels = []
    for tag in tags:
        d = np.load(os.path.join(lab.mdir(mid), f"wasb_{int(start)}_{int(dur)}_{tag}.npy"))
        for col, name, vmax in ((1, "x", 1920.0), (2, "y", 1080.0)):
            im = np.full((PH, PW, 3), 255, np.uint8)
            for (p0, p1) in pts:
                x0, x1 = (int((max(p, start) - start) / dur * PW) for p in (p0, p1))
                im[:, x0:min(x1, PW)] = (215, 235, 255)
            for s in range(int(dur) + 1):
                x = int(s / dur * PW)
                cv2.line(im, (x, 0), (x, PH), (225, 225, 225) if s % 5 else (170, 170, 170), 1)
                if s % 5 == 0:
                    cv2.putText(im, str(int(start + s)), (x + 2, PH - 4), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (90, 90, 90), 1)
            xs = (d[:, 0] / 60.0 / dur * PW).astype(int)
            ys = (d[:, col] / vmax * (PH - 1)).astype(int)
            q = np.clip((d[:, 4] - 0.5) * 2, 0, 1)
            for x, y, v in zip(xs, ys, q):
                cv2.circle(im, (int(x), int(y)), 1, (0, int(200 * v), int(255 * (1 - v))), -1)
            cv2.putText(im, f"{tag} {name}", (6, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)
            panels.append(im)
            panels.append(np.zeros((3, PW, 3), np.uint8))
    cv2.imwrite(out, np.vstack(panels))


if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0], float(a[1]), float(a[2]), a[3], a[4:])

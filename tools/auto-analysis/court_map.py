"""Top view of one point: flights (3D fits) and bounces on a court diagram.

Input: <match>/flights_s<scale>.json (ball_3d.py points).
Flight to the far side = orange, to the near side = blue. The line goes
from the start of the fitted flight to its end. Label = speed in km/h.
Bounce (kink near the ground, position from the homography) = white dot;
red ring if it is outside the singles court.
Usage: python court_map.py <match id> <point index> <out.png> [scale]
"""
import json, os, sys
import numpy as np
import cv2
import lab
from winner_ball import good, SW, BL

DW, SL, PX, MX, MY = 5.485, 6.40, 26, 9.0, 17.0     # px per meter, half size of the view (m)


def P(x, y):
    return int((x + MX) * PX), int((MY - y) * PX)


def draw(mid, idx, out, scale="0.35"):
    pt = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{scale}.json")))[idx]
    im = np.full((int(2 * MY * PX), int(2 * MX * PX), 3), (70, 110, 60), np.uint8)
    cv2.rectangle(im, P(-DW, BL), P(DW, -BL), (150, 110, 60), -1)
    for a, b in (((-DW, -BL), (DW, -BL)), ((-DW, BL), (DW, BL)), ((-DW, -BL), (-DW, BL)), ((DW, -BL), (DW, BL)),
                 ((-SW, -BL), (-SW, BL)), ((SW, -BL), (SW, BL)), ((-SW, -SL), (SW, -SL)), ((-SW, SL), (SW, SL)), ((0, -SL), (0, SL))):
        cv2.line(im, P(*a), P(*b), (255, 255, 255), 2)
    cv2.line(im, P(-DW - 0.9, 0), P(DW + 0.9, 0), (40, 40, 40), 3)
    fl = [f for f in pt["flights"] if good(f)]
    for f in fl:
        col = (0, 165, 255) if f["v0"][1] > 0 else (255, 200, 80)
        a, b = P(*f["p0"][:2]), P(*f["p1"][:2])
        cv2.arrowedLine(im, a, b, col, 2, cv2.LINE_AA, tipLength=0.04)
        sp = 3.6 * float(np.linalg.norm(f["v0"]))
        cv2.putText(im, f"{sp:.0f}", (a[0] + 5, a[1] - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.45, col, 1, cv2.LINE_AA)
    for k in pt["kinks"]:
        t, cx, cy = k[0], k[3], k[4]
        pre = [f for f in fl if 0 <= t - f["t1"] < 0.2]
        post = [f for f in fl if 0 <= f["t0"] - t < 0.2]
        if (pre and pre[-1]["p1"][2] < 0.7) or (post and post[0]["p0"][2] < 0.6 and post[0]["v0"][2] > 0):
            if abs(cx) < MX and abs(cy) < MY:
                cv2.circle(im, P(cx, cy), 5, (255, 255, 255), -1, cv2.LINE_AA)
                if abs(cx) > SW or abs(cy) > BL:
                    cv2.circle(im, P(cx, cy), 9, (0, 0, 255), 2, cv2.LINE_AA)
    cv2.putText(im, f"{mid} point {idx}  {pt['start']:.1f}-{pt['end']:.1f} s  coverage {pt['coverage']:.2f}", (8, 20),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)
    cv2.imwrite(out, im)


if __name__ == "__main__":
    a = sys.argv[1:]
    draw(a[0], int(a[1]), a[2], *a[3:4])

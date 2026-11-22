"""E10c: frame sheet of the time before a marked point, to see by eye if a first serve was there.

One sheet for each point: crops around the server's baseline, one each STEP s, from the start of
the pre-window (faults.json w0, at most MAX_S s) to the point start. The last cells show the marked (second or
in) serve as a reference.
Usage: python scan_sheet.py <match id> <out folder> <point index> [...]
"""
import json, os, subprocess, sys
import numpy as np
import cv2
import lab
from fault_sheet import box

STEP, CH, MAX_S = 0.5, 120, 14.0


def main(mid, out, idx):
    os.makedirs(out, exist_ok=True)
    recs = json.load(open(os.path.join(lab.mdir(mid), "faults.json")))
    for i in idx:
        r = recs[i]
        w0, t1, near = max(r["w0"], r["start"] - MAX_S), r["start"] + 1.0, r["serverNear"]
        n = int((t1 - w0) / STEP)
        cmd = ["ffmpeg", "-v", "error", "-ss", f"{w0:.3f}", "-i", lab.MATCHES[mid]["video"], "-t", f"{t1 - w0:.3f}",
               "-vf", f"fps={1 / STEP},scale=1920:1080,format=bgr24", "-f", "rawvideo", "-"]
        fr = np.frombuffer(subprocess.run(cmd, stdout=subprocess.PIPE).stdout, np.uint8).reshape(-1, 1080, 1920, 3)
        x0, y0, x1, y1 = box(mid, r["start"], near)
        cells = []
        for k, f in enumerate(fr[:n]):
            im = f[y0:y1, x0:x1]
            im = cv2.resize(im, (int(im.shape[1] * CH / im.shape[0]), CH))
            cv2.putText(im, f"{w0 + k * STEP - r['start']:+.1f}", (4, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1, cv2.LINE_AA)
            cells.append(im)
        cols = max(1, 1900 // cells[0].shape[1])
        while len(cells) % cols:
            cells.append(np.zeros_like(cells[0]))
        sheet = np.vstack([np.hstack(cells[j:j + cols]) for j in range(0, len(cells), cols)])
        cv2.imwrite(os.path.join(out, f"{mid}_p{i}.jpg"), sheet, [cv2.IMWRITE_JPEG_QUALITY, 80])
        print(i, sheet.shape, "near" if near else "far", flush=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], [int(x) for x in sys.argv[3:]])

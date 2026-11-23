"""Person boxes and body poses on the 1 fps keyframes.

Models (Apache-2.0, ONNX, through rtmlib): YOLOX-m person detector and
RTMPose-m (17 COCO keypoints). The detector runs on the full frame and on 3
tiles of the upper part of the frame, because the far players are small.

Writes <match>/pose.npy: one row for each person, columns
  frame (0-based second), x1, y1, x2, y2, box score, then 17 x (x, y, conf).
Coordinates are in pixels of the 1920-wide keyframe.

Usage: python pose_extract.py [--limit N] [--viz] [match id ...]
"""
import glob, os, sys, time
import numpy as np
import torch  # noqa: F401  (loads the CUDA 12 DLLs; ONNX Runtime 1.22 needs them)
import cv2
from rtmlib import YOLOX, RTMPose
import lab
import platform_cfg

DET = "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/yolox_m_8xb8-300e_humanart-c2c7a14a.zip"
POSE = "https://download.openmmlab.com/mmpose/v1/projects/rtmposev1/onnx_sdk/rtmpose-m_simcc-body7_pt-body7_420e-256x192-e48f03d0_20230504.zip"


def tiles(w, h):
    """Full frame, then 2 overlapping tiles of the upper part (y from 5 % to 65 %)."""
    y0, y1 = int(0.05 * h), int(0.65 * h)
    tw = int(0.55 * w)
    return [(0, 0, w, h)] + [(x, y0, x + tw, y1) for x in (0, w - tw)]


def merge(boxes):
    """Suppress duplicates from overlapping tiles: keep the larger box when
    the intersection covers 60 % of the smaller box."""
    if not len(boxes):
        return boxes
    b = boxes[np.argsort(-(boxes[:, 2] - boxes[:, 0]) * (boxes[:, 3] - boxes[:, 1]))]
    keep = []
    for x in b:
        dup = False
        for k in keep:
            iw = min(x[2], k[2]) - max(x[0], k[0]); ih = min(x[3], k[3]) - max(x[1], k[1])
            if iw > 0 and ih > 0 and iw * ih >= 0.6 * (x[2] - x[0]) * (x[3] - x[1]):
                dup = True; break
        if not dup:
            keep.append(x)
    return np.array(keep)


def detect(det, img):
    h, w = img.shape[:2]
    out = []
    for (x0, y0, x1, y1) in tiles(w, h):
        bb = det(img[y0:y1, x0:x1])
        for b in bb:
            # drop boxes cut by an inner tile edge; the next tile or the full frame has them
            if (x0 > 0 and b[0] < 3) or (x1 < w and b[2] > (x1 - x0) - 3):
                continue
            out.append([b[0] + x0, b[1] + y0, b[2] + x0, b[3] + y0])
    return merge(np.array(out, dtype=np.float32).reshape(-1, 4))


def run(mid, det, pose, limit=0, viz=False):
    out = lab.mdir(mid)
    files = sorted(glob.glob(os.path.join(out, "kf", "*.jpg")))
    if limit:
        step = max(1, len(files) // limit)
        files = files[::step][:limit]
    rows, t = [], time.time()
    for n, f in enumerate(files):
        img = cv2.imread(f)
        frame = int(os.path.basename(f)[:-4]) - 1
        boxes = detect(det, img)
        if len(boxes):
            kp, sc = pose(img, bboxes=boxes.tolist())
            for b, k, s in zip(boxes, kp, sc):
                rows.append(np.concatenate([[frame], b, [float(s.mean())], np.concatenate([k, s[:, None]], 1).ravel()]))
            if viz:
                for b, k, s in zip(boxes, kp, sc):
                    cv2.rectangle(img, (int(b[0]), int(b[1])), (int(b[2]), int(b[3])), (0, 255, 255), 2)
                    for (x, y), c in zip(k, s):
                        if c > 0.4:
                            cv2.circle(img, (int(x), int(y)), 3, (0, 0, 255), -1)
        if viz:
            os.makedirs(os.path.join(out, "viz"), exist_ok=True)
            cv2.imwrite(os.path.join(out, "viz", os.path.basename(f)), img, [cv2.IMWRITE_JPEG_QUALITY, 80])
        if n % 1000 == 999:
            print(mid, n + 1, "frames, %.0f s" % (time.time() - t), flush=True)
    arr = np.array(rows, dtype=np.float32).reshape(-1, 6 + 51)
    if not limit:
        np.save(os.path.join(out, "pose.npy"), arr)
    print(mid, "frames", len(files), "persons", len(arr), "%.0f s" % (time.time() - t), flush=True)


if __name__ == "__main__":
    args = sys.argv[1:]
    viz = "--viz" in args
    limit = int(args[args.index("--limit") + 1]) if "--limit" in args else 0
    ids = [a for a in args if a.startswith("m")]
    det = YOLOX(platform_cfg.pose_model(DET), model_input_size=(640, 640), backend="onnxruntime", device=platform_cfg.ort_device())
    pose = RTMPose(platform_cfg.pose_model(POSE), model_input_size=(192, 256), backend="onnxruntime", device=platform_cfg.ort_device())
    for mid in (ids or lab.LABELED):
        if os.path.exists(os.path.join(lab.mdir(mid), "kf.done")) and (limit or not os.path.exists(
                os.path.join(lab.mdir(mid), "pose.npy"))):
            run(mid, det, pose, limit, viz)

"""Shared helpers of the auto-analysis lab: match registry, ground truth,
per-second labels, segmentation of a probability curve into points, metrics.

Time base: all per-second arrays use index s for the interval [s, s+1) s.
"""
import json, os
import numpy as np
import platform_cfg

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, "matches.json"), encoding="utf-8"))
ROOT = platform_cfg.data_root(CFG["labRoot"])
MATCHES = {m["id"]: m for m in CFG["matches"]}
# testOnly (matches.json): a marked video for the held-out benchmark (pipeline.py, E43). No training and no
# fit may use it. LABELED is the marked videos for training and development only.
TEST_ONLY = [m["id"] for m in CFG["matches"] if m.get("testOnly")]
LABELED = [m["id"] for m in CFG["matches"] if m.get("project") and not m.get("testOnly")]
TEST = [m["id"] for m in CFG["matches"] if m.get("project") and m.get("testOnly")]


def check_train(mids):
    """Stops a training or a fit that would use a test-only video."""
    bad = sorted(set(mids) & set(TEST_ONLY))
    if bad:
        raise ValueError(f"test-only videos must not be in a training or a fit: {bad}")
    return list(mids)
BALL_TAG = os.environ.get("BALL_TAG", "0.35")     # ball detector version of the flight files, e.g. "0.35_e15b_a"


def mdir(mid):
    return os.path.join(ROOT, mid)


def n_seconds(mid):
    """Count of whole seconds = count of keyframes."""
    with open(os.path.join(mdir(mid), "kf_times.csv")) as f:
        return sum(1 for ln in f if ln.strip())


def gt_points(mid):
    """Hand-marked points as a list of (start_s, end_s, outcome)."""
    g = json.load(open(os.path.join(mdir(mid), "gt.json"), encoding="utf-8"))
    return [(p["startMs"] / 1000.0, p["endMs"] / 1000.0, p["outcome"]) for p in g["points"]]


def eval_span(mid, pad=20.0):
    """The part of the video that has hand marks. Outside it nothing is known."""
    pts = gt_points(mid)
    return max(0.0, pts[0][0] - pad), pts[-1][1] + pad


def labels(mid):
    """Per-second label: 1 = at least half of the second is inside a point."""
    n = n_seconds(mid)
    y = np.zeros(n, dtype=np.float32)
    for a, b, _ in gt_points(mid):
        for s in range(int(a), min(n, int(b) + 1)):
            y[s] += max(0.0, min(b, s + 1) - max(a, s))
    return (y >= 0.5).astype(np.int8)


def span_mask(mid):
    n = n_seconds(mid)
    a, b = eval_span(mid)
    s = np.arange(n)
    return (s >= a) & (s < b)


def segment(prob, hi=0.6, lo=0.4, min_len=3.0, join_gap=1.5, smooth=3, step=1.0):
    """Probability curve -> list of (start_s, end_s). Hysteresis thresholds."""
    p = np.asarray(prob, dtype=np.float32)
    if smooth > 1:
        k = np.ones(smooth, dtype=np.float32) / smooth
        p = np.convolve(p, k, mode="same")
    segs, inside, start = [], False, 0
    for i, v in enumerate(p):
        if not inside and v >= hi:
            # extend the start back while the curve stays above lo
            j = i
            while j > 0 and p[j - 1] >= lo and (not segs or (j - 1) * step >= segs[-1][1]):
                j -= 1
            inside, start = True, j
        elif inside and v < lo:
            segs.append((start * step, i * step))
            inside = False
    if inside:
        segs.append((start * step, len(p) * step))
    joined = []
    for a, b in segs:
        if joined and a - joined[-1][1] < join_gap:
            joined[-1] = (joined[-1][0], b)
        else:
            joined.append((a, b))
    return [(a, b) for a, b in joined if b - a >= min_len]


def match_points(pred, gt):
    """One-to-one match. A match = overlap >= 50 % of the shorter point."""
    used, pairs = set(), []
    for gi, g in enumerate(gt):
        best, bj = 0.0, None
        for j, p in enumerate(pred):
            if j in used or p[1] < g[0] or p[0] > g[1]:
                continue
            ov = min(p[1], g[1]) - max(p[0], g[0])
            short = min(p[1] - p[0], g[1] - g[0])
            if short > 0 and ov / short >= 0.5 and ov > best:
                best, bj = ov, j
        if bj is not None:
            used.add(bj)
            pairs.append((gi, bj))
    return pairs


def point_metrics(pred, mid):
    gt = gt_points(mid)
    a, b = eval_span(mid)
    pred = [p for p in pred if p[1] > a and p[0] < b]
    pairs = match_points(pred, gt)
    ds = np.array([pred[j][0] - gt[i][0] for i, j in pairs]) if pairs else np.zeros(1)
    de = np.array([pred[j][1] - gt[i][1] for i, j in pairs]) if pairs else np.zeros(1)
    return {
        "gt": len(gt), "pred": len(pred), "hit": len(pairs),
        "recall": len(pairs) / max(1, len(gt)),
        "precision": len(pairs) / max(1, len(pred)),
        "start_med": float(np.median(ds)), "start_abs_med": float(np.median(np.abs(ds))),
        "start_abs_p90": float(np.percentile(np.abs(ds), 90)),
        "end_med": float(np.median(de)), "end_abs_med": float(np.median(np.abs(de))),
        "end_abs_p90": float(np.percentile(np.abs(de), 90)),
        "missed": [round(gt[i][0], 1) for i in range(len(gt)) if i not in {p[0] for p in pairs}],
        "extra": [round(pred[j][0], 1) for j in range(len(pred)) if j not in {p[1] for p in pairs}],
    }


def fmt(m):
    return ("gt %3d pred %3d  recall %5.1f%%  precision %5.1f%%  start |med| %.1f p90 %.1f (bias %+.1f)  "
            "end |med| %.1f p90 %.1f (bias %+.1f)" % (
                m["gt"], m["pred"], 100 * m["recall"], 100 * m["precision"], m["start_abs_med"],
                m["start_abs_p90"], m["start_med"], m["end_abs_med"], m["end_abs_p90"], m["end_med"]))

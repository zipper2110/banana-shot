"""Ball detection with the WASB-SBDT tennis model (HRNet, MIT license).

Model: https://github.com/nttcom/WASB-SBDT. Input = 3 successive RGB frames
(9 channels), output = 3 heatmaps (one for each frame). Trained on TV
broadcast video (TrackNet tennis data set, 1280 x 720 at 30 fps, input
512 x 288). The network is fully convolutional. This script gives it the
court crop at a selected scale, not a 512 x 288 frame. The network
definition is our own file wasb_hrnet.py; only the weights file comes from
the WASB authors (loaded as tensors only).

Decode: T3 (all frames, GPU). "stride 2" gives the model frames that are
1/30 s apart (as in training). The two phases (even and odd frames) run as
separate triplets, so each 60-fps frame gets a heatmap.

Output: <match>/wasb_<start>_<dur>_s<scale>_k<stride>.npy with rows
frame, x, y, score, peak. x, y are in the 1920 x 1080 frame. Up to 4 blobs
for each frame. Frame 0 = the first decoded frame of the window.
Usage: python ball_wasb.py <match id> <start_s> <dur_s> [scale] [stride]
       python ball_wasb.py <match id> points [scale]     (all marked points)
       python ball_wasb.py <match id> pre [scale]        (the window before each marked point)

E27, optional passes (environment variable BALL_PASSES; the default is empty = the detector as before, with the
same bytes in the output):
  dim   a second pass of the model with a dimmed picture (dim()). The model does not find a ball that is darker
        than its background (a yellow ball in front of a white wall). The blobs of the passes are merged.
  low   one more pass at half of the scale for each picture (a near ball is too large for the model at scale 0.35).
  Example: BALL_PASSES=dim,low. With a pass, the picture is the full court region (padded to a multiple of 32),
  and the output files of a lab match get the tag "_e27" (the validated files stay).
"""
import os, subprocess, sys, time
import numpy as np
import cv2
import torch
import lab
import platform_cfg
import wasb_hrnet
from burst_ball import roi_mask

EXT = os.path.dirname(platform_cfg.weights_dir())
MEAN = np.array([0.485, 0.456, 0.406], np.float32)
STD = np.array([0.229, 0.224, 0.225], np.float32)


WEIGHTS = os.environ.get("BALL_WEIGHTS", "wasb_tennis.pth.tar")     # E15b: a fine-tuned file in the same folder
TAG = "" if WEIGHTS == "wasb_tennis.pth.tar" else "_" + os.path.splitext(WEIGHTS)[0]


# E27: more passes of the model. See the module text.
PASSES = [p for p in os.environ.get("BALL_PASSES", "").replace("+", ",").split(",") if p]
if set(PASSES) - {"dim", "low"}:
    raise RuntimeError("BALL_PASSES: use dim, low, or dim,low.")
DIM_LO, DIM_S0 = 0.3, 0.45       # dim(): factor for a pixel with no color; saturation from which a pixel stays as it is
MERGE_R = 6.0                    # px of the 1920 frame: two blobs of different passes are one ball
if PASSES:
    TAG += "_e27"


def lab_tag(mid):
    """File name tag for the blob file of run(): "_e27" for a lab match when passes are on, so that the validated
    file of the lab match stays. A work folder of detect.py keeps the name that detect.py expects."""
    return "_e27" if PASSES and mid in {m["id"] for m in lab.CFG["matches"]} else ""


def dim(x):
    """E27. x: pictures n, 3, h, w with values 0..1. Each pixel is multiplied by
    DIM_LO + (1 - DIM_LO) * min(1, saturation / DIM_S0), saturation = (max - min) / max of R, G, B.
    A white or gray background becomes dark; a pixel with a clear color (the ball) stays. Then the ball is
    brighter than a white wall, as the model needs it."""
    xf = x.float()
    mx, mn = xf.amax(1, keepdim=True), xf.amin(1, keepdim=True)
    a = DIM_LO + (1 - DIM_LO) * ((mx - mn) / mx.clamp(min=1 / 255) / DIM_S0).clamp(0, 1)
    return platform_cfg.fp(xf * a)


def merge_passes(lists):
    """lists: for each pass, the blobs of one frame (x, y, score, peak, scale factor; px of the 1920 frame).
    A blob within MERGE_R px (2 * MERGE_R if one of the two is from a half-scale pass) of a blob with a higher
    peak is the same ball: drop it. Returns the blobs (x, y, score, peak), highest score first."""
    al = sorted((b for bl in lists for b in bl), key=lambda b: -b[3])
    keep = []
    for b in al:
        if not any(abs(b[0] - k[0]) < MERGE_R * max(b[4], k[4]) and abs(b[1] - k[1]) < MERGE_R * max(b[4], k[4]) for k in keep):
            keep.append(b)
    return sorted((b[:4] for b in keep), key=lambda b: -b[2])


FPS = None           # a number: decode at this constant frame rate (detect.py: 30 for a video that is not 60 fps)
PROGRESS = None      # optional function (frames done), called each 3600 frames (detect.py)


def load_model():
    return platform_cfg.fp(platform_cfg.to_device(wasb_hrnet.load(EXT + "/weights/" + WEIGHTS)))


def crop_box(mid, scale, full=False):
    """Bounding box of the court region (see burst_ball.roi_mask) at the given scale.
    The size is a multiple of 32. full (E27): the full court region and all of the frame above it; the size is then not a multiple of 32
    (run() pads the pictures)."""
    ys, xs = np.nonzero(roi_mask(mid))
    x0, y0, x1, y1 = (int(v * scale) for v in (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    fw, fh = int(1920 * scale), int(1080 * scale)
    if full:
        x0, y0 = max(0, x0) // 2 * 2, 0              # x0 even (the ffmpeg crop of an nv12 picture starts at an even px); up to the top of the frame (a lob)
        return x0, y0, (min(fw, x1) - x0) // 2 * 2, (min(fh, y1) - y0) // 2 * 2, fw, fh
    cw, ch = -(-(x1 - x0) // 32) * 32, -(-(y1 - y0) // 32) * 32
    cw, ch = min(cw, fw // 32 * 32), min(ch, fh // 32 * 32)
    x0, y0 = max(0, min(x0, fw - cw)), max(0, min(y0, fh - ch))
    return x0, y0, cw, ch, fw, fh


def frames(mid, start, dur, scale):
    """Yields RGB crops of all frames of the window."""
    x0, y0, cw, ch, fw, fh = crop_box(mid, scale, bool(PASSES))
    if platform_cfg.decode() != "cuda":
        # No NVIDIA GPU: the same pictures, with the scale_cuda kernel on the CPU (cuda_scale.py).
        import cuda_scale
        yield from cuda_scale.rgb_frames(lab.MATCHES[mid]["video"], start, dur, FPS, fw, fh, x0, y0, cw, ch)
        return
    cmd = [platform_cfg.tool("ffmpeg"), "-v", "error", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda",
           "-ss", str(start), "-t", str(dur), "-i", lab.MATCHES[mid]["video"],
           "-vf", (f"fps={FPS}," if FPS else "") + f"scale_cuda={fw}:{fh},hwdownload,format=nv12,crop={cw}:{ch}:{x0}:{y0},format=rgb24",
           "-fps_mode", "passthrough", "-f", "rawvideo", "-"]
    size = cw * ch * 3
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=size * 4)
    while True:
        buf = p.stdout.read(size)
        if len(buf) < size:
            break
        yield np.frombuffer(buf, np.uint8).reshape(ch, cw, 3)


def blobs(hm, thr=0.5, top=4):
    """Heatmap -> list of (x, y, score, peak). Same rule as the WASB postprocessor
    (connected components above the threshold, heatmap-weighted center)."""
    out = []
    if hm.max() <= thr:
        return out
    n, lab_, st, _ = cv2.connectedComponentsWithStats((hm > thr).astype(np.uint8), connectivity=8)
    for i in range(1, n):
        x, y, w, h, _ = st[i]
        sub = hm[y:y + h, x:x + w] * (lab_[y:y + h, x:x + w] == i)
        s = float(sub.sum())
        yy, xx = np.mgrid[0:h, 0:w]
        out.append((x + float((xx * sub).sum()) / s, y + float((yy * sub).sum()) / s, s, float(sub.max())))
    return sorted(out, key=lambda b: -b[2])[:top]


def run(mid, start, dur, scale=0.35, stride=2, save=True, net=None, quiet=False):
    net = net or load_model()
    x0, y0, cw, ch, _, _ = crop_box(mid, scale, bool(PASSES))
    mean = platform_cfg.fp(platform_cfg.to_device(torch.tensor(MEAN).view(1, 3, 1, 1)))
    std = platform_cfg.fp(platform_cfg.to_device(torch.tensor(STD).view(1, 3, 1, 1)))
    rows, t0, group, base = [], time.time(), [], 0
    need = 3 * stride

    def flush(fr, base):
        x = platform_cfg.fp(platform_cfg.to_device(torch.from_numpy(np.stack(fr))).permute(0, 3, 1, 2)).div_(255)
        if PASSES:
            flush_passes(x, base)
            return
        x = ((x - mean) / std)                                   # n, 3, h, w
        trip = [list(range(ph, len(fr), stride))[:3] for ph in range(stride)]
        trip = [t for t in trip if len(t) == 3]
        if not trip:
            return
        inp = torch.stack([x[t].reshape(9, ch, cw) for t in trip])
        with torch.no_grad():
            hm = torch.sigmoid(net(inp)).float().cpu().numpy()
        for t, h3 in zip(trip, hm):
            for k, h in zip(t, h3):
                for (bx, by, s, pk) in blobs(h):
                    rows.append((base + k, (bx + x0) / scale, (by + y0) / scale, s, pk))

    def flush_passes(x, base):
        """E27: the passes of BALL_PASSES. x: the pictures of a group, n, 3, h, w with values 0..1."""
        trip = [t for t in (list(range(ph, len(x), stride))[:3] for ph in range(stride)) if len(t) == 3]
        if not trip:
            return
        pics = [x] + ([dim(x)] if "dim" in PASSES else [])
        found = {}                                               # frame of the group -> one blob list for each pass
        for k in ([1.0, 2.0] if "low" in PASSES else [1.0]):     # k = 2: half of the scale
            for p in pics:
                if k > 1:
                    p = platform_cfg.fp(torch.nn.functional.avg_pool2d(p.float(), 2))
                p = (p - mean) / std
                h, w = p.shape[2:]
                p = torch.nn.functional.pad(p, (0, -w % 32, 0, -h % 32), mode="replicate")
                inp = torch.stack([p[t].reshape(9, *p.shape[2:]) for t in trip])
                with torch.no_grad():
                    hm = torch.sigmoid(net(inp)).float()[:, :, :h, :w].cpu().numpy()
                for t, h3 in zip(trip, hm):
                    for i, hi in zip(t, h3):
                        found.setdefault(i, []).append([((bx * k + x0) / scale, (by * k + y0) / scale, s * k * k, pk, k)
                                                        for (bx, by, s, pk) in blobs(hi)])
        for i in sorted(found):
            for (bx, by, s, pk) in merge_passes(found[i])[:6]:
                rows.append((base + i, bx, by, s, pk))

    for f in frames(mid, start, dur, scale):
        group.append(f)
        if len(group) == need:
            flush(group, base)
            base += need
            group = []
            if PROGRESS and base % 3600 == 0:
                PROGRESS(base)
    arr = np.array(rows, np.float32).reshape(-1, 5)
    el = time.time() - t0
    if not quiet:
      print(f"{mid} {start}+{dur}s scale {scale} stride {stride} crop {cw}x{ch}: frames {base}, "
            f"{base / max(el, 1e-6):.0f} fps ({base / 60 / max(el, 1e-6):.2f}x real time), blobs {len(arr)}, "
            f"frames with a blob {len(np.unique(arr[:, 0])) / max(base, 1):.2f}", flush=True)
    if save:
        np.save(os.path.join(lab.mdir(mid), f"wasb_{int(start)}_{int(dur)}_s{scale}{lab_tag(mid)}_k{stride}.npy"), arr)
    return arr


PRE, POST = 1.0, 2.5


def run_points(mid, scale=0.35):
    """All marked points of a match. Window of point i: [start - PRE, end + POST].
    Writes <match>/ballpts_s<scale>.npy: point index, frame, x, y, score, peak.
    Time of a row = start - PRE + frame / 60."""
    net, out, t0, total = load_model(), [], time.time(), 0.0
    pts = lab.gt_points(mid)
    for i, (a, b, _) in enumerate(pts):
        d = run(mid, max(0.0, a - PRE), b - a + PRE + POST, scale, 2, save=False, net=net, quiet=True)
        out.append(np.concatenate([np.full((len(d), 1), i, np.float32), d], 1))
        total += b - a + PRE + POST
        if i % 25 == 0:
            print(f"{mid} point {i}/{len(pts)} video {total:.0f} s in {time.time() - t0:.0f} s", flush=True)
    np.save(os.path.join(lab.mdir(mid), f"ballpts_s{scale}{TAG}.npy"), np.concatenate(out))
    print(f"{mid} done: {len(pts)} points, video {total:.0f} s in {time.time() - t0:.0f} s", flush=True)


def pre_windows(mid, before=22.0):
    """Window before each marked point: from the end of the point before it (+ 1 s), at most
    "before" s, to 1 s before the start. A first-serve fault is in this window (the marks skip it)."""
    pts, out, prev = lab.gt_points(mid), [], 0.0
    for a, b, _ in pts:
        w0, w1 = max(prev + 1.0, a - before), a - 1.0
        out.append((w0, max(w0, w1)))
        prev = b
    return out


def run_pre(mid, scale=0.35):
    """Writes <match>/ballpre_s<scale>.npy: point index, frame, x, y, score, peak (frame 0 = window start)."""
    net, out, t0, total = load_model(), [], time.time(), 0.0
    for i, (w0, w1) in enumerate(pre_windows(mid)):
        if w1 - w0 < 2.0:
            continue
        d = run(mid, w0, w1 - w0, scale, 2, save=False, net=net, quiet=True)
        out.append(np.concatenate([np.full((len(d), 1), i, np.float32), d], 1))
        total += w1 - w0
        if i % 25 == 0:
            print(f"{mid} pre-window {i} video {total:.0f} s in {time.time() - t0:.0f} s", flush=True)
    np.save(os.path.join(lab.mdir(mid), f"ballpre_s{scale}{TAG}.npy"), np.concatenate(out))
    print(f"{mid} pre-windows done: video {total:.0f} s in {time.time() - t0:.0f} s", flush=True)


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[1] == "pre":
        run_pre(a[0], float(a[2]) if len(a) > 2 else 0.35)
    elif a[1] == "points":
        run_points(a[0], float(a[2]) if len(a) > 2 else 0.35)
    else:
        run(a[0], float(a[1]), float(a[2]), float(a[3]) if len(a) > 3 else 0.35, int(a[4]) if len(a) > 4 else 2)

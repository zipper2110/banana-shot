"""Demo: a video clip of one marked point with the ball analysis drawn on it.

Drawn items:
  - yellow dots: the ball detections of the selected ball path (last 0.4 s);
  - cyan line: the fitted 3D flight (projected to the image) from its start to now;
  - a label at the start of each flight: speed in km/h;
  - ring on the ground: bounce point (seen bounce) or landing point (from the fit);
    green = in the singles court, red = out;
  - a small court (top view) with the ball position from the 3D fit and the bounce points;
  - text: point number, time, and at the end the winner from the decoder against the mark.
render_fault() makes a clip of a first-serve fault candidate from the window before a point.
The clip runs at half speed (60 source frames each second, 30 fps output).
Usage: python demo_clip.py <match id> <out folder> <point index> [...]
"""
import json, os, subprocess, sys
import numpy as np
import cv2
import lab
from ball_3d import camera, project, simulate
from ball_tracks import link, moving
from ball_events import select, court_H
from ball_wasb import PRE, POST

W, H = 960, 540
S = W / 1920.0
SW, BL, SL, DW = 4.115, 11.885, 6.40, 5.485       # singles half width, baseline, service line, doubles half width
MC_W, MC_H, MC_M = 130, 270, 12                 # small court: size and margin (px)


def mini(x, y):
    """Court meters -> pixel in the small court (far side at the top)."""
    u = MC_M + (x + 6.5) / 13.0 * (MC_W - 2 * MC_M)
    v = MC_M + (13.5 - y) / 27.0 * (MC_H - 2 * MC_M)
    return int(round(u)), int(round(v))


def mini_base():
    im = np.full((MC_H, MC_W, 3), (120, 80, 40), np.uint8)
    wh = (255, 255, 255)
    cv2.rectangle(im, mini(-DW, BL), mini(DW, -BL), wh, 1)
    cv2.rectangle(im, mini(-SW, BL), mini(SW, -BL), wh, 1)
    cv2.line(im, mini(-SW, SL), mini(SW, SL), wh, 1); cv2.line(im, mini(-SW, -SL), mini(SW, -SL), wh, 1)
    cv2.line(im, mini(0, SL), mini(0, -SL), wh, 1)
    cv2.line(im, mini(-6.2, 0), mini(6.2, 0), (200, 200, 200), 2)
    return im


def text(im, s, org, scale=0.6, col=(255, 255, 255), th=1):
    cv2.putText(im, s, org, cv2.FONT_HERSHEY_SIMPLEX, scale, (0, 0, 0), th + 3, cv2.LINE_AA)
    cv2.putText(im, s, org, cv2.FONT_HERSHEY_SIMPLEX, scale, col, th, cv2.LINE_AA)


def is_in(x, y):
    return abs(x) <= SW + 0.05 and abs(y) <= BL + 0.05


def render(mid, i, out, pred=None, label=None):
    """Clip of marked point i. pred: optional dict with "pNearWins" and "truthNear" for the end text.
    Returns the file path."""
    a, b, _ = lab.gt_points(mid)[i]
    w0, w1 = max(0.0, a - PRE), b + POST
    d = np.load(os.path.join(lab.mdir(mid), f"ballpts_s{lab.BALL_TAG}.npy"))
    path = select(moving(link(d[d[:, 0] == i, 1:])))
    pt = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{lab.BALL_TAG}.json")))[i]
    return draw(mid, os.path.join(out, f"{mid}_p{i}.mp4"), w0, w1, path, pt["flights"], a, (a, b), pred, label or f"{mid} point {i + 1}")


def render_fault(mid, i, out, t):
    """Clip of the first-serve fault candidate at time t in the window before marked point i."""
    from ball_wasb import pre_windows
    p0 = pre_windows(mid)[i][0]
    d = np.load(os.path.join(lab.mdir(mid), f"ballpre_s{lab.BALL_TAG}.npy"))
    path = select(moving(link(d[d[:, 0] == i, 1:])))
    w0, w1 = max(p0, t - 2.0), t + 2.5
    path = path.copy(); path[:, 0] -= round((w0 - p0) * 60)
    pre = json.load(open(os.path.join(lab.mdir(mid), f"preflights_s{lab.BALL_TAG}.json")))[i]
    return draw(mid, os.path.join(out, f"{mid}_p{i}_fault.mp4"), w0, w1, path, pre["flights"], t, None, None,
                f"{mid}: serve before point {i + 1} (fault candidate)")


def draw(mid, fn, w0, w1, path, flights, t_court, rally, pred, label):
    a, b = rally if rally else (1e18, 1e18)
    P, _, _ = camera(court_H(mid, t_court))
    fls = [f for f in flights if f["n"] >= 8 and f["rms"] < 5 and 8 < 3.6 * np.linalg.norm(f["v0"]) < 240]
    marks = []                                     # (time, x, y, seen bounce?)
    for f in fls:
        if f.get("bounce"):
            marks.append((f["t1"], f["bounce"][0], f["bounce"][1], True))
    last = fls[-1] if fls else None
    if last is not None and not last.get("bounce") and last["land"][0] == last["land"][0] and last["landT"] < w1:
        marks.append((last["landT"], last["land"][0], last["land"][1], False))
    cmd = ["ffmpeg", "-v", "error", "-ss", f"{w0:.3f}", "-t", f"{w1 - w0:.3f}", "-i", lab.MATCHES[mid]["video"],
           "-vf", f"scale={W}:{H},format=bgr24", "-fps_mode", "passthrough", "-f", "rawvideo", "-"]
    src = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=W * H * 3 * 4)
    os.makedirs(os.path.dirname(fn), exist_ok=True)
    enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", f"{W}x{H}", "-r", "30", "-i", "-",
                            "-c:v", "libx264", "-preset", "fast", "-crf", "27", "-pix_fmt", "yuv420p", "-movflags", "+faststart", fn],
                           stdin=subprocess.PIPE)
    base = mini_base()
    k = 0
    while True:
        buf = src.stdout.read(W * H * 3)
        if len(buf) < W * H * 3:
            break
        im = np.frombuffer(buf, np.uint8).reshape(H, W, 3).copy()
        t = w0 + k / 60.0
        mc = base.copy()
        for (mt, x, y, seen) in marks:              # bounce and landing points
            if t >= mt:
                col = (80, 220, 80) if is_in(x, y) else (60, 60, 255)
                g = project(P, np.array([[x, y, 0.0]]))[0] * S
                if t - mt < 2.0 and 0 <= g[0] < W and 0 <= g[1] < H:
                    cv2.ellipse(im, (int(g[0]), int(g[1])), (11, 5), 0, 0, 360, col, 2, cv2.LINE_AA)
                cv2.circle(mc, mini(x, y), 4, col, -1 if seen else 1, cv2.LINE_AA)
        for f in fls:                               # fitted flights
            if f["t0"] <= t <= f["t1"] + 0.25:
                ts = np.arange(0, min(t, f["t1"]) - f["t0"] + 1e-6, 1 / 60)
                if len(ts) >= 2:
                    X = simulate(np.array(f["p0"]), np.array(f["v0"]), ts)
                    xy = (project(P, X) * S).astype(np.int32)
                    cv2.polylines(im, [xy.reshape(-1, 1, 2)], False, (255, 230, 0), 2, cv2.LINE_AA)
                    if t <= f["t1"]:
                        cv2.circle(mc, mini(X[-1, 0], X[-1, 1]), 3, (0, 255, 255), -1, cv2.LINE_AA)
                    if t - f["t0"] < 0.9:
                        text(im, f"{3.6 * np.linalg.norm(f['v0']):.0f} km/h", (int(xy[0, 0]) + 10, int(xy[0, 1]) - 10), 0.55, (255, 230, 0))
        fr = path[(path[:, 0] <= k) & (path[:, 0] > k - 24)]
        for r in fr:                                # ball detections
            cv2.circle(im, (int(r[1] * S), int(r[2] * S)), 3 if r[0] == k else 2, (0, 255, 255), -1, cv2.LINE_AA)
        im[10:10 + MC_H, W - MC_W - 10:W - 10] = cv2.addWeighted(im[10:10 + MC_H, W - MC_W - 10:W - 10], 0.25, mc, 0.75, 0)
        text(im, label, (12, 24), 0.6)
        text(im, f"{int(t // 60)}:{t % 60:04.1f}  (half speed)", (12, 46), 0.5, (220, 220, 220))
        if a <= t <= b:
            text(im, "RALLY (mark)", (12, 68), 0.5, (80, 220, 80))
        if pred is not None and rally and t > b - 0.5:
            pn = pred["pNearWins"]
            who = "near player" if pn > 0.5 else "far player"
            ok = pred.get("truthNear")
            col = (255, 255, 255) if ok is None else ((80, 220, 80) if (pn > 0.5) == ok else (60, 60, 255))
            text(im, f"Winner (auto): {who}  {100 * max(pn, 1 - pn):.0f}%", (12, H - 40), 0.7, col, 2)
            if ok is not None:
                text(im, f"Mark: {'near' if ok else 'far'} player", (12, H - 14), 0.55, (220, 220, 220))
        enc.stdin.write(im.tobytes())
        k += 1
    enc.stdin.close(); enc.wait()
    return fn


if __name__ == "__main__":
    for x in sys.argv[3:]:
        print(render(sys.argv[1], int(x), sys.argv[2]), flush=True)

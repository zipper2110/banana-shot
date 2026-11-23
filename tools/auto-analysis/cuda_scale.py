"""The picture path of the chain for a computer with no NVIDIA GPU (docs/auto-analysis/mac-setup.md).

On the lab PC, ffmpeg decodes on the GPU and the filter scale_cuda makes the small pictures. The ball
detector and the other models were measured with these pictures. The software scaler of ffmpeg gives
other pixels (it has an anti-alias filter; scale_cuda has none). So this module makes the same
pictures as scale_cuda on the CPU:

  ffmpeg 1 (decode, yuv420p)  ->  scale() in this file  ->  ffmpeg 2 (NV12 to RGB, or JPEG files)

scale() is the bicubic kernel of scale_cuda (libavfilter/cuda/vf_scale_cuda.cu, default parameter 0)
with the same 32-bit float operations in the same sequence (the GPU code uses fused multiply-add).
Measured on the lab PC: the output is bit-exact (E24). The decoders of H.264 and HEVC are bit-exact
by their standards. ffmpeg 2 does the same conversion as the last filters of the GPU chain.
"""
import collections, json, os, subprocess, threading
from concurrent.futures import ThreadPoolExecutor
import numpy as np
import platform_cfg

F32, F64 = np.float32, np.float64
WORKERS = max(1, min(4, (os.cpu_count() or 2) - 1))
BLOCK = 16


def _fma(a, b, c):
    """a * b + c with one rounding (the product of two 32-bit floats is exact in 64 bits)."""
    return (a.astype(F64) * b + c).astype(F32)


def _taps(src, dst, d0, dn):
    """For the output positions d0 .. d0 + dn - 1 of an axis (src pixels -> dst pixels): the 3 source
    indices of each position (clamped to the picture) and their 3 weights."""
    scale = F32(src) / F32(dst)
    xi = (np.arange(d0, d0 + dn, dtype=F32) + F32(0.5)) * scale - F32(0.5)
    px = np.floor(xi)
    x = xi - px
    one = F32(1)
    y = one - x
    c1 = _fma((F32(2) * x - F32(3)) * x, x, one)
    c2 = _fma((F32(2) * y - F32(3)) * y, y, one)
    c3 = one - c1 - c2
    idx = np.clip(px.astype(np.int64)[None, :] + np.arange(3)[:, None], 0, src - 1)
    return idx, (c1, c2, c3)


class Plane:
    """Scales a window of one 8-bit plane. (sw, sh): size of the source plane. (dw, dh): size of the full
    scaled plane. (x0, y0, w, h): the window of the scaled plane to make."""

    def __init__(self, sw, sh, dw, dh, x0=0, y0=0, w=None, h=None):
        w, h = w or dw, h or dh
        self.ix, self.cx = _taps(sw, dw, x0, w)
        iy, cy = _taps(sh, dh, y0, h)
        self.rows, inv = np.unique(iy, return_inverse=True)        # each source row is filtered one time
        self.iy = inv.reshape(iy.shape)
        self.cx = [c.astype(F64) for c in self.cx]
        self.cy = [c[:, None].astype(F64) for c in cy]
        self.box = (int(self.ix.min()), int(self.rows.min()), int(self.ix.max()) + 1, int(self.rows.max()) + 1)   # source x0, y0, x1, y1
        self.unit = (np.arange(256, dtype=F32) / F32(255)).astype(F64)      # the GPU reads a pixel as a float from 0 to 1
        self.flat = {}

    @staticmethod
    def _mix(c, t):
        """c[0] * t[0] + c[1] * t[1] + c[2] * t[2] as the GPU does it: one 32-bit product, then two fused
        multiply-adds. All values are 32-bit floats that are kept in 64-bit arrays."""
        z = (c[0] * t[0]).astype(F32)
        z = (c[1] * t[1] + z).astype(F32)
        return (c[2] * t[2] + z).astype(F32)

    def __call__(self, src, ox=0, oy=0):
        """src: the source plane, or a part of it that starts at column ox and row oy."""
        key = (src.shape[1], ox, oy)
        if key not in self.flat:
            self.flat[key] = [((self.rows - oy)[:, None] * src.shape[1] + (self.ix[k] - ox)[None, :]).astype(np.intp) for k in range(3)]
        flat, idx = src.reshape(-1), self.flat[key]
        hx = np.empty(idx[0].shape, F32)
        for a in range(0, len(hx), BLOCK):                 # blocks of rows: the temporary arrays stay in the CPU cache
            hx[a:a + BLOCK] = self._mix(self.cx, [self.unit[flat.take(i[a:a + BLOCK])] for i in idx])
        out = np.empty((self.iy.shape[1], hx.shape[1]), np.uint8)
        for a in range(0, len(out), BLOCK):
            v = self._mix([c[a:a + BLOCK] for c in self.cy], [hx[self.iy[k, a:a + BLOCK]] for k in range(3)]) * F32(255)
            out[a:a + BLOCK] = np.clip(v, 0, 255)
        return out


def probe(video):
    """Width, height (as ffmpeg gives the frames), and the color properties of the first video stream."""
    r = subprocess.run([platform_cfg.tool("ffprobe"), "-v", "error", "-select_streams", "v:0", "-show_entries",
                        "stream=width,height,pix_fmt,color_space,color_range,color_transfer,color_primaries:stream_side_data=rotation",
                        "-of", "json", video], capture_output=True, text=True, check=True)
    s = json.loads(r.stdout)["streams"][0]
    rot = next((int(float(d["rotation"])) for d in s.get("side_data_list", []) if "rotation" in d), 0)
    w, h = (s["height"], s["width"]) if abs(rot) in (90, 270) else (s["width"], s["height"])
    return {"w": w, "h": h, "color": {k: s[k] for k in ("color_space", "color_range", "color_transfer", "color_primaries")
                                      if s.get(k) and s[k] not in ("unknown", "unspecified")}}


def _setparams(color):
    """A filter that gives raw frames the color properties of the video. The NV12 to RGB conversion uses them."""
    names = {"color_space": "colorspace", "color_range": "range", "color_transfer": "color_trc", "color_primaries": "color_primaries"}
    return "setparams=" + ":".join(f"{names[k]}={v}" for k, v in color.items()) if color else ""


def _even_box(boxes, w, h):
    """The smallest source window with even edges that contains the luma box and 2 x the chroma box."""
    (lx0, ly0, lx1, ly1), (cx0, cy0, cx1, cy1) = boxes
    x0, y0 = min(lx0, 2 * cx0) // 2 * 2, min(ly0, 2 * cy0) // 2 * 2
    x1, y1 = min(w, -(-max(lx1, 2 * cx1) // 2) * 2), min(h, -(-max(ly1, 2 * cy1) // 2) * 2)
    return x0, y0, x1 - x0, y1 - y0


class Scaler:
    """yuv420p frame (or an even window of it) -> NV12 window of the scaled frame."""

    def __init__(self, sw, sh, dw, dh, x0=0, y0=0, w=None, h=None):
        w, h = w or dw, h or dh
        assert x0 % 2 == 0 and y0 % 2 == 0 and w % 2 == 0 and h % 2 == 0
        self.w, self.h = w, h
        self.luma = Plane(sw, sh, dw, dh, x0, y0, w, h)
        self.chroma = Plane(sw // 2, sh // 2, dw // 2, dh // 2, x0 // 2, y0 // 2, w // 2, h // 2)
        self.sx, self.sy, self.sw, self.sh = _even_box((self.luma.box, self.chroma.box), sw, sh)
        self.in_bytes = self.sw * self.sh * 3 // 2
        self.out_bytes = w * h * 3 // 2

    def crop_filter(self):
        return f"crop={self.sw}:{self.sh}:{self.sx}:{self.sy}:exact=1"

    def __call__(self, buf):
        n = self.sw * self.sh
        a = np.frombuffer(buf, np.uint8)
        y = self.luma(a[:n].reshape(self.sh, self.sw), self.sx, self.sy)
        u = self.chroma(a[n:n + n // 4].reshape(self.sh // 2, self.sw // 2), self.sx // 2, self.sy // 2)
        v = self.chroma(a[n + n // 4:].reshape(self.sh // 2, self.sw // 2), self.sx // 2, self.sy // 2)
        return y.tobytes() + np.stack([u, v], -1).tobytes()


def _read(pipe, size):
    buf = pipe.read(size)
    return buf if len(buf) == size else None


def _pump(src, scaler, dst):
    """Thread: frames of ffmpeg 1 -> scale -> ffmpeg 2."""
    pool = ThreadPoolExecutor(WORKERS)                   # numpy releases the lock of Python: the frames scale in parallel
    jobs = collections.deque()
    try:
        while True:
            buf = _read(src.stdout, scaler.in_bytes)
            if buf is None:
                break
            jobs.append(pool.submit(scaler, buf))
            if len(jobs) >= 2 * WORKERS:
                dst.stdin.write(jobs.popleft().result())
        while jobs:
            dst.stdin.write(jobs.popleft().result())
    except (BrokenPipeError, OSError, ValueError):
        pass                                             # the reader stopped
    finally:
        pool.shutdown(wait=False, cancel_futures=True)
        try:
            dst.stdin.close()
        except OSError:
            pass
        src.kill()


def _decode(video, pre, filters, scaler, post=()):
    cmd = [platform_cfg.tool("ffmpeg"), "-v", "error"] + platform_cfg.hwaccel() + list(pre) + ["-i", video, "-vf",
           ",".join(filters + ["format=yuv420p", scaler.crop_filter()])] + list(post) + ["-fps_mode", "passthrough", "-f", "rawvideo", "-"]
    return subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=scaler.in_bytes)


def rgb_frames(video, start, dur, fps, fw, fh, x0, y0, cw, ch):
    """Yields the RGB crops (ch x cw x 3) of the frames of a window of the video. The same pictures as the GPU chain
    fps, scale_cuda=fw:fh, hwdownload, format=nv12, crop=cw:ch:x0:y0, format=rgb24."""
    info = probe(video)
    x0, y0 = x0 // 2 * 2, y0 // 2 * 2                      # the crop filter does this for NV12
    sc = Scaler(info["w"], info["h"], fw, fh, x0, y0, cw, ch)
    src = _decode(video, ["-ss", str(start), "-t", str(dur)], [f"fps={fps}"] if fps else [], sc)
    vf = ",".join(f for f in (_setparams(info["color"]), "format=rgb24") if f)
    dst = subprocess.Popen([platform_cfg.tool("ffmpeg"), "-v", "error", "-f", "rawvideo", "-pix_fmt", "nv12", "-s", f"{cw}x{ch}", "-i", "-",
                            "-vf", vf, "-fps_mode", "passthrough", "-f", "rawvideo", "-"],
                           stdin=subprocess.PIPE, stdout=subprocess.PIPE, bufsize=cw * ch * 3 * 4)
    threading.Thread(target=_pump, args=(src, sc, dst), daemon=True).start()
    size = cw * ch * 3
    try:
        while True:
            buf = _read(dst.stdout, size)
            if buf is None:
                break
            yield np.frombuffer(buf, np.uint8).reshape(ch, cw, 3)
    finally:
        dst.kill()
        src.kill()


def keyframes_1fps(video, n, pattern, width=1920):
    """Writes n JPEG files, one for each second. The same pictures as the GPU chain
    fps=1:round=down, scale_cuda=1920:-2, hwdownload, format=nv12, then JPEG with -q:v 3."""
    info = probe(video)
    height = max(2, (int(round(width * info["h"] / info["w"])) + 1) // 2 * 2)
    sc = Scaler(info["w"], info["h"], width, height)
    src = _decode(video, [], ["fps=1:round=down"], sc, ["-frames:v", str(n)])
    cmd = [platform_cfg.tool("ffmpeg"), "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "nv12", "-s", f"{width}x{height}", "-i", "-"]
    if info["color"]:
        cmd += ["-vf", _setparams(info["color"])]
    dst = subprocess.Popen(cmd + ["-fps_mode", "passthrough", "-q:v", "3", pattern], stdin=subprocess.PIPE)
    _pump(src, sc, dst)
    if dst.wait() != 0:
        raise RuntimeError("ffmpeg did not write the keyframes")

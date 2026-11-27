"""E200 (S2, gap program G15): the ball decode of a video with more than 8 bits per sample (for example 10-bit HEVC
of a phone, often HDR).

ball_wasb.frames downloads the GPU frames with "hwdownload,format=nv12". A 10-bit video gives p010 frames on the GPU,
and ffmpeg stops: "Invalid output format nv12 for hwframe download". Here scale_cuda converts to nv12 on the GPU
(scale_cuda=w:h:format=nv12). The rest of the chain is the chain of ball_wasb.frames.

This module is a copy of ball_wasb.frames with that one change. A change of ball_wasb.py changes the ball tag of each
existing ball file (about 3.3 h of GPU for each match), so the change is not in ball_wasb.py now. Put it into
ball_wasb.frames at the next change of ball_wasb.py that changes the ball tag anyway, and then delete this module.

detect.py selects this module only for a video with more than 8 bits (detect.ball_decoder); the ball tag of such a
video has "10bit" and the bytes of this file (detect.ball_tag). An 8-bit video does not import this module.
The CPU path (no NVIDIA GPU) is the path of ball_wasb.frames: cuda_scale converts with format=yuv420p.
"""
import subprocess
import numpy as np
import ball_wasb, lab, platform_cfg


def frames(mid, start, dur, scale):
    """Yields RGB crops of all frames of the window (as ball_wasb.frames)."""
    x0, y0, cw, ch, fw, fh = ball_wasb.crop_box(mid, scale, bool(ball_wasb.PASSES))
    if platform_cfg.decode() != "cuda":
        import cuda_scale
        yield from cuda_scale.rgb_frames(lab.MATCHES[mid]["video"], start, dur, ball_wasb.FPS, fw, fh, x0, y0, cw, ch)
        return
    fps = ball_wasb.FPS
    cmd = [platform_cfg.tool("ffmpeg"), "-v", "error", "-hwaccel", "cuda", "-hwaccel_output_format", "cuda",
           "-ss", str(start), "-t", str(dur), "-i", lab.MATCHES[mid]["video"],
           "-vf", (f"fps={fps}," if fps else "") + f"scale_cuda={fw}:{fh}:format=nv12,hwdownload,format=nv12,crop={cw}:{ch}:{x0}:{y0},format=rgb24",
           "-fps_mode", "passthrough", "-f", "rawvideo", "-"]
    size = cw * ch * 3
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=size * 4)
    while True:
        buf = p.stdout.read(size)
        if len(buf) < size:
            break
        yield np.frombuffer(buf, np.uint8).reshape(ch, cw, 3)

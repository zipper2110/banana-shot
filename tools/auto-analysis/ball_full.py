"""E20: 3D flights over the full marked part of a match.

Input: the full-match blob file of ball_wasb.py (wasb_<start>_<dur>_s0.35_k2.npy,
windows in WINDOWS). The script cuts the file into chunks of CHUNK s. Each chunk
is fitted with a margin of PAD s on each side (ball_3d.fit_window). A flight is
kept in the chunk where it starts.
Output: <match>/fullflights_s0.35.json: {"start", "dur", "flights", "kinks", "events"}.
"events" (E17d, ball_bounce.py): the bounces, hits and extrapolated landings, sorted by time. A flight
refers to its events by index: ev0, ev1, bounceEv.
Times are in seconds of the video (true frame times: clock()). The fit of a chunk counts in frames of
1/60 s. to_true() puts each time of the result back on the true time of its frame (E17d).
Usage: python ball_full.py [--out <file name>] [--old] [match id ...]
  --out   another file name (the lab matches keep their validated file until a new one is validated)
  --old   no events (ball_3d.EVENTS = False)
"""
import json, os, sys, time
import numpy as np
import lab
import ball_3d
import frame_times

WINDOWS = {"m1015": (832, 2931), "m1020": (54, 4923), "m1021": (873, 5999), "m1030": (700, 4675)}
CHUNK, PAD = 30.0, 2.0


def slot_frames(tv, n_slots, fps=30.0):
    """Emulation of the ffmpeg "fps" filter (round = near): the index of the video frame in each output
    slot. Slot n shows the last frame j with round(tv[j] * fps) <= n."""
    r = np.floor(tv * fps + 0.5).astype(np.int64)
    j = np.searchsorted(r, np.arange(n_slots), side="right") - 1
    return np.clip(j, 0, len(tv) - 1)


def clock(mid, start):
    """True video time of each frame index of the blob file (frames of 1/60 s from "start").
    A lab match: the frame times of the video. A video that detect.py decoded at a constant 30 fps
    (pts_video.npy is there): frame 2 n is output frame n of the ffmpeg fps filter, and that frame is a
    copy of one video frame. Its true time is the time of that video frame, not n / 30 (difference up to
    1 / 60 s, E17d)."""
    pv = os.path.join(lab.mdir(mid), "pts_video.npy")
    if not os.path.exists(pv):
        return frame_times.times(mid, start)
    tv = np.load(pv)
    n30 = len(np.load(os.path.join(lab.mdir(mid), "pts.npy"))) // 2 + 1
    t30 = tv[slot_frames(tv, n30)]
    tt = np.repeat(t30, 2)
    tt[1::2] += 1 / 60.0                                 # the frames between: no detections there
    return tt[int(round(start * 60)):]


def to_true(t, w0, k0, tt):
    """Time of the fit (w0 + frames / 60) -> true time of that frame. Linear past the ends."""
    f = (t - w0) * 60.0 + k0
    n = len(tt) - 1
    if f <= 0:
        return float(tt[0] + f / 60.0)
    if f >= n:
        return float(tt[n] + (f - n) / 60.0)
    i = int(f)
    return float(tt[i] + (f - i) * (tt[i + 1] - tt[i]))


def run(mid, name="fullflights_s0.35.json"):
    start, dur = WINDOWS[mid]
    d = np.load(os.path.join(lab.mdir(mid), f"wasb_{start}_{dur}_s0.35{os.environ.get('BLOB_TAG', '')}_k2.npy"))
    d = d[np.argsort(d[:, 0], kind="stable")]
    tt = clock(mid, start)                               # true time of each frame (the video is not exactly 60 fps)
    ft = tt[np.minimum(d[:, 0].astype(int), len(tt) - 1)]
    fls, kks, evs, t0 = [], [], [], time.time()

    def add_event(e):
        """Index of the event in evs. The same event comes from two chunks: use it once."""
        for i in range(len(evs) - 1, max(-1, len(evs) - 80), -1):
            if evs[i]["kind"] == e["kind"] and abs(evs[i]["t"] - e["t"]) < 0.02:
                return i
        evs.append(e)
        return len(evs) - 1
    c = 0.0
    while c < dur:
        a, b = max(0.0, c - PAD), min(float(dur), c + CHUNK + PAD)
        i0, i1 = np.searchsorted(ft, start + a), np.searchsorted(ft, start + b)
        rows = d[i0:i1].copy()
        k0 = int(np.searchsorted(tt, start + a))
        rows[:, 0] -= k0
        w0 = float(tt[min(k0, len(tt) - 1)])             # time of chunk frame 0
        if len(rows) >= 8:
            try:
                r = ball_3d.fit_window(mid, rows, w0, start + b, start + c)
            except Exception as e:                       # a chunk with no usable path
                print(f"{mid} chunk {c:.0f}: {type(e).__name__} {e}", flush=True)
                r = {"flights": [], "kinks": []}
            fix = lambda t: t if t is None or t != t else to_true(t, w0, k0, tt)
            for f in r["flights"]:
                f.update(t0=fix(f["t0"]), t1=fix(f["t1"]), landT=fix(f["landT"]))
                if "evFit" in f:
                    f["evFit"]["landT"] = fix(f["evFit"]["landT"])
            for k in r["kinks"]:
                k[0] = fix(k[0])
            for e in r.get("events", []):
                e["t"] = fix(e["t"])
                if "tLast" in e:
                    e["tLast"] = fix(e["tLast"])
            lo, hi = start + c, start + c + CHUNK
            keep = [f for f in r["flights"] if lo <= f["t0"] < hi]
            ev = r.get("events", [])
            used = {f[key] for f in keep for key in ("ev0", "ev1") if f.get(key) is not None}
            gmap = {i: add_event(e) for i, e in enumerate(ev) if i in used or lo <= e["t"] < hi}
            for f in keep:
                for key in ("ev0", "ev1", "bounceEv"):
                    if f.get(key) is not None:
                        f[key] = gmap[f[key]]
            fls += keep
            kks += [k for k in r["kinks"] if lo <= k[0] < hi]
        c += CHUNK
    order = sorted(range(len(evs)), key=lambda i: evs[i]["t"])
    new = {old: i for i, old in enumerate(order)}
    for f in fls:
        for key in ("ev0", "ev1", "bounceEv"):
            if f.get(key) is not None:
                f[key] = new[f[key]]
    # E26f: the ground positions with the refined court model, if the video uses it (ball_events.court_windows).
    # The 3D values of the flights (p0, v0, p1, netZ) stay in the frame of the first model.
    import ball_events
    ws = ball_events.court_windows(lab.mdir(mid)) if os.path.exists(os.path.join(lab.mdir(mid), "court_track.json")) else []
    fixed = any("ground" in w for w in ws)
    if fixed:
        gf = lambda t, xy: None if xy is None else ball_events.ground_fix(ws, t, list(xy))
        for f in fls:
            for o in (f, f.get("evFit")):
                if o:
                    for key in ("land", "bounce"):
                        if o.get(key) is not None:
                            o[key] = gf(f["t1"], o[key])
        for k in kks:
            k[3], k[4] = gf(k[0], [k[3], k[4]])
        for e in evs:
            e["court"] = gf(e["t"], e.get("court"))
    json.dump({"start": start, "dur": dur, "groundFix": fixed, "flights": fls, "kinks": kks, "events": [evs[i] for i in order]},
              open(os.path.join(lab.mdir(mid), name), "w"), default=float)
    print(f"{mid}: flights {len(fls)}, kinks {len(kks)}, events {len(evs)} (bounces {sum(e['kind'] == 'bounce' for e in evs)}), {time.time() - t0:.0f} s -> {name}", flush=True)


if __name__ == "__main__":
    args, name = sys.argv[1:], "fullflights_s0.35.json"
    if "--old" in args:
        args.remove("--old"); ball_3d.EVENTS = False
    if "--out" in args:
        i = args.index("--out"); name = args[i + 1]; del args[i:i + 2]
    for m in (args or list(WINDOWS)):
        run(m, name)

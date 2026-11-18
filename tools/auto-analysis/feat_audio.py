"""Audio features, 1 row for each second. Writes <match>/feat_audio.npy and
<match>/hits.npy (one row for each hit candidate).

Method: band-pass envelopes at 5 ms steps, background = 1 s median, hit
candidate = local peak with a sharp rise. No model.

hits.npy columns: time_s, onset_db (over background), level_db, rise_db,
ild_db (left minus right), hf_ratio_db (4-10 kHz minus 0.5-1.5 kHz).
Usage: python feat_audio.py [match id ...]
"""
import os, sys
import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt
from scipy.ndimage import median_filter, maximum_filter
import lab

HOP_S = 0.005
THRESHOLDS = (6, 9, 12, 16, 20)


def envelope(x, sr, lo, hi):
    sos = butter(4, [lo, hi], btype="band", fs=sr, output="sos")
    y = sosfilt(sos, x)
    hop = int(sr * HOP_S)
    n = len(y) // hop
    e = np.sqrt((y[: n * hop].reshape(n, hop) ** 2).mean(1)) + 1e-7
    return 20 * np.log10(e)


def run(mid):
    out = lab.mdir(mid)
    x, sr = sf.read(os.path.join(out, "audio.flac"), dtype="float32")
    mono = x.mean(1)
    db = envelope(mono, sr, 1500, 7000)
    lo_db = envelope(mono, sr, 500, 1500)
    hi_db = envelope(mono, sr, 4000, 10000)
    l_db = envelope(x[:, 0], sr, 1500, 7000)
    r_db = envelope(x[:, 1], sr, 1500, 7000)
    bg = median_filter(db, size=200)
    onset = db - bg
    lm = maximum_filter(onset, size=21)
    rise = db - np.concatenate([np.full(3, db[0]), db[:-3]])
    idx = np.where((onset >= THRESHOLDS[0]) & (onset == lm) & (rise > 5))[0]
    hits = np.stack([idx * HOP_S, onset[idx], db[idx], rise[idx], l_db[idx] - r_db[idx], hi_db[idx] - lo_db[idx]], 1)
    np.save(os.path.join(out, "hits.npy"), hits.astype(np.float32))

    n = lab.n_seconds(mid)
    per = int(1 / HOP_S)
    f = np.zeros((n, len(THRESHOLDS) + 5), dtype=np.float32)
    sec = (hits[:, 0]).astype(int)
    for s, h in zip(sec, hits):
        if s >= n:
            continue
        for k, t in enumerate(THRESHOLDS):
            f[s, k] += h[1] >= t
        f[s, len(THRESHOLDS)] = max(f[s, len(THRESHOLDS)], h[1])          # max onset
    for s in range(min(n, len(db) // per)):
        seg = slice(s * per, (s + 1) * per)
        f[s, len(THRESHOLDS) + 1] = bg[seg].mean()                          # background level
        f[s, len(THRESHOLDS) + 2] = db[seg].max()                           # loudest instant
        f[s, len(THRESHOLDS) + 3] = lo_db[seg].mean()                       # low band (voices, steps)
        f[s, len(THRESHOLDS) + 4] = (l_db[seg] - r_db[seg]).mean()          # stereo balance
    np.save(os.path.join(out, "feat_audio.npy"), f)
    print(mid, "hits", len(hits), "feat", f.shape, flush=True)


if __name__ == "__main__":
    for mid in (sys.argv[1:] or lab.LABELED):
        if os.path.exists(os.path.join(lab.mdir(mid), "audio.flac")):
            run(mid)

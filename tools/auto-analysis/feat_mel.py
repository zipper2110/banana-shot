"""Log-mel audio features, one row for each second. Writes
<match>/feat_mel.npy with 192 columns: for each of 64 mel bands (100 Hz to
11 kHz) the mean level, the max level, and the mean onset strength
(positive level change between 10 ms frames) in the second.
Usage: python feat_mel.py [match id ...]
"""
import os, sys
import numpy as np
import soundfile as sf
import torch
import lab

SR, NFFT, HOP, NMEL = 24000, 1024, 240, 64


def mel_fb():
    def hz2mel(f): return 2595 * np.log10(1 + f / 700)
    def mel2hz(m): return 700 * (10 ** (m / 2595) - 1)
    pts = mel2hz(np.linspace(hz2mel(100), hz2mel(11000), NMEL + 2))
    bins = np.fft.rfftfreq(NFFT, 1 / SR)
    fb = np.zeros((NMEL, len(bins)), np.float32)
    for i in range(NMEL):
        l, c, r = pts[i], pts[i + 1], pts[i + 2]
        fb[i] = np.clip(np.minimum((bins - l) / (c - l), (r - bins) / (r - c)), 0, None)
    return torch.tensor(fb)


def run(mid):
    x, sr = sf.read(os.path.join(lab.mdir(mid), "audio.flac"), dtype="float32")
    assert sr == SR
    x = torch.tensor(x.mean(1))
    fb = mel_fb()
    n = lab.n_seconds(mid)
    per = SR // HOP                      # 100 frames each second
    out = np.zeros((n, 3 * NMEL), np.float32)
    chunk = 600                          # seconds for each STFT call
    for s0 in range(0, n, chunk):
        seg = x[s0 * SR: (s0 + chunk) * SR + NFFT]
        if len(seg) < NFFT:
            break
        S = torch.stft(seg, NFFT, HOP, window=torch.hann_window(NFFT), return_complex=True, center=False).abs() ** 2
        M = torch.log(fb @ S + 1e-8).T.numpy()            # [frames, mel]
        flux = np.maximum(np.diff(M, axis=0, prepend=M[:1]), 0)
        k = min(chunk, len(M) // per, n - s0)
        Mr, Fr = M[: k * per].reshape(k, per, NMEL), flux[: k * per].reshape(k, per, NMEL)
        out[s0: s0 + k] = np.concatenate([Mr.mean(1), Mr.max(1), Fr.mean(1)], 1)
    np.save(os.path.join(lab.mdir(mid), "feat_mel.npy"), out)
    print(mid, out.shape, flush=True)


if __name__ == "__main__":
    for mid in (sys.argv[1:] or lab.LABELED):
        run(mid)

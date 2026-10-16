import sys, numpy as np
from scipy.io import wavfile
from scipy.signal import butter, sosfiltfilt
sr, x = wavfile.read(sys.argv[1]); x = x.astype(np.float32)/32768
# ball hits: sharp broadband transients, strong in 1-6 kHz
sos = butter(4, [1500, 7000], btype='band', fs=sr, output='sos')
y = sosfiltfilt(sos, x)
hop = 80  # 5 ms
n = len(y)//hop
e = np.sqrt((y[:n*hop].reshape(n,hop)**2).mean(1)) + 1e-7
db = 20*np.log10(e)
# local background: median over 1 s
from scipy.ndimage import median_filter, maximum_filter
bg = median_filter(db, size=200)
onset = db - bg
np.save(sys.argv[2], np.stack([db, bg]))
# peak pick: onset > T, local max in 100ms window, sharp rise vs 15 ms earlier
T = float(sys.argv[3]) if len(sys.argv)>3 else 12
lm = maximum_filter(onset, size=21)
rise = db - np.concatenate([np.full(3, db[0]), db[:-3]])
idx = np.where((onset >= T) & (onset == lm) & (rise > 6))[0]
with open(sys.argv[4] if len(sys.argv)>4 else 'hits.csv','w') as f:
    for i in idx: f.write(f"{i*hop/sr:.3f},{onset[i]:.1f},{db[i]:.1f},{rise[i]:.1f}\n")
print(len(idx))

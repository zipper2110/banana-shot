# usage: nearhits.py env.npy nearhits.txt
# Loud near-court hits from the envelope that hits.py saves (5 ms steps).
# A hit = local peak, 10 dB over the 1 s median and a rise of 10 dB in 20 ms.
import sys, numpy as np
from scipy.ndimage import maximum_filter
db, bg = np.load(sys.argv[1])
lm = maximum_filter(db, size=41)
rise = db - np.concatenate([np.full(4, db[0]), db[:-4]])
idx = np.where((db == lm) & (db - bg > 10) & (rise > 10))[0]
np.savetxt(sys.argv[2], idx * 0.005, fmt='%.3f'); print(len(idx))

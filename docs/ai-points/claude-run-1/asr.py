# usage: asr.py wav start_s end_s model device out.tsv [lang]
import sys, time
import numpy as np
from scipy.io import wavfile
from faster_whisper import WhisperModel
wav, a, b, model, dev, out = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4], sys.argv[5], sys.argv[6]
lang = sys.argv[7] if len(sys.argv) > 7 else None
sr, x = wavfile.read(wav)
x = x[int(a*sr):int(b*sr)].astype(np.float32)/32768
m = WhisperModel(model, device=dev, compute_type="int8" if dev=="cpu" else "float16", cpu_threads=8)
t0=time.time()
segs, info = m.transcribe(x, language=lang, vad_filter=False, vad_parameters=dict(min_silence_duration_ms=300),
    beam_size=5, condition_on_previous_text=False, word_timestamps=False)
with open(out, "w", encoding="utf-8") as f:
    for s in segs:
        line = f"{a+s.start:.1f}\t{a+s.end:.1f}\t{s.avg_logprob:.2f}\t{s.no_speech_prob:.2f}\t{s.text.strip()}"
        f.write(line+"\n"); f.flush()
print("lang", info.language, info.language_probability, "time", time.time()-t0)

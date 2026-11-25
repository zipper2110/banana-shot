"""The lab benchmark of the detection chain (chain-inventory.md, G1). For each marked singles lab match it runs
chain.py, the chain that the app runs (detect.py), with models that did not see the match:
  rally model   models/rally_ball_x<match>.pt   (python rally_model.py train ball)
  case tables   the shot chain and old rule tables fitted on the other 3 matches (use_lomo_cases)
  end model     the rally end detector fitted on the other 3 matches (rally_end.py fit, use_lomo)
  ball weights  the flights files of the lab were made with the held-out weights e38_f1 to e38_f4 (E38)
Then score.py compares the rows with the marks.

Differences from the app that stay (inputs, not the chain):
  - the time range is the part of the video that the lab ball files cover (ball_full.WINDOWS), not the full video;
  - the lab files: fullflights_s0.35_ev.json, and the ball features and net events of the older ball candidates.
Output: <match>/pipeline.json (metrics and the result rows with "gt", "gtWinnerNear").
Usage: python pipeline.py [match id ...]
"""
import json, os, sys
import lab
import chain
import match_decode as md
import shot_chain as sc
import rally_model
import rally_end
import score
from ball_full import WINDOWS

CFG = {"threshold": 0.5, "shortPoints": True}        # the defaults of detect.py


def run(mid, quiet=False):
    start, dur = WINDOWS[mid]
    # E17d: another flights file for a comparison. E28: the chain needs the file with events.
    full = json.load(open(os.path.join(lab.mdir(mid), os.environ.get("FULLFLIGHTS") or ("fullflights_s0.35_ev.json" if chain.CHAIN else "fullflights_s0.35.json"))))
    prob = rally_model.predict(mid, "ball", fold=mid)
    seg, serves = chain.segments(prob, full, CFG, start, start + dur)
    rows = chain.winners(mid, seg, full, serves, prob=prob)
    m = score.score(mid, rows)
    json.dump({"metrics": m, "rallies": rows}, open(os.path.join(lab.mdir(mid), "pipeline.json"), "w"), indent=1, default=float)
    if not quiet:
        print(score.line(m), flush=True)
    return m


if __name__ == "__main__":
    md.use_lomo_cases()                       # winner probabilities measured on the other 3 matches
    rally_end.use_lomo()
    if chain.CHAIN:
        sc.use_lomo_cases()
    ms = [run(m) for m in (sys.argv[1:] or list(WINDOWS))]
    if len(ms) > 1:
        print(score.total(ms))

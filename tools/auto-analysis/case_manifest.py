"""Input of the event label page label_events.html (gap program S4 item 10): manifest.js and the page in one folder.

A page on file:// cannot read a JSON file. Thus this script writes the fragment manifest as manifest.js
(window.CASES = ...) next to a copy of label_events.html. Each fragment row gets "rowHash" (sha256 of the row), so a
label file records the exact manifest row that it was made on.

The fragment manifest (S1 item 8, the case library) is one JSON file: {"version", "fragments": [row, ...]}. A row:
  id        unique; also the name of the label file <id>.events.json
  match     the match id; testOnly: true for a test-only video
  clip      the clip path, relative to the folder of the page
  t0        source time (s) of clip time 0: source time = t0 + clip time
  fps, size the frame rate and [width, height] of the clip
  courtH    3x3, image px of a 1920-wide frame -> court m (x across, y along, far end > 0). If it is missing and the
            row has "match", the script takes the court of the full video at the span start (ball_events.court_H).
  points    the marked points in the span: [{id, startMs, endMs, ending}], source ms
  span      [a, b] source s: the part to label
  labelWindow [a, b] source s: the part that the user labels and that the measures use (A32, case_lib.label_window);
            a row with no field: the whole clip. Not in rowHash (a measure rule, not a property of the cut).
  level, kinds, control
  clipDur   added here: the duration (s) of the clip file (ffprobe). The page compares it with the loaded clip: a
            clip that changed after the page was opened blocks the marks. Not in rowHash.

Usage:
  python case_manifest.py <manifest.json> <page folder>     the manifest of S1; the clip paths become relative to the page folder
  python case_manifest.py --test <page folder>             a test manifest: 2 rally clips of the hit label work
                                                           (one lab match, one Igor clip), for a function test
"""
import hashlib, json, os, shutil, subprocess, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
HIT_LABEL_DIR = r"D:\bananashot-lab\ws\s4\label"


def court(mid, t):
    import ball_events
    return np.asarray(ball_events.court_H(mid, t), np.float64).tolist()


def clip_dur(path):
    try:
        o = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                           capture_output=True, text=True, check=True).stdout.strip()
        return round(float(o), 3)
    except Exception:
        return None


def finish(man, out):
    for r in man["fragments"]:
        if "courtH" not in r and r.get("match"):
            r["courtH"] = court(r["match"], r["span"][0] if r.get("span") else r["t0"])
        row = {k: v for k, v in r.items() if k not in ("rowHash", "clipDur", "labelWindow")}
        cd = clip_dur(os.path.join(out, r["clip"]))
        if cd:
            r["clipDur"] = cd
        r["rowHash"] = hashlib.sha256(json.dumps(row, sort_keys=True).encode()).hexdigest()[:16]
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "manifest.js"), "w", encoding="utf-8") as f:
        f.write("window.CASES = " + json.dumps(man) + ";\n")
    shutil.copy2(os.path.join(HERE, "label_events.html"), os.path.join(out, "label_events.html"))
    print(f"{len(man['fragments'])} fragments -> {out}")


def test_manifest(out):
    """Two clips of the hit label work: the first lab clip and the first Igor clip that exist."""
    wl = json.loads(open(os.path.join(HIT_LABEL_DIR, "worklist.js"), encoding="utf-8").read().split("=", 1)[1].rstrip().rstrip(";"))
    rows = []
    for want in ("m1015", "m1116"):
        it = next(x for x in wl["items"] if x["match"] == want and x.get("t0") is not None
                  and os.path.exists(os.path.join(HIT_LABEL_DIR, x["clip"])))
        clip = os.path.relpath(os.path.join(HIT_LABEL_DIR, it["clip"]), out).replace("\\", "/")
        a = it["startMs"] / 1000 - (12.0 if it.get("faultCheck") else 1.5)
        rows.append({"id": f"test-{it['match']}-{it['n']:03d}", "match": it["match"], "testOnly": it["match"] == "m1116",
                     "clip": clip, "t0": it["t0"], "fps": 60, "size": [1920, 1080],
                     "points": [{"id": it["point"], "startMs": it["startMs"], "endMs": it["endMs"], "ending": it.get("ending")}],
                     "span": [max(it["t0"], a), it["endMs"] / 1000 + 0.5], "level": "small", "kinds": ["function-test"],
                     "control": False})
    return {"version": 1, "created": "test", "fragments": rows}


if __name__ == "__main__":
    a = sys.argv[1:]
    if a and a[0] == "--test":
        finish(test_manifest(os.path.abspath(a[1])), os.path.abspath(a[1]))
    elif len(a) == 2:
        man, out = json.load(open(a[0], encoding="utf-8")), os.path.abspath(a[1])
        base = os.path.dirname(os.path.abspath(a[0]))
        for r in man["fragments"]:                  # clip paths: relative to the manifest folder -> to the page folder
            if not os.path.isabs(r["clip"]):
                r["clip"] = os.path.relpath(os.path.join(base, r["clip"]), out).replace("\\", "/")
        finish(man, out)
    else:
        print(__doc__)

"""Input of the Faults step of the label page (gap program S4, E158): the hidden fault rows of the bench.

The bench hides a detected row as "first-serve fault, not checked" (bench.no_none): a lab row that matches no marked
point, overlaps none, and ends chain.FAULT_NEXT s or less before the next marked point (not NONE). The lab marks start
at the second serve, so the truth of these rows is not known. The user answers for each row:
  F = a first-serve fault (the row is a fault clip), P = a real point (a rally) with no mark,
  N = not a fault and not a point (a false rally), Q = not sure.

Steps:
  python faults_cases.py rows <bench run folder> <out folder>
      reads rows.json of the bench run (frozen flights) and writes <out>/fault_rows.json (the rows, for the agents)
      and <out>/fault_cut_spec.json (the cut spec for S1: one clip for each row, from the row start - PRE_S s, not
      into the previous marked point, to the start of the next marked point + POST_S s).
  python faults_cases.py page <out folder> <clips.json> <page folder>
      clips.json (from S1): {row id: fragment folder} (fragment.json with offset and clip.mp4). Writes faults.js
      (window.FAULTS) into the page folder. Open label_events.html#faults in that folder.

Row hash: sha256 of the row (match, start, end, next point, clip, t0). The page keeps an answer only for the same row
hash; an answer of a changed row is "stale" (kept in the file apart, not used).
"""
import hashlib, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
PRE_S, POST_S = 1.0, 1.0
MATCHES = ("m1015", "m1020", "m1021", "m1030")


def fault_rows(mid, rows):
    """The rows of bench.no_none "fault, not checked" (the same rule), with the next and the previous marked point."""
    import lab, chain
    gt = lab.gt_points(mid)
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))
    if "faults" in g:
        return []
    pairs = lab.match_points([(r["start"], r["end"]) for r in rows], gt)
    used = {j for _, j in pairs}
    marked = [(p, x) for p, x in zip(g["points"], gt) if x[2] != "NONE"]
    out = []
    for j, r in enumerate(rows):
        if j in used or any(r["end"] > x[0] and r["start"] < x[1] for x in gt):
            continue
        nxt = next((p for p, x in marked if x[0] >= r["end"]), None)
        if nxt is None or nxt["startMs"] / 1000 - r["end"] > chain.FAULT_NEXT:
            continue
        prev = [x[1] for x in gt if x[1] <= r["start"]]
        out.append({"id": f"{mid}_{r['start']:.1f}", "match": mid, "start": round(r["start"], 3), "end": round(r["end"], 3),
                    "shots": r.get("shots"), "next": {"id": nxt["id"], "startMs": nxt["startMs"], "endMs": nxt["endMs"]},
                    "prevEnd": round(max(prev), 3) if prev else None})
    return out


def rows_step(run_dir, out):
    R = json.load(open(os.path.join(run_dir, "rows.json"), encoding="utf-8"))["Lab LOMO"]
    allr, spec = [], []
    for mid in MATCHES:
        rows = [r for r in R.get(mid, []) if r.get("kind") == "rally"]
        fr = fault_rows(mid, rows)
        allr += fr
        for x in fr:
            a = x["start"] - PRE_S if x["prevEnd"] is None else max(x["prevEnd"], x["start"] - PRE_S)
            spec.append({"id": "f" + x["id"].replace("m", "").replace(".", "_"), "row": x["id"], "mid": mid,
                         "start": round(a, 3), "end": round(x["next"]["startMs"] / 1000 + POST_S, 3),
                         "pad": [0.0, 0.0], "level": "small", "kinds": ["fault-row"], "control": False})
        print(mid, "hidden fault rows", len(fr))
    os.makedirs(out, exist_ok=True)
    json.dump({"version": 1, "bench": os.path.abspath(run_dir), "rule": "bench.no_none fault, not checked; chain.FAULT_NEXT",
               "rows": allr}, open(os.path.join(out, "fault_rows.json"), "w", encoding="utf-8"), indent=1)
    json.dump(spec, open(os.path.join(out, "fault_cut_spec.json"), "w", encoding="utf-8"), indent=1)
    print("rows", len(allr), "->", os.path.join(out, "fault_rows.json"), "and fault_cut_spec.json")


def page_step(out, clips_json, page):
    import lab
    rows = json.load(open(os.path.join(out, "fault_rows.json"), encoding="utf-8"))["rows"]
    clips = json.load(open(clips_json, encoding="utf-8"))
    by = {}
    for x in rows:
        fd = clips.get(x["id"])
        if not fd:
            print("no clip for", x["id"]); continue
        fm = json.load(open(os.path.join(fd, "fragment.json"), encoding="utf-8"))
        clip = os.path.relpath(os.path.join(fd, fm.get("clip", "clip.mp4")), page).replace(os.sep, "/")
        # source time = t0 + clip time (as manifest.js of the event page: the clip video starts videoStartInClip s
        # after clip time 0; fragment time = source time - offset).
        t0 = round(fm["offset"] - fm.get("videoStartInClip", 0.0), 6)
        r = dict(x, clip=clip, t0=t0, duration=fm.get("duration"))
        r["rowHash"] = hashlib.sha256(json.dumps({k: r[k] for k in ("match", "start", "end", "next", "clip", "t0")},
                                                 sort_keys=True).encode()).hexdigest()[:16]
        by.setdefault(x["match"], []).append(r)
    doc = {"version": 1, "matches": [{"match": m, "testOnly": m in lab.TEST_ONLY, "rows": sorted(v, key=lambda r: r["start"])}
                                     for m, v in sorted(by.items())]}
    with open(os.path.join(page, "faults.js"), "w", encoding="utf-8") as f:
        f.write("window.FAULTS = " + json.dumps(doc) + ";\n")
    print("faults.js:", sum(len(v) for v in by.values()), "rows in", len(by), "matches")


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "rows":
        rows_step(sys.argv[2], sys.argv[3])
    elif len(sys.argv) >= 5 and sys.argv[1] == "page":
        page_step(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        print(__doc__)

"""Corrections of the user after an import of automatic points in the app
(docs/auto-analysis/app-integration.md).

Input: a project folder of the app. It has:
  autodetect/result-<time>.json   the detection result (detect.py)
  autodetect/import-<time>.json   which clip id each detected rally got, and the import settings
  edl.json, score.json            the project now, after the corrections of the user
The script compares the import with the project and prints:
  - detected rallies that the user deleted (false detections);
  - clips with a moved start or end (the change in seconds);
  - winners that the user changed, split by the score reason (a change with a reason
    "players' call", "replayed", "score lost" is not a detection error);
  - clips that the user added (missed points);
  - the same for the fault clips;
  - the run time of each detection stage.
It writes <project>/autodetect/corrections-<import time>.json for later training.
Usage: python corrections.py <project folder> [import file name]
"""
import glob, json, os, sys


def load(p):
    return json.load(open(p, encoding="utf-8"))


def main(project, import_name=None):
    ad = os.path.join(project, "autodetect")
    imports = sorted(glob.glob(os.path.join(ad, "import-*.json")))
    if not imports:
        print("no import record in", ad)
        return None
    imp_file = os.path.join(ad, import_name) if import_name else imports[-1]
    imp = load(imp_file)
    res = load(os.path.join(ad, imp["resultFile"]))
    pts = {p["id"]: p for p in load(os.path.join(project, "edl.json"))["points"]}
    score = load(os.path.join(project, "score.json"))
    outcomes, reasons, details = score.get("outcomes", {}), score.get("reasons", {}), score.get("details", {})
    st = imp["settings"]
    rows, auto_ids = [], set()
    for r in imp["rows"]:
        ra = res["rallies"][r["rally"]]
        row = {"rally": r["rally"], "kind": ra.get("kind"), "start": ra["start"], "end": ra["end"],
               "prob": ra.get("prob"), "winner": ra.get("winner"), "confidence": ra.get("confidence")}
        if r.get("skipped"):
            row["state"] = "skipped:" + r["skipped"]
            rows.append(row)
            continue
        pid = r["pointId"]
        auto_ids.add(pid)
        p = pts.get(pid)
        if p is None:
            row["state"] = "deleted"
        else:
            # the import put the padding of the settings on the rally times
            row["state"] = "kept"
            row["dStart"] = round(p["startMs"] / 1000 - (ra["start"] - st["padBeforeMs"] / 1000), 2)
            row["dEnd"] = round(p["endMs"] / 1000 - (ra["end"] + st["padAfterMs"] / 1000), 2)
            now = outcomes.get(pid, "NONE")
            row["winnerNow"] = None if now == "NONE" else now
            row["reason"] = reasons.get(pid)
            row["endingNow"] = (details.get(pid) or {}).get("ending")
        fid = r.get("faultPointId")
        if fid:
            auto_ids.add(fid)
            f = pts.get(fid)
            row["fault"] = "deleted" if f is None else ("kept" if (details.get(fid) or {}).get("ending") == "SERVE_FAULT" else "changed")
        rows.append(row)
    added = [{"start": p["startMs"] / 1000, "end": p["endMs"] / 1000, "winner": outcomes.get(i), "ending": (details.get(i) or {}).get("ending")}
             for i, p in pts.items() if i not in auto_ids and not p.get("auto")]
    kept = [r for r in rows if r["state"] == "kept"]
    deleted = [r for r in rows if r["state"] == "deleted"]
    moved_s = [r for r in kept if abs(r["dStart"]) > 0.3]
    moved_e = [r for r in kept if abs(r["dEnd"]) > 0.3]
    with_w = [r for r in kept if r["winner"] and st.get("addWinners", True)]
    w_same = [r for r in with_w if r["winnerNow"] == r["winner"]]
    w_reason = [r for r in with_w if r["winnerNow"] != r["winner"] and r["reason"]]
    w_wrong = [r for r in with_w if r["winnerNow"] != r["winner"] and not r["reason"]]
    faults = [r for r in rows if "fault" in r]

    def med(v):
        v = sorted(v)
        return v[len(v) // 2] if v else 0.0

    n_det = len(kept) + len(deleted)
    print(f"import {os.path.basename(imp_file)} of {imp['resultFile']}")
    print(f"detected rallies in the project: {n_det}; kept {len(kept)}, deleted {len(deleted)} "
          f"(precision {100 * len(kept) / max(1, n_det):.1f}%); added by the user {len(added)} "
          f"(recall {100 * len(kept) / max(1, len(kept) + len(added)):.1f}%)")
    for k in ("rally", "short"):
        a, b = sum(r["kind"] == k for r in kept), sum(r["kind"] == k for r in deleted)
        print(f"  {k}: kept {a}, deleted {b}")
    print(f"start moved (> 0.3 s): {len(moved_s)} of {len(kept)}, median change {med([r['dStart'] for r in moved_s]):+.2f} s; "
          f"end moved: {len(moved_e)}, median change {med([r['dEnd'] for r in moved_e]):+.2f} s; "
          f"median of all: start {med([r['dStart'] for r in kept]):+.2f} s, end {med([r['dEnd'] for r in kept]):+.2f} s")
    print(f"winners set by the detection: {len(with_w)}; not changed {len(w_same)} "
          f"({100 * len(w_same) / max(1, len(with_w) - len(w_reason)):.1f}% of those with no reason); "
          f"changed by the user {len(w_wrong)}; changed with a reason {len(w_reason)}")
    for lo, hi in ((0.5, 0.6), (0.6, 0.7), (0.7, 0.8), (0.8, 0.9), (0.9, 1.01)):
        g = [r for r in with_w if lo <= (r["confidence"] or 0) < hi and not (r["winnerNow"] != r["winner"] and r["reason"])]
        if g:
            print(f"  confidence {lo:.1f}-{min(hi, 1.0):.1f}: {sum(r['winnerNow'] == r['winner'] for r in g)} of {len(g)} right")
    if faults:
        print(f"fault clips: {len(faults)}; kept {sum(r['fault'] == 'kept' for r in faults)}, deleted {sum(r['fault'] == 'deleted' for r in faults)}, "
              f"changed {sum(r['fault'] == 'changed' for r in faults)}; faults added by the user "
              f"{sum(a['ending'] == 'SERVE_FAULT' for a in added)}")
    print("stage seconds:", res.get("stageSeconds"), "| video seconds:", res.get("videoSeconds"))
    out = {"import": os.path.basename(imp_file), "result": imp["resultFile"], "settings": st, "rows": rows, "added": added,
           "stageSeconds": res.get("stageSeconds"), "videoSeconds": res.get("videoSeconds"), "video": res.get("video")}
    stamp = os.path.basename(imp_file)[len("import-"):-len(".json")]
    json.dump(out, open(os.path.join(ad, f"corrections-{stamp}.json"), "w", encoding="utf-8"), indent=1)
    return out


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)

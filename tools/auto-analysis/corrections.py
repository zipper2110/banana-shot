"""Corrections of the user after an import of automatic points in the app
(docs/auto-analysis/app-integration.md).

Input: a project folder of the app. It has:
  autodetect/result-<time>.json   the detection result (detect.py)
  autodetect/import-<time>.json   which clip id each detected rally got, and the import settings
  autodetect/review-notes.json    the notes of the user about the detection of a clip (optional)
  edl.json, score.json            the project now, after the corrections of the user
The script compares the import with the project and prints:
  - detected rallies that the user deleted (false detections);
  - clips with a moved start or end (the change in seconds against the clip that the import made);
  - the winners that the import set, split into: not changed, changed by "Swap players" (the player at the near
    end was wrong, the near/far winner was right), changed with a score reason ("players' call", "replayed",
    "score lost": not a detection error), and changed by the user (a wrong winner);
  - the winners under the limit minWinnerPercent: the import did not set them, so they are not changes;
  - clips that the user added (missed points): only clips after the import and inside its trim;
  - the same for the fault clips;
  - the run time of each detection stage.
G11: the counts of right and wrong use only the clips that the user reviewed (auto.checked); a clip that the user
did not review is not evidence. The import record gives "swapped" (player 1 of the detection is player 2 of the
project) and the settings; the bounds of each clip come from the same rule as the import (DetectImport.plan: the
padding stops at the middle of the gap to the next rally, then a rounding to 10 ms).
The report measures the rallies first and the outcome of each rally second (the priorities of the user,
2026-11-26). It has no score measure: a score reason only takes a winner change out of the counts.
It writes <project>/autodetect/corrections-<import time>.json for later training.
Usage: python corrections.py <project folder> [import file name]
"""
import glob, json, math, os, sys

FLIP = {"P1": "P2", "P2": "P1"}


def load(p):
    return json.load(open(p, encoding="utf-8"))


def round_half_up(x):
    """Kotlin roundToLong and roundToInt (Math.round): x.5 goes up."""
    return int(math.floor(x + 0.5))


def round10(ms):
    """Timecode.roundTo10ms."""
    return ((ms + 5) // 10) * 10


def import_bounds(rallies, rows, st, video_seconds):
    """The start and end (ms) of the clip of each imported rally, by the rule of DetectImport.plan."""
    pb, pa = st.get("padBeforeMs", 0), st.get("padAfterMs", 0)
    video_end = video_seconds * 1000 if video_seconds and video_seconds > 0 else float("inf")
    order = sorted(range(len(rallies)), key=lambda i: rallies[i]["start"])      # a stable sort, as sortedBy
    raw_s = [round_half_up(rallies[i]["start"] * 1000) for i in order]
    raw_e = [round_half_up(rallies[i]["end"] * 1000) for i in order]
    clamp = lambda v, lo, hi: min(max(v, lo), hi)
    by_rally = {r["rally"]: r for r in rows}
    out, last_end = {}, 0
    for k, i in enumerate(order):
        lower = 0 if k == 0 else max(0, (min(raw_e[k - 1], raw_s[k]) + raw_s[k]) // 2)
        upper = video_end if k == len(order) - 1 else max(raw_e[k], (raw_e[k] + max(raw_e[k], raw_s[k + 1])) // 2)
        start = round10(clamp(raw_s[k] - pb, lower, max(lower, raw_e[k])))
        end = round10(clamp(raw_e[k] + pa, raw_s[k], min(upper, video_end)))
        if by_rally.get(i, {}).get("pointId"):
            out[i] = (max(start, last_end), end)
            last_end = end
    return out


def import_winner(rally, st, swapped):
    """The winner (project players) that the import gave the clip, or None. Also the winner of the detection
    (project players) when it was under the limit."""
    conf = rally.get("confidence")
    pct = None if conf is None else min(100, max(50, round_half_up(conf * 100)))
    w = rally.get("winner")
    w = FLIP[w] if swapped and w in FLIP else (w if w in FLIP else None)
    used = w if st.get("addWinners", True) and pct is not None and pct >= st.get("minWinnerPercent", 50) else None
    return used, w


def near_player(rally, swapped):
    """DetectImport.nearPlayer: the player of the project at the near end, or None."""
    p1 = rally.get("p1Near")
    return None if p1 is None else (1 if p1 != swapped else 2)


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
    nf = os.path.join(ad, "review-notes.json")
    notes = {n["pointId"]: n for n in (load(nf).get("notes", []) if os.path.exists(nf) else [])}
    outcomes, reasons, details = score.get("outcomes", {}), score.get("reasons", {}), score.get("details", {})
    st = imp["settings"]
    swapped = bool(imp.get("swapped", False))
    bounds = import_bounds(res["rallies"], imp["rows"], st, res.get("videoSeconds"))
    rows, auto_ids = [], set()
    for r in imp["rows"]:
        ra = res["rallies"][r["rally"]]
        used, det = import_winner(ra, st, swapped)
        row = {"rally": r["rally"], "kind": ra.get("kind"), "start": ra["start"], "end": ra["end"],
               "prob": ra.get("prob"), "winner": used, "winnerDetected": det, "confidence": ra.get("confidence")}
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
            row["state"] = "kept"
            a, b = bounds[r["rally"]]
            row["dStart"] = round((p["startMs"] - a) / 1000, 2)
            row["dEnd"] = round((p["endMs"] - b) / 1000, 2)
            auto = p.get("auto") or {}
            row["checked"] = bool(auto.get("checked"))
            near_imp, near_now = near_player(ra, swapped), auto.get("nearPlayer")
            row["playersSwapped"] = near_imp is not None and near_now is not None and near_now != near_imp
            now = outcomes.get(pid, "NONE")
            row["winnerNow"] = None if now == "NONE" else now
            row["reason"] = reasons.get(pid)
            row["endingNow"] = (details.get(pid) or {}).get("ending")
        if pid in notes:
            row["note"] = notes[pid].get("text")
        fid = r.get("faultPointId")
        if fid:
            auto_ids.add(fid)
            f = pts.get(fid)
            row["fault"] = "deleted" if f is None else ("kept" if (details.get(fid) or {}).get("ending") == "SERVE_FAULT" else "changed")
        rows.append(row)
    # Clips that the user added: not made by an import, not in the project before this import, inside its trim.
    existing = imp.get("existingPointIds")
    lo, hi = imp.get("rangeStartMs"), imp.get("rangeEndMs")
    added = [{"id": i, "start": p["startMs"] / 1000, "end": p["endMs"] / 1000, "winner": outcomes.get(i),
              "ending": (details.get(i) or {}).get("ending")}
             for i, p in pts.items() if i not in auto_ids and not p.get("auto") and (existing is None or i not in existing)
             and (lo is None or p["startMs"] >= lo) and (hi is None or p["endMs"] <= hi)]
    kept = [r for r in rows if r["state"] == "kept"]
    deleted = [r for r in rows if r["state"] == "deleted"]
    seen = [r for r in kept if r["checked"]]
    moved_s = [r for r in seen if abs(r["dStart"]) > 0.3]
    moved_e = [r for r in seen if abs(r["dEnd"]) > 0.3]

    def verdict(r):
        """same, swap (the near player was wrong), reason, wrong; for a clip with a winner of the import."""
        if r["winnerNow"] == r["winner"]:
            return "same"
        if r["playersSwapped"] and r["winnerNow"] == FLIP.get(r["winner"]):
            return "swap"
        return "reason" if r["reason"] else "wrong"
    with_w = [r for r in seen if r["winner"]]
    for r in kept:
        if r["winner"]:
            r["winnerVerdict"] = verdict(r)
    count = lambda v: sum(r["winnerVerdict"] == v for r in with_w)
    under = [r for r in seen if not r["winner"] and r["winnerDetected"]]          # under the limit: not set
    for r in under:
        w = FLIP[r["winnerDetected"]] if r["playersSwapped"] else r["winnerDetected"]
        r["winnerUnderLimitRight"] = None if r["winnerNow"] is None or r["reason"] else r["winnerNow"] == w

    def med(v):
        v = sorted(v)
        return v[len(v) // 2] if v else 0.0

    n_det = len(kept) + len(deleted)
    print(f"import {os.path.basename(imp_file)} of {imp['resultFile']} (players swapped at the import: {swapped})")
    print(f"detected rallies in the project: {n_det}; kept {len(kept)}, deleted {len(deleted)} "
          f"(precision {100 * len(kept) / max(1, n_det):.1f}%); added by the user {len(added)} "
          f"(recall {100 * len(kept) / max(1, len(kept) + len(added)):.1f}%)")
    if existing is None:
        print("  note: an old import record with no list of the clips before the import; 'added' can have older clips")
    print(f"reviewed by the user (checked): {len(seen)} of {len(kept)} kept; the counts below use only these")
    for k in ("rally", "short"):
        a, b = sum(r["kind"] == k for r in kept), sum(r["kind"] == k for r in deleted)
        print(f"  {k}: kept {a}, deleted {b}")
    print(f"start moved (> 0.3 s): {len(moved_s)} of {len(seen)}, median change {med([r['dStart'] for r in moved_s]):+.2f} s; "
          f"end moved: {len(moved_e)}, median change {med([r['dEnd'] for r in moved_e]):+.2f} s; "
          f"median of all: start {med([r['dStart'] for r in seen]):+.2f} s, end {med([r['dEnd'] for r in seen]):+.2f} s")
    judged = len(with_w) - count("reason")
    print(f"winners set by the import: {len(with_w)}; not changed {count('same')} "
          f"({100 * (count('same') + count('swap')) / max(1, judged):.1f}% right near/far of those with no reason); "
          f"players swapped {count('swap')}; changed by the user {count('wrong')}; changed with a reason {count('reason')}")
    # "Swap players" is an explicit act of the user, also on a clip that the user did not review: count all kept clips.
    print(f"players swapped by the user (near player wrong): {sum(r['playersSwapped'] for r in kept)} of {len(kept)}")
    for lo_c, hi_c in ((0.5, 0.6), (0.6, 0.7), (0.7, 0.8), (0.8, 0.9), (0.9, 1.01)):
        g = [r for r in with_w if lo_c <= (r["confidence"] or 0) < hi_c and r["winnerVerdict"] != "reason"]
        if g:
            print(f"  confidence {lo_c:.1f}-{min(hi_c, 1.0):.1f}: {sum(r['winnerVerdict'] in ('same', 'swap') for r in g)} of {len(g)} right")
    u = [r for r in under if r["winnerUnderLimitRight"] is not None]
    if under:
        print(f"winners under the limit {st.get('minWinnerPercent', 50)} %: {len(under)} not set; "
              f"the user set {len(u)}, the detection was right for {sum(r['winnerUnderLimitRight'] for r in u)}")
    faults = [r for r in rows if "fault" in r]
    if faults:
        print(f"fault clips: {len(faults)}; kept {sum(r['fault'] == 'kept' for r in faults)}, deleted {sum(r['fault'] == 'deleted' for r in faults)}, "
              f"changed {sum(r['fault'] == 'changed' for r in faults)}; faults added by the user "
              f"{sum(a['ending'] == 'SERVE_FAULT' for a in added)}")
    if notes:
        print(f"review notes: {len(notes)} ({sum(1 for r in rows if 'note' in r)} on clips of this import)")
    print("stage seconds:", res.get("stageSeconds"), "| video seconds:", res.get("videoSeconds"))
    out = {"import": os.path.basename(imp_file), "result": imp["resultFile"], "settings": st, "swapped": swapped,
           "existingKnown": existing is not None, "rows": rows, "added": added,
           "notes": [n for n in notes.values() if n.get("resultFile") in (None, imp["resultFile"])],
           "run": {k: res.get(k) for k in ("created", "work", "videoId", "version", "partial", "warnings")},
           "stageSeconds": res.get("stageSeconds"), "videoSeconds": res.get("videoSeconds"), "video": res.get("video")}
    stamp = os.path.basename(imp_file)[len("import-"):-len(".json")]
    json.dump(out, open(os.path.join(ad, f"corrections-{stamp}.json"), "w", encoding="utf-8"), indent=1)
    return out


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)

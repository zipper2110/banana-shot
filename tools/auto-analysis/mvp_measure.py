"""MVP measures 3, 5 and 6 of milestone M2 (value box 1, the user 2026-11-28; gap-program.md section 1, A56 E217).

For the rally rows of one match against its marks (lab.match_points: one-to-one, overlap 50 % of the shorter point):
  3 bounds   the share of the matched real points (outcome not NONE) with the row start within START_S of the
             marked start and the row end within END_S of the marked end. Target 90 %.
  5 server   the share of the matched real points with the right server player. The truth is sides.json "server"
             (the score replay of the app). The row player is "server" (P1 = the player at the near end in the first
             rally, chain.winners); the user names the players once, so the names of each match take the mapping
             (P1 = P1, or P1 = P2) that fits more points. A row with no server is a miss. Also the server end
             ("serverNear" against ends.json "serverNearRules"). Target 95 %.
  6 doubles  the share of the marked double faults (gt.json "ending" SERVE_FAULT) that have a matched row. Only
             matches with endings in gt.json have this measure. Target 70 %.
Usage: python mvp_measure.py <bench entry folder> [set name, default "Lab LOMO"]
       python mvp_measure.py <result.json or a file with "rows" or "rallies"> <match id>
"""
import json, os, sys
import lab

START_S, END_S = 1.0, 2.0
TARGETS = {"bounds": 0.90, "server": 0.95, "doubleFaults": 0.70}
WORST = 5


def marks(mid):
    """(points, endings, servers, server ends) of the marks of mid: points = lab.gt_points; endings = the gt.json
    "ending" of each point (None when not marked); servers = the sides.json "server" of each point (by id; None when
    missing); server ends = ends.json "serverNearRules" (by id; None when missing)."""
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))
    ids = [p["id"] for p in g["points"]]

    def by_id(name, key):
        f = os.path.join(lab.mdir(mid), name)
        if not os.path.exists(f):
            return [None] * len(ids)
        d = {x["id"]: x.get(key) for x in json.load(open(f))}
        return [d.get(i) for i in ids]
    return lab.gt_points(mid), [p.get("ending") for p in g["points"]], by_id("sides.json", "server"), by_id("ends.json", "serverNearRules")


def measure(points, endings, servers, server_ends, rows):
    """The 3 measures of one match. points: [(start, end, outcome)]; endings, servers, server_ends: one value for each
    point (or None); rows: the rally rows ("start", "end", "server", "serverNear")."""
    pairs = lab.match_points([(r["start"], r["end"]) for r in rows], points)
    real = [(gi, j) for gi, j in pairs if points[gi][2] != "NONE"]
    errs = []
    for gi, j in real:
        ds, de = rows[j]["start"] - points[gi][0], rows[j]["end"] - points[gi][1]
        errs.append({"mark": [round(points[gi][0], 2), round(points[gi][1], 2)], "row": [round(rows[j]["start"], 2), round(rows[j]["end"], 2)],
                     "start": round(ds, 2), "end": round(de, 2), "ok": abs(ds) <= START_S and abs(de) <= END_S,
                     "score": round(max(abs(ds) / START_S, abs(de) / END_S), 3)})
    bad = sorted([e for e in errs if not e["ok"]], key=lambda e: -e["score"])
    bounds = {"n": len(errs), "ok": sum(e["ok"] for e in errs), "miss": len(bad),
              "startMiss": sum(abs(e["start"]) > START_S for e in errs), "endMiss": sum(abs(e["end"]) > END_S for e in errs), "bad": bad}
    sv = [(servers[gi], rows[j].get("server")) for gi, j in real if servers[gi] in ("P1", "P2")]
    same = sum(t == r for t, r in sv)
    swap = sum(r is not None and t != r for t, r in sv)
    end = [(server_ends[gi], rows[j].get("serverNear")) for gi, j in real if server_ends[gi] is not None]
    server = {"n": len(sv), "ok": max(same, swap), "names": "same" if same >= swap else "swapped", "none": sum(r is None for _, r in sv),
              "endN": len(end), "endOk": sum(r is not None and bool(r) == bool(t) for t, r in end)}
    server["miss"] = server["n"] - server["ok"]
    got = {gi for gi, _ in pairs}
    df = [gi for gi, e in enumerate(endings) if e == "SERVE_FAULT" and points[gi][2] != "NONE"]
    doubles = None if not any(e is not None for e in endings) else {
        "n": len(df), "ok": sum(gi in got for gi in df),
        "missed": [[round(points[gi][0], 2), round(points[gi][1], 2)] for gi in df if gi not in got]}
    if doubles is not None:
        doubles["miss"] = doubles["n"] - doubles["ok"]
    return {"bounds": bounds, "server": server, "doubleFaults": doubles}


def total(per):
    """Pooled shares over the matches of {mid: measure()}, and the WORST bound errors of all."""
    out = {}
    for k in ("bounds", "server", "doubleFaults"):
        ms = [m[k] for m in per.values() if m[k] is not None]
        if not ms:
            out[k] = None
            continue
        n, ok = sum(m["n"] for m in ms), sum(m["ok"] for m in ms)
        out[k] = {"n": n, "ok": ok, "miss": n - ok, "share": ok / n if n else None, "target": TARGETS[k],
                  "met": bool(n and ok / n >= TARGETS[k])}
    if out["server"] is not None:
        ms = [m["server"] for m in per.values()]
        en, eo = sum(m["endN"] for m in ms), sum(m["endOk"] for m in ms)
        out["server"].update(none=sum(m["none"] for m in ms), endShare=eo / en if en else None, endN=en)
    out["worstBounds"] = sorted([dict(e, match=mid) for mid, m in per.items() for e in m["bounds"]["bad"]], key=lambda e: -e["score"])[:WORST]
    return out


def run(rows_by_match):
    """{mid: rows} -> {"perMatch": {mid: measure}, "total": total}."""
    per = {mid: measure(*marks(mid), rows) for mid, rows in rows_by_match.items()}
    return {"perMatch": per, "total": total(per)}


def fmt(t):
    def s(k):
        x = t[k]
        return "n/a" if x is None else f"{100 * x['share']:.1f} % ({x['ok']} of {x['n']}, miss {x['miss']}, target {100 * x['target']:.0f} %)"
    lines = [f"3 bounds (start {START_S:g} s, end {END_S:g} s): {s('bounds')}", f"5 server player: {s('server')}"]
    if t["server"] is not None and t["server"]["endShare"] is not None:
        lines.append(f"  server end: {100 * t['server']['endShare']:.1f} % of {t['server']['endN']}; rows with no server: {t['server']['none']}")
    lines.append(f"6 double faults found: {s('doubleFaults')}")
    lines += [f"  worst bounds {e['match']} mark {e['mark']} row {e['row']} start {e['start']:+.2f} s end {e['end']:+.2f} s" for e in t["worstBounds"]]
    return "\n".join(lines)


if __name__ == "__main__":
    p = sys.argv[1]
    if os.path.isdir(p):
        rows = json.load(open(os.path.join(p, "rows.json")))[sys.argv[2] if len(sys.argv) > 2 else "Lab LOMO"]
    else:
        d = json.load(open(p))
        rows = {sys.argv[2]: d.get("rows") or d.get("rallies")}
    res = run(rows)
    print(fmt(res["total"]))
    for mid, m in res["perMatch"].items():
        print(mid, {k: None if v is None else f"{v['ok']}/{v['n']}" for k, v in m.items()})

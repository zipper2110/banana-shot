"""The history of the case library (gap program S1 item 8): one command runs the current chain on the published
fragments and compares the events of each fragment with the user's labels. Each run is one history entry.

The chain: the lab path of bench.py (CPU), not a run on the clip. The flights and net events are made again by the
code key from the frozen lab ball files (bench.app_nets; under the GPU lock when the key is new, about 18 min),
the rally rows come from pipeline.run with the models that did not see the match (LOMO). Then each fragment takes
the rows and the events inside its window. A fragment run of detect.py on the clip needs the context input (whole
match statistics; open), so the history measures the full-match chain on the fragment spans.

Window of a fragment: the label span, widened to the first and the last labeled event (the page asks for the
first-serve fault in the pad before the point). The user labels all events of the span from zero, so an event of
the chain in the window with no label near it counts as a false event.

Per fragment (rules of score_events.py, E75):
  rallies   marked points in the window against the chain rows (lab.match_points: overlap 50 % of the shorter)
  hits      labeled serves + shots against the chain shots with a flight (shot_chain.build on the window), within
            TOL_HIT, one to one; hitter right = the end (near, far) of the label
  serves    labeled serves against the server end and the fault of the matched chain row. The fault of a serve is
            label_faults.infer_faults (S4 E159; the X mark keeps the flag, the user no longer presses X); a point
            with an "unsure" serve and no fault is not counted for the fault ("faultUnsure")
  bounces   labeled bounces against the bounce events, within TOL_BOUNCE
  nets      labeled net touches ("stop" = net, "cord") against the net events with conf >= NET_CONF, within TOL_NET
  last shot the end of the last labeled event of each point (net, out, in) against the last shot of the chain row
A fragment with no labels gets the chain counts only.

Output: <lab>/ws/s1/cases/history/<time>-<commit8>.json (all details), one line in history.jsonl, and
report.md (the table of this run against the entry before it).
Use: python case_history.py [--level small] [--frozen-flights] [--note text]
"""
import argparse, hashlib, json, os, sys, time
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import lab, case_lib, score_events as se, shot_chain as sc
from label_faults import infer_faults
TOL_HIT, TOL_BOUNCE, TOL_NET = se.TOL_HIT, se.TOL_BOUNCE, se.TOL_NET
HIST = os.path.join(case_lib.ROOT, "history")
KINDS = ("hits", "bounces", "nets")


def window(row, L):
    a, b = row["span"]
    if L and L.get("events"):
        ts = [e["t"] for e in L["events"]]
        a, b = min(a, min(ts)), max(b, max(ts))
    return a, b


def last_end(evs):
    """The end of a point from its labeled events: net (a net touch that stops the ball), out (an out bounce), else in."""
    if not evs:
        return None
    e = evs[-1]
    if e["kind"] == "net" and e.get("net") != "cord":
        return "net"
    if e["kind"] == "bounce" and e.get("out"):
        return "out"
    return "in"


def chain_end(r):
    """The end of the last shot of a chain row: the case of the shot chain when the row used it, else the case of the
    ball rule (lastShot). Cases: net, net_early, out, out_seen, out_early, in, in_seen, in_early, late_in, late_out, ..."""
    ch = r.get("chain")
    c = ch.get("case") if isinstance(ch, dict) and ch.get("used") else None
    if c is None:
        ls = r.get("lastShot")
        c = ls.get("case") if isinstance(ls, dict) else None
    if not c:
        return None
    parts = c.split("_")
    return "net" if "net" in parts else "out" if "out" in parts else "in" if "in" in parts else None


def fragment(row, L, data, rows):
    a, b = window(row, L)
    shots = [s for s in se.chain_shots(data, a, b) if a - TOL_HIT <= s["t"] <= b + TOL_HIT and s["fl"]]
    bev = [e for e in (data["full"].get("events") or []) if e["kind"] == "bounce" and a - TOL_BOUNCE <= e["t"] <= b + TOL_BOUNCE]
    nev = [e for e in data["nets"] if e["kind"] in ("net", "cord") and e["conf"] >= sc.NET_CONF and a - TOL_NET <= e["t"] <= b + TOL_NET]
    crow = [r for r in rows if r["end"] > a and r["start"] < b]
    pts = [p for p in row["points"] if p["endMs"] / 1000 > a and p["startMs"] / 1000 < b]
    pairs = lab.match_points([(r["start"], r["end"]) for r in crow], [(p["startMs"] / 1000, p["endMs"] / 1000) for p in pts])
    out = {"id": row["id"], "match": row["match"], "window": [round(a, 3), round(b, 3)], "control": row.get("control", False),
           "kinds": row.get("kinds", []), "labeled": L is not None,
           "chain": {"rallies": len(crow), "hits": len(shots), "bounces": len(bev), "nets": len(nev),
                     "serverNear": [r.get("serverNear") for r in crow], "fault": [r.get("fault") is not None for r in crow],
                     "lastEnd": [chain_end(r) for r in crow]},
           "rallies": {"marked": len(pts), "detected": len(crow), "matched": len(pairs)}}
    if L is None:
        return out
    ev = sorted(L["events"], key=lambda e: e["t"])
    lh = [e for e in ev if e["kind"] in ("serve", "shot")]
    m = se.match([s["t"] for s in shots], [e["t"] for e in lh], TOL_HIT)
    hitter = sum(("near" if shots[i]["near"] else "far") == lh[j].get("end") for i, j in m)
    lb = [e for e in ev if e["kind"] == "bounce"]
    mb = se.match([e["t"] for e in bev], [e["t"] for e in lb], TOL_BOUNCE)
    ln = [e for e in ev if e["kind"] == "net"]
    mn = se.match([e["t"] for e in nev], [e["t"] for e in ln], TOL_NET)
    kind_ok = sum(nev[i]["kind"] == ("cord" if ln[j].get("net") == "cord" else "net") for i, j in mn)
    out["hits"] = {"labeled": len(lh), "chain": len(shots), "matched": len(m), "hitterOk": int(hitter),
                   "missed": [round(lh[j]["t"], 3) for j in range(len(lh)) if j not in {q for _, q in m}],
                   "false": [round(shots[i]["t"], 3) for i in range(len(shots)) if i not in {p for p, _ in m}]}
    out["bounces"] = {"labeled": len(lb), "chain": len(bev), "matched": len(mb),
                      "missed": [round(lb[j]["t"], 3) for j in range(len(lb)) if j not in {q for _, q in mb}],
                      "false": [round(bev[i]["t"], 3) for i in range(len(bev)) if i not in {p for p, _ in mb}]}
    out["nets"] = {"labeled": len(ln), "chain": len(nev), "matched": len(mn), "kindOk": int(kind_ok),
                   "missed": [round(ln[j]["t"], 3) for j in range(len(ln)) if j not in {q for _, q in mn}],
                   "false": [round(nev[i]["t"], 3) for i in range(len(nev)) if i not in {p for p, _ in mn}]}
    # serves and the last shot of each matched point: the labeled events from the point start - 25 s (the fault
    # serve before it), but not before the end of the marked point before it, to the point end + 1 s
    sv = {"points": 0, "serverOk": 0, "faultOk": 0, "faultN": 0, "faultUnsure": 0, "lastOk": 0, "lastN": 0, "detail": []}
    fault = {round(x["t"], 6): x["fault"] for x in infer_faults(ev)}      # over all events of the fragment (the next serve)
    for gi, j in pairs:
        p, r = pts[gi], crow[j]
        s0, s1 = p["startMs"] / 1000, p["endMs"] / 1000
        prev = max([q["endMs"] / 1000 for q in row["points"] if q["endMs"] / 1000 <= s0] or [s0 - 25.0])
        pe = [e for e in ev if max(s0 - 25.0, prev) <= e["t"] <= s1 + 1.0]
        serves = [e for e in pe if e["kind"] == "serve"]
        fs = [fault[round(e["t"], 6)] for e in serves]
        play = [e for e, f in zip(serves, fs) if f is not True]
        label_fault = True if True in fs else "unsure" if "unsure" in fs else False
        d = {"point": p["id"], "labelServe": play[-1]["end"] if play else None, "labelFault": label_fault,
             "chainServerNear": r.get("serverNear"), "chainFault": r.get("fault") is not None,
             "labelEnd": last_end([e for e in pe if e["t"] >= s0 - 1.0]), "chainEnd": chain_end(r)}
        if d["labelServe"] is not None and d["chainServerNear"] is not None:
            sv["points"] += 1
            sv["serverOk"] += int((d["labelServe"] == "near") == d["chainServerNear"])
            if d["labelFault"] == "unsure":             # the marks do not decide it: not a fault and not "no fault"
                sv["faultUnsure"] += 1
            else:
                sv["faultN"] += 1
                sv["faultOk"] += int(d["labelFault"] == d["chainFault"])
        if d["labelEnd"] and d["chainEnd"]:
            sv["lastN"] += 1
            sv["lastOk"] += int(d["labelEnd"] == d["chainEnd"])
        sv["detail"].append(d)
    out["serves"] = sv
    return out


def total(frs):
    t = {"fragments": len(frs), "labeled": sum(f["labeled"] for f in frs)}
    for k in ("marked", "detected", "matched"):
        t["rallies_" + k] = sum(f["rallies"][k] for f in frs)
    lab_ = [f for f in frs if f["labeled"]]
    for k in KINDS:
        for q in ("labeled", "chain", "matched"):
            t[f"{k}_{q}"] = sum(f[k][q] for f in lab_)
    t["hitterOk"] = sum(f["hits"]["hitterOk"] for f in lab_)
    t["netKindOk"] = sum(f["nets"]["kindOk"] for f in lab_)
    for q in ("points", "serverOk", "faultOk", "faultN", "faultUnsure", "lastOk", "lastN"):
        t["serve_" + q] = sum(f["serves"][q] for f in lab_)
    return t


def fmt(f):
    r = f["rallies"]
    s = f"| {f['id']} | {f['match']} | {'control' if f['control'] else ','.join(f['kinds'])} | {r['matched']}/{r['marked']} ({r['detected']} rows) |"
    if not f["labeled"]:
        c = f["chain"]
        return s + f" no labels | chain {c['hits']} | chain {c['bounces']} | chain {c['nets']} | - | - |"
    h, b, n, v = f["hits"], f["bounces"], f["nets"], f["serves"]
    return (s + " yes | " + f"{h['matched']}/{h['labeled']} (chain {h['chain']}, hitter {h['hitterOk']})" + " | "
            + f"{b['matched']}/{b['labeled']} (chain {b['chain']})" + " | " + f"{n['matched']}/{n['labeled']} (chain {n['chain']})" + " | "
            + (f"{v['serverOk']}/{v['points']}, fault {v['faultOk']}/{v['faultN']}" + (f" ({v['faultUnsure']} unsure)" if v["faultUnsure"] else "")
               if v["points"] else "-") + " | "
            + (f"{v['lastOk']}/{v['lastN']}" if v["lastN"] else "-") + " |")


def run(a):
    import bench
    t0 = time.time()
    bench.LAB_FLIGHTS = "frozen" if a.frozen_flights else "remake"
    from ball_full import WINDOWS
    commit, dirty = bench.code_commit()
    nets = bench.app_nets(list(WINDOWS))             # the flights and net events by the code key (cache, or a run under the lock)
    res = bench.run_lab(nets)                        # the rally rows of the chain (CPU, LOMO)
    man = json.load(open(os.path.join(case_lib.ROOT, "manifest.json")))["fragments"]
    labels = case_lib.labels()
    data, frs = {}, []
    for row in man:
        if row["level"] != a.level or row["match"] not in WINDOWS:
            continue
        mid = row["match"]
        if mid not in data:
            data[mid] = se.load(mid, os.path.dirname(nets[mid]))
        frs.append(fragment(row, labels.get(row["id"]), data[mid], res[mid][1]))
    entry = {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "commit": commit, "dirty": dirty, "level": a.level, "note": a.note,
             "flights": {m: os.path.basename(os.path.dirname(os.path.dirname(nets[m]))) for m in nets},
             "labFlights": bench.LAB_FLIGHTS,
             "labels": {k: hashlib.sha256(json.dumps(v, sort_keys=True).encode()).hexdigest()[:16] for k, v in labels.items()},
             "total": total(frs), "fragments": frs, "seconds": None}
    os.makedirs(HIST, exist_ok=True)
    prev = None
    hj = os.path.join(HIST, "history.jsonl")
    if os.path.exists(hj):
        lines = [json.loads(l) for l in open(hj, encoding="utf-8") if l.strip()]
        lines = [l for l in lines if l.get("level") == a.level and l.get("labFlights", "remake") == bench.LAB_FLIGHTS]
        prev = lines[-1] if lines else None
    entry["seconds"] = round(time.time() - t0)
    name = time.strftime("%Y%m%d-%H%M%S") + "-" + commit[:8]
    json.dump(entry, open(os.path.join(HIST, name + ".json"), "w", encoding="utf-8"), indent=1, default=float)
    with open(hj, "a", encoding="utf-8") as f:
        f.write(json.dumps({"entry": name, "time": entry["time"], "commit": commit[:12], "dirty": dirty, "level": a.level,
                            "note": a.note, "labFlights": bench.LAB_FLIGHTS, "flights": entry["flights"], "total": entry["total"],
                            "perFragment": {x["id"]: {k: (x[k]["matched"], x[k]["labeled"]) for k in KINDS if k in x}
                                            | {"rallies": (x["rallies"]["matched"], x["rallies"]["marked"])} for x in frs}}) + "\n")
    t = entry["total"]
    def pct(k, n):
        return f"{k}/{n} ({100 * k / n:.0f} %)" if n else "-"
    L = [f"# Case library history: {a.level} level, {entry['time']}", "",
         f"Code `{commit[:12]}`{' (dirty)' if dirty else ''}. Flights: {bench.LAB_FLIGHTS} (key {', '.join(sorted(set(entry['flights'].values())))}). "
         f"Fragments {t['fragments']}, with the user's labels {t['labeled']}. Run time {entry['seconds']} s. Entry `{name}`.", "",
         *([f"Note: {a.note}", ""] if a.note else []),
         "Totals over the labeled fragments (rallies: all fragments). The user labels all events of the span, so chain events "
         "without a label are false events.", "",
         "| Measure | This run | Entry before (same level and flights) |", "|---|---|---|"]
    pt = (prev or {}).get("total", {})
    rowsT = [("Rallies found (all fragments)", "rallies_matched", "rallies_marked"),
             ("Hits found (serves and shots)", "hits_matched", "hits_labeled"), ("Chain hits right", "hits_matched", "hits_chain"),
             ("Hitter right (matched hits)", "hitterOk", "hits_matched"),
             ("Bounces found", "bounces_matched", "bounces_labeled"), ("Chain bounces right", "bounces_matched", "bounces_chain"),
             ("Net touches found", "nets_matched", "nets_labeled"), ("Chain net touches right", "nets_matched", "nets_chain"),
             ("Server end right (matched points)", "serve_serverOk", "serve_points"), ("Fault right (matched points, unsure out)", "serve_faultOk", "serve_faultN"),
             ("End of the last shot right (in/out/net)", "serve_lastOk", "serve_lastN")]
    for name_, k, n in rowsT:
        if n == "serve_faultN" and n not in pt:        # an entry before infer_faults: its faults were the X marks, of all points
            n_old = "serve_points"
        else:
            n_old = n
        L.append(f"| {name_} | {pct(t[k], t[n])} | {pct(pt[k], pt[n_old]) if k in pt and n_old in pt else '-'} |")
    L.append(f"| Points with an unsure serve fault (label_faults) | {t['serve_faultUnsure']} | {pt.get('serve_faultUnsure', '-')} |")
    L += ["", "| Fragment | Match | Kind | Rallies | Labels | Hits found | Bounces found | Net touches found | Server, fault | Last shot |",
          "|---|---|---|---|---|---|---|---|---|---|"] + [fmt(f) for f in frs]
    txt = "\n".join(L) + "\n"
    open(os.path.join(HIST, "report.md"), "w", encoding="utf-8").write(txt)
    print(txt)
    print(f"written: {os.path.join(HIST, name + '.json')} ({entry['seconds']} s)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--level", default="small")
    ap.add_argument("--frozen-flights", action="store_true")
    ap.add_argument("--note", default="")
    run(ap.parse_args())

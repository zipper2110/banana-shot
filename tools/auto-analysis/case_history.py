"""The history of the case library (gap program S1 item 8): one command runs the current chain on the published
fragments and compares the events of each fragment with the user's labels. Each run is one history entry.

The chain: the lab path of bench.py (CPU), not a run on the clip. The flights and net events are made again by the
code key from the frozen lab ball files (bench.app_nets; under the GPU lock when the key is new, about 18 min),
the rally rows come from pipeline.run with the models that did not see the match (LOMO). Then each fragment takes
the rows and the events inside its window. A fragment run of detect.py on the clip needs the context input (whole
match statistics; open), so the history measures the full-match chain on the fragment spans.

Window of a fragment: its label window (A32, case_lib.label_window: from the end of the point before + 3 s, but
not more than 25 s before the label span, or from the user's first mark when the user labeled earlier points of the
clip; to the clip end). Only the labeled events and the chain events inside the
window count; a mark before it stays in the label file and is not used. The user labels all events of the window
from zero, so an event of the chain in the window with no label near it counts as a false event. Before A32: the
label span, widened to the first and the last labeled event.

Per fragment (rules of score_events.py, E75):
  rallies   marked points in the window against the chain rows (lab.match_points: overlap 50 % of the shorter)
  hits      labeled serves + shots against the shots of the chain rows as the app shows them (row["chain"]["shots"],
            "late" too), within TOL_HIT, one to one, over all rows whose chain window (start - 1 s to end + 2.5 s,
            chain.py) meets the fragment window (S4 E173). hitter right = the end (near, far) of the label. A label
            that no row window covers is "outside the rows"; a labeled serve there with a shot of a row fault clip
            within TOL_HIT is "by a fault clip" (the app shows the clip), counted apart. Before this: the window
            chain (shot_chain.build on the window, flights only); hitsWindow keeps its count
  after the point: a chain event with no label between the last labeled event of a marked point and the next
            labeled event is counted apart from the false events (S5 E139): "afterPoint" of hits, bounces, nets
  serves    labeled serves against the server end and the fault of the matched chain row. The fault of a serve is
            label_faults.infer_faults (S4 E159; the X mark keeps the flag, the user no longer presses X); a point
            with an "unsure" serve and no fault is not counted for the fault ("faultUnsure")
  bounces   labeled bounces against the bounce events, within TOL_BOUNCE
  nets      labeled net touches ("stop" = net, "cord") against the net events with conf >= NET_CONF, within TOL_NET
  last shot the end of the last labeled event of each point (net, out, in) against the last shot of the chain row
  label_measure.py (S4, E170; it gets the windows, pairs, fault map and hidden rows of this file): rally borders,
            shot count, fault clips, event time and place errors, bounce in/out (out["borders"], "shotCount",
            "faultClips", "errors"; totals in total()["measure"])
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
import label_measure as lm
TOL_HIT, TOL_BOUNCE, TOL_NET = se.TOL_HIT, se.TOL_BOUNCE, se.TOL_NET
HIST = os.path.join(case_lib.ROOT, "history")
KINDS = ("hits", "bounces", "nets")


def window(row, L=None):
    """The label window of the fragment (A32), source s; L = its labels (the first mark can move the start)."""
    a, b = case_lib.window_of(row["id"], L)
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


ROW_PRE, ROW_POST = 1.0, 2.5                           # the flight window of a chain row (chain.py): start - 1 s, end + 2.5 s


def row_shots(rows, a, b):
    """The shots of the chain rows whose chain window meets a..b, in the window (TOL_HIT); each with its row."""
    out = []
    for r in rows:
        ch = r.get("chain")
        if r["start"] - ROW_PRE <= b and r["end"] + ROW_POST >= a and isinstance(ch, dict):
            out += [dict(s, row=(r["start"], r["end"])) for s in ch.get("shots") or [] if a - TOL_HIT <= s["t"] <= b + TOL_HIT]
    return sorted(out, key=lambda s: s["t"])


def covered(rows, t):
    return any(r["start"] - ROW_PRE <= t <= r["end"] + ROW_POST for r in rows)


def fault_clip_serve(rows, t):
    """A row fault clip has a shot within TOL_HIT of t (the clip that the app shows for a first-serve fault)."""
    return any(abs(q["t"] - t) <= TOL_HIT for r in rows if r.get("fault")
               for q in ((r["fault"].get("chain") or {}).get("shots") or []))


def dead_gaps(points, ev):
    """The times (t0, t1) after each marked point: from its last labeled event to the next labeled event (open ends)."""
    out = []
    for p in points:
        pe = [e["t"] for e in p["events"]]
        if not pe:
            continue
        t0 = max(pe)
        nxt = [e["t"] for e in ev if e["t"] > t0]
        out.append((t0, min(nxt) if nxt else float("inf")))
    return out


def after_point(times, gaps):
    return sum(any(t0 < t < t1 for t0, t1 in gaps) for t in times)


def fragment(row, L, data, rows, hidden=()):
    """hidden: the rows of the match that the bench hides as first-serve faults (bench.hidden_fault_rows)."""
    a, b = window(row, L)
    shots = row_shots(rows, a, b)                      # the row chain (S4 E173), not the window chain
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
    ev = sorted([e for e in L["events"] if a <= e["t"] <= b], key=lambda e: e["t"])     # A32: marks before the window are not used
    out["marksOutside"] = len(L["events"]) - len(ev)
    lh = [e for e in ev if e["kind"] in ("serve", "shot")]
    m = se.match([s["t"] for s in shots], [e["t"] for e in lh], TOL_HIT)
    hitter = sum(("near" if shots[i]["near"] else "far") == lh[j].get("end") for i, j in m)
    wshots = [s for s in se.chain_shots(data, a, b) if a - TOL_HIT <= s["t"] <= b + TOL_HIT and s["fl"]]
    wm = se.match([s["t"] for s in wshots], [e["t"] for e in lh], TOL_HIT)
    lb = [e for e in ev if e["kind"] == "bounce"]
    mb = se.match([e["t"] for e in bev], [e["t"] for e in lb], TOL_BOUNCE)
    ln = [e for e in ev if e["kind"] == "net"]
    mn = se.match([e["t"] for e in nev], [e["t"] for e in ln], TOL_NET)
    kind_ok = sum(nev[i]["kind"] == ("cord" if ln[j].get("net") == "cord" else "net") for i, j in mn)
    missed = [j for j in range(len(lh)) if j not in {q for _, q in m}]
    outside = [j for j in missed if not covered(rows, lh[j]["t"])]
    by_clip = [j for j in outside if lh[j]["kind"] == "serve" and fault_clip_serve(rows, lh[j]["t"])]
    out["hits"] = {"labeled": len(lh), "chain": len(shots), "matched": len(m), "hitterOk": int(hitter),
                   "outsideRows": len(outside), "byFaultClip": len(by_clip),
                   "missed": [round(lh[j]["t"], 3) for j in missed],
                   "false": [round(shots[i]["t"], 3) for i in range(len(shots)) if i not in {p for p, _ in m}]}
    out["hitsWindow"] = {"chain": len(wshots), "matched": len(wm)}     # the measure before E173 (the window chain)
    out["bounces"] = {"labeled": len(lb), "chain": len(bev), "matched": len(mb),
                      "missed": [round(lb[j]["t"], 3) for j in range(len(lb)) if j not in {q for _, q in mb}],
                      "false": [round(bev[i]["t"], 3) for i in range(len(bev)) if i not in {p for p, _ in mb}]}
    out["nets"] = {"labeled": len(ln), "chain": len(nev), "matched": len(mn), "kindOk": int(kind_ok),
                   "missed": [round(ln[j]["t"], 3) for j in range(len(ln)) if j not in {q for _, q in mn}],
                   "false": [round(nev[i]["t"], 3) for i in range(len(nev)) if i not in {p for p, _ in mn}]}
    # serves and the last shot of each matched point: the labeled events from the point start - 25 s (the fault
    # serve before it), but not before the end of the marked point before it, to the point end + 1 s
    sv = {"points": 0, "serverOk": 0, "faultOk": 0, "faultN": 0, "faultUnsure": 0, "lastOk": 0, "lastN": 0, "detail": []}
    fault = {round(x["t"], 6): x["fault"] for x in infer_faults(ev)}      # over the events of the label window (the next serve)
    points = []                                        # the point windows for label_measure
    for gi, j in pairs:
        p, r = pts[gi], crow[j]
        s0, s1 = p["startMs"] / 1000, p["endMs"] / 1000
        prev = max([q["endMs"] / 1000 for q in row["points"] if q["endMs"] / 1000 <= s0] or [s0 - 25.0])
        pe = [e for e in ev if max(s0 - 25.0, prev) <= e["t"] <= s1 + 1.0]
        points.append({"point": p, "row": r, "events": pe, "fault": fault})
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
    out["borders"] = lm.borders(points)
    out["shotCount"] = lm.shot_count(points)
    out["faultClips"] = lm.fault_clips(crow, ev, fault, [r for r in hidden if r["end"] > a and r["start"] < b], (a, b))
    out["errors"] = lm.event_errors(ev, fault, data, shots, lh, m, bev, lb, mb, nev, ln, mn)
    gaps = dead_gaps(points_all(row, pts, ev), ev)
    for k in KINDS:
        out[k]["afterPoint"] = after_point(out[k]["false"], gaps)
    return out


def points_all(row, pts, ev):
    """All marked points of the window with their labeled events (the rule of the serve window), paired or not."""
    out = []
    for p in pts:
        s0, s1 = p["startMs"] / 1000, p["endMs"] / 1000
        prev = max([q["endMs"] / 1000 for q in row["points"] if q["endMs"] / 1000 <= s0] or [s0 - 25.0])
        out.append({"events": [e for e in ev if max(s0 - 25.0, prev) <= e["t"] <= s1 + 1.0]})
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
    for k in KINDS:
        t[k + "_afterPoint"] = sum(f[k].get("afterPoint", 0) for f in lab_)
    for q in ("outsideRows", "byFaultClip"):
        t["hits_" + q] = sum(f["hits"].get(q, 0) for f in lab_)
    t["hitsMethod"] = "row"                            # E173: the row chain (before: the window chain)
    t["hitsWindow_matched"] = sum(f["hitsWindow"]["matched"] for f in lab_)
    t["hitsWindow_chain"] = sum(f["hitsWindow"]["chain"] for f in lab_)
    t["netKindOk"] = sum(f["nets"]["kindOk"] for f in lab_)
    for q in ("points", "serverOk", "faultOk", "faultN", "faultUnsure", "lastOk", "lastN"):
        t["serve_" + q] = sum(f["serves"][q] for f in lab_)
    t["measure"] = lm.totals(lab_)                    # label_measure (S4, E170)
    return t


def fmt(f):
    r = f["rallies"]
    s = f"| {f['id']} | {f['match']} | {'control' if f['control'] else ','.join(f['kinds'])} | {r['matched']}/{r['marked']} ({r['detected']} rows) |"
    if not f["labeled"]:
        c = f["chain"]
        return s + f" no labels | chain {c['hits']} | chain {c['bounces']} | chain {c['nets']} | - | - | - |"
    h, b, n, v = f["hits"], f["bounces"], f["nets"], f["serves"]
    return (s + " yes | " + f"{h['matched']}/{h['labeled']} (chain {h['chain']}, hitter {h['hitterOk']})" + " | "
            + f"{b['matched']}/{b['labeled']} (chain {b['chain']})" + " | " + f"{n['matched']}/{n['labeled']} (chain {n['chain']})" + " | "
            + (f"{v['serverOk']}/{v['points']}, fault {v['faultOk']}/{v['faultN']}" + (f" ({v['faultUnsure']} unsure)" if v["faultUnsure"] else "")
               if v["points"] else "-") + " | "
            + (f"{v['lastOk']}/{v['lastN']}" if v["lastN"] else "-") + " | "
            + (f"{fc['right']}/{fc['clips']}" if (fc := f.get("faultClips")) and fc["clips"] else "-") + " |")


def measure_rows(t, pt):
    """The report rows of label_measure.totals (S4, E170): this run and the entry before ("-" when it has no measure)."""
    if not t:
        return []

    def st(d, unit="s", signed=True):
        if not d or not d.get("n"):
            return "-"
        return (f"{d['med']:+.2f} / " if signed and d.get("med") is not None else "") + f"{d['absMed']:.2f} / {d['p90']:.2f} {unit} (n {d['n']})"

    def kn(k, n):
        return f"{k}/{n}" if n else "-"

    def rows(x):
        fc, io, sc_ = x["faultClips"], x["inOut"], x["shotCount"]
        return [st(x["rallyStartPlay"]), st(x["rallyStartFirst"]), st(x["rallyEnd"]),
                f"{kn(fc['right'], fc['clips'])}, wrong {fc['wrong']}"
                + (f", double fault {fc['doubleFault']}" if "doubleFault" in fc else "")   # E171: a clip on a second-serve fault
                + f", unsure {fc['unsure']}, no serve label {fc['noServe']}",
                kn(x["labeledFaultsWithClip"], x["labeledFaults"]), kn(x["hiddenRowsWithFault"], x["hiddenRows"]),
                st(x["hitTimeErr"], signed=False), st(x["bounceTimeErr"], signed=False), st(x["netTimeErr"], signed=False),
                st(x["bouncePlaceErr"], "m", signed=False),
                f"{kn(io['ok'], io['n'])} (serve bounces {io['serveBounces']}: side checked {io['sideCheck']}, not {io['noSideCheck']})",
                f"{kn(sc_['ok'], sc_['n'])}, off by 1 {sc_['off1']}, mean diff {sc_['meanDiff'] if sc_['meanDiff'] is not None else '-'}"]
    names = ["Rally start - serve in play (signed med / |med| / p90)", "Rally start - first serve of the point",
             "Rally end - last labeled event", "Fault clips right (labeled first-serve fault)",
             "Labeled first-serve faults with a chain fault clip", "Hidden fault rows with a labeled fault serve",
             "Hit time error (|med| / p90)", "Bounce time error", "Net time error", "Bounce place error",
             "Bounce in/out right (matched bounces)", "Shot count right (matched points; chain shots - labels)"]
    old = rows(pt) if pt else ["-"] * len(names)
    return [f"| {n} | {v} | {o} |" for n, v, o in zip(names, rows(t), old)]


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
    data, hidden, frs = {}, {}, []
    for row in man:
        if row["level"] != a.level or row["match"] not in WINDOWS:
            continue
        mid = row["match"]
        if mid not in data:
            data[mid] = se.load(mid, os.path.dirname(nets[mid]))
            nc, fc = bench.hidden_fault_rows(mid, res[mid][1])
            hidden[mid] = [res[mid][1][k] for k in nc + fc]
        frs.append(fragment(row, labels.get(row["id"]), data[mid], res[mid][1], hidden[mid]))
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
    if pt and pt.get("hitsMethod") != "row":         # the entry before has the window chain: both, this one time
        L.append(f"| Hits found by the window chain (the measure of the entry before) | {pct(t['hitsWindow_matched'], t['hits_labeled'])} "
                 f"| {pct(pt['hits_matched'], pt['hits_labeled']) if 'hits_matched' in pt else '-'} |")
    for name_, k, n in rowsT:
        if k == "hits_matched" and n == "hits_labeled":
            L.append(f"| Hits found by the row chain (S4 E173) | {pct(t[k], t[n])} | "
                     + (f"{pct(pt[k], pt[n])}" if pt.get("hitsMethod") == "row" else "-") + " |")
            L.append(f"| Hits found, with the serves of fault clips | {pct(t[k] + t['hits_byFaultClip'], t[n])} | "
                     + (f"{pct(pt[k] + pt['hits_byFaultClip'], pt[n])}" if pt.get("hitsMethod") == "row" else "-") + " |")
            L.append(f"| Labeled hits outside the rows (of them serves of fault clips) | {t['hits_outsideRows']} ({t['hits_byFaultClip']}) | "
                     + (f"{pt['hits_outsideRows']} ({pt['hits_byFaultClip']})" if pt.get("hitsMethod") == "row" else "-") + " |")
            continue
        if n.endswith("_chain"):                      # the chain precision, then without the events after the point
            kk = n[:-len("_chain")]
            old = pt.get(kk + "_afterPoint")
            L.append(f"| {name_} | {pct(t[k], t[n])} | {pct(pt[k], pt[n]) if k in pt and n in pt and (kk != 'hits' or pt.get('hitsMethod') == 'row') else '-'} |")
            L.append(f"| {name_}, events after the point out ({t[kk + '_afterPoint']} events) | {pct(t[k], t[n] - t[kk + '_afterPoint'])} | "
                     + (f"{pct(pt[k], pt[n] - old)}" if old is not None and (kk != "hits" or pt.get("hitsMethod") == "row") else "-") + " |")
            continue
        if n == "serve_faultN" and n not in pt:        # an entry before infer_faults: its faults were the X marks, of all points
            n_old = "serve_points"
        else:
            n_old = n
        L.append(f"| {name_} | {pct(t[k], t[n])} | {pct(pt[k], pt[n_old]) if k in pt and n_old in pt else '-'} |")
    L.append(f"| Points with an unsure serve fault (label_faults) | {t['serve_faultUnsure']} | {pt.get('serve_faultUnsure', '-')} |")
    L += measure_rows(t.get("measure"), pt.get("measure"))
    L += ["", "| Fragment | Match | Kind | Rallies | Labels | Hits found | Bounces found | Net touches found | Server, fault | Last shot | Fault clips |",
          "|---|---|---|---|---|---|---|---|---|---|---|"] + [fmt(f) for f in frs]
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

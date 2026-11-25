"""The event measure of the chain (gap program, priority 2; journal E75).

The user's rule (2026-11-26): the outcome of each rally is measured by its events, not by the winner. A right
winner from wrong events is luck. This module matches the events of the chain to the hand labels and gives, for
each match, the measures with their counts:

  hits        user labels hit_labels_<match>.json (label_hits.html, E70): recall, precision, time error; hitter
              right; shot count right. The chain is built on the window of each labeled clip with the same calls
              as chain.py (shot_chain.build on the flights with a start in the window), so the measure does not
              depend on the rally detection (priority 1 has its own measure).
  bounces     bounce_labels.json (E17d): recall, time error, place error (px and court m); false bounce events at
              the labels "hit" and "none".
  nets        net_labels.json (E16f): net touch recall (label net or cord) and false touches at the labels "no".
  lastShot    the error details of a project in the advanced scoring mode (Igor): the end of the last shot of each
              point (net, out, in) against the end of the last chain shot before the marked end + T_CUT.

Rules:
  - A chain hit is a shot of shot_chain.build with a flight; its time is the shot time (the hit event, else the
    start of its first flight). Match: one to one, the nearest pairs first, within TOL_HIT.
  - Double touch (decision 2026-11-26, hit-labels-howto.md): a run of labeled hits of the same player is one shot
    for the shot count; the hit times keep all touches. A point with only two serves (a double fault) is two shots.
  - Shot count of the chain: all its shots in the clip window (also a shot that has only a net event).
  - m1116 is test only (lab.TEST_ONLY): measure only.

Use: python score_events.py [match ...]        default: all matches with labels
     from bench.py: score_events.score(mid) -> dict; score_events.total([...]) -> dict; score_events.lines(d)
"""
import glob, hashlib, json, os, sys
import numpy as np
import lab
import shot_chain as sc

HERE = os.path.dirname(os.path.abspath(__file__))
HIT_LABELS_DIR = os.environ.get("HIT_LABELS_DIR", HERE)
TOL_HIT, TOL_BOUNCE, TOL_NET = 0.30, 0.10, 0.50       # s
NEAR_HIT = 0.05                                       # s: a bounce event this near to a labeled hit is a false bounce
CLIP_PRE, CLIP_POST = 1.5, 0.5                        # s: the window of a labeled clip (label_clips.py PRE, POST)
MIN_SPEED = 8.0                                       # km/h (chain.PARAMS)
T_CUT = -1.5                                          # s: winner_ball.T_CUT for a marked end
END_OF = {"NET": "net", "LONG": "out", "WIDE": "out"}  # errorKind -> end of the last shot; WINNER, ACE -> in


def work_folder(mid):
    """The folder with the flights of a match: the lab folder, else the app folder (m0913)."""
    d = lab.mdir(mid)
    if glob.glob(os.path.join(d, "fullflights_s0.35*.json")):
        return d
    return os.path.join(os.path.dirname(lab.ROOT.rstrip("/\\")), "app", mid)


def _sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()[:16]


def load(mid, folder=None):
    """The chain inputs of a match: flights with events (the _ev file of the lab, else the file of detect.py), net events."""
    d = folder or work_folder(mid)
    fp = next((os.path.join(d, n) for n in ("fullflights_s0.35_ev.json", "fullflights_s0.35.json") if os.path.exists(os.path.join(d, n))), None)
    if fp is None:
        return None
    full = json.load(open(fp))
    np_ = os.path.join(d, "net_events.json")
    nets = json.load(open(np_))["events"] if os.path.exists(np_) else []
    files = {os.path.basename(fp): _sha(fp)}
    if os.path.exists(np_):
        files["net_events.json"] = _sha(np_)
    return {"folder": d, "full": full, "nets": nets, "t0": np.array([f["t0"] for f in full["flights"]]), "files": files}


def chain_shots(data, a, b):
    """The shots of the chain for the window a..b (s), with the flights and net events of a - 1 s to b + 2.5 s (chain.py)."""
    sel = np.nonzero((data["t0"] >= a - 1.0) & (data["t0"] <= b + 2.5))[0]
    fls = [data["full"]["flights"][i] for i in sel]
    nets = [e for e in data["nets"] if a - 1.0 <= e["t"] <= b + 2.5]
    return sc.build(fls, data["full"].get("events"), nets, MIN_SPEED)


def match(pred, true, tol):
    """One-to-one pairs (i pred, j true) with |pred - true| <= tol, the nearest pairs first."""
    c = sorted((abs(p - t), i, j) for i, p in enumerate(pred) for j, t in enumerate(true) if abs(p - t) <= tol)
    used_i, used_j, out = set(), set(), []
    for d, i, j in c:
        if i not in used_i and j not in used_j:
            used_i.add(i); used_j.add(j); out.append((i, j))
    return out


def shots_of(hits):
    """Shot count and the player of each shot of the labeled hits (double touch rule)."""
    p = [h["player"] for h in hits]
    if len(p) == 2 and p[0] == p[1]:
        return p                                      # the two serves of a double fault
    out = []
    for x in p:
        if not out or out[-1] != x:
            out.append(x)
    return out


def _pct(a, b):
    return None if not b else a / b


def _err(x):
    x = np.abs(np.asarray(x, float))
    return {"med": float(np.median(x)), "p90": float(np.percentile(x, 90)), "n": int(len(x))} if len(x) else None


# ---------------------------------------------------------------- hits, hitter, shot count

def hit_labels(mid):
    p = os.path.join(HIT_LABELS_DIR, f"hit_labels_{mid}.json")
    return json.load(open(p, encoding="utf-8")) if os.path.exists(p) else None


def hits(mid, data, doc):
    n_lab = n_pred = n_match = hitter_ok = cnt_ok = 0
    dt, dt_paused, cnt_diff, rallies, double = [], [], [], [], []
    for r in doc["rallies"]:
        if r.get("skip"):
            continue
        a, b = r["startMs"] / 1000 - CLIP_PRE, r["endMs"] / 1000 + CLIP_POST
        shots = chain_shots(data, a, b)
        sh = [s for s in shots if a <= s["t"] <= b]
        ph = [s for s in sh if s["fl"]]
        lt = [h["t"] for h in r["hits"]]
        m = match([s["t"] for s in ph], lt, TOL_HIT)
        n_lab += len(lt); n_pred += len(ph); n_match += len(m)
        for i, j in m:
            d = ph[i]["t"] - lt[j]
            dt.append(d)
            if r["hits"][j].get("paused"):
                dt_paused.append(d)
            hitter_ok += int(("near" if ph[i]["near"] else "far") == r["hits"][j]["player"])
        lab_shots = shots_of(r["hits"])
        if len(lab_shots) < len(r["hits"]):
            double.append(r["n"])
        cnt_ok += int(len(sh) == len(lab_shots))
        cnt_diff.append(len(sh) - len(lab_shots))
        rallies.append({"n": r["n"], "point": r["point"], "label": "".join(x[0] for x in lab_shots),
                        "chain": "".join(("n" if s["near"] else "f") + ("" if s["fl"] else "*") for s in sh),
                        "matched": len(m), "labeledHits": len(lt), "chainHits": len(ph)})
    k = len(rallies)
    return {"rallies": k, "labeled": n_lab, "chain": n_pred, "matched": n_match, "recall": _pct(n_match, n_lab),
            "precision": _pct(n_match, n_pred), "timeErr": _err(dt), "timeSignedMed": float(np.median(dt)) if dt else None,
            "timeErrPaused": _err(dt_paused), "hitterRight": _pct(hitter_ok, n_match), "hitterN": n_match,
            "shotCountRight": _pct(cnt_ok, k), "shotCountDiff": {str(x): cnt_diff.count(x) for x in sorted(set(cnt_diff))},
            "doubleTouch": double, "perRally": rallies, "raw": {"dt": dt}}


# ---------------------------------------------------------------- bounces

def court_H(folder, t):
    import ball_events
    ws = ball_events.court_windows(folder)
    return np.array(ball_events.window_at(ws, t)["H"], np.float64) if ws else None


def bounces(mid, data, labels):
    import ball_events
    ev = [e for e in (data["full"].get("events") or []) if e["kind"] == "bounce"]
    et = np.array([e["t"] for e in ev]) if ev else np.zeros(0)
    pos = [l for l in labels if l["kind"] == "bounce" and l.get("t") is not None]
    m = match(list(et), [l["t"] for l in pos], TOL_BOUNCE)
    dt, dpx, dm = [], [], []
    for i, j in m:
        e, l = ev[i], pos[j]
        dt.append(e["t"] - l["t"])
        if not l.get("px") or not e.get("px"):
            continue                                  # a label with no pixel (the ball is not seen): time only
        dpx.append(float(np.hypot(e["px"][0] - l["px"][0], e["px"][1] - l["px"][1])))
        H = court_H(data["folder"], l["t"])
        if H is not None and e.get("court") is not None:
            # the label pixel through the same transform as the event (ball_bounce.ground has the ball radius
            # correction; the plain homography of both pixels compares them in the same way)
            g = ball_events.to_court([l["px"], e["px"]], H)
            dm.append(float(np.hypot(*(g[0] - g[1]))))
    false = 0
    neg = [l for l in labels if l["kind"] in ("hit", "none")]
    for l in neg:
        if l["kind"] == "hit" and l.get("t") is not None:
            false += int(np.any(np.abs(et - l["t"]) <= NEAR_HIT))
        elif l.get("t0") is not None:
            false += int(np.any((et >= l["t0"]) & (et <= l["t1"])))
    return {"labeled": len(pos), "matched": len(m), "recall": _pct(len(m), len(pos)), "timeErr": _err(dt),
            "pxErr": _err(dpx), "courtErr": _err(dm), "negLabels": len(neg), "falseAtNeg": false, "raw": {"dt": dt, "dm": dm}}


# ---------------------------------------------------------------- net touches

def nets(mid, data, labels):
    ev = [e for e in data["nets"] if e["kind"] in ("net", "cord") and e["conf"] >= sc.NET_CONF]
    et = np.array([e["t"] for e in ev]) if ev else np.zeros(0)
    pos = [l for l in labels if l["label"] in ("net", "cord")]
    neg = [l for l in labels if l["label"] == "no"]
    m = match(list(et), [l["t"] for l in pos], TOL_NET)
    kind_ok = sum(ev[i]["kind"] == pos[j]["label"] for i, j in m)
    false = sum(int(np.any(np.abs(et - l["t"]) <= TOL_NET)) for l in neg)
    return {"labeled": len(pos), "matched": len(m), "recall": _pct(len(m), len(pos)), "kindRight": _pct(kind_ok, len(m)),
            "negLabels": len(neg), "falseAtNeg": false}


# ---------------------------------------------------------------- end of the last shot (projects with error details)

def end_truth(mid):
    """{point id: "net" / "out" / "in"} from the error details of the project (advanced scoring mode), else None."""
    m = lab.MATCHES.get(mid) or {}
    if not m.get("project"):
        return None
    root = os.path.expandvars(lab.CFG["projectsRoot"]).replace("%APPDATA%", os.environ.get("APPDATA", ""))
    p = os.path.join(root, m["project"], "score.json")
    if not os.path.exists(p):
        return None
    det = json.load(open(p, encoding="utf-8")).get("details") or {}
    out = {}
    for pid, d in det.items():
        d = d or {}
        if d.get("ending") in ("WINNER", "ACE"):
            out[pid] = "in"
        elif d.get("ending") in ("UNFORCED_ERROR", "FORCED_ERROR") and d.get("errorKind") in END_OF:
            out[pid] = END_OF[d["errorKind"]]
    return out or None


def last_shot(mid, data, truth):
    g = json.load(open(os.path.join(lab.mdir(mid), "gt.json"), encoding="utf-8"))
    tab, n = {}, 0
    for p in g["points"]:
        y = truth.get(p["id"])
        if y is None:
            continue
        a, b = p["startMs"] / 1000, p["endMs"] / 1000
        shots = chain_shots(data, a, b)
        li = sc.last_shot(shots, b, T_CUT)
        k = sc.shot_end(shots[li])[0] if li is not None else "no shot"
        k = {"own": "net"}.get(k, k)              # a bounce on the side of the hitter: the ball did not pass the net
        tab.setdefault(y, {}).setdefault(k, 0)
        tab[y][k] += 1
        n += 1
    ok = sum(tab.get(y, {}).get(y, 0) for y in tab)
    seen = sum(v for y in tab for k, v in tab[y].items() if k in ("net", "out", "in"))
    return {"points": n, "right": _pct(ok, n), "seen": seen, "rightOfSeen": _pct(ok, seen), "table": tab}


# ---------------------------------------------------------------- per match, total, report

def score(mid, folder=None):
    """All event measures of a match that has labels. Returns {} if the match has no flights."""
    data = load(mid, folder)
    if data is None:
        return {}
    out = {"match": mid, "files": data["files"], "testOnly": mid in lab.TEST_ONLY}
    doc = hit_labels(mid)
    if doc:
        out["hits"] = hits(mid, data, doc)
    bl = [l for l in json.load(open(os.path.join(HERE, "bounce_labels.json"), encoding="utf-8"))["labels"] if l["vid"] == mid]
    if bl:
        out["bounces"] = bounces(mid, data, bl)
    nl = [l for l in json.load(open(os.path.join(HERE, "net_labels.json"), encoding="utf-8"))["labels"] if l["video"] == mid]
    if nl:
        out["nets"] = nets(mid, data, nl)
    tr = end_truth(mid)
    if tr and os.path.exists(os.path.join(lab.mdir(mid), "gt.json")):
        out["lastShot"] = last_shot(mid, data, tr)
    return out


def total(per):
    """Sums of the counts over matches (a list of score() results)."""
    t = {}
    for sec, keys in (("hits", ("rallies", "labeled", "chain", "matched", "hitterN")), ("bounces", ("labeled", "matched", "negLabels", "falseAtNeg")),
                      ("nets", ("labeled", "matched", "negLabels", "falseAtNeg")), ("lastShot", ("points", "seen"))):
        rows = [p[sec] for p in per if sec in p]
        if not rows:
            continue
        s = {k: sum(r[k] for r in rows) for k in keys}
        if sec == "hits":
            s["recall"], s["precision"] = _pct(s["matched"], s["labeled"]), _pct(s["matched"], s["chain"])
            s["hitterRight"] = _pct(sum(r["hitterRight"] * r["hitterN"] for r in rows if r["hitterRight"] is not None), s["hitterN"])
            s["shotCountRight"] = _pct(sum(r["shotCountRight"] * r["rallies"] for r in rows if r["shotCountRight"] is not None), s["rallies"])
        elif sec == "lastShot":
            s["right"] = _pct(sum(r["right"] * r["points"] for r in rows), s["points"])
        else:
            s["recall"] = _pct(s["matched"], s["labeled"])
        if sec in ("hits", "bounces"):
            s["timeErr"] = _err([x for r in rows for x in r["raw"]["dt"]])
        if sec == "bounces":
            s["courtErr"] = _err([x for r in rows for x in r["raw"]["dm"]])
        s["matches"] = [p["match"] for p in per if sec in p]
        t[sec] = s
    return t


def _f(x):
    return "-" if x is None else f"{100 * x:.1f} %"


def lines(d):
    """Short report lines of one score() or total() result."""
    out = []
    h = d.get("hits")
    if h:
        e = h.get("timeErr")
        out.append(f"hits: {h['rallies']} rallies, labeled {h['labeled']}, chain {h['chain']}, recall {_f(h['recall'])}, precision {_f(h['precision'])}"
                   + (f", time error med {1000 * e['med']:.0f} ms (p90 {1000 * e['p90']:.0f})" if e else "")
                   + f"; hitter right {_f(h['hitterRight'])}; shot count right {_f(h['shotCountRight'])}")
    b = d.get("bounces")
    if b:
        e, c = b.get("timeErr"), b.get("courtErr")
        out.append(f"bounces: labeled {b['labeled']}, recall {_f(b['recall'])}" + (f", time error med {1000 * e['med']:.0f} ms" if e else "")
                   + (f", place error med {c['med']:.2f} m (p90 {c['p90']:.2f})" if c else "") + f"; false at {b['falseAtNeg']} of {b['negLabels']} hit/none labels")
    n = d.get("nets")
    if n:
        out.append(f"net touches: labeled {n['labeled']}, recall {_f(n['recall'])}" + (f", kind right {_f(n['kindRight'])}" if "kindRight" in n else "")
                   + f"; false at {n['falseAtNeg']} of {n['negLabels']} 'no' labels")
    s = d.get("lastShot")
    if s:
        out.append(f"last shot end (net/out/in): {s['points']} points, right {_f(s['right'])}" + (f", right of seen {_f(s['rightOfSeen'])} ({s['seen']})" if "rightOfSeen" in s else ""))
    return out


def main(mids):
    labels = {l["vid"] for l in json.load(open(os.path.join(HERE, "bounce_labels.json"), encoding="utf-8"))["labels"]}
    labels |= {l["video"] for l in json.load(open(os.path.join(HERE, "net_labels.json"), encoding="utf-8"))["labels"]}
    labels |= {os.path.basename(p)[11:-5] for p in glob.glob(os.path.join(HIT_LABELS_DIR, "hit_labels_*.json"))}
    labels |= {m for m in lab.MATCHES if end_truth(m)}
    per = []
    for mid in mids or sorted(labels):
        r = score(mid)
        if not r:
            print(f"{mid}: no flights file")
            continue
        per.append(r)
        print(f"{mid}{' (test only)' if r['testOnly'] else ''}:")
        for ln in lines(r):
            print("  " + ln)
    tot = total(per)
    print("ALL:")
    for ln in lines(tot):
        print("  " + ln)
    return per, tot


if __name__ == "__main__":
    main(sys.argv[1:])

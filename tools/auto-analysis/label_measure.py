"""Measures of the chain on the user's event labels that case_history.py does not have yet (gap program S4, E170).

case_history.py (S1) is the one tool that runs the chain on the case library and scores it on the labels. It makes
the pairs, the point windows, the fault map (label_faults.infer_faults over the fragment) and the hidden fault rows
(bench.hidden_fault_rows), and calls these functions with them. Each rule is in one place. Owner of this module: S4.

  points: [{"point": p, "row": r, "events": the labeled events of the point window (time order), "fault": {round(t, 6):
           True / False / "unsure"}}], one for each matched point (case_history: the window from max(s0 - 25 s, the end
           of the marked point before) to s1 + 1 s)
  borders(points)                                    row start - serve in play, row start - first serve, row end - last event
  shot_count(points)                                 the chain shots (len(row["chain"]["shots"])) - the user's contacts
  fault_clips(crow, ev, fault, hidden)               the fault clips of the chain rows against the fault map
  event_errors(ev, fault, data, shots, lh, m, bev, lb, mb, nev, ln, mn)   time errors, bounce place and in/out
  totals(frs)                                        {name: numbers} over the fragments (raw lists in, JSON numbers out)

Windows: TOL_HIT, TOL_BOUNCE and TOL_NET of score_events.py (the bench windows), through the pairs of case_history.
New rule (E170): the in/out of a matched bounce event from its court point, singles lines (shot_chain SW, BL), margin 0.
For the first bounce after a serve: the service box (0 < |y| <= SL on the receiver half) and, when the labels give the
box, its side (the sign of x; manager, E170). The box is given by the user's bounce of that serve when it is in, else
by the other serve of the same point (a fault and its second serve go to the same box; label_faults._side). Else the
bounce has "no side check" (counted apart).
"""
import numpy as np

import score_events as se
import shot_chain as sc
import label_faults as lf

TOL_HIT, TOL_BOUNCE, TOL_NET = se.TOL_HIT, se.TOL_BOUNCE, se.TOL_NET
END_PRE = 1.0                   # s, the events of the point end: from the point start - 1 s (case_history last_end)


def _r(x):
    return round(float(x), 3)


def _play_serves(pt):
    """(the serve in play, the first serve of the point) of a points item, or (None, None). The serve in play is the
    last serve of the window with fault not True; the first serve is the serve just before it when that one is a fault
    of the same end."""
    sv = [e for e in pt["events"] if e["kind"] == "serve"]
    f = pt["fault"]
    play = [e for e in sv if f.get(round(e["t"], 6)) is not True]
    if not play:
        return None, None
    p = play[-1]
    before = [e for e in sv if e["t"] < p["t"]]
    first = before[-1] if before and f.get(round(before[-1]["t"], 6)) is True and before[-1].get("end") == p.get("end") else p
    return p, first


def borders(points):
    """dPlay = row start - the serve in play, dFirst = row start - the first serve of the point, dEnd = row end - the
    last labeled event of the point (from the point start - END_PRE s). Lists of s, and a detail for each point."""
    out = {"dPlay": [], "dFirst": [], "dEnd": [], "detail": []}
    for pt in points:
        p, r = pt["point"], pt["row"]
        s0 = p["startMs"] / 1000
        play, first = _play_serves(pt)
        last = [e for e in pt["events"] if e["t"] >= s0 - END_PRE]
        d = {"point": p["id"], "rowStart": r["start"], "rowEnd": r["end"]}
        if play is not None:
            d["dPlay"], d["dFirst"] = _r(r["start"] - play["t"]), _r(r["start"] - first["t"])
            out["dPlay"].append(d["dPlay"])
            out["dFirst"].append(d["dFirst"])
        if last:
            d["dEnd"] = _r(r["end"] - last[-1]["t"])
            out["dEnd"].append(d["dEnd"])
        out["detail"].append(d)
    return out


def shot_count(points):
    """The chain shot count of the row (len(row["chain"]["shots"]); manager: not the "shots" field, the good flights)
    against the user's contacts (serves and shots) from the serve in play to the end of the window. diff = chain - user."""
    out = {"n": 0, "ok": 0, "off1": 0, "diff": []}
    for pt in points:
        play, _ = _play_serves(pt)
        ch = pt["row"].get("chain")
        if play is None or not isinstance(ch, dict) or ch.get("shots") is None:
            continue
        n = sum(1 for e in pt["events"] if e["kind"] in ("serve", "shot") and e["t"] >= play["t"])
        d = len(ch["shots"]) - n
        out["n"] += 1
        out["ok"] += int(d == 0)
        out["off1"] += int(abs(d) == 1)
        out["diff"].append(d)
    return out


def _contact(f):
    shots = (f.get("chain") or {}).get("shots") or []
    return shots[0]["t"] if shots else f["t"]


def fault_clips(crow, ev, fault, hidden):
    """The fault clips the user sees: the "fault" record of each chain row of the window (crow). Its serve contact
    (fault.chain.shots[0].t, else fault.t) is paired with the labeled serves within TOL_HIT (one to one). right = a
    labeled first-serve fault (fault map True); wrong = a serve with no fault; unsure; noServe = no labeled serve
    (false). found = the labeled first-serve faults with a fault clip. hidden: the rows of the window that the bench
    hides as first-serve faults (bench.hidden_fault_rows); hiddenWithFault = those with a labeled first-serve fault
    inside the row (from its start - TOL_HIT)."""
    serves = [(e["t"], fault.get(round(e["t"], 6))) for e in ev if e["kind"] == "serve"]
    clips = [(r, _contact(r["fault"])) for r in crow if r.get("fault")]
    m = se.match([t for _, t in clips], [t for t, _ in serves], TOL_HIT)
    got = {i: serves[j][1] for i, j in m}
    out = {"clips": len(clips), "right": 0, "wrong": 0, "unsure": 0, "noServe": 0,
           "labeledFaults": sum(f is True for _, f in serves), "found": sum(1 for _, j in m if serves[j][1] is True),
           "hiddenRows": len(hidden), "hiddenWithFault": 0, "detail": []}
    for i, (r, t) in enumerate(clips):
        f = got.get(i, "none")
        k = "right" if f is True else "wrong" if f is False else "unsure" if f == "unsure" else "noServe"
        out[k] += 1
        out["detail"].append({"t": _r(t), "row": [r["start"], r["end"]], "result": k})
    out["hiddenWithFault"] = sum(any(f is True and x["start"] - TOL_HIT <= t <= x["end"] for t, f in serves) for x in hidden)
    return out


def _serve_of_bounce(E, k):
    """The index in E of the serve whose first bounce is E[k] (a net cord between them is allowed), or None."""
    j = k - 1
    if j >= 0 and E[j]["kind"] == "net" and E[j].get("net") == "cord":
        j -= 1
    return j if j >= 0 and E[j]["kind"] == "serve" else None


def _box_side(E, i, fr):
    """The side (+1 / -1) of the service box of the serve E[i] from the labels, or None (no side check)."""
    _, b = lf._after_serve(E, i)
    if b and b["kind"] == "bounce" and not b.get("out") and b.get("court") and abs(b["court"][0]) >= lf.BOX_MIN_X:
        return 1 if b["court"][0] > 0 else -1
    S = [j for j, e in enumerate(E) if e["kind"] == "serve"]
    a = S.index(i)
    if fr.get(round(E[i]["t"], 6)) is True and a + 1 < len(S) and E[S[a + 1]].get("end") == E[i].get("end"):
        return lf._side(E, S[a + 1])                       # the second serve goes to the same box
    if a > 0 and fr.get(round(E[S[a - 1]]["t"], 6)) is True and E[S[a - 1]].get("end") == E[i].get("end"):
        return lf._side(E, S[a - 1])                       # this is the second serve: the box of the fault before
    return None


def chain_in(court, serve_end=None, side=None):
    """The in/out of a chain bounce from its court point (x across, y along, far end > 0). serve_end: the end of the
    server when the bounce is the first bounce of a serve (then the service box; side = the box side, or None)."""
    x, y = float(court[0]), float(court[1])
    if serve_end is None:
        return abs(x) <= sc.SW and abs(y) <= sc.BL
    recv = -1 if serve_end == "far" else 1                 # a far serve lands on the near half (y < 0)
    ok = abs(x) <= sc.SW and 0 < y * recv <= sc.SL
    if ok and side is not None:
        ok = (1 if x > 0 else -1) == side
    return ok


def event_errors(ev, fault, data, shots, lh, m, bev, lb, mb, nev, ln, mn):
    """The errors of the pairs of case_history (m: (chain shot, labeled serve or shot), mb: (bounce event, labeled
    bounce), mn: (net event, labeled net)). ev: the labeled events of the fragment (time order; lb holds the same
    dicts). fault: the fault map. data: score_events.load (data["folder"] for court_H). Time errors are chain - label."""
    idx = {id(e): k for k, e in enumerate(ev)}
    out = {"hitDt": [_r(shots[i]["t"] - lh[j]["t"]) for i, j in m],
           "bounceDt": [_r(bev[i]["t"] - lb[j]["t"]) for i, j in mb],
           "netDt": [_r(nev[i]["t"] - ln[j]["t"]) for i, j in mn],
           "placeErr": [], "inOutN": 0, "inOutOk": 0, "serveBounces": 0, "sideChecked": 0, "inOutWrong": []}
    folder = (data or {}).get("folder")
    for i, j in mb:
        e, l = bev[i], lb[j]
        if folder is not None and l.get("px") and e.get("px"):
            H = se.court_H(folder, l["t"])
            if H is not None:
                import ball_events
                g = ball_events.to_court([l["px"], e["px"]], H)
                out["placeErr"].append(_r(np.hypot(*(g[0] - g[1]))))
        if e.get("court") is None:
            continue
        k = idx.get(id(l))
        si = _serve_of_bounce(ev, k) if k is not None else None
        side = None
        if si is not None:
            out["serveBounces"] += 1
            side = _box_side(ev, si, fault)
            out["sideChecked"] += int(side is not None)
        got = chain_in(e["court"], ev[si].get("end") if si is not None else None, side)
        out["inOutN"] += 1
        ok = got == (not l.get("out"))
        out["inOutOk"] += int(ok)
        if not ok:
            out["inOutWrong"].append({"t": _r(l["t"]), "label": "out" if l.get("out") else "in", "serve": si is not None,
                                      "sideCheck": side is not None, "court": [_r(c) for c in e["court"]]})
    return out


def _st(x, signed=True):
    """JSON numbers of a list of values: n, the signed median, the median and the p90 of |x| (None when empty)."""
    if not x:
        return {"n": 0, "med": None, "absMed": None, "p90": None}
    a = np.asarray(x, float)
    return {"n": int(len(a)), "med": round(float(np.median(a)), 3) if signed else None,
            "absMed": round(float(np.median(np.abs(a))), 3), "p90": round(float(np.percentile(np.abs(a), 90)), 3)}


def totals(frs):
    """{name: numbers} over the fragments. frs: the fragment dicts of case_history with the keys "borders", "shotCount",
    "faultClips", "errors" (a missing key is skipped). Only JSON numbers (for the entry JSON)."""
    L = {k: [] for k in ("dPlay", "dFirst", "dEnd", "diff", "hitDt", "bounceDt", "netDt", "placeErr")}
    C = {k: 0 for k in ("n", "ok", "off1", "clips", "right", "wrong", "unsure", "noServe", "labeledFaults", "found",
                        "hiddenRows", "hiddenWithFault", "inOutN", "inOutOk", "serveBounces", "sideChecked")}
    for f in frs:
        for part in ("borders", "shotCount", "faultClips", "errors"):
            for k, v in (f.get(part) or {}).items():
                if k in L:
                    L[k] += v
                elif k in C:
                    C[k] += v
    return {"rallyStartPlay": _st(L["dPlay"]), "rallyStartFirst": _st(L["dFirst"]), "rallyEnd": _st(L["dEnd"]),
            "faultClips": {k: C[k] for k in ("clips", "right", "wrong", "unsure", "noServe")},
            "labeledFaults": C["labeledFaults"], "labeledFaultsWithClip": C["found"],
            "hiddenRows": C["hiddenRows"], "hiddenRowsWithFault": C["hiddenWithFault"],
            "hitTimeErr": _st(L["hitDt"]), "bounceTimeErr": _st(L["bounceDt"]), "netTimeErr": _st(L["netDt"]),
            "bouncePlaceErr": _st(L["placeErr"], signed=False),
            "inOut": {"n": C["inOutN"], "ok": C["inOutOk"], "serveBounces": C["serveBounces"],
                      "sideCheck": C["sideChecked"], "noSideCheck": C["serveBounces"] - C["sideChecked"]},
            "shotCount": {"n": C["n"], "ok": C["ok"], "off1": C["off1"],
                          "meanDiff": round(float(np.mean(L["diff"])), 3) if L["diff"] else None}}

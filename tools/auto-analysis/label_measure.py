"""Measures of the chain on the user's event labels that case_history.py does not have yet (gap program S4, E170).

case_history.py (S1) is the one tool that runs the chain on the case library and scores it on the labels. It calls
these functions in fragment() and total() and shows their rows in its report. Owner of this module: S4.

  borders(pts, crow, pairs, ev)          the rally row start and end against the user's serve contact and last event
  fault_clips(mid, crow, ev)             the fault clips of the chain rows against label_faults.infer_faults
  event_errors(...)                      time errors, bounce place error and in/out, shot count
  total(frs)                             the sums over the fragments (the "measure" dicts of fragment())
  report_rows(t)                         the rows of the totals table of report.md

Windows: TOL_HIT, TOL_BOUNCE and TOL_NET of score_events.py (the bench windows), and the point window of case_history
(the events from the point start - 25 s to the point end + 1 s). One new rule (E170): the in/out of a matched bounce
event from its court point with the singles lines (shot_chain SW, BL; margin 0). For the first bounce after a serve:
the service box (|y| <= SL on the receiver half) and, when the labels give the box, its side (the sign of x). The box
is given by the user's bounce of that serve when it is in, else by the other serve of the same point (the fault and
its second serve go to the same box; label_faults._side). Else "no side check".
"""
import numpy as np

import score_events as se
import shot_chain as sc
import label_faults as lf

TOL_HIT, TOL_BOUNCE, TOL_NET = se.TOL_HIT, se.TOL_BOUNCE, se.TOL_NET
PRE, POST = 25.0, 1.0           # s, the point window of case_history (the fault serve before the point)
END_PRE = 1.0                   # s, the events of the point end: from the point start - 1 s (case_history last_end)


def _r(x):
    return round(float(x), 3)


def _play_serves(ev, fr, s0, s1):
    """(the serve in play, the first serve of the point) of the marked point s0..s1, or (None, None). fr: infer_faults
    results by time. The serve in play is the last serve in the point window with fault not True; the first serve is
    the serve just before it when that one is a fault of the same end."""
    sv = [e for e in ev if e["kind"] == "serve" and s0 - PRE <= e["t"] <= s1 + POST]
    play = [e for e in sv if fr.get(round(e["t"], 6)) is not True]
    if not play:
        return None, None
    p = play[-1]
    before = [e for e in sv if e["t"] < p["t"]]
    first = before[-1] if before and fr.get(round(before[-1]["t"], 6)) is True and before[-1].get("end") == p.get("end") else p
    return p, first


def _fr(ev):
    return {round(x["t"], 6): x["fault"] for x in lf.infer_faults(ev)}


def borders(pts, crow, pairs, ev):
    """For each matched point (pairs: (point index, row index) of lab.match_points): dPlay = row start - the serve in
    play, dFirst = row start - the first serve of the point, dEnd = row end - the last labeled event of the point."""
    fr = _fr(ev)
    out = {"dPlay": [], "dFirst": [], "dEnd": [], "detail": []}
    for gi, j in pairs:
        p, r = pts[gi], crow[j]
        s0, s1 = p["startMs"] / 1000, p["endMs"] / 1000
        play, first = _play_serves(ev, fr, s0, s1)
        last = [e for e in ev if s0 - END_PRE <= e["t"] <= s1 + POST]
        d = {"point": p["id"], "rowStart": r["start"], "rowEnd": r["end"]}
        if play is not None:
            d["dPlay"], d["dFirst"] = _r(r["start"] - play["t"]), _r(r["start"] - first["t"])
            out["dPlay"].append(d["dPlay"]); out["dFirst"].append(d["dFirst"])
        if last:
            d["dEnd"] = _r(r["end"] - last[-1]["t"])
            out["dEnd"].append(d["dEnd"])
        out["detail"].append(d)
    return out


def _contact(f):
    ch = f.get("chain") or {}
    shots = ch.get("shots") or []
    return shots[0]["t"] if shots else f["t"]


def fault_clips(mid, crow, ev, window):
    """The fault clips the user sees: the "fault" record of each chain row in the window. Its serve contact is paired
    with the labeled serves within TOL_HIT (one to one). Right = a labeled first-serve fault (infer_faults True); wrong
    = a serve with no fault; unsure; no labeled serve = false. found = the labeled first-serve faults with a fault clip.
    hidden = the rows of the bench rule "fault, not checked" (bench.no_none) in the window, with and without a labeled
    first-serve fault inside the row."""
    a, b = window
    res = lf.infer_faults(ev)
    serves = [(x["t"], x["fault"]) for x in res]
    clips = [(r, _contact(r["fault"])) for r in crow if r.get("fault")]
    clips = [(r, t) for r, t in clips if a - TOL_HIT <= t <= b + TOL_HIT]
    m = se.match([t for _, t in clips], [t for t, _ in serves], TOL_HIT)
    got = {i: serves[j][1] for i, j in m}
    out = {"clips": len(clips), "right": 0, "wrong": 0, "unsure": 0, "noServe": 0,
           "labeledFaults": sum(f is True for _, f in serves), "found": 0, "detail": []}
    for i, (r, t) in enumerate(clips):
        f = got.get(i, "none")
        k = "right" if f is True else "wrong" if f is False else "unsure" if f == "unsure" else "noServe"
        out[k] += 1
        out["detail"].append({"t": _r(t), "row": [r["start"], r["end"]], "result": k})
    out["found"] = sum(1 for i, j in m if serves[j][1] is True)
    out["hiddenRows"], out["hiddenWithFault"] = 0, 0
    try:
        import faults_cases
        for x in faults_cases.fault_rows(mid, crow):
            if x["end"] <= a or x["start"] >= b:
                continue
            out["hiddenRows"] += 1
            out["hiddenWithFault"] += int(any(f is True and x["start"] - TOL_HIT <= t <= x["end"] for t, f in serves))
    except Exception as e:                                 # a match with no lab folder (no gt.json): no hidden rows
        out["hiddenError"] = str(e)
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
    f = fr.get(round(E[i]["t"], 6))
    if f is True and a + 1 < len(S) and E[S[a + 1]].get("end") == E[i].get("end"):
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


def event_errors(ev, shots, lh, m_hits, bev, lb, m_b, nev, ln, m_n, pts, crow, pairs, folder=None):
    """The errors of the matched pairs that case_history makes (m_hits: (shot, label hit), m_b: (bounce event, label
    bounce), m_n: (net event, label net)). folder: the lab folder of the match (court_H) for the place error."""
    E = sorted(ev, key=lambda e: e["t"])
    fr = _fr(E)
    idx = {id(e): k for k, e in enumerate(E)}
    out = {"hitDt": [_r(shots[i]["t"] - lh[j]["t"]) for i, j in m_hits],
           "bounceDt": [_r(bev[i]["t"] - lb[j]["t"]) for i, j in m_b],
           "netDt": [_r(nev[i]["t"] - ln[j]["t"]) for i, j in m_n],
           "placeErr": [], "inOutN": 0, "inOutOk": 0, "serveBounces": 0, "sideChecked": 0, "inOutWrong": [],
           "shotN": 0, "shotOk": 0, "shotOff1": 0, "shotDiff": []}
    for i, j in m_b:
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
        si = _serve_of_bounce(E, k) if k is not None else None
        side = None
        if si is not None:
            out["serveBounces"] += 1
            side = _box_side(E, si, fr)
            out["sideChecked"] += int(side is not None)
        got = chain_in(e["court"], E[si].get("end") if si is not None else None, side)
        out["inOutN"] += 1
        ok = got == (not l.get("out"))
        out["inOutOk"] += int(ok)
        if not ok:
            out["inOutWrong"].append({"t": _r(l["t"]), "label": "out" if l.get("out") else "in", "serve": si is not None,
                                      "court": [_r(c) for c in e["court"]]})
    for gi, j in pairs:
        p, r = pts[gi], crow[j]
        s0, s1 = p["startMs"] / 1000, p["endMs"] / 1000
        play, _ = _play_serves(E, fr, s0, s1)
        if play is None or r.get("shots") is None:
            continue
        n = sum(1 for e in E if e["kind"] in ("serve", "shot") and play["t"] <= e["t"] <= s1 + POST)
        d = int(r["shots"]) - n
        out["shotN"] += 1; out["shotOk"] += int(d == 0); out["shotOff1"] += int(abs(d) == 1); out["shotDiff"].append(d)
    return out


def total(frs):
    """Sums over the fragment results: frs = [{"borders": .., "faultClips": .., "errors": ..}, ...] (missing keys are
    skipped)."""
    t = {"dPlay": [], "dFirst": [], "dEnd": [], "hitDt": [], "bounceDt": [], "netDt": [], "placeErr": [], "shotDiff": []}
    cnt = ("clips", "right", "wrong", "unsure", "noServe", "labeledFaults", "found", "hiddenRows", "hiddenWithFault",
           "inOutN", "inOutOk", "serveBounces", "sideChecked", "shotN", "shotOk", "shotOff1")
    for k in cnt:
        t[k] = 0
    for f in frs:
        for part in ("borders", "faultClips", "errors"):
            d = f.get(part) or {}
            for k, v in d.items():
                if k in t and isinstance(v, list) and k != "detail":
                    t[k] += v
                elif k in cnt:
                    t[k] += v
    return t


def _stat(x, abs_=True):
    if not x:
        return "-"
    a = np.asarray(x, float)
    return (f"{np.median(a):+.2f} / {np.median(np.abs(a)):.2f} / {np.percentile(np.abs(a), 90):.2f} s (n {len(a)})" if not abs_
            else f"{np.median(np.abs(a)):.3f} / {np.percentile(np.abs(a), 90):.3f} s (n {len(a)})")


def report_rows(t):
    """[(name, text)] for the totals table of the case_history report (counts with each rate)."""
    def kn(k, n):
        return f"{k}/{n} ({100 * k / n:.0f} %)" if n else "-"
    pe = t["placeErr"]
    return [
        ("Rally start - serve in play (med signed / |med| / p90)", _stat(t["dPlay"], False)),
        ("Rally start - first serve of the point", _stat(t["dFirst"], False)),
        ("Rally end - last labeled event", _stat(t["dEnd"], False)),
        ("Fault clips right (a labeled first-serve fault)", kn(t["right"], t["clips"]) +
         f", wrong {t['wrong']}, unsure {t['unsure']}, no labeled serve {t['noServe']}"),
        ("Labeled first-serve faults with a fault clip", kn(t["found"], t["labeledFaults"])),
        ("Hidden fault rows with a labeled fault serve", kn(t["hiddenWithFault"], t["hiddenRows"])),
        ("Hit time error (|med| / p90)", _stat(t["hitDt"])),
        ("Bounce time error (|med| / p90)", _stat(t["bounceDt"])),
        ("Net time error (|med| / p90)", _stat(t["netDt"])),
        ("Bounce place error (med / p90)", f"{np.median(pe):.2f} / {np.percentile(pe, 90):.2f} m (n {len(pe)})" if pe else "-"),
        ("Bounce in/out right (matched)", kn(t["inOutOk"], t["inOutN"]) +
         f"; serve bounces {t['serveBounces']}, side check {t['sideChecked']}, no side check {t['serveBounces'] - t['sideChecked']}"),
        ("Shot count right (matched points)", kn(t["shotOk"], t["shotN"]) + f", off by 1: {t['shotOff1']}" +
         (f", mean diff {np.mean(t['shotDiff']):+.2f}" if t["shotDiff"] else "")),
    ]

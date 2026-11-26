"""The first-serve fault flag of the user's event labels, inferred from the marks (gap program S4, E159).

The user decision (gap-program.md, 2026-11-26): the user no longer presses X. The scripts infer the flag. This is the
one place of the rule; S1 imports infer_faults (history, bench).

Rule, for each serve (events sorted by t):
  1. A serve with the X mark ("fault": true) keeps it.
  2. The serve is a candidate only if its next event is an out bounce or a net stop (a net cord, then an out bounce or a
     net stop, also counts). A net cord, then a bounce in = a let candidate: unsure. Any other next event: no fault.
  3. The next serve must be of the same end. The other end: no fault (a new game). No later serve: unsure.
  4. The next serve is the second serve (then the candidate is a first-serve fault) or the first serve of a new point:
     a. The box: when both serves have a bounce as the next event (after a cord too) with a court place, compare the
        side (the sign of x). An out bounce inside a service box of the receiver landed in the wrong box: its side is
        the other side. A second serve goes to the same box. The other box = a new point (no fault). The same box = a
        fault, but with a gap of NEW_MIN s or more it is unsure: after a change of ends, the first point of a new game
        comes from the same end and can go to the same box.
     b. Else the time gap from the serve to the next serve: SECOND_MAX s or less = a fault; NEW_MIN s or more = no
        fault; between = unsure.
     The box and the gap must agree: the other box with a gap of SECOND_MAX s or less is unsure.
  5. A second serve is not a first-serve fault: when the serve before (same end) is a fault by this rule and this serve
     is its next serve, the result is False (an out or a net stop is a double fault). A let candidate stays unsure (the
     user list), and a result True becomes unsure (three serves in a row: a let replay?).

The limits are measured on the user's labels s001 to s008 (E159), not guessed:
  serve to second serve after an X: 5.45 to 12.60 s (8 serves);
  serve to the next point after a second-serve fault (net stop or out): 14.63 to 22.70 s (5 serves).
  (serve to the next point after a rally: 16.61 to 29.93 s, 6 serves; not used for the limits.)
"""
import json

SECOND_MAX = 12.6       # s, the longest serve-to-second-serve gap of the labels (E159)
NEW_MIN = 14.6          # s, the shortest serve-to-next-point gap after a second-serve fault (E159)
BOX_MIN_X = 0.3         # m, a bounce nearer to the center line than this does not tell the side (label px accuracy)
SW, SL = 4.115, 6.40    # m, singles half width and service line (court_fit.py)


def _after_serve(E, i):
    """The events after the serve E[i] up to its first bounce or net stop: (net cord seen, the deciding event or None)."""
    cord = False
    for e in E[i + 1:i + 3]:
        if e["kind"] == "net" and e.get("net") == "cord" and not cord:
            cord = True
            continue
        return cord, e
    return cord, None


def _side(E, i):
    """The service box side (+1 / -1) of the serve E[i] from its bounce, or None."""
    _, b = _after_serve(E, i)
    if not b or b["kind"] != "bounce" or not b.get("court"):
        return None
    x, y = b["court"][0], b["court"][1]
    if abs(x) < BOX_MIN_X:
        return None
    s = 1 if x > 0 else -1
    if b.get("out") and abs(x) <= SW and abs(y) <= SL:
        s = -s                                   # out, but inside a service box: the wrong box
    return s


def _fmt(t):
    m = int(t // 60)
    return f"{m}:{t - 60 * m:06.3f}"


def infer_faults(events, use_x=True):
    """events: the "events" of a <fragment>.events.json (source time t). Returns one dict for each serve, in time order:
    {row (1-based place in the time-sorted event list, as the page list), t, time (m:ss.fff of t, as the page), end,
    x (the X mark), fault (True, False or "unsure"), reason, gap (s to the next serve of the same end, or None)}.
    use_x=False ignores the X marks (for the test of the rule)."""
    E = sorted(events, key=lambda e: e["t"])
    S = [i for i, e in enumerate(E) if e["kind"] == "serve"]
    out = []
    for a, i in enumerate(S):
        _one(E, S, a, i, out, use_x)
        p = out[-2] if len(out) > 1 else None
        r = out[-1]
        if p and p["fault"] is True and p["end"] == r["end"] and not r["x"]:
            if r["fault"] is True:
                r.update(fault="unsure", reason=r["reason"] + "; but the serve before is a fault too (a let replay?)")
            elif r["fault"] is False or "no later serve" in r["reason"]:
                r.update(fault=False, reason="the second serve (the serve before is a first-serve fault)" +
                         ("; " + r["reason"] if r["reason"].startswith(("out", "net")) else ""))
    return out


def _one(E, S, a, i, out, use_x):
    """The result of the serve E[i] (S[a] = i), appended to out; steps 1 to 4 of the rule."""
    s = E[i]
    r = {"row": i + 1, "t": s["t"], "time": _fmt(s["t"]), "end": s.get("end"), "x": bool(s.get("fault")), "gap": None}
    out.append(r)
    if use_x and s.get("fault"):
        r.update(fault=True, reason="X mark")
        return
    cord, n = _after_serve(E, i)
    if n is None:
        r.update(fault="unsure" if cord else False, reason="net cord, then no mark" if cord else "no mark after the serve")
        return
    out_or_stop = (n["kind"] == "bounce" and n.get("out")) or (n["kind"] == "net" and n.get("net") == "stop")
    if cord and n["kind"] == "bounce" and not n.get("out"):
        r.update(fault="unsure", reason="net cord, then the ball lands in: a let?")
        return
    if cord and not out_or_stop:
        r.update(fault="unsure", reason="net cord, then no bounce and no net stop")
        return
    if not out_or_stop:
        r.update(fault=False, reason="next event: " + ("bounce in" if n["kind"] == "bounce" else n["kind"] + (" " + n["net"] if n["kind"] == "net" else "")))
        return
    what = ("net cord, then " if cord else "") + ("out bounce" if n["kind"] == "bounce" else "net stop")
    if a + 1 >= len(S):
        r.update(fault="unsure", reason=f"{what}; no later serve in the labels")
        return
    j = S[a + 1]
    if E[j].get("end") != s.get("end"):
        r.update(fault=False, reason=f"{what}; the next serve is from the other end (a new game)")
        return
    gap = E[j]["t"] - s["t"]
    r["gap"] = round(gap, 3)
    s1, s2 = _side(E, i), _side(E, j)
    if s1 is not None and s2 is not None:
        if s1 != s2:
            if gap <= SECOND_MAX:
                r.update(fault="unsure", reason=f"{what}; the next serve goes to the other box, but the gap is only {gap:.1f} s")
            else:
                r.update(fault=False, reason=f"{what}; the next serve goes to the other box (a new point), gap {gap:.1f} s")
        elif gap >= NEW_MIN:
            r.update(fault="unsure", reason=f"{what}; the same box, but the gap is {gap:.1f} s (a new game after a change of ends?)")
        else:
            r.update(fault=True, reason=f"{what}; the next serve goes to the same box (the second serve), gap {gap:.1f} s")
        return
    if gap <= SECOND_MAX:
        r.update(fault=True, reason=f"{what}; no box; the next serve {gap:.1f} s later (second serve: {SECOND_MAX} s or less)")
    elif gap >= NEW_MIN:
        r.update(fault=False, reason=f"{what}; no box; the next serve {gap:.1f} s later (a new point: {NEW_MIN} s or more)")
    else:
        r.update(fault="unsure", reason=f"{what}; no box; the gap {gap:.1f} s is between {SECOND_MAX} and {NEW_MIN} s")


if __name__ == "__main__":
    import sys
    for p in sys.argv[1:]:
        for r in infer_faults(json.load(open(p, encoding="utf-8"))["events"]):
            print(p, r)

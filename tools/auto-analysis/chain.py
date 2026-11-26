"""The detection chain: from the features of a video to the rally rows of the result. detect.py (the app) and
pipeline.py (the lab benchmark) both call it, so the lab measures the chain that the app runs
(chain-inventory.md, G1). The chain does not read marks. score.py compares its rows with the marks.

Steps (E21 and later):
  1. segments(): the rally GRU probabilities -> rally segments, and short points from the ball serve events (E21b);
  2. winners(): for each rally the winner evidence (shot chain E28, old ball rule E16), the server end (pose vote
     E12, ball serve event E9), the end change (shirt color flip, E11), the first-serve fault candidate (E10);
     then the score decoder (match_decode.py) over all rallies, from 0-0 with the server not known.
The rows have the format of the rallies in result.json (detect.py).
"""
import json, os
import numpy as np
import cv2
import lab
import winner_ball as wb
import winner_sweep as ws
import match_decode as md
import shot_chain as sc
import rally_end
from serve_ball import serve_events
from server_end import near_far, wrist_up
from ball_events import court_H
from near_identity import torso_color, flips

# The parameters of the old ball rule (E16c) for a video with no marks: the value of 3 of the 4 lab matches.
PARAMS = {"margin": -0.15, "net_fwd": 2.0, "net_back": 4.0, "min_speed": 8}


def p_null(prob, shots):
    """Probability that a detected rally is not a point, from the mean GRU probability and the
    count of tracked shots (measured on the 4 singles matches, E21)."""
    base = 0.5 if prob < 0.6 else 0.33 if prob < 0.75 else 0.15 if prob < 0.85 else 0.05 if prob < 0.93 else 0.01
    return min(0.7, base * (1.6 if shots < 2 else 1.0 if shots < 4 else 0.6))


def p_miss(gap):
    """Probability of a missed point in the gap (s) before a detected rally (E21)."""
    return 0.02 if gap < 15 else 0.05 if gap < 25 else 0.2 if gap < 40 else 0.4

MIN_GAP = 2.0
SERVICE_LINE = 6.40                      # the distance of the service line from the net, m
# E122 (found on Igor, rule of tennis; S4 item 8): a detected rally that is a first-serve fault. After a fault the
# same player serves again from the same side of the court; after a point the server changes the side. A rally with
# FAULT_SHOTS chain shots or less, then the next rally from the same server end and the same side (x of the serve
# flight) not later than FAULT_NEXT s after it, is the first serve of the next rally: it is not a rally.
FAULT_SIDE = True
FAULT_SHOTS, FAULT_NEXT, SIDE_X = 2, 25.0, 0.3          # shots; s (the time between points of the rules); m (x near 0: side not known)
# item 10: when the serve flight does not give the side, the court x of the feet of the server (pose, the H of the
# court window at the rally start: ball_events.court_H; one court.json H is off when the camera moves) gives it.
# The rule of tennis: the server serves from the other side after a point, from the same side after a fault.
POSE_SIDE = os.environ.get("CHAIN_POSE_SIDE", "1") != "0"
FAULT_BEFORE, FAULT_AFTER = 1.0, 2.5    # the window of the shots of a fault candidate around its serve event (the fault clip of the app), s
SHORT_WAIT, P_NULL_SHORT = 12.0, 0.5
T_CUT_E2E = -2.5      # time cut of the winner rule for a detected end (E21b): the end is less exact than a mark (-1.5 there)
ID_ABS = 0.97         # E22b: probability that the color identity of a rally is right (None = use the local flips, E21)
CHAIN = os.environ.get("CHAIN", "1") == "1"      # E28, E29: the winner evidence comes from the shot chain (shot_chain.py) when the flights file has "events"; CHAIN=0 gives the old rule
FPS_ADAPT = os.environ.get("FPS_ADAPT", "1") == "1"   # E28: the sample counts of the winner rule follow the frame rate of the ball track (winner_ball.set_fps)
END_MODEL = os.environ.get("END_MODEL", "1") == "1"   # E60: the end for the winner evidence comes from the rally end detector (rally_end.py); END_MODEL=0 gives the GRU end
# The shirt colors of each rally (the image reads are the slow part). "_t2": the pictures of the true seconds (G2);
# the colors of color_cache.json came from the picture row of the second number. An absolute path is also valid.
COLOR_CACHE = "color_cache_t2.json"
# The net events file of a match, if it is not <match folder>/net_events.json. bench.py (S1, E85) puts here the net
# events that the code under test makes from the frozen lab inputs, as detect.py makes them.
NET_FILES = {}
# G13: the faults that the chain survives with a fallback. detect.py puts them in the warnings of result.json.
# Key: the kind of fault; value: [count, the first case as a text].
FAULTS = {}


def note_fault(kind, t, e):
    """Records a fault of the chain at video time t (s) with the exception e."""
    f = FAULTS.setdefault(kind, [0, f"{t:.1f} s: {type(e).__name__}: {e}"])
    f[0] += 1


def fault_texts():
    """The warning texts of the recorded faults."""
    what = {"shot chain": "the old ball rule gave the winner evidence",
            "fault chain": "the fault candidate has no shots"}
    return [f"The {k} failed on {c} {'rally' if c == 1 else 'rallies'} (first at {first}); {what.get(k, 'a fallback was used')}."
            for k, (c, first) in FAULTS.items()]


def rallies(prob, serves, hi=0.5, lo=0.35, short=True, t0=0.0, t1=None):
    """Rally segments (start, end, mean probability) from the GRU probabilities, inside t0 to t1 (s). A test (E21)
    moved the start to a ball serve event near it: the start error got larger (0.42 -> 0.6 s), so the start stays
    as it is. E21b: the GRU misses short points (ace, serve winner, double fault: 4 to 7 s in the marks).
    A ball serve event outside the segments with no other serve event and no segment start in
    the SHORT_WAIT s after it is added as a short point (time - 0.5 s to time + 4.5 s), with the probability -1.
    The decoder gets P_NULL_SHORT for it (about half of these are real points)."""
    t1 = len(prob) if t1 is None else t1
    out = []
    for a, b in lab.segment(prob, smooth=1, hi=hi, lo=lo, min_len=2.0):
        if a >= t0 and b <= t1:
            out.append((float(a), float(b), float(prob[int(a):int(b) + 1].mean())))
    if SHORT_WAIT is None or not short:
        return out
    ts = [e[0] for e in serves]
    add = []
    for t in ts:
        if any(a - 3 <= t <= b + 2 for a, b, _ in out):
            continue
        nxt = min([x for x in ts if x > t + 0.5] + [a for a, _, _ in out if a > t] + [1e18])
        if nxt - t >= SHORT_WAIT:
            if add and t - add[-1] < 6:
                add[-1] = t
            else:
                add.append(t)
    for t in add:
        out.append((float(t - 0.5), float(t + 4.5), -1.0))           # -1: a short point from a serve event
    return sorted(out)


def track_fps(flights):
    """Samples in each second of the ball track (30 or 60 for the known videos). A flight with no missing
    sample has exactly this rate; flights with missing samples have a lower rate. So: the 90 % point."""
    r = [(f["n"] - 1) / (f["t1"] - f["t0"]) for f in flights if f["n"] >= 8 and f["t1"] > f["t0"]]
    return float(round(np.percentile(r, 90))) if r else 60.0


def a_end_t(q, t_end):
    """The video time of the start of the last shot (q["t_rel"] is relative to the end of the rally)."""
    return float(t_end + q["t_rel"])


def evidence(mid, seg, full, serves, p=PARAMS, prob=None):
    """The evidence of each rally of seg (start, end, prob). p: the parameters of the old ball rule. prob: the GRU
    probability of each second, for the rally end detector (None: the evidence uses the GRU end)."""
    out = lab.mdir(mid)
    n = lab.n_seconds(mid)
    pose = lab.pose_by_second(out, n)                # the poses of the picture of each second (G2)
    kf = lab.kf_of_second(out, n)
    fl_t0 = np.array([f["t0"] for f in full["flights"]])
    kk_t = np.array([k[0] for k in full["kinks"]])
    rows, feats, prev_end = [], [], -1e9
    cp = os.path.join(out, COLOR_CACHE)
    cache = json.load(open(cp)) if os.path.exists(cp) else {}
    dirty = False
    if FPS_ADAPT:
        wb.set_fps(track_fps(full["flights"]))
    np_ = NET_FILES.get(mid) or os.path.join(out, "net_events.json")
    nets = json.load(open(np_))["events"] if os.path.exists(np_) else []
    hits = rally_end.load_hits(out)
    gru = prob
    for (a, b, prob) in seg:
        # E60: the end of the winner evidence (the window of the flights and the time cut). The row keeps the GRU end b.
        t_end = rally_end.end(mid, a, b, full, nets, gru, hits, T_CUT_E2E) if END_MODEL else b
        fls = [full["flights"][i] for i in np.nonzero((fl_t0 >= a - 1.0) & (fl_t0 <= t_end + 2.5))[0]]
        kks = [full["kinks"][i] for i in np.nonzero((kk_t >= a - 1.0) & (kk_t <= t_end + 2.5))[0]]
        pt = {"start": a, "end": t_end, "flights": fls, "kinks": kks, "coverage": 0.0, "audio": hits}   # audio: item 7 (shot_chain.back_time)
        if "events" in full:                         # E17d: the flights refer to the events by index (winner_ball.USE_EVENTS)
            pt["events"] = full["events"]
        orig = wb.good
        wb.good = lambda f, orig=orig: orig(f) and 3.6 * float(np.linalg.norm(f["v0"])) >= p["min_speed"]
        t_cut, wb.T_CUT = wb.T_CUT, T_CUT_E2E
        q = wb.analyse(pt)
        wb.good, wb.T_CUT = orig, t_cut
        w = ws.decide(q, p)
        pw = 0.5 if w is None else (md.case_prob(q, p, mid) if w else 1 - md.case_prob(q, p, mid))
        ch = None
        if "events" in full:                         # E28: the shot chain of the rally (facts for the user; with CHAIN also the evidence)
            try:
                ch = sc.evidence(pt, [e for e in nets if a - 1.0 <= e["t"] <= t_end + 2.5], T_CUT_E2E, q, w,
                                 md.case_key(q, p) if w is not None else None, p["min_speed"])
                pc = sc.case_prob(ch["case"], mid)
                chain_row = sc.export(ch, t_end, T_CUT_E2E, CHAIN, pc)
            except Exception as e:                   # a fault in the chain must not stop the detection: the old rule decides
                note_fault("shot chain", a, e)
                ch = None
            if CHAIN and ch is not None:
                pw = 0.5 if ch["nearWins"] is None else (pc if ch["nearWins"] else 1 - pc)
        # server end: pose vote, then the ball serve event
        nu, fu, nx, fx = [], [], [], []
        Hc = court_H(mid, a) if POSE_SIDE and os.path.exists(os.path.join(out, "court.json")) else None
        for s in range(max(0, int(a) - 1), min(n, int(a) + 3)):
            r, fr = near_far(pose[s])
            for row, up, xs in ((r, nu, nx), (fr, fu, fx)):
                if row is None:
                    continue
                up.append(wrist_up(row))
                if Hc is not None and s <= int(a) + 1:     # the feet: the bottom center of the box
                    xs.append(cv2.perspectiveTransform(np.array([[[(row[1] + row[3]) / 2, row[4]]]], np.float64), Hc)[0, 0, 0])
        nmax = np.nanmax(nu) if np.isfinite(nu).any() else -9
        fmax = np.nanmax(fu) if np.isfinite(fu).any() else -9
        srv = None if max(nmax, fmax) < 0.5 else bool(nmax > fmax)
        xs = nx if srv else fx
        x_srv = float(np.median(xs)) if srv is not None and xs else None
        pose_side = None if x_srv is None or abs(x_srv) < SIDE_X else (srv, x_srv > 0)
        ev = [e for e in serves if a - 1.0 <= e[0] <= a + 2.5]
        speed = None
        if ev:
            srv = ev[0][1]
            if ev[0][2] != "toss":
                speed = 3.6 * float(np.linalg.norm(ev[0][3]["v0"]))
        # fault candidate: the last serve event from the server end before this rally
        fault = None
        if srv is not None:
            c = [e for e in serves if max(prev_end + 1.0, a - 22.0) <= e[0] <= a - MIN_GAP and e[1] == srv]
            if c:
                fault = {"t": c[-1][0], "kind": c[-1][2], "speed": 3.6 * float(np.linalg.norm(c[-1][3]["v0"]))}
                if "events" in full:
                    # The shots of the fault candidate, for the review window (the same window as the fault clip of
                    # the app: 1 s before the serve event to 2.5 s after it). They are facts for the user only.
                    try:
                        fa, fb = fault["t"] - FAULT_BEFORE, min(fault["t"] + FAULT_AFTER, a)
                        fpt = {"start": fa, "end": fb, "events": full["events"], "kinks": [],
                               "flights": [full["flights"][i] for i in np.nonzero((fl_t0 >= fa) & (fl_t0 <= fb))[0]]}
                        fch = sc.evidence(fpt, [e for e in nets if fa <= e["t"] <= fb], T_CUT_E2E, None, None, None, p["min_speed"])
                        fault["chain"] = {"shots": [{k: v for k, v in s.items() if k != "late"} for s in sc.export(fch, fb, T_CUT_E2E, False)["shots"]]}
                        # The serve must land in the service boxes (the chain tests the full court). The side of the box
                        # is not tested: the detection does not know the side of the server.
                        s0 = fault["chain"]["shots"][0] if fault["chain"]["shots"] else None
                        if s0 is not None and s0["end"] in ("in", "out") and s0.get("court"):
                            s0["margin"] = round(min(sc.SW - abs(s0["court"][0]), SERVICE_LINE - abs(s0["court"][1])), 2)
                            s0["end"] = "in" if s0["margin"] > 0 else "out"
                    except Exception as e:           # the fault candidate stays, with no shots
                        note_fault("fault chain", fault["t"], e)
        # shirt color: near minus far (cached: the image reads are the slow part)
        key = f"{int(a)}_{int(b)}"
        if key not in cache:
            cols = []
            for s in range(int(a) + 1, min(n, int(b))):
                r, fr = near_far(pose[s])
                if r is None or fr is None:
                    continue
                img = cv2.imread(lab.kf_file(out, s, kf))
                c1, c2 = torso_color(img, r), torso_color(img, fr)
                if c1 is not None and c2 is not None:
                    cols.append((c1 - c2).tolist())
            cache[key] = cols
            dirty = True
        cols = [np.array(c) for c in cache[key]]
        feats.append(np.median(cols, 0) if len(cols) >= 2 else None)
        last = None
        if q is not None:
            # "nearWins": what the ball rule says (None = no decision); "case": the case of the rule (match_decode.case_key)
            last = {"nearWins": w, "case": md.case_key(q, p) if w is not None else None, "t": a_end_t(q, t_end),
                    "endY": q["end_y"], "endZ": q["end_z"], "netZ": None if np.isnan(q["net_z"]) else q["net_z"],
                    "toFar": bool(q["to_far"]), "speed": q["speed"], "src": int(q["src"]),
                    "margin": None if np.isnan(q["margin"]) else q["margin"],
                    "bx": None if np.isnan(q["bx"]) else q["bx"], "by": None if np.isnan(q["by"]) else q["by"], "tRel": q["t_rel"]}
        rows.append({"start": a, "end": b, "prob": prob, "pBall": pw, "serverNear": srv, "serveSpeed": speed, "fault": fault, "lastShot": last,
                     "shots": int(sum(wb.good(f) and a <= f["t0"] <= b for f in fls)), "poseSide": pose_side})
        if ch is not None:
            rows[-1]["chainCase"], rows[-1]["chainNearWins"] = ch["case"], ch["nearWins"]
            rows[-1]["chain"] = chain_row
        prev_end = b
    if dirty:
        json.dump(cache, open(cp, "w"))
    for r, f, c in zip(rows, flips(feats), feats):
        r["flip"] = f
        r["color"] = None if c is None else [float(x) for x in c]      # for identity.py (E22)
    return rows


def segments(prob, full, cfg, t0=0.0, t1=None):
    """Step 1. The rally segments (start, end, prob; prob -1 = a short point) and the ball serve events inside
    t0 to t1 (s). full: the flights file, or None when the video has no ball stages (then: no short points).
    cfg: the settings of detect.py ("threshold", "shortPoints")."""
    hi = float(cfg.get("threshold", 0.5))
    lo = max(0.05, hi - 0.15)
    if full is None:
        t1 = len(prob) if t1 is None else t1
        return [(float(a), float(b), float(prob[int(a):int(b) + 1].mean()))
                for a, b in lab.segment(prob, smooth=1, hi=hi, lo=lo, min_len=2.0) if a >= t0 and b <= t1], []
    serves = serve_events(full["flights"], t0, len(prob) if t1 is None else t1)
    # E61: short points are off by default. Only 18 of 62 added short points were real on the 4 lab matches, and no
    # rule on the serve facts reached 70 % with a small loss of the real ones.
    return rallies(prob, serves, hi, lo, bool(cfg.get("shortPoints", False)), t0, t1), serves


def winners(mid, seg, full, serves, p=PARAMS, prob=None):
    """Step 2. The result rows of the rallies in seg: evidence, then the score decoder from 0-0 with the server
    not known. Player 1 is the player at the near end in the first rally. The player at the near end comes from
    the shirt colors (identity.py, E22): the decoder gets it as evidence for each rally (E22b). The decoder's own
    state is not usable for the names: right for 30 to 66 %. prob: the GRU probabilities for the rally end
    detector (E60)."""
    import identity
    rows = evidence(mid, seg, full, serves, p, prob)
    if FAULT_SIDE:
        rows = fault_rallies(rows, serves)
    st0 = {(0, 0, 0, 0, -1, srv, True, 0): 0.5 for srv in (1, 2)}
    near1 = identity.p1_near([r["color"] for r in rows])
    if ID_ABS:
        md.FLIP, md.P_ID = None, ID_ABS
        ps = [(r["serverNear"], 0 if n1 else 1) for r, n1 in zip(rows, near1)]
    else:
        md.FLIP = [r["flip"] for r in rows]
        ps = [r["serverNear"] for r in rows]
    for i, r in enumerate(rows):
        r["pNull"] = P_NULL_SHORT if r["prob"] < 0 else p_null(r["prob"], r["shots"])
        r["pMiss"] = p_miss(r["start"] - rows[i - 1]["end"]) if i else 0.02
    post = md.decode([r["pBall"] for r in rows], ps, st0, [], [r["pMiss"] for r in rows], [r["pNull"] for r in rows])
    out = []
    for i, (r, pp, p1_near) in enumerate(zip(rows, post, near1)):
        near_wins = None if abs(pp - 0.5) < 0.02 else bool(pp > 0.5)
        out.append({
            "start": r["start"], "end": r["end"], "kind": "short" if r["prob"] < 0 else "rally",
            "prob": None if r["prob"] < 0 else r["prob"], "pNotPoint": r["pNull"],
            "p1Near": p1_near, "pNearWins": pp, "pBall": r["pBall"],
            "winner": None if near_wins is None else ("P1" if near_wins == p1_near else "P2"),
            "confidence": max(pp, 1 - pp),
            "server": None if r["serverNear"] is None else ("P1" if r["serverNear"] == p1_near else "P2"),
            "serverNear": r["serverNear"], "serveSpeedKmh": r["serveSpeed"], "fault": r["fault"],
            "shots": r["shots"], "lastShot": r["lastShot"], "endChangeBefore": bool(p1_near != near1[i - 1]) if i else None,     # E64: from the identity, as p1Near (the color flips had 7 false changes, the identity 1)
            # E28: the shot chain of the rally (shot_chain.py). E29: it is also the winner evidence of the
            # score model (CHAIN is on).
            "chain": r.get("chain")})
    return out


def serve_side(serves, a):
    """(server near?, x side of the serve flight start > 0?) of the first serve event (not a toss alone) of a rally
    that starts at a, or None."""
    ev = [e for e in serves if a - 1.0 <= e[0] <= a + 2.5 and e[2] != "toss"]
    if not ev or abs(ev[0][3]["p0"][0]) < SIDE_X:
        return None
    return ev[0][1], ev[0][3]["p0"][0] > 0


def fault_rallies(rows, serves):
    """E122: removes each rally that is a first-serve fault before the next rally (see FAULT_SIDE). The next rally
    gets its serve event as the fault candidate when it has none."""
    out, i = [], 0
    while i < len(rows):
        r = rows[i]
        nxt = rows[i + 1] if i + 1 < len(rows) else None
        if (nxt is not None and r.get("chain") is not None and len(r["chain"]["shots"]) <= FAULT_SHOTS
                and nxt["start"] - r["end"] <= FAULT_NEXT):
            s1 = serve_side(serves, r["start"]) or (r.get("poseSide") if POSE_SIDE else None)
            s2 = serve_side(serves, nxt["start"]) or (nxt.get("poseSide") if POSE_SIDE else None)
            if s1 is not None and s1 == s2:
                e = [x for x in serves if r["start"] - 1.0 <= x[0] <= r["start"] + 2.5 and x[2] != "toss"]
                if nxt.get("fault") is None and e:
                    e = e[0]
                    nxt["fault"] = {"t": e[0], "kind": e[2], "speed": 3.6 * float(np.linalg.norm(e[3]["v0"])), "fromRally": True}
                i += 1
                continue
        out.append(r)
        i += 1
    return out


def rally_rows(seg):
    """The result rows with only the boundaries: no ball stages, or a partial result (G13). prob -1 = a short point."""
    return [{"start": a, "end": b, "kind": "short" if prob < 0 else "rally", "prob": None if prob < 0 else prob} for a, b, prob in seg]

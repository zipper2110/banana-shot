"""E28: the shot chain of a rally, and the winner evidence from it.

Input: the fitted flights of a rally (ball_full.py), the ball events that the flights refer to
(ball_bounce.py: "bounce", "hit", "other", "end"), and the net events (net_events.py).
Output of build(): the list of shots in time order. One shot = one hit and the path of the ball
until the next hit:
    hit (who, when, how the hitter is known) -> flight -> net touch or bounce (where) -> flight after the bounce.
Rules of the chain (facts of the game, no tuned values):
  - A flight that starts at a bounce event is the same shot after its bounce.
  - A flight that starts at a hit event, or that flies in the opposite direction along the court,
    is a new shot. The hitter is the player at the end that the ball leaves.
  - The ball data has gaps. A flight in the same direction as the shot before it, after a long gap
    or with a start far behind the place where the shot before ended, is a new shot of the same
    player: the return of the other player between them was not seen ("gap").
evidence(): the last shot before the end of the rally gives one case (CASES) and the end of the
winner. The probability of each case is measured on the marked matches (fit_cases(), leave one
match out).
Usage: python shot_chain.py                    the measurement on the 554 marked points
       python shot_chain.py <work folder> <t0> <t1> ...   the chain of the rallies of a work folder
"""
import json, os, sys
import numpy as np

SW, BL, SL = 4.115, 11.885, 6.40      # m: half width of the singles court, baseline, service line
MIN_DUR = 0.15        # s: shortest flight that is used (10 samples at 60 fps, as winner_ball.good; 6 samples at 30 fps)
MIN_N = 6             # samples
SAME_GAP = 0.6        # s: a flight in the same direction after a shorter gap is the same shot (as old_quantities)
NEW_GAP = 1.5         # s: after a longer gap it is a new shot (a tennis ball is in the air for less than this between two hits)
BACK_M = 3.0          # m: or a new shot if the flight starts this far behind the last known place of the ball
HIT_V = 8.0           # m/s along the court: a flight back that is faster than this is a hit (ball_bounce.HIT_V)
OWN_BACK = 0.5        # m: a bounce on the side of the hitter counts up to this distance behind the baseline
NET_AFTER = 0.7       # s: a net event belongs to a shot if it is not later than this after the last sample of the shot
# E32:
FIRST_STEEP = os.environ.get("CHAIN_FIRST_STEEP", "1") == "1"
STEEP, STEEP_Z, FIRST_DEEP = 0.7, 1.0, 3.0      # vertical / ground speed (35 degrees up); m, start height (a ball after its bounce starts lower); m before the baseline
OUT_LINE = os.environ.get("OUT_LINE", "1") == "1"
NET_NEAR = float(os.environ.get("NET_NEAR", "0.5"))     # m behind the net: a bounce nearer to the net than this is not a good shot (the place of a bounce has an error of 0.1 to 0.3 m)
SLOW = os.environ.get("CHAIN_SLOW", "1") == "1"
SLOW_V = 4.0          # m/s over the ground: a slower flight does not start a shot (a ball toss, a ball that drops at the net, a ball in the hand)
FIRST_WEAK = os.environ.get("CHAIN_FIRST_WEAK", "1") == "1"
FIRST_DUR, FIRST_GAP = 0.4, 0.6     # s: a weak first shot shorter than FIRST_DUR with no bounce and no net event, and FIRST_GAP before the next shot, is not a shot
PRED_SIGMA, PRED_AFTER = 1.0, 1.0   # m, s: the fit landing is a "predicted" end of a shot if its 1-sigma error and its time after the last sample are below these
PRED_ERR = 1.2        # m: the measured error of a fit landing (E33: median 1.1 to 1.6 m against the seen bounces). A predicted landing nearer to a line is "line", not in or out
LATE_BOUNCE = os.environ.get("CHAIN_LATE_BOUNCE", "1") == "1"
# E121 (found on Igor, E76 group B; values from physics): the serve shot starts at the racket contact, not at the toss.
# A toss goes up 1 m or more over the hand and falls back to the racket: 2 * sqrt(2 * 1.0 / 9.81) = 0.9 s; 0.7 s allows
# for the samples that are lost at the start. A toss higher than 2.5 m over the hand is not normal: 1.5 s at most.
# 9 m from the net: the toss area of serve_ball.is_toss.
SERVE_TIME = os.environ.get("CHAIN_SERVE_TIME", "1") == "1"
TOSS_DUR, TOSS_MAX, TOSS_GAP, TOSS_Y = 0.7, 1.5, 0.3, 9.0   # s, s, s (a split of the track at the contact), m
# E123: a toss exists only at the serve, and the toss is over the court (|x| < TOSS_X m, as serve_ball.is_toss). The
# serve is the first shot of a point, or the second when one false shot is before it (ball handling, E76 group C).
SERVE_SHOTS, TOSS_X = 2, 5.5
# Item 8 (E76 group C, found on Igor m1116): a toss exists only at the serve (E123). When the second shot has the toss, the
# first shot is ball handling before the serve, and it is removed. A first shot that starts at the serve place (TOSS_Y m or
# more from the net on the end of its hitter) stays: it can be a fault, and the second serve follows.
PRE_SERVE = os.environ.get("CHAIN_PRE_SERVE", "1") == "1"
LATE_S, LATE_V = 1.5, (0.5, 2.0)    # s after the last sample of the shot; lowest and highest mean ground speed to the bounce, as a factor of the fit speed
CONT = os.environ.get("CHAIN_CONT", "1") == "1"
CONT_GAP, CONT_M = float(os.environ.get("CONT_GAP", "0.35")), float(os.environ.get("CONT_M", "4.0"))   # s, m: see continuous()
NET_CONF, NET_DIR = 0.4, 0.3      # lowest confidence of a net event and of its direction (net_events.HINT_DIR)
# Item 6 group D (found on Igor m1116): the ball is dead after a shot into the net, out or on the side of the hitter. A later
# "shot" is ball handling, and the shot list stops there (see dead_stop).
DEAD_STOP = os.environ.get("CHAIN_DEAD_STOP", "1") == "1"
DEAD_GAP = 1.0       # s from the last sample of the dead shot to the next shot (lab: 95 % of the gaps between real rally shots are shorter)
# Item 6 group E (found on Igor m1116): one real shot gives two shots of the two players, and one of them is weak (see drop_splits).
SPLIT = os.environ.get("CHAIN_SPLIT", "1") == "1"
SPLIT_DT = 0.45      # s between the starts of two shots of the two players (lab: 99 % of these times between two shots that are not weak are longer)
# Item 7 (E76 group A, found on Igor m1116): the time of a "back" shot (its hit flight is not seen) is the meeting point of
# the incoming and the outgoing ball (see back_time).
BACK_TIME = os.environ.get("CHAIN_BACK_TIME", "1") == "1"
BOUNCE_KEEP = 0.36   # speed along the court after a bounce / before it (lab: median of 1303 bounces, 25 to 75 %: 0.23 to 0.50)
# s: a meeting time up to this before the last sample of the incoming ball is that sample (item 9: at seen hits the hit is
# 0.04 s after that sample, median; lab back shots 0.1 to 0.3 s before it: an audio candidate after the sample for 81 %, after the
# old time for 40 %; more than 0.3 s before: no support). An earlier meeting time is not used.
HIT_EARLY = 0.3
AUDIO_DB, AUDIO_WIN = 9.0, (-0.02, 0.2)   # E71: an audio hit candidate of 9 dB or more comes 0 to 0.2 s after the hit


def kmh(f):
    return 3.6 * float(np.linalg.norm(f["v0"]))


def usable(f, min_speed=8.0):
    """The fit of the flight is reliable for the direction along the court."""
    return f["t1"] - f["t0"] >= MIN_DUR - 1e-6 and f["n"] >= MIN_N and f["rms"] < 5 and min_speed < kmh(f) < 230


def pos_ok(f):
    """The 3D start position is inside a large box around the court (also old_good)."""
    return abs(f["p0"][1]) < 25 and abs(f["p0"][0]) < 12


def _ev(events, i, kind):
    return events[i] if (events is not None and i is not None and events[i]["kind"] == kind) else None


def continuous(cur, f, b0):
    """E39. A flight that changes the direction of the ball starts where the ball was: at the bounce event at its start,
    or at the end of the last flight of the shot with a plausible position. False: the start of the fit is too far from
    that place for the time between them. Then the fit is wrong (a short piece after a bounce on the far side often
    gets a fit that flies back fast from a far place), and its direction is not a fact."""
    if cur["fl"][-1]["n"] < f["n"]:
        return True                        # the fit with more samples is the better one: a short piece does not reject a long flight
    if b0 is not None and b0.get("court") is not None:
        # The place of the event is exact only if the ball was on the ground there. The flight before it gives that.
        ref, t, v = b0["court"], b0["t"], 0.0
    else:
        g = next((x for x in reversed(cur["fl"]) if pos_ok(x)), None)
        if g is None or g["n"] < f["n"]:
            return True
        ref, t, v = g["p1"], g["t1"], float(np.linalg.norm(g["v0"]))
    gap = f["t0"] - t
    if gap > CONT_GAP:
        return True
    reach = CONT_M + max(gap, 0.0) * max(v, min(float(np.linalg.norm(f["v0"])), 70.0))
    return float(np.hypot(f["p0"][0] - ref[0], f["p0"][1] - ref[1])) <= reach


def build(flights, events, nets=(), min_speed=8.0, weak=True, audio=None):
    """The shot chain. flights: the flights of the rally window, in time order. events: the event list
    that "ev0", "ev1" and "bounceEv" of the flights refer to (None = a flights file with no events).
    nets: net events of the same time range. weak: also use flights with a start position outside the
    court box (their direction is used, their positions are not).
    audio: the audio hit candidates (hits.npy: time, dB, ...) or None (for back_time).
    Returns a list of shots: {"t", "near", "how", "fl", "bounces", "net", "weak", "serve"}."""
    shots, cur = [], None
    for f in flights:
        if not usable(f, min_speed) or (not weak and not pos_ok(f)):
            continue
        to_far = f["v0"][1] > 0
        s = 1.0 if to_far else -1.0
        b0, h0 = _ev(events, f.get("ev0"), "bounce"), _ev(events, f.get("ev0"), "hit")
        same = cur is not None and cur["near"] == to_far
        new, how, turn = True, "first", None
        hs = float(np.hypot(f["v0"][0], f["v0"][1]))
        slow = SLOW and hs < SLOW_V
        if slow and (cur is None or (same and f["t0"] - cur["fl"][-1]["t1"] >= SAME_GAP) or (not same and b0 is None)):
            continue                                          # E32: a slow flight that is not a part of the shot before it starts no shot
        if FIRST_STEEP and cur is None and b0 is None and f["p0"][2] > STEEP_Z and f["v0"][2] > STEEP * hs:
            continue                                          # E32b: the first shot of a rally does not go up this steeply from this height: a ball toss
        if same:
            gap = f["t0"] - cur["fl"][-1]["t1"]
            if b0 is not None and gap < SAME_GAP:
                new = False                                   # the same shot after its bounce
            elif h0 is not None and gap < SAME_GAP:
                new = False                                   # a hit event with no change of the direction: not a new shot
            elif gap < SAME_GAP:
                new = False
            else:
                back = pos_ok(f) and cur["reach"] is not None and f["p0"][1] * s < cur["reach"] - BACK_M
                new, how = (gap >= NEW_GAP or back), "gap"
            if not new and came_back(cur, [e for e in (b0, _ev(events, f.get("bounceEv"), "bounce")) if e is not None]):
                # The ball bounced on the other side, and now it bounces on the side of the hitter: the other
                # player returned it. The return was not seen (or its fit has the wrong direction).
                new, how, to_far, s = True, "back", not to_far, -s
        elif cur is not None:
            if CONT and not continuous(cur, f, b0):
                # E39: the fit does not start where the ball was. It is not a new shot. Its bounce event is the bounce of the shot.
                if b0 is not None and b0["court"] is not None and came_back(cur, [b0]) and to_far != cur["near"]:
                    # The ball bounced on the other side before, and now it bounces on the side of the hitter: the other
                    # player returned it (the return was not seen). The fit agrees with the direction of the return.
                    cur = {"t": float(f["t0"]), "near": bool(to_far), "how": "back", "fl": [f], "bounces": [b0], "net": None,
                           "reach": b0["court"][1] * s, "weak": True, "serve": None}
                    shots.append(cur)
                    continue
                if b0 is not None and b0["court"] is not None and all(b0 is not x for x in cur["bounces"]):
                    cur["bounces"].append(b0)
                    cur["reach"] = max(cur["reach"] if cur["reach"] is not None else -99.0, b0["court"][1] * (1.0 if cur["near"] else -1.0))
                continue
            how = "hit" if h0 is not None else "back"
            if b0 is not None:
                # The direction changes at an event that passed the bounce test. A fast flight back: the other player
                # hit the ball there (on the rise after the bounce, or in the air: then the event is not on the ground).
                # A slow flight back: the bounce is real, and the direction of the short fit after it is not reliable.
                if abs(f["v0"][1]) >= HIT_V and b0.get("byLanding"):
                    # E40: the flight before this event came to the ground there: the bounce is real. The hit after it
                    # made no event. The new shot starts after the bounce, and the bounce stays with the shot before.
                    how = "back"
                    if b0["court"] is not None and all(b0 is not x for x in cur["bounces"]):
                        cur["bounces"].append(b0)
                    b0 = None
                elif abs(f["v0"][1]) >= HIT_V:
                    how, turn = "hit", b0
                    cur["bounces"] = [e for e in cur["bounces"] if e is not b0]
                    cur["returned"] = True
                    b0 = None
                else:
                    new, to_far, s = False, cur["near"], -s
        if new:
            if turn is not None:
                h0 = turn
            cur = {"t": float(h0["t"] if h0 is not None else f["t0"]), "near": bool(to_far), "how": how, "fl": [], "bounces": [],
                   "net": None, "reach": None, "weak": not pos_ok(f), "serve": None}
            shots.append(cur)
        cur["fl"].append(f)
        for e in (b0, _ev(events, f.get("bounceEv"), "bounce")):
            if e is not None and e["court"] is not None and all(e is not x for x in cur["bounces"]):
                cur["bounces"].append(e)
        if pos_ok(f):
            r = max(f["p0"][1] * s, f["p1"][1] * s)
            cur["reach"] = r if cur["reach"] is None else max(cur["reach"], r)
        for e in cur["bounces"]:
            cur["reach"] = e["court"][1] * s if cur["reach"] is None else max(cur["reach"], e["court"][1] * s)
    if FIRST_WEAK and len(shots) >= 2:
        a, b = shots[0], shots[1]
        s0 = 1.0 if a["near"] else -1.0
        # E32b: also a flight that starts near the baseline of the end it flies to (it did not come over the net in view)
        deep = FIRST_STEEP and not a["weak"] and a["fl"][0]["p0"][1] * s0 > BL - FIRST_DEEP
        if (a["weak"] or deep) and not a["bounces"] and a["fl"][-1]["t1"] - a["fl"][0]["t0"] < FIRST_DUR and b["fl"][0]["t0"] - a["fl"][-1]["t1"] > FIRST_GAP:
            shots.pop(0)                                      # E32: the hitter of the next shot came from this flight: now it is the first shot
            b["how"] = "first"
    if SPLIT:
        shots = drop_splits(shots)
    if SERVE_TIME:
        toss = [serve_time(sh) for sh in shots[:SERVE_SHOTS]]
        if PRE_SERVE and toss == [False, True] and (shots[0]["fl"][0]["p0"][1] * (-1.0 if shots[0]["near"] else 1.0) < TOSS_Y):
            shots.pop(0)
            shots[0]["how"] = "first"
    if LATE_BOUNCE and events:
        late_bounces(shots, events)
    if BACK_TIME:
        back_time(shots, events, audio)
    # net events: each one belongs to the shot that is in the air at its time
    seen, alone = list(shots), []
    for e in nets:
        if e["kind"] != "net" or e["conf"] < NET_CONF:
            continue
        c = [i for i, sh in enumerate(seen) if sh["fl"][0]["t0"] - 0.05 <= e["t"] <= sh["fl"][-1]["t1"] + NET_AFTER
             and (i + 1 == len(seen) or e["t"] < seen[i + 1]["fl"][0]["t0"])]
        if c:
            sh = seen[c[-1]]
            if sh["near"] == bool(e["toFar"]):
                if sh["net"] is None:
                    sh["net"] = e
            elif e["dirConf"] >= 0.7 and sh["weak"]:
                sh["near"], sh["net"] = bool(e["toFar"]), e           # the net event knows the direction better than a weak flight
            else:
                sh.setdefault("netConflict", e)                       # the ball flies away from the net: not a touch by this shot
        elif e["dirConf"] >= NET_DIR:
            alone.append({"t": float(e["t"]), "near": bool(e["toFar"]), "how": "net", "fl": [], "bounces": [], "net": e,
                          "reach": None, "weak": True, "serve": None})      # the flight of this shot was not tracked
    shots = sorted(seen + alone, key=lambda sh: sh["t"])
    return dead_stop(shots) if DEAD_STOP else shots


def dead(sh):
    """True if the ball is surely dead after the shot: a net event and no bounce on the other side (a let cord goes on), a bounce
    out by more than the bounce place error (LINE_M), or a bounce on the side of the hitter."""
    s = 1.0 if sh["near"] else -1.0
    if sh["net"] is not None and not any(e["court"][1] * s > NET_NEAR for e in sh["bounces"]):
        return True
    k, m = shot_end(sh)
    return k == "own" or (k == "out" and m < -LINE_M)


def dead_stop(shots):
    """Item 6 group D: the shots up to the first dead shot that has a gap of DEAD_GAP or more to the next shot. The stop needs
    a returned shot before the dead shot (a shot "in" and a shot of the other player less than DEAD_GAP after it): before the
    return, a dead ball can be a fault (a second serve follows) or ball handling before the serve. The gap keeps a real return
    after a dead ball that was measured wrong (lab, 4 matches: these returns start 0.3 s after the last sample, median)."""
    def t0(sh):
        return sh["fl"][0]["t0"] if sh["fl"] else sh["t"]

    def t1(sh):
        return sh["fl"][-1]["t1"] if sh["fl"] else sh["t"]
    returned = False
    for i in range(len(shots) - 1):
        sh, nx = shots[i], shots[i + 1]
        gap = t0(nx) - t1(sh)
        if returned and gap >= DEAD_GAP and dead(sh):
            return shots[:i + 1]
        returned = returned or (shot_end(sh)[0] == "in" and nx["near"] != sh["near"] and gap < DEAD_GAP)
    return shots


def serve_time(sh):
    """E121: a shot that has a toss flight and then a faster flight from the same end: the hit is at the start of the
    faster flight. Toss flight: it starts TOSS_Y m or more from the net on the end of the hitter and lasts TOSS_DUR to
    TOSS_MAX s. The faster flight starts on the same end not later than TOSS_GAP s after the end of the toss flight.
    Returns True if the shot has a toss."""
    s = -1.0 if sh["near"] else 1.0                     # the end of the hitter: the near player is at y < 0
    fl = sh["fl"]
    for f, g in zip(fl, fl[1:]):
        if (f["p0"][1] * s >= TOSS_Y and abs(f["p0"][0]) < TOSS_X and TOSS_DUR <= f["t1"] - f["t0"] <= TOSS_MAX and g["p0"][1] * s >= TOSS_Y
                and 0.0 <= g["t0"] - f["t1"] <= TOSS_GAP and np.linalg.norm(g["v0"]) > np.linalg.norm(f["v0"])):
            if g["t0"] > sh["t"]:
                sh["t"] = float(g["t0"])
            return True
    return False


def meet_time(prev, sh, events):
    """The time at which the ball line of the shot sh, extended back, meets the incoming ball line of the shot prev (the other
    player), along the court (y). Incoming line: the last flight of prev; from its bounce (seen, else the fit landing of a flight
    that did not bounce yet) with BOUNCE_KEEP of its speed. None if the lines do not meet between the last sample of the incoming
    ball (- HIT_EARLY) and the first sample of sh. A time before the last incoming sample is that sample (item 9)."""
    fi = [f for f in prev["fl"] if pos_ok(f)]
    if not fi or not sh["fl"] or not pos_ok(sh["fl"][0]):
        return None
    fi, fo = fi[-1], sh["fl"][0]
    ti, yi, vi = fi["t0"], fi["p0"][1], fi["v0"][1]
    post = events is not None and fi.get("ev0") is not None and events[fi["ev0"]]["kind"] == "bounce"
    bl = [e for e in prev["bounces"] if e["t"] > fi["t1"] - 0.05]
    if bl:
        e = min(bl, key=lambda e: e["t"])
        ti, yi, vi = e["t"], e["court"][1], vi * BOUNCE_KEEP
    elif not post and fi.get("landT") is not None and fi.get("land") is not None and fi["landT"] > fi["t1"] - 0.05:
        ti, yi, vi = fi["landT"], fi["land"][1], vi * BOUNCE_KEEP
    vo = fo["v0"][1]
    if vi * vo >= 0:
        return None
    t = (fo["p0"][1] - yi + vi * ti - vo * fo["t0"]) / (vi - vo)
    return min(max(t, fi["t1"]), fo["t0"]) if fi["t1"] - HIT_EARLY <= t <= fo["t0"] else None   # item 9: the hit is not before the last incoming sample


def back_time(shots, events, audio=None):
    """Item 7: a "back" shot starts at its first seen flight, often 0.2 to 0.9 s after the hit. Its time becomes the meet_time
    with the shot before (lab, 4 matches: the error at seen hit events has a median of 0.06 s; an audio candidate follows the
    new time for 77 % of the back shots, the old time for 40 %, a random time for 40 %). With audio: the old time stays if an
    audio candidate follows it and none follows the new time."""
    au = None if audio is None or not len(audio) else np.asarray(audio)[np.asarray(audio)[:, 1] >= AUDIO_DB, 0]

    def heard(t):
        return au is not None and bool(((au >= t + AUDIO_WIN[0]) & (au <= t + AUDIO_WIN[1])).any())
    for prev, sh in zip(shots, shots[1:]):
        if sh["how"] != "back" or prev["near"] == sh["near"]:
            continue
        t = meet_time(prev, sh, events)
        if t is None or (heard(sh["t"]) and not heard(t)):
            continue
        sh["t"] = float(t)


def drop_splits(shots):
    """Item 6 group E: two shots of the two players that start less than SPLIT_DT apart, of which one is weak (its start is
    outside the court box), are one shot: the weak one is a broken fit of the other one. The weak shot is removed. A bounce of a
    later weak shot on the other side of the kept shot goes to the kept shot."""
    out = []
    for sh in shots:
        pv = out[-1] if out else None
        if (pv is not None and pv["near"] != sh["near"] and pv["weak"] != sh["weak"] and pv["fl"] and sh["fl"]
                and sh["fl"][0]["t0"] - pv["fl"][0]["t0"] < SPLIT_DT):
            if sh["weak"]:
                s = 1.0 if pv["near"] else -1.0
                for e in sh["bounces"]:
                    if e["court"][1] * s > 0 and all(e is not x for x in pv["bounces"]):
                        pv["bounces"].append(e)
                        pv["reach"] = e["court"][1] * s if pv["reach"] is None else max(pv["reach"], e["court"][1] * s)
                continue
            if pv["how"] == "first":
                sh["how"] = "first"
            out[-1] = sh
            continue
        out.append(sh)
    return out


def late_bounces(shots, events):
    """E36: a bounce event that no shot has, after the last sample of a shot and before the next shot. The track of
    the ball stopped in the air, and the flight after the bounce is too short to use, so no flight of the shot refers
    to the event. The shot gets the bounce if it is on the other side of the net, not later than LATE_S after the last
    sample, and at a distance that agrees with the speed of the last flight."""
    owned = {id(e) for sh in shots for e in sh["bounces"]}
    for i, sh in enumerate(shots):
        if sh["bounces"] or not sh["fl"] or sh.get("returned"):
            continue
        f = sh["fl"][-1]
        if not pos_ok(f):
            continue
        s = 1.0 if sh["near"] else -1.0
        t_next = shots[i + 1]["t"] if i + 1 < len(shots) else f["t1"] + LATE_S
        hs = float(np.hypot(f["v0"][0], f["v0"][1]))
        for e in events:
            if e["kind"] != "bounce" or e["court"] is None or id(e) in owned or not f["t1"] < e["t"] <= min(t_next, f["t1"] + LATE_S):
                continue
            v = float(np.hypot(e["court"][0] - f["p0"][0], e["court"][1] - f["p0"][1])) / (e["t"] - f["t0"])
            if e["court"][1] * s > 0 and LATE_V[0] * hs <= v <= LATE_V[1] * hs:
                sh["bounces"].append(e)
                owned.add(id(e))
                break


def came_back(sh, evs):
    """True if one of the bounce events evs is more than BACK_M behind a bounce of the shot that is on the other side."""
    s = 1.0 if sh["near"] else -1.0
    far = [e["court"][1] * s for e in sh["bounces"] if e["court"][1] * s > 0]
    return bool(far) and any(e["court"] is not None and all(e is not x for x in sh["bounces"]) and e["court"][1] * s < min(0.0, max(far) - BACK_M)
                             for e in evs)


def first_bounce(sh):
    """(x, across; y, along the court in the direction of the shot (m after the net); time; sigma m) or None."""
    if not sh["bounces"]:
        return None
    e = min(sh["bounces"], key=lambda e: e["t"])
    s = 1.0 if sh["near"] else -1.0
    return float(e["court"][0]), float(e["court"][1] * s), float(e["t"]), float(e["conf"] or 0.0)


def shot_end(sh, tol=0.0):
    """The end of a shot as a fact: (kind, margin m). kind: "net", "own" (the bounce is on the side of the
    hitter), "in", "out", "none" (not seen). margin: distance to the nearest line (positive = inside)."""
    if sh["net"] is not None:
        return "net", None
    b = first_bounce(sh)
    if b is None:
        return "none", None
    x, y = b[0], b[1]
    if y < -(BL + OWN_BACK):
        return "none", None           # behind the baseline of the hitter: not a bounce of a shot that flies to the net
    if y < NET_NEAR:
        return "own", min(y, 0.0)     # E32: also a bounce right behind the net: the ball touched the net and dropped
    m = min(SW - abs(x), BL - y)
    return ("in" if m > -tol else "out"), m


# E74 (G7): the net test of the old rule (winner_sweep.decide, match_decode.PARAMS net_back and net_fwd) in the chain.
NET_STOP = os.environ.get("CHAIN_NET_STOP", "1") == "1"
STOP_BACK, STOP_FWD, STOP_Z = 4.0, 2.0, 1.8     # m before and after the net plane along the shot; highest end of the flight


def net_stop(sh, t_last):
    """True if the ball of a shot with no net event and no bounce event stopped at the net: the last flight of the shot
    that starts before t_last ends 4 m before to 2 m after the net plane, lower than 1.8 m (the old rule: "in the net")."""
    if sh["net"] is not None or sh["bounces"]:
        return False
    fl = [f for f in sh["fl"] if f["t0"] < t_last and pos_ok(f)]
    if not fl:
        return False
    s = 1.0 if sh["near"] else -1.0
    return -STOP_BACK < fl[-1]["p1"][1] * s < STOP_FWD and fl[-1]["p1"][2] < STOP_Z


def after_bounce(sh):
    """Seconds for which the ball was tracked after the first bounce of the shot."""
    b = first_bounce(sh)
    if b is None or not sh["fl"]:
        return 0.0
    return max(0.0, sh["fl"][-1]["t1"] - b[2])


def last_shot(shots, t_end, t_cut):
    """Index of the last shot that starts before t_end + t_cut (winner_ball.T_CUT: later shots are ball handling), or None."""
    c = [i for i, sh in enumerate(shots) if (sh["fl"][0]["t0"] if sh["fl"] else sh["t"]) < t_end + t_cut]
    return c[-1] if c else None


# ---------------------------------------------------------------- the old rule (G7 step 1)
# The landing test of the old rule (E16: winner_ball.analyse, winner_sweep.decide, match_decode.case_key), moved into the
# chain with the same decisions (G7, manager decision 5199e697). The chain uses its decision only when the last shot has
# no net, bounce or stop fact (cases "old_*" and "back_*"). The old rule has its own filter, run and bounce: the last run
# of good flights in one direction, its first low kink (the kink rule) or else the fit landing of its first flight.
OLD_MIN_N, OLD_LAND_N = 10, 20     # samples of a good flight, and of a flight with a usable fit landing, at 60 fps (0.15 s, 0.32 s)
# G7 step 3: the seen bounce of the old rule. "event" = the bounce event at the end of a flight of the run (ball_bounce, the
# one bounce fact of the chain); "kink" = the kink rule (a low kink in the court area, before G7 step 3).
OLD_BOUNCE = os.environ.get("CHAIN_OLD_BOUNCE", "event")


def set_fps(fps):
    """E28: the sample counts of the old rule for a ball track with fps samples in each second (the same durations)."""
    global OLD_MIN_N, OLD_LAND_N
    OLD_MIN_N, OLD_LAND_N = max(5, int(round(10 * fps / 60.0))), max(8, int(round(20 * fps / 60.0)))


def old_good(f, min_speed=8.0):
    """A good flight of the old rule: OLD_MIN_N samples, fit error < 5 px, speed min_speed (and 8) to 230 km/h, start
    inside a large box around the court."""
    sp = kmh(f)
    return f["n"] >= OLD_MIN_N and f["rms"] < 5 and 8 < sp < 230 and pos_ok(f) and sp >= min_speed


def old_quantities(pt, t_cut, min_speed=8.0):
    """The quantities of the last shot of the old rule (before G7: winner_ball.analyse), or None if there is no
    good flight that starts before the end + t_cut. The seen bounce: OLD_BOUNCE; else the fit landing of the first
    flight of the run."""
    T = pt["end"]
    fl = [f for f in pt["flights"] if old_good(f, min_speed) and f["t0"] < T + t_cut]
    if not fl:
        return None
    run = [fl[-1]]
    for f in fl[-2::-1]:
        if (f["v0"][1] > 0) == (run[0]["v0"][1] > 0) and run[0]["t0"] - f["t1"] < 0.6:
            run.insert(0, f)
        else:
            break
    to_far = run[0]["v0"][1] > 0
    s = 1.0 if to_far else -1.0
    bounce, src = None, 0
    events = pt.get("events")
    for f in run if OLD_BOUNCE == "event" and events else []:
        if f.get("bounceEv") is not None:            # this flight ends at a bounce event
            e = events[f["bounceEv"]]
            bounce, src = (e["court"][0], e["court"][1], e["t"]), 2
            break
    for k in pt["kinks"] if OLD_BOUNCE == "kink" else []:   # the kink rule: a kink where the ball is low, inside the court area
        t = k[0]
        if t < run[0]["t0"] + 0.1 or t > run[-1]["t1"] + 0.3:
            continue
        pre = [f for f in run if 0 <= t - f["t1"] < 0.2]
        post = [f for f in run if 0 <= f["t0"] - t < 0.2]
        low = (pre and pre[-1]["p1"][2] < 0.7) or (post and post[0]["p0"][2] < 0.6 and post[0]["v0"][2] > 0)
        if low and abs(k[3]) < 12 and abs(k[4]) < 22:
            bounce, src = (k[3], k[4], t), 2
            break
    if bounce is None and not np.isnan(run[0]["land"][0]) and run[0]["n"] >= OLD_LAND_N and run[0]["landT"] < T + 1.5:
        bounce, src = (run[0]["land"][0], run[0]["land"][1], run[0]["landT"]), 1
    crossed = any(f["p1"][1] * s > 0.5 for f in run) or (bounce is not None and bounce[1] * s > 0)
    q = {"to_far": to_far, "src": src, "n_run": len(run), "n_pts": sum(f["n"] for f in run), "speed": kmh(run[0]),
         "t_rel": run[0]["t0"] - T, "t_end_rel": run[-1]["t1"] - T, "net_z": run[0]["netZ"], "crossed": crossed,
         "start_y": run[0]["p0"][1] * s, "start_z": run[0]["p0"][2], "end_y": run[-1]["p1"][1] * s, "end_z": run[-1]["p1"][2],
         "bx": np.nan, "by": np.nan, "margin": np.nan, "n_flights": len(fl), "coverage": pt["coverage"], "sigma": np.nan}
    if bounce is not None:
        bx, by = bounce[0], bounce[1] * s
        q.update(bx=abs(bx), by=by, margin=min(SW - abs(bx), BL - by, by), b_rel=bounce[2] - T)
    return q


def old_decide(q, p):
    """The decision of the old rule (before G7: winner_sweep.decide): True = the near player wins, None = no decision.
    p: margin (a bounce out by less than this is in), net_back and net_fwd (the net test), optional z (landing sigma)."""
    if q is None:
        return None
    if -p["net_back"] < q["end_y"] < p["net_fwd"] and q["end_z"] < 1.8 and not q["src"] == 2:
        return not q["to_far"]                         # in the net: the hitter loses
    if q["src"] == 0:
        return None
    if q["src"] == 1 and p.get("z", 0) > 0 and not np.isnan(q.get("sigma", np.nan)) and abs(q["margin"] + p["margin"]) < p["z"] * q["sigma"]:
        return None
    is_in = q["margin"] > -p["margin"] and (np.isnan(q["net_z"]) or q["net_z"] > 0.85)
    return q["to_far"] if is_in else not q["to_far"]


def old_case_key(q, p):
    """The case of a decision of the old rule (before G7: match_decode.case_key). "early" = the last shot starts more than 4 s
    before the end (a later shot was probably missed)."""
    early = q["t_rel"] < -4
    if -p["net_back"] < q["end_y"] < p["net_fwd"] and q["end_z"] < 1.8 and q["src"] != 2:
        return "net_early" if early else "net"
    is_in = q["margin"] > -p["margin"] and (np.isnan(q["net_z"]) or q["net_z"] > 0.85)
    if is_in:
        return "in_seen" if q["src"] == 2 else ("in_early" if early else "in")
    return "out_early" if early else ("out_seen" if q["src"] == 2 else "out")


def old_rule(pt, t_cut, p):
    """(quantities, decision, case) of the old rule for the window pt (needs "kinks" and "coverage")."""
    q = old_quantities(pt, t_cut, p["min_speed"])
    w = old_decide(q, p)
    return q, w, (old_case_key(q, p) if w is not None else None)


# ---------------------------------------------------------------- winner evidence

LINE_M = 0.3          # m: a bounce nearer to a line than this is a "line" case (the position error of a bounce event, E17d-2: 90 % below 0.28 m)
EARLY = -4.0          # s: the last shot starts more than this before the end: a later shot was probably not tracked (old_case_key)
# The cases, and the probability that the decision of the case is right (all 4 marked matches, T_CUT -1.5, events of E32;
# fit_cases() measures them; use_lomo_cases() gives each lab match the table of the other 3 matches).
# E38d: fitted with the detections of the fine-tuned ball weights (each match with weights that did not see it).
# E74: fitted again with the cases "stop" and "stop_early" (the net test of the old rule in the chain).
CASE_P = {"back_in": 0.58, "back_stop": 0.85, "in": 0.88, "in_early": 0.83, "in_line": 0.79, "in_line_early": 0.83, "net_hi": 0.97, "net_lo": 0.8, "old_in": 0.7, "old_in_early": 0.75, "old_in_seen": 0.75, "old_net": 0.5, "old_out": 0.72, "old_out_early": 0.55, "old_out_seen": 0.94, "out": 0.96, "out_early": 0.9, "out_line": 0.83, "out_line_early": 0.75, "own": 0.8, "own_early": 0.61, "stop": 0.87, "stop_early": 0.83}
CASES_BY_MATCH = None
TEXT = {"net": "the ball touched the net and stayed on the side of the hitter", "own": "the ball came down on the side of the hitter",
        "out": "the ball bounced out", "in": "the ball bounced in and no return was seen",
        "back": "the end of this shot was not seen", "stop": "the flight of the ball ends low near the net", "old": "no bounce or net event; the fitted flight decides", "none": "nothing is known"}


def evidence(pt, nets, t_cut, p=None, min_speed=8.0):
    """The winner evidence of the window pt (_evidence). p: the parameters of the old rule (old_rule; None = no old
    rule, for example the shots of a fault candidate). r["old"]: the quantities, the decision and the case of the old
    rule (for the "lastShot" of the result and for CHAIN=0)."""
    q, w_old, old_case = old_rule(pt, t_cut, p) if p is not None else (None, None, None)
    r = _evidence(pt, nets, t_cut, q, w_old, old_case, min_speed)
    r["events"] = pt.get("events")
    r["old"] = {"q": q, "nearWins": w_old, "case": old_case}
    return r


def predicted_end(sh, events):
    """E32. A shot with no seen end: the landing of the fit of its last flight ("end" event of ball_bounce.py), if the
    fit is good. Returns {"t", "end" ("in", "out", "line" = too near to a line for a decision, "own"), "court", "margin", "sigma"} or None. This is for the display.
    The winner evidence does not use it: the old rule uses the same landing with its measured probability."""
    if not sh["fl"] or events is None:
        return None
    i = sh["fl"][-1].get("ev1")
    e = events[i] if i is not None else None
    if e is None or e["kind"] != "end" or e.get("court") is None or e.get("conf") is None or e["conf"] > PRED_SIGMA:
        return None
    if not -0.05 <= e["t"] - sh["fl"][-1]["t1"] <= PRED_AFTER:
        return None
    s = 1.0 if sh["near"] else -1.0
    x, y = float(e["court"][0]), float(e["court"][1]) * s
    if y < -(BL + OWN_BACK):
        return None
    kind, m = ("own", min(y, 0.0)) if y < NET_NEAR else (lambda m: ("line" if abs(m) < PRED_ERR else "in" if m > 0 else "out", m))(min(SW - abs(x), BL - y))
    return {"t": round(float(e["t"]), 3), "end": kind, "court": [round(x, 2), round(y * s, 2)], "margin": round(m, 2), "sigma": round(float(e["conf"]), 2)}


def _evidence(pt, nets, t_cut, q, w_old, old_case, min_speed=8.0):
    """Winner evidence of a rally from its shot chain.
    pt: {"end", "flights", "events"}; nets: the net events of the window; t_cut: winner_ball.T_CUT;
    q, w_old, old_case: the quantities, the decision and the case of the old rule (old_rule) for the same window.
    Returns {"nearWins": bool or None, "case", "shots", "last": index of the last shot or None}.
    Order of the facts of the last shot: net event; bounce event (on the side of the hitter, out, in);
    else the old rule (kink or fit landing). "back_": the old rule decided from the shot before the last
    shot of the chain (it did not use the last flight)."""
    shots = build(pt["flights"], pt.get("events"), nets, min_speed, audio=pt.get("audio"))
    li = last_shot(shots, pt["end"], t_cut)
    old = (lambda pre: (w_old, pre + old_case) if w_old is not None else (None, "none"))
    if li is None:
        # No shot starts before the time cut (a short rally). Then the cut has no sense: the last shot before the
        # end decides if it has a fact. Cases "late_...": too rare in the marked matches for a measured probability.
        lj = last_shot(shots, pt["end"], 0.0)
        kind = shot_end(shots[lj])[0] if lj is not None else "none"
        if kind != "none" and w_old is None:
            return {"nearWins": shots[lj]["near"] if kind == "in" else (not shots[lj]["near"]), "case": "late_" + kind, "shots": shots, "last": lj}
        w, case = old("old_")
        return {"nearWins": w, "case": case, "shots": shots, "last": None}
    sh = shots[li]
    kind, m = shot_end(sh)
    e = "_early" if sh["t"] - pt["end"] < EARLY else ""
    same = bool(q is not None and sh["fl"] and abs((pt["end"] + q["t_rel"]) - sh["fl"][0]["t0"]) < 1e-6)
    # G7 step 2b: no FIRST_DT override. The kink rule of the old rule does not remove the bounce event of the chain.
    if kind == "net":
        w, case = (not sh["near"]), "net_" + ("hi" if sh["net"]["dirConf"] >= 0.7 else "lo")
    elif kind == "own":
        w, case = (not sh["near"]), "own" + e
    elif kind == "out":
        w, case = (not sh["near"]), "out" + ("_line" if OUT_LINE and m > -LINE_M else "") + e      # E32: "out" near a line is its own case, as "in"
    elif kind == "in":
        w, case = sh["near"], "in" + ("_line" if m < LINE_M else "") + e
    elif NET_STOP and net_stop(sh, pt["end"] + t_cut):
        w, case = (not sh["near"]), "stop" + e
    else:
        w, case = old("old_")
        if not same:
            # The old rule did not use the last flight: its decision is about a shot before the last shot.
            # The chain facts of that shot have priority: "in" = the last shot is its return, and the end of the
            # return is not known. Net, out, own side = the ball after it is not a rally shot.
            prev = [x for x in shots[:li] if q is not None and x["fl"] and abs((pt["end"] + q["t_rel"]) - x["fl"][0]["t0"]) < 1e-6]
            pk = shot_end(prev[-1])[0] if prev else "none"
            if pk == "in":
                w, case = None, "none"
            elif pk in ("net", "out", "own"):
                w, case = (not prev[-1]["near"]), "back_stop"
            elif w is not None:                       # 2 cases only: the old rule says "in" or it says "net or out"
                case = "back_in" if old_case.startswith("in") else "back_stop"
    return {"nearWins": w, "case": case, "shots": shots, "last": li}


def _who(near):
    return "Near player" if near else "Far player"


def explain(r, t_end, t_cut):
    """One sentence group for the user: the facts of the last shot and what they mean for the winner."""
    shots, li, case, w = r["shots"], r["last"], r["case"], r["nearWins"]
    if li is None:
        return "No shot was tracked before the end of the rally." + ("" if w is None else " The fitted flight decides (old rule).")
    sh = shots[li]
    kind, m = shot_end(sh)
    b = first_bounce(sh)
    how = {"hit": "hit the ball", "back": "sent the ball back (the hit was not seen)", "gap": "hit the ball again (the return between was not seen)",
           "first": "hit the first tracked ball", "net": "hit the ball (only the net touch was seen)"}[sh["how"]]
    txt = f"{_who(sh['near'])} {how} at {sh['t']:.1f} s."
    win = "" if w is None else f" {_who(w)} wins the point."
    fact = kind if (case.startswith("late_") or case.split("_")[0] in ("net", "own", "out", "in")) else None
    if fact == "net":
        return txt + f" The ball touched the net at {sh['net']['t']:.1f} s and stayed on the side of the hitter." + win
    if fact == "own":
        return txt + f" The ball came down on the side of the hitter at {b[2]:.1f} s." + win
    if fact == "out":
        return txt + f" The ball bounced out at {b[2]:.1f} s ({-m:.1f} m outside)." + win
    if fact == "in":
        a = after_bounce(sh)
        line = " This is near a line." if m < LINE_M else ""
        seen = f" The ball was tracked for {a:.1f} s after the bounce, and no return was seen." if a >= 0.1 else " No return was seen."
        return txt + f" The ball bounced in at {b[2]:.1f} s ({m:.1f} m inside).{line}{seen}" + win
    if case.startswith("stop"):
        return txt + " No bounce event and no net event. The flight of the ball ends low near the net: the ball stopped at the net." + win
    if case == "back_stop":
        return txt + " The end of this shot was not seen. The shot before it went into the net or out, so this ball is not a rally shot." + win
    if case == "back_in":
        return txt + " The end of this shot was not seen. The fitted flight of the shot before it landed in." + win
    if kind in ("own", "out", "in") and case.startswith("old_"):
        return txt + " A bounce event exists, but the ball touched the ground before it. The first ground contact decides (old rule)." + win
    if case.startswith("old_"):
        what = "stopped at the net" if "net" in case else "landed in" if "_in" in case else "landed out"
        return txt + f" No bounce event and no net event. The fitted flight {what}." + win
    prev = [x for x in shots[:li] if x["fl"]]
    if prev and shot_end(prev[-1])[0] == "in" and prev[-1]["near"] != sh["near"]:
        pb = first_bounce(prev[-1])
        return txt + f" This is the return of the ball that bounced in at {pb[2]:.1f} s. The end of the return was not seen: no decision."
    return txt + " The end of this shot was not seen: no decision."


def export(r, t_end, t_cut, used, p=None):
    """The chain of a rally for the review window (JSON). Times in s of the video, places in m of the court
    (x across, y along: negative = near half). See docs/auto-analysis/app-integration.md."""
    li = r["last"]
    out = []
    for i, sh in enumerate(r["shots"]):
        kind, m = shot_end(sh)
        b = first_bounce(sh)
        s = 1.0 if sh["near"] else -1.0
        d = {"t": round(sh["t"], 3), "near": sh["near"], "how": sh["how"], "end": kind}
        if sh["fl"]:
            # The speed of the shot: the flight with the most samples before the first bounce. (Before: the first flight.
            # A shot can start with a short piece that has a bad fit, for example right after the contact of a serve.)
            pre = [f for f in sh["fl"] if b is None or f["t0"] < b[2]] or sh["fl"]
            d["kmh"] = round(kmh(max(pre, key=lambda f: f["n"])))
            d["tLast"] = round(sh["fl"][-1]["t1"], 3)
        if kind == "net":
            d["tEnd"], d["netConf"] = round(sh["net"]["t"], 3), sh["net"]["conf"]
        elif kind != "none":
            d["tEnd"], d["court"], d["margin"], d["sigma"] = round(b[2], 3), [round(b[0], 2), round(b[1] * s, 2)], round(m, 2), round(b[3], 2)
            d["after"] = round(after_bounce(sh), 2)
        else:
            pe = predicted_end(sh, r.get("events"))
            if pe is not None:
                d["predicted"] = pe
        if sh["weak"]:
            d["weak"] = True              # the 3D start of the flight is not plausible: only the direction is used
        if (sh["fl"][0]["t0"] if sh["fl"] else sh["t"]) >= t_end + t_cut:
            d["late"] = True              # after the time cut: ball handling after the point, not used
        out.append(d)
    return {"case": r["case"], "nearWins": r["nearWins"], "p": None if (p is None or r["nearWins"] is None) else round(p, 2), "used": bool(used),
            "last": li, "text": explain(r, t_end, t_cut), "shots": out}


def point_evidence(pt, nets, p, t_cut=None):
    """evidence() for a marked point (flights file of ball_3d.py with events), with the old rule (parameters p)."""
    import winner_ball as wb
    return evidence(pt, nets, wb.T_CUT if t_cut is None else t_cut, p, p["min_speed"])


EV_TAG = os.environ.get("EV_TAG", "0.35_ev")       # the flights files with events of the lab matches


def marked(mid):
    """The marked points of a lab match: list of (point, winnerNear, net events of its window)."""
    import lab
    pts = json.load(open(os.path.join(lab.mdir(mid), f"flights_s{EV_TAG}.json")))
    ends = json.load(open(os.path.join(lab.mdir(mid), "ends.json")))
    ne = json.load(open(os.path.join(lab.mdir(mid), "net_events.json")))["events"]
    return [(pt, e["winnerNear"], [x for x in ne if pt["start"] - 1.0 <= x["t"] <= pt["end"] + 2.5])
            for pt, e in zip(pts, ends) if e["winnerNear"] is not None]


_COUNTS = {}


def counts(mid):
    """{case: [decisions, right]} of the marked points of one lab match."""
    import match_decode as md
    if mid not in _COUNTS:
        c = {}
        for pt, y, nn in marked(mid):
            r = point_evidence(pt, nn, md.PARAMS[mid])
            if r["nearWins"] is not None:
                v = c.setdefault(r["case"], [0, 0])
                v[0] += 1; v[1] += int(r["nearWins"] == y)
        _COUNTS[mid] = c
    return _COUNTS[mid]


def fit_cases(mids):
    """Case table from the marked points of the given matches (2 pseudo counts at P_DEC for each case, as match_decode.fit_cases)."""
    from score_decode import P_DEC
    import lab
    lab.check_train(mids)
    tot = {}
    for mid in mids:
        for k, (n, r) in counts(mid).items():
            v = tot.setdefault(k, [0, 0]); v[0] += n; v[1] += r
    return {k: float(np.clip((r + 2 * P_DEC) / (n + 2), 0.5, 0.97)) for k, (n, r) in tot.items()}


def use_lomo_cases():
    global CASES_BY_MATCH
    import winner_ball as wb
    CASES_BY_MATCH = {m: fit_cases([x for x in wb.SINGLES if x != m]) for m in wb.SINGLES}


def case_prob(case, mid=None):
    """Probability that the decision of the case is right. A case that the table does not have: P_DEC."""
    from score_decode import P_DEC
    t = CASES_BY_MATCH[mid] if (CASES_BY_MATCH and mid in CASES_BY_MATCH) else CASE_P
    return t.get(case, P_DEC)


def main():
    import winner_ball as wb, match_decode as md
    use_lomo_cases()
    tot, allc = [0, 0, 0, 0.0], {}
    for mid in wb.SINGLES:
        h = d = n = 0
        hp = 0.0
        for pt, y, nn in marked(mid):
            r = point_evidence(pt, nn, md.PARAMS[mid])
            n += 1
            w = r["nearWins"]
            if w is not None and case_prob(r["case"], mid) <= 0.5:      # the other 3 matches say: this case is a coin
                w = None
            if w is not None:
                d += 1; h += int(w == y)
        for k, (cn, cr) in counts(mid).items():
            v = allc.setdefault(k, [0, 0]); v[0] += cn; v[1] += cr
        tot = [tot[0] + h, tot[1] + d, tot[2] + n]
        print(f"{mid}: decided {100 * d / n:.0f}%, correct of decided {100 * h / max(1, d):.1f}%, with coin {100 * (h + 0.5 * (n - d)) / n:.1f}%")
    h, d, n = tot[:3]
    print(f"ALL (chain, case tables leave one match out): decided {100 * d / n:.0f}%, correct of decided {100 * h / d:.1f}%, with coin {100 * (h + 0.5 * (n - d)) / n:.1f}%")
    t = fit_cases(wb.SINGLES)
    for k in sorted(allc):
        print(f"  {k:18s} decisions {allc[k][0]:4d}, right {allc[k][1]:4d} ({100 * allc[k][1] / allc[k][0]:5.1f}%), table {t[k]:.2f}")
    print("CASE_P =", json.dumps({k: round(v, 2) for k, v in sorted(t.items())}))


if __name__ == "__main__":
    main()

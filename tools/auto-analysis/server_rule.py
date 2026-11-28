"""A58 (S4, E224): the server of the result rows that have no server or the server end of a serve event against the
pose vote. A step after chain.winners (it changes only "server" and "serverNear" of the rows; no row is added or
removed). Default "1" (on: manager decision after E224; the check with CHAIN_START_CLAMP on gave the same values);
"0" gives the old way (CHAIN_SERVER_FILL).

In chain.evidence the server end is the pose vote (the max wrist_up of the near and the far player in the seconds
int(a) - 1 to int(a) + 2 of the rally start a, a vote when it is VOTE_MIN or more), then the end of the first serve
event from a - 1 to a + 2.5 s: the serve event wins. E224 (the E217 rows): all rows with no server (lab 28, Igor 20)
have no vote and no serve event; most of them have a far server with a weak far vote.
The steps (the server player: "P1" when the server end is the end of P1 in that row, "p1Near"):
  1 vote   a row with a server end from a serve event and a pose vote of the other end gets the end of the vote.
           E224: in the rows with both and a disagreement, the vote is right 12 times, the event 1 time.
  2 game   a row with no server gets the server player of the nearest row before it and the nearest row after it
           that have a server, when the two are the same player (a game has 4 points or more, with one server).
           E224 (leave one out on the rows with a server): right 444 times, wrong 4 times.
  3 weak   a row with no server after step 2 gets the end with the larger wrist_up max (no VOTE_MIN), when both
           players have a value. E224: right for 40 of 48 rows with no server.
"""
import os
import numpy as np

FILL = os.environ.get("CHAIN_SERVER_FILL", "1") != "0"
VOTE_MIN = 0.5


def vote(pose, a):
    """(near max, far max) of the wrist_up of the near and the far player (server_end.near_far) in the seconds
    int(a) - 1 to int(a) + 2, as chain.evidence; None for a player with no value."""
    from server_end import near_far, wrist_up
    nu, fu = [], []
    for s in range(max(0, int(a) - 1), min(len(pose), int(a) + 3)):
        r, fr = near_far(pose[s])
        for row, up in ((r, nu), (fr, fu)):
            if row is not None:
                up.append(wrist_up(row))
    best = [float(np.nanmax(v)) if np.isfinite(v).any() else None for v in (nu, fu)]
    return best[0], best[1]


def _player(near, p1_near):
    return "P1" if bool(near) == bool(p1_near) else "P2"


def fill(rows, votes):
    """Steps 1 to 3 on the result rows (in time order; "server", "serverNear", "p1Near"). votes: (near max, far max)
    of each row. A changed row gets "serverFrom" ("vote", "game" or "weak"). Returns rows (changed in place)."""
    for r, (nv, fv) in zip(rows, votes):
        if r.get("serverNear") is None or r.get("p1Near") is None:
            continue
        top = max(-9.0 if nv is None else nv, -9.0 if fv is None else fv)
        if top < VOTE_MIN:
            continue
        end = (-9.0 if nv is None else nv) > (-9.0 if fv is None else fv)
        if end != bool(r["serverNear"]):
            r["serverNear"], r["server"], r["serverFrom"] = end, _player(end, r["p1Near"]), "vote"
    known = [r.get("server") for r in rows]
    for i, r in enumerate(rows):
        if r.get("server") is not None or r.get("p1Near") is None:
            continue
        before = next((known[k] for k in range(i - 1, -1, -1) if known[k] is not None), None)
        after = next((known[k] for k in range(i + 1, len(rows)) if known[k] is not None), None)
        if before is not None and before == after:
            r["server"], r["serverFrom"] = before, "game"
            r["serverNear"] = (before == "P1") == bool(r["p1Near"])
            continue
        nv, fv = votes[i]
        if nv is not None and fv is not None and nv != fv:
            end = nv > fv
            r["serverNear"], r["server"], r["serverFrom"] = end, _player(end, r["p1Near"]), "weak"
    return rows


def apply(mid, rows):
    """fill() with the pose votes of the rows of match mid (the poses of each second, lab.pose_by_second)."""
    import lab
    pose = lab.pose_by_second(lab.mdir(mid), lab.n_seconds(mid))
    return fill(rows, [vote(pose, r["start"]) for r in rows])

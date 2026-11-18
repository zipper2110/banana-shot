"""Replay the score of each marked match as the app does, and write sides.json.

This is a port of org.litvin.scoring.ScoringEngine (Kotlin): the same rules, the same
server marks, the same score overrides and manual wins. The app does not model the
change of ends, so the ends come from the tennis rules (see ends_swaps).

Output: <lab data>/<match id>/sides.json, one entry for each point in time order.
Run: python sides.py [match id ...]
"""
import json, os, sys

from lab import LABELED, mdir

ENDLESS = 2 ** 31 - 1          # ENDLESS_TIEBREAK_TARGET
STAR_POINT_SCORE = 5
MATCH_TIEBREAK_POINTS = 10
NO_MATCH_END = 0
PLAYER = {"P1": 1, "P2": 2}     # Outcome.playerNumber(); "NONE" gives None


def other(p):
    return 3 - p


def normalized(r):
    """MatchRulesV1 with defaults, then MatchRulesV1.normalized()."""
    d = {"manualScoring": False, "structure": "SETS", "bestOfSets": 3, "gamesPerSet": 6,
         "setTiebreak": True, "earlyTiebreak": False, "tiebreakPoints": 7, "finalSet": "FULL_SET",
         "deuce": "ADVANTAGE", "totalPoints": 24, "serveTurnPoints": 4}
    d.update(r or {})
    clamp = lambda v, a, b: max(a, min(b, v))
    if d["bestOfSets"] not in (1, 3, 5, NO_MATCH_END):
        d["bestOfSets"] = 3
    d["gamesPerSet"] = clamp(d["gamesPerSet"], 1, 12)
    d["earlyTiebreak"] = d["earlyTiebreak"] and d["setTiebreak"] and d["gamesPerSet"] > 1
    d["tiebreakPoints"] = clamp(d["tiebreakPoints"], 3, 21)
    d["totalPoints"] = clamp(d["totalPoints"], 2, 99)
    d["serveTurnPoints"] = clamp(d["serveTurnPoints"], 1, 8)
    return d


class Match:
    """Port of ScoringEngine.Match: the mutable match score."""

    def __init__(self, rules):
        self.r = rules
        self.p1 = self.p2 = 0
        self.g1 = self.g2 = 0
        self.s1 = self.s2 = 0
        self.tb = False
        self.tb_target = rules["tiebreakPoints"]
        self.tb_replaces_set = False
        self.game_server = 1
        self.ended = False
        self.match_won = None
        self.game_won = None
        self.set_won = None
        self.sets = []          # completed sets: (p1, p2, tiebreak, tbP1, tbP2)
        if not rules["manualScoring"]:
            if rules["structure"] == "SINGLE_TIEBREAK":
                self.start_tiebreak(rules["tiebreakPoints"], True)
            elif rules["structure"] in ("PLAIN_POINTS", "TOTAL_POINTS"):
                self.start_tiebreak(ENDLESS, True)

    def sets_to_win(self):
        return self.r["bestOfSets"] // 2 + 1 if self.r["bestOfSets"] != NO_MATCH_END else ENDLESS

    def has_mtb_decider(self):
        return self.r["finalSet"] == "MATCH_TIEBREAK" and self.r["bestOfSets"] > 1

    def tb_games(self):
        return self.r["gamesPerSet"] - 1 if self.r["earlyTiebreak"] else self.r["gamesPerSet"]

    def total_points_mode(self):
        return self.r["structure"] == "TOTAL_POINTS" and not self.r["manualScoring"]

    def start_point(self):
        self.game_won = None
        self.set_won = None

    def point_won_by(self, p):
        if self.ended:
            return
        if p == 1:
            self.p1 += 1
        else:
            self.p2 += 1
        if self.r["manualScoring"]:
            return
        if self.r["structure"] == "TOTAL_POINTS":
            self.check_total_points()
        elif self.tb:
            self.check_tiebreak()
        else:
            self.check_game()

    def mark_game_won(self, p):
        if p == 1:
            self.g1 += 1
        else:
            self.g2 += 1
        self.p1 = self.p2 = 0
        self.game_won = p
        self.game_server = other(self.game_server)

    def receiver_serves(self):
        if self.total_points_mode():
            return ((self.p1 + self.p2) // self.r["serveTurnPoints"]) % 2 == 1
        if self.tb:
            return ((self.p1 + self.p2 + 1) // 2) % 2 == 1
        return False

    def server(self):
        return other(self.game_server) if self.receiver_serves() else self.game_server

    def set_server(self, p):
        self.game_server = other(p) if self.receiver_serves() else p

    def apply_override(self, o):
        sets, games, points = o.get("sets"), o.get("games"), o.get("points")
        if sets is not None:
            self.sets = []
            for s in sets:
                tb = s.get("tiebreakP1") is not None and s.get("tiebreakP2") is not None
                self.sets.append((s.get("p1", 0), s.get("p2", 0), tb, s.get("tiebreakP1") or 0, s.get("tiebreakP2") or 0))
            self.s1 = sum(1 for s in self.sets if s[0] > s[1])
            self.s2 = sum(1 for s in self.sets if s[1] > s[0])
        if games is not None:
            self.g1 = max(0, games.get("p1", 0))
            self.g2 = max(0, games.get("p2", 0))
        if sets is not None or games is not None:
            self.refresh_phase()
        if points is not None:
            self.p1 = max(0, points.get("p1", 0))
            self.p2 = max(0, points.get("p2", 0))
            if self.total_points_mode():
                self.ended, self.match_won = False, None
                self.check_total_points()

    def refresh_phase(self):
        if self.r["manualScoring"] or self.r["structure"] != "SETS":
            return
        stw = self.sets_to_win()
        self.ended = max(self.s1, self.s2) >= stw
        self.match_won = None if not self.ended else (1 if self.s1 > self.s2 else 2)
        if self.ended:
            self.tb = False
        elif self.has_mtb_decider() and self.s1 == stw - 1 and self.s2 == stw - 1:
            self.tb, self.tb_target, self.tb_replaces_set = True, MATCH_TIEBREAK_POINTS, True
        elif self.r["setTiebreak"] and self.g1 == self.tb_games() and self.g2 == self.tb_games():
            self.tb, self.tb_target, self.tb_replaces_set = True, self.r["tiebreakPoints"], False
        else:
            self.tb = False

    def mark_set_won(self, p):
        self.complete_set(p, (self.g1, self.g2, False, 0, 0))

    def check_game(self):
        lead, top = abs(self.p1 - self.p2), max(self.p1, self.p2)
        deuce = self.r["deuce"]
        if deuce == "ADVANTAGE":
            won = top >= 4 and lead >= 2
        elif deuce == "NO_AD":
            won = top >= 4
        else:
            won = top >= 4 and (lead >= 2 or (lead == 1 and min(self.p1, self.p2) >= STAR_POINT_SCORE))
        if won:
            self.win_game(1 if self.p1 > self.p2 else 2)

    def check_total_points(self):
        if self.p1 + self.p2 < self.r["totalPoints"]:
            return
        self.ended = True
        self.match_won = 1 if self.p1 > self.p2 else 2 if self.p2 > self.p1 else None

    def win_game(self, p):
        self.mark_game_won(p)
        if self.r["structure"] != "SETS":
            return
        lead, top = abs(self.g1 - self.g2), max(self.g1, self.g2)
        if top >= self.r["gamesPerSet"] and lead >= 2:
            self.complete_set(p, (self.g1, self.g2, False, 0, 0))
        elif self.r["setTiebreak"] and self.g1 == self.tb_games() and self.g2 == self.tb_games():
            self.start_tiebreak(self.r["tiebreakPoints"], False)

    def check_tiebreak(self):
        if max(self.p1, self.p2) < self.tb_target or abs(self.p1 - self.p2) < 2:
            return
        p = 1 if self.p1 > self.p2 else 2
        t1, t2 = self.p1, self.p2
        if self.tb_replaces_set:
            self.game_server = other(self.game_server)
            self.complete_set(p, (1, 0, True, t1, t2) if p == 1 else (0, 1, True, t1, t2))
        else:
            self.mark_game_won(p)
            self.complete_set(p, (self.g1, self.g2, True, t1, t2))

    def complete_set(self, p, score):
        self.sets.append(score)
        if p == 1:
            self.s1 += 1
        else:
            self.s2 += 1
        self.g1 = self.g2 = 0
        self.p1 = self.p2 = 0
        self.tb = False
        self.set_won = p
        if self.r["manualScoring"]:
            return
        stw = 1 if self.r["structure"] == "SINGLE_TIEBREAK" else self.sets_to_win()
        if max(self.s1, self.s2) >= stw:
            self.match_won, self.ended = p, True
        elif self.has_mtb_decider() and self.s1 == stw - 1 and self.s2 == stw - 1:
            self.start_tiebreak(MATCH_TIEBREAK_POINTS, True)

    def start_tiebreak(self, target, replaces_set):
        self.tb, self.tb_target, self.tb_replaces_set = True, target, replaces_set
        self.p1 = self.p2 = 0

    def snapshot(self):
        return {"p1": self.p1, "p2": self.p2, "g1": self.g1, "g2": self.g2, "s1": self.s1, "s2": self.s2,
                "tb": self.tb, "ended": self.ended, "sets": list(self.sets),
                "gameWon": self.game_won, "setWon": self.set_won}


def points_label(mine, oth):
    """ScoreboardDisplay.pointsLabel."""
    if mine < 4 and oth < 4:
        return ["0", "15", "30", "40"][max(0, min(3, mine))]
    if mine == oth:
        return "40"
    return "Ad" if mine > oth else "40"


def ends_swaps(st):
    """Count of end changes before the point with the score st (the app has no ends).
    Tennis rules: change after each odd game of a set (the set end counts with the next
    set: an odd set total gives a change), and in a tiebreak after each 6 points.
    A tiebreak counts as one game. The count comes from the score, so an override
    that skips games moves the ends too."""
    n = 0
    for p1, p2, tb, t1, t2 in st["sets"]:
        n += (p1 + p2 + 1) // 2
        if tb:
            n += max(0, t1 + t2 - 1) // 6
    n += (st["g1"] + st["g2"] + 1) // 2
    if st["tb"]:
        n += (st["p1"] + st["p2"]) // 6
    return n


def replay(gt):
    """Port of ScoringEngine.timeline. Returns (entries, notes)."""
    pts, sc = gt["points"], gt["score"]
    rules = normalized(sc.get("rules"))
    marks = sc.get("serverMarks") or {}
    overrides = sc.get("scoreOverrides") or {}
    gwins, swins = sc.get("manualGameWins") or {}, sc.get("manualSetWins") or {}
    m = Match(rules)
    notes, servers, before, after, ovr_at = [], [], [], [], []
    marked = False
    for i, pt in enumerate(pts):
        m.start_point()
        mark = PLAYER.get(marks.get(pt["id"]))
        if mark is not None:
            computed = m.server()
            if not marked and computed != mark:
                servers = [other(s) for s in servers]
            elif marked and computed != mark:
                notes.append("point %d (%.1f s): server mark P%d, computed P%d" % (i, pt["startMs"] / 1000, mark, computed))
            marked = True
            m.set_server(mark)
        servers.append(m.server())
        before.append(m.snapshot())
        outcome = PLAYER.get(pt.get("outcome"))   # NONE or missing keeps the score
        if outcome is not None:
            m.point_won_by(outcome)
        manual = False
        if rules["manualScoring"]:
            if PLAYER.get(gwins.get(pt["id"])):
                m.mark_game_won(PLAYER[gwins[pt["id"]]]); manual = True
            if PLAYER.get(swins.get(pt["id"])):
                m.mark_set_won(PLAYER[swins[pt["id"]]]); manual = True
        o = overrides.get(pt["id"])
        if o:
            m.apply_override(o)
        ovr_at.append(bool(o) or manual)
        after.append(m.snapshot())
    if not marked:
        servers = [None] * len(pts)
        notes.append("no server mark: the server is unknown")

    entries, game, pig = [], 0, 0
    for i, pt in enumerate(pts):
        b, a = before[i], after[i]
        key_b = (len(b["sets"]), b["s1"], b["s2"], b["g1"], b["g2"])
        key_a = (len(a["sets"]), a["s1"], a["s2"], a["g1"], a["g2"])
        # A new game starts after a point that changes the sets or the games.
        new_game = key_a != key_b
        winner = a["gameWon"] or a["setWon"]
        if new_game and winner is None:
            # An override or a manual win ended the game. Infer the winner from the games.
            d1, d2 = a["g1"] - b["g1"], a["g2"] - b["g2"]
            winner = 1 if (d1, d2) == (1, 0) else 2 if (d1, d2) == (0, 1) else None
        if ovr_at[i]:
            notes.append("point %d (%.1f s, %s): override %d-%d %d-%d -> %d-%d %d-%d (engine: %s)" % (
                i, pt["startMs"] / 1000, pt.get("outcome"), b["g1"], b["g2"], b["p1"], b["p2"],
                a["g1"], a["g2"], a["p1"], a["p2"],
                "game won by P%d" % a["gameWon"] if a["gameWon"] else "no game end"))
        if b["tb"]:
            pl = [str(b["p1"]), str(b["p2"])]
        else:
            pl = [points_label(b["p1"], b["p2"]), points_label(b["p2"], b["p1"])]
        entries.append({
            "id": pt["id"], "startMs": pt["startMs"], "outcome": pt.get("outcome", "NONE"),
            "set": len(b["sets"]), "game": game, "pointInGame": pig, "isTiebreak": b["tb"],
            "scoreBefore": {"sets": [b["s1"], b["s2"]], "games": [b["g1"], b["g2"]], "points": pl,
                            "rawPoints": [b["p1"], b["p2"]],
                            "completedSets": [[s[0], s[1]] for s in b["sets"]]},
            "server": None if servers[i] is None else "P%d" % servers[i],
            "serveSide": "deuce" if (b["p1"] + b["p2"]) % 2 == 0 else "ad",
            "endsSwapped": ends_swaps(b) % 2 == 1,
            "gameWinner": ("P%d" % winner) if new_game and winner else None,
            "matchEnded": b["ended"],
            "_override": ovr_at[i], "_newGame": bool(new_game),
        })
        if new_game:
            game, pig = game + 1, 0
        else:
            pig += 1
    return entries, notes, after[-1] if after else None


def self_check(entries):
    """Assert basic invariants. Returns a list of soft findings."""
    findings = []
    games = {}
    for e in entries:
        games.setdefault(e["game"], []).append(e)
    last = max(games) if games else -1
    for g, es in games.items():
        counted = [e for e in es if e["outcome"] != "NONE" and not e["matchEnded"]]
        touched = any(e["_override"] for e in es)
        # The serve side alternates over the counted points (a NONE point is replayed from the same side).
        for x, y in zip(counted, counted[1:]):
            if x["_override"]:
                continue
            assert x["serveSide"] != y["serveSide"], "game %d: serve side does not alternate at %s" % (g, y["id"])
        # The server stays the same inside a normal game.
        if not es[0]["isTiebreak"]:
            assert len({e["server"] for e in es}) == 1, "game %d: server changes inside a game" % g
        ended = es[-1]["gameWinner"] is not None
        starts_at_zero = es[0]["scoreBefore"]["rawPoints"] == [0, 0]
        if ended and not touched and starts_at_zero and g != 0:
            assert len(counted) >= 4, "game %d: only %d counted points" % (g, len(counted))
        elif ended and len(counted) < 4:
            findings.append("game %d has %d counted points (override, first game or late start)" % (g, len(counted)))
        if not ended and g != last:
            findings.append("game %d has no winner" % g)
    assert entries[0]["endsSwapped"] is False or entries[0]["scoreBefore"]["games"] != [0, 0]
    return findings


def main(ids):
    for mid in ids:
        gt = json.load(open(os.path.join(mdir(mid), "gt.json"), encoding="utf-8"))
        entries, notes, final = replay(gt)
        notes += self_check(entries)
        out = [{k: v for k, v in e.items() if not k.startswith("_")} for e in entries]
        with open(os.path.join(mdir(mid), "sides.json"), "w", encoding="utf-8") as f:
            json.dump(out, f, indent=1)
        sets = " ".join("%d-%d%s" % (s[0], s[1], "(%d-%d)" % (s[3], s[4]) if s[2] else "") for s in final["sets"])
        cur = "" if final["ended"] else " | now %d-%d %s" % (final["g1"], final["g2"], "tb " if final["tb"] else "") + \
            "%d-%d" % (final["p1"], final["p2"])
        unknown = sum(1 for e in out if e["server"] is None)
        print("%s  %s  sets %s%s%s  games %d  points %d  unknown server %d" % (
            mid, gt["score"].get("sport", "TENNIS"), sets or "-", cur, " (ended)" if final["ended"] else "",
            out[-1]["game"] + (0 if entries[-1]["_newGame"] else 1), len(out), unknown))
        for n in notes:
            print("   ", n)


if __name__ == "__main__":
    main(sys.argv[1:] or LABELED)

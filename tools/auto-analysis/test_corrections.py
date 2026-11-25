"""Tests of corrections.py (chain-inventory G11). Usage: python -m unittest test_corrections"""
import io, json, os, tempfile, unittest
from contextlib import redirect_stdout
import corrections


def rally(start, end, winner=None, conf=None, p1_near=True):
    return {"start": start, "end": end, "kind": "rally", "prob": 0.9, "winner": winner, "confidence": conf, "p1Near": p1_near}


def project(rallies, rows, points, outcomes, settings=None, swapped=False, existing=None, notes=None, rng=None):
    d = tempfile.mkdtemp(prefix="corr_")
    ad = os.path.join(d, "autodetect")
    os.makedirs(ad)
    w = lambda p, o: json.dump(o, open(p, "w", encoding="utf-8"))
    w(os.path.join(ad, "result-1.json"), {"version": 2, "videoSeconds": 600, "rallies": rallies})
    rec = {"created": "1", "resultFile": "result-1.json", "rows": rows, "swapped": swapped,
           "settings": {"padBeforeMs": 0, "padAfterMs": 0, "addWinners": True, "minWinnerPercent": 50, **(settings or {})}}
    if existing is not None:
        rec["existingPointIds"] = existing
    if rng:
        rec["rangeStartMs"], rec["rangeEndMs"] = rng
    w(os.path.join(ad, "import-1.json"), rec)
    if notes:
        w(os.path.join(ad, "review-notes.json"), {"version": 1, "notes": notes})
    w(os.path.join(d, "edl.json"), {"points": points})
    w(os.path.join(d, "score.json"), {"outcomes": outcomes})
    return d


def auto(pid, a, b, checked=True, near=1):
    return {"id": pid, "startMs": a, "endMs": b, "auto": {"kind": "RALLY", "checked": checked, "nearPlayer": near}}


def run(d):
    with redirect_stdout(io.StringIO()):
        out = corrections.main(d)
    return {r.get("rally"): r for r in out["rows"]}, out


class Corrections(unittest.TestCase):
    def test_swapped_import_and_swap_players(self):
        # Import with swapped players: the detected P1 is P2 of the project. The user changed nothing on rally 0.
        # On rally 1 the user clicked "Swap players": the near player and the winner changed together.
        rs = [rally(10, 20, "P1", 0.9), rally(30, 40, "P1", 0.9)]
        rows = [{"rally": 0, "pointId": "a"}, {"rally": 1, "pointId": "b"}]
        pts = [auto("a", 10000, 20000, near=2), auto("b", 30000, 40000, near=1)]    # p1Near and swapped: near = 2
        rows, _ = run(project(rs, rows, pts, {"a": "P2", "b": "P1"}, swapped=True))
        self.assertEqual(rows[0]["winner"], "P2")
        self.assertEqual(rows[0]["winnerVerdict"], "same")
        self.assertTrue(rows[1]["playersSwapped"])
        self.assertEqual(rows[1]["winnerVerdict"], "swap")

    def test_under_the_limit_is_not_a_change_and_unchecked_is_not_evidence(self):
        rs = [rally(10, 20, "P1", 0.6), rally(30, 40, "P1", 0.9), rally(50, 60, "P2", 0.9)]
        rows = [{"rally": i, "pointId": p} for i, p in enumerate("abc")]
        pts = [auto("a", 10000, 20000), auto("b", 30000, 40000, checked=False), auto("c", 50000, 60000)]
        rows, _ = run(project(rs, rows, pts, {"a": "P1", "b": "P2", "c": "P1"}, settings={"minWinnerPercent": 70}))
        self.assertIsNone(rows[0]["winner"])                       # not set by the import
        self.assertNotIn("winnerVerdict", rows[0])
        self.assertTrue(rows[0]["winnerUnderLimitRight"])          # the detection was right under the limit
        self.assertFalse(rows[1]["checked"])                       # changed, but not reviewed: not in the counts
        self.assertEqual(rows[2]["winnerVerdict"], "wrong")

    def test_bounds_use_the_gap_clamp_and_the_rounding(self):
        # Pad 2 s after the end, but the next rally starts 1 s later: the import stopped at the middle of the gap.
        rs = [rally(10.0, 20.0), rally(21.0, 30.0041)]
        rows = [{"rally": 0, "pointId": "a"}, {"rally": 1, "pointId": "b"}]
        pts = [auto("a", 10000, 20500), auto("b", 21000, 30000)]
        rows, _ = run(project(rs, rows, pts, {}, settings={"padAfterMs": 2000}))
        self.assertEqual(rows[0]["dEnd"], 0.0)                     # the old script gave -1.5 s
        self.assertEqual(rows[1]["dEnd"], -2.0)                    # 30.004 s + 2 s, rounded to 32.000 s

    def test_added_counts_only_new_clips_inside_the_trim_and_notes_are_read(self):
        rs = [rally(100, 110)]
        rows = [{"rally": 0, "pointId": "a"}]
        pts = [auto("a", 100000, 110000), {"id": "old", "startMs": 5000, "endMs": 9000},
               {"id": "new", "startMs": 200000, "endMs": 210000}, {"id": "out", "startMs": 900000, "endMs": 905000}]
        notes = [{"pointId": "a", "text": "late end", "resultFile": "result-1.json"}]
        rows, out = run(project(rs, rows, pts, {}, existing=["old"], rng=(0, 600000), notes=notes))
        self.assertEqual([a["id"] for a in out["added"]], ["new"])
        self.assertEqual(rows[0]["note"], "late end")
        self.assertEqual(out["notes"][0]["text"], "late end")


if __name__ == "__main__":
    unittest.main()

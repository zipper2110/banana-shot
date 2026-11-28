"""Tests of mvp_measure.py (A56, E217): MVP measures 3 (bounds), 5 (server) and 6 (double faults).
Usage: python -m unittest test_mvp_measure"""
import unittest
import mvp_measure as mm

PTS = [(10.0, 20.0, "P1"), (30.0, 40.0, "P2"), (50.0, 55.0, "NONE"), (60.0, 64.0, "P2"), (80.0, 90.0, "P1")]


def row(a, b, server=None, near=None):
    return {"start": a, "end": b, "server": server, "serverNear": near}


class Measure(unittest.TestCase):
    def test_bounds(self):
        rows = [row(10.9, 21.9), row(28.5, 40.0), row(50.0, 55.0), row(60.0, 66.5)]
        b = mm.measure(PTS, [None] * 5, [None] * 5, [None] * 5, rows)["bounds"]
        self.assertEqual((b["n"], b["ok"], b["miss"], b["startMiss"], b["endMiss"]), (3, 1, 2, 1, 1))   # the NONE mark does not count
        # the worst first: start -1.5 s (1.5 times the limit), then end +2.5 s (1.25 times the limit)
        self.assertEqual([e["mark"] for e in b["bad"]], [[30.0, 40.0], [60.0, 64.0]])
        self.assertEqual([e["score"] for e in b["bad"]], [1.5, 1.25])

    def test_worst_order(self):
        rows = [row(10.0, 26.0), row(31.2, 40.0)]
        b = mm.measure(PTS, [None] * 5, [None] * 5, [None] * 5, rows)["bounds"]
        self.assertEqual([e["score"] for e in b["bad"]], [3.0, 1.2])

    def test_server_with_one_naming(self):
        rows = [row(10, 20, "P2", True), row(30, 40, "P1", False), row(60, 64, None, None), row(80, 90, "P1", True)]
        srv = ["P1", "P2", None, "P2", "P1"]
        ends = [True, False, None, True, False]
        s = mm.measure(PTS, [None] * 5, srv, ends, rows)["server"]
        self.assertEqual((s["n"], s["ok"], s["names"], s["none"], s["miss"]), (4, 2, "swapped", 1, 2))
        self.assertEqual((s["endN"], s["endOk"]), (4, 2))

    def test_double_faults(self):
        endings = ["WINNER", "SERVE_FAULT", None, "SERVE_FAULT", "ACE"]
        d = mm.measure(PTS, endings, [None] * 5, [None] * 5, [row(30, 40)])["doubleFaults"]
        self.assertEqual((d["n"], d["ok"], d["miss"], d["missed"]), (2, 1, 1, [[60.0, 64.0]]))
        self.assertIsNone(mm.measure(PTS, [None] * 5, [None] * 5, [None] * 5, [])["doubleFaults"])     # no endings marked

    def test_total(self):
        a = mm.measure(PTS, ["SERVE_FAULT"] + [None] * 4, ["P1"] * 5, [True] * 5, [row(10, 20, "P1", True), row(30, 45, "P1", True)])
        b = mm.measure(PTS, [None] * 5, ["P1"] * 5, [True] * 5, [row(80, 90, "P1", False)])
        t = mm.total({"a": a, "b": b})
        self.assertEqual((t["bounds"]["n"], t["bounds"]["ok"]), (3, 2))
        self.assertEqual((t["server"]["n"], t["server"]["ok"], t["server"]["endN"]), (3, 3, 3))
        self.assertAlmostEqual(t["server"]["endShare"], 2 / 3)
        self.assertEqual((t["doubleFaults"]["n"], t["doubleFaults"]["ok"], t["doubleFaults"]["met"]), (1, 1, True))
        self.assertEqual([e["match"] for e in t["worstBounds"]], ["a"])
        self.assertFalse(t["bounds"]["met"])


if __name__ == "__main__":
    unittest.main()

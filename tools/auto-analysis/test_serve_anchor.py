"""Tests of the serve-anchored rally of A46 (chain.serve_anchor_segs, CHAIN_SERVE_ANCHOR).
Usage: python -m unittest test_serve_anchor"""
import os, unittest
import numpy as np
import chain


def ev(t, near=True, kind="serve"):
    return (t, near, kind, {"p0": [1.0, 11.0 if near else -11.0, 2.5]})


def row(a, b):
    return {"start": a, "end": b}


def flights(*t):
    # the court end changes from flight to flight (a rally): the sign of the court y of the start
    return [{"t0": x, "t1": x + 0.8, "p0": [0.0, (11.0 if i % 2 == 0 else -11.0), 1.0]} for i, x in enumerate(t)]


class ServeAnchor(unittest.TestCase):
    def setUp(self):
        self.prob = np.zeros(300, np.float32)
        self.prob[100:107] = 0.35                      # the GRU is silent on the point, but above the floor
        self.rows = [row(50, 60), row(200, 210)]
        self.fl = flights(100.0, 101.2, 102.5, 104.0)

    def segs(self, serves=None, rows=None, prob=None, fl=None):
        return chain.serve_anchor_segs(rows or self.rows, serves or [ev(100.0)], fl or self.fl,
                                       self.prob if prob is None else prob)

    def test_on_by_default(self):
        # manager decision after E211; CHAIN_SERVE_ANCHOR=0 gives the old way
        if "CHAIN_SERVE_ANCHOR" not in os.environ:
            self.assertTrue(chain.SERVE_ANCHOR)

    def test_a_serve_with_play_gives_a_new_rally(self):
        got = self.segs()
        self.assertEqual(len(got), 1)
        a, b, p = got[0]
        self.assertAlmostEqual(a, 100.0 - chain.SS_BEFORE)
        self.assertAlmostEqual(b, 104.8 + chain.SS_AFTER)
        self.assertAlmostEqual(p, 0.35, places=3)

    def test_the_filter_drops_play_that_runs_into_the_next_rally(self):
        nxt = 104.8 + chain.SS_AFTER + chain.SA_PAUSE - 0.2
        self.assertEqual(self.segs(rows=[row(50, 60), row(nxt, nxt + 8)]), [])
        nxt += 0.4
        self.assertEqual(len(self.segs(rows=[row(50, 60), row(nxt, nxt + 8)])), 1)

    def test_a_rally_near_the_serve_gives_nothing(self):
        self.assertEqual(self.segs(rows=[row(85, 100.0 - chain.SA_AFTER + 0.5), row(200, 210)]), [])
        self.assertEqual(self.segs(serves=[ev(100.0)], rows=[row(100.0 + chain.SA_BEFORE - 0.5, 140), row(200, 210)]), [])

    def test_the_gru_above_the_threshold_or_below_the_floor_gives_nothing(self):
        p = self.prob.copy()
        p[102] = 0.6
        self.assertEqual(self.segs(prob=p), [])
        self.assertEqual(self.segs(prob=np.zeros(300, np.float32) + 0.1), [])

    def test_too_few_flights_no_end_change_or_a_toss_gives_nothing(self):
        self.assertEqual(self.segs(fl=flights(100.0, 101.2)), [])
        same = [dict(f, p0=[0.0, 11.0, 1.0]) for f in self.fl]
        self.assertEqual(self.segs(fl=same), [])
        self.assertEqual(self.segs(serves=[ev(100.0, kind="toss")]), [])


if __name__ == "__main__":
    unittest.main()

"""Tests of the second-serve rally of A38 (chain.second_serve_segs, CHAIN_SECOND_SERVE).
Usage: python -m unittest test_second_serve"""
import unittest
import numpy as np
import chain


def ev(t, near, x, kind="serve"):
    return (t, near, kind, {"p0": [x, 11.0 if near else -11.0, 2.5], "v0": [0.0, 20.0, 0.0]})


def row(a, b, shots=1):
    return {"start": a, "end": b, "chain": {"shots": [{"t": a + 1.0}] * shots}}


def flights(*t):
    return [{"t0": x, "t1": x + 0.8} for x in t]


class SecondServe(unittest.TestCase):
    def setUp(self):
        self.prob = np.zeros(200, np.float32)
        self.prob[100:106] = 1.0                       # the GRU fires on the fault serve only
        self.rows = [row(100, 106), row(160, 170, 5)]
        self.fl = flights(101.0, 111.0, 112.0, 113.5, 130.0)

    def segs(self, serves, rows=None, prob=None):
        return chain.second_serve_segs(rows or self.rows, serves, self.fl, self.prob if prob is None else prob)

    def test_off_by_default(self):
        self.assertFalse(chain.SECOND_SERVE)

    def test_same_server_and_side_gives_a_new_rally(self):
        got = self.segs([ev(101.0, True, 2.0), ev(111.0, True, 2.5)])
        self.assertEqual(len(got), 1)
        a, b, p = got[0]
        self.assertAlmostEqual(a, 111.0 - chain.SS_BEFORE)
        self.assertAlmostEqual(b, 113.5 + 0.8 + chain.SS_AFTER)     # the last flight of the group after the serve
        self.assertEqual(p, 0.0)

    def test_other_side_or_server_gives_nothing(self):
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, True, -2.5)]), [])     # side changed: a point
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, False, 2.5)]), [])     # the other player serves

    def test_side_not_known_gives_nothing(self):
        self.assertEqual(self.segs([ev(101.0, True, 0.1), ev(111.0, True, 2.5)]), [])
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, True, 0.1)]), [])

    def test_long_rally_late_serve_or_a_toss_gives_nothing(self):
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, True, 2.5)], rows=[row(100, 106, 2), row(160, 170, 5)]), [])
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(106.0 + chain.SS_NEXT + 0.5, True, 2.5)]), [])
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, True, 2.5, "toss")]), [])

    def test_gru_or_a_rally_at_the_serve_gives_nothing(self):
        p = self.prob.copy()
        p[112] = 0.6
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, True, 2.5)], prob=p), [])
        self.assertEqual(self.segs([ev(101.0, True, 2.0), ev(111.0, True, 2.5)], rows=[row(100, 106), row(112, 120, 5)]), [])

    def test_the_new_rally_starts_after_the_first_one(self):
        got = self.segs([ev(101.0, True, 2.0), ev(106.8, True, 2.5)])
        self.assertEqual(len(got), 1)
        self.assertAlmostEqual(got[0][0], 106.5)


if __name__ == "__main__":
    unittest.main()

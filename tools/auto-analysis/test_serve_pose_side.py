"""Tests of A55 (E216): the side of a rally for FAULT_SIDE from the pose feet x of the server at the serve time, then
the serve flight x (chain.serve_pose_side, chain.row_side, CHAIN_SERVE_POSE_SIDE).
Usage: python -m unittest test_serve_pose_side"""
import os, unittest
import numpy as np
import chain


def ev(t, near=False, x=2.0, kind="serve"):
    return (t, near, kind, {"p0": [x, -11.0 if near else 11.0, 2.5], "v0": [0.0, 20.0, 0.0]})


def box(x):
    """A pose row whose feet (the bottom center of the box) are at court x with the H of the test (identity)."""
    return [0.0, x - 0.2, 0.0, x + 0.2, 5.0]


class ServePoseSide(unittest.TestCase):
    def setUp(self):
        self.old = (chain.SERVE_POSE_SIDE, chain.court_H, chain.near_far)
        chain.court_H = lambda mid, t: np.eye(3)
        self.feet = {}                               # {second: (near x, far x)}

        def nf(p):
            n, f = self.feet.get(int(p[0]), (None, None))
            return (None if n is None else box(n)), (None if f is None else box(f))
        chain.near_far = nf
        self.pose = np.arange(200)[:, None].astype(float)    # pose[s][0] = s

    def tearDown(self):
        chain.SERVE_POSE_SIDE, chain.court_H, chain.near_far = self.old

    def side(self, serves, a=100.0, server=None, pose=True):
        return chain.serve_pose_side("m", self.pose if pose else None, serves, a, server)

    def test_on_by_default(self):
        # manager decision after E216; CHAIN_SERVE_POSE_SIDE=0 gives the old way
        if "CHAIN_SERVE_POSE_SIDE" not in os.environ:
            self.assertTrue(self.old[0])

    def test_the_pose_feet_first(self):
        self.feet = {100: (None, -1.5), 101: (None, -1.0)}        # the far server at x < 0; the serve flight says x > 0
        self.assertEqual(self.side([ev(101.4, x=1.0)]), (False, False))

    def test_the_seconds_of_the_serve_time(self):
        self.feet = {99: (None, 2.0), 100: (None, 2.0), 101: (None, -2.0), 102: (None, -2.0)}
        self.assertEqual(self.side([ev(102.4, x=2.0)]), (False, False))   # seconds 101 and 102, not the row start

    def test_the_server_end_of_the_serve_event(self):
        self.feet = {100: (1.0, -1.0), 101: (1.0, -1.0)}
        self.assertEqual(self.side([ev(101.0, near=True, x=-2.0)], server=False), (True, True))

    def test_the_flight_when_the_pose_has_no_side(self):
        self.feet = {100: (None, 0.1), 101: (None, 0.2)}           # |x| < SIDE_X
        self.assertEqual(self.side([ev(101.0, x=-1.0)]), (False, False))
        self.feet = {}
        self.assertEqual(self.side([ev(101.0, x=1.0)]), (False, True))
        self.assertEqual(self.side([ev(101.0, x=1.0)], pose=False), (False, True))
        self.assertIsNone(self.side([ev(101.0, x=0.1)], pose=False))

    def test_no_serve_event(self):
        self.feet = {99: (-1.0, None), 100: (-1.0, None)}
        self.assertEqual(self.side([ev(100.5, near=True, kind="toss")], server=True), (True, False))   # a toss alone
        self.assertIsNone(self.side([], server=None))

    def test_row_side_falls_back(self):
        serves = [ev(100.5, x=1.0)]
        r = {"start": 100.0, "poseSide": (False, False), "sideServe": (False, False)}
        chain.SERVE_POSE_SIDE = False
        self.assertEqual(chain.row_side(r, serves), (False, True))          # the old rule: the serve flight
        chain.SERVE_POSE_SIDE = True
        self.assertEqual(chain.row_side(r, serves), (False, False))
        r["sideServe"] = None
        self.assertEqual(chain.row_side(r, serves), (False, True))
        self.assertEqual(chain.row_side({"start": 100.0, "poseSide": (True, True), "sideServe": None}, []), (True, True))

    def test_fault_side_with_the_new_side(self):
        serves = [ev(100.5, x=1.0), ev(112.0, x=-1.0)]                       # the flights give two sides
        rows = [{"start": 100.0, "end": 106.0, "fault": None, "chain": {"shots": [{"t": 101.0, "end": "out"}]}, "sideServe": (False, False)},
                {"start": 111.0, "end": 120.0, "fault": None, "chain": {"shots": [{"t": 112.0, "end": "in"}] * 3}, "sideServe": (False, False)}]
        chain.SERVE_POSE_SIDE = False
        self.assertEqual(len(chain.fault_rallies([dict(r) for r in rows], serves)), 2)
        chain.SERVE_POSE_SIDE = True
        got = chain.fault_rallies([dict(r) for r in rows], serves)
        self.assertEqual([r["start"] for r in got], [111.0])
        self.assertAlmostEqual(got[0]["fault"]["t"], 100.5)


if __name__ == "__main__":
    unittest.main()

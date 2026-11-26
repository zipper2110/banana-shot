"""E136: ball_path.py, the weak blobs where a path supports them (CPU, no model)."""
import unittest
import numpy as np
import ball_path as bp


def path(frames, x0=500.0, y0=300.0, vx=4.0, vy=6.0, skip=()):
    return np.array([(k, x0 + vx * k, y0 + vy * k, 3.0, 0.8) for k in frames if k not in skip], np.float32)


class TestBallPath(unittest.TestCase):
    def test_blobs_only_below_strong(self):
        hm = np.zeros((40, 40), np.float32)
        hm[5:8, 5:8] = 0.9                       # a strong component: not a weak blob
        hm[20:23, 20:23] = 0.3                   # a weak component
        hm[30, 30] = 0.1                         # below low
        out = bp.Weak(0.2).blobs(hm)
        self.assertEqual(len(out), 1)
        self.assertAlmostEqual(out[0][0], 21.0, 3)
        self.assertLess(out[0][3], bp.STRONG)

    def test_path_keeps_weak_on_the_line(self):
        s = path(range(0, 20), skip=(8, 9, 10))
        w = np.array([(9, 500 + 36 + 3, 300 + 54 - 2, 1.0, 0.4),       # on the line (5 px off)
                      (9, 500 + 36 + 60, 300 + 54, 1.0, 0.4)], np.float32)  # 60 px off
        k = bp.path_keep(s, w)
        self.assertEqual(k.tolist(), w[:1].tolist())

    def test_static_and_same_ball(self):
        st = np.array([(k, 800.0, 200.0, 3.0, 0.9) for k in range(0, 20) if k != 9], np.float32)
        self.assertEqual(len(bp.path_keep(st, np.array([(9, 801, 200, 1, 0.4)], np.float32))), 0)   # a lamp
        s = path(range(0, 20))
        self.assertEqual(len(bp.path_keep(s, np.array([(9, 536 + 4, 354, 1, 0.4)], np.float32))), 0)  # same as the strong blob

    def test_only_gaps_and_one_for_each_frame(self):
        s = path(range(0, 20), skip=(9,))
        extra = np.array([(9, 536.0 + 30, 354.0, 3.0, 0.9)], np.float32)      # a strong blob of frame 9, 30 px off the path
        w = np.array([(9, 536 + 2, 354, 1, 0.4), (9, 536 + 6, 354, 1, 0.4)], np.float32)
        self.assertEqual(bp.path_keep(s, w).tolist(), w[:1].tolist())       # one blob for the frame: the nearer one
        s2 = np.concatenate([s, np.array([(9, 536.0 - 9, 354.0 - 5, 3.0, 0.9)], np.float32)])
        self.assertEqual(len(bp.path_keep(s2, w)), 0)                       # a strong blob 10.3 px from the path: no gap
        self.assertEqual(len(bp.path_keep(np.concatenate([s, extra]), w)), 1)

    def test_gap_limit(self):
        s = path(range(0, 40), skip=range(5, 30))
        self.assertEqual(len(bp.path_keep(s, np.array([(17, 500 + 68, 300 + 102, 1, 0.4)], np.float32))), 0)

    def test_finish_keeps_strong_rows(self):
        W = bp.Weak(0.2)
        s = path(range(0, 20), skip=(9,))
        W.add(9, [(536.0, 354.0, 1.0, 0.4)])
        W.add(9, [(900.0, 100.0, 1.0, 0.4)])
        out = W.finish(s)
        self.assertEqual(len(out), len(s) + 1)
        self.assertEqual(out[out[:, 4] >= 0.5].tolist(), s.tolist())
        self.assertEqual(W.rows, [])
        self.assertEqual(W.finish(s).tolist(), s.tolist())


if __name__ == "__main__":
    unittest.main()

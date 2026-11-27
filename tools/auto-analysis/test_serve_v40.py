"""Tests of SERVE_V40 (A47, E220 cause 1): is_serve takes a slow serve from V_SLOW km/h when the switch is on.
Usage: python -m unittest test_serve_v40"""
import os
import unittest
import serve_ball as sb


def flight(p0, v0, n=35, rms=4.7):
    return {"t0": 100.0, "t1": 100.6, "p0": list(p0), "v0": list(v0), "n": n, "rms": rms}


# E220: s011 m1030 1676.71, a far serve fit of 44 km/h (start y 11.7 m, z 2.4 m) to the near half.
SLOW = flight((-0.5, 11.7, 2.4), (0.0, -12.2, 0.0))      # 43.9 km/h
SLOWER = flight((-0.5, 11.7, 2.4), (0.0, -10.8, 0.0))    # 38.9 km/h, under V_SLOW


class ServeV40(unittest.TestCase):
    def setUp(self):
        self.old = sb.SERVE_V40

    def tearDown(self):
        sb.SERVE_V40 = self.old

    def test_the_default_is_on(self):
        if "SERVE_V40" not in os.environ:
            self.assertTrue(sb.SERVE_V40)

    def test_off_a_44_kmh_serve_is_not_a_serve(self):
        sb.SERVE_V40 = False
        self.assertFalse(sb.is_serve(SLOW))
        self.assertEqual(sb.serve_events([SLOW], 0, 200), [])

    def test_on_a_44_kmh_serve_is_a_serve(self):
        sb.SERVE_V40 = True
        self.assertTrue(sb.is_serve(SLOW))
        self.assertEqual([(e[0], e[2]) for e in sb.serve_events([SLOW], 0, 200)], [(100.0, "serve")])

    def test_on_a_serve_under_40_kmh_is_not_a_serve(self):
        sb.SERVE_V40 = True
        self.assertFalse(sb.is_serve(SLOWER))


if __name__ == "__main__":
    unittest.main()

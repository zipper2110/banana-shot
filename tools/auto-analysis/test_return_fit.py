"""Tests of RETURN_FIT (A42, E174 cause 2): a return candidate with an impossible fit does not remove a serve.
Usage: python -m unittest test_return_fit"""
import unittest
import serve_ball as sb


def flight(t0, t1, p0, v0, n=20, rms=1.0):
    return {"t0": t0, "t1": t1, "p0": list(p0), "v0": list(v0), "n": n, "rms": rms}


# A far serve (E174: m1020 3585.24, start y 11.7 m, 141 km/h to the near half).
SERVE = flight(100.0, 100.4, (0.3, 11.7, 2.7), (0.0, -39.0, -3.0))
# The far toss fit with a wrong depth just before it (E174: start y -30.8 m, 221 km/h, to the far half).
WRONG = flight(99.55, 99.93, (-0.2, -30.8, 3.6), (0.0, 61.4, 0.0), n=22, rms=3.6)
# A real return of the near player before the flight (start on the court, 100 km/h).
REAL = flight(99.0, 99.9, (0.5, -10.0, 1.0), (0.0, 27.0, 4.0))
# A slower fit from far behind the baseline (150 km/h, under RETURN_FIT_KMH): still a return.
SLOW = flight(99.55, 99.93, (-0.2, -30.8, 3.6), (0.0, 41.7, 0.0), n=22, rms=3.6)


class ReturnFit(unittest.TestCase):
    def setUp(self):
        self.old = sb.RETURN_FIT

    def tearDown(self):
        sb.RETURN_FIT = self.old

    def test_the_default_is_on(self):
        import os
        if "RETURN_FIT" not in os.environ:
            self.assertTrue(sb.RETURN_FIT)

    def test_off_the_wrong_fit_removes_the_serve(self):
        sb.RETURN_FIT = False
        self.assertTrue(sb.is_serve(SERVE))
        self.assertTrue(sb.is_return(SERVE, [WRONG]))
        self.assertEqual(sb.serve_events([WRONG, SERVE], 0, 200), [])

    def test_on_the_wrong_fit_is_not_a_return(self):
        sb.RETURN_FIT = True
        self.assertFalse(sb.is_return(SERVE, [WRONG]))
        self.assertEqual([(e[0], e[2]) for e in sb.serve_events([WRONG, SERVE], 0, 200)], [(100.0, "serve")])

    def test_on_a_real_return_and_a_slower_fit_stay_returns(self):
        sb.RETURN_FIT = True
        self.assertTrue(sb.is_return(SERVE, [REAL]))
        self.assertTrue(sb.is_return(SERVE, [SLOW]))
        self.assertTrue(sb.is_return(SERVE, [WRONG, REAL]))


if __name__ == "__main__":
    unittest.main()

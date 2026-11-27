"""Tests of CHAIN_SERVE_BOX (A31: a serve is judged against the service box). Usage: python -m unittest test_serve_box"""
import os, unittest
import shot_chain as sc


def bounce(t, x, y):
    """y: m from the net, + = the far side of the court."""
    return {"t": t, "kind": "bounce", "court": [x, y], "conf": 0.1}


def shot(t, near, bounces=(), x0=2.0, dur=0.8):
    s = 1.0 if near else -1.0
    fl = {"t0": t, "t1": t + dur, "p0": [x0, -11.8 * s, 2.6], "p1": [-1.0, 5.0 * s, 0.3], "v0": [0.0, 40.0 * s, -1.0], "n": 20}
    return {"t": t, "near": near, "how": "first", "fl": [fl], "bounces": list(bounces), "net": None, "reach": None,
            "weak": False, "serve": None}


def serve_ev(t, near, x0=2.0, kind="serve"):
    return (t, near, kind, {"p0": [x0, -11.8 if near else 11.8, 2.6], "v0": [0.0, 40.0, -1.0]})


class Switch(object):
    def __init__(self, mode):
        self.mode = mode

    def __enter__(self):
        self.old, sc.SERVE_BOX = sc.SERVE_BOX, self.mode

    def __exit__(self, *a):
        sc.SERVE_BOX = self.old


class Default(unittest.TestCase):
    def test_the_default_is_off(self):
        if "CHAIN_SERVE_BOX" not in os.environ:
            self.assertEqual(sc.SERVE_BOX, "0")


class ServeMargin(unittest.TestCase):
    def test_depth_always(self):
        self.assertAlmostEqual(sc.serve_margin(-1.0, 6.71), -0.31)       # E170 s008: 0.31 m behind the service line
        self.assertGreater(sc.serve_margin(-1.0, 5.0), 0.0)

    def test_the_side_line(self):
        self.assertLess(sc.serve_margin(-4.3, 5.0), 0.0)

    def test_the_box_side_when_the_side_is_known(self):
        self.assertGreater(sc.serve_margin(-1.0, 5.0, 2.0), 0.0)          # cross-court: the other sign
        self.assertAlmostEqual(sc.serve_margin(1.0, 5.0, 2.0), -1.0)     # the wrong box
        self.assertAlmostEqual(sc.serve_margin(-1.0, 5.0, -2.0), -1.0)

    def test_no_box_side_near_the_middle(self):
        self.assertGreater(sc.serve_margin(1.0, 5.0, sc.SIDE_X - 0.01), 0.0)
        self.assertGreater(sc.serve_margin(1.0, 5.0, None), 0.0)

    def test_no_side_is_the_old_fault_candidate_test(self):
        # chain.py (E39) used min(SW - |x|, SERVICE_LINE - |y|) with SERVICE_LINE 6.40
        for x, y in ((0.5, 3.0), (-3.9, 6.2), (2.0, 7.1), (4.5, 1.0)):
            self.assertAlmostEqual(sc.serve_margin(x, abs(y)), min(sc.SW - abs(x), 6.40 - abs(y)))


class ShotEnd(unittest.TestCase):
    def test_a_long_serve_is_out_only_with_the_switch(self):
        sh = shot(10.0, True, [bounce(10.6, -1.0, 8.0)])
        sh["serve"] = {"x0": 2.0, "ev": True}
        with Switch("0"):
            self.assertEqual(sc.shot_end(sh)[0], "in")
        with Switch("e"):
            k, m = sc.shot_end(sh)
            self.assertEqual(k, "out")
            self.assertAlmostEqual(m, sc.SL - 8.0)

    def test_a_rally_shot_keeps_the_full_court(self):
        sh = shot(10.0, True, [bounce(10.6, -1.0, 8.0)])
        with Switch("e"):
            self.assertEqual(sc.shot_end(sh)[0], "in")

    def test_the_far_server_and_the_wrong_box(self):
        sh = shot(10.0, False, [bounce(10.6, -1.0, -5.0)], x0=-2.0)   # far server at x < 0: the box is at x > 0
        sh["serve"] = {"x0": -2.0, "ev": True}
        with Switch("e"):
            self.assertEqual(sc.shot_end(sh), ("out", -1.0))


class MarkServes(unittest.TestCase):
    def test_a_shot_at_a_serve_event(self):
        a, b = shot(10.0, True), shot(11.2, False)
        sc.mark_serves([a, b], [serve_ev(10.1, True, x0=1.5)], "e")
        self.assertEqual(a["serve"], {"x0": 1.5, "ev": True})
        self.assertIsNone(b["serve"])

    def test_a_toss_alone_or_the_other_end_is_no_serve(self):
        a = shot(10.0, True)
        sc.mark_serves([a], [serve_ev(10.1, True, kind="toss"), serve_ev(10.1, False)], "e")
        self.assertIsNone(a["serve"])

    def test_no_serve_after_a_shot_of_the_other_end(self):
        a, b = shot(10.0, True), shot(11.2, False)
        sc.mark_serves([a, b], [serve_ev(11.2, False)], "e")
        self.assertIsNone(b["serve"])

    def test_the_second_serve_of_the_same_end(self):
        a, b = shot(10.0, True), shot(16.0, True)
        sc.mark_serves([a, b], [serve_ev(10.0, True), serve_ev(16.05, True)], "e")
        self.assertIsNotNone(a["serve"])
        self.assertIsNotNone(b["serve"])

    def test_the_first_shot_with_no_event_only_with_f(self):
        a = shot(10.0, True, x0=-1.2)
        sc.mark_serves([a], [], "e")
        self.assertIsNone(a["serve"])
        sc.mark_serves([a], [], "ef")
        self.assertEqual(a["serve"], {"x0": -1.2, "ev": False})

    def test_the_shot_that_serve_start_moved(self):
        a = shot(10.0, True)
        a["tToss"] = 9.2
        sc.mark_serves([a], [], "e")
        self.assertTrue(a["serve"] and not a["serve"]["ev"])

    def test_off_marks_nothing(self):
        a = shot(10.0, True)
        sc.mark_serves([a], [serve_ev(10.0, True)], "0")
        self.assertIsNone(a["serve"])


class ServeStart(unittest.TestCase):
    def run_a(self, mode):
        s0 = shot(10.0, True, [bounce(10.6, -1.0, 8.0)])           # in the court, behind the service line
        s1 = shot(16.0, True, [bounce(16.6, -1.0, 5.0)])
        s2 = shot(17.0, False, [bounce(17.8, 1.0, -9.0)])
        with Switch(mode):
            return sc.serve_start([s0, s1, s2], [], [serve_ev(10.0, True), serve_ev(16.0, True)], mode="a")

    def test_a_long_first_serve_is_a_fault_before_the_serve_in_play(self):
        out = self.run_a("e")
        self.assertEqual(len(out), 2)
        self.assertEqual(out[0]["t"], 16.0)
        self.assertEqual(out[0]["before"]["shot"]["t"], 10.0)      # chain.py makes the fault clip (E164 a)

    def test_off_keeps_the_old_list(self):
        self.assertEqual(len(self.run_a("0")), 3)


class Build(unittest.TestCase):
    def test_off_sets_no_serve(self):
        a = shot(10.0, True, [bounce(10.6, -1.0, 8.0)])
        with Switch("0"):
            sc.mark_serves([a], [serve_ev(10.0, True)])
        self.assertIsNone(a["serve"])
        self.assertEqual(sc.shot_end(a)[0], "in")


if __name__ == "__main__":
    unittest.main()

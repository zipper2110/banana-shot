"""Tests of CHAIN_TIME_ORDER (E166 a: shot_chain.time_order). Usage: python -m unittest test_time_order"""
import os, unittest
import shot_chain as sc


def bounce(t, y, x=0.0):
    return {"t": t, "kind": "bounce", "court": [x, y], "conf": 0.1}


def shot(t, near, bounces=(), how="first", dur=0.8):
    """A shot with one flight from t to t + dur. y of the bounces: m from the net, + = the far side of the court."""
    s = 1.0 if near else -1.0
    fl = {"t0": t, "t1": t + dur, "p0": [0.0, -11.0 * s, 1.0], "p1": [0.0, 5.0 * s, 0.3], "v0": [0.0, 20.0 * s, 1.0], "n": 20}
    return {"t": t, "near": near, "how": how, "fl": [fl], "bounces": list(bounces), "net": None, "reach": None, "weak": False,
            "serve": None}


def net_alone(t, near):
    e = {"t": t, "kind": "net", "conf": 0.8, "dirConf": 0.8, "toFar": near}
    return {"t": t - 0.5, "near": near, "how": "net", "fl": [], "bounces": [], "net": e, "reach": None, "weak": True, "serve": None}


class Default(unittest.TestCase):
    def test_the_default_is_a(self):
        # manager decision after E168; the app removes each CHAIN_* variable, so it uses this default
        if "CHAIN_TIME_ORDER" not in os.environ:
            self.assertEqual(sc.TIME_ORDER, "a")

    def test_part_c_is_removed(self):
        # E168: part c removed a real net stop (label s002)
        self.assertFalse(hasattr(sc, "no_net_alone"))


class TimeOrder(unittest.TestCase):
    def test_a_bounce_of_the_hitter_at_the_flight_start_goes(self):
        # E167: the bounce event at the start of the first flight is 0.02 to 0.06 s before the flight start
        b = bounce(9.97, -11.5)                      # near hitter: y < 0 is its side (at the baseline)
        sh = shot(10.0, True, [b, bounce(10.7, 5.0)])
        sc.time_order([sh])
        self.assertEqual([e["t"] for e in sh["bounces"]], [10.7])
        self.assertIs(sh["early"][0], b)
        self.assertEqual(sc.shot_end(sh)[0], "in")

    def test_the_side_is_by_the_hitter(self):
        sh = shot(10.0, False, [bounce(9.97, 11.5)])  # far hitter: y > 0 is its side
        sc.time_order([sh])
        self.assertEqual(sh["bounces"], [])

    def test_a_bounce_on_the_other_side_at_the_flight_start_stays(self):
        # the track starts after the bounce of the shot: the contact was before it
        sh = shot(10.0, True, [bounce(9.97, 4.0)])
        sc.time_order([sh])
        self.assertEqual(len(sh["bounces"]), 1)
        self.assertNotIn("early", sh)

    def test_an_overhead_bounce_on_the_other_side_0_1_s_after_the_contact_stays(self):
        sh = shot(10.0, True, [bounce(10.1, 1.5)])
        sc.time_order([sh])
        self.assertEqual(len(sh["bounces"]), 1)

    def test_a_bounce_of_the_hitter_later_than_the_margin_stays(self):
        # a real "own" end (the ball drops after the net touch) is after the contact
        sh = shot(10.0, True, [bounce(10.0 + sc.TO_EPS + 0.01, -0.3)])
        sc.time_order([sh])
        self.assertEqual(len(sh["bounces"]), 1)
        self.assertEqual(sc.shot_end(sh)[0], "own")

    def test_a_bounce_before_a_moved_serve_contact_goes(self):
        # serve_start (b) moved the shot from the toss to the serve event: a bounce before the contact is not of the serve
        sh = shot(20.0, False, [bounce(20.4, 3.0), bounce(21.6, -5.0)])
        sh["tToss"], sh["t"] = 20.0, 20.9
        sc.time_order([sh])
        self.assertEqual([e["t"] for e in sh["bounces"]], [21.6])

    def test_a_shot_with_no_flight_is_not_changed(self):
        sh = net_alone(10.0, True)
        sc.time_order([sh])
        self.assertEqual(sh["bounces"], [])


class BuildGate(unittest.TestCase):
    def _run(self, mode):
        old = sc.TIME_ORDER, sc.time_order
        calls = []
        try:
            sc.TIME_ORDER = mode
            sc.time_order = lambda shots: calls.append("a")
            sc.build([], None, [], serves=None)
        finally:
            sc.TIME_ORDER, sc.time_order = old
        return calls

    def test_the_switch_values(self):
        self.assertEqual(self._run("0"), [])
        self.assertEqual(self._run("a"), ["a", "a"])


if __name__ == "__main__":
    unittest.main()

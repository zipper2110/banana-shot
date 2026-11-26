"""Tests of CHAIN_TIME_ORDER (E166 a and c: shot_chain.time_order and no_net_alone). Usage: python -m unittest test_time_order"""
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
    def test_the_default_is_off(self):
        # a new switch is off by default; only the manager changes a default
        if "CHAIN_TIME_ORDER" not in os.environ:
            self.assertEqual(sc.TIME_ORDER, "0")


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


class NoNetAlone(unittest.TestCase):
    def test_a_net_alone_shot_before_a_same_end_shot_goes(self):
        shots = [net_alone(10.0, True), shot(11.0, True, how="gap"), shot(12.2, False, how="hit")]
        out = sc.no_net_alone(shots)
        self.assertEqual([s["t"] for s in out], [11.0, 12.2])
        self.assertEqual(out[0]["how"], "first")

    def test_the_gap_is_from_the_net_event(self):
        for dt, n in ((sc.TO_NET_GAP - 0.05, 1), (sc.TO_NET_GAP + 0.05, 2)):
            shots = [net_alone(10.0, True), shot(10.0 + dt, True, how="gap")]
            self.assertEqual(len(sc.no_net_alone(shots)), n, dt)

    def test_the_other_end_next_keeps_it(self):
        shots = [net_alone(10.0, True), shot(10.5, False, how="hit")]
        self.assertEqual(len(sc.no_net_alone(shots)), 2)

    def test_a_net_shot_with_a_flight_stays(self):
        a = shot(9.5, True, how="first")
        a["net"] = {"t": 10.0, "kind": "net", "conf": 0.8, "dirConf": 0.8, "toFar": True}
        a["how"] = "net"
        self.assertEqual(len(sc.no_net_alone([a, shot(10.5, True, how="gap")])), 2)

    def test_a_later_net_alone_shot_goes_and_the_how_of_the_next_stays(self):
        shots = [shot(8.0, False, how="first"), net_alone(10.0, True), shot(10.8, True, how="gap")]
        out = sc.no_net_alone(shots)
        self.assertEqual([s["how"] for s in out], ["first", "gap"])


class BuildGate(unittest.TestCase):
    def _run(self, mode):
        old = sc.TIME_ORDER, sc.time_order, sc.no_net_alone
        calls = []
        try:
            sc.TIME_ORDER = mode
            sc.time_order = lambda shots: calls.append("a")
            sc.no_net_alone = lambda shots: calls.append("c") or shots
            sc.build([], None, [], serves=None)
        finally:
            sc.TIME_ORDER, sc.time_order, sc.no_net_alone = old
        return calls

    def test_the_switch_values(self):
        self.assertEqual(self._run("0"), [])
        self.assertEqual(self._run("a"), ["a", "a"])
        self.assertEqual(self._run("c"), ["c"])
        self.assertEqual(self._run("ac"), ["a", "c", "a"])


if __name__ == "__main__":
    unittest.main()

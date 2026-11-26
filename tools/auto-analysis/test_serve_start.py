"""Tests of CHAIN_SERVE_START (E164, shot_chain.serve_start and chain.before_fault). Usage: python -m unittest test_serve_start"""
import unittest
import chain
import shot_chain as sc


def shot(t, near, end=None, dur=0.8, how="first"):
    """A shot with one flight from t to t + dur. end: "net" (a net event), "out" (a bounce out), "in" (a bounce in), None."""
    s = 1.0 if near else -1.0
    fl = {"t0": t, "t1": t + dur, "p0": [0.0, -11.0 * s, 1.0], "v0": [0.0, 20.0 * s, 1.0], "n": 20}
    sh = {"t": t, "near": near, "how": how, "fl": [fl], "bounces": [], "net": None, "reach": None, "weak": False, "serve": None}
    if end == "net":
        sh["net"] = {"t": t + 0.5, "kind": "net", "conf": 0.8, "dirConf": 0.8, "toFar": near}
    elif end in ("out", "in"):
        y = 13.5 if end == "out" else 5.0
        sh["bounces"] = [{"t": t + 0.7, "court": [0.0, y * s], "conf": 0.1}]
    return sh


def serve(t, near, kind="toss+serve"):
    return (t, near, kind, None)


class ServeStartA(unittest.TestCase):
    def test_a_fault_into_the_net_before_a_same_end_serve_is_removed(self):
        shots = [shot(10.0, True, "net"), shot(13.0, True, "in", how="gap"), shot(14.2, False, "in")]
        out = sc.serve_start(shots, [], [], "a")
        self.assertEqual([s["t"] for s in out], [13.0, 14.2])
        self.assertEqual(out[0]["how"], "first")
        self.assertEqual(out[0]["before"]["shot"]["t"], 10.0)
        self.assertIsNone(out[0]["before"]["cord"])

    def test_an_out_serve_is_removed_too(self):
        out = sc.serve_start([shot(10.0, False, "out"), shot(12.5, False, None, how="gap")], [], [], "a")
        self.assertEqual([s["t"] for s in out], [12.5])

    def test_a_return_of_the_other_end_keeps_the_first_shot(self):
        # E164 step 1: a serve in play with a wrong "out" end has a return 1 to 2.2 s later
        shots = [shot(10.0, True, "out"), shot(11.5, False, "in"), shot(14.0, True, "in")]
        self.assertEqual(len(sc.serve_start(shots, [], [], "a")), 3)

    def test_a_same_end_shot_less_than_the_gap_later_keeps_the_first_shot(self):
        shots = [shot(10.0, True, "net"), shot(10.0 + sc.SS_GAP - 0.1, True, "in", how="gap")]
        self.assertEqual(len(sc.serve_start(shots, [], [], "a")), 2)

    def test_a_first_shot_in_or_not_seen_is_kept(self):
        # not the "any end" form (E164 step 1: it also removed 4 serves in play)
        for end in ("in", None):
            shots = [shot(10.0, True, end), shot(13.0, True, "in", how="gap")]
            self.assertEqual(len(sc.serve_start(shots, [], [], "a")), 2, end)

    def test_a_cord_event_is_a_let_the_shot_goes_with_the_cord(self):
        cord = {"t": 10.5, "kind": "cord", "conf": 0.8, "dirConf": 0.8, "toFar": True}
        out = sc.serve_start([shot(10.0, True, "in"), shot(13.0, True, "in", how="gap")], [cord], [], "a")
        self.assertEqual(len(out), 1)
        self.assertIs(out[0]["before"]["cord"], cord)

    def test_off_and_b_alone_do_not_remove(self):
        for mode in ("0", "b"):
            shots = [shot(10.0, True, "net"), shot(13.0, True, "in", how="gap")]
            self.assertEqual(len(sc.serve_start(shots, [], [], mode)), 2, mode)


class ServeStartB(unittest.TestCase):
    def test_the_shot_at_the_toss_moves_to_the_serve_event(self):
        shots = [shot(20.0, False, "in"), shot(21.9, True, "in")]
        out = sc.serve_start(shots, [], [serve(20.9, False)], "b")
        self.assertEqual(out[0]["t"], 20.9)
        self.assertEqual(out[0]["tToss"], 20.0)

    def test_no_move_for_another_end_a_toss_alone_or_out_of_the_window(self):
        for ev in (serve(20.9, True), serve(20.9, False, "toss"), serve(20.3, False), serve(21.5, False)):
            out = sc.serve_start([shot(20.0, False, "in"), shot(23.0, True, "in")], [], [ev], "b")
            self.assertEqual(out[0]["t"], 20.0, ev)

    def test_no_move_past_the_next_shot(self):
        out = sc.serve_start([shot(20.0, False, "in"), shot(20.95, True, "in")], [], [serve(20.9, False)], "b")
        self.assertEqual(out[0]["t"], 20.0)

    def test_ab_measures_the_gap_from_the_serve_contact(self):
        # "b" first: the toss shot moves to 10.9 s; the next same-end shot at 12.7 s is then 1.8 s later: no removal
        shots = [shot(10.0, True, "net"), shot(12.7, True, "in", how="gap")]
        self.assertEqual(len(sc.serve_start(shots, [], [serve(10.9, True)], "ab")), 2)
        self.assertEqual(len(sc.serve_start([shot(10.0, True, "net"), shot(12.7, True, "in", how="gap")], [], [serve(10.9, True)], "a")), 1)


class BeforeFault(unittest.TestCase):
    def test_the_fault_clip_is_at_the_removed_shot(self):
        f = chain.before_fault(shot(10.0, True, "net"), 30.0)
        self.assertEqual(f["t"], 10.0)
        self.assertEqual(f["kind"], "chain")
        self.assertTrue(f["near"])
        self.assertEqual(f["chain"]["shots"][0]["t"], 10.0)      # label_measure takes the contact from chain.shots[0].t
        self.assertEqual(f["chain"]["shots"][0]["end"], "net")


class BuildGate(unittest.TestCase):
    def test_build_without_serves_never_calls_serve_start(self):
        old = sc.SERVE_START
        try:
            sc.SERVE_START = "ab"
            called = []
            orig = sc.serve_start
            sc.serve_start = lambda *a, **k: called.append(1) or a[0]
            sc.build([], None, [], serves=None)
            self.assertEqual(called, [])
            sc.build([], None, [], serves=[])
            self.assertEqual(called, [1])
        finally:
            sc.SERVE_START, sc.serve_start = old, orig


if __name__ == "__main__":
    unittest.main()

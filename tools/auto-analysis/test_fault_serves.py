"""Tests of A47 (S4 E220 FS): the chain of a fault candidate gets the serve events of its window (chain.fault_pt,
CHAIN_FAULT_SERVES). Usage: python -m unittest test_fault_serves"""
import os, unittest
import numpy as np
import chain
import shot_chain as sc


def ev(t, near=False, kind="serve"):
    return (t, near, kind, {"p0": [1.0, -11.0, 2.5], "v0": [0.0, 20.0, 0.0]})


class FaultServes(unittest.TestCase):
    def setUp(self):
        self.old = chain.FAULT_SERVES, sc.SERVE_START
        self.full = {"events": [], "flights": [{"t0": x, "t1": x + 0.8} for x in (98.0, 100.2, 101.5, 104.0)]}
        self.fl_t0 = np.array([f["t0"] for f in self.full["flights"]])
        self.serves = [ev(96.5), ev(98.5), ev(100.3), ev(104.0), ev(105.0)]

    def tearDown(self):
        chain.FAULT_SERVES, sc.SERVE_START = self.old

    def pt(self):
        return chain.fault_pt(99.3, 102.8, self.full, self.fl_t0, self.serves)

    def test_on_by_default(self):
        # manager decision after S4 E221; CHAIN_FAULT_SERVES=0 gives the old way
        if "CHAIN_FAULT_SERVES" not in os.environ:
            self.assertTrue(self.old[0])

    def test_off_gives_no_serve_events_as_before(self):
        chain.FAULT_SERVES = False
        p = self.pt()
        self.assertNotIn("serves", p)
        self.assertEqual([f["t0"] for f in p["flights"]], [100.2, 101.5])
        self.assertEqual(p["kinks"], [])

    def test_on_gives_the_serve_events_of_the_window(self):
        chain.FAULT_SERVES, sc.SERVE_START = True, "abc"
        p = self.pt()
        # the window of the rally chain: 1 s before the start to 2.5 s after the end (98.3 to 105.3 s)
        self.assertEqual([e[0] for e in p["serves"]], [98.5, 100.3, 104.0, 105.0])
        self.assertEqual([f["t0"] for f in p["flights"]], [100.2, 101.5])    # the flights do not change

    def test_on_with_serve_start_off_gives_nothing(self):
        chain.FAULT_SERVES, sc.SERVE_START = True, "0"
        self.assertNotIn("serves", self.pt())


if __name__ == "__main__":
    unittest.main()

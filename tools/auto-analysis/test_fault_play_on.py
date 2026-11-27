"""Tests of A50 (E213): FAULT_SIDE takes a fault row with more shots when its serve ends out or in the net
(chain.fault_shots_ok, CHAIN_FAULT_PLAY_ON). Usage: python -m unittest test_fault_play_on"""
import os, unittest
import chain


def ev(t, near=False, x=2.0, kind="serve"):
    return (t, near, kind, {"p0": [x, -11.0 if not near else 11.0, 2.5], "v0": [0.0, 20.0, 0.0]})


def row(a, b, ends):
    return {"start": a, "end": b, "fault": None, "chain": {"shots": [{"t": a + 1.0 + i, "end": e} for i, e in enumerate(ends)]}}


class FaultPlayOn(unittest.TestCase):
    def setUp(self):
        self.old = chain.FAULT_PLAY_ON
        self.serves = [ev(100.5), ev(112.0)]          # the same server and side: a first fault, then the second serve

    def tearDown(self):
        chain.FAULT_PLAY_ON = self.old

    def rows(self, ends):
        return chain.fault_rallies([row(100.0, 106.0, ends), row(111.0, 120.0, ("in", "in", "out"))], self.serves)

    def test_on_by_default(self):
        # manager decision after E213; CHAIN_FAULT_PLAY_ON=0 gives the old way
        if "CHAIN_FAULT_PLAY_ON" not in os.environ:
            self.assertTrue(self.old)

    def test_off_keeps_a_row_of_three_shots(self):
        chain.FAULT_PLAY_ON = False
        self.assertEqual(len(self.rows(("out", "in", "none"))), 2)
        self.assertEqual(len(self.rows(("out", "none"))), 1)          # two shots: the old rule takes it

    def test_on_takes_it_when_the_serve_ends_out_or_in_the_net(self):
        chain.FAULT_PLAY_ON = True
        for e in chain.FAULT_PLAY_ENDS:
            got = self.rows((e, "in", "none"))
            self.assertEqual(len(got), 1, e)
            self.assertEqual(got[0]["start"], 111.0)
            self.assertAlmostEqual(got[0]["fault"]["t"], 100.5)     # the next rally gets the fault serve

    def test_on_keeps_it_when_the_serve_is_in_or_not_known(self):
        chain.FAULT_PLAY_ON = True
        for e in ("in", "none", "own"):
            self.assertEqual(len(self.rows((e, "in", "none"))), 2, e)

    def test_on_still_needs_the_same_server_and_side(self):
        chain.FAULT_PLAY_ON = True
        self.serves = [ev(100.5), ev(112.0, x=-2.0)]
        self.assertEqual(len(self.rows(("out", "in", "none"))), 2)


if __name__ == "__main__":
    unittest.main()

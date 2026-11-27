"""Tests of A48 (E212): a rally with no fact of a point is ball play between points (chain.play_between,
CHAIN_PLAY_DROP). Usage: python -m unittest test_play_drop"""
import os, unittest
import chain


def ev(t, kind="serve"):
    return (t, True, kind, {"p0": [1.0, 11.0, 2.5], "v0": [0.0, 20.0, 0.0]})


def row(ends=("none", "none"), server=None, prob=0.6, a=100.0, b=106.0):
    return {"start": a, "end": b, "prob": prob, "serverNear": server,
            "chain": {"shots": [{"t": a + 1.0 + i, "end": e} for i, e in enumerate(ends)]}}


class PlayDrop(unittest.TestCase):
    def test_on_by_default(self):
        # manager decision after E212; CHAIN_PLAY_DROP=0 gives the old way
        if "CHAIN_PLAY_DROP" not in os.environ:
            self.assertTrue(chain.PLAY_DROP)

    def test_no_serve_no_server_no_end_is_play(self):
        self.assertTrue(chain.play_between(row(), []))
        self.assertTrue(chain.play_between(row(ends=()), [ev(99.5, "toss")]))     # a toss alone is no serve

    def test_a_serve_event_in_the_row_keeps_it(self):
        for t in (99.0, 103.0, 106.0):
            self.assertFalse(chain.play_between(row(), [ev(t)]), t)
        self.assertTrue(chain.play_between(row(), [ev(98.9), ev(106.1)]))          # outside the window

    def test_a_server_from_the_pose_keeps_it(self):
        self.assertFalse(chain.play_between(row(server=False), []))
        self.assertFalse(chain.play_between(row(server=True), []))

    def test_an_end_of_a_ball_in_play_keeps_it(self):
        for e in chain.PLAY_ENDS:
            self.assertFalse(chain.play_between(row(ends=("none", e)), []), e)

    def test_a_short_row_or_no_chain_is_not_judged(self):
        self.assertFalse(chain.play_between(row(prob=-1.0), []))
        r = row()
        r["chain"] = None
        self.assertFalse(chain.play_between(r, []))


if __name__ == "__main__":
    unittest.main()

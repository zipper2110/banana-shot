"""Tests of A59 (E218): the start of a result row clipped to a band around the serve event (chain.start_clamp,
CHAIN_START_CLAMP). Usage: python -m unittest test_start_clamp"""
import os, unittest
import chain


def ev(t, kind="serve", near=False):
    return (t, near, kind, {"p0": [1.0, 11.0, 2.5], "v0": [0.0, -20.0, 0.0]})


class StartClamp(unittest.TestCase):
    def test_off_by_default(self):
        if "CHAIN_START_CLAMP" not in os.environ:
            self.assertFalse(chain.START_CLAMP)

    def test_band(self):
        # serve at 105.0: the band is 103.7 - 1.0 to 103.7 + 1.0
        self.assertAlmostEqual(chain.start_clamp(102.0, 120.0, [ev(105.0)]), 102.7)     # early: to the band start
        self.assertAlmostEqual(chain.start_clamp(103.0, 120.0, [ev(105.0)]), 103.0)     # inside: no change
        self.assertAlmostEqual(chain.start_clamp(104.9, 120.0, [ev(105.0)]), 104.7)     # late: to the band end

    def test_window_and_kind(self):
        self.assertEqual(chain.start_clamp(100.0, 120.0, [ev(104.5)]), 100.0)           # the serve is past start + 4 s
        self.assertEqual(chain.start_clamp(106.5, 120.0, [ev(105.0)]), 106.5)           # past start - 1 s: kept
        self.assertEqual(chain.start_clamp(102.0, 120.0, [ev(105.0, "toss")]), 102.0)   # a toss alone is not a serve
        self.assertEqual(chain.start_clamp(102.0, 120.0, []), 102.0)
        self.assertAlmostEqual(chain.start_clamp(102.0, 120.0, [ev(104.0), ev(105.5)]), 102.0)   # the first event: band 101.7 to 103.7

    def test_not_past_the_end(self):
        self.assertAlmostEqual(chain.start_clamp(100.0, 101.5, [ev(103.5)]), 100.5)


if __name__ == "__main__":
    unittest.main()

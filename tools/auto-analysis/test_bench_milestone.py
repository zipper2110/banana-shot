"""Tests of the milestone rule of the bench held-out set (manager, 2026-11-27: Misha m1113 only at a milestone, by S1).
Usage: python -m unittest test_bench_milestone"""
import unittest
import bench
import lab


class Milestone(unittest.TestCase):
    def test_the_default_held_out_set_has_no_milestone_video(self):
        mids = bench.heldout_mids()
        self.assertNotIn("m1113", mids)
        self.assertIn("m1116", mids)                  # Igor stays in each bench

    def test_the_flag_adds_the_milestone_video(self):
        self.assertEqual(bench.heldout_mids(True), list(lab.TEST))
        self.assertIn("m1113", bench.heldout_mids(True))

    def test_the_events_skip_the_milestone_video(self):
        import score_events
        seen = []
        old = (score_events.measures, score_events.load)
        score_events.measures = lambda mid, folder=None: seen.append(mid) or None
        try:
            bench.events_all(None, skip=bench.MILESTONE_ONLY)
            self.assertNotIn("m1113", seen)
            self.assertIn("m1116", seen)
            seen.clear()
            bench.events_all(None)
            self.assertIn("m1113", seen)
        finally:
            score_events.measures, score_events.load = old

    def test_the_cli_has_the_flag(self):
        src = open(bench.__file__, encoding="utf-8").read()
        self.assertIn('"--milestone"', src)


if __name__ == "__main__":
    unittest.main()

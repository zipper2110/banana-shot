"""Tests of the row-chain hit measure and the "after the point" count of case_history (S4 E173, S5 E139).
Usage: python -m unittest test_case_history_hits"""
import unittest
import case_history as ch


def row(a, b, shots=(), fault_shots=None):
    r = {"start": a, "end": b, "chain": {"shots": [{"t": t, "near": True} for t in shots]}}
    if fault_shots is not None:
        r["fault"] = {"chain": {"shots": [{"t": t} for t in fault_shots]}}
    return r


class RowShots(unittest.TestCase):
    def test_the_chain_window_of_a_row_is_start_minus_1_to_end_plus_2_5(self):
        rows = [row(100.0, 110.0, [99.2, 105.0, 112.4])]
        self.assertTrue(ch.covered(rows, 99.0))
        self.assertTrue(ch.covered(rows, 112.5))
        self.assertFalse(ch.covered(rows, 98.9))
        self.assertFalse(ch.covered(rows, 112.6))

    def test_all_rows_that_meet_the_window_give_shots_inside_the_window(self):
        # a false row can hold a real hit (s016 50:43): all rows, not only the matched ones
        rows = [row(100.0, 110.0, [101.0, 109.0]), row(111.5, 113.0, [111.6]), row(200.0, 210.0, [201.0])]
        got = [s["t"] for s in ch.row_shots(rows, 108.0, 112.0)]
        self.assertEqual(got, [109.0, 111.6])

    def test_a_serve_on_a_fault_clip(self):
        rows = [row(100.0, 110.0, [101.0], fault_shots=[95.2])]
        self.assertTrue(ch.fault_clip_serve(rows, 95.4))
        self.assertFalse(ch.fault_clip_serve(rows, 95.6))      # outside TOL_HIT 0.3 s
        self.assertFalse(ch.fault_clip_serve([row(100.0, 110.0, [95.4])], 95.4))   # a row shot is not a fault clip


class AfterPoint(unittest.TestCase):
    def test_a_false_event_after_the_last_label_of_a_point_counts_apart(self):
        ev = [{"t": t} for t in (100.0, 101.0, 104.0, 130.0, 131.0)]
        points = [{"events": ev[:3]}, {"events": ev[3:]}]
        gaps = ch.dead_gaps(points, ev)
        self.assertEqual(gaps, [(104.0, 130.0), (131.0, float("inf"))])
        # 102.5: inside the point (a false event); 105 and 129: after the point; 140: after the last point
        self.assertEqual(ch.after_point([102.5, 105.0, 129.0, 140.0], gaps), 3)
        self.assertEqual(ch.after_point([104.0, 130.0], gaps), 0)   # at a labeled event: not after the point


if __name__ == "__main__":
    unittest.main()

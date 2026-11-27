"""Tests of the label window of a fragment (A32): case_lib.label_window and label_measure.fault_clips(window).
Usage: python -m unittest test_label_window"""
import unittest
import case_lib
import label_measure as lm


def frag(offset, span, duration=60.0, mid="m9999"):
    return {"offset": offset, "labelSpan": [span[0] - offset, span[1] - offset], "duration": duration,
            "source": {"mid": mid}}


class LabelWindow(unittest.TestCase):
    def setUp(self):
        self.old = case_lib.points
        self.pts = []
        case_lib.points = lambda mid: self.pts

    def tearDown(self):
        case_lib.points = self.old

    def test_the_point_before_limits_the_start(self):
        self.pts = [{"start": 80.0, "end": 90.0}, {"start": 100.0, "end": 105.0}]
        w = case_lib.label_window(frag(70.0, (100.0, 105.0)))
        self.assertEqual(w["source"], [93.0, 130.0])               # 90 + 3 s; the clip end
        self.assertEqual(w["fragment"], [23.0, 60.0])
        self.assertEqual(w["prevEnd"], 90.0)

    def test_not_more_than_25_s_before_the_span(self):
        self.pts = [{"start": 10.0, "end": 20.0}, {"start": 100.0, "end": 105.0}]
        self.assertEqual(case_lib.label_window(frag(40.0, (100.0, 105.0)))["source"][0], 75.0)

    def test_not_before_the_clip_start(self):
        self.pts = [{"start": 100.0, "end": 105.0}]
        self.assertEqual(case_lib.label_window(frag(92.0, (100.0, 105.0)))["source"][0], 92.0)

    def test_a_point_that_ends_after_the_span_start_is_not_the_point_before(self):
        self.pts = [{"start": 95.0, "end": 106.0}]
        self.assertIsNone(case_lib.label_window(frag(70.0, (100.0, 105.0)))["prevEnd"])


class FaultClipsWindow(unittest.TestCase):
    def test_a_clip_outside_the_window_is_not_counted(self):
        rows = [{"start": 50.0, "end": 52.0, "fault": {"t": 50.5}}, {"start": 99.0, "end": 101.0, "fault": {"t": 100.0}}]
        ev = [{"t": 50.5, "kind": "serve"}, {"t": 100.0, "kind": "serve"}]
        self.assertEqual(lm.fault_clips(rows, ev, {}, [])["clips"], 2)
        self.assertEqual(lm.fault_clips(rows, ev, {}, [], (90.0, 130.0))["clips"], 1)


if __name__ == "__main__":
    unittest.main()

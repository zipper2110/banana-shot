"""E170: label_measure on made-up marks. Usage: python -m unittest test_label_measure"""
import unittest
import label_measure as lm


def sv(t, end="far", x=False):
    return {"kind": "serve", "end": end, "fault": x, "t": t}


def sh(t, end="near"):
    return {"kind": "shot", "end": end, "t": t}


def b(t, cx, cy, out=False):
    return {"kind": "bounce", "out": out, "seen": True, "t": t, "court": [cx, cy]}


def net(t, k="stop"):
    return {"kind": "net", "net": k, "t": t}


PT = {"id": "p1", "startMs": 106000, "endMs": 112000}


class LabelMeasure(unittest.TestCase):
    def test_borders(self):
        # a first-serve fault at 100 (net stop), the second serve at 106, the point ends with a shot at 111
        ev = [sv(100.0), net(100.5), sv(106.0), b(106.8, 2.0, -4.0), sh(107.5), b(108.4, 1.0, 9.0), sh(111.0)]
        r = lm.borders([PT], [{"start": 105.0, "end": 112.0}], [(0, 0)], ev)
        self.assertEqual((r["dPlay"], r["dFirst"], r["dEnd"]), ([-1.0], [5.0], [1.0]))

    def test_chain_in(self):
        self.assertTrue(lm.chain_in([2.0, 9.0]))
        self.assertFalse(lm.chain_in([4.5, 9.0]))
        self.assertTrue(lm.chain_in([2.0, -4.0], "far"))                  # a far serve in the near half, in the box
        self.assertFalse(lm.chain_in([2.0, -7.0], "far"))                 # behind the service line
        self.assertFalse(lm.chain_in([2.0, -4.0], "far", side=-1))        # the wrong box
        self.assertTrue(lm.chain_in([-2.0, -4.0], "far", side=-1))

    def test_box_side(self):
        ev = sorted([sv(100.0), b(100.8, 2.5, -7.5, out=True), sv(106.0), b(106.8, 2.0, -4.0)], key=lambda e: e["t"])
        fr = lm._fr(ev)
        self.assertEqual(lm._box_side(ev, 2, fr), 1)                      # its own bounce is in
        self.assertEqual(lm._box_side(ev, 0, fr), 1)                      # the fault: the box of its second serve

    def test_event_errors(self):
        ev = sorted([sv(106.0), b(106.8, 2.0, -4.0), sh(107.5), b(108.4, 1.0, 9.0, out=True)], key=lambda e: e["t"])
        lb = [e for e in ev if e["kind"] == "bounce"]
        bev = [{"t": 106.82, "court": [2.1, -4.1]}, {"t": 108.38, "court": [1.0, 8.0]}]    # the second: chain says in
        r = lm.event_errors(ev, [], [], [], bev, lb, [(0, 0), (1, 1)], [], [], [], [PT], [{"start": 105.0, "end": 112.0, "shots": 3}], [(0, 0)])
        self.assertEqual((r["inOutN"], r["inOutOk"], r["serveBounces"], r["sideChecked"]), (2, 1, 1, 1))
        self.assertEqual((r["shotN"], r["shotOk"], r["shotDiff"]), (1, 0, [1]))
        self.assertEqual(r["bounceDt"], [0.02, -0.02])

    def test_fault_clips_and_total(self):
        ev = [sv(100.0), net(100.5), sv(106.0), b(106.8, 2.0, -4.0), sh(107.5)]
        rows = [{"start": 105.0, "end": 112.0, "fault": {"t": 99.2, "chain": {"shots": [{"t": 100.1}]}}},
                {"start": 130.0, "end": 140.0, "fault": {"t": 131.0}}]
        r = lm.fault_clips("m_none", rows, ev, (95.0, 145.0))
        self.assertEqual((r["clips"], r["right"], r["noServe"], r["labeledFaults"], r["found"]), (2, 1, 1, 1, 1))
        t = lm.total([{"faultClips": r, "borders": {"dPlay": [0.5], "detail": []}}])
        self.assertEqual((t["clips"], t["right"], t["dPlay"]), (2, 1, [0.5]))
        self.assertEqual(len(lm.report_rows(t)), 12)


if __name__ == "__main__":
    unittest.main()

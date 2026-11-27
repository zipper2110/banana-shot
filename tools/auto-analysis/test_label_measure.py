"""E170: label_measure on made-up marks. Usage: python -m unittest test_label_measure"""
import json
import unittest
import label_measure as lm
from label_faults import infer_faults


def sv(t, end="far", x=False):
    return {"kind": "serve", "end": end, "fault": x, "t": t}


def sh(t, end="near"):
    return {"kind": "shot", "end": end, "t": t}


def b(t, cx, cy, out=False):
    return {"kind": "bounce", "out": out, "seen": True, "t": t, "court": [cx, cy]}


def net(t, k="stop"):
    return {"kind": "net", "net": k, "t": t}


def fmap(ev):
    return {round(x["t"], 6): x["fault"] for x in infer_faults(ev)}


PT = {"id": "p1", "startMs": 106000, "endMs": 112000}
# a first-serve fault at 100 (net stop), the second serve at 106, the point ends with a shot at 111
EV = [sv(100.0), net(100.5), sv(106.0), b(106.8, 2.0, -4.0), sh(107.5), b(108.4, 1.0, 9.0), sh(111.0)]
ROW = {"start": 105.0, "end": 112.0, "shots": 9, "chain": {"shots": [{}, {}, {}, {}]}}


class LabelMeasure(unittest.TestCase):
    def test_borders_and_shot_count(self):
        pts = [{"point": PT, "row": ROW, "events": EV, "fault": fmap(EV)}]
        r = lm.borders(pts)
        self.assertEqual((r["dPlay"], r["dFirst"], r["dEnd"]), ([-1.0], [5.0], [1.0]))
        c = lm.shot_count(pts)                       # the chain shots (4), not the "shots" field (9), against 3 contacts
        self.assertEqual((c["n"], c["ok"], c["off1"], c["diff"]), (1, 0, 1, [1]))

    def test_shot_count_late_shots_do_not_count(self):
        late = dict(ROW, chain={"shots": [{}, {}, {}, {"late": True}]})    # A41: the 4th shot is after the time cut
        pts = [{"point": PT, "row": late, "events": EV, "fault": fmap(EV)}]
        self.assertEqual(lm.shot_count(pts)["diff"], [0])
        old = lm.SC_LATE
        try:
            lm.SC_LATE = False
            self.assertEqual(lm.shot_count(pts)["diff"], [1])
        finally:
            lm.SC_LATE = old

    def test_shot_count_point_after_the_window_end_is_not_comparable(self):
        pts = [{"point": PT, "row": ROW, "events": EV, "fault": fmap(EV)}]
        c = lm.shot_count(pts, (95.0, 111.5))           # A41: the point ends at 112, after the window end 111.5
        self.assertEqual((c["n"], c["notComparable"], c["diff"]), (0, 1, []))
        c = lm.shot_count(pts, (95.0, 112.0))           # the point ends at the window end: compared
        self.assertEqual((c["n"], c["notComparable"], c["diff"]), (1, 0, [1]))
        self.assertEqual(lm.totals([{"shotCount": lm.shot_count(pts, (95.0, 111.5))}])["shotCount"]["notComparable"], 1)
        old = lm.SC_WINDOW
        try:
            lm.SC_WINDOW = False
            self.assertEqual(lm.shot_count(pts, (95.0, 111.5))["n"], 1)
        finally:
            lm.SC_WINDOW = old

    def test_chain_in(self):
        self.assertTrue(lm.chain_in([2.0, 9.0], "near"))
        self.assertFalse(lm.chain_in([4.5, 9.0], "near"))
        self.assertFalse(lm.chain_in([2.0, 3.0], "far"))                  # a far shot on the far half ("own")
        self.assertTrue(lm.chain_in([2.0, -4.0], "far", True))            # a far serve in the near half, in the box
        self.assertFalse(lm.chain_in([2.0, -7.0], "far", True))           # behind the service line
        self.assertFalse(lm.chain_in([2.0, -4.0], "far", True, -1))       # the wrong box
        self.assertTrue(lm.chain_in([-2.0, -4.0], "far", True, -1))

    def test_box_side(self):
        ev = [sv(100.0), b(100.8, 2.5, -7.5, out=True), sv(106.0), b(106.8, 2.0, -4.0)]
        f = fmap(ev)
        self.assertEqual(lm._box_side(ev, 2, f), 1)                       # its own bounce is in
        self.assertEqual(lm._box_side(ev, 0, f), 1)                       # the fault: the box of its second serve

    def test_event_errors(self):
        ev = [sv(106.0), b(106.8, 2.0, -4.0), sh(107.5), b(108.4, 1.0, 9.0, out=True)]
        lb = [e for e in ev if e["kind"] == "bounce"]
        bev = [{"t": 106.82, "court": [2.1, -4.1]}, {"t": 108.38, "court": [1.0, 8.0]}]    # the second: chain says in
        r = lm.event_errors(ev, fmap(ev), None, [], [], [], bev, lb, [(0, 0), (1, 1)], [], [], [])
        self.assertEqual((r["inOutN"], r["inOutOk"], r["serveBounces"], r["sideChecked"]), (2, 1, 1, 1))
        self.assertEqual(r["bounceDt"], [0.02, -0.02])

    def test_fault_clips_and_totals(self):
        rows = [{"start": 105.0, "end": 112.0, "fault": {"t": 99.2, "chain": {"shots": [{"t": 100.1}]}}},
                {"start": 130.0, "end": 140.0, "fault": {"t": 131.0}}]
        r = lm.fault_clips(rows, EV, fmap(EV), [{"start": 99.0, "end": 101.0}])
        self.assertEqual((r["clips"], r["right"], r["noServe"], r["labeledFaults"], r["found"]), (2, 1, 1, 1, 1))
        self.assertEqual((r["hiddenRows"], r["hiddenWithFault"]), (1, 1))
        # a clip on the second serve of a double fault: doubleFault, not wrong
        ev2 = [sv(100.0), net(100.5), sv(106.0), b(106.8, 2.0, -8.0, out=True), sv(125.0)]
        r2 = lm.fault_clips([{"start": 105.0, "end": 108.0, "fault": {"t": 106.05}}], ev2, fmap(ev2), [])
        self.assertEqual((r2["doubleFault"], r2["wrong"]), (1, 0))
        t = lm.totals([{"faultClips": r, "borders": {"dPlay": [0.5, -1.5], "detail": []}}, {}])
        self.assertEqual((t["faultClips"]["clips"], t["faultClips"]["right"], t["rallyStartPlay"]["n"]), (2, 1, 2))
        self.assertEqual(t["rallyStartPlay"]["med"], -0.5)
        json.dumps(t)                                                     # JSON numbers only


if __name__ == "__main__":
    unittest.main()

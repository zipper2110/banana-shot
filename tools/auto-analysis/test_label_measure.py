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

    def test_chain_in(self):
        self.assertTrue(lm.chain_in([2.0, 9.0]))
        self.assertFalse(lm.chain_in([4.5, 9.0]))
        self.assertTrue(lm.chain_in([2.0, -4.0], "far"))                  # a far serve in the near half, in the box
        self.assertFalse(lm.chain_in([2.0, -7.0], "far"))                 # behind the service line
        self.assertFalse(lm.chain_in([2.0, -4.0], "far", side=-1))        # the wrong box
        self.assertTrue(lm.chain_in([-2.0, -4.0], "far", side=-1))

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
        t = lm.totals([{"faultClips": r, "borders": {"dPlay": [0.5, -1.5], "detail": []}}, {}])
        self.assertEqual((t["faultClips"]["clips"], t["faultClips"]["right"], t["rallyStartPlay"]["n"]), (2, 1, 2))
        self.assertEqual(t["rallyStartPlay"]["med"], -0.5)
        json.dumps(t)                                                     # JSON numbers only


if __name__ == "__main__":
    unittest.main()

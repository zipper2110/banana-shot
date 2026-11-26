"""E159: the rule of label_faults.infer_faults on made-up marks . Usage: python -m unittest test_label_faults"""
import unittest
from label_faults import infer_faults


def sv(t, end="far", x=False):
    return {"kind": "serve", "end": end, "fault": x, "t": t}


def b(t, cx=None, cy=-3.0, out=False):
    return {"kind": "bounce", "out": out, "seen": True, "t": t, **({"court": [cx, cy]} if cx is not None else {})}


def net(t, k="stop"):
    return {"kind": "net", "net": k, "t": t}


def res(ev, **k):
    return [(r["fault"], r["reason"]) for r in infer_faults(ev, **k)]


class LabelFaults(unittest.TestCase):
    def test_x_kept_and_ignored(self):
        ev = [sv(0, x=True), b(0.8, 1.0, out=True), sv(30)]
        assert res(ev)[0][0] is True and res(ev)[0][1] == "X mark"
        assert res(ev, use_x=False)[0][0] is False            # 30 s later: a new point


    def test_gap_limits(self):
        for gap, want in ((6.0, True), (12.6, True), (13.5, "unsure"), (14.6, False), (20.0, False)):
            assert res([sv(0), net(0.5), sv(gap)])[0][0] == want, gap


    def test_other_end_and_no_later_serve(self):
        assert res([sv(0), net(0.5), sv(6, "near")])[0][0] is False
        assert res([sv(0), net(0.5)])[0][0] == "unsure"


    def test_box(self):
        assert res([sv(0), b(0.8, 2.0, -8.0, out=True), sv(20), b(20.8, 2.0)])[0][0] == "unsure"     # same box, long gap
        assert res([sv(0), b(0.8, 2.0, -8.0, out=True), sv(9), b(9.8, 2.0)])[0][0] is True
        assert res([sv(0), b(0.8, 2.0, -8.0, out=True), sv(20), b(20.8, -2.0)])[0][0] is False
        assert res([sv(0), b(0.8, 2.0, -8.0, out=True), sv(9), b(9.8, -2.0)])[0][0] == "unsure"     # other box, short gap
        # out inside a service box = the wrong box: the side is the other side
        assert res([sv(0), b(0.8, 0.6, -4.0, out=True), sv(20), b(20.8, 1.2)])[0][0] is False


    def test_let_and_second_serve(self):
        assert res([sv(0), net(0.5, "cord"), b(0.7, 2.0), sv(12)])[0][0] == "unsure"
        assert res([sv(0), net(0.5, "cord"), b(0.7, 2.0, -8.0, out=True), sv(6)])[0][0] is True
        r = res([sv(0), net(0.5), sv(6), b(6.8, 2.0, -8.0, out=True)])           # fault, then a double fault, no later serve
        assert r[0][0] is True and r[1][0] is False and r[1][1].startswith("the second serve")


if __name__ == "__main__":
    unittest.main()

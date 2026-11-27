"""E186 (S5, A26): the fast court detector gives the same results as before.

court_auto.detect got faster parts (E186): the hypotheses are built with numpy, the two passes share the line mask,
the lines, the hypotheses and the coarse score with the rule off, and a vectorized pre-check (a superset of valid_H)
skips the sorted hypotheses that valid_H rejects. The results must not change:
  - the hypothesis builder against the old loops (kept here as the reference);
  - the pre-check accepts each hypothesis that valid_H accepts;
  - detect on 3 synthetic frames (a clear court, a court with clutter lines, no court) against the values of the
    old code (GOLDEN: made with the code before E186, commit 4dd32409).
E187 (S5, A26): the rule switches of court_track (default off) that make the stage faster and can change results:
  - the default values give the old results (test_detect_same with the explicit defaults);
  - smaller rules (fewer hypotheses, a smaller sorted search, fewer refined candidates, fewer coarse points) reach
    detect and give a valid model;
  - COURT_SKIP_AFTER: fit_window does not call detect after a window of that kind, and calls it when the switch is off.
Run: python -m unittest test_court_fast
"""
import os, sys, unittest
import numpy as np
import cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import court_auto as ca
import court_track as ct

W, H = 1920, 1080


def court_to_img():
    """A plausible camera behind the near baseline: image px of the 4 doubles corners."""
    src = np.array([(-ca.DW, -ca.BL), (ca.DW, -ca.BL), (ca.DW, ca.BL), (-ca.DW, ca.BL)], np.float32)
    dst = np.array([(330, 1010), (1590, 1010), (1230, 290), (690, 290)], np.float32)
    return cv2.getPerspectiveTransform(src, dst)


def frame(kind, seed=0):
    rng = np.random.default_rng(seed)
    img = np.full((H, W, 3), (70, 110, 60), np.uint8)
    img = np.clip(img.astype(int) + rng.integers(-12, 13, img.shape), 0, 255).astype(np.uint8)
    if kind in ("clear", "clutter"):
        G = court_to_img()
        for a, b in ca.SEGS:
            p = cv2.perspectiveTransform(np.array([[a, b]], np.float32), G)[0]
            cv2.line(img, tuple(int(v) for v in p[0]), tuple(int(v) for v in p[1]), (235, 235, 235), 4, cv2.LINE_AA)
    if kind == "clutter":
        for _ in range(25):
            x1, y1, x2, y2 = rng.integers(0, W), rng.integers(0, 330), rng.integers(0, W), rng.integers(0, 330)
            cv2.line(img, (int(x1), int(y1)), (int(x2), int(y2)), (220, 220, 220), 3, cv2.LINE_AA)
        for x in range(60, W, 140):
            cv2.line(img, (x, 0), (x + 40, 300), (200, 200, 200), 3, cv2.LINE_AA)
    return img


def old_hypotheses(hor, side, ww, hh):
    """The loops of detect_pass before E186."""
    src, dst = [], []
    for i in range(len(hor)):
        for j in range(i + 1, len(hor)):
            for a in range(len(side)):
                for b in range(a + 1, len(side)):
                    P = [ca.intersect(hor[r][:2], side[s][:2]) for r in (i, j) for s in (a, b)]
                    if any(p is None or abs(p[0]) > 6 * ww or abs(p[1]) > 6 * hh for p in P):
                        continue
                    for ri in range(4):
                        for rj in range(ri + 1, 4):
                            for ci in range(5):
                                for cj in range(ci + 1, 5):
                                    src.append(P)
                                    dst.append([(ca.COLS[ci], ca.ROWS[ri]), (ca.COLS[cj], ca.ROWS[ri]),
                                                (ca.COLS[ci], ca.ROWS[rj]), (ca.COLS[cj], ca.ROWS[rj])])
    return np.array(src, float), np.array(dst, float)


# detect() of the code before E186 on the frames above: (H, score, ok, n_hyp)
GOLDEN = {
    'clear': ([[0.13813858904043655, 0.021243054893256858, -126.47070625466702], [-0.0014554693758172146, -0.08581662397182106, 44.16006095571389], [0.0002018627977769908, 0.0057166626787568875, 1.0]], 0.6292416910930089, True, 12600),
    'clutter': ([[0.043925651699322416, -0.00016246793662883936, -42.03213501766996], [-6.846657435194546e-05, -0.11874145978537624, 60.351590654273686], [3.084086472864859e-06, 0.00399019705726158, 1.0]], 0.0, False, 121500),
    'none': (None, 14.0, False, None),
}


class FastCourt(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.frames = {k: frame(k) for k in ("clear", "clutter", "none")}

    def lines(self, img, n_h=10, n_s=14):
        mask = ca.line_mask(img)
        return ca.lines_hs(ca.find_lines(mask), n_h, n_s, img.shape[1], img.shape[0])

    def test_hypotheses_same(self):
        for k in ("clear", "clutter"):
            hor, side, _ = self.lines(self.frames[k])
            s0, d0 = old_hypotheses(hor, side, W, H)
            s1, d1 = ca.hypotheses(hor, side, W, H)
            self.assertEqual(s0.shape, s1.shape, k)
            self.assertTrue(np.array_equal(s0, s1) and np.array_equal(d0, d1), k)

    def test_precheck_superset(self):
        n_ok = 0
        for k in ("clear", "clutter"):
            hor, side, _ = self.lines(self.frames[k])
            src, dst = ca.hypotheses(hor, side, W, H)
            Hs, okh = ca.homog_batch(src, dst)
            Hs = Hs[okh][:40000]
            pre = ca.valid_pre(Hs, W, H)
            for i in range(len(Hs)):
                if ca.valid_H(Hs[i], W, H):
                    n_ok += 1
                    self.assertTrue(pre[i], (k, i))
            self.assertLess(pre.mean(), 0.5, k)          # the pre-check removes most of them
        self.assertGreater(n_ok, 0)

    def test_detect_same(self):
        if not GOLDEN:
            self.skipTest("no golden values")
        for k, (Hg, sg, okg, ng) in GOLDEN.items():
            Hd, sd, info = ca.detect(self.frames[k])
            self.assertEqual(info.get("ok"), okg, k)
            self.assertEqual(info.get("n_hyp"), ng, k)
            if Hg is None:
                self.assertIsNone(Hd, k)
            else:
                self.assertTrue(np.array_equal(np.asarray(Hd), np.asarray(Hg)), k)
                self.assertEqual(sd, sg, k)


    def test_defaults_explicit(self):
        """The E187 parameters at their default values give the old results."""
        Hg, sg, okg, ng = GOLDEN["clear"]
        Hd, sd, info = ca.detect(self.frames["clear"], 10, 14, 8, top=30000, coarse_n=24)
        self.assertTrue(np.array_equal(np.asarray(Hd), np.asarray(Hg)))
        self.assertEqual(sd, sg)
        self.assertIs(ca.coarse_points(24), ca.COARSE)
        self.assertEqual(len(ca.coarse_points(12)), 12 * len(ca.SEGS))
        self.assertEqual(ct.rules(), ((10, 14), 30000, 8, 24, ()))
        self.assertEqual(ct.rules("8,10", "5000", "4", "12", "fail"), ((8, 10), 5000, 4, 12, ("lost", "weak")))

    def test_small_rules_clear(self):
        """Each smaller rule reaches detect: fewer hypotheses give a smaller n_hyp, and each rule still gives a valid
        ok model on the clear frame. (The 2 px check is for the real windows of E187: on this synthetic frame the
        default model fits the lines with cost 0.63 but is not the drawn court, and n_refine=4 picks another model.)"""
        ng = GOLDEN["clear"][3]
        for kw in (dict(n_h=8, n_s=10), dict(top=5000), dict(n_refine=4), dict(coarse_n=12)):
            Hd, sd, info = ca.detect(self.frames["clear"], **kw)
            self.assertTrue(info.get("ok") and ca.valid_H(np.asarray(Hd)), kw)
            self.assertEqual(info["n_hyp"], ng, kw)          # the clear frame has fewer lines than 8 and 10
        hor, side, _ = self.lines(self.frames["clutter"], 6, 8)       # 10 side lines at the default; the pseudo lines refill hor
        self.assertLess(len(ca.hypotheses(hor, side, W, H)[0]), len(ca.hypotheses(*self.lines(self.frames["clutter"])[:2], W, H)[0]))

    def test_skip_after(self):
        """COURT_SKIP_AFTER: no detect after a window of that kind; detect when the switch is off."""
        calls = []
        img = self.frames["none"]
        old = ct.window_frame, ca.detect, ct.SKIP_AFTER
        start = np.array([(500, 600), (1400, 600), (300, 900), (1600, 900)], np.float32)
        try:
            ct.window_frame = lambda *a, **k: img
            ca.detect = lambda *a, **k: (calls.append(1), (None, ct.CAP, {"ok": False}))[1]
            for skip, prev, n in (((), "lost", 1), (("lost",), "lost", 0), (("lost",), "weak", 1),
                                  (("lost", "weak"), "weak", 0), (("lost", "weak"), None, 1)):
                calls.clear()
                ct.SKIP_AFTER = skip
                out = ct.fit_window("x", 0, 100, start, 0.5, prev_how=prev)
                self.assertEqual(len(calls), n, (skip, prev))
                self.assertEqual(out[4], "lost", (skip, prev))
        finally:
            ct.window_frame, ca.detect, ct.SKIP_AFTER = old


if __name__ == "__main__":
    unittest.main()

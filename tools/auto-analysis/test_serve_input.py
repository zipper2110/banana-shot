"""Tests of A54 (E215): the serve events of the flights as an input of the rally GRU (rally_model.serve_feat, the
"ballsv" model, SERVE_INPUT). Usage: python -m unittest test_serve_input"""
import os, unittest
import numpy as np
import rally_model as rm


def fl(t, near, kind):
    return (t, near, kind, {})


class ServeInput(unittest.TestCase):
    def setUp(self):
        self.old = (rm.SERVE_INPUT, rm.MODEL_DIR, rm.SEED_DIR, rm.SEEDS)
        self.events = None
        import serve_ball
        self.sb = serve_ball
        self.orig = serve_ball.serve_events

        def ev(flights, t0, t1, rally_filter=None):
            return [e for e in self.events if t0 <= e[0] <= t1]
        serve_ball.serve_events = ev

    def tearDown(self):
        rm.SERVE_INPUT, rm.MODEL_DIR, rm.SEED_DIR, rm.SEEDS = self.old
        self.sb.serve_events = self.orig

    def test_off_by_default(self):
        self.assertFalse(self.old[0])

    def test_off_gives_the_old_model_and_files(self):
        rm.SERVE_INPUT = False
        self.assertEqual(rm.ball_name(), "ball")
        self.assertEqual(os.path.basename(rm.path("ball")), "rally_ball_t2_h.pt")
        rm.SERVE_INPUT = True
        self.assertEqual(rm.ball_name(), "ballsv")
        self.assertEqual(rm.MODELS["ballsv"], rm.MODELS["ball"] + ["serve"])
        self.assertEqual(os.path.basename(rm.path("ballsv", "m1020", 2)), "rally_ballsv_t2_h_xm1020_s2.pt")

    def test_model_dir(self):
        rm.MODEL_DIR, rm.SEED_DIR = os.path.join("x", "m"), None
        self.assertEqual(os.path.dirname(rm.path("ballsv")), rm.MODEL_DIR)
        self.assertEqual(os.path.dirname(rm.path("ballsv", None, 1)), rm.MODEL_DIR)
        rm.MODEL_DIR = None
        self.assertNotEqual(os.path.dirname(rm.path("ball")), os.path.join("x", "m"))

    def test_the_feature_columns(self):
        self.events = [fl(3.4, True, "serve"), fl(7.9, False, "toss+serve"), fl(12.0, False, "toss"),
                       fl(15.2, True, "toss+fast"), fl(30.0, True, "serve")]
        f = rm.serve_feat([], 20)
        self.assertEqual(f.shape, (20, 3))
        self.assertEqual(f.dtype, np.float32)
        want = np.zeros((20, 3), np.float32)
        want[3, 0] = want[7, 1] = want[12, 2] = want[15, 0] = 1.0     # the event at 30 s is past the end
        np.testing.assert_array_equal(f, want)

    def test_no_flights(self):
        self.events = []
        np.testing.assert_array_equal(rm.serve_feat(None, 4), np.zeros((4, 3), np.float32))

    def test_load_parts_adds_the_serve_part_last(self):
        self.events = [fl(1.2, True, "serve")]
        old_lw, old_n = rm.load_wasbf, rm.lab.n_seconds
        got = {}

        def lw(mid, feats, pca, folder):
            got["feats"] = list(feats)
            return [(n, np.zeros((5, 2), np.float32)) for n in feats]
        rm.load_wasbf, rm.lab.n_seconds = lw, (lambda mid: 5)
        try:
            parts = rm.load_parts("m1020", rm.MODELS["ballsv"], 0, None, flights=[])
            self.assertEqual(got["feats"], rm.MODELS["ball"])
            self.assertEqual([n for n, _ in parts], rm.MODELS["ballsv"])
            self.assertEqual(parts[-1][1][1, 0], 1.0)
            self.assertEqual(len(rm.load_parts("m1020", rm.MODELS["ball"], 0, None)), 4)
        finally:
            rm.load_wasbf, rm.lab.n_seconds = old_lw, old_n


if __name__ == "__main__":
    unittest.main()

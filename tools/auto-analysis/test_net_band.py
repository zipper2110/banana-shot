"""Tests of the net band modules (item 6 (b)). Usage: python -m unittest test_net_band"""
import ast, os, tempfile, unittest
import numpy as np
import net_band_mask as nbm
import net_band as nb

HERE = os.path.dirname(os.path.abspath(__file__))
# The court homography of m1030 (court.json "H": px of the 1920 frame -> court m).
HM = [[-1.36228949e-02, 9.22318169e-04, 1.23968202e+01],
      [9.32950248e-04, 8.76182802e-02, -4.43579312e+01],
      [-2.86331783e-05, -4.19977237e-03, 1.00000000e+00]]


class Level1Key(unittest.TestCase):
    def test_no_folder_module_at_the_top(self):
        # The level-1 key is the ball tag and the content of net_band_mask.py only (a hand list). A module of the
        # folder that is imported at the top would change the masks without a key change.
        tree = ast.parse(open(os.path.join(HERE, "net_band_mask.py"), encoding="utf-8").read())
        names = set()
        for n in tree.body:
            if isinstance(n, ast.Import):
                names |= {a.name.split(".")[0] for a in n.names}
            elif isinstance(n, ast.ImportFrom):
                names.add(n.module.split(".")[0])
        own = {f[:-3] for f in os.listdir(HERE) if f.endswith(".py")}
        self.assertEqual(names & own, set())

    def test_camera_is_the_copy_of_ball_3d(self):
        import ball_3d
        Hm = np.array(HM)
        P1, f1, C1 = nbm.camera(Hm)
        P2, f2, C2 = ball_3d.camera(Hm)
        np.testing.assert_allclose(P1, P2)
        X = np.array([[0.0, 0.0, 0.914], [6.4, 0.0, 1.07], [-4.1, 11.89, 0.0]])
        np.testing.assert_allclose(nbm.project(P1, X), ball_3d.project(P2, X))


class TwoLevels(unittest.TestCase):
    def test_masks_then_bins(self):
        Hm = np.array(HM)
        crop = (0, 0, 672, 378)
        w = nbm.MaskWriter(Hm, crop, 0.35, 2)
        self.assertIsNotNone(w.box)
        rng = np.random.default_rng(0)
        for _ in range(8):
            w.feed((rng.random((378, 672, 3)) * 255).astype(np.uint8))
        d = tempfile.mkdtemp(prefix="nb_")
        m = w.save(os.path.join(d, "mask.npz"))
        M = nbm.Masks(m)
        self.assertEqual(len(M), 6)                       # the first 2 pictures have no picture 1/30 s before
        self.assertEqual(list(M.frame[:2]), [2, 3])
        self.assertEqual(list(M.prev[:2]), [0, 1])
        b = nb.build(m, np.zeros((0, 5)), Hm, os.path.join(d, "band.npz"))
        z = np.load(b)
        self.assertEqual(z["band"].shape, (6, len(nb.BIN_EDGES) - 1))
        self.assertGreater(np.nanmean(z["band"].astype(float)), 0.5)   # random pictures: most pixels move


if __name__ == "__main__":
    unittest.main()

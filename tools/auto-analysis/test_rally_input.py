"""Tests of the head GRU input of the rally model (E192) and the key of the rallies stage (G12).
Usage: python -m unittest test_rally_input"""
import os, shutil, tempfile, unittest
import numpy as np
import lab
import rally_model as rm
import detect

MID = "m1020"
N = 40


class ModelNames(unittest.TestCase):
    def tearDown(self):
        rm.HEAD_INPUT = True

    def test_the_default_is_the_head_input(self):
        self.assertTrue(rm.HEAD_INPUT)
        self.assertEqual(os.path.basename(rm.path("ball")), "rally_ball_t2_h.pt")
        self.assertEqual(os.path.basename(rm.path("ball", "m1015")), "rally_ball_t2_h_xm1015.pt")

    def test_one_setting_gives_the_old_files(self):
        rm.HEAD_INPUT = False
        self.assertEqual(os.path.basename(rm.path("ball")), "rally_ball_t2.pt")
        self.assertEqual(os.path.basename(rm.path("ball", "m1015")), "rally_ball_t2_xm1015.pt")

    def test_a_model_with_no_ball_features_keeps_its_name(self):
        self.assertEqual(os.path.basename(rm.path("base")), "rally_base_t2.pt")


class LoadWasbf(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="ri_")
        self.lab_dir, self.head = os.path.join(self.tmp, "lab"), os.path.join(self.tmp, "head")
        rng = np.random.default_rng(2)
        for d in (self.lab_dir, self.head):
            os.makedirs(d)
            np.save(os.path.join(d, "pts.npy"), np.arange(N * 10) / 10.0)
            np.save(os.path.join(d, "feat_wasbf.npy"), rng.normal(size=(N, 3)).astype(np.float32))
        np.save(os.path.join(self.lab_dir, "feat_audio.npy"), rng.normal(size=(N, 2)).astype(np.float32))
        self.old = lab.mdir, lab.n_seconds
        lab.mdir, lab.n_seconds = (lambda m: self.lab_dir), (lambda m: N)

    def tearDown(self):
        lab.mdir, lab.n_seconds = self.old
        shutil.rmtree(self.tmp, ignore_errors=True)

    def one(self, folder, name):
        mdir = lab.mdir
        lab.mdir = lambda m: folder
        try:
            return dict(rm.load(MID, [name], 0))[name]
        finally:
            lab.mdir = mdir

    def test_the_folder_replaces_only_wasbf(self):
        got = dict(rm.load_wasbf(MID, ["audio", "wasbf"], 0, self.head))
        np.testing.assert_array_equal(got["wasbf"], self.one(self.head, "wasbf"))
        np.testing.assert_array_equal(got["audio"], self.one(self.lab_dir, "audio"))
        self.assertFalse(np.array_equal(got["wasbf"], self.one(self.lab_dir, "wasbf")))

    def test_no_folder_gives_the_lab_files(self):
        got = dict(rm.load_wasbf(MID, ["audio", "wasbf"], 0, None))
        np.testing.assert_array_equal(got["wasbf"], self.one(self.lab_dir, "wasbf"))


class RallyKey(unittest.TestCase):
    def test_the_key_follows_the_model_file(self):
        d = tempfile.mkdtemp(prefix="rk_")
        old = rm.path
        try:
            rm.path = lambda name, fold=None: os.path.join(d, f"rally_{name}.pt")
            open(rm.path("ball"), "wb").write(b"model one")
            a = detect.rally_key("ball")
            self.assertEqual(detect.rally_key("ball"), a)
            open(rm.path("ball"), "wb").write(b"model two")
            self.assertNotEqual(detect.rally_key("ball"), a)
        finally:
            rm.path = old
            shutil.rmtree(d, ignore_errors=True)

    def test_the_old_and_the_new_model_give_other_keys(self):
        old = rm.HEAD_INPUT
        try:
            new = detect.rally_key("ball")
            rm.HEAD_INPUT = False
            self.assertNotEqual(detect.rally_key("ball"), new)
        finally:
            rm.HEAD_INPUT = old


class SeedMean(unittest.TestCase):
    """A37: the rally GRU as the mean of SEEDS models (default 1 = off)."""
    def setUp(self):
        self.old = rm.SEEDS, rm.SEED_DIR, rm.predict_one, rm.path

    def tearDown(self):
        rm.SEEDS, rm.SEED_DIR, rm.predict_one, rm.path = self.old

    def test_the_default_is_one_model(self):
        self.assertEqual(rm.SEEDS, 1)
        self.assertEqual(rm.model_files("ball", "m1015"), [rm.path("ball", "m1015")])

    def test_seed_file_names(self):
        self.assertEqual(rm.path("ball", "m1015", 0), rm.path("ball", "m1015"))
        self.assertEqual(os.path.basename(rm.path("ball", "m1015", 2)), "rally_ball_t2_h_xm1015_s2.pt")
        self.assertEqual(os.path.basename(rm.path("ball", None, 1)), "rally_ball_t2_h_s1.pt")
        rm.SEED_DIR = os.path.join("x", "seeds")
        self.assertEqual(os.path.dirname(rm.path("ball", None, 1)), rm.SEED_DIR)
        self.assertNotEqual(os.path.dirname(rm.path("ball")), rm.SEED_DIR)      # seed 0 stays in the models folder

    def test_the_mean_of_the_seeds(self):
        got = []

        def one(mid, name, fold=None, wasbf=None, seed=0):
            got.append(seed)
            return np.array([0.1, 0.9, 0.5], np.float32) * (seed + 1)
        rm.predict_one = one
        np.testing.assert_allclose(rm.predict("m1020", "ball", "m1020"), [0.1, 0.9, 0.5])
        self.assertEqual(got, [0])
        got.clear()
        rm.SEEDS = 3
        np.testing.assert_allclose(rm.predict("m1020", "ball", "m1020"), [0.2, 1.8, 1.0], rtol=1e-6)
        self.assertEqual(got, [0, 1, 2])
        np.testing.assert_allclose(rm.mean_prob([np.zeros(2), np.ones(2)]), [0.5, 0.5])

    def test_the_rally_key_with_seeds(self):
        d = tempfile.mkdtemp(prefix="rs_")
        try:
            rm.path = lambda name, fold=None, seed=0: os.path.join(d, f"rally_{name}_s{seed}.pt")
            for k in range(3):
                open(rm.path("ball", None, k), "wb").write(b"model %d" % k)
            one = detect.rally_key("ball")
            rm.SEEDS = 3
            three = detect.rally_key("ball")
            self.assertNotEqual(one, three)
            open(rm.path("ball", None, 2), "wb").write(b"model two again")
            self.assertNotEqual(detect.rally_key("ball"), three)
            rm.SEEDS = 1
            self.assertEqual(detect.rally_key("ball"), one)
        finally:
            shutil.rmtree(d, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()

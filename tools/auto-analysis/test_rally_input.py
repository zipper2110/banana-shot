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


if __name__ == "__main__":
    unittest.main()

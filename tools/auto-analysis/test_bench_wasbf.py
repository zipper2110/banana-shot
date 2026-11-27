"""Tests of the rally GRU ball input of the bench lab set (E101: feat_wasbf.npy from the flights of the code key).
Usage: python -m unittest test_bench_wasbf"""
import os, shutil, tempfile, unittest
import numpy as np
import bench
import lab
import rally_model
from ball_full import WINDOWS

MID = "m1020"
N = 40                                                # seconds of the made-up match


class WasbfKey(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="wf_")
        self.fl = os.path.join(self.tmp, "flights")
        os.makedirs(self.fl)
        for n in ("fullflights_s0.35.json", "pts.npy", "wasb_%d_%d_s0.35%s_k2.npy" % (*WINDOWS[MID], bench.LAB_BLOB_TAG)):
            open(os.path.join(self.fl, n), "w").write(n)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def key(self):
        return os.path.basename(os.path.dirname(bench.wasbf_key(MID, self.fl, bench.HERE)[1]))

    def test_the_key_follows_the_flights(self):
        a = self.key()
        open(os.path.join(self.fl, "fullflights_s0.35.json"), "w").write("other flights")
        self.assertNotEqual(self.key(), a)

    def test_a_set_switch_of_the_feature_code_changes_the_key(self):
        # SERVE_FILTER is read in serve_ball.py, which feat_wasb imports: it is in the closure of the step
        os.environ.pop("SERVE_FILTER", None)
        a = self.key()
        os.environ["SERVE_FILTER"] = "0"
        try:
            self.assertEqual(bench.env_switches(bench.HERE, ("feat_wasb",)), {"SERVE_FILTER": "0"})
            self.assertNotEqual(self.key(), a)
        finally:
            del os.environ["SERVE_FILTER"]
        self.assertEqual(self.key(), a)

    def test_the_key_has_the_feature_code(self):
        cc = bench.closure(bench.HERE)
        names = {os.path.basename(f) for f in cc.files("feat_wasb", folder=bench.HERE)}
        self.assertTrue({"feat_wasb.py", "serve_ball.py", "frame_times.py"} <= names)


class WasbfLoad(unittest.TestCase):
    """run_lab gives the GRU the feat_wasbf.npy of the code-key folder and the other inputs of the lab folder."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="wl_")
        self.lab_dir, self.key_dir = os.path.join(self.tmp, "lab"), os.path.join(self.tmp, "key")
        rng = np.random.default_rng(1)
        for d in (self.lab_dir, self.key_dir):
            os.makedirs(d)
            np.save(os.path.join(d, "pts.npy"), np.arange(N * 10) / 10.0)
            np.save(os.path.join(d, "feat_wasbf.npy"), rng.normal(size=(N, 3)).astype(np.float32))
        np.save(os.path.join(self.lab_dir, "feat_audio.npy"), rng.normal(size=(N, 2)).astype(np.float32))
        self.old = (lab.mdir, bench._run_lab, rally_model.load)
        lab.mdir = lambda m: self.lab_dir
        bench._run_lab = lambda nets, pipeline, chain: dict(rally_model.load(MID, ["audio", "wasbf"], 0))

    def tearDown(self):
        lab.mdir, bench._run_lab, rally_model.load = self.old
        shutil.rmtree(self.tmp, ignore_errors=True)

    def expected(self, folder, name):
        mdir = lab.mdir
        lab.mdir = lambda m: folder
        try:
            return dict(self.old[2](MID, [name], 0))[name]
        finally:
            lab.mdir = mdir

    def test_the_code_key_file_replaces_only_wasbf(self):
        got = bench.run_lab({}, {MID: self.key_dir})
        np.testing.assert_array_equal(got["wasbf"], self.expected(self.key_dir, "wasbf"))
        np.testing.assert_array_equal(got["audio"], self.expected(self.lab_dir, "audio"))
        self.assertIs(rally_model.load, self.old[2])  # the load of the model is the old one again

    def test_none_reads_the_frozen_lab_file(self):
        got = bench.run_lab({}, None)
        np.testing.assert_array_equal(got["wasbf"], self.expected(self.lab_dir, "wasbf"))

    def test_auto_with_frozen_flights_reads_the_frozen_lab_file(self):
        old = bench.LAB_FLIGHTS
        bench.LAB_FLIGHTS = "frozen"
        try:
            got = bench.run_lab({MID: os.path.join(self.key_dir, "net_events.json")})
        finally:
            bench.LAB_FLIGHTS = old
        np.testing.assert_array_equal(got["wasbf"], self.expected(self.lab_dir, "wasbf"))


if __name__ == "__main__":
    unittest.main()

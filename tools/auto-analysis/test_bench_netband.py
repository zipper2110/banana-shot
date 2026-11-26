"""Tests of the net band switch of bench.py (BENCH_NETBAND_DIR, S5 item 6 (d)).
Usage: python -m unittest test_bench_netband"""
import hashlib, json, os, shutil, tempfile, unittest
import bench
import lab

MID = "m1020"


class NetBandSwitch(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="nb_")
        self.mdir = os.path.join(self.tmp, "data", MID)
        os.makedirs(self.mdir)
        for n in bench.NET_COPY:
            open(os.path.join(self.mdir, n), "w").write(n)
        self.old = (lab.mdir, bench.NETBAND_DIR, bench.LAB_FLIGHTS)
        lab.mdir = lambda m: self.mdir
        bench.LAB_FLIGHTS = "remake"
        self.band_dir = os.path.join(self.tmp, "nb")

    def tearDown(self):
        lab.mdir, bench.NETBAND_DIR, bench.LAB_FLIGHTS = self.old
        shutil.rmtree(self.tmp, ignore_errors=True)

    def band(self, text):
        os.makedirs(os.path.join(self.band_dir, MID), exist_ok=True)
        open(os.path.join(self.band_dir, MID, "net_band.npz"), "w").write(text)

    def key(self):
        return os.path.basename(os.path.dirname(bench.net_key(MID, bench.HERE)[3]))

    def test_off_reads_no_band_and_keeps_the_key_of_before(self):
        bench.NETBAND_DIR = ""
        self.band("a")                               # a band file that the run must not read
        kind, src = bench.net_inputs(MID)
        self.assertEqual(kind, "lab")
        self.assertFalse(any(os.path.basename(f) == "net_band.npz" for f in src))
        # The key of the runs before the switch: the code files and the inputs that exist, then the flight switches.
        h = hashlib.sha256()
        for f in bench.code_files(bench.HERE, True) + [f for f in src if os.path.exists(f)]:
            h.update(os.path.basename(f).encode() + bench.sha(f).encode())
        h.update(json.dumps({k: os.environ.get(k) for k in bench.FLIGHT_ENV}, sort_keys=True).encode())
        self.assertEqual(self.key(), h.hexdigest()[:16])

    def test_on_adds_the_band_file_to_the_inputs_and_the_key(self):
        bench.NETBAND_DIR = ""
        off = self.key()
        bench.NETBAND_DIR = self.band_dir
        self.band("a")
        self.assertIn(os.path.join(self.band_dir, MID, "net_band.npz"), bench.net_inputs(MID)[1])
        on_a = self.key()
        self.band("b")
        on_b = self.key()
        self.assertNotEqual(off, on_a)
        self.assertNotEqual(on_a, on_b)                # a changed band file gives a new key

    def test_on_with_a_missing_band_file_stops(self):
        bench.NETBAND_DIR = self.band_dir
        with self.assertRaises(FileNotFoundError):
            bench.net_key(MID, bench.HERE)

    def test_the_band_folder_is_not_a_switch_of_the_manifest(self):
        os.environ["BENCH_NETBAND_DIR"] = self.band_dir
        try:
            self.assertNotIn("BENCH_NETBAND_DIR", bench.switches()["env"])
        finally:
            del os.environ["BENCH_NETBAND_DIR"]

    def test_band_in_finds_only_a_copied_band(self):
        d = os.path.join(self.tmp, "cache")
        os.makedirs(d)
        self.assertIsNone(bench.band_in(d))
        open(os.path.join(d, "net_band.npz"), "w").write("a")
        self.assertEqual(bench.band_in(d), os.path.join(d, "net_band.npz"))


if __name__ == "__main__":
    unittest.main()

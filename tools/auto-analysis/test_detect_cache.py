"""Tests of the cache keys of detect.py (chain-inventory G12). Usage: python -m unittest test_detect_cache"""
import json, os, subprocess, tempfile, unittest
import numpy as np
import detect

SAMPLE = r"D:\bananashot-lab\samples\m1020_sample.mp4"     # a real video for the length check of the adoption


def tmp_file(data):
    p = os.path.join(tempfile.mkdtemp(prefix="dc_"), "f.bin")
    open(p, "wb").write(data)
    return p


class DetectCache(unittest.TestCase):
    def test_video_id_is_the_value_of_the_app(self):
        # The same bytes as DetectCacheTest.kt (VideoIdentity.of).
        self.assertEqual(detect.video_id(tmp_file(bytes((i * 7 + 3) % 256 for i in range(1000)))),
                         "c1330d6024ad321e3cb274a568501a83beafd4fd")

    def test_work_folder_of_another_video_stops_the_run(self):
        work = tempfile.mkdtemp(prefix="dc_")
        a, b = tmp_file(b"a" * 5000), tmp_file(b"b" * 5000)
        vid = detect.check_video(work, a)
        self.assertEqual(json.load(open(os.path.join(work, "video.json")))["id"], vid)
        self.assertEqual(detect.check_video(work, a), vid)          # the same video: no stop
        with self.assertRaises(RuntimeError):
            detect.check_video(work, b)

    def test_content_key_follows_the_bytes_not_the_line_ends(self):
        lf, crlf = tmp_file(b"x = 1\ny = 2\n"), tmp_file(b"x = 1\r\ny = 2\r\n")
        self.assertEqual(detect.content_key("w", files=[lf]), detect.content_key("w", files=[crlf]))
        self.assertNotEqual(detect.content_key("w", files=[lf]), detect.content_key("w2", files=[lf]))
        self.assertNotEqual(detect.content_key("w", files=[lf]), detect.content_key("w", files=[tmp_file(b"x = 2\n")]))

    def test_a_changed_key_runs_the_stage_again(self):
        work = tempfile.mkdtemp(prefix="dc_")
        out = os.path.join(work, "out.txt")
        open(out, "w").write("old")
        detect.KEYS_FILE = os.path.join(work, "stage_keys.json")
        detect.STAGE_KEYS.clear()
        runs = []
        detect.stage("flights", [out], lambda: runs.append(1), key="k1")
        detect.stage("flights", [out], lambda: runs.append(2), key="k1")
        detect.stage("flights", [out], lambda: runs.append(3), key="k2")
        self.assertEqual(runs, [1, 3])
        self.assertEqual(json.load(open(detect.KEYS_FILE)), {"flights": "k2"})


@unittest.skipUnless(os.path.exists(SAMPLE), "no sample video")
class AdoptBall(unittest.TestCase):
    """E52: an old ball file gets the name of the new cache key only when the checks pass."""

    def setUp(self):
        import platform_cfg
        self.work = tempfile.mkdtemp(prefix="ab_")
        wd = tempfile.mkdtemp(prefix="ab_w_")
        open(os.path.join(wd, "w.pth.tar"), "wb").write(b"weights")
        self._wd, platform_cfg.weights_dir = platform_cfg.weights_dir, lambda: wd
        r = subprocess.run([platform_cfg.tool("ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                            SAMPLE], capture_output=True, text=True)
        dur = float(r.stdout)
        np.save(os.path.join(self.work, "pts.npy"), np.arange(0, dur, 1 / 60))
        open(os.path.join(self.work, "kf_times.csv"), "w").write("\n".join(str(k) for k in range(int(dur))) + "\n")
        open(os.path.join(self.work, "court.json"), "w").write("{}")
        self.n = int(dur) + 1

    def tearDown(self):
        import platform_cfg
        platform_cfg.weights_dir = self._wd

    def old(self, last_s, n=None):
        f = os.path.join(self.work, f"wasb_0_{n or self.n}_s0.35_k2.npy")
        fr = np.arange(0, last_s * 60, 2, dtype=np.float32)
        np.save(f, np.stack([fr, fr, fr, fr, fr], 1))
        return f

    def test_adopt_copies_the_file_and_records_it(self):
        old = self.old(self.n - 3)
        rec = detect.adopt_ball(self.work, SAMPLE, os.path.basename(old), "w.pth.tar", "dim")
        new = os.path.join(self.work, rec["file"])
        self.assertTrue(rec["file"].startswith(f"wasb_0_{self.n}_s0.35_w_dim_") and rec["file"].endswith("_k2.npy"))
        self.assertEqual(open(new, "rb").read(), open(old, "rb").read())
        self.assertTrue(os.path.exists(old))                         # the old name stays for the lab scripts
        keys = json.load(open(os.path.join(self.work, "stage_keys.json")))
        self.assertEqual(keys["ballAdopted"]["from"], os.path.basename(old))
        self.assertEqual(keys["ballAdopted"]["weights"], "w.pth.tar")
        self.assertEqual(json.load(open(os.path.join(self.work, "video.json")))["id"], detect.video_id(SAMPLE))
        import lab
        self.assertEqual(lab.BLOB_TAG, detect.ball_tag("w.pth.tar", "dim", self.n, self.work))   # the name of a run
        with self.assertRaises(RuntimeError):                       # a second adoption: the file is there
            detect.adopt_ball(self.work, SAMPLE, old, "w.pth.tar", "dim")

    def test_a_later_write_to_the_new_name_does_not_change_the_old_file(self):
        # A ball run with --force writes the new name again (np.save truncates the path in place).
        old = self.old(self.n - 3)
        before = open(old, "rb").read()
        rec = detect.adopt_ball(self.work, SAMPLE, old, "w.pth.tar", "dim")
        np.save(os.path.join(self.work, rec["file"]), np.zeros((1, 5), np.float32))
        self.assertEqual(open(old, "rb").read(), before)

    def test_checks_stop_the_adoption(self):
        cases = [(self.n + 30, SAMPLE, "w.pth.tar"),                 # detections after the end: another video
                 (self.n // 2, SAMPLE, "w.pth.tar"),                 # the old stage did not end
                 (self.n - 3, tmp_file(b"x" * 5000), "w.pth.tar"),   # another video file
                 (self.n - 3, SAMPLE, "other.pth.tar")]              # weights that are not there
        for last, video, w in cases:
            with self.assertRaises(RuntimeError):
                detect.adopt_ball(self.work, video, self.old(last), w, "dim")
        self.assertFalse(os.path.exists(os.path.join(self.work, "video.json")))   # no record of a wrong video
        self.assertFalse(any("_w_dim_" in f for f in os.listdir(self.work)))


if __name__ == "__main__":
    unittest.main()

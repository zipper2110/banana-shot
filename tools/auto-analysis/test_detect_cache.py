"""Tests of the cache keys of detect.py (chain-inventory G12). Usage: python -m unittest test_detect_cache"""
import json, os, tempfile, unittest
import detect


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


if __name__ == "__main__":
    unittest.main()

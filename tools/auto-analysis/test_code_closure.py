"""Tests of the code part of the stage keys (G12 follow-up, S2 item 13). Usage: python -m unittest test_code_closure"""
import json, os, tempfile, unittest
import numpy as np
import code_closure
import detect

# The modules in the import closure of the ball stage and of the pose stage that are not in their hand lists
# (detect.BALL_CODE, detect.POSE_CODE), each with the reason why a change of it does not change the output
# (decision 90f2389d). A new module in a closure makes the test fail: put it into the hand list, or here with a reason.
BALL_NO_EFFECT = {"lab": "paths, the video path and the match table; the time base enters the tag through n"}
POSE_NO_EFFECT = {"lab": "the path of the work folder"}


def module_folder(sources):
    d = tempfile.mkdtemp(prefix="cc_")
    for name, text in sources.items():
        open(os.path.join(d, name + ".py"), "w").write(text)
    return d


def names(code):
    return {f[:-3] for f in code}


class CodeClosure(unittest.TestCase):
    def test_follows_imports_also_inside_functions(self):
        d = module_folder({"a": "import os\ndef f():\n    import b\n", "b": "from c import x\nimport numpy as np\n",
                           "c": "x = 1\nfrom . import d\n", "d": "y = 2\n"})
        self.assertEqual(code_closure.modules("a", folder=d), ["a", "b", "c"])     # no os, numpy; no relative import

    def test_flights_key_has_serve_ball(self):
        # The gap that S1 found: feat_wasb imports serve_ball, and feat_wasb.npy is an output of the flights stage.
        mods = code_closure.modules(*detect.STAGE_ENTRIES["flights"])
        for m in ("serve_ball", "feat_wasb", "ball_full", "net_events", "court_refine", "frame_times"):
            self.assertIn(m, mods)

    def test_each_closure_stage_has_its_entry_modules(self):
        for stage, entries in detect.STAGE_ENTRIES.items():
            for m in entries:
                self.assertTrue(os.path.exists(os.path.join(code_closure.HERE, m + ".py")), f"{stage}: {m}")

    def test_ball_tag_covers_its_closure(self):
        keyed = names(detect.BALL_CODE)
        self.assertEqual(set(code_closure.modules(*keyed)) - keyed, set(BALL_NO_EFFECT))

    def test_pose_key_covers_its_closure(self):
        keyed = names(detect.POSE_CODE)
        self.assertEqual(set(code_closure.modules(*keyed)) - keyed, set(POSE_NO_EFFECT))

    def test_key_changes_with_an_imported_module(self):
        d = module_folder({"a": "import b\n", "b": "v = 1\n"})
        k1 = detect.content_key(files=code_closure.files("a", folder=d))
        open(os.path.join(d, "b.py"), "w").write("v = 2\n")
        self.assertNotEqual(detect.content_key(files=code_closure.files("a", folder=d)), k1)


class GitChanges(unittest.TestCase):
    def test_git_log_of_this_folder(self):
        import time
        self.assertTrue(detect.git_changes(["detect.py"], 0))                     # detect.py has commits
        self.assertEqual(detect.git_changes(["detect.py"], time.time() + 86400), [])  # none after tomorrow


class AdoptKeys(unittest.TestCase):
    """detect.adopt_keys: a ball file of the old tag and a pose.npy get the new keys only with no git change."""
    N = 30

    def work(self):
        import lab
        w = tempfile.mkdtemp(prefix="ak_")
        json.dump({"H": np.eye(3).tolist(), "chamfer_px": 1.0}, open(os.path.join(w, "court.json"), "w"))
        tag = lab.BLOB_TAG
        lab.BLOB_TAG = detect.ball_tag("ball_e38.pth", "dim", self.N, w, detect.BALL_CODE_OLD)
        self.old = lab.blob_file(w, 0, self.N)
        lab.BLOB_TAG = detect.ball_tag("ball_e38.pth", "dim", self.N, w)
        self.new = lab.blob_file(w, 0, self.N)
        lab.BLOB_TAG = tag
        np.save(self.old, np.array([[0, 1, 2, 3, 0.5], [60, 4, 5, 6, 0.6]]))
        np.save(os.path.join(w, "pose.npy"), np.zeros((2, 57)))
        detect.KEYS_FILE = os.path.join(w, "stage_keys.json")
        detect.STAGE_KEYS.clear()
        return w

    def run_adopt(self, changes):
        real = detect.git_changes
        detect.git_changes = lambda files, since: changes
        try:
            return detect.adopt_keys(self.w, "ball_e38.pth", "dim", self.N)
        finally:
            detect.git_changes = real

    def test_no_change_adopts(self):
        self.w = self.work()
        self.assertNotEqual(self.old, self.new)
        out = self.run_adopt([])
        self.assertEqual((out["ball"]["state"], out["pose"]["state"]), ("adopted", "adopted"))
        self.assertTrue(np.array_equal(np.load(self.new), np.load(self.old)))        # a copy; the old file stays
        keys = json.load(open(os.path.join(self.w, "stage_keys.json")))
        self.assertEqual(keys["pose"], detect.pose_key())
        self.assertEqual(self.run_adopt([])["ball"]["state"], "current")              # a second call: nothing to do

    def test_an_e52_record_moves_to_the_new_name(self):
        self.w = self.work()
        detect.STAGE_KEYS["ballAdopted"] = {"file": os.path.basename(self.old), "from": "wasb_0_30_s0.35_k2.npy"}
        self.run_adopt([])
        keys = json.load(open(os.path.join(self.w, "stage_keys.json")))
        self.assertEqual(keys["ballAdopted"]["file"], os.path.basename(self.new))

    def test_a_change_leaves_the_files_stale(self):
        self.w = self.work()
        out = self.run_adopt(["abc1234 2026-11-26T12:00:00+04:00 burst_ball: a new crop"])
        self.assertEqual((out["ball"]["state"], out["pose"]["state"]), ("stale", "stale"))
        self.assertFalse(os.path.exists(self.new))
        self.assertNotIn("pose", json.load(open(os.path.join(self.w, "stage_keys.json"))))
        self.assertEqual(self.run_adopt(None)["ball"]["changes"], "no git")


if __name__ == "__main__":
    unittest.main()

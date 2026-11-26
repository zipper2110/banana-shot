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
IMAGE_NO_EFFECT = {"lab": "the path of the work folder (feat_dino reads the kf pictures with glob)"}    # item 16
# Level 1 of the net band (detect.NETMASK_CODE, item 6 (b)): the modules of its closure that are not in the hand list.
# fill_in imports ball_wasb: the ball tag (detect.BALL_CODE) is a part of the level-1 key and covers it and its imports.
# Item 17 (E119): the ball stage in parts makes the level-1 file with net_band_join.join_parts (not in the key). The
# test of identical arrays (one pass against parts) is test_ball_parts.MaskParts, so each change of the join is checked.
MASK_PARTS = ("net_band_join",)
MASK_NO_EFFECT = {"lab": "paths, the video path and the match table (as for the ball tag)",
                  "net_band_join": "the join gives the same arrays as the one pass (test_ball_parts.MaskParts)"}


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

    def test_netmask_key_covers_its_closure(self):
        keyed = names(detect.NETMASK_CODE) | names(detect.BALL_CODE)
        self.assertEqual(set(code_closure.modules(*names(detect.NETMASK_CODE), *MASK_PARTS)) - keyed, set(MASK_NO_EFFECT))

    def test_pose_key_covers_its_closure(self):
        keyed = names(detect.POSE_CODE)
        self.assertEqual(set(code_closure.modules(*keyed)) - keyed, set(POSE_NO_EFFECT))

    def test_image_key_covers_its_closure(self):
        keyed = names(detect.IMAGE_CODE)
        self.assertEqual(set(code_closure.modules(*keyed)) - keyed, set(IMAGE_NO_EFFECT))

    def test_the_media_key_is_a_part_of_the_later_keys(self):
        # Item 16 (E117): new pictures or new audio of the media stage run audio, image, court and pose again.
        saved = dict(detect.STAGE_KEYS)
        try:
            detect.STAGE_KEYS["media"] = "11111111"
            k1 = {n: detect.stage_key(n) for n in ("audio", "image", "court", "pose", "court2")}
            detect.STAGE_KEYS["media"] = "22222222"
            k2 = {n: detect.stage_key(n) for n in k1}
        finally:
            detect.STAGE_KEYS.clear()
            detect.STAGE_KEYS.update(saved)
        self.assertEqual([n for n in k1 if k1[n] != k2[n]], ["audio", "image", "court", "pose"])
        self.assertEqual(k1["court2"], k2["court2"])      # court2 follows the bytes of court_track.json

    def test_a_new_media_key_runs_the_image_stage_again(self):
        w = tempfile.mkdtemp(prefix="up_")
        out = os.path.join(w, "feat_dino.npy")
        open(out, "w").write("x")
        detect.KEYS_FILE = os.path.join(w, "stage_keys.json")
        detect.STAGE_KEYS.clear()
        runs = []
        for media in ("k1", "k1", "k2"):
            detect.STAGE_KEYS["media"] = media
            detect.stage("image", [out], lambda: runs.append(media), key=detect.stage_key("image"))
        self.assertEqual(runs, ["k1", "k2"])

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
        for f in ("audio.flac", "kf_times.csv", "kf.done", "pts.npy"):          # item 16: media files, older than the others
            open(os.path.join(w, f), "w").write("m")
            os.utime(os.path.join(w, f), (1e9, 1e9))
        np.save(self.old, np.array([[0, 1, 2, 3, 0.5], [60, 4, 5, 6, 0.6]]))
        np.save(os.path.join(w, "pose.npy"), np.zeros((2, 57)))
        np.save(os.path.join(w, "feat_dino.npy"), np.zeros((2, 4)))
        for f in ("court_track.json", "court_track_refined.json", "fullflights_s0.35.json"):
            json.dump([], open(os.path.join(w, f), "w"))
        court = [os.path.join(w, "court_track.json"), os.path.join(w, "court_track_refined.json")]
        self.flights_old = os.path.basename(self.old).split("_s0.35")[1][:-7] + "_" + detect.content_key(
            "", files=court + [os.path.join(detect.HERE, f) for f in detect.FLIGHTS_CODE_OLD])
        detect.KEYS_FILE = os.path.join(w, "stage_keys.json")
        detect.STAGE_KEYS.clear()
        detect.STAGE_KEYS["flights"] = self.flights_old
        detect.STAGE_KEYS["media"] = detect.stage_key("media")
        return w

    def run_adopt(self, changes, dry=False):
        real = detect.git_changes
        detect.git_changes = lambda files, since: changes
        try:
            return detect.adopt_keys(self.w, "ball_e38.pth", "dim", self.N, dry=dry)
        finally:
            detect.git_changes = real

    def test_no_change_adopts(self):
        self.w = self.work()
        self.assertNotEqual(self.old, self.new)
        out = self.run_adopt([])
        self.assertEqual((out["ball"]["state"], out["pose"]["state"], out["flights"]["state"]), ("adopted", "adopted", "adopted"))
        self.assertTrue(np.array_equal(np.load(self.new), np.load(self.old)))        # a copy; the old file stays
        keys = json.load(open(os.path.join(self.w, "stage_keys.json")))
        self.assertEqual(keys["pose"], detect.pose_key())
        self.assertEqual(self.run_adopt([])["ball"]["state"], "current")              # a second call: nothing to do

    def test_court_with_no_recorded_key_is_adopted_and_another_key_stays_stale(self):
        self.w = self.work()
        out = self.run_adopt([])
        self.assertEqual((out["court"]["state"], out["court2"]["state"], out["media"]["state"], out["image"]["state"]),
                         ("adopted", "adopted", "current", "adopted"))
        keys = json.load(open(os.path.join(self.w, "stage_keys.json")))
        self.assertEqual(keys["court"], detect.stage_key("court"))
        self.assertEqual(keys["image"], detect.stage_key("image"))
        self.w = self.work()
        detect.STAGE_KEYS["court"] = "00000000"                                   # a run of other code
        self.assertEqual(self.run_adopt([])["court"]["state"], "stale")

    def test_flights_of_another_key_stay_stale(self):
        self.w = self.work()
        detect.STAGE_KEYS["flights"] = "_ball_e38_dim_00000000_00000000"
        self.assertEqual(self.run_adopt([])["flights"]["state"], "stale")

    def test_an_e52_record_moves_to_the_new_name(self):
        self.w = self.work()
        detect.STAGE_KEYS["ballAdopted"] = {"file": os.path.basename(self.old), "from": "wasb_0_30_s0.35_k2.npy"}
        self.run_adopt([])
        keys = json.load(open(os.path.join(self.w, "stage_keys.json")))
        self.assertEqual(keys["ballAdopted"]["file"], os.path.basename(self.new))

    def test_keys_before_item_16_are_adopted_with_a_note(self):
        self.w = self.work()
        for n in ("audio", "court", "pose"):
            detect.STAGE_KEYS[n] = detect.old_key(n)
        for f in ("feat_audio.npy", "feat_mel.npy"):
            np.save(os.path.join(self.w, f), np.zeros(2))
        detect.STAGE_KEYS["keysAdopted"] = {"pose": {"state": "current"}, "time": "2026-11-26T14:50:12"}
        out = self.run_adopt(["abc1234 a change"])                 # git is not asked for a key of the old formula
        keys = json.load(open(os.path.join(self.w, "stage_keys.json")))
        for n in ("audio", "court", "pose"):
            self.assertEqual(out[n]["state"], "adopted", n)
            self.assertIn("before item 16", out[n]["from"])
            self.assertEqual(keys[n], detect.stage_key(n))
        self.assertEqual(keys["keysAdopted"]["earlier"]["time"], "2026-11-26T14:50:12")     # the E113 record stays
        self.assertEqual(self.run_adopt([])["pose"]["state"], "current")

    def test_new_pictures_leave_the_later_stages_stale(self):
        self.w = self.work()
        detect.STAGE_KEYS["pose"] = detect.old_key("pose")
        os.utime(os.path.join(self.w, "kf.done"), None)               # the pictures are newer than pose.npy and feat_dino.npy
        os.utime(os.path.join(self.w, "pose.npy"), (2e9 - 10, 1.5e9))
        os.utime(os.path.join(self.w, "feat_dino.npy"), (2e9 - 10, 1.5e9))
        out = self.run_adopt([])
        self.assertEqual((out["pose"]["state"], out["image"]["state"]), ("stale", "stale"))
        self.assertIn("older than the media files", out["pose"]["changes"])

    def test_a_media_key_of_other_code_leaves_the_later_stages_stale(self):
        self.w = self.work()
        detect.STAGE_KEYS["media"] = "00000000"
        detect.STAGE_KEYS["pose"] = detect.old_key("pose")
        out = self.run_adopt([])
        self.assertEqual((out["media"]["state"], out["pose"]["state"], out["court"]["state"], out["image"]["state"]),
                         ("stale", "stale", "stale", "stale"))
        self.assertEqual(out["pose"]["changes"], "the media stage is not current")

    def test_dry_writes_and_copies_nothing(self):
        self.w = self.work()
        out = self.run_adopt([], dry=True)
        self.assertEqual(out["ball"]["state"], "adopted")
        self.assertFalse(os.path.exists(self.new))
        self.assertFalse(os.path.exists(os.path.join(self.w, "stage_keys.json")))

    def test_a_change_leaves_the_files_stale(self):
        self.w = self.work()
        out = self.run_adopt(["abc1234 2026-11-26T12:00:00+04:00 burst_ball: a new crop"])
        self.assertEqual((out["ball"]["state"], out["pose"]["state"], out["flights"]["state"], out["court"]["state"]),
                         ("stale", "stale", "stale", "stale"))
        self.assertFalse(os.path.exists(self.new))
        self.assertNotIn("pose", json.load(open(os.path.join(self.w, "stage_keys.json"))))
        self.assertEqual(self.run_adopt(None)["ball"]["changes"], "no git")


class AutoAdopt(unittest.TestCase):
    """Item 16b (E118): detect.auto_adopt keeps the files of a folder of a run before item 16 when the code is the same."""

    def work(self):
        w = tempfile.mkdtemp(prefix="aa_")
        for f in ("audio.flac", "kf_times.csv", "kf.done", "pts.npy"):           # media files, older than the features
            open(os.path.join(w, f), "w").write("m")
            os.utime(os.path.join(w, f), (1e9, 1e9))
        for f in ("feat_audio.npy", "feat_mel.npy", "feat_dino.npy", "pose.npy"):
            np.save(os.path.join(w, f), np.zeros(2))
        json.dump([], open(os.path.join(w, "court_track.json"), "w"))
        detect.KEYS_FILE = os.path.join(w, "stage_keys.json")
        detect.STAGE_KEYS.clear()
        detect.STAGE_KEYS["media"] = detect.stage_key("media")
        for n in ("audio", "court", "pose"):                                     # the format before item 16; no image key
            detect.STAGE_KEYS[n] = detect.old_key(n)
        detect.STAGE_KEYS["keysAdopted"] = {"time": "2026-11-26T14:50:12"}
        return w

    def test_an_old_folder_with_the_same_code_is_adopted(self):
        w = self.work()
        done = detect.auto_adopt(w)
        self.assertEqual(sorted(done), ["audio", "court", "image", "pose"])
        keys = json.load(open(detect.KEYS_FILE))
        for n in done:
            self.assertEqual(keys[n], detect.stage_key(n), n)
            self.assertEqual(keys["keysAdopted"][n]["how"], "automatic")
        self.assertEqual(keys["keysAdopted"]["earlier"]["time"], "2026-11-26T14:50:12")
        runs = []
        detect.stage("pose", [os.path.join(w, "pose.npy")], lambda: runs.append(1), key=detect.stage_key("pose"))
        self.assertEqual(runs, [])                                               # the stage does not run
        self.assertEqual(detect.auto_adopt(w), {})                               # a second call: nothing to do

    def test_an_old_folder_with_changed_stage_code_runs_again(self):
        w = self.work()
        detect.STAGE_KEYS["court"] = "00000000"         # the old-format key of other court code
        done = detect.auto_adopt(w)
        self.assertNotIn("court", done)
        self.assertEqual(sorted(done), ["audio", "image", "pose"])
        runs = []
        detect.stage("court", [os.path.join(w, "court_track.json")], lambda: runs.append(1), key=detect.stage_key("court"))
        self.assertEqual(runs, [1])

    def test_a_changed_media_key_runs_the_stages_again(self):
        w = self.work()
        detect.STAGE_KEYS["media"] = "00000000"
        self.assertEqual(detect.auto_adopt(w), {})
        self.assertFalse(os.path.exists(detect.KEYS_FILE))

    def test_features_older_than_the_media_files_run_again(self):
        w = self.work()
        os.utime(os.path.join(w, "kf.done"), None)      # new pictures, after the image, court and pose files
        for f in ("feat_dino.npy", "court_track.json", "pose.npy"):
            os.utime(os.path.join(w, f), (1.5e9, 1.5e9))
        self.assertEqual(sorted(detect.auto_adopt(w)), ["audio"])

    def test_a_new_picture_time_file_runs_the_picture_stages_again(self):
        # Item 16b (manager): each output must be newer than each media file that the stage reads.
        w = self.work()
        os.utime(os.path.join(w, "kf_times.csv"), None)
        for f in ("feat_dino.npy", "court_track.json", "pose.npy"):
            os.utime(os.path.join(w, f), (1.5e9, 1.5e9))
        self.assertEqual(sorted(detect.auto_adopt(w)), ["audio"])


if __name__ == "__main__":
    unittest.main()

"""Tests of the time base of the 1 fps pictures (chain-inventory G2). Usage: python -m unittest test_timebase"""
import os, tempfile, unittest
import numpy as np
import lab
import prep


def folder(kt, pts, fps1=False, pose=None):
    d = tempfile.mkdtemp(prefix="tb_")
    open(os.path.join(d, "kf_times.csv"), "w").write("\n".join(str(t) for t in kt) + "\n")
    np.save(os.path.join(d, "pts.npy"), np.asarray(pts, float))
    if fps1:
        open(os.path.join(d, "kf.fps1"), "w").write("ok")
    if pose is not None:
        np.save(os.path.join(d, "pose.npy"), np.asarray(pose, float))
    return d


class TimeBase(unittest.TestCase):
    def test_drift_picks_the_picture_of_the_true_second(self):
        # A picture each 1.1 s (the phone drifts): picture k is at 1.1 k s, not at k s.
        kt = [round(1.1 * k, 3) for k in range(10)]
        d = folder(kt, np.arange(0, 10.9, 1 / 30))
        self.assertEqual(lab.video_seconds(d), 11)
        k = lab.kf_of_second(d)
        self.assertEqual(len(k), 11)
        for s, j in enumerate(k):                    # the nearest picture to the middle of the second
            self.assertAlmostEqual(abs(kt[j] - (s + 0.5)), min(abs(t - (s + 0.5)) for t in kt))
        self.assertEqual(k[10], 9)                   # second 10 = picture 9 (9.9 s), not picture 10

    def test_more_pictures_than_seconds(self):
        # A keyframe each 0.95 s: 81 pictures for 76.4 s (the clips of 2026-10-19).
        kt = [round(0.95 * k, 3) for k in range(81)]
        d = folder(kt, np.arange(0, 76.4, 1 / 30))
        self.assertEqual(lab.video_seconds(d), 77)
        k = lab.kf_of_second(d)
        self.assertAlmostEqual(kt[k[70]], 70.3, delta=0.5)

    def test_by_second_and_pose(self):
        kt = [0.0, 1.0, 2.2, 3.4, 4.6]
        pose = [[k, 0, 0, 0, 0, 0.9] + [0] * 51 for k in (3, 0, 1, 2, 4, 4)]
        d = folder(kt, np.arange(0, 5.5, 1 / 60), pose=pose)
        rows = np.arange(5)[:, None] * np.ones((5, 2))
        bs = lab.by_second(rows, d)
        np.testing.assert_array_equal(bs[:, 0], lab.kf_of_second(d))
        ps = lab.pose_by_second(d)
        self.assertEqual(len(ps), lab.video_seconds(d))
        for s, rws in enumerate(ps):
            self.assertTrue((rws[:, 0] == lab.kf_of_second(d)[s]).all())
        self.assertEqual(len(ps[5]), 2)              # second 5 = picture 4 (4.6 s), with its two persons
        self.assertTrue(lab.kf_file(d, 5).endswith("00005.jpg"))

    def test_fps1_old_folder_gets_the_true_time(self):
        # Old work folders of a video with no keyframe each second: kf_times.csv has k. Picture k of
        # "fps=1:round=down" is the last frame before k + 1 s.
        pts = np.arange(0, 6, 1 / 30)
        d = folder([0, 1, 2, 3, 4], pts, fps1=True)
        kt = lab.kf_times(d)
        np.testing.assert_allclose(kt, np.arange(5) + 29 / 30, atol=1e-6)
        np.testing.assert_array_equal(lab.kf_of_second(d)[:5], np.arange(5))   # the picture is inside its second

    def test_prep_writes_the_true_time(self):
        frames = list(np.arange(0, 6, 1 / 30))
        self.assertAlmostEqual(prep.fps1_time(frames, 0), 29 / 30, places=6)
        self.assertAlmostEqual(prep.fps1_time(frames, 4), 4 + 29 / 30, places=6)
        self.assertEqual(prep.fps1_time([2.5], 0), 0.0)      # no frame before 1 s: the second itself


if __name__ == "__main__":
    unittest.main()

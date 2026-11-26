"""Tests of the ball pass in parts (S2 item 17, E119): detect.ball_plan, ball_parts, clean_parts.
A fake decode stands in for ball_wasb.run. Usage: python -m unittest test_ball_parts"""
import glob, os, tempfile, unittest
import numpy as np
import detect


def fake_video(fps, seconds, jitter=0.0):
    """The frame times of a video (s), with an offset of the first frame as in a phone video."""
    t = 0.016 + np.arange(int(seconds * fps)) / fps
    return t + (np.sin(np.arange(len(t))) * jitter if jitter else 0)


def fake_run(pts, stride, calls=None):
    """As ball_wasb.run with the ffmpeg seek of E119: the decode starts at the first frame at or after ss (in the time
    of the stream), ends before ss + dur, and gives groups of 3 * stride frames (a short last group is dropped).
    Rows: local frame, and values of the absolute frame (a wrong start gives other values)."""
    def run(ss, dur, f0=0):
        if calls is not None:
            calls.append(f0)
        if stride == 1:
            idx = fps_filter(pts, ss, dur, detect.part_fps(f0))
        else:
            idx = np.nonzero((pts >= ss - 1e-9) & (pts < ss + dur))[0]
        need = 3 * stride
        idx = idx[:len(idx) // need * need]
        return [(k, float(a) * 2, float(a) % 7, 0.9, 0.5) for k, a in enumerate(idx) if a % 5 != 0]
    return run


def fps_filter(src, ss, dur, spec):
    """As "ffmpeg -ss ss -t dur ... -vf fps=<spec>" (spec: 30, or "30:start_time=t"): the source frames at or after
    ss; the time of the decode starts at ss; the grid of 1/30 s starts at start_time; each output frame k is the last
    source frame that rounds to k or before it. Returns the source frame of each output frame."""
    st = float(str(spec).split("start_time=")[1]) if "start_time=" in str(spec) else 0.0
    keep = np.nonzero((src >= ss - 1e-9) & (src < ss + dur))[0]
    slot = np.round((src[keep] - ss - st) * 30).astype(int)
    out = []
    for k in range(max(0, slot[0]), slot[-1] + 1):
        j = np.searchsorted(slot, k, side="right") - 1
        if j >= 0:
            out.append(keep[j])
    return np.array(out, int)


class BallParts(unittest.TestCase):
    def setUp(self):
        self.w = tempfile.mkdtemp(prefix="bp_")
        self.blob = os.path.join(self.w, "wasb_0_100_s0.35_ball_e38_dim_aaaaaaaa_k2.npy")

    def check(self, fps, pts, n, part_s):
        stride = 2 if fps == 60 else 1
        one = np.array(fake_run(pts, stride)(0, n), np.float32).reshape(-1, 5)
        plan = detect.ball_plan(n, fps, pts, part_s=part_s)
        self.assertGreater(len(plan), 2)
        d, files = detect.ball_parts(self.blob, plan, fake_run(pts, stride))
        self.assertTrue(np.array_equal(d, one))                    # the same rows in the same order
        return plan, files

    def test_60_fps_parts_give_the_one_pass_rows(self):
        pts = fake_video(60, 100, jitter=0.002)                     # a phone video: the frame times are not exact
        plan, _ = self.check(60, pts, 100, part_s=30)
        self.assertEqual([p[0] % 6 for p in plan], [0] * len(plan))  # each part starts on a group bound

    def test_30_fps_parts_give_the_one_pass_rows(self):
        # A 60 fps phone video with frame times that are not exact, decoded by the fps filter at 30 fps (E119: with
        # the grid of the seek, half a step off the grid of the one pass, the filter takes other frames).
        self.check(30, fake_video(60, 100, jitter=0.004), 100, part_s=30)

    def test_a_short_video_is_one_part_as_before(self):
        pts = fake_video(60, 20)
        self.assertEqual(detect.ball_plan(20, 60, pts), [(0, None, 0.0, 20)])

    def test_a_stopped_run_continues_after_the_last_part(self):
        pts, n = fake_video(60, 100), 100
        plan = detect.ball_plan(n, 60, pts, part_s=30)
        calls, run = [], fake_run(pts, 2)

        def stop_in_part_3(ss, dur, f0):
            if len(calls) == 2:
                raise KeyboardInterrupt                             # the user stops the run during part 3
            calls.append(f0)
            return run(ss, dur, f0)
        with self.assertRaises(KeyboardInterrupt):
            detect.ball_parts(self.blob, plan, stop_in_part_3)
        self.assertEqual(len(glob.glob(self.blob[:-4] + ".part*.npy")), 2)
        again = []
        d, _ = detect.ball_parts(self.blob, plan, fake_run(pts, 2, again))
        self.assertEqual(again, [p[0] for p in plan[2:]])           # only the parts that are not done run
        self.assertTrue(np.array_equal(d, np.array(run(0, n), np.float32).reshape(-1, 5)))

    def test_a_temp_file_and_parts_of_another_tag_are_not_used(self):
        pts, n = fake_video(60, 100), 100
        plan = detect.ball_plan(n, 60, pts, part_s=30)
        f0, f1 = plan[0][:2]
        tmp = detect.part_file(self.blob, f0, f1)[:-4] + ".tmp.npy"
        other = detect.part_file(self.blob.replace("aaaaaaaa", "bbbbbbbb"), f0, f1)
        bad = np.zeros((3, 5), np.float32)
        np.save(tmp, bad)                                           # a stop during the write
        np.save(other, bad)
        detect.clean_parts(self.blob)
        self.assertFalse(os.path.exists(tmp) or os.path.exists(other))
        calls = []
        detect.ball_parts(self.blob, plan, fake_run(pts, 2, calls))
        self.assertEqual(calls, [p[0] for p in plan])               # part 1 runs: the temp file is not a part


if __name__ == "__main__":
    unittest.main()

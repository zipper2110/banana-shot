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
    def run(ss, dur, f0=0, f1=None):
        if calls is not None:
            calls.append(f0)
        idx = decoded(pts, stride, ss, dur, f0)
        need = 3 * stride
        idx = idx[:len(idx) // need * need]
        return [(k, float(a) * 2, float(a) % 7, 0.9, 0.5) for k, a in enumerate(idx) if a % 5 != 0]
    return run


def decoded(pts, stride, ss, dur, f0):
    """The source frames of the pictures of a decode window (60 fps: the frames; 30 fps: the fps filter)."""
    if stride == 1:
        return fps_filter(pts, ss, dur, detect.part_fps(f0))
    return np.nonzero((pts >= ss - 1e-9) & (pts < ss + dur))[0]


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

    def test_the_one_pass_keeps_the_filter_of_before(self):
        # The one pass and the first part start at 0 s with "fps=30", as before item 17 (no start_time, the same
        # frames; the ball tag stays). Only a part after a seek gets the grid fix.
        self.assertEqual(detect.part_fps(0), 30)
        self.assertEqual(detect.ball_plan(100, 30, part_s=30)[0][:3], (0, 90 * 10, 0.0))
        self.assertIn("start_time=", str(detect.part_fps(900)))

    def test_a_stopped_run_continues_after_the_last_part(self):
        pts, n = fake_video(60, 100), 100
        plan = detect.ball_plan(n, 60, pts, part_s=30)
        calls, run = [], fake_run(pts, 2)

        def stop_in_part_3(ss, dur, f0, f1):
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


class MaskParts(unittest.TestCase):
    """With the net band on: a mask file for each part (MaskWriter, as detect.py ball_run), then
    net_band_join.join_parts. The joined file has the same arrays as the file of the one pass."""

    def masks(self, fps, pts, n, part_s):
        import net_band_mask as nbm, net_band_join
        from test_net_band import HM
        stride, crop = (2 if fps == 60 else 1), (0, 0, 672, 378)    # the crop of test_net_band
        k = 1 if fps == 60 else 2                                   # the frame index of the masks (60 fps line)

        def picture(a):                                             # the picture of source frame a
            return (np.random.default_rng(int(a)).random((378, 672, 3)) * 255).astype(np.uint8)

        def write(f0, ss, dur, path):
            w = nbm.MaskWriter(np.array(HM), crop, 0.35, stride, (lambda i: (f0 + i) * k))
            for a in decoded(pts, stride, ss, dur, f0):
                w.feed(picture(a))
            return w.save(path)
        d = tempfile.mkdtemp(prefix="mp_")
        one = np.load(write(0, 0, n, os.path.join(d, "one.npz")))
        plan = detect.ball_plan(n, fps, pts, part_s=part_s)
        self.assertGreater(len(plan), 2)
        parts = [(write(f0, ss, dur, os.path.join(d, f"p{f0}.npz")), f0 * k, None if f1 is None else f1 * k)
                 for f0, f1, ss, dur in plan]
        joined = np.load(net_band_join.join_parts(parts, os.path.join(d, "joined.npz")))
        self.assertGreater(len(one["frame"]), 0)
        self.assertEqual(sorted(one.files), sorted(joined.files))
        for name in one.files:
            self.assertTrue(np.array_equal(one[name], joined[name]), name)
            self.assertEqual(one[name].dtype, joined[name].dtype, name)

    def test_60_fps_joined_masks_are_the_one_pass_masks(self):
        self.masks(60, fake_video(60, 4, jitter=0.002), 4, part_s=1)

    def test_30_fps_joined_masks_are_the_one_pass_masks(self):
        self.masks(30, fake_video(60, 4, jitter=0.004), 4, part_s=1)

    def test_the_join_of_many_records_is_fast(self):
        # S5 review: an NpzFile unzips the full array at each index, and the join was quadratic (8000 records 42 s;
        # a 1 h match at 60 fps has about 216000 records). 20000 synthetic records in 4 parts: a few seconds at most.
        import time, net_band_join
        d, rng, parts = tempfile.mkdtemp(prefix="mp_"), np.random.default_rng(0), []
        for p in range(4):
            fr = np.arange(p * 5000 + 2, (p + 1) * 5000 + 2, dtype=np.int32)   # 2 frames past the end
            size = rng.integers(1, 40, len(fr)).astype(np.int64)
            off = np.concatenate([[0], np.cumsum(size)[:-1]]).astype(np.int64)
            data = rng.integers(0, 255, int(size.sum())).astype(np.uint8)
            f = os.path.join(d, f"p{p}.npz")
            np.savez(f, frame=fr, prev=fr - 2, off=off, size=size, data=data, box=np.array((1, 2, 3, 4)),
                     crop=np.array((0, 0, 672, 378)), scale=0.35, thr=4.0, version=1)
            parts.append((f, p * 5000, None if p == 3 else (p + 1) * 5000))
        t = time.time()
        z = np.load(net_band_join.join_parts(parts, os.path.join(d, "j.npz")))
        self.assertLess(time.time() - t, 5.0)
        self.assertEqual(list(z["frame"]), list(range(2, 20002)))
        self.assertTrue(np.array_equal(z["off"][1:], np.cumsum(z["size"])[:-1]))

    def test_parts_of_another_crop_are_not_joined(self):
        import net_band_mask as nbm, net_band_join
        from test_net_band import HM
        d = tempfile.mkdtemp(prefix="mp_")
        a = nbm.MaskWriter(np.array(HM), (0, 0, 672, 378), 0.35, 2).save(os.path.join(d, "a.npz"))
        b = nbm.MaskWriter(np.array(HM), (2, 0, 672, 378), 0.35, 2).save(os.path.join(d, "b.npz"))
        with self.assertRaises(ValueError):
            net_band_join.join_parts([(a, 0, 10), (b, 10, None)], os.path.join(d, "j.npz"))


if __name__ == "__main__":
    unittest.main()

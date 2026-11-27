"""Tests of the 10-bit ball decode (E200, G15) and of the stops for a failed decode (E201, G13).
Usage: python -m unittest test_decode10"""
import json, os, sys, tempfile, unittest
import numpy as np
import detect


class VideoFormat(unittest.TestCase):
    def test_bits_of_the_pixel_format(self):
        self.assertEqual(detect.bits_of("yuv420p10le", "N/A"), 10)
        self.assertEqual(detect.bits_of("p010le"), 10)
        self.assertEqual(detect.bits_of("yuv420p", None), 8)
        self.assertEqual(detect.bits_of("nv12", "8"), 8)
        self.assertEqual(detect.bits_of("yuv420p", "10"), 10)          # bits_per_raw_sample first


class BallTag(unittest.TestCase):
    def setUp(self):
        self.w = tempfile.mkdtemp(prefix="bt_")
        open(os.path.join(self.w, "court.json"), "w").write('{"H": [[1, 0, 0], [0, 1, 0], [0, 0, 1]]}')

    def test_an_8_bit_video_keeps_the_tag_of_before(self):
        import platform_cfg
        # The formula of before E200: no bits part, no decode10.py (existing ball files keep their names).
        before = "_ball_e38_dim_" + detect.content_key(
            "ball_e38.pth", "dim", 100, platform_cfg.decode(), files=[os.path.join(detect.HERE, f) for f in detect.BALL_CODE] + [
                os.path.join(platform_cfg.weights_dir(), "ball_e38.pth"), os.path.join(self.w, "court.json")])
        self.assertEqual(detect.VIDEO["bits"], 8)
        self.assertEqual(detect.ball_tag("ball_e38.pth", "dim", 100, self.w), before)
        self.assertEqual(detect.ball_tag("ball_e38.pth", "dim", 100, self.w, bits=8), before)
        ten = detect.ball_tag("ball_e38.pth", "dim", 100, self.w, bits=10)
        self.assertNotEqual(ten, before)                                  # a 10-bit video: a new file

    def test_an_8_bit_video_does_not_import_decode10(self):
        sys.modules.pop("decode10", None)
        self.assertIsNone(detect.ball_decoder(8))
        self.assertNotIn("decode10", sys.modules)
        self.assertEqual(detect.ball_decoder(10).__name__, "decode10")


class Chain(unittest.TestCase):
    def test_decode10_is_the_chain_of_ball_wasb_with_the_10_bit_fix(self):
        # The copy must stay the chain of ball_wasb.frames with one change (scale_cuda ... :format=nv12), so that it
        # can go back into ball_wasb.py at its next keyed change.
        import ball_wasb, decode10, lab, platform_cfg
        cmds = []

        class Fake:
            PIPE = -1

            @staticmethod
            def Popen(cmd, **kw):
                cmds.append(cmd)
                return type("P", (), {"stdout": type("S", (), {"read": staticmethod(lambda n: b"")})()})()
        saved = (ball_wasb.subprocess, decode10.subprocess, ball_wasb.crop_box, platform_cfg._cache.get("decode"))
        try:
            ball_wasb.subprocess = decode10.subprocess = Fake
            ball_wasb.crop_box = lambda *a: (2, 4, 64, 32, 128, 72)
            platform_cfg._cache["decode"] = "cuda"
            lab.MATCHES["t10"] = {"id": "t10", "video": "v.mp4"}
            for fps in (None, 30):
                ball_wasb.FPS = fps
                cmds.clear()
                list(ball_wasb.frames("t10", 1.5, 10, 0.35))
                list(decode10.frames("t10", 1.5, 10, 0.35))
                old, new = cmds
                self.assertEqual([c.replace(":format=nv12,hwdownload", ",hwdownload") for c in new], old)
                self.assertNotEqual(new, old)
        finally:
            ball_wasb.subprocess, decode10.subprocess, ball_wasb.crop_box = saved[:3]
            ball_wasb.FPS = None
            if saved[3] is None:
                platform_cfg._cache.pop("decode", None)
            else:
                platform_cfg._cache["decode"] = saved[3]


class ShortDecode(unittest.TestCase):
    def test_the_ffmpeg_message_is_kept_and_the_module_is_restored(self):
        import subprocess, types
        m = types.SimpleNamespace(subprocess=subprocess)
        log = detect.FfmpegLog([m])
        p = m.subprocess.Popen([sys.executable, "-c", "import sys; sys.stderr.write('[hwdownload] Invalid output format nv12\\n')"],
                               stdout=subprocess.PIPE)
        p.stdout.read()
        self.assertIn("Invalid output format nv12", log.text())
        log.close()
        self.assertIs(m.subprocess, subprocess)

    def test_close_puts_the_module_back_after_an_exception(self):
        # The shim changes the subprocess module of the decode modules for the ball stage only (manager note).
        import subprocess, types
        m = types.SimpleNamespace(subprocess=subprocess)
        log = detect.FfmpegLog([m])
        try:
            raise KeyboardInterrupt                                    # a stop during the ball stage
        except KeyboardInterrupt:
            pass
        finally:
            log.close()
        self.assertIs(m.subprocess, subprocess)
        log = detect.FfmpegLog([m])

        def broken():
            raise OSError("the temp file is gone")
        log.text = broken
        with self.assertRaises(OSError):
            log.close()
        self.assertIs(m.subprocess, subprocess)                         # also when the copy of the text fails

    def test_a_short_decode_stops_with_the_ffmpeg_message(self):
        text = "[rawvideo] non monotonically increasing dts\n[hwdownload @ 0] Invalid output format nv12 for hwframe download.\n"
        with self.assertRaises(RuntimeError) as e:
            detect.check_decode(0, 8160, text, 0, 136)
        self.assertIn("0 pictures of about 8160", str(e.exception))
        self.assertIn("Invalid output format nv12", str(e.exception))
        self.assertNotIn("dts", str(e.exception))                        # the error lines first
        detect.check_decode(8150, 8160, "", 0, 136)                       # a full decode passes

    def test_no_ball_candidate_gives_empty_net_events(self):
        w = tempfile.mkdtemp(prefix="ne_")
        blob = os.path.join(w, "wasb.npy")
        np.save(blob, np.zeros((0, 5), np.float32))
        said = []
        detect.run_net_events(w, blob, None, said.append)
        self.assertEqual(json.load(open(os.path.join(w, "net_events.json")))["events"], [])
        self.assertEqual(len(said), 1)


if __name__ == "__main__":
    unittest.main()

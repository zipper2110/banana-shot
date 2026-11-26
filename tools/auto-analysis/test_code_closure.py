"""Tests of the code part of the stage keys (G12 follow-up, S2 item 13). Usage: python -m unittest test_code_closure"""
import os, tempfile, unittest
import code_closure
import detect

# The modules in the import closure of the ball stage that are not in its tag (detect.BALL_CODE). Each one can change
# the detections (burst_ball.roi_mask is the crop; cuda_scale the pictures with no NVIDIA GPU; platform_cfg the
# precision; lab the paths). Decision pending (S2 item 13): add them to the tag (each ball file gets a new name) or
# keep them out. A new module in the closure makes test_ball_tag_covers_its_closure fail.
BALL_NOT_KEYED = {"burst_ball", "cuda_scale", "lab", "platform_cfg"}


def module_folder(sources):
    d = tempfile.mkdtemp(prefix="cc_")
    for name, text in sources.items():
        open(os.path.join(d, name + ".py"), "w").write(text)
    return d


class CodeClosure(unittest.TestCase):
    def test_follows_imports_also_inside_functions(self):
        d = module_folder({"a": "import os\ndef f():\n    import b\n", "b": "from c import x\nimport numpy as np\n",
                           "c": "x = 1\nfrom . import d\n", "d": "y = 2\n"})
        self.assertEqual(code_closure.modules("a", folder=d), ["a", "b", "c"])     # no os, numpy; no relative import

    def test_flights_key_has_serve_ball(self):
        # The gap that S1 found: feat_wasb imports serve_ball, and feat_wasb.npy is an output of the flights stage.
        mods = code_closure.modules(*detect.FLIGHTS_ENTRIES)
        for m in ("serve_ball", "feat_wasb", "ball_full", "net_events", "court_refine", "frame_times"):
            self.assertIn(m, mods)

    def test_ball_tag_covers_its_closure(self):
        keyed = {f[:-3] for f in detect.BALL_CODE}
        self.assertEqual(set(code_closure.modules(*keyed)) - keyed, BALL_NOT_KEYED)

    def test_flights_key_changes_with_an_imported_module(self):
        d = module_folder({"a": "import b\n", "b": "v = 1\n"})
        k1 = detect.content_key(files=code_closure.files("a", folder=d))
        open(os.path.join(d, "b.py"), "w").write("v = 2\n")
        self.assertNotEqual(detect.content_key(files=code_closure.files("a", folder=d)), k1)


if __name__ == "__main__":
    unittest.main()

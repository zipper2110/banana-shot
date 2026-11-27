"""Tests of adopt_reach (A39, E202): the flights and netband keys of a work folder get the A39 formula only with a
proof. Usage: python -m unittest test_adopt_reach"""
import json, os, shutil, tempfile, unittest
import adopt_reach, code_closure, detect

ENTRIES = detect.FLIGHTS_ENTRIES + detect.STAGE_ENTRIES["netband"]
TAG = "_ball_e38_dim_0000aaaa"
MASK = TAG + "_7fef77c9"


def code_copy(edit=None):
    """A copy of the modules of the flights and netband closures; edit: (module, old text or None to append, new text)."""
    d = tempfile.mkdtemp(prefix="arc_")
    for m in code_closure.modules(*ENTRIES):
        shutil.copy(os.path.join(code_closure.HERE, m + ".py"), d)
    if edit:
        m, old, new = edit
        p = os.path.join(d, m + ".py")
        s = open(p, encoding="utf-8").read()
        assert old is None or s.count(old) == 1
        open(p, "w", encoding="utf-8").write(s + new if old is None else s.replace(old, new))
    return d


def old_keys(code, work):
    """The netband and flights keys that detect.py before A39 records with the code in folder code."""
    court = [os.path.join(work, f) for f in ("court_track.json", "court_track_refined.json")]
    nb = MASK + "_" + detect.content_key(files=[os.path.join(code, m + ".py") for m in
                                                code_closure.modules(*detect.STAGE_ENTRIES["netband"], folder=code)])
    fl = TAG + "_" + detect.content_key("", files=court + [os.path.join(code, m + ".py") for m in
                                                          code_closure.modules(*detect.FLIGHTS_ENTRIES, folder=code)])
    return nb, fl + "_nb" + nb


def work_folder(code):
    w = tempfile.mkdtemp(prefix="arw_")
    for f in ("court_track.json", "court_track_refined.json"):
        json.dump([{"t0": 0}], open(os.path.join(w, f), "w"))
    nb, fl = old_keys(code, w)
    json.dump({"media": "1a771021", "netmask": MASK, "netband": nb, "flights": fl}, open(os.path.join(w, "stage_keys.json"), "w"))
    return w


class AdoptReach(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.now = code_copy()
        # E186-like: a court_auto change that the flights and the net band do not use
        cls.court = code_copy(("court_auto", "def valid_H(H, w=1920, h=1080):\n", "def valid_H(H, w=1920, h=1000):\n"))
        # a change of a file that the flights use
        cls.used = code_copy(("ball_full", None, "\n# a change\n"))

    def test_an_unused_court_change_is_adopted(self):
        w = work_folder(self.court)
        out = adopt_reach.plan(w, [("old", self.court)], now_code=self.now)
        self.assertEqual((out["netband"]["state"], out["flights"]["state"]), ("adopt", "adopt"))
        self.assertEqual(out["flights"]["proof"], "old")
        before = open(os.path.join(w, "stage_keys.json")).read()
        self.assertEqual(open(os.path.join(w, "stage_keys.json")).read(), before)   # the plan (dry run) writes nothing
        self.assertTrue(adopt_reach.apply(w, out))
        keys = json.load(open(os.path.join(w, "stage_keys.json")))
        self.assertEqual(json.load(open(os.path.join(w, "stage_keys.a39.json"))), json.loads(before))
        # the keys of now, as detect.py of the A39 rule makes them
        court = [os.path.join(w, f) for f in ("court_track.json", "court_track_refined.json")]
        nb = MASK + "_" + detect.content_key(files=code_closure.files(*detect.STAGE_ENTRIES["netband"], folder=self.now))
        self.assertEqual(keys["netband"], nb)
        self.assertEqual(keys["flights"], TAG + "_" + detect.content_key(
            "", files=court + code_closure.files(*detect.FLIGHTS_ENTRIES, folder=self.now)) + "_nb" + nb)
        self.assertEqual(keys["keysAdoptedA39"]["flights"]["proof"], "old")
        again = adopt_reach.plan(w, [("old", self.court)], now_code=self.now)
        self.assertEqual((again["netband"]["state"], again["flights"]["state"]), ("current", "current"))

    def test_a_change_of_a_used_file_stays_stale(self):
        w = work_folder(self.used)
        out = adopt_reach.plan(w, [("used", self.used), ("court", self.court)], now_code=self.now)
        self.assertEqual(out["flights"]["state"], "stale")
        self.assertIn("made by used", out["flights"]["why"])
        self.assertIn("ball_full.py", out["flights"]["why"])
        self.assertEqual(out["netband"]["state"], "adopt")          # net_band does not use ball_full: the same files
        self.assertFalse(adopt_reach.apply(w, {"flights": out["flights"]}))

    def test_no_version_with_the_recorded_key_stays_stale(self):
        w = work_folder(self.court)
        out = adopt_reach.plan(w, [("now", self.now)], now_code=self.now)
        self.assertEqual((out["netband"]["state"], out["flights"]["state"]), ("stale", "stale"))

    def test_a_flights_key_of_another_netband_key_stays_stale(self):
        w = work_folder(self.court)
        keys = json.load(open(os.path.join(w, "stage_keys.json")))
        keys["netband"] = MASK + "_12345678"                         # the net band ran again after the flights
        json.dump(keys, open(os.path.join(w, "stage_keys.json"), "w"))
        out = adopt_reach.plan(w, [("old", self.court)], now_code=self.now)
        self.assertEqual(out["flights"]["state"], "stale")
        self.assertIn("netband part", out["flights"]["why"])

    def parts_folder(self, code):
        """A folder of code with its netband key adopted by E203 (as Igor and the E200 folder)."""
        w = work_folder(code)
        keys = json.load(open(os.path.join(w, "stage_keys.json")))
        now_nb = MASK + "_" + detect.content_key(files=code_closure.files(*detect.STAGE_ENTRIES["netband"], folder=self.now))
        keys["keysAdoptedA39"] = {"netband": {"from": keys["netband"], "proof": "x"}}
        keys["netband"] = now_nb
        json.dump(keys, open(os.path.join(w, "stage_keys.json"), "w"))
        return w, now_nb

    def test_a45_a_serve_ball_change_keeps_the_ball_flights_and_the_net_events(self):
        serve = code_copy(("serve_ball", None, "\n# a chain rule change\n"))
        w, nb = self.parts_folder(serve)
        out = adopt_reach.plan_parts(w, [("now", self.now), ("serve", serve)], now_code=self.now)
        self.assertEqual((out["run"], out["proof"]), (["wasbf"], "serve"))
        self.assertIn("serve_ball.py changed", out["why"])
        court = [os.path.join(w, f) for f in ("court_track.json", "court_track_refined.json")]
        now = detect.flight_keys(TAG, "", court, nb, folder=self.now)
        self.assertEqual(out["parts"], {"flights": now["flights"], "netevents": now["netevents"]})
        self.assertTrue(adopt_reach.apply_parts(w, out))
        saved = dict(detect.STAGE_KEYS)
        try:
            detect.STAGE_KEYS.clear()
            detect.STAGE_KEYS.update(json.load(open(os.path.join(w, "stage_keys.json"))))
            for _, _, outs in detect.FLIGHT_PARTS:
                for o in outs:
                    open(os.path.join(w, o), "w").write("x")
            self.assertEqual(detect.flight_parts_to_run(w, now), ["wasbf"])        # detect.py runs only feat_wasb
        finally:
            detect.STAGE_KEYS.clear()
            detect.STAGE_KEYS.update(saved)
        self.assertTrue(os.path.exists(os.path.join(w, "stage_keys.a45.json")))

    def test_a45_a_ball_full_change_keeps_no_part(self):
        w, _ = self.parts_folder(self.used)
        out = adopt_reach.plan_parts(w, [("used", self.used)], now_code=self.now)
        self.assertEqual((out["run"], out["parts"]), (["flights", "netevents", "wasbf"], {}))
        self.assertIn("ball_full.py changed", out["why"])
        self.assertFalse(adopt_reach.apply_parts(w, out))

    def test_a45_an_unused_court_change_keeps_all_parts(self):
        w, _ = self.parts_folder(self.court)
        self.assertEqual(adopt_reach.plan_parts(w, [("court", self.court)], now_code=self.now)["run"], [])

    def test_the_split_of_a_flights_key(self):
        nb = MASK + "_564913c2"
        self.assertEqual(adopt_reach.split_flights(TAG + "_7f3cb9f8_nb" + nb, nb), (TAG, "7f3cb9f8", nb))
        self.assertEqual(adopt_reach.split_flights(TAG + "_7f3cb9f8", None), (TAG, "7f3cb9f8", None))
        self.assertIsNone(adopt_reach.split_flights(TAG + "_7f3cb9f8_nb" + nb, MASK + "_00000000"))


if __name__ == "__main__":
    unittest.main()

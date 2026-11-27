"""Tests of the flights stage in three parts (A45, E204). Usage: python -m unittest test_flight_parts"""
import json, os, shutil, tempfile, unittest
import code_closure, detect

TAG = "_ball_e38_dim_0000aaaa"
NB = TAG + "_7fef77c9_11111111"


def code_copy(edit=None):
    """A copy of the modules of the flights closure; edit: (module, text to append)."""
    d = tempfile.mkdtemp(prefix="fpc_")
    for m in code_closure.modules(*detect.FLIGHTS_ENTRIES):
        shutil.copy(os.path.join(code_closure.HERE, m + ".py"), d)
    if edit:
        with open(os.path.join(d, edit[0] + ".py"), "a", encoding="utf-8") as f:
            f.write(edit[1])
    return d


class FlightParts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.code = code_copy()

    def setUp(self):
        self.w = tempfile.mkdtemp(prefix="fpw_")
        for f in ("court_track.json", "court_track_refined.json"):
            json.dump([{"t0": 0}], open(os.path.join(self.w, f), "w"))
        for _, _, outs in detect.FLIGHT_PARTS:
            for o in outs:
                open(os.path.join(self.w, o), "w").write("x")
        self.court = [os.path.join(self.w, f) for f in ("court_track.json", "court_track_refined.json")]
        self.saved = (dict(detect.STAGE_KEYS), detect.KEYS_FILE)
        detect.KEYS_FILE = os.path.join(self.w, "stage_keys.json")
        detect.STAGE_KEYS.clear()

    def tearDown(self):
        detect.STAGE_KEYS.clear()
        detect.STAGE_KEYS.update(self.saved[0])
        detect.KEYS_FILE = self.saved[1]

    def keys(self, code, nb=NB):
        return detect.flight_keys(TAG, "", self.court, nb, folder=code)

    def runs(self, code, nb=NB):
        """The parts that a run with the code in folder code runs, after a run with self.code."""
        ran = []
        fns = {p: (lambda p=p: ran.append(p)) for p, _, _ in detect.FLIGHT_PARTS}
        detect.run_flight_parts(self.w, self.keys(self.code), fns)
        self.assertEqual(ran, ["flights", "netevents", "wasbf"])          # the first run: all parts
        ran.clear()
        detect.run_flight_parts(self.w, self.keys(code, nb), fns)
        return ran

    def test_no_change_runs_nothing(self):
        self.assertEqual(self.runs(self.code), [])
        self.assertEqual(json.load(open(detect.KEYS_FILE))["wasbf"], self.keys(self.code)["wasbf"])

    def test_a_serve_ball_change_runs_only_feat_wasb(self):
        self.assertEqual(self.runs(code_copy(("serve_ball", "\n# a chain rule change\n"))), ["wasbf"])

    def test_a_ball_full_change_runs_all_three(self):
        self.assertEqual(self.runs(code_copy(("ball_full", "\n# a change\n"))), ["flights", "netevents", "wasbf"])

    def test_a_net_events_change_runs_it_and_feat_wasb(self):
        self.assertEqual(self.runs(code_copy(("net_events", "\n# a change\n"))), ["netevents", "wasbf"])

    def test_a_new_netband_key_runs_net_events_and_feat_wasb(self):
        self.assertEqual(self.runs(self.code, nb=TAG + "_7fef77c9_22222222"), ["netevents", "wasbf"])

    def test_a_court_change_runs_all_three(self):
        ran = self.runs(self.code)
        json.dump([{"t0": 1}], open(self.court[1], "w"))
        ran = []
        detect.run_flight_parts(self.w, self.keys(self.code), {p: (lambda p=p: ran.append(p)) for p, _, _ in detect.FLIGHT_PARTS})
        self.assertEqual(ran, ["flights", "netevents", "wasbf"])

    def test_a_missing_output_runs_its_part_and_the_parts_after_it(self):
        self.runs(self.code)
        os.remove(os.path.join(self.w, "net_events.json"))
        self.assertEqual(detect.flight_parts_to_run(self.w, self.keys(self.code)), ["netevents", "wasbf"])

    def test_the_one_key_of_before_is_adopted_only_when_it_is_the_key_of_now(self):
        one = detect.one_flights_key(TAG, "", self.court, NB, folder=self.code)
        self.assertTrue(one.endswith("_nb" + NB))
        detect.STAGE_KEYS["flights"] = one
        self.assertTrue(detect.adopt_flight_parts(self.w, TAG, "", self.court, NB, folder=self.code))
        self.assertEqual(detect.flight_parts_to_run(self.w, self.keys(self.code)), [])
        self.assertEqual(json.load(open(detect.KEYS_FILE))["flightsSplit"]["from"], one)
        self.assertFalse(detect.adopt_flight_parts(self.w, TAG, "", self.court, NB, folder=self.code))   # once
        detect.STAGE_KEYS.clear()
        detect.STAGE_KEYS["flights"] = detect.one_flights_key(TAG, "", self.court, NB,
                                                              folder=code_copy(("serve_ball", "\n# x\n")))
        self.assertFalse(detect.adopt_flight_parts(self.w, TAG, "", self.court, NB, folder=self.code))   # other code
        self.assertEqual(detect.flight_parts_to_run(self.w, self.keys(self.code)), ["flights", "netevents", "wasbf"])

    def test_the_parts_cover_the_closure_of_before(self):
        # Each module of the one key of before A45 is in the key of a part (no module is lost by the split).
        one = set(code_closure.reach(*detect.FLIGHTS_ENTRIES))
        parts = set()
        for _, entry, _ in detect.FLIGHT_PARTS:
            parts |= set(code_closure.reach(entry))
        self.assertEqual(one, parts)
        self.assertIn("serve_ball", code_closure.reach("feat_wasb"))
        self.assertNotIn("serve_ball", code_closure.reach("ball_full"))
        self.assertNotIn("serve_ball", code_closure.reach("net_events"))


if __name__ == "__main__":
    unittest.main()

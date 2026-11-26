"""Tests of the user's fault answers in the bench (S4 E158, faults_<match>.json).
Usage: python -m unittest test_bench_fault_answers"""
import json, os, shutil, tempfile, unittest
import bench
import faults_cases
import lab

MID = "m1015"
# Marked points A, B, C (source s). The lab marks start at the second serve: a row that matches no point and ends
# chain.FAULT_NEXT s or less before the next marked point is "fault, not checked".
POINTS = [("pA", 100.0, 110.0), ("pB", 140.0, 150.0), ("pC", 200.0, 210.0)]
ROWS = [(100.0, 110.0),    # matches A
        (120.0, 130.0),    # fault row, next B
        (170.0, 180.0),    # fault row, next C
        (185.0, 190.0),    # fault row, next C
        (300.0, 305.0)]    # a false rally (no next point)
M = {"marked": 3, "detected": 5, "matched": 1, "recall": 1 / 3, "precision": 0.2, "startAbsMed": 0.0, "endAbsMed": 0.0}


class FaultAnswers(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="fa_")
        json.dump({"points": [{"id": i, "startMs": a * 1000, "endMs": b * 1000, "outcome": "P1"} for i, a, b in POINTS]},
                  open(os.path.join(self.tmp, "gt.json"), "w"))
        self.old = (lab.mdir, bench.sides_file)
        lab.mdir = lambda m: self.tmp
        bench.sides_file = lambda m: os.path.join(self.tmp, f"sides_{m}.json")
        self.rows = [{"start": a, "end": b, "kind": "rally"} for a, b in ROWS]

    def tearDown(self):
        lab.mdir, bench.sides_file = self.old
        shutil.rmtree(self.tmp, ignore_errors=True)

    def answer(self, row, next_id, answer, h=None):
        a, b = ROWS[row]
        return {"row": f"{MID}_{a:.1f}", "rowHash": h or faults_cases.row_hash(MID, a, b, next_id), "start": a, "end": b,
                "next": next_id, "answer": answer}

    def write(self, answers):
        json.dump({"version": 1, "kind": "bananashot-faults", "match": MID, "answers": answers, "stale": []},
                  open(bench.faults_file(MID), "w"))

    def test_the_row_hash_of_s4(self):
        self.assertEqual(faults_cases.row_hash("m1015", 3328.0, 3344.0, "76707867-47a2-40f0-9153-e95b6da1f6a5"),
                         "102bf9c7f9e2006a")

    def test_no_answer_file_gives_the_numbers_of_before(self):
        out = bench.no_none(MID, self.rows, dict(M))
        self.assertEqual((out["marked"], out["detected"], out["matched"]), (3, 2, 1))
        self.assertEqual((out["faultNotChecked"], out["faultClipRows"]), (3, 0))
        self.assertNotIn("faultAnswers", out)

    def test_hidden_fault_rows_are_the_rows_before_a_next_point(self):
        # the one place of the rule (no_none and case_history): rows 1 to 3 end 25 s or less before the next point
        self.assertEqual(bench.hidden_fault_rows(MID, self.rows), ([1, 2, 3], []))

    def test_f_is_a_fault_clip_p_a_found_point_n_a_false_rally(self):
        self.write([self.answer(1, "pB", "F"), self.answer(2, "pC", "P"), self.answer(3, "pC", "N")])
        out = bench.no_none(MID, self.rows, dict(M))
        self.assertEqual(out["faultAnswers"], {"F": 1, "P": 1, "N": 1, "Q": 0, "none": 0})
        self.assertEqual((out["marked"], out["detected"], out["matched"]), (4, 4, 2))
        self.assertEqual((out["faultNotChecked"], out["faultClipRows"]), (0, 1))
        self.assertEqual((out["markedNoAnswers"], out["detectedNoAnswers"], out["matchedNoAnswers"]), (3, 2, 1))

    def test_q_and_a_changed_row_stay_not_checked(self):
        self.write([self.answer(1, "pB", "Q"), self.answer(2, "pC", "P", h="0123456789abcdef")])
        out = bench.no_none(MID, self.rows, dict(M))
        self.assertEqual(out["faultAnswers"], {"F": 0, "P": 0, "N": 0, "Q": 1, "none": 2})
        self.assertEqual((out["marked"], out["detected"], out["matched"], out["faultNotChecked"]), (3, 2, 1, 3))


if __name__ == "__main__":
    unittest.main()

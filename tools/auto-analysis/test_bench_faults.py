"""Tests of the chain fault rows of the bench report (G13, S2 f294b4a4). Usage: python -m unittest test_bench_faults"""
import unittest
import bench
import chain


class BenchFaults(unittest.TestCase):
    def setUp(self):
        chain.FAULTS.clear()

    def tearDown(self):
        chain.FAULTS.clear()

    def test_the_held_out_warnings_give_the_counts_of_the_chain(self):
        for t in (3.0, 9.0):
            chain.note_fault("shot chain", t, NameError("name 'same' is not defined"))
        chain.note_fault("fault chain", 5.0, KeyError("shots"))
        warnings = chain.fault_texts() + ["The court track is missing; the fixed court was used."]
        self.assertEqual(bench.heldout_faults(warnings), {"shot chain": 2, "fault chain": 1})

    def test_no_fault_gives_no_row(self):
        self.assertEqual(bench.heldout_faults([]), {})
        self.assertEqual(bench.fault_rows({"m1015": {"chainFaults": {}}, "m1020": {}}), [])

    def test_a_match_with_faults_gives_one_bold_row(self):
        rows = bench.fault_rows({"m1015": {"chainFaults": {"shot chain": 79}}, "m1020": {"chainFaults": {}}})
        self.assertEqual(rows, ["| **Chain faults (the old rule decided)** | **m1015: shot chain 79** |"])


if __name__ == "__main__":
    unittest.main()

"""Tests of the fault records of the chain (chain-inventory G13). Usage: python -m unittest test_chain_faults"""
import unittest
import chain


class ChainFaults(unittest.TestCase):
    def setUp(self):
        chain.FAULTS.clear()

    def test_each_fault_kind_is_one_warning_with_its_count(self):
        chain.note_fault("shot chain", 12.34, KeyError("ev1"))
        chain.note_fault("shot chain", 50.0, ValueError("x"))
        chain.note_fault("fault chain", 7.0, IndexError("list index out of range"))
        texts = chain.fault_texts()
        self.assertEqual(len(texts), 2)
        self.assertIn("The shot chain failed on 2 rallies (first at 12.3 s: KeyError: 'ev1')", texts[0])
        self.assertIn("old ball rule", texts[0])
        self.assertIn("The fault chain failed on 1 rally ", texts[1])

    def test_no_fault_no_warning(self):
        self.assertEqual(chain.fault_texts(), [])

    def test_partial_rows_keep_the_short_points(self):
        rows = chain.rally_rows([(10.0, 20.0, 0.8), (30.0, 35.0, -1.0)])
        self.assertEqual([r["kind"] for r in rows], ["rally", "short"])
        self.assertIsNone(rows[1]["prob"])


if __name__ == "__main__":
    unittest.main()

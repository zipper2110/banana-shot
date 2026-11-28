"""Tests of server_rule (A58, E224): the server of the rows with no server, and the pose vote against a serve event.
Usage: python -m unittest test_server_rule"""
import os
import unittest
import server_rule as sr


def row(t, server=None, near=None, p1_near=True):
    return {"start": t, "end": t + 8.0, "server": server, "serverNear": near, "p1Near": p1_near}


class ServerRule(unittest.TestCase):
    def test_the_default_is_on(self):
        if "CHAIN_SERVER_FILL" not in os.environ:
            self.assertTrue(sr.FILL)

    def test_a_strong_vote_wins_over_the_serve_event_end(self):
        # E224 m1020 363: the event says far, the near vote is 0.74
        rows = sr.fill([row(363.0, "P2", False)], [(0.74, -0.25)])
        self.assertEqual((rows[0]["server"], rows[0]["serverNear"], rows[0]["serverFrom"]), ("P1", True, "vote"))

    def test_a_row_that_agrees_with_the_vote_does_not_change(self):
        rows = sr.fill([row(10.0, "P1", True)], [(0.8, 0.1)])
        self.assertEqual((rows[0]["server"], rows[0]["serverNear"]), ("P1", True))
        self.assertNotIn("serverFrom", rows[0])

    def test_a_weak_vote_does_not_change_a_server(self):
        rows = sr.fill([row(10.0, "P1", True)], [(0.1, 0.45)])
        self.assertEqual(rows[0]["server"], "P1")

    def test_no_server_between_two_rows_of_the_same_server(self):
        # p1Near False: P2 is at the near end in that row
        rows = sr.fill([row(0.0, "P2", True, False), row(30.0, p1_near=False), row(60.0, "P2", True, False)],
                       [(0.9, 0.0), (None, None), (0.9, 0.0)])
        self.assertEqual((rows[1]["server"], rows[1]["serverNear"], rows[1]["serverFrom"]), ("P2", True, "game"))

    def test_the_nearest_rows_with_a_server_are_the_neighbors(self):
        rows = sr.fill([row(0.0, "P1", False), row(30.0), row(60.0), row(90.0, "P1", False)],
                       [(0.0, 0.9), (None, None), (None, None), (0.0, 0.9)])
        self.assertEqual([r["server"] for r in rows], ["P1", "P1", "P1", "P1"])

    def test_neighbors_that_differ_give_the_larger_weak_vote(self):
        rows = sr.fill([row(0.0, "P1", True), row(30.0), row(60.0, "P2", False)], [(0.9, 0.0), (-0.23, 0.37), (0.0, 0.9)])
        self.assertEqual((rows[1]["server"], rows[1]["serverNear"], rows[1]["serverFrom"]), ("P2", False, "weak"))

    def test_no_neighbor_and_no_vote_stays_no_server(self):
        rows = sr.fill([row(0.0), row(30.0, "P1", True)], [(0.2, None), (0.9, 0.0)])
        self.assertIsNone(rows[0]["server"])
        self.assertIsNone(rows[0]["serverNear"])

    def test_a_filled_row_is_not_a_neighbor_of_the_next_row(self):
        # row 1 gets P1 from its neighbors; row 3 has P1 before (row 0, not row 1) and P2 after, so its weak vote acts
        rows = sr.fill([row(0.0, "P1", True), row(30.0), row(60.0, "P1", True), row(90.0), row(120.0, "P2", False)],
                       [(0.9, 0.0), (None, None), (0.9, 0.0), (0.3, -0.5), (0.0, 0.9)])
        self.assertEqual(rows[1]["serverFrom"], "game")
        self.assertEqual((rows[3]["server"], rows[3]["serverFrom"]), ("P1", "weak"))


if __name__ == "__main__":
    unittest.main()

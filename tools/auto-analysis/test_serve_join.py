"""Tests of the time of a joined toss+serve event (A36, E174: SERVE_JOIN_CONTACT).
Usage: python -m unittest test_serve_join"""
import unittest
import serve_ball as sb


def flight(t0, t1, p0, v0, n=30, rms=1.0, d_row=100.0):
    return {"t0": t0, "t1": t1, "p0": list(p0), "v0": list(v0), "n": n, "rms": rms, "dRow": d_row}


# A far server: the toss (slow and high over the far baseline), then the serve 1.1 s later (fast, to the near half).
TOSS = flight(100.0, 100.9, (0.0, 12.5, 1.5), (0.0, -1.0, 6.0), d_row=-60.0)
SERVE = flight(101.1, 101.6, (0.2, 11.7, 2.5), (0.0, -25.0, -2.0))
# A fast flight of the same end 1.4 s after the contact (2.5 s after the toss start), for example a second part of the serve.
PART = flight(102.5, 102.8, (0.1, 9.0, 1.5), (0.0, -20.0, 1.0))


class ServeJoin(unittest.TestCase):
    def setUp(self):
        self.old = sb.SERVE_JOIN_CONTACT

    def tearDown(self):
        sb.SERVE_JOIN_CONTACT = self.old

    def test_the_flights_are_a_toss_and_a_serve(self):
        self.assertTrue(sb.is_toss(TOSS))
        self.assertTrue(sb.is_serve(SERVE))

    def test_off_the_event_has_the_time_of_the_toss_start(self):
        sb.SERVE_JOIN_CONTACT = False
        ev = sb.serve_events([TOSS, SERVE], 0, 200)
        self.assertEqual([(e[0], e[1], e[2]) for e in ev], [(100.0, False, "toss+serve")])
        self.assertIs(ev[0][3], SERVE)

    def test_on_the_event_has_the_time_of_the_serve_flight(self):
        sb.SERVE_JOIN_CONTACT = True
        ev = sb.serve_events([TOSS, SERVE], 0, 200)
        self.assertEqual([(e[0], e[1], e[2]) for e in ev], [(101.1, False, "toss+serve")])
        self.assertIs(ev[0][3], SERVE)

    def test_a_later_flight_joins_within_1_5_s_of_the_event_time(self):
        self.assertTrue(sb.is_fast_out(PART))
        sb.SERVE_JOIN_CONTACT = False                             # 2.5 s after the toss start: an event of its own
        self.assertEqual([(e[0], e[2]) for e in sb.serve_events([TOSS, SERVE, PART], 0, 200)], [(100.0, "toss+serve"), (102.5, "fast")])
        sb.SERVE_JOIN_CONTACT = True                              # 1.4 s after the contact: it joins the serve event
        self.assertEqual([(e[0], e[2]) for e in sb.serve_events([TOSS, SERVE, PART], 0, 200)], [(101.1, "toss+serve")])

    def test_a_serve_with_no_toss_is_not_changed(self):
        for on in (False, True):
            sb.SERVE_JOIN_CONTACT = on
            self.assertEqual([(e[0], e[2]) for e in sb.serve_events([SERVE], 0, 200)], [(101.1, "serve")])


if __name__ == "__main__":
    unittest.main()

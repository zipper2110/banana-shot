-- The session totals of each UTC day from the analytics database (the user base section).
-- The analytics Worker deletes a session 90 days after its last summary. These totals stay, so that the cockpit can show
-- all-time numbers. They are counts only: no session ID and no other data of a session.
-- `dev` 0: release builds only. `dev` 1: release and development (-SNAPSHOT) builds.
CREATE TABLE IF NOT EXISTS app_daily (
  day TEXT NOT NULL,
  dev INTEGER NOT NULL,
  sessions INTEGER NOT NULL,
  session_n_1 INTEGER NOT NULL,
  session_n_2_5 INTEGER NOT NULL,
  session_n_6_20 INTEGER NOT NULL,
  session_n_21p INTEGER NOT NULL,
  taken_at INTEGER NOT NULL,
  PRIMARY KEY (day, dev),
  CHECK (dev IN (0,1))
);

-- The reports of B-8. The log is not here: the Worker sends it to Telegram and drops it (decision 10).
CREATE TABLE IF NOT EXISTS feedback_report (
  report_id TEXT PRIMARY KEY, received_at INTEGER NOT NULL, topic TEXT NOT NULL, message TEXT NOT NULL,
  email TEXT, app_version TEXT NOT NULL, os_name TEXT NOT NULL, os_version TEXT NOT NULL, java_version TEXT NOT NULL,
  error TEXT, has_log INTEGER NOT NULL, delivered INTEGER NOT NULL,
  CHECK (topic IN ('problem','idea','question','other')), CHECK (length(message) BETWEEN 1 AND 10000),
  CHECK (has_log IN (0,1)), CHECK (delivered IN (0,1))
);
CREATE INDEX IF NOT EXISTS feedback_report_received_at_idx ON feedback_report(received_at);
-- The report count of each IP address in the current hour. The ID is an HMAC, not the address.
CREATE TABLE IF NOT EXISTS feedback_rate (
  id TEXT PRIMARY KEY, hour INTEGER NOT NULL, count INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS feedback_rate_hour_idx ON feedback_rate(hour);

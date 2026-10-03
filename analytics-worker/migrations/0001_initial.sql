-- One row for each analytics session (docs/analytics/design.md, "Worker and database").
-- The row keeps the summary with the highest snapshot. Both times are rounded down to the hour.
CREATE TABLE IF NOT EXISTS analytics_session (
  session_id TEXT PRIMARY KEY,
  first_received_at INTEGER NOT NULL,
  last_received_at INTEGER NOT NULL,
  schema_version INTEGER NOT NULL,
  notice_version INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  os_family TEXT NOT NULL,
  snapshot INTEGER NOT NULL,
  final INTEGER NOT NULL,
  duration_s INTEGER NOT NULL,
  active_s INTEGER NOT NULL,
  counters TEXT NOT NULL CHECK (json_valid(counters) AND length(counters) <= 4096),
  CHECK (final IN (0,1)), CHECK (snapshot BETWEEN 0 AND 1000000),
  CHECK (duration_s BETWEEN 0 AND 604800), CHECK (active_s BETWEEN 0 AND 604800)
);
CREATE INDEX IF NOT EXISTS analytics_session_last_received_at_idx ON analytics_session(last_received_at);
-- The summary count of each IP address in the current hour. The ID is an HMAC, not the address.
CREATE TABLE IF NOT EXISTS analytics_rate (
  id TEXT PRIMARY KEY, hour INTEGER NOT NULL, count INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS analytics_rate_hour_idx ON analytics_rate(hour);
-- The result of the last daily retention run.
CREATE TABLE IF NOT EXISTS analytics_retention_status (
  id INTEGER PRIMARY KEY CHECK (id = 1), ran_at INTEGER NOT NULL, deleted_count INTEGER NOT NULL,
  oldest_received_at INTEGER
);

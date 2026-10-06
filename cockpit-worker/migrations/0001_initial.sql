-- The download totals of each release file for each UTC day (docs/cockpit/b-41-tasks.md, decision 3).
-- The hourly cron writes the current totals. The last write of a day stays.
CREATE TABLE IF NOT EXISTS download_snapshot (
  day TEXT NOT NULL,
  release_tag TEXT NOT NULL,
  asset_name TEXT NOT NULL,
  download_count INTEGER NOT NULL,
  published_at TEXT,
  taken_at INTEGER NOT NULL,
  PRIMARY KEY (day, release_tag, asset_name),
  CHECK (download_count >= 0)
);
-- The result of the last run of each job.
CREATE TABLE IF NOT EXISTS job_status (
  name TEXT PRIMARY KEY, ran_at INTEGER NOT NULL, ok INTEGER NOT NULL, message TEXT NOT NULL,
  CHECK (ok IN (0,1))
);

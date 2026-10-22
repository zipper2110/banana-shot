-- The totals at the last run of the Telegram digest (src/notify.ts). The next run sends only the increase.
-- Visits and installs have the UTC day of the total. The digest deletes these rows when the day is older than
-- yesterday. Downloads have no day: the key is the release tag and the file name.
CREATE TABLE IF NOT EXISTS notify_seen (
  source TEXT NOT NULL CHECK (source IN ('visit','download','install')),
  key TEXT NOT NULL,
  day TEXT NOT NULL,
  count INTEGER NOT NULL CHECK (count >= 0),
  PRIMARY KEY (source, key)
);

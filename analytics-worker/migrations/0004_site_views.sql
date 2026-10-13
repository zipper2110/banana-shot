-- The page views of the website (site/README.md, "Page view counter"). One row for each UTC day, page, referrer host,
-- country, and device type. The rows contain only totals: no IP address, no user agent, no time of day.
CREATE TABLE IF NOT EXISTS site_daily (
  date TEXT NOT NULL CHECK (length(date) = 10),
  path TEXT NOT NULL CHECK (length(path) <= 64),
  referrer TEXT NOT NULL CHECK (length(referrer) <= 100),
  country TEXT NOT NULL CHECK (length(country) = 2),
  device TEXT NOT NULL CHECK (device IN ('desktop', 'mobile', 'tablet')),
  views INTEGER NOT NULL CHECK (views >= 0),
  visits INTEGER NOT NULL CHECK (visits >= 0),
  PRIMARY KEY (date, path, referrer, country, device)
);

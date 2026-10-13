-- The page requests of bots and other programs on the website (site/README.md, "Bot counter"). The site Worker
-- reports each HTML request. The analytics Worker keeps only the requests of known bots and programs: one row for
-- each UTC day, bot name, and page. The requests of people are not in this table.
CREATE TABLE IF NOT EXISTS site_bot_daily (
  date TEXT NOT NULL CHECK (length(date) = 10),
  bot TEXT NOT NULL CHECK (length(bot) <= 32),
  path TEXT NOT NULL CHECK (length(path) <= 64),
  requests INTEGER NOT NULL CHECK (requests >= 0),
  PRIMARY KEY (date, bot, path)
);

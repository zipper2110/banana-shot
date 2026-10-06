-- The level of the statistics (docs/analytics/design.md, "Levels"). Schema version 2 sends it with each summary.
-- The rows of version 1 came only from test builds with the old opt-in consent, so they get 'extended'.
ALTER TABLE analytics_session ADD COLUMN level TEXT NOT NULL DEFAULT 'extended' CHECK (level IN ('essential', 'extended'));

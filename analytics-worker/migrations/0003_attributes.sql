-- The session attributes (docs/analytics/design.md, "Attributes"). Schema version 3 sends them with an extended
-- summary. The rows of schema version 2 and the essential rows get an empty object.
ALTER TABLE analytics_session ADD COLUMN attributes TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(attributes) AND length(attributes) <= 1024);

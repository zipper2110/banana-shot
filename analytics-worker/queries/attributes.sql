-- B-44: the session attributes (theme, accent, language, sport) of the extended sessions in the last 30 days.
-- Only schema 3 and later summaries have attributes. A sport value is a JSON array, so each combination of sports is one row.
-- Run (in analytics-worker): npm run query -- queries/attributes.sql
WITH recent AS (
  SELECT attributes FROM analytics_session
  WHERE app_version <> 'synthetic-smoke' AND level = 'extended' AND schema_version >= 3
    AND last_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 30 * 86400) * 1000
)
SELECT a.key AS attribute, a.value AS value, COUNT(*) AS sessions
FROM recent, json_each(recent.attributes) AS a
GROUP BY a.key, a.value
ORDER BY a.key, sessions DESC, a.value;

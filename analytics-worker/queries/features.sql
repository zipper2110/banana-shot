-- Question 1: which tabs and features do users use?
-- One line for each tab and feature counter in the last 30 days: the number of sessions that used it, the percent
-- of all sessions, and the total count. The tab_s_ counters are active seconds.
-- Only extended sessions have these counters, so the percent uses only the extended sessions.
-- Run (in analytics-worker): npm run query -- queries/features.sql
WITH recent AS (
  SELECT counters FROM analytics_session
  WHERE app_version <> 'synthetic-smoke' AND level = 'extended'
    AND last_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 30 * 86400) * 1000
)
SELECT c.key AS counter,
       COUNT(*) AS sessions,
       ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM recent), 1) AS pct_sessions,
       SUM(c.value) AS total
FROM recent, json_each(recent.counters) AS c
WHERE c.key NOT LIKE 'export\_%' ESCAPE '\'
  AND c.key NOT LIKE 'session\_n\_%' ESCAPE '\'
  AND c.key NOT IN ('unclean_exit', 'uncaught_error')
GROUP BY c.key
ORDER BY c.key;

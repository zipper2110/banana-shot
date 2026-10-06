-- Does anyone use the app? One line for each week (Monday to Sunday, UTC) of the last 12 weeks. All sessions count,
-- essential and extended. There is no install ID, so the counts are estimates:
-- - first_sessions: sessions with session_n_1. About the number of new installs that started the app.
-- - returning_sessions: sessions with a higher session number. Users who came back.
-- - active_hours: the sum of active_s (the main window was active).
-- Run (in analytics-worker): npm run query -- queries/usage.sql
SELECT date(first_received_at / 1000, 'unixepoch', 'weekday 0', '-6 days') AS week,
       COUNT(*) AS sessions,
       SUM(json_extract(counters, '$.session_n_1') IS NOT NULL) AS first_sessions,
       SUM(json_extract(counters, '$.session_n_1') IS NULL) AS returning_sessions,
       SUM(active_s >= 60) AS sessions_active_1_min_plus,
       ROUND(SUM(active_s) / 3600.0, 1) AS active_hours,
       ROUND(100.0 * SUM(level = 'extended') / COUNT(*), 1) AS pct_extended
FROM analytics_session
WHERE app_version <> 'synthetic-smoke'
  AND first_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 84 * 86400) * 1000
GROUP BY week
ORDER BY week DESC;

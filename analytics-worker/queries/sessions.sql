-- Question 3: how long are sessions? Which app versions and OS families are in use?
-- One line for each app version and OS family in the last 30 days. All sessions count, essential and extended.
-- active_s is the time while the main window is active. Crashes are unclean_exit (the previous session did not close normally), not final = 0.
-- Run (in analytics-worker): npm run query -- queries/sessions.sql
SELECT app_version,
       os_family,
       COUNT(*) AS sessions,
       SUM(level = 'extended') AS extended_sessions,
       ROUND(AVG(active_s) / 60.0, 1) AS avg_active_min,
       SUM(active_s < 60) AS active_under_1_min,
       SUM(active_s >= 60 AND active_s < 600) AS active_1_to_10_min,
       SUM(active_s >= 600 AND active_s < 3600) AS active_10_to_60_min,
       SUM(active_s >= 3600) AS active_over_60_min,
       ROUND(AVG(duration_s) / 60.0, 1) AS avg_open_min,
       SUM(COALESCE(json_extract(counters, '$.unclean_exit'), 0)) AS unclean_exits,
       SUM(COALESCE(json_extract(counters, '$.uncaught_error'), 0)) AS uncaught_errors,
       SUM(json_extract(counters, '$.session_n_1') IS NOT NULL) AS first_sessions,
       SUM(json_extract(counters, '$.session_n_2_5') IS NOT NULL) AS sessions_2_to_5,
       SUM(json_extract(counters, '$.session_n_6_20') IS NOT NULL) AS sessions_6_to_20,
       SUM(json_extract(counters, '$.session_n_21p') IS NOT NULL) AS sessions_21_plus
FROM analytics_session
WHERE app_version <> 'synthetic-smoke'
  AND last_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 30 * 86400) * 1000
GROUP BY app_version, os_family
ORDER BY sessions DESC;

-- B-11 E2: the app language and the Windows display language of the sessions in the last 30 days, for all levels.
-- A language with fewer than 5 sessions shows as 'other' (docs/analytics/legitimate-interest.md, safeguards).
-- Sessions of older app versions have no language attribute. They show as 'unknown'.
-- Run (in analytics-worker): npm run query -- queries/languages.sql
WITH recent AS (
  SELECT COALESCE(json_extract(attributes, '$.os_language'), 'unknown') AS os_language,
         COALESCE(json_extract(attributes, '$.language'), 'unknown') AS language
  FROM analytics_session
  WHERE app_version <> 'synthetic-smoke'
    AND last_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 30 * 86400) * 1000
),
counts AS (
  SELECT 'os_language' AS attribute, os_language AS value, COUNT(*) AS sessions FROM recent GROUP BY os_language
  UNION ALL
  SELECT 'language', language, COUNT(*) FROM recent GROUP BY language
)
SELECT attribute, CASE WHEN sessions < 5 AND value <> 'unknown' THEN 'other' ELSE value END AS value, SUM(sessions) AS sessions
FROM counts
GROUP BY attribute, 2
HAVING SUM(sessions) >= 5
ORDER BY attribute, sessions DESC, value;

-- Question 2, details: failure reasons, export options, and output resolutions in the last 30 days.
-- export_fail_ counts failed exports. export_opt_ and export_res_ count started exports.
-- Run: npx wrangler d1 execute bananashot-analytics --remote --file queries/export-details.sql
WITH recent AS (
  SELECT counters FROM analytics_session
  WHERE app_version <> 'synthetic-smoke'
    AND last_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 30 * 86400) * 1000
)
SELECT c.key AS counter, COUNT(*) AS sessions, SUM(c.value) AS total
FROM recent, json_each(recent.counters) AS c
WHERE c.key LIKE 'export\_fail\_%' ESCAPE '\'
   OR c.key LIKE 'export\_opt\_%' ESCAPE '\'
   OR c.key LIKE 'export\_res\_%' ESCAPE '\'
GROUP BY c.key
ORDER BY c.key;

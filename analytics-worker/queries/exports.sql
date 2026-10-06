-- Question 2: how many exports start, complete, fail, or get cancelled? With which encoder? How fast are they?
-- One line for each encoder in the last 30 days. speed is the output video seconds for each second of run time
-- of the completed exports. A speed of 2.0 encodes 1 minute of video in 30 seconds.
-- Run (in analytics-worker): npm run query -- queries/exports.sql
WITH recent AS (
  SELECT counters FROM analytics_session
  WHERE app_version <> 'synthetic-smoke' AND level = 'extended'
    AND last_received_at >= (CAST(strftime('%s', 'now') AS INTEGER) - 30 * 86400) * 1000
),
encoder(name) AS (VALUES ('software'), ('nvenc'), ('amf'), ('qsv')),
totals AS (
  SELECT encoder.name AS encoder,
         SUM(COALESCE(json_extract(recent.counters, '$.export_started_' || encoder.name), 0)) AS started,
         SUM(COALESCE(json_extract(recent.counters, '$.export_completed_' || encoder.name), 0)) AS completed,
         SUM(COALESCE(json_extract(recent.counters, '$.export_failed_' || encoder.name), 0)) AS failed,
         SUM(COALESCE(json_extract(recent.counters, '$.export_cancelled_' || encoder.name), 0)) AS cancelled,
         SUM(COALESCE(json_extract(recent.counters, '$.export_interrupted_' || encoder.name), 0)) AS interrupted,
         SUM(COALESCE(json_extract(recent.counters, '$.export_run_s_' || encoder.name), 0)) AS run_s,
         SUM(COALESCE(json_extract(recent.counters, '$.export_video_s_' || encoder.name), 0)) AS video_s
  FROM encoder, recent
  GROUP BY encoder.name
)
SELECT encoder, started, completed, failed, cancelled, interrupted, run_s, video_s,
       CASE WHEN run_s > 0 THEN ROUND(1.0 * video_s / run_s, 2) END AS speed
FROM totals
ORDER BY started DESC;

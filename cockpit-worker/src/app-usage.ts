import type { Env } from './env';
import { type Period, dates, parseDate, weekOf, weeks } from './period';

const ENCODERS = ['software', 'nvenc', 'amf', 'qsv'] as const;

/** Decision 10: never the smoke rows; the development versions only when the page asks for them. */
const FILTER = "app_version <> 'synthetic-smoke' AND (? = 1 OR app_version NOT LIKE '%-SNAPSHOT')";
const IN_PERIOD = `${FILTER} AND first_received_at >= ? AND first_received_at < ?`;

function counter(key: string): string {
  return `COALESCE(json_extract(counters, '$.${key}'), 0)`;
}

function sumOfEncoders(prefix: string): string {
  return ENCODERS.map(encoder => counter(`${prefix}${encoder}`)).join(' + ');
}

const DAILY = `SELECT date(first_received_at / 1000, 'unixepoch') AS day,
  COUNT(*) AS sessions,
  SUM(json_extract(counters, '$.session_n_1') IS NOT NULL) AS first_sessions,
  SUM(level = 'extended') AS extended_sessions,
  SUM(active_s) AS active_s,
  SUM(${counter('unclean_exit')}) AS unclean_exits,
  SUM(${counter('uncaught_error')}) AS uncaught_errors,
  SUM(${counter('video_open_failed')}) AS video_open_failed,
  SUM(${sumOfEncoders('export_started_')}) AS exports_started,
  SUM(${sumOfEncoders('export_completed_')}) AS exports_completed,
  SUM(${sumOfEncoders('export_failed_')}) AS exports_failed
FROM analytics_session WHERE ${IN_PERIOD} GROUP BY day ORDER BY day`;

const VERSIONS = `SELECT app_version AS version, COUNT(*) AS sessions,
  SUM(json_extract(counters, '$.session_n_1') IS NOT NULL) AS first_sessions,
  SUM(${counter('unclean_exit')}) AS unclean_exits,
  SUM(${counter('uncaught_error')}) AS uncaught_errors,
  MAX(last_received_at) AS last_seen
FROM analytics_session WHERE ${IN_PERIOD} GROUP BY app_version ORDER BY sessions DESC LIMIT 12`;

const OS_FAMILIES = `SELECT os_family AS name, COUNT(*) AS sessions
FROM analytics_session WHERE ${IN_PERIOD} GROUP BY os_family ORDER BY sessions DESC`;

const SESSION_SHAPE = `SELECT COUNT(*) AS sessions,
  SUM(level = 'extended') AS extended_sessions,
  ROUND(AVG(active_s) / 60.0, 1) AS avg_active_min,
  SUM(active_s < 60) AS active_under_1_min,
  SUM(active_s >= 60 AND active_s < 600) AS active_1_to_10_min,
  SUM(active_s >= 600 AND active_s < 3600) AS active_10_to_60_min,
  SUM(active_s >= 3600) AS active_over_60_min,
  SUM(json_extract(counters, '$.session_n_1') IS NOT NULL) AS session_n_1,
  SUM(json_extract(counters, '$.session_n_2_5') IS NOT NULL) AS session_n_2_5,
  SUM(json_extract(counters, '$.session_n_6_20') IS NOT NULL) AS session_n_6_20,
  SUM(json_extract(counters, '$.session_n_21p') IS NOT NULL) AS session_n_21p
FROM analytics_session WHERE ${IN_PERIOD}`;

/** The counters of the extended sessions (the features, the tabs, and the export details). */
const COUNTERS = `SELECT c.key AS key, COUNT(*) AS sessions, SUM(c.value) AS total
FROM analytics_session, json_each(analytics_session.counters) AS c
WHERE ${IN_PERIOD} AND level = 'extended'
  AND c.key NOT LIKE 'session\\_n\\_%' ESCAPE '\\' AND c.key NOT IN ('unclean_exit', 'uncaught_error')
GROUP BY c.key ORDER BY c.key`;

const RETENTION = 'SELECT ran_at, deleted_count, oldest_received_at FROM analytics_retention_status WHERE id = 1';

interface DailyRow {
  day: string; sessions: number; first_sessions: number; extended_sessions: number; active_s: number;
  unclean_exits: number; uncaught_errors: number; video_open_failed: number;
  exports_started: number; exports_completed: number; exports_failed: number;
}

type Totals = Omit<DailyRow, 'day'>;

export interface AppUsage {
  totals: Totals;
  prevTotals: Totals;
  daily: (DailyRow & { date: string })[];
  weekly: (Totals & { date: string })[];
  versions: { version: string; sessions: number; first_sessions: number; unclean_exits: number; uncaught_errors: number; last_seen: number }[];
  osFamilies: { name: string; sessions: number }[];
  sessionShape: Record<string, number | null>;
  /** Counter key -> sessions that used it and the total. Only the extended sessions. */
  counters: { key: string; sessions: number; total: number }[];
  encoders: { encoder: string; started: number; completed: number; failed: number; cancelled: number; interrupted: number; speed: number | null }[];
  retention: { ranAt: number; deletedCount: number; oldestReceivedAt: number | null } | null;
}

const ZERO: Totals = {
  sessions: 0, first_sessions: 0, extended_sessions: 0, active_s: 0, unclean_exits: 0, uncaught_errors: 0,
  video_open_failed: 0, exports_started: 0, exports_completed: 0, exports_failed: 0,
};

function add(target: Totals, row: Totals) {
  for (const key of Object.keys(ZERO) as (keyof Totals)[]) target[key] += row[key] ?? 0;
}

export async function loadAppUsage(env: Env, period: Period, includeDev: boolean): Promise<AppUsage> {
  const db = env.ANALYTICS_DB;
  const dev = includeDev ? 1 : 0;
  const from = Math.min(period.prevStart, period.weeksStart);
  const [dailyRows, versions, osFamilies, shape, counters, retention] = await Promise.all([
    db.prepare(DAILY).bind(dev, from, period.end).all<DailyRow>(),
    db.prepare(VERSIONS).bind(dev, period.start, period.end).all<AppUsage['versions'][number]>(),
    db.prepare(OS_FAMILIES).bind(dev, period.start, period.end).all<AppUsage['osFamilies'][number]>(),
    db.prepare(SESSION_SHAPE).bind(dev, period.start, period.end).first<Record<string, number | null>>(),
    db.prepare(COUNTERS).bind(dev, period.start, period.end).all<AppUsage['counters'][number]>(),
    db.prepare(RETENTION).first<{ ran_at: number; deleted_count: number; oldest_received_at: number | null }>(),
  ]);

  const byDay = new Map(dailyRows.results.map(row => [row.day, row]));
  const totals = { ...ZERO }, prevTotals = { ...ZERO };
  const weekly = weeks(period).map(date => ({ date, ...ZERO }));
  const weekIndex = new Map(weekly.map((item, index) => [item.date, index]));
  for (const row of dailyRows.results) {
    const time = parseDate(row.day);
    if (time >= period.start) add(totals, row);
    else if (time >= period.prevStart) add(prevTotals, row);
    const week = weekly[weekIndex.get(weekOf(row.day)) ?? -1];
    if (week) add(week, row);
  }

  return {
    totals,
    prevTotals,
    daily: dates(period.start, period.end).map(date => ({ ...ZERO, day: date, ...byDay.get(date), date })),
    weekly,
    versions: versions.results,
    osFamilies: osFamilies.results,
    sessionShape: shape ?? {},
    counters: counters.results,
    encoders: encoderTable(counters.results),
    retention: retention ? { ranAt: retention.ran_at, deletedCount: retention.deleted_count, oldestReceivedAt: retention.oldest_received_at } : null,
  };
}

function encoderTable(counters: AppUsage['counters']): AppUsage['encoders'] {
  const total = new Map(counters.map(row => [row.key, row.total]));
  const value = (key: string) => total.get(key) ?? 0;
  return ENCODERS.map(encoder => {
    const runS = value(`export_run_s_${encoder}`), videoS = value(`export_video_s_${encoder}`);
    return {
      encoder,
      started: value(`export_started_${encoder}`),
      completed: value(`export_completed_${encoder}`),
      failed: value(`export_failed_${encoder}`),
      cancelled: value(`export_cancelled_${encoder}`),
      interrupted: value(`export_interrupted_${encoder}`),
      speed: runS > 0 ? Math.round(100 * videoS / runS) / 100 : null,
    };
  }).sort((a, b) => b.started - a.started);
}

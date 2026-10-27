import type { Env } from './env';
import { type Period, dataStart, dates, parseDate, weekOf, weeks } from './period';

const ENCODERS = ['software', 'nvenc', 'amf', 'qsv'] as const;

/** Decision 10: never the smoke rows; the development versions only when the page asks for them. */
export const FILTER = "app_version <> 'synthetic-smoke' AND (? = 1 OR app_version NOT LIKE '%-SNAPSHOT')";
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
  SUM(level = 'extended' AND schema_version >= 3) AS attribute_sessions,
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

/**
 * The session attributes (B-44) of the extended sessions. Only schema 3 and later summaries have them.
 * An array value (sport) groups as its JSON text, so each combination of sports is one row.
 */
const ATTRIBUTES = `SELECT a.key AS key, a.value AS value, COUNT(*) AS sessions
FROM analytics_session, json_each(analytics_session.attributes) AS a
WHERE ${IN_PERIOD} AND level = 'extended' AND a.key NOT IN ('language', 'os_language')
GROUP BY a.key, a.value ORDER BY a.key, sessions DESC, a.value`;

/**
 * B-11 E2: the app language and the Windows display language of all sessions (essential and extended), for each day.
 * A session of an older app version has no language attribute: its value is 'unknown'. Only `languageGroups` may give
 * these counts to the page, because a group must have at least 5 sessions.
 */
const LANGUAGES = `SELECT date(first_received_at / 1000, 'unixepoch') AS day,
  COALESCE(json_extract(attributes, '$.os_language'), 'unknown') AS os_language,
  COALESCE(json_extract(attributes, '$.language'), 'unknown') AS language,
  COUNT(*) AS sessions
FROM analytics_session WHERE ${IN_PERIOD} GROUP BY day, os_language, language`;

/** The smallest group of sessions that the cockpit shows for a language (docs/analytics/legitimate-interest.md). */
export const MIN_LANGUAGE_SESSIONS = 5;
export const UNKNOWN_LANGUAGE = 'unknown';
export const OTHER_LANGUAGE = 'other';

interface LanguageDayRow { day: string; os_language: string; language: string; sessions: number }

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
  /** Attribute key and value -> extended sessions. An array value is its JSON text, for example '["padel","tennis"]'. */
  attributes: { key: string; value: string; sessions: number }[];
  encoders: { encoder: string; started: number; completed: number; failed: number; cancelled: number; interrupted: number; speed: number | null }[];
  /** B-11 E2: the language groups of all sessions. Each group has at least 5 sessions. */
  languages: Languages;
  retention: { ranAt: number; deletedCount: number; oldestReceivedAt: number | null } | null;
}

export interface LanguageGroup { value: string; sessions: number }

export interface Languages {
  /** The Windows display languages in the period. */
  os: LanguageGroup[];
  /** The app languages in the period. */
  app: LanguageGroup[];
  /** The Windows display languages of the sessions with the app in English: the users that a translation can help. */
  osOfEnglish: LanguageGroup[];
  /** The groups of each of the 12 weeks. */
  weeklyOs: { date: string; groups: LanguageGroup[] }[];
  weeklyApp: { date: string; groups: LanguageGroup[] }[];
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
  // Never a session before DATA_START.
  const floor = dataStart(env.DATA_START);
  const after = (time: number) => Math.max(time, floor);
  const from = after(Math.min(period.prevStart, period.weeksStart));
  const start = after(period.start);
  const [dailyRows, versions, osFamilies, shape, counters, attributes, languageRows, retention] = await Promise.all([
    db.prepare(DAILY).bind(dev, from, period.end).all<DailyRow>(),
    db.prepare(VERSIONS).bind(dev, start, period.end).all<AppUsage['versions'][number]>(),
    db.prepare(OS_FAMILIES).bind(dev, start, period.end).all<AppUsage['osFamilies'][number]>(),
    db.prepare(SESSION_SHAPE).bind(dev, start, period.end).first<Record<string, number | null>>(),
    db.prepare(COUNTERS).bind(dev, start, period.end).all<AppUsage['counters'][number]>(),
    db.prepare(ATTRIBUTES).bind(dev, start, period.end).all<AppUsage['attributes'][number]>(),
    db.prepare(LANGUAGES).bind(dev, after(Math.min(period.start, period.weeksStart)), period.end).all<LanguageDayRow>(),
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
    attributes: attributes.results,
    encoders: encoderTable(counters.results),
    languages: languageTables(languageRows.results, period),
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

function languageTables(rows: LanguageDayRow[], period: Period): Languages {
  const inPeriod = rows.filter(row => parseDate(row.day) >= period.start);
  const sum = (list: LanguageDayRow[], key: (row: LanguageDayRow) => string) => {
    const counts = new Map<string, number>();
    for (const row of list) counts.set(key(row), (counts.get(key(row)) ?? 0) + row.sessions);
    return languageGroups(counts);
  };
  const weekly = (key: (row: LanguageDayRow) => string) => weeks(period).map(date =>
    ({ date, groups: sum(rows.filter(row => weekOf(row.day) === date), key) }));
  return {
    os: sum(inPeriod, row => row.os_language),
    app: sum(inPeriod, row => row.language),
    osOfEnglish: sum(inPeriod.filter(row => row.language === 'en'), row => row.os_language),
    weeklyOs: weekly(row => row.os_language),
    weeklyApp: weekly(row => row.language),
  };
}

/**
 * The safeguard of the language data (docs/analytics/legitimate-interest.md): a language with fewer than 5 sessions
 * goes into 'other'. Then a group with fewer than 5 sessions ('other' or 'unknown') is not shown. Thus no shown group
 * has fewer than 5 sessions, and no rare language shows with its name.
 */
export function languageGroups(counts: ReadonlyMap<string, number>): LanguageGroup[] {
  const merged = new Map<string, number>();
  for (const [value, sessions] of counts) {
    const name = sessions < MIN_LANGUAGE_SESSIONS && value !== UNKNOWN_LANGUAGE ? OTHER_LANGUAGE : value;
    merged.set(name, (merged.get(name) ?? 0) + sessions);
  }
  return [...merged]
    .filter(([, sessions]) => sessions >= MIN_LANGUAGE_SESSIONS)
    .map(([value, sessions]) => ({ value, sessions }))
    .sort((a, b) => b.sessions - a.sessions || a.value.localeCompare(b.value));
}

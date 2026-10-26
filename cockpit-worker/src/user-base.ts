import { FILTER } from './app-usage';
import type { Env } from './env';
import { DAY, type Period, dates, isoDate, startOfDay } from './period';

export const APP_DAYS_JOB = 'app_daily';

/**
 * Each run writes the totals of the complete days again for the last 60 days. A session row never moves to another
 * day (`first_received_at` does not change), and the analytics Worker deletes no row of these days yet (90 days after
 * the last summary). An older day is written only when it has no row yet: then the deletions cannot make its totals
 * smaller.
 */
export const REWRITE_DAYS = 60;

/**
 * The analytics data has no install ID (docs/analytics/design.md). Each install sends exactly one session with
 * `session_n_1`, so the sum of these sessions is the number of installs that started the app. The other buckets
 * count sessions, not installs.
 */
export interface DayTotals { sessions: number; session_n_1: number; session_n_2_5: number; session_n_6_20: number; session_n_21p: number }

interface DayRow extends DayTotals { day: string }

const BUCKETS = `COUNT(*) AS sessions,
  SUM(json_extract(counters, '$.session_n_1') IS NOT NULL) AS session_n_1,
  SUM(json_extract(counters, '$.session_n_2_5') IS NOT NULL) AS session_n_2_5,
  SUM(json_extract(counters, '$.session_n_6_20') IS NOT NULL) AS session_n_6_20,
  SUM(json_extract(counters, '$.session_n_21p') IS NOT NULL) AS session_n_21p`;

const LIVE_DAYS = `SELECT date(first_received_at / 1000, 'unixepoch') AS day, ${BUCKETS}
FROM analytics_session WHERE ${FILTER} AND first_received_at >= ? AND first_received_at < ? GROUP BY day ORDER BY day`;

const STORED_DAYS = `SELECT day, sessions, session_n_1, session_n_2_5, session_n_6_20, session_n_21p
FROM app_daily WHERE dev = ? ORDER BY day`;

const COLUMNS = 'day,dev,sessions,session_n_1,session_n_2_5,session_n_6_20,session_n_21p,taken_at';
const VALUES = 'VALUES (?,?,?,?,?,?,?,?)';
const REWRITE = `INSERT INTO app_daily (${COLUMNS}) ${VALUES} ON CONFLICT(day,dev) DO UPDATE SET sessions=excluded.sessions, `
  + 'session_n_1=excluded.session_n_1, session_n_2_5=excluded.session_n_2_5, session_n_6_20=excluded.session_n_6_20, '
  + 'session_n_21p=excluded.session_n_21p, taken_at=excluded.taken_at';
const KEEP = `INSERT INTO app_daily (${COLUMNS}) ${VALUES} ON CONFLICT(day,dev) DO NOTHING`;

const ZERO: DayTotals = { sessions: 0, session_n_1: 0, session_n_2_5: 0, session_n_6_20: 0, session_n_21p: 0 };

/** Copies the totals of each complete UTC day from the analytics database. Returns the number of days. */
export async function saveAppDays(env: Env, now: number): Promise<number> {
  try {
    const today = startOfDay(now);
    const rewriteFrom = isoDate(today - REWRITE_DAYS * DAY);
    const statements: D1PreparedStatement[] = [];
    for (const dev of [0, 1]) {
      const { results } = await env.ANALYTICS_DB.prepare(LIVE_DAYS).bind(dev, 0, today).all<DayRow>();
      for (const row of results) {
        statements.push(env.COCKPIT_DB.prepare(row.day >= rewriteFrom ? REWRITE : KEEP).bind(
          row.day, dev, row.sessions, row.session_n_1, row.session_n_2_5, row.session_n_6_20, row.session_n_21p, now));
      }
    }
    if (statements.length) await env.COCKPIT_DB.batch(statements);
    await writeStatus(env, now, true, `${statements.length} day rows`);
    return statements.length;
  } catch (error) {
    await writeStatus(env, now, false, error instanceof Error ? error.message : String(error));
    throw error;
  }
}

async function writeStatus(env: Env, now: number, ok: boolean, message: string) {
  await env.COCKPIT_DB.prepare('INSERT INTO job_status (name,ran_at,ok,message) VALUES (?,?,?,?) '
    + 'ON CONFLICT(name) DO UPDATE SET ran_at=excluded.ran_at, ok=excluded.ok, message=excluded.message')
    .bind(APP_DAYS_JOB, now, ok ? 1 : 0, message.slice(0, 500)).run();
}

export interface UserBase {
  /** The first day with sessions, or null. */
  since: string | null;
  /** The last day in the cockpit database. The days after it come from the analytics database. */
  savedThrough: string | null;
  lastRun: { ranAt: number; ok: boolean; message: string } | null;
  /** From the first day to the end of the period. */
  total: DayTotals;
  /** The installs that started the app before the period. */
  installsBefore: number;
  /** For each day of the period: the new installs and the installs up to the end of that day. */
  daily: { date: string; newInstalls: number; installs: number }[];
}

/**
 * The all-time totals up to the end of the period: the saved days, and the days after the last saved day from the
 * analytics database. Thus the numbers are correct also when the cron did not run yet.
 */
export async function loadUserBase(env: Env, period: Period, includeDev: boolean): Promise<UserBase> {
  const dev = includeDev ? 1 : 0;
  const [stored, status] = await Promise.all([
    env.COCKPIT_DB.prepare(STORED_DAYS).bind(dev).all<DayRow>(),
    env.COCKPIT_DB.prepare('SELECT ran_at, ok, message FROM job_status WHERE name = ?').bind(APP_DAYS_JOB)
      .first<{ ran_at: number; ok: number; message: string }>(),
  ]);
  const savedThrough = stored.results.length ? stored.results[stored.results.length - 1].day : null;
  const liveFrom = savedThrough ? Date.parse(`${savedThrough}T00:00:00Z`) + DAY : 0;
  const live = liveFrom < period.end
    ? (await env.ANALYTICS_DB.prepare(LIVE_DAYS).bind(dev, liveFrom, period.end).all<DayRow>()).results
    : [];

  const endDate = isoDate(period.end);
  const startDate = isoDate(period.start);
  const rows = [...stored.results, ...live].filter(row => row.day < endDate);
  const total = { ...ZERO };
  let installsBefore = 0;
  const newByDay = new Map<string, number>();
  for (const row of rows) {
    for (const key of Object.keys(ZERO) as (keyof DayTotals)[]) total[key] += row[key] ?? 0;
    if (row.day < startDate) installsBefore += row.session_n_1 ?? 0;
    else newByDay.set(row.day, row.session_n_1 ?? 0);
  }
  let installs = installsBefore;
  const daily = dates(period.start, period.end).map(date => {
    const newInstalls = newByDay.get(date) ?? 0;
    installs += newInstalls;
    return { date, newInstalls, installs };
  });

  return {
    since: rows.find(row => row.sessions > 0)?.day ?? null,
    savedThrough,
    lastRun: status ? { ranAt: status.ran_at, ok: status.ok === 1, message: status.message } : null,
    total,
    installsBefore,
    daily,
  };
}

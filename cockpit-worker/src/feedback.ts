import type { Env } from './env';
import { type Period, dates } from './period';

const TOPICS = ['problem', 'idea', 'question', 'other'] as const;
const FILTER = "app_version <> 'synthetic-smoke' AND (? = 1 OR app_version NOT LIKE '%-SNAPSHOT')";
const MESSAGE_LIMIT = 600;
const REPORT_LIMIT = 50;

/** Decision 9: no email address, only "email given". */
const REPORTS = `SELECT report_id, received_at, topic, substr(message, 1, ${MESSAGE_LIMIT + 1}) AS message,
  email IS NOT NULL AS has_email, app_version, os_name, os_version, error, has_log, delivered
FROM feedback_report WHERE ${FILTER} AND received_at >= ? AND received_at < ?
ORDER BY received_at DESC LIMIT ${REPORT_LIMIT}`;

const DAILY = `SELECT date(received_at / 1000, 'unixepoch') AS day, topic, COUNT(*) AS reports
FROM feedback_report WHERE ${FILTER} AND received_at >= ? AND received_at < ? GROUP BY day, topic`;

const ERRORS = `SELECT error FROM feedback_report WHERE ${FILTER} AND received_at >= ? AND received_at < ? AND error IS NOT NULL`;

interface ReportRow {
  report_id: string; received_at: number; topic: string; message: string; has_email: number; app_version: string;
  os_name: string; os_version: string; error: string | null; has_log: number; delivered: number;
}

export interface Feedback {
  total: number;
  prevTotal: number;
  undelivered: number;
  byTopic: Record<string, number>;
  daily: ({ date: string } & Record<string, number | string>)[];
  topErrors: { error: string; reports: number }[];
  reports: {
    id: string; receivedAt: number; topic: string; message: string; truncated: boolean; hasEmail: boolean;
    version: string; os: string; errorLine: string | null; hasLog: boolean; delivered: boolean;
  }[];
}

/** The first line of an error text: the title of the error message in the app. */
export function errorLine(error: string): string {
  return error.split('\n').map(line => line.trim()).find(line => line.length > 0)?.slice(0, 200) ?? '';
}

export async function loadFeedback(env: Env, period: Period, includeDev: boolean): Promise<Feedback> {
  const db = env.FEEDBACK_DB;
  const dev = includeDev ? 1 : 0;
  const [reports, daily, prev, errors, undelivered] = await Promise.all([
    db.prepare(REPORTS).bind(dev, period.start, period.end).all<ReportRow>(),
    db.prepare(DAILY).bind(dev, period.start, period.end).all<{ day: string; topic: string; reports: number }>(),
    db.prepare(`SELECT COUNT(*) AS reports FROM feedback_report WHERE ${FILTER} AND received_at >= ? AND received_at < ?`)
      .bind(dev, period.prevStart, period.start).first<{ reports: number }>(),
    db.prepare(ERRORS).bind(dev, period.start, period.end).all<{ error: string }>(),
    db.prepare(`SELECT COUNT(*) AS reports FROM feedback_report WHERE ${FILTER} AND received_at >= ? AND received_at < ? AND delivered = 0`)
      .bind(dev, period.start, period.end).first<{ reports: number }>(),
  ]);

  const byTopic: Record<string, number> = Object.fromEntries(TOPICS.map(topic => [topic, 0]));
  const byDay = new Map<string, Record<string, number>>();
  for (const row of daily.results) {
    byTopic[row.topic] = (byTopic[row.topic] ?? 0) + row.reports;
    const day = byDay.get(row.day) ?? {};
    day[row.topic] = row.reports;
    byDay.set(row.day, day);
  }

  const errorCounts = new Map<string, number>();
  for (const row of errors.results) {
    const line = errorLine(row.error);
    errorCounts.set(line, (errorCounts.get(line) ?? 0) + 1);
  }

  return {
    total: Object.values(byTopic).reduce((sum, value) => sum + value, 0),
    prevTotal: prev?.reports ?? 0,
    undelivered: undelivered?.reports ?? 0,
    byTopic,
    daily: dates(period.start, period.end).map(date => ({ date, ...Object.fromEntries(TOPICS.map(topic => [topic, byDay.get(date)?.[topic] ?? 0])) })),
    topErrors: [...errorCounts].map(([error, count]) => ({ error, reports: count })).sort((a, b) => b.reports - a.reports).slice(0, 10),
    reports: reports.results.map(row => ({
      id: row.report_id,
      receivedAt: row.received_at,
      topic: row.topic,
      message: row.message.slice(0, MESSAGE_LIMIT),
      truncated: row.message.length > MESSAGE_LIMIT,
      hasEmail: row.has_email === 1,
      version: row.app_version,
      os: `${row.os_name} ${row.os_version}`.trim(),
      errorLine: row.error ? errorLine(row.error) : null,
      hasLog: row.has_log === 1,
      delivered: row.delivered === 1,
    })),
  };
}

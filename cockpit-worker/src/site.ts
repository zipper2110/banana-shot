import type { Env } from './env';
import { type Period, dates, isoDate } from './period';

const TOP_LIMIT = 10;

/** The page views of the website. The analytics Worker counts them in `site_daily` (site/README.md). */
export interface Site {
  /** The first day with a page view. Null before the first page view. */
  countingSince: string | null;
  totals: { visits: number; pageViews: number };
  prevTotals: { visits: number; pageViews: number };
  daily: { date: string; visits: number; pageViews: number }[];
  pages: Ranked[];
  /** Only the referrers that sent a visit. An empty name is a direct open. */
  referrers: Ranked[];
  /** The country code of Cloudflare. `XX` is an unknown country. */
  countries: Ranked[];
  devices: Ranked[];
}

export interface Ranked { name: string; visits: number; pageViews: number }

interface Row { name: string; visits: number; views: number }

const RANGE = 'FROM site_daily WHERE date >= ? AND date < ?';
const top = (column: string, order: 'views' | 'visits', where = '') =>
  `SELECT ${column} AS name, SUM(visits) AS visits, SUM(views) AS views ${RANGE}${where} GROUP BY ${column} ORDER BY ${order} DESC, name LIMIT ${TOP_LIMIT}`;

export async function loadSite(env: Env, period: Period): Promise<Site> {
  const db = env.ANALYTICS_DB;
  const start = isoDate(period.start);
  const end = isoDate(period.end);
  const [daily, prev, pages, referrers, countries, devices, first] = await Promise.all([
    db.prepare(`SELECT date AS name, SUM(visits) AS visits, SUM(views) AS views ${RANGE} GROUP BY date`).bind(start, end).all<Row>(),
    db.prepare(`SELECT COALESCE(SUM(visits), 0) AS visits, COALESCE(SUM(views), 0) AS views ${RANGE}`).bind(isoDate(period.prevStart), start).first<Row>(),
    db.prepare(top('path', 'views')).bind(start, end).all<Row>(),
    db.prepare(top('referrer', 'visits', ' AND visits > 0')).bind(start, end).all<Row>(),
    db.prepare(top('country', 'visits')).bind(start, end).all<Row>(),
    db.prepare(top('device', 'visits')).bind(start, end).all<Row>(),
    db.prepare('SELECT MIN(date) AS name FROM site_daily').first<{ name: string | null }>(),
  ]);

  const byDate = new Map(daily.results.map(row => [row.name, row]));
  const days = dates(period.start, period.end).map(date => ({ date, visits: byDate.get(date)?.visits ?? 0, pageViews: byDate.get(date)?.views ?? 0 }));
  const ranked = (rows: Row[]) => rows.map(row => ({ name: row.name, visits: row.visits, pageViews: row.views }));
  return {
    countingSince: first?.name ?? null,
    totals: {
      visits: days.reduce((total, day) => total + day.visits, 0),
      pageViews: days.reduce((total, day) => total + day.pageViews, 0),
    },
    prevTotals: { visits: prev?.visits ?? 0, pageViews: prev?.views ?? 0 },
    daily: days,
    pages: ranked(pages.results),
    referrers: ranked(referrers.results),
    countries: ranked(countries.results),
    devices: ranked(devices.results),
  };
}

import type { Env } from './env';
import { type Period, dates, isoDate, parseDate, weekOf, weeks } from './period';

export const SETUP_ASSET = 'BananaShot-win-Setup.exe';
export const SNAPSHOT_JOB = 'download_snapshot';

interface GithubRelease {
  tag_name: string;
  draft: boolean;
  published_at: string | null;
  assets: { name: string; download_count: number }[];
}

interface SnapshotRow {
  day: string;
  release_tag: string;
  asset_name: string;
  download_count: number;
  published_at: string | null;
}

/** Only the releases of the app (decision 4): no drafts, and no native dependency releases. */
export function isAppRelease(release: GithubRelease): boolean {
  return !release.draft && !release.tag_name.startsWith('natives-');
}

/** Reads the totals from GitHub and writes them for the current UTC day. Returns the number of rows. */
export async function takeSnapshot(env: Env, now: number, fetcher: typeof fetch = fetch): Promise<number> {
  try {
    const releases = await fetchReleases(env, fetcher);
    const day = isoDate(now);
    const statements = releases.filter(isAppRelease).flatMap(release => release.assets.map(asset =>
      env.COCKPIT_DB.prepare('INSERT INTO download_snapshot (day,release_tag,asset_name,download_count,published_at,taken_at) VALUES (?,?,?,?,?,?) '
        + 'ON CONFLICT(day,release_tag,asset_name) DO UPDATE SET download_count=excluded.download_count, published_at=excluded.published_at, taken_at=excluded.taken_at')
        .bind(day, release.tag_name, asset.name, asset.download_count, release.published_at, now)));
    if (statements.length) await env.COCKPIT_DB.batch(statements);
    await writeStatus(env, now, true, `${statements.length} release files`);
    return statements.length;
  } catch (error) {
    await writeStatus(env, now, false, error instanceof Error ? error.message : String(error));
    throw error;
  }
}

async function fetchReleases(env: Env, fetcher: typeof fetch): Promise<GithubRelease[]> {
  const headers: Record<string, string> = {
    accept: 'application/vnd.github+json',
    'user-agent': 'bananashot-cockpit',
    'x-github-api-version': '2022-11-28',
  };
  if (env.GITHUB_TOKEN) headers.authorization = `Bearer ${env.GITHUB_TOKEN}`;
  const response = await fetcher(`https://api.github.com/repos/${env.GITHUB_REPO}/releases?per_page=100`, { headers });
  if (!response.ok) throw new Error(`GitHub API returned ${response.status}`);
  return await response.json() as GithubRelease[];
}

async function writeStatus(env: Env, now: number, ok: boolean, message: string) {
  await env.COCKPIT_DB.prepare('INSERT INTO job_status (name,ran_at,ok,message) VALUES (?,?,?,?) '
    + 'ON CONFLICT(name) DO UPDATE SET ran_at=excluded.ran_at, ok=excluded.ok, message=excluded.message')
    .bind(SNAPSHOT_JOB, now, ok ? 1 : 0, message.slice(0, 500)).run();
}

export interface DownloadPoint { date: string; setup: number | null; other: number | null }

export interface Downloads {
  trackingSince: string | null;
  lastSnapshot: { ranAt: number; ok: boolean; message: string } | null;
  total: { setup: number; prevSetup: number; other: number };
  daily: DownloadPoint[];
  weekly: DownloadPoint[];
  releases: { tag: string; publishedAt: string | null; setup: number; other: number }[];
}

/**
 * The downloads of each snapshot day: the increase of each file since the previous snapshot day (decision 3).
 * A file that is new in a snapshot counts from 0. The first snapshot day is the base line and has no value.
 * A day without a snapshot has no value; the next snapshot day gets its downloads.
 */
export function dailyIncreases(rows: SnapshotRow[]): Map<string, { setup: number; other: number }> {
  const byDay = new Map<string, Map<string, SnapshotRow>>();
  for (const row of rows) {
    const day = byDay.get(row.day) ?? new Map<string, SnapshotRow>();
    day.set(`${row.release_tag}\u0000${row.asset_name}`, row);
    byDay.set(row.day, day);
  }
  const result = new Map<string, { setup: number; other: number }>();
  let previous: Map<string, SnapshotRow> | null = null;
  for (const day of [...byDay.keys()].sort()) {
    const current = byDay.get(day)!;
    if (previous) {
      const value = { setup: 0, other: 0 };
      for (const [key, row] of current) {
        const increase = Math.max(0, row.download_count - (previous.get(key)?.download_count ?? 0));
        if (row.asset_name === SETUP_ASSET) value.setup += increase;
        else value.other += increase;
      }
      result.set(day, value);
    }
    previous = current;
  }
  return result;
}

export async function loadDownloads(env: Env, period: Period): Promise<Downloads> {
  const { results: rows } = await env.COCKPIT_DB.prepare(
    'SELECT day, release_tag, asset_name, download_count, published_at FROM download_snapshot ORDER BY day').all<SnapshotRow>();
  const status = await env.COCKPIT_DB.prepare('SELECT ran_at, ok, message FROM job_status WHERE name = ?')
    .bind(SNAPSHOT_JOB).first<{ ran_at: number; ok: number; message: string }>();
  const increases = dailyIncreases(rows);

  const daily = dates(period.start, period.end).map(date => point(date, increases.get(date)));
  const weekly = weeks(period).map(week => ({ date: week, setup: null as number | null, other: null as number | null }));
  const weekIndex = new Map(weekly.map((item, index) => [item.date, index]));
  for (const [date, value] of increases) {
    const item = weekly[weekIndex.get(weekOf(date)) ?? -1];
    if (!item) continue;
    item.setup = (item.setup ?? 0) + value.setup;
    item.other = (item.other ?? 0) + value.other;
  }

  let setup = 0, prevSetup = 0, other = 0;
  for (const [date, value] of increases) {
    const time = parseDate(date);
    if (time >= period.start && time < period.end) { setup += value.setup; other += value.other; }
    else if (time >= period.prevStart && time < period.start) prevSetup += value.setup;
  }

  const lastDay = rows.length ? rows[rows.length - 1].day : null;
  const releases = new Map<string, Downloads['releases'][number]>();
  for (const row of rows.filter(row => row.day === lastDay)) {
    const release = releases.get(row.release_tag) ?? { tag: row.release_tag, publishedAt: row.published_at, setup: 0, other: 0 };
    if (row.asset_name === SETUP_ASSET) release.setup += row.download_count;
    else release.other += row.download_count;
    releases.set(row.release_tag, release);
  }

  return {
    trackingSince: rows.length ? rows[0].day : null,
    lastSnapshot: status ? { ranAt: status.ran_at, ok: status.ok === 1, message: status.message } : null,
    total: { setup, prevSetup, other },
    daily,
    weekly,
    releases: [...releases.values()].sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? '')),
  };
}

function point(date: string, value: { setup: number; other: number } | undefined): DownloadPoint {
  return { date, setup: value?.setup ?? null, other: value?.other ?? null };
}

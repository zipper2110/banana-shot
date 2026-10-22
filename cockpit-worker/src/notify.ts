import { SETUP_ASSET } from './downloads';
import type { Env } from './env';
import { DAY, isoDate, parseDate } from './period';

/**
 * The hourly Telegram digest: new site visits, new downloads, and new installs since the last run. The sources keep
 * only totals, so the digest compares the current totals with the totals of the last run (table `notify_seen`).
 */
export const DIGEST_JOB = 'telegram_digest';

/** Telegram refuses a message text that is longer than 4,096 characters. */
const TEXT_LIMIT = 4096;
/** The largest number of lines for each section. The other lines become one "and N more" line. */
const LINE_LIMIT = 10;

type Source = 'visit' | 'download' | 'install';

/**
 * One total of a source. `key` is a JSON array (SQLite cuts a text at a NUL character, so no NUL separator). `day` is
 * the UTC day of the total, so that old rows can go. Downloads have no day.
 */
interface Total { key: string; day: string; count: number; label: string }

const TITLES: Record<Source, string> = { visit: 'Visits', download: 'Downloads', install: 'New installs' };
const SOURCES: Source[] = ['visit', 'download', 'install'];

const DEV_FILTER = "app_version <> 'synthetic-smoke' AND app_version NOT LIKE '%-SNAPSHOT'";

/** The visits of yesterday and today (UTC). A visit is a page view that came from another site or from a direct open. */
async function loadVisits(env: Env, since: string): Promise<Total[]> {
  const { results } = await env.ANALYTICS_DB.prepare(
    'SELECT date, path, referrer, country, device, visits FROM site_daily WHERE date >= ? AND visits > 0')
    .bind(since).all<{ date: string; path: string; referrer: string; country: string; device: string; visits: number }>();
  return results.map(row => ({
    key: JSON.stringify([row.date, row.path, row.referrer, row.country, row.device]),
    day: row.date,
    count: row.visits,
    label: `${row.path} · ${row.referrer || 'direct'} · ${row.country} · ${row.device}`,
  }));
}

/**
 * The first sessions of the app since yesterday (UTC). A first session is about one new install that started the
 * app (analytics-worker/queries/usage.sql). The receive time is rounded down to the hour.
 */
async function loadInstalls(env: Env, since: string): Promise<Total[]> {
  const { results } = await env.ANALYTICS_DB.prepare(
    `SELECT first_received_at AS hour, app_version, os_family, COUNT(*) AS sessions FROM analytics_session
     WHERE first_received_at >= ? AND json_extract(counters, '$.session_n_1') IS NOT NULL AND ${DEV_FILTER}
     GROUP BY first_received_at, app_version, os_family`)
    .bind(parseDate(since)).all<{ hour: number; app_version: string; os_family: string; sessions: number }>();
  return results.map(row => ({
    key: JSON.stringify([row.hour, row.app_version, row.os_family]),
    day: isoDate(row.hour),
    count: row.sessions,
    label: `${row.app_version} · ${row.os_family}`,
  }));
}

/**
 * The download totals of the last snapshot: the installer and the update packages. The other files (for example the
 * release feed `releases.win.json`) are not downloads of a person.
 */
async function loadDownloads(env: Env): Promise<Total[]> {
  const { results } = await env.COCKPIT_DB.prepare(
    'SELECT release_tag, asset_name, download_count FROM download_snapshot WHERE day = (SELECT MAX(day) FROM download_snapshot)')
    .all<{ release_tag: string; asset_name: string; download_count: number }>();
  return results.flatMap(row => {
    const kind = row.asset_name === SETUP_ASSET ? 'installer' : row.asset_name.endsWith('.nupkg') ? 'update package' : null;
    if (!kind) return [];
    return [{ key: JSON.stringify([row.release_tag, row.asset_name]), day: '', count: row.download_count, label: `${kind} ${row.release_tag}` }];
  });
}

export interface DigestResult {
  /** False when the Telegram secrets are not set. Then the digest does nothing. */
  configured: boolean;
  /** True when the run only kept the current totals as the base line. */
  baseline: boolean;
  /** The new items of each source. */
  counts: Record<Source, number>;
  /** The text that went to Telegram, or null when there was nothing new. */
  text: string | null;
}

/**
 * Sends the new visits, downloads, and installs since the last run to Telegram. The first run only keeps the base
 * line. A source that fails stays at its old totals, so its new items go in a later message. When Telegram does not
 * accept the message, no total changes, and the next run sends the items again.
 */
export async function sendDigest(env: Env, now: number, fetcher: typeof fetch = fetch): Promise<DigestResult> {
  const counts: Record<Source, number> = { visit: 0, download: 0, install: 0 };
  if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) return { configured: false, baseline: false, counts, text: null };
  const since = isoDate(now - DAY);
  const firstRun = !await env.COCKPIT_DB.prepare('SELECT 1 FROM job_status WHERE name = ?').bind(DIGEST_JOB).first();

  const loaded = new Map<Source, Total[]>();
  const failures: string[] = [];
  const loaders: Record<Source, () => Promise<Total[]>> = {
    visit: () => loadVisits(env, since),
    download: () => loadDownloads(env),
    install: () => loadInstalls(env, since),
  };
  for (const source of SOURCES) {
    try {
      loaded.set(source, await loaders[source]());
    } catch (error) {
      failures.push(`${source}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  const { results: seenRows } = await env.COCKPIT_DB.prepare('SELECT source, key, count FROM notify_seen')
    .all<{ source: Source; key: string; count: number }>();
  const seen = new Map(seenRows.map(row => [`${row.source}\u0000${row.key}`, row.count]));
  // A new release counts from 0. But without any download row, all download totals would be new: keep a base line.
  const noDownloadBase = !seenRows.some(row => row.source === 'download');

  const sections: string[] = [];
  for (const [source, totals] of loaded) {
    if (firstRun || (source === 'download' && noDownloadBase)) continue;
    const groups = new Map<string, number>();
    for (const total of totals) {
      const increase = total.count - (seen.get(`${source}\u0000${total.key}`) ?? 0);
      if (increase <= 0) continue;
      counts[source] += increase;
      groups.set(total.label, (groups.get(total.label) ?? 0) + increase);
    }
    if (counts[source] > 0) sections.push(section(TITLES[source], counts[source], groups));
  }

  const text = sections.length ? fitText(`BananaShot: new activity\n\n${sections.join('\n\n')}`) : null;
  if (text) {
    try {
      await sendMessage(env.TELEGRAM_BOT_TOKEN, env.TELEGRAM_CHAT_ID, text, fetcher);
    } catch (error) {
      await writeStatus(env, now, false, error instanceof Error ? error.message : String(error));
      throw error;
    }
  }

  const statements = [...loaded].flatMap(([source, totals]) => totals.map(total =>
    env.COCKPIT_DB.prepare('INSERT INTO notify_seen (source,key,day,count) VALUES (?,?,?,?) '
      + 'ON CONFLICT(source,key) DO UPDATE SET day=excluded.day, count=excluded.count')
      .bind(source, total.key, total.day, total.count)));
  statements.push(env.COCKPIT_DB.prepare("DELETE FROM notify_seen WHERE source IN ('visit','install') AND day < ?").bind(since));
  await env.COCKPIT_DB.batch(statements);

  const summary = firstRun ? 'base line' : `${counts.visit} visits, ${counts.download} downloads, ${counts.install} installs`;
  await writeStatus(env, now, failures.length === 0, failures.length ? `${summary}; failed: ${failures.join('; ')}` : summary);
  if (failures.length) console.error(`Digest sources failed: ${failures.join('; ')}`);
  return { configured: true, baseline: firstRun, counts, text };
}

/** One section of the message: the title with the total, then the largest groups. */
function section(title: string, total: number, groups: Map<string, number>): string {
  const sorted = [...groups].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  const lines = sorted.slice(0, LINE_LIMIT).map(([label, count]) => `${count} × ${label}`);
  const rest = sorted.slice(LINE_LIMIT).reduce((sum, [, count]) => sum + count, 0);
  if (rest > 0) lines.push(`${rest} more`);
  return [`${title}: ${total}`, ...lines].join('\n');
}

function fitText(text: string): string {
  return text.length <= TEXT_LIMIT ? text : `${text.slice(0, TEXT_LIMIT - 1)}…`;
}

async function sendMessage(token: string, chatId: string, text: string, fetcher: typeof fetch): Promise<void> {
  const response = await fetcher(`https://api.telegram.org/bot${token}/sendMessage`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ chat_id: chatId, text, disable_web_page_preview: true }),
  });
  const result = await response.json().catch(() => null) as { ok?: boolean } | null;
  // The error must not contain the URL: the URL contains the bot token.
  if (!response.ok || result?.ok !== true) throw new Error(`Telegram sendMessage failed with status ${response.status}`);
}

async function writeStatus(env: Env, now: number, ok: boolean, message: string) {
  await env.COCKPIT_DB.prepare('INSERT INTO job_status (name,ran_at,ok,message) VALUES (?,?,?,?) '
    + 'ON CONFLICT(name) DO UPDATE SET ran_at=excluded.ran_at, ok=excluded.ok, message=excluded.message')
    .bind(DIGEST_JOB, now, ok ? 1 : 0, message.slice(0, 500)).run();
}

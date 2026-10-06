import { type Env, NotConfigured } from './env';
import { DAY, type Period, dates } from './period';

const ENDPOINT = 'https://api.cloudflare.com/client/v4/graphql';
const FALLBACK_DAYS = 30;
const SAFE_TAG = /^[A-Za-z0-9_-]+$/;
const SAFE_HOST = /^[a-z0-9.-]+$/;
const SAFE_SCRIPT = /^[a-z0-9_-]+$/;

export interface Site {
  /** Set when Cloudflare refused the long range and the numbers cover fewer days (Q1). */
  clampedToDays: number | null;
  totals: { visits: number; pageViews: number };
  prevTotals: { visits: number; pageViews: number } | null;
  daily: { date: string; visits: number; pageViews: number }[];
  pages: Ranked[];
  referrers: Ranked[];
  countries: Ranked[];
  devices: Ranked[];
}

export interface Ranked { name: string; visits: number; pageViews: number }

export interface WorkerHealth {
  clampedToDays: number | null;
  scripts: { name: string; requests: number; errors: number }[];
  daily: { date: string; requests: number; errors: number }[];
}

interface Group { count: number; sum: { visits?: number; requests?: number; errors?: number }; dimensions?: Record<string, string> }

type Fetcher = typeof fetch;

function config(env: Env) {
  if (!env.CF_API_TOKEN || !env.CF_ACCOUNT_ID) throw new NotConfigured('Not configured: set CF_API_TOKEN and CF_ACCOUNT_ID (README).');
  if (!SAFE_TAG.test(env.CF_ACCOUNT_ID)) throw new Error('CF_ACCOUNT_ID has an invalid format.');
  // A pasted secret often ends with a line break. The header must not contain it.
  const token = env.CF_API_TOKEN.trim();
  if (!/^[A-Za-z0-9_-]{20,}$/.test(token)) {
    throw new Error(`CF_API_TOKEN has an invalid format (${token.length} characters). Set it again with "npx wrangler secret put CF_API_TOKEN" (README).`);
  }
  return { token, account: env.CF_ACCOUNT_ID };
}

/** Sends the request. A network error gets one more try, and its message tells after how many seconds it failed. */
async function send(token: string, text: string, fetcher: Fetcher): Promise<Response> {
  const init = {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json', 'user-agent': 'bananashot-cockpit' },
    body: JSON.stringify({ query: text }),
  };
  for (let attempt = 1; ; attempt++) {
    const started = Date.now();
    try {
      return await fetcher(ENDPOINT, init);
    } catch (error) {
      const message = `${error instanceof Error ? error.message : String(error)} (after ${((Date.now() - started) / 1000).toFixed(1)} s, try ${attempt})`;
      console.error(`Cloudflare API: ${message}`);
      if (attempt >= 2) throw new Error(`Cloudflare API: ${message}`);
    }
  }
}

async function query(env: Env, text: string, fetcher: Fetcher): Promise<Record<string, Group[]>> {
  const { token } = config(env);
  const response = await send(token, text, fetcher);
  if (!response.ok) {
    const text = await response.text().catch(() => '');
    let answer: { errors?: { message: string }[] } | null = null;
    try { answer = JSON.parse(text); } catch { /* Not JSON: the start of the text goes to the log. */ }
    const detail = answer?.errors?.map(error => error.message).join('; ');
    console.error(`Cloudflare API ${response.status}: ${text.slice(0, 300)}`);
    throw new Error(`Cloudflare API returned ${response.status}${detail ? `: ${detail}` : ''}`);
  }
  const body = await response.json() as { data?: { viewer?: { accounts?: Record<string, Group[]>[] } }; errors?: { message: string }[] | null };
  if (body.errors?.length) throw new Error(`Cloudflare API: ${body.errors.map(error => error.message).join('; ')}`);
  const account = body.data?.viewer?.accounts?.[0];
  if (!account) throw new Error('Cloudflare API: no account in the answer. Check CF_ACCOUNT_ID and the token.');
  return account;
}

/** Runs the query for the full period. If Cloudflare refuses the range, runs it again for the last 30 days. */
async function withFallback<T>(period: Period, load: (start: number, prevStart: number | null) => Promise<T>): Promise<T & { clampedToDays: number | null }> {
  try {
    return { ...await load(period.start, period.prevStart), clampedToDays: null };
  } catch (error) {
    if (period.days <= FALLBACK_DAYS || error instanceof NotConfigured) throw error;
    const start = period.end - FALLBACK_DAYS * DAY;
    return { ...await load(start, null), clampedToDays: FALLBACK_DAYS };
  }
}

function range(start: number, end: number): string {
  return `datetime_geq: "${new Date(start).toISOString()}", datetime_lt: "${new Date(end).toISOString()}"`;
}

function ranked(groups: Group[] | undefined, dimension: string): Ranked[] {
  return (groups ?? []).map(group => ({
    name: group.dimensions?.[dimension] || '(none)',
    visits: group.sum.visits ?? 0,
    pageViews: group.count,
  }));
}

/** The site tag when it is set. Otherwise the host of the site: the account has only this one site. */
function siteFilter(env: Env): string {
  if (env.CF_WEB_ANALYTICS_SITE_TAG) {
    if (!SAFE_TAG.test(env.CF_WEB_ANALYTICS_SITE_TAG)) throw new Error('CF_WEB_ANALYTICS_SITE_TAG has an invalid format.');
    return `{ siteTag: "${env.CF_WEB_ANALYTICS_SITE_TAG}" }`;
  }
  const host = env.SITE_HOST || 'banana-shot-editor.app';
  if (!SAFE_HOST.test(host)) throw new Error('SITE_HOST has an invalid format.');
  return `{ requestHost: "${host}" }`;
}

export async function loadSite(env: Env, period: Period, fetcher: Fetcher = fetch): Promise<Site> {
  const { account } = config(env);
  const site = siteFilter(env);

  return withFallback(period, async (start, prevStart) => {
    const filter = `{ AND: [{ ${range(start, period.end)} }, ${site}] }`;
    const top = (alias: string, dimension: string) =>
      `${alias}: rumPageloadEventsAdaptiveGroups(filter: ${filter}, limit: 10, orderBy: [count_DESC]) { count sum { visits } dimensions { ${dimension} } }`;
    const prev = prevStart === null ? '' : `prev: rumPageloadEventsAdaptiveGroups(filter: { AND: [{ ${range(prevStart, start)} }, ${site}] }, limit: 1) { count sum { visits } }`;
    const account_ = await query(env, `{ viewer { accounts(filter: { accountTag: "${account}" }) {
      daily: rumPageloadEventsAdaptiveGroups(filter: ${filter}, limit: 100, orderBy: [date_ASC]) { count sum { visits } dimensions { date } }
      ${top('pages', 'requestPath')}
      ${top('referrers', 'refererHost')}
      ${top('countries', 'countryName')}
      ${top('devices', 'deviceType')}
      ${prev}
    } } }`, fetcher);

    const byDate = new Map((account_.daily ?? []).map(group => [group.dimensions?.date ?? '', group]));
    const daily = dates(period.start, period.end).map(date => {
      const group = byDate.get(date);
      return { date, visits: group?.sum.visits ?? 0, pageViews: group?.count ?? 0 };
    });
    const sum = (groups: Group[] | undefined) => ({
      visits: (groups ?? []).reduce((total, group) => total + (group.sum.visits ?? 0), 0),
      pageViews: (groups ?? []).reduce((total, group) => total + group.count, 0),
    });
    return {
      totals: sum(account_.daily),
      prevTotals: prevStart === null ? null : sum(account_.prev),
      daily,
      pages: ranked(account_.pages, 'requestPath'),
      referrers: ranked(account_.referrers, 'refererHost'),
      countries: ranked(account_.countries, 'countryName'),
      devices: ranked(account_.devices, 'deviceType'),
    };
  });
}

export async function loadWorkerHealth(env: Env, period: Period, fetcher: Fetcher = fetch): Promise<WorkerHealth> {
  const { account } = config(env);
  const scripts = (env.WORKER_SCRIPTS ?? '').split(',').map(name => name.trim()).filter(name => SAFE_SCRIPT.test(name));
  if (!scripts.length) throw new NotConfigured('Not configured: set WORKER_SCRIPTS.');

  return withFallback(period, async start => {
    const filter = `{ ${range(start, period.end)}, scriptName_in: [${scripts.map(name => `"${name}"`).join(', ')}] }`;
    const account_ = await query(env, `{ viewer { accounts(filter: { accountTag: "${account}" }) {
      groups: workersInvocationsAdaptive(filter: ${filter}, limit: 10000) { sum { requests errors } dimensions { date scriptName } }
    } } }`, fetcher);

    const byScript = new Map(scripts.map(name => [name, { name, requests: 0, errors: 0 }]));
    const byDate = new Map<string, { requests: number; errors: number }>();
    for (const group of account_.groups ?? []) {
      const script = byScript.get(group.dimensions?.scriptName ?? '');
      const day = byDate.get(group.dimensions?.date ?? '') ?? { requests: 0, errors: 0 };
      day.requests += group.sum.requests ?? 0;
      day.errors += group.sum.errors ?? 0;
      byDate.set(group.dimensions?.date ?? '', day);
      if (!script) continue;
      script.requests += group.sum.requests ?? 0;
      script.errors += group.sum.errors ?? 0;
    }
    return {
      scripts: [...byScript.values()],
      daily: dates(period.start, period.end).map(date => ({ date, ...(byDate.get(date) ?? { requests: 0, errors: 0 }) })),
    };
  });
}

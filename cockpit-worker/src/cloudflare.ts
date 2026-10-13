import { type Env, NotConfigured } from './env';
import { DAY, type Period, dates } from './period';

const ENDPOINT = 'https://api.cloudflare.com/client/v4/graphql';
const FALLBACK_DAYS = 30;
const SAFE_TAG = /^[A-Za-z0-9_-]+$/;
const SAFE_SCRIPT = /^[a-z0-9_-]+$/;

export interface WorkerHealth {
  clampedToDays: number | null;
  scripts: { name: string; requests: number; errors: number }[];
  daily: { date: string; requests: number; errors: number }[];
}

interface Group { sum: { requests?: number; errors?: number }; dimensions?: Record<string, string> }

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

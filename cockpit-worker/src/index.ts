import { loadAppUsage } from './app-usage';
import { isAuthorized } from './auth';
import { loadSite, loadWorkerHealth } from './cloudflare';
import { loadDownloads, takeSnapshot } from './downloads';
import { type Env, section } from './env';
import { loadFeedback } from './feedback';
import { makePeriod, parseDays } from './period';

const SECURITY_HEADERS: Record<string, string> = {
  'cache-control': 'no-store',
  'x-robots-tag': 'noindex, nofollow',
  'x-frame-options': 'DENY',
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'no-referrer',
  'content-security-policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
};

function withHeaders(response: Response, extra: Record<string, string> = {}): Response {
  const result = new Response(response.body, response);
  for (const [name, value] of Object.entries({ ...SECURITY_HEADERS, ...extra })) result.headers.set(name, value);
  return result;
}

function json(body: unknown, status = 200): Response {
  return withHeaders(new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json; charset=utf-8' } }));
}

export async function summary(env: Env, url: URL, now: number, fetcher: typeof fetch = fetch) {
  const period = makePeriod(parseDays(url.searchParams.get('days')), now);
  const includeDev = url.searchParams.get('dev') === '1';
  const [downloads, app, feedback, site, workers] = await Promise.all([
    section(() => loadDownloads(env, period)),
    section(() => loadAppUsage(env, period, includeDev)),
    section(() => loadFeedback(env, period, includeDev)),
    section(() => loadSite(env, period, fetcher)),
    section(() => loadWorkerHealth(env, period, fetcher)),
  ]);
  return {
    generatedAt: now,
    period: { days: period.days, start: period.start, end: period.end, prevStart: period.prevStart, weeksStart: period.weeksStart },
    includeDev,
    downloads, app, feedback, site, workers,
  };
}

/**
 * The local preview needs no password (decision 15). Both conditions must be true: only `.dev.vars` sets the
 * variable, and Cloudflare never routes a request with the host localhost to the deployed Worker.
 */
export function isLocalDevelopment(env: Env, url: URL): boolean {
  return env.COCKPIT_DEV_NO_AUTH === 'true' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (!isLocalDevelopment(env, url)) {
      if (!env.COCKPIT_PASSWORD) {
        return withHeaders(new Response('The cockpit has no password. Set the secret COCKPIT_PASSWORD.', { status: 503 }));
      }
      if (!await isAuthorized(request, env.COCKPIT_PASSWORD)) {
        return withHeaders(new Response('Authentication required.', { status: 401 }), { 'www-authenticate': 'Basic realm="BananaShot cockpit", charset="UTF-8"' });
      }
    }
    if (url.pathname === '/api/summary') {
      if (request.method !== 'GET') return withHeaders(new Response(null, { status: 405 }));
      return json(await summary(env, url, Date.now()));
    }
    if (url.pathname === '/api/snapshot') {
      if (request.method !== 'POST') return withHeaders(new Response(null, { status: 405 }));
      try {
        return json({ files: await takeSnapshot(env, Date.now()) });
      } catch (error) {
        return json({ error: error instanceof Error ? error.message : String(error) }, 502);
      }
    }
    if (request.method !== 'GET' && request.method !== 'HEAD') return withHeaders(new Response(null, { status: 405 }));
    return withHeaders(await env.ASSETS.fetch(request));
  },

  async scheduled(_controller: ScheduledController, env: Env, context: ExecutionContext): Promise<void> {
    context.waitUntil(takeSnapshot(env, Date.now()));
  },
} satisfies ExportedHandler<Env>;

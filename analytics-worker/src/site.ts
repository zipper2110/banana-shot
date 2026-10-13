import { allow } from './rate-limit';

/** The path of the page view counter of the website. */
export const SITE_PATH = '/v1/site';

/** The only origin that can send to [SITE_PATH]. */
export const SITE_ORIGIN = 'https://banana-shot-editor.app';

/** The hosts of the site. A page view with a referrer on one of these hosts is not a visit. */
const SITE_HOSTS = ['banana-shot-editor.app', 'www.banana-shot-editor.app'];

/** The largest body. A page view is about 100 bytes. */
export const MAX_SITE_BODY_BYTES = 1024;

/** A page of the site, for example `/` or `/download/`. The 404 page sends `/404`. Other paths count as `(other)`. */
const PAGE_PATH = /^\/(?:[a-z0-9-]{1,32}\/){0,3}$/;
const NOT_FOUND_PATH = '/404';
const HOST = /^[a-z0-9-]+(?:\.[a-z0-9-]+)*$/;
const COUNTRY = /^[A-Z0-9]{2}$/;
/** Programs that are not a person in a browser. Most of them do not run scripts, but some do. */
const BOT = /bot|crawl|spider|slurp|headless|lighthouse|pagespeed|preview|python|curl|wget/i;

export type Device = 'desktop' | 'mobile' | 'tablet';

export interface PageView {
  date: string;
  path: string;
  /** The host of the page that sent the visitor. Empty for a direct open. */
  referrer: string;
  /** The country code of Cloudflare. `XX` when Cloudflare does not know it. */
  country: string;
  device: Device;
  /** True when the visitor came from another site or opened the page directly. */
  visit: boolean;
}

const UPSERT = 'INSERT INTO site_daily (date,path,referrer,country,device,views,visits) VALUES (?,?,?,?,?,1,?) '
  + 'ON CONFLICT(date,path,referrer,country,device) DO UPDATE SET views = views + 1, visits = visits + excluded.visits';

const status = (code: number) => new Response(null, { status: code, headers: { 'access-control-allow-origin': SITE_ORIGIN } });

/**
 * Counts one page view of the website. The script of the site sends it with `navigator.sendBeacon`. The browser does
 * not read the answer, so each answer has no body.
 */
export async function countPageView(request: Request, db: D1Database, rateLimitKey: string | undefined, now = Date.now()): Promise<Response> {
  if (request.headers.get('origin') !== SITE_ORIGIN) return status(403);
  if (request.method !== 'POST') return status(405);
  if (Number(request.headers.get('content-length') ?? 0) > MAX_SITE_BODY_BYTES) return status(413);
  const bytes = await request.arrayBuffer();
  if (bytes.byteLength > MAX_SITE_BODY_BYTES) return status(413);
  let body: unknown;
  try { body = JSON.parse(new TextDecoder().decode(bytes)); } catch { return status(400); }
  const view = parsePageView(body, request, now);
  if (!view) return status(400);
  // A bot gets the same answer as a browser.
  if (BOT.test(request.headers.get('user-agent') ?? '')) return status(204);
  try {
    if (!rateLimitKey) throw new Error('RATE_LIMIT_KEY is not set');
    if (!await allow(db, rateLimitKey, request.headers.get('cf-connecting-ip') ?? 'unknown', now)) return status(429);
    await db.prepare(UPSERT).bind(view.date, view.path, view.referrer, view.country, view.device, view.visit ? 1 : 0).run();
    return status(204);
  } catch (failure) {
    console.error(`Page view not stored: ${failure instanceof Error ? failure.message : 'unknown failure'}`);
    return status(503);
  }
}

/** Makes the row values from the body `{ path, referrer }` and the request. Returns null for an invalid body. */
export function parsePageView(body: unknown, request: Request, now: number): PageView | null {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) return null;
  const { path, referrer } = body as Record<string, unknown>;
  if (typeof path !== 'string' || typeof referrer !== 'string') return null;
  const host = referrerHost(referrer);
  const country = (request as { cf?: { country?: unknown } }).cf?.country;
  return {
    date: new Date(now).toISOString().slice(0, 10),
    path: PAGE_PATH.test(path) || path === NOT_FOUND_PATH ? path : '(other)',
    referrer: host,
    country: typeof country === 'string' && COUNTRY.test(country) ? country : 'XX',
    device: deviceType(request.headers.get('user-agent') ?? ''),
    visit: !SITE_HOSTS.includes(host),
  };
}

function referrerHost(referrer: string): string {
  if (referrer === '') return '';
  try {
    const host = new URL(referrer).hostname.toLowerCase();
    return host.length <= 100 && HOST.test(host) ? host : '(other)';
  } catch {
    return '(other)';
  }
}

export function deviceType(userAgent: string): Device {
  if (/iPad|Tablet|PlayBook|Silk|Android(?!.*Mobile)/i.test(userAgent)) return 'tablet';
  if (/Mobi|iPhone|iPod|Android|Windows Phone/i.test(userAgent)) return 'mobile';
  return 'desktop';
}

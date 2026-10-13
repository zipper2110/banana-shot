import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import worker, { SiteCounter, type Env } from '../src/index';
import { HOURLY_LIMIT } from '../src/rate-limit';
import { MAX_SITE_BODY_BYTES, SITE_ORIGIN, botName, deviceType } from '../src/site';
import { FakeD1 } from './fake-d1';

const NOW = Date.UTC(2026, 9, 13, 23, 59, 0);
const URL_SITE = 'https://analytics.example.test/v1/site';
const DESKTOP = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36';
const PHONE = 'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36';

let db: FakeD1;
let env: Env;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  db = new FakeD1();
  env = { ANALYTICS_INGESTION_ENABLED: 'true', ANALYTICS_DB: db.asD1(), RATE_LIMIT_KEY: 'test-rate-key' };
});

afterEach(() => {
  vi.useRealTimers();
});

function view(body: unknown, options: { origin?: string | null; userAgent?: string; country?: string; ip?: string; method?: string } = {}): Request {
  const headers = new Headers({ 'content-type': 'text/plain;charset=UTF-8', 'user-agent': options.userAgent ?? DESKTOP, 'cf-connecting-ip': options.ip ?? '203.0.113.7' });
  if (options.origin !== null) headers.set('origin', options.origin ?? SITE_ORIGIN);
  const request = new Request(URL_SITE, { method: options.method ?? 'POST', headers, body: typeof body === 'string' ? body : JSON.stringify(body) });
  // Cloudflare adds `cf` to the incoming request. A test request does not have it.
  if (options.country !== undefined) Object.defineProperty(request, 'cf', { value: { country: options.country } });
  return request;
}

const send = (request: Request) => worker.fetch(request, env);
const rows = () => [...db.siteDays.values()];

describe('page view counter', () => {
  it('counts a page view and a visit from another site', async () => {
    const response = await send(view({ path: '/download/', referrer: 'https://www.google.com/' }, { country: 'DE' }));
    expect(response.status).toBe(204);
    expect(response.headers.get('access-control-allow-origin')).toBe(SITE_ORIGIN);
    expect(rows()).toEqual([{ date: '2026-10-13', path: '/download/', referrer: 'www.google.com', country: 'DE', device: 'desktop', views: 1, visits: 1 }]);
  });

  it('counts a direct open as a visit', async () => {
    await send(view({ path: '/', referrer: '' }));
    expect(rows()).toEqual([{ date: '2026-10-13', path: '/', referrer: '', country: 'XX', device: 'desktop', views: 1, visits: 1 }]);
  });

  it('adds a page view inside the site to the same row, but not as a visit', async () => {
    await send(view({ path: '/', referrer: '' }));
    await send(view({ path: '/', referrer: '' }));
    await send(view({ path: '/', referrer: 'https://banana-shot-editor.app/faq/' }));
    expect(rows().map(row => [row.referrer, row.views, row.visits])).toEqual([['', 2, 2], ['banana-shot-editor.app', 1, 0]]);
  });

  it('keeps known page paths and the 404 page, and counts other paths as (other)', async () => {
    for (const path of ['/', '/download/', '/404', '/wp-login.php', '/download', '/a/b/c/d/', 'x'.repeat(70)]) {
      await send(view({ path, referrer: '' }));
    }
    expect(rows().map(row => [row.path, row.views])).toEqual([['/', 1], ['/download/', 1], ['/404', 1], ['(other)', 4]]);
  });

  it('keeps only a valid host of the referrer', async () => {
    await send(view({ path: '/', referrer: 'not a url' }));
    await send(view({ path: '/', referrer: 'android-app://com.google.android.gm/' }));
    expect(rows().map(row => row.referrer)).toEqual(['(other)', 'com.google.android.gm']);
  });

  it('finds the device type and ignores an invalid country', async () => {
    await send(view({ path: '/', referrer: '' }, { userAgent: PHONE, country: 'not a country' }));
    expect(rows()[0]).toMatchObject({ device: 'mobile', country: 'XX' });
    expect(deviceType('Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X)')).toBe('tablet');
    expect(deviceType('Mozilla/5.0 (Linux; Android 15; SM-X910) AppleWebKit/537.36 Chrome/141.0 Safari/537.36')).toBe('tablet');
    expect(deviceType('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile/15E148')).toBe('mobile');
  });

  it('does not count a bot, but gives it the same answer', async () => {
    const response = await send(view({ path: '/', referrer: '' }, { userAgent: 'Mozilla/5.0 (compatible; Googlebot/2.1)' }));
    expect(response.status).toBe(204);
    expect(rows()).toHaveLength(0);
    expect(db.rates.size).toBe(0);
  });

  it.each([null, 'https://example.test', 'http://banana-shot-editor.app'])('refuses the origin %s', async origin => {
    expect((await send(view({ path: '/', referrer: '' }, { origin }))).status).toBe(403);
    expect(rows()).toHaveLength(0);
  });

  it('refuses a method other than POST', async () => {
    const request = new Request(URL_SITE, { method: 'GET', headers: { origin: SITE_ORIGIN } });
    expect((await send(request)).status).toBe(405);
  });

  it.each(['not json', '[]', 'null', '{"path":"/"}', '{"path":1,"referrer":""}'])('refuses the body %s', async body => {
    expect((await send(view(body))).status).toBe(400);
    expect(rows()).toHaveLength(0);
  });

  it('refuses a large body', async () => {
    expect((await send(view({ path: '/', referrer: 'x'.repeat(MAX_SITE_BODY_BYTES) }))).status).toBe(413);
  });

  it('stops at the rate limit of the address', async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) await send(view({ path: '/', referrer: '' }));
    expect((await send(view({ path: '/', referrer: '' }))).status).toBe(429);
    expect(rows()[0].views).toBe(HOURLY_LIMIT);
  });

  it('returns 503 when the database fails', async () => {
    db.fail = true;
    expect((await send(view({ path: '/', referrer: '' }))).status).toBe(503);
  });

  it('returns 410 when ingestion is off', async () => {
    env.ANALYTICS_INGESTION_ENABLED = 'false';
    expect((await send(view({ path: '/', referrer: '' }))).status).toBe(410);
    expect(rows()).toHaveLength(0);
  });
});

describe('bot counter', () => {
  const counter = () => new SiteCounter({} as ExecutionContext, env);
  const botRows = () => [...db.botDays.values()];

  it.each([
    ['Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)', 'Googlebot'],
    ['Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)', 'Bingbot'],
    ['Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)', 'YandexBot'],
    ['Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; GPTBot/1.2; +https://openai.com/gptbot)', 'OpenAI'],
    ['Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; ClaudeBot/1.0; +claudebot@anthropic.com)', 'Anthropic'],
    ['TelegramBot (like TwitterBot)', 'Telegram'],
    ['WhatsApp/2.23.20.0 A', 'WhatsApp'],
    ['facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)', 'Meta'],
    ['Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)', 'SEO crawler'],
    ['Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/141.0 Safari/537.36', 'Headless browser'],
    ['curl/8.5.0', 'Script'],
    ['python-requests/2.32.3', 'Script'],
    ['Mozilla/5.0 (compatible; SomeNewCrawler/1.0)', 'Other bot'],
    ['', 'No user agent'],
    ['Lynx/2.9.0', 'Other program'],
  ])('names the bot of %s', (userAgent, name) => {
    expect(botName(userAgent)).toBe(name);
  });

  it.each([
    DESKTOP,
    PHONE,
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0',
    'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36 YandexSearch/25.10',
    'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile DuckDuckGo/5 Safari/537.36',
  ])('does not take a browser for a bot: %s', userAgent => {
    expect(botName(userAgent)).toBeNull();
  });

  it('counts the HTML requests of bots for each day, bot, and page', async () => {
    await counter().pageRequest('/', 200, 'Mozilla/5.0 (compatible; Googlebot/2.1)');
    await counter().pageRequest('/', 200, 'Mozilla/5.0 (compatible; Googlebot/2.1)');
    await counter().pageRequest('/wp-admin/setup.php', 404, 'python-requests/2.32.3');
    await counter().pageRequest('/download/', 200, 'curl/8.5.0');
    expect(botRows()).toEqual([
      { date: '2026-10-13', bot: 'Googlebot', path: '/', requests: 2 },
      { date: '2026-10-13', bot: 'Script', path: '/404', requests: 1 },
      { date: '2026-10-13', bot: 'Script', path: '/download/', requests: 1 },
    ]);
  });

  it('does not store a request of a person', async () => {
    await counter().pageRequest('/', 200, DESKTOP);
    expect(botRows()).toHaveLength(0);
  });

  it('stores nothing when ingestion is off, and does not throw when the database fails', async () => {
    env.ANALYTICS_INGESTION_ENABLED = 'false';
    await counter().pageRequest('/', 200, 'curl/8.5.0');
    expect(botRows()).toHaveLength(0);
    env.ANALYTICS_INGESTION_ENABLED = 'true';
    db.fail = true;
    await expect(counter().pageRequest('/', 200, 'curl/8.5.0')).resolves.toBeUndefined();
  });

  it('does not count a page view of the beacon from a bot', async () => {
    await send(view({ path: '/', referrer: '' }, { userAgent: 'Mozilla/5.0 (compatible; Googlebot/2.1)' }));
    expect(rows()).toHaveLength(0);
    expect(botRows()).toHaveLength(0);
  });
});

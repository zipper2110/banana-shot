import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import worker, { SITE_ORIGIN, type Env } from '../src/index';
import { SITE_APP_VERSION } from '../src/validation';
import { FakeD1 } from './fake-d1';
import { FakeTelegram } from './fake-telegram';
import { LOG, report } from './fixtures';

const URL_SITE = 'https://feedback.example.test/v1/site-feedback';

let db: FakeD1;
let telegram: FakeTelegram;
let env: Env;

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  db = new FakeD1();
  telegram = new FakeTelegram();
  telegram.install();
  env = { FEEDBACK_INGESTION_ENABLED: 'true', FEEDBACK_DB: db.asD1(), TELEGRAM_BOT_TOKEN: '123:secret-token', TELEGRAM_CHAT_ID: '42', RATE_LIMIT_KEY: 'rate-key' };
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

/** A valid report of the site form. A value of `undefined` removes the key. */
function siteReport(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return report({ app_version: undefined, os_name: undefined, os_version: undefined, java_version: undefined, trap: '', ...overrides });
}

function post(body: unknown, origin: string | null = SITE_ORIGIN): Request {
  const headers = new Headers({ 'content-type': 'application/json', 'cf-connecting-ip': '198.51.100.7' });
  if (origin !== null) headers.set('origin', origin);
  return new Request(URL_SITE, { method: 'POST', headers, body: JSON.stringify(body) });
}

const send = (request: Request) => worker.fetch(request, env);

describe('site form', () => {
  it('answers the preflight request of the site', async () => {
    const response = await send(new Request(URL_SITE, { method: 'OPTIONS', headers: { origin: SITE_ORIGIN } }));
    expect(response.status).toBe(204);
    expect(response.headers.get('access-control-allow-origin')).toBe(SITE_ORIGIN);
    expect(response.headers.get('access-control-allow-headers')).toBe('content-type');
  });

  it('answers the preflight request also when the kill switch is off', async () => {
    env.FEEDBACK_INGESTION_ENABLED = 'false';
    expect((await send(new Request(URL_SITE, { method: 'OPTIONS', headers: { origin: SITE_ORIGIN } }))).status).toBe(204);
  });

  it.each([null, 'https://evil.example.test', 'http://banana-shot-editor.app'])('returns 403 for the origin %s and keeps nothing', async origin => {
    const response = await send(post(siteReport(), origin));
    expect(response.status).toBe(403);
    expect(db.reportRows()).toHaveLength(0);
    expect(telegram.calls).toHaveLength(0);
  });

  it('keeps the report, sends it to the author, and lets the site read the answer', async () => {
    const response = await send(post(siteReport()));
    expect(response.status).toBe(201);
    expect(response.headers.get('access-control-allow-origin')).toBe(SITE_ORIGIN);
    expect(await response.json()).toEqual({ report_id: siteReport().report_id });
    expect(db.reportRows()).toEqual([expect.objectContaining({
      topic: 'problem', message: 'The export stops.', email: 'user@example.test',
      app_version: SITE_APP_VERSION, os_name: '', os_version: '', java_version: '', has_log: 0, delivered: 1,
    })]);
    const text = telegram.calls[0].json?.text as string;
    expect(text).toContain('From: the contact form of the website');
    expect(text).not.toContain('App:');
  });

  it('accepts a report without an email address and without the trap field', async () => {
    expect((await send(post(siteReport({ email: undefined, trap: undefined })))).status).toBe(201);
    expect(db.reportRows()[0].email).toBeNull();
  });

  it('returns 201 for a filled trap field, but keeps and sends nothing', async () => {
    const response = await send(post(siteReport({ trap: 'https://spam.example.test' })));
    expect(response.status).toBe(201);
    expect(db.reportRows()).toHaveLength(0);
    expect(telegram.calls).toHaveLength(0);
  });

  it.each([
    ['app data', { app_version: '1.0.0' }],
    ['an error text', { error: 'Boom' }],
    ['a log', { log: LOG }],
    ['an unknown key', { page: '/contact/' }],
    ['a blank message', { message: '   ' }],
    ['a wrong email address', { email: 'name' }],
    ['a trap that is not text', { trap: 1 }],
  ])('returns 422 for a report with %s', async (_name, overrides) => {
    const response = await send(post(siteReport(overrides)));
    expect(response.status).toBe(422);
    expect(response.headers.get('access-control-allow-origin')).toBe(SITE_ORIGIN);
    expect(db.reportRows()).toHaveLength(0);
  });

  it('returns a readable 410 when the kill switch is off', async () => {
    env.FEEDBACK_INGESTION_ENABLED = 'false';
    const response = await send(post(siteReport()));
    expect(response.status).toBe(410);
    expect(response.headers.get('access-control-allow-origin')).toBe(SITE_ORIGIN);
  });

  it('does not accept a site report on the path of the app', async () => {
    const request = new Request('https://feedback.example.test/v1/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(siteReport()) });
    expect((await send(request)).status).toBe(422);
  });

  it('does not add the CORS headers on the path of the app', async () => {
    const response = await send(new Request('https://feedback.example.test/v1/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(report()) }));
    expect(response.status).toBe(201);
    expect(response.headers.get('access-control-allow-origin')).toBeNull();
  });
});

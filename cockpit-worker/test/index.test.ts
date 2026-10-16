import { describe, expect, it } from 'vitest';
import worker, { summary } from '../src/index';
import { DAY, isoDate, startOfDay } from '../src/period';
import { NOW, addSession, at, basicAuth, makeEnv } from './fixtures';

function get(path: string, authorization?: string, method = 'GET') {
  return new Request(`https://cockpit.example.test${path}`, { method, headers: authorization ? { authorization } : {} });
}

describe('access', () => {
  it('refuses each request when the password is not set', async () => {
    const response = await worker.fetch(get('/', basicAuth('')), makeEnv({ COCKPIT_PASSWORD: undefined }));
    expect(response.status).toBe(503);
  });

  it('asks for the password, and refuses a wrong one', async () => {
    const env = makeEnv();
    const missing = await worker.fetch(get('/'), env);
    expect(missing.status).toBe(401);
    expect(missing.headers.get('www-authenticate')).toContain('Basic');
    expect((await worker.fetch(get('/', basicAuth('wrong')), env)).status).toBe(401);
    expect((await worker.fetch(get('/api/summary', 'Basic !!!'), env)).status).toBe(401);
  });

  it('serves the page and the API with the right password, and never lets a cache keep them', async () => {
    const env = makeEnv();
    const page = await worker.fetch(get('/', basicAuth('secret')), env);
    expect(page.status).toBe(200);
    expect(page.headers.get('cache-control')).toBe('no-store');
    expect(page.headers.get('x-robots-tag')).toContain('noindex');

    const api = await worker.fetch(get('/api/summary?days=7', basicAuth('secret')), env);
    expect(api.status).toBe(200);
    expect(api.headers.get('content-type')).toContain('application/json');
  });

  it('needs no password on localhost when .dev.vars turns the check off', async () => {
    const env = makeEnv({ COCKPIT_PASSWORD: undefined, COCKPIT_DEV_NO_AUTH: 'true' });
    const local = await worker.fetch(new Request('http://localhost:8791/api/summary'), env);
    expect(local.status).toBe(200);
  });

  it('asks for the password on a real host, also when the variable is set by mistake', async () => {
    const env = makeEnv({ COCKPIT_DEV_NO_AUTH: 'true' });
    expect((await worker.fetch(get('/'), env)).status).toBe(401);
    expect((await worker.fetch(new Request('http://localhost:8791/'), makeEnv())).status).toBe(401);
  });

  it('accepts only POST for a manual snapshot', async () => {
    const response = await worker.fetch(get('/api/snapshot', basicAuth('secret')), makeEnv());
    expect(response.status).toBe(405);
  });
});

describe('summary', () => {
  it('keeps the other sections when one source fails', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-10-06') });
    env.FEEDBACK_DB = { prepare: () => { throw new Error('D1 is not available'); } } as unknown as D1Database;

    const result = await summary(env, new URL('https://x/api/summary?days=30'), NOW);

    expect(result.period.days).toBe(30);
    expect(result.feedback).toEqual({ ok: false, error: 'D1 is not available' });
    expect(result.app.ok && result.app.data.totals.sessions).toBe(1);
    expect(result.site.ok).toBe(true);
    expect(result.workers.ok).toBe(false);
    expect(result.downloads.ok).toBe(true);
  });

  it('counts only today for the period of 1 day', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: NOW });
    addSession(env, { receivedAt: NOW - DAY });

    const result = await summary(env, new URL('https://x/api/summary?days=1'), NOW);

    expect(result.period.days).toBe(1);
    expect(result.period.start).toBe(startOfDay(NOW));
    expect(result.period.prevStart).toBe(startOfDay(NOW) - DAY);
    expect(result.app.ok && result.app.data.totals.sessions).toBe(1);
    expect(result.app.ok && result.app.data.prevTotals.sessions).toBe(1);
    expect(result.app.ok && result.app.data.daily.length).toBe(1);
  });

  it('ends the period on the day of the parameter end', async () => {
    const env = makeEnv();
    const lastDay = startOfDay(NOW) - 10 * DAY;
    addSession(env, { receivedAt: lastDay + 3_600_000 });
    addSession(env, { receivedAt: lastDay - DAY + 3_600_000 });
    addSession(env, { receivedAt: NOW });

    const result = await summary(env, new URL(`https://x/api/summary?days=1&end=${isoDate(lastDay)}`), NOW);

    expect(result.period.start).toBe(lastDay);
    expect(result.period.end).toBe(lastDay + DAY);
    expect(result.app.ok && result.app.data.totals.sessions).toBe(1);
    expect(result.app.ok && result.app.data.prevTotals.sessions).toBe(1);
  });

  it.each(['2999-01-01', '2026-02-30', 'yesterday'])('ends the period today for the parameter end=%s', async end => {
    const result = await summary(makeEnv(), new URL(`https://x/api/summary?days=7&end=${end}`), NOW);
    expect(result.period.end).toBe(startOfDay(NOW) + DAY);
  });

  it('uses 30 days for an unknown period', async () => {
    const result = await summary(makeEnv(), new URL('https://x/api/summary?days=5'), NOW);
    expect(result.period.days).toBe(30);
  });
});

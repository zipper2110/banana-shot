import { describe, expect, it } from 'vitest';
import worker, { summary } from '../src/index';
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
    expect(result.site.ok).toBe(false);
    expect(result.downloads.ok).toBe(true);
  });

  it('uses 30 days for an unknown period', async () => {
    const result = await summary(makeEnv(), new URL('https://x/api/summary?days=5'), NOW);
    expect(result.period.days).toBe(30);
  });
});

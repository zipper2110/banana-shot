import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import worker, { MAX_BODY_BYTES, type Env } from '../src/index';
import { HOURLY_LIMIT } from '../src/rate-limit';
import { appVersionText } from '../src/validation';
import { FakeD1 } from './fake-d1';
import { bodyOf, invalidSummaries, summary, validSummaries } from './fixtures';

const HOUR = 60 * 60 * 1000;
const NOW = Date.UTC(2026, 9, 3, 12, 34, 56);
const NOW_HOUR = Date.UTC(2026, 9, 3, 12, 0, 0);
const URL_SESSION = 'https://analytics.example.test/v1/session';
const IP = '203.0.113.7';

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

function post(body: string, contentType: string | null = 'application/json', url = URL_SESSION, ip = IP): Request {
  const headers = new Headers({ 'cf-connecting-ip': ip, 'user-agent': 'Java-http-client/25' });
  if (contentType !== null) headers.set('content-type', contentType);
  return new Request(url, { method: 'POST', headers, body });
}

const send = (request: Request) => worker.fetch(request, env);
const sendSummary = (overrides: Record<string, unknown> = {}) => send(post(JSON.stringify(summary(overrides))));

describe('kill switch', () => {
  it.each(['false', '', 'TRUE', '1'])('returns 410 and stores nothing when ingestion is "%s"', async value => {
    env.ANALYTICS_INGESTION_ENABLED = value;
    const response = await sendSummary();
    expect(response.status).toBe(410);
    expect(await response.text()).toBe('');
    expect(db.sessionRows()).toHaveLength(0);
  });

  it('comes before all other checks', async () => {
    env.ANALYTICS_INGESTION_ENABLED = 'false';
    expect((await send(new Request('https://analytics.example.test/other', { method: 'GET' }))).status).toBe(410);
  });
});

describe('request checks', () => {
  it.each(['/', '/v1', '/v1/session/', '/v1/events/batch'])('returns 404 for the path %s', async path => {
    expect((await send(post(JSON.stringify(summary()), 'application/json', `https://analytics.example.test${path}`))).status).toBe(404);
  });

  it.each(['GET', 'PUT', 'DELETE'])('returns 405 for %s', async method => {
    const body = method === 'GET' ? undefined : '{}';
    expect((await send(new Request(URL_SESSION, { method, body }))).status).toBe(405);
  });

  it.each([null, 'text/plain', 'application/x-www-form-urlencoded'])('returns 415 for the content type %s', async contentType => {
    expect((await send(post(JSON.stringify(summary()), contentType))).status).toBe(415);
  });

  it('accepts a content type with a charset and in uppercase', async () => {
    expect((await send(post(JSON.stringify(summary()), 'Application/JSON; charset=utf-8'))).status).toBe(204);
  });

  it('returns 413 for a body above 8 KiB and stores nothing', async () => {
    const body = JSON.stringify(summary({ app_version: 'x'.repeat(MAX_BODY_BYTES) }));
    expect((await send(post(body))).status).toBe(413);
    expect(db.sessionRows()).toHaveLength(0);
  });

  it('accepts a body of exactly 8 KiB', async () => {
    const empty = JSON.stringify(summary({ app_version: '' }));
    const body = JSON.stringify(summary({ app_version: 'x'.repeat(MAX_BODY_BYTES - empty.length) }));
    expect(new TextEncoder().encode(body).length).toBe(MAX_BODY_BYTES);
    expect((await send(post(body))).status).toBe(204);
  });

  it('counts bytes, not characters', async () => {
    const empty = JSON.stringify(summary({ app_version: '' }));
    const body = JSON.stringify(summary({ app_version: 'ü'.repeat((MAX_BODY_BYTES - empty.length) / 2 + 1) }));
    expect((await send(post(body))).status).toBe(413);
  });
});

describe('invalid summaries', () => {
  it.each(invalidSummaries.map(invalid => [invalid.name, invalid]))('returns 400 for "%s" and stores nothing', async (_name, invalid) => {
    const response = await send(post(bodyOf(invalid)));
    expect(response.status).toBe(400);
    expect(await response.text()).toBe('');
    expect(db.sessionRows()).toHaveLength(0);
    expect(db.rates.size).toBe(0);
  });
});

describe('valid summaries', () => {
  it.each(validSummaries.map((value, i) => [i, value]))('stores valid summary %i', async (_i, value) => {
    const response = await send(post(JSON.stringify(value)));
    expect(response.status).toBe(204);
    expect(await response.text()).toBe('');
    expect(db.sessionRows()).toEqual([{
      session_id: value.session_id,
      first_received_at: NOW_HOUR,
      last_received_at: NOW_HOUR,
      schema_version: value.schema_version,
      notice_version: value.notice_version,
      level: value.level,
      app_version: appVersionText(value.app_version),
      os_family: value.os_family,
      snapshot: value.snapshot,
      final: value.final ? 1 : 0,
      duration_s: value.duration_s,
      active_s: value.active_s,
      counters: JSON.stringify(value.counters),
      attributes: JSON.stringify(value.attributes ?? {}),
    }]);
  });

  it('stores the attributes with the array values in the order of the list', async () => {
    const response = await sendSummary({ schema_version: 3, notice_version: 2, attributes: { sport: ['padel', 'tennis'], theme: 'light' } });
    expect(response.status).toBe(204);
    expect(db.sessionRows()[0].attributes).toBe('{"theme":"light","sport":["tennis","padel"]}');
  });

  it('does not store the IP address or the headers', async () => {
    await sendSummary({ counters: { point_added: 2 } });
    const stored = JSON.stringify([db.sessionRows(), [...db.rates.values()]]);
    expect(stored).not.toContain(IP);
    expect(stored).not.toContain('Java-http-client');
    expect(stored).not.toContain('application/json');
  });
});

describe('snapshots', () => {
  it('replaces the row with a higher snapshot and keeps first_received_at', async () => {
    await sendSummary({ snapshot: 0 });
    vi.setSystemTime(NOW + 2 * HOUR);
    const response = await sendSummary({ snapshot: 1, final: true, duration_s: 300, active_s: 200, counters: { point_added: 4 } });
    expect(response.status).toBe(204);
    expect(db.sessionRows()).toHaveLength(1);
    expect(db.sessionRows()[0]).toMatchObject({
      first_received_at: NOW_HOUR, last_received_at: NOW_HOUR + 2 * HOUR, snapshot: 1, final: 1, duration_s: 300, active_s: 200,
      counters: '{"point_added":4}',
    });
  });

  it.each([3, 2])('keeps the row when the snapshot is %i (not higher than 3)', async snapshot => {
    await sendSummary({ snapshot: 3, counters: { point_added: 9 } });
    vi.setSystemTime(NOW + HOUR);
    expect((await sendSummary({ snapshot, counters: { point_added: 1 } })).status).toBe(204);
    expect(db.sessionRows()[0]).toMatchObject({ snapshot: 3, last_received_at: NOW_HOUR, counters: '{"point_added":9}' });
  });

  it('removes the attributes when the level changes to essential', async () => {
    await sendSummary({ schema_version: 3, notice_version: 2, snapshot: 1, attributes: { theme: 'dark', sport: ['padel'] } });
    expect((await sendSummary({ schema_version: 3, notice_version: 2, snapshot: 2, level: 'essential', counters: { session_n_1: 1 } })).status).toBe(204);
    expect(db.sessionRows()[0]).toMatchObject({ level: 'essential', attributes: '{}' });
  });

  it('replaces the extended counters when the level changes to essential', async () => {
    await sendSummary({ snapshot: 1, counters: { session_n_1: 1, point_added: 4, tab_s_points: 60 } });
    expect((await sendSummary({ snapshot: 2, level: 'essential', counters: { session_n_1: 1 } })).status).toBe(204);
    expect(db.sessionRows()[0]).toMatchObject({ level: 'essential', snapshot: 2, counters: '{"session_n_1":1}' });
  });

  it('keeps one row for each session', async () => {
    await sendSummary({ session_id: '00000000-0000-4000-8000-000000000001' });
    await sendSummary({ session_id: '00000000-0000-4000-8000-000000000002' });
    expect(db.sessionRows()).toHaveLength(2);
  });
});

describe('rate limit', () => {
  it(`accepts ${HOURLY_LIMIT} summaries from one address in one hour and refuses the next one`, async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) expect((await sendSummary({ snapshot: i })).status).toBe(204);
    expect((await sendSummary({ snapshot: HOURLY_LIMIT })).status).toBe(429);
    expect(db.sessionRows()[0].snapshot).toBe(HOURLY_LIMIT - 1);
  });

  it('counts each address separately', async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) await sendSummary({ snapshot: i });
    expect((await send(post(JSON.stringify(summary({ snapshot: 999 })), 'application/json', URL_SESSION, '198.51.100.1'))).status).toBe(204);
  });

  it('starts a new count in the next hour and deletes the old rows', async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) await sendSummary({ snapshot: i });
    vi.setSystemTime(NOW + HOUR);
    expect((await sendSummary({ snapshot: 999 })).status).toBe(204);
    expect(db.rates.size).toBe(1);
  });

  it('returns 503 and stores nothing when the rate key is not set', async () => {
    env.RATE_LIMIT_KEY = undefined;
    expect((await sendSummary()).status).toBe(503);
    expect(db.sessionRows()).toHaveLength(0);
  });
});

describe('failures', () => {
  it('returns 503 when D1 fails', async () => {
    db.fail = true;
    const response = await sendSummary();
    expect(response.status).toBe(503);
    expect(await response.text()).toBe('');
  });
});

describe('app_version', () => {
  it.each([
    ['1.2.3', '1.2.3'],
    ['', ''],
    ['1.0 beta (build 7) ü/2026', '1.0 beta (build 7) ü/2026'],
    [7, '7'],
    [{ major: 1 }, '{"major":1}'],
    [null, 'unknown'],
    [undefined, 'unknown'],
  ])('accepts %j and stores %j', async (value, stored) => {
    expect((await sendSummary({ app_version: value })).status).toBe(204);
    expect(db.sessionRows()[0].app_version).toBe(stored);
  });
});

describe('scheduled', () => {
  it('runs the retention', async () => {
    db.sessions.set('old', { session_id: 'old', last_received_at: 0 });
    await worker.scheduled({} as ScheduledController, env);
    expect(db.sessionRows()).toHaveLength(0);
    expect(db.retentionStatus).toMatchObject({ ran_at: NOW, deleted_count: 1 });
  });
});

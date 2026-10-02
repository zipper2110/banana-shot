import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import worker, { type Env } from '../src/index';
import { appVersionText } from '../src/validation';
import { FakeD1 } from './fake-d1';
import { batch, bodyOf, invalidBatches, validBatches } from './fixtures';

const NOW = Date.UTC(2026, 9, 3, 12, 0, 0);
const URL_BATCH = 'https://analytics.example.test/v1/events/batch';

let db: FakeD1;
let env: Env;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  db = new FakeD1();
  env = { ANALYTICS_INGESTION_ENABLED: 'true', ANALYTICS_DB: db.asD1() };
});

afterEach(() => {
  vi.useRealTimers();
});

function post(body: string, contentType: string | null = 'application/json', url = URL_BATCH): Request {
  const headers = new Headers();
  if (contentType !== null) headers.set('content-type', contentType);
  return new Request(url, { method: 'POST', headers, body });
}

const send = (request: Request) => worker.fetch(request, env);

describe('kill switch', () => {
  it.each(['false', '', 'TRUE', '1'])('returns 410 and stores nothing when ingestion is "%s"', async value => {
    env.ANALYTICS_INGESTION_ENABLED = value;
    const response = await send(post(JSON.stringify(batch())));
    expect(response.status).toBe(410);
    expect(await response.json()).toEqual({ accepted: 0, rejected: 0, reasons: {} });
    expect(db.eventRows()).toHaveLength(0);
  });

  it('comes before all other checks', async () => {
    env.ANALYTICS_INGESTION_ENABLED = 'false';
    const response = await send(new Request('https://analytics.example.test/other', { method: 'GET' }));
    expect(response.status).toBe(410);
  });
});

describe('request checks', () => {
  it('returns 405 for GET', async () => {
    expect((await send(new Request(URL_BATCH, { method: 'GET' }))).status).toBe(405);
  });

  it.each(['PUT', 'DELETE', 'PATCH'])('returns 404 for %s', async method => {
    expect((await send(new Request(URL_BATCH, { method, body: '{}' }))).status).toBe(404);
  });

  it.each(['/', '/v1/events', '/v1/events/batch/', '/v1/session'])('returns 404 for the path %s', async path => {
    expect((await send(post(JSON.stringify(batch()), 'application/json', `https://analytics.example.test${path}`))).status).toBe(404);
  });

  it.each([null, 'text/plain', 'application/x-www-form-urlencoded'])('returns 415 for the content type %s', async contentType => {
    expect((await send(post(JSON.stringify(batch()), contentType))).status).toBe(415);
  });

  it('accepts a content type with a charset and in uppercase', async () => {
    expect((await send(post(JSON.stringify(batch()), 'Application/JSON; charset=utf-8'))).status).toBe(202);
  });

  it('returns 413 for a body above 64 KiB', async () => {
    const body = JSON.stringify(batch({ app_version: 'x'.repeat(65_536) }));
    expect((await send(post(body))).status).toBe(413);
    expect(db.eventRows()).toHaveLength(0);
  });

  it('accepts a body of exactly 64 KiB', async () => {
    const empty = JSON.stringify(batch({ app_version: '' }));
    const body = JSON.stringify(batch({ app_version: 'x'.repeat(65_536 - empty.length) }));
    expect(body.length).toBe(65_536);
    expect((await send(post(body))).status).toBe(202);
  });
});

describe('invalid batches', () => {
  it.each(invalidBatches.map(invalid => [invalid.name, invalid]))('returns 422 for "%s" and stores nothing', async (_name, invalid) => {
    const response = await send(post(bodyOf(invalid)));
    expect(response.status).toBe(422);
    const text = await response.text();
    expect(JSON.parse(text)).toEqual({ accepted: 0, rejected: 1, reasons: { invalid: 1 } });
    expect(text).not.toContain('secret');
    expect(text).not.toContain('example.test');
    expect(db.eventRows()).toHaveLength(0);
  });
});

describe('valid batches', () => {
  it.each(validBatches.map((value, i) => [i, value]))('stores valid batch %i', async (_i, value) => {
    const events = value.events as Record<string, unknown>[];
    const response = await send(post(JSON.stringify(value)));
    expect(response.status).toBe(202);
    expect(await response.json()).toEqual({ accepted: events.length, rejected: 0, reasons: {} });
    expect(db.eventRows()).toEqual(events.map(event => ({
      session_id: value.session_id,
      sequence_number: event.sequence_number,
      received_at: NOW,
      schema_version: 1,
      notice_version: 1,
      event_name: event.name,
      elapsed_ms: event.elapsed_ms,
      app_version: appVersionText(value.app_version),
      os_family: value.os_family,
      properties: JSON.stringify(event.properties),
    })));
  });

  it('ignores an event that is already stored', async () => {
    await send(post(JSON.stringify(batch())));
    vi.setSystemTime(NOW + 60_000);
    const response = await send(post(JSON.stringify(batch({ app_version: '2.0.0' }))));
    expect(response.status).toBe(202);
    expect(db.eventRows()).toHaveLength(1);
    expect(db.eventRows()[0]).toMatchObject({ app_version: '1.0.0', received_at: NOW });
  });

  it('returns 503 when D1 fails', async () => {
    db.failBatch = true;
    const response = await send(post(JSON.stringify(batch())));
    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({ accepted: 0, rejected: 0, reasons: { unavailable: 1 } });
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
    const response = await send(post(JSON.stringify(batch({ app_version: value }))));
    expect(response.status).toBe(202);
    expect(db.eventRows()[0].app_version).toBe(stored);
  });
});

describe('scheduled', () => {
  it('runs the retention', async () => {
    db.events.set('old|0', { session_id: 'old', sequence_number: 0, received_at: 0 });
    await worker.scheduled({} as ScheduledController, env);
    expect(db.eventRows()).toHaveLength(0);
    expect(db.retentionStatus).toMatchObject({ ran_at: NOW, deleted_count: 1 });
  });
});

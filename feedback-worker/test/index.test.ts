import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import worker, { MAX_BODY_BYTES, type Env } from '../src/index';
import { HOURLY_LIMIT } from '../src/rate-limit';
import { FakeD1 } from './fake-d1';
import { FakeTelegram } from './fake-telegram';
import { bodyOf, invalidReports, LOG, report, validReports } from './fixtures';

const NOW = Date.UTC(2026, 9, 3, 12, 0, 0);
const URL_FEEDBACK = 'https://feedback.example.test/v1/feedback';
const TOKEN = '123:secret-token';

let db: FakeD1;
let telegram: FakeTelegram;
let env: Env;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  vi.spyOn(console, 'error').mockImplementation(() => {});
  db = new FakeD1();
  telegram = new FakeTelegram();
  telegram.install();
  env = { FEEDBACK_INGESTION_ENABLED: 'true', FEEDBACK_DB: db.asD1(), TELEGRAM_BOT_TOKEN: TOKEN, TELEGRAM_CHAT_ID: '42', RATE_LIMIT_KEY: 'rate-key' };
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function post(body: string, options: { contentType?: string | null; url?: string; ip?: string } = {}): Request {
  const headers = new Headers();
  const contentType = options.contentType === undefined ? 'application/json' : options.contentType;
  if (contentType !== null) headers.set('content-type', contentType);
  headers.set('cf-connecting-ip', options.ip ?? '198.51.100.7');
  return new Request(options.url ?? URL_FEEDBACK, { method: 'POST', headers, body });
}

const send = (request: Request) => worker.fetch(request, env);

describe('kill switch', () => {
  it.each(['false', '', 'TRUE', '1'])('returns 410 and keeps nothing when ingestion is "%s"', async value => {
    env.FEEDBACK_INGESTION_ENABLED = value;
    const response = await send(post(JSON.stringify(report())));
    expect(response.status).toBe(410);
    expect(await response.json()).toEqual({ error: 'disabled' });
    expect(db.reportRows()).toHaveLength(0);
    expect(telegram.calls).toHaveLength(0);
  });

  it('comes before all other checks', async () => {
    env.FEEDBACK_INGESTION_ENABLED = 'false';
    expect((await send(new Request('https://feedback.example.test/other', { method: 'GET' }))).status).toBe(410);
  });
});

describe('request checks', () => {
  it('returns 405 for GET', async () => {
    expect((await send(new Request(URL_FEEDBACK, { method: 'GET' }))).status).toBe(405);
  });

  it.each(['/', '/v1/feedback/', '/v1/events/batch', '/v2/feedback'])('returns 404 for the path %s', async path => {
    expect((await send(post(JSON.stringify(report()), { url: `https://feedback.example.test${path}` }))).status).toBe(404);
  });

  it.each([null, 'text/plain', 'multipart/form-data'])('returns 415 for the content type %s', async contentType => {
    expect((await send(post(JSON.stringify(report()), { contentType }))).status).toBe(415);
  });

  it('accepts a content type with a charset and in uppercase', async () => {
    expect((await send(post(JSON.stringify(report()), { contentType: 'Application/JSON; charset=utf-8' }))).status).toBe(201);
  });

  it('returns 413 for a body above 3 MB and keeps nothing', async () => {
    const body = JSON.stringify(report({ message: 'x'.repeat(MAX_BODY_BYTES) }));
    const response = await send(post(body));
    expect(response.status).toBe(413);
    expect(await response.json()).toEqual({ error: 'too_large' });
    expect(db.reportRows()).toHaveLength(0);
  });

  it('counts the bytes, not the characters', async () => {
    const body = JSON.stringify(report({ message: 'ü'.repeat(MAX_BODY_BYTES / 2 + 1) }));
    expect(body.length).toBeLessThan(MAX_BODY_BYTES);
    expect((await send(post(body))).status).toBe(413);
  });
});

describe('invalid reports', () => {
  it.each(invalidReports.map(invalid => [invalid.name, invalid]))('returns 422 for "%s" and keeps nothing', async (_name, invalid) => {
    const response = await send(post(bodyOf(invalid)));
    expect(response.status).toBe(422);
    const text = await response.text();
    expect(JSON.parse(text)).toEqual({ error: 'invalid' });
    expect(text).not.toContain('secret');
    expect(db.reportRows()).toHaveLength(0);
    expect(telegram.calls).toHaveLength(0);
  });
});

describe('valid reports', () => {
  it.each(validReports.map((value, i) => [i, value]))('keeps and sends valid report %i', async (_i, value) => {
    const response = await send(post(JSON.stringify(value)));
    expect(response.status).toBe(201);
    expect(await response.json()).toEqual({ report_id: value.report_id });
    expect(db.reportRows()).toEqual([{
      report_id: value.report_id,
      received_at: NOW,
      topic: value.topic,
      message: value.message,
      email: value.email ?? null,
      app_version: value.app_version,
      os_name: value.os_name,
      os_version: value.os_version,
      java_version: value.java_version,
      error: value.error ?? null,
      has_log: value.log ? 1 : 0,
      delivered: 1,
    }]);
    expect(telegram.calls[0].method).toBe('sendMessage');
    expect(telegram.calls).toHaveLength(value.log ? 2 : 1);
  });

  it('does not keep the log', async () => {
    await send(post(JSON.stringify(report({ log: LOG }))));
    expect(JSON.stringify(db.reportRows())).not.toContain(LOG);
  });

  it('sends the message to the chat of the secret with the token of the secret', async () => {
    await send(post(JSON.stringify(report())));
    expect(telegram.calls[0].url).toBe(`https://api.telegram.org/bot${TOKEN}/sendMessage`);
    expect(telegram.calls[0].json).toMatchObject({ chat_id: '42' });
    expect(telegram.calls[0].json!.text).toContain('The export stops.');
  });
});

describe('a second request with the same report_id', () => {
  it('returns 200 and changes nothing after a delivered report', async () => {
    await send(post(JSON.stringify(report())));
    vi.setSystemTime(NOW + 60_000);
    const response = await send(post(JSON.stringify(report({ message: 'Other text' }))));
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ report_id: report().report_id });
    expect(db.reportRows()).toHaveLength(1);
    expect(db.reportRows()[0]).toMatchObject({ message: 'The export stops.', received_at: NOW });
    expect(telegram.calls).toHaveLength(1);
  });

  it('sends again after a failed delivery, with the log', async () => {
    telegram.failAt = 1;
    const first = await send(post(JSON.stringify(report({ log: LOG }))));
    expect(first.status).toBe(503);
    expect(db.reportRows()[0].delivered).toBe(0);

    const second = await send(post(JSON.stringify(report({ log: LOG }))));
    expect(second.status).toBe(201);
    expect(db.reportRows()).toHaveLength(1);
    expect(db.reportRows()[0].delivered).toBe(1);
    expect(telegram.calls.map(call => call.method)).toEqual(['sendMessage', 'sendDocument', 'sendMessage', 'sendDocument']);
  });
});

describe('failures', () => {
  it('returns 503 and keeps the row undelivered when Telegram fails', async () => {
    telegram.failAll = true;
    const response = await send(post(JSON.stringify(report())));
    expect(response.status).toBe(503);
    const text = await response.text();
    expect(JSON.parse(text)).toEqual({ error: 'unavailable' });
    expect(text).not.toContain(TOKEN);
    expect(db.reportRows()[0].delivered).toBe(0);
  });

  it('does not write the bot token to the log', async () => {
    telegram.failAll = true;
    await send(post(JSON.stringify(report())));
    const logged = vi.mocked(console.error).mock.calls.flat().join(' ');
    expect(logged).not.toContain(TOKEN);
  });

  it('returns 503 when D1 fails', async () => {
    db.fail = true;
    expect((await send(post(JSON.stringify(report())))).status).toBe(503);
    expect(telegram.calls).toHaveLength(0);
  });

  it.each(['TELEGRAM_BOT_TOKEN', 'TELEGRAM_CHAT_ID', 'RATE_LIMIT_KEY'] as const)('returns 503 without the secret %s', async secret => {
    delete env[secret];
    expect((await send(post(JSON.stringify(report())))).status).toBe(503);
    expect(telegram.calls).toHaveLength(0);
  });
});

describe('rate limit', () => {
  const nth = (i: number) => report({ report_id: `00000000-0000-4000-8000-${String(i).padStart(12, '0')}` });

  it(`accepts ${HOURLY_LIMIT} reports from one address in one hour and refuses the next`, async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) expect((await send(post(JSON.stringify(nth(i))))).status).toBe(201);
    const response = await send(post(JSON.stringify(nth(HOURLY_LIMIT))));
    expect(response.status).toBe(429);
    expect(await response.json()).toEqual({ error: 'rate_limited' });
    expect(db.reportRows()).toHaveLength(HOURLY_LIMIT);
  });

  it('counts each address separately', async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) await send(post(JSON.stringify(nth(i))));
    expect((await send(post(JSON.stringify(nth(HOURLY_LIMIT)), { ip: '203.0.113.9' }))).status).toBe(201);
  });

  it('accepts reports again in the next hour', async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) await send(post(JSON.stringify(nth(i))));
    vi.setSystemTime(NOW + 60 * 60 * 1000);
    expect((await send(post(JSON.stringify(nth(HOURLY_LIMIT))))).status).toBe(201);
  });

  it('does not count a report that was delivered before', async () => {
    for (let i = 0; i < HOURLY_LIMIT; i++) await send(post(JSON.stringify(nth(i))));
    expect((await send(post(JSON.stringify(nth(0))))).status).toBe(200);
  });

  it('does not keep the IP address', async () => {
    await send(post(JSON.stringify(report())));
    const stored = JSON.stringify([...db.rates.values(), ...db.reportRows()]);
    expect(stored).not.toContain('198.51.100.7');
    expect([...db.rates.keys()][0]).toMatch(/^[0-9a-f]{64}$/);
  });
});

describe('scheduled', () => {
  it('runs the retention', async () => {
    db.reports.set('old', { report_id: 'old', received_at: 0 });
    await worker.scheduled({} as ScheduledController, env);
    expect(db.reportRows()).toHaveLength(0);
  });
});

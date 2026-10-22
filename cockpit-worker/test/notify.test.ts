import { describe, expect, it } from 'vitest';
import { SETUP_ASSET } from '../src/downloads';
import { runHourly } from '../src/index';
import { DIGEST_JOB, sendDigest } from '../src/notify';
import { NOW, addPageView, addSession, addSnapshot, makeEnv, type TestEnv } from './fixtures';

const HOUR = 3_600_000;
const TODAY = '2026-10-06';

/** A fake Telegram. It keeps each message text. `ok: false` makes Telegram refuse each message. */
function telegram(ok = true) {
  const texts: string[] = [];
  const urls: string[] = [];
  const fetcher = (async (input: RequestInfo, init?: RequestInit) => {
    urls.push(String(input));
    texts.push(JSON.parse(String(init?.body)).text);
    return Response.json({ ok }, { status: ok ? 200 : 400 });
  }) as typeof fetch;
  return { texts, urls, fetcher };
}

function configured(): TestEnv {
  return makeEnv({ TELEGRAM_BOT_TOKEN: 'bot-token', TELEGRAM_CHAT_ID: '42' });
}

function setVisits(env: TestEnv, visits: number, values: { path?: string; referrer?: string; country?: string; device?: string } = {}) {
  env.analytics.db.prepare('DELETE FROM site_daily WHERE path = ? AND referrer = ?').run(values.path ?? '/', values.referrer ?? '');
  addPageView(env, { date: TODAY, views: visits, visits, ...values });
}

describe('telegram digest', () => {
  it('does nothing without the Telegram secrets', async () => {
    const env = makeEnv();
    const bot = telegram();
    setVisits(env, 3);
    expect((await sendDigest(env, NOW, bot.fetcher)).configured).toBe(false);
    expect(bot.texts).toEqual([]);
    expect(env.cockpit.db.prepare('SELECT COUNT(*) AS n FROM notify_seen').get()).toEqual({ n: 0 });
  });

  it('keeps only the base line on the first run, and then sends the new visits, downloads, and installs', async () => {
    const env = configured();
    const bot = telegram();
    setVisits(env, 5, { path: '/download/', referrer: 'google.com', country: 'DE' });
    addSnapshot(env, TODAY, { [SETUP_ASSET]: 10, 'BananaShot-1.0.0-full.nupkg': 4, 'releases.win.json': 100 });
    addSession(env, { receivedAt: NOW - 2 * HOUR, counters: { session_n_1: 1 } });

    expect((await sendDigest(env, NOW, bot.fetcher)).baseline).toBe(true);
    expect(bot.texts).toEqual([]);

    setVisits(env, 7, { path: '/download/', referrer: 'google.com', country: 'DE' });
    setVisits(env, 1, { path: '/', referrer: '', country: 'US', device: 'mobile' });
    env.cockpit.db.prepare('DELETE FROM download_snapshot').run();
    addSnapshot(env, TODAY, { [SETUP_ASSET]: 13, 'BananaShot-1.0.0-full.nupkg': 5, 'releases.win.json': 180 });
    addSession(env, { receivedAt: NOW, version: '1.1.0', counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: NOW, version: '1.1.0', counters: { session_n_4: 1 } });
    addSession(env, { receivedAt: NOW, version: '1.2.0-SNAPSHOT', counters: { session_n_1: 1 } });

    const result = await sendDigest(env, NOW + HOUR, bot.fetcher);

    expect(result.counts).toEqual({ visit: 3, download: 4, install: 1 });
    expect(bot.urls).toEqual(['https://api.telegram.org/botbot-token/sendMessage']);
    expect(bot.texts).toEqual([[
      'BananaShot: new activity',
      '',
      'Visits: 3',
      '2 × /download/ · google.com · DE · desktop',
      '1 × / · direct · US · mobile',
      '',
      'Downloads: 4',
      '3 × installer v1.0.0',
      '1 × update package v1.0.0',
      '',
      'New installs: 1',
      '1 × 1.1.0 · windows',
    ].join('\n')]);
  });

  it('sends nothing when nothing is new', async () => {
    const env = configured();
    const bot = telegram();
    setVisits(env, 2);
    await sendDigest(env, NOW, bot.fetcher);
    const result = await sendDigest(env, NOW + HOUR, bot.fetcher);
    expect(result.text).toBeNull();
    expect(bot.texts).toEqual([]);
    expect(env.cockpit.db.prepare('SELECT ok, message FROM job_status WHERE name = ?').get(DIGEST_JOB))
      .toEqual({ ok: 1, message: '0 visits, 0 downloads, 0 installs' });
  });

  it('counts the files of a new release from 0', async () => {
    const env = configured();
    const bot = telegram();
    addSnapshot(env, TODAY, { [SETUP_ASSET]: 10 });
    await sendDigest(env, NOW, bot.fetcher);
    addSnapshot(env, TODAY, { [SETUP_ASSET]: 2 }, 'v1.1.0');
    await sendDigest(env, NOW + HOUR, bot.fetcher);
    expect(bot.texts[0]).toContain('Downloads: 2\n2 × installer v1.1.0');
  });

  it('keeps a base line for the downloads when the first snapshot comes after the first run', async () => {
    const env = configured();
    const bot = telegram();
    await sendDigest(env, NOW, bot.fetcher);
    addSnapshot(env, TODAY, { [SETUP_ASSET]: 500 });
    await sendDigest(env, NOW + HOUR, bot.fetcher);
    expect(bot.texts).toEqual([]);
  });

  it('sends the same items again in the next run when Telegram refuses the message', async () => {
    const env = configured();
    await sendDigest(env, NOW, telegram().fetcher);
    setVisits(env, 2);

    await expect(sendDigest(env, NOW + HOUR, telegram(false).fetcher)).rejects.toThrow('Telegram sendMessage failed with status 400');
    const error = env.cockpit.db.prepare('SELECT ok, message FROM job_status WHERE name = ?').get(DIGEST_JOB) as { ok: number; message: string };
    expect(error.ok).toBe(0);
    expect(error.message).not.toContain('bot-token');

    const bot = telegram();
    await sendDigest(env, NOW + 2 * HOUR, bot.fetcher);
    expect(bot.texts[0]).toContain('Visits: 2');
  });

  it('deletes the visit totals of the days before yesterday', async () => {
    const env = configured();
    addPageView(env, { date: '2026-10-04', views: 1, visits: 1 });
    addPageView(env, { date: '2026-10-05', views: 1, visits: 1 });
    await sendDigest(env, NOW, telegram().fetcher);
    expect(env.cockpit.db.prepare('SELECT day FROM notify_seen').all()).toEqual([{ day: '2026-10-05' }]);
  });

  it('shows the largest groups and puts the rest on one line', async () => {
    const env = configured();
    await sendDigest(env, NOW, telegram().fetcher);
    for (let index = 0; index < 12; index++) setVisits(env, index < 10 ? 2 : 1, { referrer: `site-${index}.com` });
    const bot = telegram();
    await sendDigest(env, NOW + HOUR, bot.fetcher);
    const lines = bot.texts[0].split('\n');
    expect(lines[2]).toBe('Visits: 22');
    expect(lines).toHaveLength(3 + 10 + 1);
    expect(lines.at(-1)).toBe('2 more');
  });

  it('runs the snapshot before the digest, and runs the digest when the snapshot fails', async () => {
    const env = configured();
    await sendDigest(env, NOW, telegram().fetcher);
    setVisits(env, 1);
    const texts: string[] = [];
    const fetcher = (async (input: RequestInfo, init?: RequestInit) => {
      if (String(input).startsWith('https://api.github.com/')) return new Response('limit', { status: 403 });
      texts.push(JSON.parse(String(init?.body)).text);
      return Response.json({ ok: true });
    }) as typeof fetch;
    await expect(runHourly(env, NOW + HOUR, fetcher)).rejects.toThrow('GitHub API returned 403');
    expect(texts[0]).toContain('Visits: 1');
  });
});

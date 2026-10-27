import { describe, expect, it } from 'vitest';
import { loadAppUsage } from '../src/app-usage';
import { SETUP_ASSET, loadDownloads } from '../src/downloads';
import { loadFeedback } from '../src/feedback';
import { dataStart, makePeriod, parseDate } from '../src/period';
import { loadSite } from '../src/site';
import { loadUserBase, saveAppDays } from '../src/user-base';
import { NOW, addBotRequests, addPageView, addReport, addSession, addSnapshot, at, makeEnv } from './fixtures';

// NOW is 2026-10-06. The 7-day period is 2026-09-30 to 2026-10-06. The data starts on 2026-10-03.
const START = '2026-10-03';
const period = makePeriod(7, NOW);

describe('data start', () => {
  it('reads a valid date only', () => {
    expect(dataStart(START)).toBe(parseDate(START));
    expect(dataStart(undefined)).toBe(0);
    expect(dataStart('')).toBe(0);
    expect(dataStart('2026-02-30')).toBe(0);
    expect(dataStart('13.10.2026')).toBe(0);
  });

  it('ignores the sessions before the start', async () => {
    const env = makeEnv({ DATA_START: START });
    addSession(env, { receivedAt: at('2026-10-02'), counters: { session_n_1: 1, unclean_exit: 1 } });
    addSession(env, { receivedAt: at('2026-10-04'), counters: { session_n_1: 1 } });

    const app = await loadAppUsage(env, period, false);

    expect(app.totals.sessions).toBe(1);
    expect(app.totals.unclean_exits).toBe(0);
    expect(app.prevTotals.sessions).toBe(0);
    expect(app.versions).toEqual([expect.objectContaining({ version: '1.0.0', sessions: 1 })]);
    expect(app.sessionShape.sessions).toBe(1);
  });

  it('ignores the old days of the user base, also the days that the cockpit saved before', async () => {
    const env = makeEnv({ DATA_START: START });
    env.cockpit.db.prepare(`INSERT INTO app_daily (day, dev, sessions, session_n_1, session_n_2_5, session_n_6_20, session_n_21p, taken_at)
      VALUES ('2026-10-01', 0, 9, 9, 0, 0, 0, 0)`).run();
    addSession(env, { receivedAt: at('2026-10-02'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-04'), counters: { session_n_1: 1 } });

    await saveAppDays(env, NOW);
    const users = await loadUserBase(env, period, false);

    expect(env.cockpit.db.prepare('SELECT day FROM app_daily WHERE dev = 0 ORDER BY day').all())
      .toEqual([{ day: '2026-10-01' }, { day: '2026-10-04' }]);
    expect(users.since).toBe('2026-10-04');
    expect(users.total.session_n_1).toBe(1);
    expect(users.installsBefore).toBe(0);
  });

  it('ignores the reports before the start', async () => {
    const env = makeEnv({ DATA_START: START });
    addReport(env, { receivedAt: at('2026-10-02'), error: 'Old error' });
    addReport(env, { receivedAt: at('2026-10-04') });

    const feedback = await loadFeedback(env, period, false);

    expect(feedback.total).toBe(1);
    expect(feedback.reports).toHaveLength(1);
    expect(feedback.topErrors).toEqual([]);
  });

  it('ignores the page views and the bot requests before the start', async () => {
    const env = makeEnv({ DATA_START: START });
    addPageView(env, { date: '2026-10-02', views: 5, visits: 4 });
    addPageView(env, { date: '2026-10-04', views: 3, visits: 2 });
    addBotRequests(env, { date: '2026-10-02', bot: 'Googlebot', requests: 7 });
    addBotRequests(env, { date: '2026-10-04', bot: 'Googlebot', requests: 1 });

    const site = await loadSite(env, period);

    expect(site.totals).toEqual({ visits: 2, pageViews: 3 });
    expect(site.countingSince).toBe('2026-10-04');
    expect(site.bots.total).toBe(1);
    expect(site.bots.countingSince).toBe('2026-10-04');
  });

  it('counts the downloads from the start, with the last older snapshot as the base line', async () => {
    const env = makeEnv({ DATA_START: START });
    addSnapshot(env, '2026-10-01', { [SETUP_ASSET]: 10 });
    addSnapshot(env, '2026-10-02', { [SETUP_ASSET]: 15 });
    addSnapshot(env, '2026-10-04', { [SETUP_ASSET]: 18 });
    addSnapshot(env, '2026-10-05', { [SETUP_ASSET]: 20 });

    const downloads = await loadDownloads(env, period);

    expect(downloads.trackingSince).toBe(START);
    expect(downloads.total.setup).toBe(5);
    expect(downloads.daily.filter(point => point.setup !== null)).toEqual([
      { date: '2026-10-04', setup: 3, other: 0 },
      { date: '2026-10-05', setup: 2, other: 0 },
    ]);
    expect(downloads.releases).toEqual([{ tag: 'v1.0.0', publishedAt: '2026-09-01T00:00:00Z', setup: 5, other: 0 }]);
  });
});

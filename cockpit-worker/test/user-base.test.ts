import { describe, expect, it } from 'vitest';
import { makePeriod } from '../src/period';
import { REWRITE_DAYS, loadUserBase, saveAppDays } from '../src/user-base';
import { DAY, NOW, addSession, at, makeEnv } from './fixtures';

describe('user base', () => {
  it('copies the complete days only, for release builds and for all builds', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-10-05'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-05'), version: '1.0.1-SNAPSHOT', counters: { session_n_2_5: 1 } });
    addSession(env, { receivedAt: at('2026-10-05'), version: 'synthetic-smoke', counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-06'), counters: { session_n_1: 1 } });

    expect(await saveAppDays(env, NOW)).toBe(2);

    expect(env.cockpit.db.prepare('SELECT day, dev, sessions, session_n_1, session_n_2_5 FROM app_daily ORDER BY dev').all()).toEqual([
      { day: '2026-10-05', dev: 0, sessions: 1, session_n_1: 1, session_n_2_5: 0 },
      { day: '2026-10-05', dev: 1, sessions: 2, session_n_1: 1, session_n_2_5: 1 },
    ]);
    expect(env.cockpit.db.prepare("SELECT ok, message FROM job_status WHERE name = 'app_daily'").get()).toEqual({ ok: 1, message: '2 day rows' });
  });

  it('writes the recent days again, and keeps the older days after the analytics Worker deletes their rows', async () => {
    const env = makeEnv();
    const oldDay = NOW - (REWRITE_DAYS + 5) * DAY;
    addSession(env, { receivedAt: oldDay, counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: oldDay, counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-04'), counters: { session_n_1: 1 } });
    await saveAppDays(env, NOW);

    // The analytics Worker deletes one row of the old day. The recent day gets one more row.
    env.analytics.db.prepare('DELETE FROM analytics_session WHERE first_received_at = ? AND session_id = (SELECT MIN(session_id) FROM analytics_session WHERE first_received_at = ?)').run(oldDay, oldDay);
    addSession(env, { receivedAt: at('2026-10-04'), counters: { session_n_6_20: 1 } });
    await saveAppDays(env, NOW + 3_600_000);

    expect(env.cockpit.db.prepare('SELECT day, sessions FROM app_daily WHERE dev = 0 ORDER BY day').all()).toEqual([
      { day: new Date(oldDay).toISOString().slice(0, 10), sessions: 2 },
      { day: '2026-10-04', sessions: 2 },
    ]);
  });

  it('adds the saved days and the later days of the analytics database without a double count', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-09-01'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-09-20'), counters: { session_n_21p: 1 } });
    await saveAppDays(env, at('2026-10-01'));
    // The analytics Worker deleted the row of 09-01. The saved days still have it.
    env.analytics.db.prepare('DELETE FROM analytics_session WHERE first_received_at = ?').run(at('2026-09-01'));
    addSession(env, { receivedAt: at('2026-10-02'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-06'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-06'), counters: { session_n_2_5: 1 } });

    const users = await loadUserBase(env, makePeriod(7, NOW), false);

    expect(users.since).toBe('2026-09-01');
    expect(users.savedThrough).toBe('2026-09-20');
    expect(users.total).toEqual({ sessions: 5, session_n_1: 3, session_n_2_5: 1, session_n_6_20: 0, session_n_21p: 1 });
    expect(users.lastRun?.ok).toBe(true);
  });

  it('counts up to the end of the period only', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-09-01'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-05'), counters: { session_n_1: 1 } });
    await saveAppDays(env, NOW);

    const users = await loadUserBase(env, makePeriod(7, NOW, Date.parse('2026-09-30T00:00:00Z')), false);

    expect(users.total.session_n_1).toBe(1);
    expect(users.total.sessions).toBe(1);
  });

  it('uses the analytics database only before the first copy, and includes the development builds on request', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-10-06'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-06'), version: '1.0.1-SNAPSHOT', counters: { session_n_1: 1 } });

    const users = await loadUserBase(env, makePeriod(1, NOW), false);
    expect(users.savedThrough).toBeNull();
    expect(users.lastRun).toBeNull();
    expect(users.total.session_n_1).toBe(1);
    expect((await loadUserBase(env, makePeriod(1, NOW), true)).total.session_n_1).toBe(2);
  });

  it('keeps the error of a failed copy for the page', async () => {
    const env = makeEnv();
    env.ANALYTICS_DB = { prepare: () => { throw new Error('D1 is not available'); } } as unknown as D1Database;
    await expect(saveAppDays(env, NOW)).rejects.toThrow('D1 is not available');
    expect(env.cockpit.db.prepare("SELECT ok, message FROM job_status WHERE name = 'app_daily'").get()).toEqual({ ok: 0, message: 'D1 is not available' });
  });
});

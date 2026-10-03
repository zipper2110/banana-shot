import { beforeEach, describe, expect, it } from 'vitest';
import { runRetention } from '../src/retention';
import { FakeD1 } from './fake-d1';

const NOW = Date.UTC(2026, 9, 3, 3, 0, 0);
const HOUR = 60 * 60 * 1000;
const DAYS_90 = 90 * 24 * HOUR;

let db: FakeD1;

function addSession(sessionId: string, lastReceivedAt: number) {
  db.sessions.set(sessionId, { session_id: sessionId, first_received_at: 0, last_received_at: lastReceivedAt });
}

beforeEach(() => {
  db = new FakeD1();
});

describe('runRetention', () => {
  it('deletes sessions with a last summary older than 90 days and keeps the others', async () => {
    addSession('older', NOW - DAYS_90 - 1);
    addSession('at-cutoff', NOW - DAYS_90);
    addSession('new', NOW - 1000);
    await runRetention(db.asD1(), NOW);
    expect(db.sessionRows().map(row => row.session_id)).toEqual(['at-cutoff', 'new']);
  });

  it('keeps a session that started long ago when its last summary is new', async () => {
    db.sessions.set('long', { session_id: 'long', first_received_at: NOW - 2 * DAYS_90, last_received_at: NOW - HOUR });
    await runRetention(db.asD1(), NOW);
    expect(db.sessionRows()).toHaveLength(1);
  });

  it('deletes the rate rows of the previous hours', async () => {
    const hour = NOW / HOUR;
    db.rates.set('old', { id: 'old', hour: hour - 1, count: 5 });
    db.rates.set('current', { id: 'current', hour, count: 5 });
    await runRetention(db.asD1(), NOW);
    expect([...db.rates.keys()]).toEqual(['current']);
  });

  it('writes the status with the deleted count and the oldest kept session', async () => {
    addSession('a', NOW - DAYS_90 - 5);
    addSession('b', NOW - DAYS_90 - 6);
    addSession('c', NOW - 2000);
    await runRetention(db.asD1(), NOW);
    expect(db.retentionStatus).toEqual({ id: 1, ran_at: NOW, deleted_count: 2, oldest_received_at: NOW - 2000 });
  });

  it('writes no oldest session when the table is empty', async () => {
    await runRetention(db.asD1(), NOW);
    expect(db.retentionStatus).toEqual({ id: 1, ran_at: NOW, deleted_count: 0, oldest_received_at: null });
  });

  it('replaces the status of the previous run', async () => {
    addSession('a', NOW - DAYS_90 - 1);
    await runRetention(db.asD1(), NOW);
    await runRetention(db.asD1(), NOW + 1000);
    expect(db.retentionStatus).toEqual({ id: 1, ran_at: NOW + 1000, deleted_count: 0, oldest_received_at: null });
  });
});

import { beforeEach, describe, expect, it } from 'vitest';
import { runRetention } from '../src/retention';
import { FakeD1 } from './fake-d1';

const NOW = Date.UTC(2026, 9, 3, 3, 0, 0);
const DAYS_90 = 90 * 24 * 60 * 60 * 1000;

let db: FakeD1;

function addEvent(sessionId: string, receivedAt: number) {
  db.events.set(`${sessionId}|0`, { session_id: sessionId, sequence_number: 0, received_at: receivedAt });
}

beforeEach(() => {
  db = new FakeD1();
});

describe('runRetention', () => {
  it('deletes events older than 90 days and keeps the others', async () => {
    addEvent('older', NOW - DAYS_90 - 1);
    addEvent('at-cutoff', NOW - DAYS_90);
    addEvent('new', NOW - 1000);
    await runRetention(db.asD1(), NOW);
    expect(db.eventRows().map(row => row.session_id)).toEqual(['at-cutoff', 'new']);
  });

  it('writes the status with the deleted count and the oldest kept event', async () => {
    addEvent('a', NOW - DAYS_90 - 5);
    addEvent('b', NOW - DAYS_90 - 6);
    addEvent('c', NOW - 2000);
    await runRetention(db.asD1(), NOW);
    expect(db.retentionStatus).toEqual({ id: 1, ran_at: NOW, deleted_count: 2, oldest_received_at: NOW - 2000, consecutive_failures: 0 });
  });

  it('writes no oldest event when the table is empty', async () => {
    await runRetention(db.asD1(), NOW);
    expect(db.retentionStatus).toEqual({ id: 1, ran_at: NOW, deleted_count: 0, oldest_received_at: null, consecutive_failures: 0 });
  });

  it('replaces the status of the previous run', async () => {
    addEvent('a', NOW - DAYS_90 - 1);
    await runRetention(db.asD1(), NOW);
    await runRetention(db.asD1(), NOW + 1000);
    expect(db.retentionStatus).toEqual({ id: 1, ran_at: NOW + 1000, deleted_count: 0, oldest_received_at: null, consecutive_failures: 0 });
  });
});

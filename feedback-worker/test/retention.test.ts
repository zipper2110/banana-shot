import { beforeEach, describe, expect, it } from 'vitest';
import { runRetention } from '../src/retention';
import { FakeD1 } from './fake-d1';

const NOW = Date.UTC(2026, 9, 3, 3, 15, 0);
const DAYS_90 = 90 * 24 * 60 * 60 * 1000;
const HOUR = 60 * 60 * 1000;

let db: FakeD1;

function addReport(id: string, receivedAt: number) {
  db.reports.set(id, { report_id: id, received_at: receivedAt });
}

beforeEach(() => {
  db = new FakeD1();
});

describe('runRetention', () => {
  it('deletes reports older than 90 days and keeps the others', async () => {
    addReport('older', NOW - DAYS_90 - 1);
    addReport('at-cutoff', NOW - DAYS_90);
    addReport('new', NOW - 1000);
    await runRetention(db.asD1(), NOW);
    expect(db.reportRows().map(row => row.report_id)).toEqual(['at-cutoff', 'new']);
  });

  it('deletes the rate counts of the previous hours', async () => {
    const hour = Math.floor(NOW / HOUR);
    db.rates.set('old', { id: 'old', hour: hour - 1, count: 3 });
    db.rates.set('current', { id: 'current', hour, count: 1 });
    await runRetention(db.asD1(), NOW);
    expect([...db.rates.keys()]).toEqual(['current']);
  });
});

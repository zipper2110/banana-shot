/// <reference types="node" />
import type { Env } from '../src/env';
import { MIGRATIONS, SqliteD1 } from './sqlite-d1';

export const NOW = Date.parse('2026-10-06T12:00:00Z');
export const DAY = 86_400_000;

export function at(date: string, hour = 10): number {
  return Date.parse(`${date}T${String(hour).padStart(2, '0')}:00:00Z`);
}

export interface TestEnv extends Env {
  analytics: SqliteD1;
  feedback: SqliteD1;
  cockpit: SqliteD1;
}

export function makeEnv(overrides: Partial<Env> = {}): TestEnv {
  const analytics = new SqliteD1(MIGRATIONS.analytics);
  const feedback = new SqliteD1(MIGRATIONS.feedback);
  const cockpit = new SqliteD1(MIGRATIONS.cockpit);
  return {
    analytics, feedback, cockpit,
    ANALYTICS_DB: analytics.asD1(),
    FEEDBACK_DB: feedback.asD1(),
    COCKPIT_DB: cockpit.asD1(),
    ASSETS: { fetch: async () => new Response('<!doctype html><title>Cockpit</title>', { headers: { 'content-type': 'text/html' } }) } as unknown as Fetcher,
    COCKPIT_PASSWORD: 'secret',
    GITHUB_REPO: 'owner/repo',
    ...overrides,
  };
}

let sessionNumber = 0;

export function addSession(env: TestEnv, values: {
  receivedAt: number; version?: string; level?: 'essential' | 'extended'; os?: string; activeS?: number; counters?: Record<string, number>;
}) {
  sessionNumber++;
  env.analytics.db.prepare(`INSERT INTO analytics_session (session_id, first_received_at, last_received_at, schema_version, notice_version, level,
    app_version, os_family, snapshot, final, duration_s, active_s, counters) VALUES (?, ?, ?, 2, 1, ?, ?, ?, 1, 1, ?, ?, ?)`).run(
    `session-${sessionNumber}`, values.receivedAt, values.receivedAt, values.level ?? 'extended', values.version ?? '1.0.0',
    values.os ?? 'windows', (values.activeS ?? 300) + 60, values.activeS ?? 300, JSON.stringify(values.counters ?? {}));
}

let reportNumber = 0;

export function addReport(env: TestEnv, values: {
  receivedAt: number; topic?: string; message?: string; email?: string; version?: string; error?: string; delivered?: boolean;
}) {
  reportNumber++;
  env.feedback.db.prepare(`INSERT INTO feedback_report (report_id, received_at, topic, message, email, app_version, os_name, os_version,
    java_version, error, has_log, delivered) VALUES (?, ?, ?, ?, ?, ?, 'Windows 11', '10.0', '25', ?, 0, ?)`).run(
    `report-${reportNumber}`, values.receivedAt, values.topic ?? 'problem', values.message ?? 'Text', values.email ?? null,
    values.version ?? '1.0.0', values.error ?? null, values.delivered === false ? 0 : 1);
}

export function addSnapshot(env: TestEnv, day: string, files: Record<string, number>, tag = 'v1.0.0') {
  for (const [name, count] of Object.entries(files)) {
    env.cockpit.db.prepare(`INSERT INTO download_snapshot (day, release_tag, asset_name, download_count, published_at, taken_at)
      VALUES (?, ?, ?, ?, '2026-09-01T00:00:00Z', 0)`).run(day, tag, name, count);
  }
}

export function basicAuth(password: string): string {
  return `Basic ${btoa(`cockpit:${password}`)}`;
}

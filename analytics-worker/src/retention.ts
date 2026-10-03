import { HOUR } from './rate-limit';

/** A session row stays for 90 days after its last summary (docs/analytics/design.md). */
export const RETENTION_DAYS = 90;

export async function runRetention(db: D1Database, now = Date.now()): Promise<void> {
  const cutoff = now - RETENTION_DAYS * 24 * HOUR;
  const deleted = await db.prepare('DELETE FROM analytics_session WHERE last_received_at < ?').bind(cutoff).run();
  await db.prepare('DELETE FROM analytics_rate WHERE hour < ?').bind(Math.floor(now / HOUR)).run();
  const oldest = await db.prepare('SELECT MIN(last_received_at) AS oldest FROM analytics_session').first<{ oldest: number | null }>();
  await db.prepare('INSERT INTO analytics_retention_status (id,ran_at,deleted_count,oldest_received_at) VALUES (1,?,?,?) ON CONFLICT(id) DO UPDATE SET ran_at=excluded.ran_at, deleted_count=excluded.deleted_count, oldest_received_at=excluded.oldest_received_at').bind(now, deleted.meta.changes, oldest?.oldest ?? null).run();
}

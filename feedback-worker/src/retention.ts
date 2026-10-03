/** A report row stays for 90 days (decision 11 of B-8), the same as the analytics retention. */
export const RETENTION_DAYS = 90;

export async function runRetention(db: D1Database, now = Date.now()): Promise<void> {
  const cutoff = now - RETENTION_DAYS * 24 * 60 * 60 * 1000;
  await db.prepare('DELETE FROM feedback_report WHERE received_at < ?').bind(cutoff).run();
  await db.prepare('DELETE FROM feedback_rate WHERE hour < ?').bind(Math.floor(now / (60 * 60 * 1000))).run();
}

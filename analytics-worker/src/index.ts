import { allow, HOUR } from './rate-limit';
import { runRetention } from './retention';
import { validateSummary, type Summary } from './validation';

export interface Env {
  ANALYTICS_INGESTION_ENABLED: string;
  ANALYTICS_DB: D1Database;
  RATE_LIMIT_KEY?: string;
}

/** The largest request body: 8 KiB. A summary with all counter keys is about 2 KiB. */
export const MAX_BODY_BYTES = 8 * 1024;

const status = (code: number) => new Response(null, { status: code });

/**
 * Inserts the first summary of a session. A later summary replaces the row only when its snapshot is higher.
 * `first_received_at` does not change. Thus an essential summary after a change from the extended level also
 * replaces the extended counters.
 */
const UPSERT = 'INSERT INTO analytics_session (session_id,first_received_at,last_received_at,schema_version,notice_version,level,app_version,os_family,snapshot,final,duration_s,active_s,counters) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) '
  + 'ON CONFLICT(session_id) DO UPDATE SET last_received_at=excluded.last_received_at, schema_version=excluded.schema_version, notice_version=excluded.notice_version, level=excluded.level, '
  + 'app_version=excluded.app_version, os_family=excluded.os_family, snapshot=excluded.snapshot, final=excluded.final, duration_s=excluded.duration_s, '
  + 'active_s=excluded.active_s, counters=excluded.counters WHERE excluded.snapshot > analytics_session.snapshot';

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (env.ANALYTICS_INGESTION_ENABLED !== 'true') return status(410);
    if (new URL(request.url).pathname !== '/v1/session') return status(404);
    if (request.method !== 'POST') return status(405);
    if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return status(415);
    if (Number(request.headers.get('content-length') ?? 0) > MAX_BODY_BYTES) return status(413);
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength > MAX_BODY_BYTES) return status(413);
    const result = (() => {
      try { return validateSummary(JSON.parse(new TextDecoder().decode(bytes))); } catch { return { status: 400 } as const; }
    })();
    if ('status' in result) return status(result.status);
    try {
      return await store(result.summary, request, env);
    } catch (failure) {
      console.error(`Summary not stored: ${failure instanceof Error ? failure.message : 'unknown failure'}`);
      return status(503);
    }
  },
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    await runRetention(env.ANALYTICS_DB);
  },
} satisfies ExportedHandler<Env>;

async function store(summary: Summary, request: Request, env: Env): Promise<Response> {
  const db = env.ANALYTICS_DB;
  if (!env.RATE_LIMIT_KEY) throw new Error('RATE_LIMIT_KEY is not set');
  if (!await allow(db, env.RATE_LIMIT_KEY, request.headers.get('cf-connecting-ip') ?? 'unknown')) return status(429);
  // The server keeps no exact clock time: both times are rounded down to the hour.
  const hour = Math.floor(Date.now() / HOUR) * HOUR;
  await db.prepare(UPSERT).bind(
    summary.session_id, hour, hour, summary.schema_version, summary.notice_version, summary.level, summary.app_version, summary.os_family,
    summary.snapshot, summary.final ? 1 : 0, summary.duration_s, summary.active_s, JSON.stringify(summary.counters),
  ).run();
  return status(204);
}

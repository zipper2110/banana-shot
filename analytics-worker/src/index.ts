import { WorkerEntrypoint } from 'cloudflare:workers';
import { allow, HOUR } from './rate-limit';
import { runRetention } from './retention';
import { countBotRequest, countPageView, SITE_PATH } from './site';
import { MAX_BODY_BYTES, validateSummary, type Summary } from './validation';

// This module has an RPC entrypoint, so it can export only handlers. The runtime refuses other values. Thus the
// constants are in other modules.
export interface Env {
  ANALYTICS_INGESTION_ENABLED: string;
  ANALYTICS_DB: D1Database;
  RATE_LIMIT_KEY?: string;
}

const status = (code: number) => new Response(null, { status: code });

/**
 * Inserts the first summary of a session. A later summary replaces the row only when its snapshot is higher.
 * `first_received_at` does not change. Thus an essential summary after a change from the extended level also
 * replaces the extended counters.
 */
const UPSERT = 'INSERT INTO analytics_session (session_id,first_received_at,last_received_at,schema_version,notice_version,level,app_version,os_family,snapshot,final,duration_s,active_s,counters,attributes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) '
  + 'ON CONFLICT(session_id) DO UPDATE SET last_received_at=excluded.last_received_at, schema_version=excluded.schema_version, notice_version=excluded.notice_version, level=excluded.level, '
  + 'app_version=excluded.app_version, os_family=excluded.os_family, snapshot=excluded.snapshot, final=excluded.final, duration_s=excluded.duration_s, '
  + 'active_s=excluded.active_s, counters=excluded.counters, attributes=excluded.attributes WHERE excluded.snapshot > analytics_session.snapshot';

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (env.ANALYTICS_INGESTION_ENABLED !== 'true') return status(410);
    const path = new URL(request.url).pathname;
    if (path === SITE_PATH) return countPageView(request, env.ANALYTICS_DB, env.RATE_LIMIT_KEY);
    if (path !== '/v1/session') return status(404);
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

/**
 * The RPC entrypoint for the site Worker (service binding `ANALYTICS` in site/wrangler.toml). It is not on the public
 * URL, so only the site Worker can call it.
 */
export class SiteCounter extends WorkerEntrypoint<Env> {
  /** Counts one HTML request of the site when it comes from a bot. A request of a person is not stored. */
  async pageRequest(path: string, status: number, userAgent: string): Promise<void> {
    if (this.env.ANALYTICS_INGESTION_ENABLED !== 'true') return;
    try {
      await countBotRequest(this.env.ANALYTICS_DB, path, status, userAgent);
    } catch (failure) {
      console.error(`Bot request not stored: ${failure instanceof Error ? failure.message : 'unknown failure'}`);
    }
  }
}

async function store(summary: Summary, request: Request, env: Env): Promise<Response> {
  const db = env.ANALYTICS_DB;
  if (!env.RATE_LIMIT_KEY) throw new Error('RATE_LIMIT_KEY is not set');
  if (!await allow(db, env.RATE_LIMIT_KEY, request.headers.get('cf-connecting-ip') ?? 'unknown')) return status(429);
  // The server keeps no exact clock time: both times are rounded down to the hour.
  const hour = Math.floor(Date.now() / HOUR) * HOUR;
  await db.prepare(UPSERT).bind(
    summary.session_id, hour, hour, summary.schema_version, summary.notice_version, summary.level, summary.app_version, summary.os_family,
    summary.snapshot, summary.final ? 1 : 0, summary.duration_s, summary.active_s, JSON.stringify(summary.counters),
    JSON.stringify(summary.attributes),
  ).run();
  return status(204);
}

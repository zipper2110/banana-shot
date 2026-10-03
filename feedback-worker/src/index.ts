import { allow } from './rate-limit';
import { runRetention } from './retention';
import { deliver } from './telegram';
import { validateReport, type Report } from './validation';

export interface Env {
  FEEDBACK_INGESTION_ENABLED: string;
  FEEDBACK_DB: D1Database;
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  RATE_LIMIT_KEY?: string;
}

/** The largest request body: 3 MB. A gzip log of 2 MB text is about 200 KB, so a real report is much smaller. */
export const MAX_BODY_BYTES = 3 * 1024 * 1024;

const error = (code: string, status: number) => Response.json({ error: code }, { status });

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (env.FEEDBACK_INGESTION_ENABLED !== 'true') return error('disabled', 410);
    if (new URL(request.url).pathname !== '/v1/feedback') return new Response(null, { status: 404 });
    if (request.method !== 'POST') return new Response(null, { status: 405 });
    if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return error('not_json', 415);
    if (Number(request.headers.get('content-length') ?? 0) > MAX_BODY_BYTES) return error('too_large', 413);
    const bytes = await request.arrayBuffer();
    if (bytes.byteLength > MAX_BODY_BYTES) return error('too_large', 413);
    const report = (() => { try { return validateReport(JSON.parse(new TextDecoder().decode(bytes))); } catch { return null; } })();
    if (!report) return error('invalid', 422);
    try {
      return await receive(report, request, env);
    } catch (failure) {
      console.error(`Report ${report.report_id}: ${failure instanceof Error ? failure.message : 'unknown failure'}`);
      return error('unavailable', 503);
    }
  },
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    await runRetention(env.FEEDBACK_DB);
  },
} satisfies ExportedHandler<Env>;

/**
 * Keeps the report and sends it to the author. A report that Telegram got before returns 200 and changes nothing.
 * A report that Telegram did not get goes to Telegram again, because only the new request has the log.
 */
async function receive(report: Report, request: Request, env: Env): Promise<Response> {
  const db = env.FEEDBACK_DB;
  const id = { report_id: report.report_id };
  const stored = await db.prepare('SELECT delivered FROM feedback_report WHERE report_id = ?').bind(report.report_id).first<{ delivered: number }>();
  if (stored?.delivered === 1) return Response.json(id, { status: 200 });
  if (!env.RATE_LIMIT_KEY) throw new Error('RATE_LIMIT_KEY is not set');
  if (!await allow(db, env.RATE_LIMIT_KEY, request.headers.get('cf-connecting-ip') ?? 'unknown')) return error('rate_limited', 429);
  if (!stored) {
    await db.prepare('INSERT OR IGNORE INTO feedback_report (report_id,received_at,topic,message,email,app_version,os_name,os_version,java_version,error,has_log,delivered) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
      .bind(report.report_id, Date.now(), report.topic, report.message, report.email ?? null, report.app_version, report.os_name, report.os_version, report.java_version, report.error ?? null, report.log ? 1 : 0, 0)
      .run();
  }
  if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) throw new Error('The Telegram secrets are not set');
  await deliver(report, { token: env.TELEGRAM_BOT_TOKEN, chatId: env.TELEGRAM_CHAT_ID });
  await db.prepare('UPDATE feedback_report SET delivered = 1 WHERE report_id = ?').bind(report.report_id).run();
  return Response.json(id, { status: 201 });
}

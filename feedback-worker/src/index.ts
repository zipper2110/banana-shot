import { allow } from './rate-limit';
import { runRetention } from './retention';
import { deliver } from './telegram';
import { validateReport, validateSiteReport, type Report } from './validation';

export interface Env {
  FEEDBACK_INGESTION_ENABLED: string;
  FEEDBACK_DB: D1Database;
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  RATE_LIMIT_KEY?: string;
}

/** The largest request body: 3 MB. A gzip log of 2 MB text is about 200 KB, so a real report is much smaller. */
export const MAX_BODY_BYTES = 3 * 1024 * 1024;

/** The path of the contact form on the website. Only this path answers the browser (CORS). */
export const SITE_PATH = '/v1/site-feedback';

/** The only origin that can send to [SITE_PATH]. */
export const SITE_ORIGIN = 'https://banana-shot-editor.app';

const error = (code: string, status: number) => Response.json({ error: code }, { status });

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (new URL(request.url).pathname === SITE_PATH) return withCors(await fromSite(request, env));
    if (env.FEEDBACK_INGESTION_ENABLED !== 'true') return error('disabled', 410);
    if (new URL(request.url).pathname !== '/v1/feedback') return new Response(null, { status: 404 });
    if (request.method !== 'POST') return new Response(null, { status: 405 });
    const body = await readJson(request);
    if (body instanceof Response) return body;
    const report = validateReport(body.value);
    if (!report) return error('invalid', 422);
    return receiveSafely(report, request, env);
  },
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    await runRetention(env.FEEDBACK_DB);
  },
} satisfies ExportedHandler<Env>;

/**
 * A report from the contact form of the website. Other origins get 403. The preflight request gets 204 also when
 * the kill switch is off, so the browser can read the 410 of the real request.
 */
async function fromSite(request: Request, env: Env): Promise<Response> {
  if (request.headers.get('origin') !== SITE_ORIGIN) return error('origin', 403);
  if (request.method === 'OPTIONS') return new Response(null, { status: 204 });
  if (env.FEEDBACK_INGESTION_ENABLED !== 'true') return error('disabled', 410);
  if (request.method !== 'POST') return new Response(null, { status: 405 });
  const body = await readJson(request);
  if (body instanceof Response) return body;
  const site = validateSiteReport(body.value);
  if (!site) return error('invalid', 422);
  // A bot gets the same answer as a person, so it does not learn about the trap.
  if (site.trapped) return Response.json({ report_id: site.report.report_id }, { status: 201 });
  return receiveSafely(site.report, request, env);
}

function withCors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set('access-control-allow-origin', SITE_ORIGIN);
  headers.set('access-control-allow-methods', 'POST');
  headers.set('access-control-allow-headers', 'content-type');
  headers.set('access-control-max-age', '86400');
  headers.set('vary', 'origin');
  return new Response(response.body, { status: response.status, headers });
}

/** The JSON value of the body, or the error response. A body that is not JSON gives the value `undefined`. */
async function readJson(request: Request): Promise<{ value: unknown } | Response> {
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) return error('not_json', 415);
  if (Number(request.headers.get('content-length') ?? 0) > MAX_BODY_BYTES) return error('too_large', 413);
  const bytes = await request.arrayBuffer();
  if (bytes.byteLength > MAX_BODY_BYTES) return error('too_large', 413);
  try {
    return { value: JSON.parse(new TextDecoder().decode(bytes)) };
  } catch {
    return { value: undefined };
  }
}

async function receiveSafely(report: Report, request: Request, env: Env): Promise<Response> {
  try {
    return await receive(report, request, env);
  } catch (failure) {
    console.error(`Report ${report.report_id}: ${failure instanceof Error ? failure.message : 'unknown failure'}`);
    return error('unavailable', 503);
  }
}

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

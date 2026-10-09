/** The checks of a report: `feedback-contract/v1`. */
export const TOPICS = ['problem', 'idea', 'question', 'other'] as const;
export type Topic = typeof TOPICS[number];

export type Report = {
  report_id: string;
  topic: Topic;
  message: string;
  email?: string;
  app_version: string;
  os_name: string;
  os_version: string;
  java_version: string;
  error?: string;
  log?: string;
  /** Set only by the Worker: `site` for a report from the contact form of the website. Not a key of the JSON. */
  source?: 'site';
};

export const MAX_MESSAGE = 10_000;
export const MAX_LOG_BASE64 = 2_900_000;

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const base64 = /^[A-Za-z0-9+/]+={0,2}$/;
const required = ['report_id', 'topic', 'message', 'app_version', 'os_name', 'os_version', 'java_version'];
const optional = ['email', 'error', 'log'];

/** Returns the report, or null when the value is not a valid report. */
export function validateReport(value: unknown): Report | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const r = value as Record<string, unknown>;
  if (!required.every(key => Object.hasOwn(r, key))) return null;
  if (!Object.keys(r).every(key => required.includes(key) || optional.includes(key))) return null;
  if (typeof r.report_id !== 'string' || !uuid.test(r.report_id)) return null;
  if (typeof r.topic !== 'string' || !(TOPICS as readonly string[]).includes(r.topic)) return null;
  if (!text(r.message, 1, MAX_MESSAGE) || (r.message as string).trim().length === 0) return null;
  if (!['app_version', 'os_name', 'os_version', 'java_version'].every(key => text(r[key], 1, 100))) return null;
  if (Object.hasOwn(r, 'email') && !(text(r.email, 3, 254) && email.test(r.email as string))) return null;
  if (Object.hasOwn(r, 'error') && !text(r.error, 1, MAX_MESSAGE)) return null;
  if (Object.hasOwn(r, 'log') && !(text(r.log, 4, MAX_LOG_BASE64) && base64.test(r.log as string) && isGzip(r.log as string))) return null;
  return r as Report;
}

/** The hidden field of the site form. People do not see it, so a value in it comes from a bot. */
export const SITE_TRAP = 'trap';

/** The `app_version` of a site report in D1. The site sends no app data, so the other app fields are empty. */
export const SITE_APP_VERSION = 'website';

const siteKeys = ['report_id', 'topic', 'message', 'email', SITE_TRAP];

/**
 * Returns the report of the site form, or null when the value is not valid. `trapped` is true when the hidden field
 * has a value. The site sends only the topic, the message, and the email address: no app data, no error, no log.
 */
export function validateSiteReport(value: unknown): { report: Report; trapped: boolean } | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  if (!Object.keys(value).every(key => siteKeys.includes(key))) return null;
  const { [SITE_TRAP]: trap, ...fields } = value as Record<string, unknown>;
  if (trap !== undefined && !text(trap, 0, 1000)) return null;
  // The app checks are the same for the topic, the message, and the email. The placeholders only pass these checks.
  const report = validateReport({ ...fields, app_version: SITE_APP_VERSION, os_name: '-', os_version: '-', java_version: '-' });
  if (!report) return null;
  return { report: { ...report, os_name: '', os_version: '', java_version: '', source: 'site' }, trapped: typeof trap === 'string' && trap !== '' };
}

function text(value: unknown, min: number, max: number): boolean {
  return typeof value === 'string' && value.length >= min && value.length <= max;
}

function isGzip(log: string): boolean {
  const bytes = decodeBase64(log);
  return bytes !== null && bytes.length >= 2 && bytes[0] === 0x1f && bytes[1] === 0x8b;
}

/** The bytes of a Base64 text, or null when the text is not Base64. */
export function decodeBase64(value: string): Uint8Array | null {
  try {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  } catch {
    return null;
  }
}

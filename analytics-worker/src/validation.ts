import { COUNTER_KEYS, MAX_COUNTER_VALUE } from './counters';

/** The schema version of this Worker. A higher version is invalid (400). */
export const SCHEMA_VERSION = 1;
/** The lowest schema version that the Worker accepts. A lower version gets 410, so that an old app stops sending. */
export const MIN_SCHEMA_VERSION = 1;
export const NOTICE_VERSIONS: ReadonlySet<number> = new Set([1]);
export const MAX_SNAPSHOT = 1_000_000;
/** 7 days. The app clamps `duration_s` and `active_s` to this value. */
export const MAX_SECONDS = 604_800;

const KEYS = ['schema_version', 'notice_version', 'session_id', 'os_family', 'snapshot', 'final', 'duration_s', 'active_s', 'counters'];
const OPTIONAL_KEYS = ['app_version'];
const OS_FAMILIES = ['windows', 'macos', 'linux', 'other'];
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export type Summary = {
  schema_version: number;
  notice_version: number;
  session_id: string;
  app_version: string;
  os_family: string;
  snapshot: number;
  final: boolean;
  duration_s: number;
  active_s: number;
  counters: Record<string, number>;
};

export type Validation = { summary: Summary } | { status: 400 | 410 };

/** Checks a parsed request body. The result never contains a value from an invalid body. */
export function validateSummary(value: unknown, minSchemaVersion = MIN_SCHEMA_VERSION): Validation {
  const invalid = { status: 400 } as const;
  if (!isObject(value)) return invalid;
  const schema = value.schema_version;
  if (isInteger(schema, 1, minSchemaVersion - 1)) return { status: 410 };
  if (!exactKeys(value, KEYS, OPTIONAL_KEYS)) return invalid;
  if (!isInteger(schema, minSchemaVersion, SCHEMA_VERSION)) return invalid;
  if (typeof value.notice_version !== 'number' || !NOTICE_VERSIONS.has(value.notice_version)) return invalid;
  if (typeof value.session_id !== 'string' || !UUID_V4.test(value.session_id)) return invalid;
  if (typeof value.os_family !== 'string' || !OS_FAMILIES.includes(value.os_family)) return invalid;
  if (!isInteger(value.snapshot, 0, MAX_SNAPSHOT) || typeof value.final !== 'boolean') return invalid;
  if (!isInteger(value.duration_s, 0, MAX_SECONDS) || !isInteger(value.active_s, 0, MAX_SECONDS)) return invalid;
  if (!validCounters(value.counters)) return invalid;
  return {
    summary: {
      schema_version: schema as number,
      notice_version: value.notice_version,
      session_id: value.session_id,
      app_version: appVersionText(value.app_version),
      os_family: value.os_family,
      snapshot: value.snapshot as number,
      final: value.final,
      duration_s: value.duration_s as number,
      active_s: value.active_s as number,
      counters: value.counters as Record<string, number>,
    },
  };
}

/** Accept all app version values, so that no build loses its data. A missing or null value is "unknown". */
export function appVersionText(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value === undefined || value === null) return 'unknown';
  return JSON.stringify(value);
}

function validCounters(value: unknown): boolean {
  if (!isObject(value)) return false;
  return Object.entries(value).every(([key, count]) => COUNTER_KEYS.has(key) && isInteger(count, 0, MAX_COUNTER_VALUE));
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isInteger(value: unknown, min: number, max: number): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= min && value <= max;
}

function exactKeys(value: Record<string, unknown>, keys: string[], optional: string[]): boolean {
  return keys.every(key => Object.hasOwn(value, key)) && Object.keys(value).every(key => keys.includes(key) || optional.includes(key));
}

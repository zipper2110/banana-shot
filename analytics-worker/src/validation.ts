import { ESSENTIAL_ATTRIBUTES, SCHEMA_4_ATTRIBUTES, validAttributes, type Attributes } from './attributes';
import { COUNTER_KEYS, ESSENTIAL_COUNTER_KEYS, MAX_COUNTER_VALUE } from './counters';

/** The largest request body: 8 KiB. A summary with all counter keys is about 2 KiB. */
export const MAX_BODY_BYTES = 8 * 1024;

/**
 * The schema version of this Worker. A higher version is invalid (400). Version 2 added `level`, version 3 `attributes`,
 * and version 4 the language attributes on the essential level (B-11 E2).
 */
export const SCHEMA_VERSION = 4;
/** The lowest schema version that the Worker accepts. A lower version gets 410, so that an old app stops sending. */
export const MIN_SCHEMA_VERSION = 2;
/**
 * The notice version of each schema version. Version 3 sends the attributes, which notice version 2 tells about.
 * Version 4 sends the language attributes also on the essential level, which notice version 3 tells about.
 */
export const NOTICE_VERSION_OF_SCHEMA: Readonly<Record<number, number>> = { 2: 1, 3: 2, 4: 3 };
export const MAX_SNAPSHOT = 1_000_000;
/** 7 days. The app clamps `duration_s` and `active_s` to this value. */
export const MAX_SECONDS = 604_800;

const KEYS = ['schema_version', 'notice_version', 'level', 'session_id', 'os_family', 'snapshot', 'final', 'duration_s', 'active_s', 'counters'];
const OPTIONAL_KEYS = ['app_version', 'attributes'];
const OS_FAMILIES = ['windows', 'macos', 'linux', 'other'];
/** The level of the statistics. An essential summary can contain only the essential counter keys. */
const LEVELS: Record<string, ReadonlySet<string>> = { essential: ESSENTIAL_COUNTER_KEYS, extended: COUNTER_KEYS };
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export type Summary = {
  schema_version: number;
  notice_version: number;
  level: 'essential' | 'extended';
  session_id: string;
  app_version: string;
  os_family: string;
  snapshot: number;
  final: boolean;
  duration_s: number;
  active_s: number;
  counters: Record<string, number>;
  /** Empty for schema version 2. An essential summary has only the essential attributes. */
  attributes: Attributes;
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
  if (value.notice_version !== NOTICE_VERSION_OF_SCHEMA[schema as number]) return invalid;
  if (typeof value.level !== 'string' || !Object.hasOwn(LEVELS, value.level)) return invalid;
  if (typeof value.session_id !== 'string' || !UUID_V4.test(value.session_id)) return invalid;
  if (typeof value.os_family !== 'string' || !OS_FAMILIES.includes(value.os_family)) return invalid;
  if (!isInteger(value.snapshot, 0, MAX_SNAPSHOT) || typeof value.final !== 'boolean') return invalid;
  if (!isInteger(value.duration_s, 0, MAX_SECONDS) || !isInteger(value.active_s, 0, MAX_SECONDS)) return invalid;
  if (!validCounters(value.counters, LEVELS[value.level])) return invalid;
  let attributes: Attributes = {};
  if (Object.hasOwn(value, 'attributes')) {
    const allowed = allowedAttributes(schema as number, value.level);
    if (allowed === null) return invalid;
    const valid = validAttributes(value.attributes, allowed);
    if (valid === null) return invalid;
    attributes = valid;
  }
  return {
    summary: {
      schema_version: schema as number,
      notice_version: value.notice_version as number,
      level: value.level as Summary['level'],
      session_id: value.session_id,
      app_version: appVersionText(value.app_version),
      os_family: value.os_family,
      snapshot: value.snapshot as number,
      final: value.final,
      duration_s: value.duration_s as number,
      active_s: value.active_s as number,
      counters: value.counters as Record<string, number>,
      attributes,
    },
  };
}

/**
 * The attribute keys that a summary can contain, or null when it can contain no attributes. Schema version 2 has no
 * attributes. Schema version 3 has them only on the extended level, and not `os_language`. Schema version 4 has the
 * essential attributes on the essential level, and all attributes on the extended level.
 */
function allowedAttributes(schema: number, level: string): ((key: string) => boolean) | null {
  if (schema === 3) return level === 'extended' ? key => !SCHEMA_4_ATTRIBUTES.has(key) : null;
  if (schema >= 4) return level === 'extended' ? () => true : key => ESSENTIAL_ATTRIBUTES.has(key);
  return null;
}

/** Accept all app version values, so that no build loses its data. A missing or null value is "unknown". */
export function appVersionText(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value === undefined || value === null) return 'unknown';
  return JSON.stringify(value);
}

function validCounters(value: unknown, keys: ReadonlySet<string>): boolean {
  if (!isObject(value)) return false;
  return Object.entries(value).every(([key, count]) => keys.has(key) && isInteger(count, 0, MAX_COUNTER_VALUE));
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

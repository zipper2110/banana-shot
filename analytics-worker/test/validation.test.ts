import { describe, expect, it } from 'vitest';
import { ARRAY_ATTRIBUTES, ATTRIBUTE_VALUES } from '../src/attributes';
import { COUNTER_KEYS, ESSENTIAL_COUNTER_KEYS } from '../src/counters';
import { appVersionText, validateSummary } from '../src/validation';
import { bodyOf, contractArrayAttributes, contractAttributes, contractCounterKeys, contractEssentialCounterKeys, invalidSummaries, smokeSummary, summary, validSummaries } from './fixtures';

const accepted = (value: unknown) => 'summary' in validateSummary(value);

describe('counter keys', () => {
  it('are the keys of the contract', () => {
    expect([...COUNTER_KEYS].sort()).toEqual([...contractCounterKeys].sort());
    expect(new Set(contractCounterKeys).size).toBe(contractCounterKeys.length);
  });

  it('have the essential keys of the contract, and each essential key is a counter key', () => {
    expect([...ESSENTIAL_COUNTER_KEYS].sort()).toEqual([...contractEssentialCounterKeys].sort());
    expect([...ESSENTIAL_COUNTER_KEYS].every(key => COUNTER_KEYS.has(key))).toBe(true);
  });

  it('are all in one valid fixture', () => {
    const counts = validSummaries.map(value => Object.keys(value.counters as object).length);
    expect(Math.max(...counts)).toBe(COUNTER_KEYS.size);
  });
});

describe('attributes', () => {
  const v3 = (attributes: unknown, overrides: Record<string, unknown> = {}) =>
    summary({ schema_version: 3, notice_version: 2, attributes, ...overrides });

  it('are the attributes of the contract', () => {
    expect(ATTRIBUTE_VALUES).toEqual(contractAttributes);
    expect([...ARRAY_ATTRIBUTES].sort()).toEqual([...contractArrayAttributes].sort());
  });

  it('are all in one valid fixture, with all values of each array attribute', () => {
    const full = validSummaries.find(value => Object.keys((value.attributes ?? {}) as object).length === Object.keys(ATTRIBUTE_VALUES).length);
    expect(full).toBeDefined();
    for (const key of ARRAY_ATTRIBUTES) expect(((full!.attributes as Record<string, string[]>)[key]).length).toBe(ATTRIBUTE_VALUES[key].length);
  });

  it('returns the array values in the order of the list', () => {
    const result = validateSummary(v3({ sport: ['padel', 'tennis'], theme: 'mid' }));
    expect('summary' in result && result.summary.attributes).toEqual({ theme: 'mid', sport: ['tennis', 'padel'] });
  });

  it('returns no attributes for schema version 2, for an essential summary, and for a summary without them', () => {
    for (const value of [summary(), summary({ schema_version: 3, notice_version: 2 }), summary({ schema_version: 3, notice_version: 2, level: 'essential' })]) {
      const result = validateSummary(value);
      expect('summary' in result && result.summary.attributes).toEqual({});
    }
  });

  it.each(Object.entries(ATTRIBUTE_VALUES).filter(([key]) => !ARRAY_ATTRIBUTES.has(key)))('accepts each value of %s', (key, values) => {
    for (const value of values) expect(accepted(v3({ [key]: value }))).toBe(true);
  });

  it.each(['constructor', '__proto__', 'toString'])('refuses the attribute key "%s"', key => {
    expect(accepted(JSON.parse(`{"schema_version":3,"notice_version":2,"level":"extended","session_id":"00000000-0000-4000-8000-000000000201","os_family":"windows","snapshot":0,"final":false,"duration_s":0,"active_s":0,"counters":{},"attributes":{"${key}":"dark"}}`))).toBe(false);
  });

  it('refuses a theme value of the prototype', () => {
    expect(accepted(v3({ theme: 'constructor' }))).toBe(false);
  });
});

describe('validateSummary with the contract fixtures', () => {
  it.each(validSummaries.map((value, i) => [i, value]))('accepts valid summary %i', (_i, value) => {
    expect(accepted(value)).toBe(true);
  });

  it('accepts the smoke summary', () => {
    expect(accepted(smokeSummary)).toBe(true);
    expect(smokeSummary.app_version).toBe('synthetic-smoke');
  });

  it.each(invalidSummaries.map(invalid => [invalid.name, invalid]))('refuses "%s" with 400', (_name, invalid) => {
    const value = (() => { try { return JSON.parse(bodyOf(invalid)); } catch { return undefined; } })();
    expect(validateSummary(value)).toEqual({ status: 400 });
  });
});

describe('fields', () => {
  it('returns the values of a valid summary', () => {
    const value = summary({ snapshot: 3, final: true, duration_s: 912, active_s: 640, counters: { point_added: 31 } });
    expect(validateSummary(value)).toEqual({ summary: { ...value, app_version: '1.0.0', attributes: {} } });
  });

  it.each([
    ['snapshot', 1_000_000],
    ['duration_s', 604_800],
    ['active_s', 604_800],
  ])('accepts the highest %s', (key, max) => {
    expect(accepted(summary({ [key]: max }))).toBe(true);
    expect(accepted(summary({ [key]: max + 1 }))).toBe(false);
  });

  it('accepts a counter value of 0 and of 1000000', () => {
    expect(accepted(summary({ counters: { point_added: 0, help_opened: 1_000_000 } }))).toBe(true);
  });

  it.each(['windows', 'macos', 'linux', 'other'])('accepts the os family %s', os => {
    expect(accepted(summary({ os_family: os }))).toBe(true);
  });

  it('accepts each essential key in an essential summary, and refuses the other keys', () => {
    for (const key of COUNTER_KEYS) {
      expect(accepted(summary({ level: 'essential', counters: { [key]: 1 } }))).toBe(ESSENTIAL_COUNTER_KEYS.has(key));
      expect(accepted(summary({ level: 'extended', counters: { [key]: 1 } }))).toBe(true);
    }
  });

  it('returns the level', () => {
    const result = validateSummary(summary({ level: 'essential' }));
    expect('summary' in result && result.summary.level).toBe('essential');
  });

  it.each(['constructor', '__proto__', 'toString'])('refuses the level "%s"', level => {
    expect(accepted(summary({ level }))).toBe(false);
  });

  it('refuses a counter key of the prototype', () => {
    expect(accepted(JSON.parse('{"schema_version":2,"notice_version":1,"level":"extended","session_id":"00000000-0000-4000-8000-000000000201","os_family":"windows","snapshot":0,"final":false,"duration_s":0,"active_s":0,"counters":{"__proto__":1}}'))).toBe(false);
  });
});

describe('schema version', () => {
  it('gives 410 for a version below the lowest accepted version', () => {
    expect(validateSummary(summary({ schema_version: 1 }), 2)).toEqual({ status: 410 });
    expect(validateSummary({ schema_version: 1, events: [] }, 2)).toEqual({ status: 410 });
  });

  it('gives 410 for a version 1 summary (no level), so that an old app stops sending', () => {
    expect(validateSummary(summary({ schema_version: 1, level: undefined }))).toEqual({ status: 410 });
  });

  it('accepts version 2 with notice version 1, and version 3 with notice version 2', () => {
    expect(accepted(summary({ schema_version: 2, notice_version: 1 }))).toBe(true);
    expect(accepted(summary({ schema_version: 3, notice_version: 2 }))).toBe(true);
    expect(accepted(summary({ schema_version: 2, notice_version: 2 }))).toBe(false);
    expect(accepted(summary({ schema_version: 3, notice_version: 1 }))).toBe(false);
  });

  it('gives 400 for a version above the current version, 0, or a text', () => {
    expect(validateSummary(summary({ schema_version: 4, notice_version: 2 }))).toEqual({ status: 400 });
    expect(validateSummary(summary({ schema_version: 0 }))).toEqual({ status: 400 });
    expect(validateSummary(summary({ schema_version: '2' }))).toEqual({ status: 400 });
  });
});

describe('app_version', () => {
  it.each([
    ['a release version', '1.2.3', '1.2.3'],
    ['a dev version', '1.0-SNAPSHOT', '1.0-SNAPSHOT'],
    ['an empty string', '', ''],
    ['spaces and other characters', '1.0 beta (build 7) ü/2026', '1.0 beta (build 7) ü/2026'],
    ['a long string', 'x'.repeat(1000), 'x'.repeat(1000)],
    ['an integer', 7, '7'],
    ['a decimal number', 1.5, '1.5'],
    ['true', true, 'true'],
    ['an object', { major: 1 }, '{"major":1}'],
    ['an array', ['1', '0'], '["1","0"]'],
    ['null', null, 'unknown'],
  ])('accepts %s', (_name, value, stored) => {
    const result = validateSummary(summary({ app_version: value }));
    expect('summary' in result && result.summary.app_version).toBe(stored);
  });

  it('accepts a summary without app_version and stores "unknown"', () => {
    const result = validateSummary(summary({ app_version: undefined }));
    expect('summary' in result && result.summary.app_version).toBe('unknown');
  });

  it('does not make other checks less strict', () => {
    expect(accepted(summary({ app_version: undefined, metadata: {} }))).toBe(false);
    expect(accepted(summary({ app_version: 7, session_id: undefined }))).toBe(false);
    expect(accepted(summary({ app_version: 7, os_family: 'beos' }))).toBe(false);
  });
});

describe('appVersionText', () => {
  it('keeps a string, writes other values as JSON, and gives "unknown" for no value', () => {
    expect(appVersionText('1.0.0')).toBe('1.0.0');
    expect(appVersionText(0)).toBe('0');
    expect(appVersionText(false)).toBe('false');
    expect(appVersionText(undefined)).toBe('unknown');
    expect(appVersionText(null)).toBe('unknown');
  });
});

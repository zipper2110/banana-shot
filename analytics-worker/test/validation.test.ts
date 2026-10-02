import { describe, expect, it } from 'vitest';
import { appVersionText, validateBatch } from '../src/validation';
import { batch, bodyOf, invalidBatches, validBatches } from './fixtures';

describe('validateBatch with the contract fixtures', () => {
  it.each(validBatches.map((value, i) => [i, value]))('accepts valid batch %i', (_i, value) => {
    expect(validateBatch(value)).not.toBeNull();
  });

  it.each(invalidBatches.map(invalid => [invalid.name, invalid]))('rejects "%s"', (_name, invalid) => {
    const value = (() => { try { return JSON.parse(bodyOf(invalid)); } catch { return undefined; } })();
    expect(validateBatch(value)).toBeNull();
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
    expect(validateBatch(batch({ app_version: value }))?.app_version).toBe(stored);
  });

  it('accepts a batch without app_version and stores "unknown"', () => {
    expect(validateBatch(batch({ app_version: undefined }))?.app_version).toBe('unknown');
  });

  it('does not make other envelope checks less strict', () => {
    expect(validateBatch(batch({ app_version: undefined, metadata: {} }))).toBeNull();
    expect(validateBatch(batch({ app_version: 7, session_id: undefined }))).toBeNull();
    expect(validateBatch(batch({ app_version: 7, os_family: 'beos' }))).toBeNull();
    expect(validateBatch(batch({ app_version: 7, schema_version: 2 }))).toBeNull();
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

describe('envelope checks', () => {
  it.each([
    ['a non-object body', [batch()]],
    ['a string body', 'batch'],
    ['null', null],
    ['an unknown notice version', batch({ notice_version: 2 })],
    ['a session ID that is not a UUID v4', batch({ session_id: '00000000-0000-1000-8000-000000000201' })],
    ['an uppercase session ID', batch({ session_id: '00000000-0000-4000-8000-00000000020A' })],
    ['no events', batch({ events: [] })],
    ['more than 20 events', batch({ events: Array.from({ length: 21 }, (_, i) => ({ sequence_number: i, name: 'point_added', elapsed_ms: i, properties: {} })) })],
  ])('rejects %s', (_name, value) => {
    expect(validateBatch(value)).toBeNull();
  });

  it('accepts 20 events', () => {
    const events = Array.from({ length: 20 }, (_, i) => ({ sequence_number: i, name: 'point_added', elapsed_ms: i, properties: {} }));
    expect(validateBatch(batch({ events }))?.events).toHaveLength(20);
  });
});

describe('event checks', () => {
  const withEvent = (event: Record<string, unknown>) => batch({ events: [{ sequence_number: 0, name: 'session_started', elapsed_ms: 0, properties: {}, ...event }] });

  it.each([
    ['a duplicate sequence number', batch({ events: [
      { sequence_number: 3, name: 'point_added', elapsed_ms: 0, properties: {} },
      { sequence_number: 3, name: 'point_added', elapsed_ms: 1, properties: {} },
    ] })],
    ['a negative sequence number', withEvent({ sequence_number: -1 })],
    ['a sequence number above 1000000', withEvent({ sequence_number: 1_000_001 })],
    ['a decimal sequence number', withEvent({ sequence_number: 0.5 })],
    ['elapsed_ms above 7 days', withEvent({ elapsed_ms: 604_800_001 })],
    ['a text elapsed_ms', withEvent({ elapsed_ms: '0' })],
    ['an array as properties', withEvent({ properties: [] })],
    ['properties on an event without properties', withEvent({ properties: { result: 'success' } })],
    ['a missing property', withEvent({ name: 'export_cancelled', properties: {} })],
    ['a negative duration', withEvent({ name: 'export_cancelled', properties: { duration_ms: -1 } })],
    ['an unknown encoder family', withEvent({ name: 'export_started', properties: { container: 'mp4', encoder_family: 'cuda' } })],
    ['a missing event key', batch({ events: [{ sequence_number: 0, name: 'session_started', elapsed_ms: 0 }] })],
  ])('rejects %s', (_name, value) => {
    expect(validateBatch(value)).toBeNull();
  });

  it('returns the events as they were sent', () => {
    const event = { sequence_number: 4, name: 'export_failed', elapsed_ms: 9, properties: { failure_category: 'output_write', duration_ms: 12 } };
    expect(validateBatch(batch({ events: [event] }))?.events).toEqual([event]);
  });
});

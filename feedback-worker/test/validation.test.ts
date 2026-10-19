import { describe, expect, it } from 'vitest';
import analyticsAttributes from '../../analytics-contract/v1/attributes.json';
import schema from '../../feedback-contract/v1/report.schema.json';
import { LANGUAGES, MAX_MESSAGE, validateReport } from '../src/validation';
import { bodyOf, invalidReports, report, validReports } from './fixtures';

describe('validateReport with the contract fixtures', () => {
  it.each(validReports.map((value, i) => [i, value]))('accepts valid report %i', (_i, value) => {
    expect(validateReport(value)).not.toBeNull();
  });

  it.each(invalidReports.map(invalid => [invalid.name, invalid]))('refuses "%s"', (_name, invalid) => {
    const value = (() => { try { return JSON.parse(bodyOf(invalid)); } catch { return undefined; } })();
    expect(validateReport(value)).toBeNull();
  });
});

describe('limits', () => {
  it('accepts a message of 10,000 characters and refuses one more', () => {
    expect(validateReport(report({ message: 'x'.repeat(MAX_MESSAGE) }))).not.toBeNull();
    expect(validateReport(report({ message: 'x'.repeat(MAX_MESSAGE + 1) }))).toBeNull();
  });

  it('accepts a report without the optional keys', () => {
    expect(validateReport(report({ email: undefined, error: undefined, log: undefined }))).not.toBeNull();
  });

  it.each(['app_version', 'os_name', 'os_version', 'java_version'])('refuses %s above 100 characters', key => {
    expect(validateReport(report({ [key]: 'x'.repeat(100) }))).not.toBeNull();
    expect(validateReport(report({ [key]: 'x'.repeat(101) }))).toBeNull();
  });

  it('refuses an email address above 254 characters', () => {
    expect(validateReport(report({ email: `${'a'.repeat(242)}@example.test` }))).toBeNull();
  });
});

describe('language fields', () => {
  it('use the language list of the analytics contract, also in the schema', () => {
    expect(LANGUAGES).toEqual(analyticsAttributes.attributes.os_language);
    expect(schema.properties.app_language.enum).toEqual(LANGUAGES);
    expect(schema.properties.os_language.enum).toEqual(LANGUAGES);
  });

  it('are optional', () => {
    expect(validateReport(report())).not.toBeNull();
    expect(validateReport(report({ app_language: 'es', os_language: 'es' }))).not.toBeNull();
  });

  it.each(LANGUAGES)('accept the value %s', code => {
    expect(validateReport(report({ app_language: code, os_language: code }))).not.toBeNull();
  });

  it.each(['de-DE', 'DE', 'xx', 'spa', 'iw', '', ' es', 'constructor'])('refuse the value "%s"', code => {
    expect(validateReport(report({ os_language: code }))).toBeNull();
    expect(validateReport(report({ app_language: code }))).toBeNull();
  });
});

describe('text in other scripts', () => {
  it('accepts a text of 10,000 UTF-16 characters that ends with an emoji', () => {
    const message = 'x'.repeat(MAX_MESSAGE - 2) + '🎾';
    expect(message).toHaveLength(MAX_MESSAGE);
    expect(validateReport(report({ message, error: message }))?.message).toBe(message);
  });
});

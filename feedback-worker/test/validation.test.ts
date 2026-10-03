import { describe, expect, it } from 'vitest';
import { MAX_MESSAGE, validateReport } from '../src/validation';
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

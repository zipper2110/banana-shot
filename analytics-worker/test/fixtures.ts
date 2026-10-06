import keysFile from '../../analytics-contract/v1/counter-keys.json';
import validFile from '../../analytics-contract/v1/valid-summaries.json';
import invalidFile from '../../analytics-contract/v1/invalid-summaries.json';
import smokeFile from '../../analytics-contract/v1/smoke-summary.json';

export type InvalidSummary = { name: string; payload?: unknown; raw?: string };

export const contractCounterKeys: string[] = keysFile.counterKeys;
export const contractEssentialCounterKeys: string[] = keysFile.essentialCounterKeys;
export const validSummaries: Record<string, unknown>[] = validFile.validSummaries;
export const invalidSummaries: InvalidSummary[] = invalidFile.invalidSummaries;
export const smokeSummary: Record<string, unknown> = smokeFile;

/** A valid summary. Change it with `overrides`; a value of `undefined` removes the key. */
export function summary(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const value: Record<string, unknown> = {
    schema_version: 2,
    notice_version: 1,
    level: 'extended',
    session_id: '00000000-0000-4000-8000-000000000201',
    app_version: '1.0.0',
    os_family: 'windows',
    snapshot: 0,
    final: false,
    duration_s: 0,
    active_s: 0,
    counters: {},
    ...overrides,
  };
  for (const key of Object.keys(value)) if (value[key] === undefined) delete value[key];
  return value;
}

/** The text of a payload as the Worker gets it: an invalid fixture has a payload or a raw body. */
export function bodyOf(invalid: InvalidSummary): string {
  return invalid.raw ?? JSON.stringify(invalid.payload);
}

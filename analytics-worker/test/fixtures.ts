import validFile from '../../analytics-contract/v1/valid-batches.json';
import invalidFile from '../../analytics-contract/v1/invalid-batches.json';

export type InvalidBatch = { name: string; payload?: unknown; raw?: string };

export const validBatches: Record<string, unknown>[] = validFile.validBatches;
export const invalidBatches: InvalidBatch[] = invalidFile.invalidBatches;

/** A valid batch with one event. Change it with `overrides`; a value of `undefined` removes the key. */
export function batch(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const value: Record<string, unknown> = {
    schema_version: 1,
    notice_version: 1,
    session_id: '00000000-0000-4000-8000-000000000201',
    app_version: '1.0.0',
    os_family: 'windows',
    events: [{ sequence_number: 0, name: 'session_started', elapsed_ms: 0, properties: {} }],
    ...overrides,
  };
  for (const key of Object.keys(value)) if (value[key] === undefined) delete value[key];
  return value;
}

/** The text of a payload as the Worker gets it: an invalid fixture has a payload or a raw body. */
export function bodyOf(invalid: InvalidBatch): string {
  return invalid.raw ?? JSON.stringify(invalid.payload);
}

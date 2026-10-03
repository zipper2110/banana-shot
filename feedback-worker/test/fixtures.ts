import validFile from '../../feedback-contract/v1/valid-reports.json';
import invalidFile from '../../feedback-contract/v1/invalid-reports.json';

export type InvalidReport = { name: string; payload?: unknown; raw?: string };

export const validReports: Record<string, unknown>[] = validFile.validReports;
export const invalidReports: InvalidReport[] = invalidFile.invalidReports;

/** The Base64 gzip log of the first valid fixture. */
export const LOG = validReports[0].log as string;

/** A valid report without a log. Change it with `overrides`; a value of `undefined` removes the key. */
export function report(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  const value: Record<string, unknown> = {
    report_id: '00000000-0000-4000-8000-000000000201',
    topic: 'problem',
    message: 'The export stops.',
    email: 'user@example.test',
    app_version: '1.0.0',
    os_name: 'Windows 11',
    os_version: '10.0',
    java_version: '21.0.4',
    ...overrides,
  };
  for (const key of Object.keys(value)) if (value[key] === undefined) delete value[key];
  return value;
}

/** The text of a payload as the Worker gets it: an invalid fixture has a payload or a raw body. */
export function bodyOf(invalid: InvalidReport): string {
  return invalid.raw ?? JSON.stringify(invalid.payload);
}

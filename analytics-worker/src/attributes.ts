/**
 * The closed lists of session attributes (docs/analytics/design.md, "Attributes"). Only an extended summary of
 * schema version 3 can contain them. A test checks the lists against analytics-contract/v1/attributes.json.
 */
export const ATTRIBUTE_VALUES: Readonly<Record<string, readonly string[]>> = {
  theme: ['dark', 'mid', 'light'],
  accent: ['default', 'custom'],
  language: ['en'],
  sport: ['tennis', 'padel'],
};

/** Attributes with an array of values. Each value can be in the array one time. */
export const ARRAY_ATTRIBUTES: ReadonlySet<string> = new Set(['sport']);

export type Attributes = Record<string, string | string[]>;

/** The attributes as the Worker stores them: the keys and the array values in a fixed order. Null for invalid input. */
export function validAttributes(value: unknown): Attributes | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null;
  const result: Attributes = {};
  for (const key of Object.keys(ATTRIBUTE_VALUES)) {
    if (!Object.hasOwn(value, key)) continue;
    const item = (value as Record<string, unknown>)[key];
    const allowed = ATTRIBUTE_VALUES[key];
    if (ARRAY_ATTRIBUTES.has(key)) {
      if (!Array.isArray(item) || new Set(item).size !== item.length) return null;
      if (!item.every(entry => typeof entry === 'string' && allowed.includes(entry))) return null;
      result[key] = allowed.filter(entry => item.includes(entry));
    } else {
      if (typeof item !== 'string' || !allowed.includes(item)) return null;
      result[key] = item;
    }
  }
  return Object.keys(value).every(key => Object.hasOwn(ATTRIBUTE_VALUES, key)) ? result : null;
}

/**
 * The closed lists of session attributes (docs/analytics/design.md, "Attributes"). A test checks the lists against
 * analytics-contract/v1/attributes.json.
 */

/**
 * The language values (B-11 E2): the primary language subtags of the Windows display languages, and `other`. The app
 * language (`language`) and the Windows display language (`os_language`) use this list. Never a region or a full locale.
 */
export const LANGUAGES: readonly string[] = [
  'ar', 'bg', 'ca', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'eu', 'fi', 'fr', 'gl', 'he', 'hi', 'hr', 'hu', 'id', 'it',
  'ja', 'ko', 'lt', 'lv', 'ms', 'nb', 'nl', 'pl', 'pt', 'ro', 'ru', 'sk', 'sl', 'sr', 'sv', 'th', 'tr', 'uk', 'vi', 'zh',
  'other',
];

export const ATTRIBUTE_VALUES: Readonly<Record<string, readonly string[]>> = {
  theme: ['dark', 'mid', 'light'],
  accent: ['default', 'custom'],
  language: LANGUAGES,
  os_language: LANGUAGES,
  sport: ['tennis', 'padel'],
};

/** Attributes with an array of values. Each value can be in the array one time. */
export const ARRAY_ATTRIBUTES: ReadonlySet<string> = new Set(['sport']);

/** The attributes that an essential summary of schema version 4 can contain. The extended level can send all. */
export const ESSENTIAL_ATTRIBUTES: ReadonlySet<string> = new Set(['language', 'os_language']);

/** The attributes that schema version 3 does not have. Notice version 2 does not tell about them. */
export const SCHEMA_4_ATTRIBUTES: ReadonlySet<string> = new Set(['os_language']);

export type Attributes = Record<string, string | string[]>;

/**
 * The attributes as the Worker stores them: the keys and the array values in a fixed order. Null for invalid input.
 * [allowed] is the set of the attribute keys that the summary can contain.
 */
export function validAttributes(value: unknown, allowed: (key: string) => boolean = () => true): Attributes | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null;
  if (!Object.keys(value).every(key => Object.hasOwn(ATTRIBUTE_VALUES, key) && allowed(key))) return null;
  const result: Attributes = {};
  for (const key of Object.keys(ATTRIBUTE_VALUES)) {
    if (!Object.hasOwn(value, key)) continue;
    const item = (value as Record<string, unknown>)[key];
    const values = ATTRIBUTE_VALUES[key];
    if (ARRAY_ATTRIBUTES.has(key)) {
      if (!Array.isArray(item) || new Set(item).size !== item.length) return null;
      if (!item.every(entry => typeof entry === 'string' && values.includes(entry))) return null;
      result[key] = values.filter(entry => item.includes(entry));
    } else {
      if (typeof item !== 'string' || !values.includes(item)) return null;
      result[key] = item;
    }
  }
  return result;
}

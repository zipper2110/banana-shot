export const DAY = 86_400_000;
export const ALLOWED_DAYS = [1, 7, 30, 90] as const;
export const WEEKS = 12;

/**
 * The selected period: `days` UTC days that end with the day `lastDay` (included). Without `lastDay`, the period ends
 * today. `days` 1 is only that day. `prev` is the period of the same length before it.
 * All times are milliseconds. `end` is exclusive.
 */
export interface Period {
  days: number;
  start: number;
  end: number;
  prevStart: number;
  /** The Monday (UTC) of the first of the 12 weeks of the weekly charts. */
  weeksStart: number;
}

export function makePeriod(days: number, now: number, lastDay = startOfDay(now)): Period {
  const end = lastDay + DAY;
  const start = end - days * DAY;
  const lastMonday = startOfWeek(lastDay);
  return { days, start, end, prevStart: start - days * DAY, weeksStart: lastMonday - (WEEKS - 1) * 7 * DAY };
}

export function parseDays(value: string | null): number {
  const days = Number(value);
  return (ALLOWED_DAYS as readonly number[]).includes(days) ? days : 30;
}

/** The UTC day of the date `YYYY-MM-DD`. Today for no date, an invalid date, or a date after today. */
export function parseLastDay(value: string | null, now: number): number {
  const today = startOfDay(now);
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return today;
  const day = parseDate(value);
  return Number.isNaN(day) || isoDate(day) !== value || day > today ? today : day;
}

/**
 * The first UTC day of data that the cockpit shows (the variable `DATA_START`, `YYYY-MM-DD`). The cockpit ignores all
 * older data, for example test data before the first release. Returns 0 (no limit) for no date or an invalid date.
 */
export function dataStart(value: string | undefined): number {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return 0;
  const day = parseDate(value);
  return Number.isNaN(day) || isoDate(day) !== value ? 0 : day;
}

export function startOfDay(time: number): number {
  return Math.floor(time / DAY) * DAY;
}

/** The Monday 00:00 UTC of the week of `time`. */
export function startOfWeek(time: number): number {
  const day = startOfDay(time);
  const weekday = (new Date(day).getUTCDay() + 6) % 7;
  return day - weekday * DAY;
}

export function isoDate(time: number): string {
  return new Date(time).toISOString().slice(0, 10);
}

export function parseDate(date: string): number {
  return Date.parse(`${date}T00:00:00Z`);
}

/** Each UTC date from `start` (inclusive) to `end` (exclusive). */
export function dates(start: number, end: number): string[] {
  const result: string[] = [];
  for (let time = start; time < end; time += DAY) result.push(isoDate(time));
  return result;
}

/** The Monday dates of the 12 weeks of the weekly charts. */
export function weeks(period: Period): string[] {
  return Array.from({ length: WEEKS }, (_, index) => isoDate(period.weeksStart + index * 7 * DAY));
}

export function weekOf(date: string): string {
  return isoDate(startOfWeek(parseDate(date)));
}

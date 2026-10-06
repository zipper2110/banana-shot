export const DAY = 86_400_000;
export const ALLOWED_DAYS = [7, 30, 90] as const;
export const WEEKS = 12;

/**
 * The selected period: the last `days` UTC days, today included. `prev` is the period of the same length before it.
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

export function makePeriod(days: number, now: number): Period {
  const tomorrow = startOfDay(now) + DAY;
  const start = tomorrow - days * DAY;
  const thisMonday = startOfWeek(now);
  return { days, start, end: tomorrow, prevStart: start - days * DAY, weeksStart: thisMonday - (WEEKS - 1) * 7 * DAY };
}

export function parseDays(value: string | null): number {
  const days = Number(value);
  return (ALLOWED_DAYS as readonly number[]).includes(days) ? days : 30;
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

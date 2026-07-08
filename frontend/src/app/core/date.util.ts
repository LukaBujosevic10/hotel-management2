/**
 * Date helpers.
 *
 * The API speaks plain calendar dates (yyyy-MM-dd) with no time zone. Never use
 * Date.toISOString() for those: it converts to UTC first, so a date picked at
 * local midnight in UTC+2 silently becomes the previous day.
 */

/** Formats a Date as yyyy-MM-dd using the LOCAL calendar day. */
export function toApiDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Parses yyyy-MM-dd into a local Date at midnight (no time-zone shifting). */
export function fromApiDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Midnight today, local time. */
export function today(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

export function addDays(date: Date, days: number): Date {
  const copy = new Date(date.getTime());
  copy.setDate(copy.getDate() + days);
  return copy;
}

/** Whole days between two dates, ignoring DST by comparing calendar days. */
export function daysBetween(from: Date, to: Date): number {
  const a = Date.UTC(from.getFullYear(), from.getMonth(), from.getDate());
  const b = Date.UTC(to.getFullYear(), to.getMonth(), to.getDate());
  return Math.round((b - a) / 86400000);
}

export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();
}

export function isWeekend(d: Date): boolean {
  const day = d.getDay();
  return day === 0 || day === 6;
}

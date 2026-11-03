/**
 * Small text rules both intake parsers share. Mirrored in Kotlin
 * (domain/intake/Common.kt); the cases are in intake.fixtures.json.
 */

/** Splits into lines, drops form feeds and trailing spaces, keeps empty lines (they separate blocks). */
export function lines(text: string): string[] {
  return text
    .replace(/\r\n?/g, "\n")
    .replace(/\f/g, "\n")
    .replace(/\u00a0/g, " ")
    .split("\n")
    .map((l) => l.replace(/\s+$/, ""));
}

/** "Gleis 4 C-F" → "4", "12a" → "12a", "3 D" → "3": the platform without its sectors. */
export function platformOf(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const match = /^\s*(\d{1,3}[a-z]?|[A-Z]{1,2})/.exec(raw.trim());
  return match ? match[1] : null;
}

/** The letters a line name starts with: "RE19" → "RE", "ICE 728" → "ICE"; null without. */
export function productOf(line: string): string | null {
  const match = /^([A-Za-zÄÖÜäöü]{1,5})(?=\s|\d|$)/.exec(line.trim());
  return match ? match[1].toUpperCase() : null;
}

/**
 * The form two line names are compared in: upper case, no spaces, so "RE19",
 * "RE 19" and "re19" are the same line.
 */
export function lineKey(line: string | null | undefined): string {
  return (line ?? "").toUpperCase().replace(/\s+/g, "");
}

/** "14.11.2025" → "2025-11-14"; null when it is no date. */
export function isoDate(day: string, month: string, year: string): string | null {
  const d = Number(day);
  const m = Number(month);
  const y = Number(year.length === 2 ? `20${year}` : year);
  if (!(d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 2000)) return null;
  return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

/** "6:29" → "06:29"; null when it is no time. */
export function hhmm(raw: string): string | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(raw.trim());
  if (!match) return null;
  const h = Number(match[1]);
  const m = Number(match[2]);
  if (h > 23 || m > 59) return null;
  return `${String(h).padStart(2, "0")}:${match[2]}`;
}

/** The ISO date one day after `date`. */
export function nextDay(date: string): string {
  const t = new Date(`${date}T12:00:00Z`);
  t.setUTCDate(t.getUTCDate() + 1);
  return t.toISOString().slice(0, 10);
}

/** "117,40" / "1.117,40" → 11740; null when it is no amount. */
export function cents(raw: string): number | null {
  const match = /^(\d{1,3}(?:\.\d{3})*|\d+),(\d{2})$/.exec(raw.trim());
  if (!match) return null;
  return Number(match[1].replace(/\./g, "")) * 100 + Number(match[2]);
}

/** Collapses runs of spaces: what a station name looks like whatever the extraction did to it. */
export function squash(text: string): string {
  return text.replace(/\s+/g, " ").trim();
}

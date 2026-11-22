/**
 * The coach and seats of a ride's reservation, as the ticket prints them
 * (decisions/2026-11-22-abeona-coach-and-seat-from-the-ticket.md). Mirrored
 * in Kotlin (domain/intake/Seat.kt); both run the `seats` cases in
 * intake.fixtures.json.
 *
 * DB: "1 Sitzplatz, Wg. 24, Pl. 66, 1 Fenster, Großraum, …, Res.Nr. 8051 …".
 * Trenitalia, as trenitaliaTicket.ts writes it: "Coach 7, seats 10C, 10D".
 * The seats keep the ticket's own numbers: several are kept apart by one
 * space ("71 73"), a range by a dash ("45-48"). The reservation number is
 * never read: only a number after the coach or seat word counts.
 */

export interface Seat {
  /** "24"; null when the reservation names no coach. */
  coach: string | null;
  /** "66", "71 73", "45-48", "10C 10D"; null when it names no seat. */
  seats: string | null;
}

const COACH = /(?:^|[\s,;(])(?:Wg\.|Wagen|Coach|Carrozza)\s*:?\s*(\d{1,4}[A-Za-z]?)(?![\w])/i;
/**
 * The seat word, then its numbers: one, several (space or comma) or a range.
 * A number followed by a word ("1 Fenster") is the next item of the note,
 * not a seat.
 */
const ITEM = String.raw`\d{1,3}[A-Za-z]?(?![\w])(?!\s+[A-Za-zÄÖÜäöüß]{2})`;
const SEATS = new RegExp(String.raw`(?:^|[\s,;(])(?:Pl\.|Platz|Plätze|seats?|posti|posto)\s*:?\s*(${ITEM}(?:\s*(?:[-–]|,|\s)\s*${ITEM})*)`, "i");

export function seatOf(reservation: string | null | undefined): Seat {
  if (!reservation) return { coach: null, seats: null };
  const coach = COACH.exec(reservation)?.[1] ?? null;
  const list = SEATS.exec(reservation)?.[1] ?? null;
  const seats = list
    ? list
        .trim()
        .replace(/\s*[-–]\s*/g, "-")
        .replace(/\s*,\s*|\s+/g, " ")
    : null;
  return { coach, seats: seats || null };
}

/**
 * The seat as the app shows it, the same words on both clients:
 * "Coach 24 · Seat 66", "Coach 23 · Seats 71, 73", "Coach 7 · Seats 45–48",
 * "Coach 24", "Seat 66"; null when neither is known.
 */
export function seatText(coach: string | null | undefined, seats: string | null | undefined): string | null {
  const c = coach?.trim() || null;
  const s = seats?.trim() || null;
  const parts: string[] = [];
  if (c) parts.push(`Coach ${c}`);
  if (s) {
    const several = /[\s,-]/.test(s);
    parts.push(`${several ? "Seats" : "Seat"} ${s.replace(/\s*-\s*/g, "–").replace(/\s*,\s*|\s+/g, ", ")}`);
  }
  return parts.length > 0 ? parts.join(" · ") : null;
}

/** The same for a screen reader, as part of a sentence: "Coach 24, seat 66". */
export function seatSpoken(coach: string | null | undefined, seats: string | null | undefined): string | null {
  const text = seatText(coach, seats);
  return text ? text.replace(" · Seat", ", seat").replace("–", " to ") : null;
}

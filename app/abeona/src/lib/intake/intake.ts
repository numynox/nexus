/**
 * Intake end to end, without the network: a share text or a ticket's text →
 * what was read, the connection found for it, and what the save sheet starts
 * with. Mirrored in Kotlin (domain/intake/Intake.kt).
 */
import { isDbTicket, parseDbTicket, parseDbTickets } from "./dbTicket.ts";
import type { FareTypeValue } from "./fare.ts";
import { matchConnection, type MatchResult, type TransitPort } from "./match.ts";
import type { Parsed, ParsedTicket } from "./parsed.ts";
import { isShareText, parseShareText } from "./shareText.ts";
import { isTrenitaliaTicket, parseTrenitaliaTicket } from "./trenitaliaTicket.ts";

/** What the save sheet starts with; the user can change every field. */
export interface Prefill {
  fare_type: FareTypeValue | null;
  train_binding: boolean | null;
  travel_class: 1 | 2 | null;
  /** One discount per person the ticket prints (ParsedTicket.discounts); empty for a share. */
  discounts: (25 | 50 | null)[];
  /** How many people the ticket is for: a hint next to "Who travels". */
  travellers: number | null;
  price_cents: number | null;
}

export interface IntakeDraft {
  /** abeona_trips.source. */
  source: "share" | "pdf";
  parsed: Parsed;
  match: MatchResult;
  prefill: Prefill;
}

/** Reads a pasted or shared text: a DB Navigator share, or a ticket's text. Null when it is neither. */
export function parseText(text: string): Parsed | null {
  if (isTrenitaliaTicket(text)) return parseTrenitaliaTicket(text);
  if (isShareText(text) && !isDbTicket(text)) return parseShareText(text);
  if (isDbTicket(text)) return parseDbTicket(text);
  return parseShareText(text);
}

/**
 * Reads a ticket PDF's text: Trenitalia's or DB's, else whatever `parseText`
 * makes of it. One trip per direction the ticket is for, so a DB ticket for
 * both directions gives two that share it; empty when nothing was found.
 */
export function parseTicketTrips(text: string): Parsed[] {
  if (isTrenitaliaTicket(text)) {
    const ticket = parseTrenitaliaTicket(text);
    return ticket ? [ticket] : [];
  }
  const db = parseDbTickets(text);
  if (db.length > 0) return db;
  const other = parseText(text);
  return other ? [other] : [];
}

/** "Outward journey" / "Return journey" for one direction of a ticket; null otherwise. */
export function directionLabel(parsed: Parsed): string | null {
  if (parsed.kind !== "ticket" || !parsed.direction) return null;
  return parsed.direction === "outward" ? "Outward journey" : "Return journey";
}

export function prefillOf(parsed: Parsed): Prefill {
  if (parsed.kind === "share") {
    return {
      fare_type: null,
      train_binding: null,
      travel_class: null,
      discounts: [],
      travellers: null,
      price_cents: null,
    };
  }
  const t: ParsedTicket = parsed;
  return {
    fare_type: t.fare_type,
    train_binding: t.train_binding,
    travel_class: t.travel_class,
    discounts: t.discounts,
    travellers: t.travellers,
    price_cents: t.price_cents,
  };
}

export async function intake(parsed: Parsed, source: "share" | "pdf", port: TransitPort): Promise<IntakeDraft> {
  if (parsed.legs.length === 0) throw new Error("No rides were found in it.");
  return { source, parsed, match: await matchConnection(parsed.legs, port), prefill: prefillOf(parsed) };
}

/** One English sentence on how well the timetable knows the rides. */
export function matchSummary(match: MatchResult): string {
  if (match.status === "full") return "Found in the timetable: Abeona follows its delays and platforms.";
  if (match.status === "partial") {
    return `${match.matched} of ${match.total} rides found in the timetable. The others are saved as printed, without live updates.`;
  }
  return "Not found in the timetable — it may be too far ahead, in the past, or abroad. Saved as printed, without live updates.";
}

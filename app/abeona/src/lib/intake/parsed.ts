/**
 * What intake reads out of a DB Navigator share text or a DB ticket's text
 * layer, before anything is looked up. Parsing happens on the device only —
 * never in an Edge Function (decisions/2026-11-04-abeona-intake.md).
 *
 * The Kotlin copy is android/abeona/.../domain/intake/Parsed.kt. Both run the
 * cases in intake.fixtures.json; change the shapes together.
 *
 * Times are local wall-clock times as printed ("06:29"), dates ISO
 * ("2025-11-14"). Turning them into instants is matching's job (match.ts).
 */
import type { FareTypeValue } from "./fare.ts";

/** One ride as printed: a share text's block or two rows of a ticket's stop table. */
export interface ParsedLeg {
  /** As printed, without the bracketed number: "RE19", "ICE 728", "IC 535". */
  line: string;
  /** The letters of the line: "RE", "ICE", "IC"; null when it has none. */
  product: string | null;
  /** The train number DB prints in brackets after a regional line: "RE19 (4901)" → "4901". */
  number: string | null;
  /** "Nach …" in a share text; null when not printed. */
  headsign: string | null;
  from: string;
  to: string;
  /** Local date of the departure. */
  date: string;
  /** "06:29". */
  departure: string;
  /** Local date of the arrival: the next day when the ride runs past midnight. */
  arrival_date: string;
  arrival: string;
  /** "Gleis 4 C-F" → "4": sectors are dropped. Null when not printed. */
  from_platform: string | null;
  to_platform: string | null;
  /** A ticket's reservation column for this ride, as printed. */
  reservation: string | null;
}

/** A DB Navigator "Teilen" text. */
export interface ParsedShare {
  kind: "share";
  origin: string;
  destination: string;
  date: string;
  legs: ParsedLeg[];
  /** The bahn.de link, as printed. */
  link: string | null;
  /**
   * The connection id inside the link. Kept as a reference only: resolving it
   * needs bahn.de, which refuses our servers (transit providers ADR).
   */
  vbid: string | null;
}

/**
 * Whose price the ticket prints. `booking`: one direction of a return
 * booking, whose "Gesamtpreis" is very likely the whole booking — the user
 * confirms what this trip cost; Abeona never halves it by itself.
 */
export type PriceScope = "trip" | "booking";

/** A DB Online-Ticket (the PDF's text layer). */
export interface ParsedTicket {
  kind: "ticket";
  operator: "db";
  /** "Super Sparpreis Europa", without "(Rückfahrt)". */
  fare_name: string | null;
  fare_type: FareTypeValue;
  /** Which half of a return booking this ticket is; null for a single. */
  direction: "outward" | "return" | null;
  travel_class: 1 | 2 | null;
  /** "2 Personen" → 2. A hint for "Who travels", not a list of names. */
  travellers: number | null;
  /** "2 BC25" → 25 … */
  bahncard_discount: 25 | 50 | null;
  /** … and 2. */
  bahncard_count: number | null;
  /** Bound to the trains in `booked_lines`. */
  train_binding: boolean;
  /** "ICE 17", … from the "Zugbindung" lines, in order. */
  booked_lines: string[];
  origin: string | null;
  destination: string | null;
  /** Local date of the first departure. */
  date: string | null;
  legs: ParsedLeg[];
  price_cents: number | null;
  price_scope: PriceScope;
  /** Seat reservations as printed, one per ride that has one. */
  reservation: string | null;
}

export type Parsed = ParsedShare | ParsedTicket;

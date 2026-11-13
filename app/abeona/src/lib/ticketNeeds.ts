/**
 * What a trip's ticket needs (issue #62): how many travellers, and which
 * BahnCards it was booked with — not who. A ticket prints persons, not names
 * ("3 Personen … mit 1 BC25, 1 BC50"). Android's domain/TicketNeeds.kt is the
 * same; both run the "ticket_summary" and "ticket_cards" cases in
 * intake/intake.fixtures.json.
 */

export interface TicketSummary {
  /** People on the ticket; null when unknown. */
  traveller_count: number | null;
  /** One discount per BahnCard, ascending. */
  bahncard_discounts: number[];
}

/**
 * From what the ticket printed: the persons (`travellers`, else one per
 * discount printed) and their BahnCards (`discounts`, one per person, null
 * for a person without one).
 */
export function ticketSummary(travellers: number | null, discounts: readonly (number | null)[]): TicketSummary {
  const cards = discounts.filter((d): d is number => d !== null).sort((a, b) => a - b);
  return { traveller_count: travellers ?? (discounts.length > 0 ? discounts.length : null), bahncard_discounts: cards };
}

/** The few fields of a household BahnCard the ticket viewer chooses by. */
export interface CardOnFile {
  id: string;
  kind: number;
  /** yyyy-mm-dd */
  valid_from: string;
  valid_to: string;
  file_path: string | null;
}

/**
 * The household's BahnCards to show with a trip's ticket: one per BahnCard
 * the ticket was booked with (a BahnCard 100 fare: one per traveller), each
 * with a file and valid on `day` (yyyy-mm-dd). The same kind first, else the
 * smallest that covers it; the largest discount first. A card is used once.
 */
export function cardsForTrip<C extends CardOnFile>(
  cards: readonly C[],
  discounts: readonly number[],
  travellerCount: number | null,
  fareType: string,
  day: string,
): C[] {
  const need = fareType === "bahncard100"
    ? Array.from({ length: Math.max(1, travellerCount ?? 1) }, () => 100)
    : [...discounts].sort((a, b) => b - a);
  const left = cards.filter((c) => !!c.file_path && c.valid_from <= day && c.valid_to >= day);
  const shown: C[] = [];
  for (const d of need) {
    const same = left.find((c) => c.kind === d);
    const cover = same ?? left.filter((c) => c.kind > d).sort((a, b) => a.kind - b.kind)[0];
    if (!cover) continue;
    shown.push(cover);
    left.splice(left.indexOf(cover), 1);
  }
  return shown;
}

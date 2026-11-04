/**
 * Who on a ticket holds which BahnCard. A ticket prints persons, not names
 * ("3 Personen … mit 1 BC25, 1 BC50"), so the save sheet hands its discounts
 * to the travellers the user picks, one at a time, and the user can change
 * each. Android's domain/Travellers.kt is the same; both run the "discounts"
 * cases in intake/intake.fixtures.json.
 */
export type Discount = 25 | 50 | null;

/**
 * The discount for the next traveller on the trip: one of the ticket's that is
 * not yet given to someone. `held` are the kinds of the BahnCards that
 * traveller holds on the day. In order:
 *   1. the one their own card matches;
 *   2. none, if they hold no card and the ticket has a person without one;
 *   3. the first left, as printed;
 *   4. none, once every person on the ticket is given away (or it names none).
 */
export function nextDiscount(ticket: readonly Discount[], given: readonly Discount[], held: readonly number[]): Discount {
  const left = [...ticket];
  for (const d of given) {
    const i = left.indexOf(d);
    if (i >= 0) left.splice(i, 1);
  }
  const own = left.find((d) => d !== null && held.includes(d));
  if (own !== undefined) return own;
  if (held.length === 0 && left.includes(null)) return null;
  return left.find((d) => d !== null) ?? null;
}

/** The few fields of a BahnCard the ticket viewer chooses by. */
export interface CardOnFile {
  id: string;
  traveller_id: string;
  kind: number;
  /** yyyy-mm-dd */
  valid_from: string;
  valid_to: string;
  file_path: string | null;
}

/**
 * The BahnCards to show with a trip's ticket: one per traveller on the trip,
 * with a file, valid on `day` (yyyy-mm-dd), of at least their discount when
 * they have one. Without the trip's travellers (`riders` null), every card
 * valid on the day. Android's TicketViewModel.loadCards chooses the same.
 */
export function cardsForTrip<C extends CardOnFile>(
  cards: readonly C[],
  riders: readonly { traveller_id: string; bahncard_discount: number | null }[] | null,
  day: string,
): C[] {
  const byTraveller = new Map<string, C[]>();
  for (const c of cards) {
    if (!c.file_path || c.valid_from > day || c.valid_to < day) continue;
    if (riders && !riders.some((r) => r.traveller_id === c.traveller_id)) continue;
    byTraveller.set(c.traveller_id, [...(byTraveller.get(c.traveller_id) ?? []), c]);
  }
  return [...byTraveller.entries()].map(([traveller, own]) => {
    const need = riders?.find((r) => r.traveller_id === traveller)?.bahncard_discount ?? 0;
    const best = (list: C[]) => list.reduce((a, b) => (b.kind > a.kind ? b : a));
    const enough = own.filter((c) => c.kind >= need);
    return best(enough.length > 0 ? enough : own);
  });
}

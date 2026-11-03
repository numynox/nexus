// Marks alternative connections against what the trip's ticket allows, and
// ranks them. The rules themselves are data (abeona_fare_rules, read through
// abeona_allowed_products); this only applies them, so both clients get the
// same marks from one place.

import type { Allowance, Alternative, Journey, Validity } from "./contract.ts";

function rides(journey: Journey) {
  return journey.legs.filter((leg) => leg.mode !== "walk");
}

function label(leg: Journey["legs"][number]): string {
  return leg.line_name ?? leg.product ?? "this train";
}

/** "ICE 25" and "ice25" are the same train. */
function sameLine(a: string, b: string): boolean {
  const n = (s: string) => s.replace(/\s+/g, "").toUpperCase();
  return n(a) === n(b);
}

export function mark(journey: Journey, allowance: Allowance | null): Alternative {
  const result = (validity: Validity, validity_note: string): Alternative => ({ ...journey, validity, validity_note });

  if (!allowance) return result("check", "Check whether your ticket covers this connection.");
  if (!allowance.known) return result("check", allowance.hint);

  const excluded = new Set(allowance.excluded_products.map((p) => p.toUpperCase()));
  const notCovered = rides(journey).filter((leg) => leg.product && excluded.has(leg.product.toUpperCase()));
  if (notCovered.length > 0) {
    return result("invalid", `Not covered by your ticket: ${notCovered.map(label).join(", ")}`);
  }

  if (allowance.train_bound) {
    const other = rides(journey).filter((leg) => !leg.line_name || !allowance.booked_lines.some((b) => sameLine(b, leg.line_name!)));
    if (other.length > 0) {
      return result("invalid", `Your ticket is bound to your booked trains until DB lifts the binding: ${other.map(label).join(", ")}`);
    }
    return result("valid", "Your booked trains");
  }

  // A ride without a known product cannot be checked against the exclusions.
  if (!allowance.long_distance && rides(journey).some((leg) => leg.mode === "train" && !leg.product)) {
    return result("check", "Abeona cannot tell what kind of train this is. Check the terms on your ticket.");
  }

  return result("valid", allowance.binding_lifted ? "Covered: the train binding is lifted" : "Covered by your ticket");
}

function arrivalMs(journey: Journey): number {
  const last = journey.legs[journey.legs.length - 1];
  const at = last?.to.current ?? last?.to.planned;
  const t = at ? Date.parse(at) : NaN;
  return Number.isNaN(t) ? Number.MAX_SAFE_INTEGER : t;
}

/** Earliest arrival first, then fewer changes; ties keep the provider's order. */
export function rank(list: Alternative[]): Alternative[] {
  return list
    .map((journey, index) => ({ journey, index }))
    .sort((a, b) =>
      arrivalMs(a.journey) - arrivalMs(b.journey) ||
      rides(a.journey).length - rides(b.journey).length ||
      a.index - b.index
    )
    .map(({ journey }) => journey);
}

// The words around a compensation verdict, and the manual replacement ride.
// The verdict itself, its amount, reasons and the German form text come from
// abeona_compensation() in Postgres, so both clients say the same; only the
// short labels live here, mirrored in android/abeona/.../domain/Compensation.kt.
// Change both together.

import type { Leg } from "./transit.ts";

export type Verdict = "eligible" | "not_eligible" | "below_minimum" | "unknown_price" | "unknown_arrival";
export type ClaimStatusValue = "open" | "claimed" | "paid" | "rejected";

/** The card's headline: what the delay is worth, or what is missing. */
export function verdictHeadline(verdict: Verdict, amount: string | null): string {
  switch (verdict) {
    case "eligible":
      return amount ? `${amount} to claim` : "Compensation due";
    case "below_minimum":
      return amount ? `${amount}, under DB's €4 minimum` : "Under DB's €4 minimum";
    case "unknown_price":
      return "Compensation due: add the fare";
    case "unknown_arrival":
      return "When did you arrive?";
    default:
      return "No compensation";
  }
}

export const CLAIM_STATUS_LABELS: Record<ClaimStatusValue, string> = {
  open: "Not claimed yet",
  claimed: "Claimed",
  paid: "Paid",
  rejected: "Rejected",
};

/** "Claim by 14 Jan (in 72 days)", for the deadline in days from today. */
export function claimDeadlineText(dayLabel: string, daysLeft: number): string {
  if (daysLeft < 0) return `The claim deadline was ${dayLabel}`;
  if (daysLeft === 0) return `Claim by today, ${dayLabel}`;
  if (daysLeft === 1) return `Claim by tomorrow, ${dayLabel}`;
  return `Claim by ${dayLabel} (in ${daysLeft} days)`;
}

/** Where the delay came from, in a few words under the delay. */
export function delaySourceText(source: string): string {
  switch (source) {
    case "final":
      return "as recorded after arrival";
    case "replacement":
      return "with the trains you took instead";
    case "live":
      return "from the last live update";
    case "planned":
      return "no live data, so the timetable";
    default:
      return "";
  }
}

/**
 * A ride entered by hand: from the stop of the missed ride to the trip's
 * destination, arriving at `arrival` (ISO). Only what is known is filled in.
 */
export function manualReplacement(missed: Leg, destination: Leg, line: string, arrival: string): Leg {
  const name = line.trim();
  const product = /^([A-Za-zÄÖÜäöü]{1,5})(?=\s|\d|$)/.exec(name)?.[1]?.toUpperCase() ?? null;
  return {
    mode: "train",
    product,
    line_name: name || null,
    trip_ref: null,
    headsign: null,
    operator: null,
    from: { ...missed.from, current: null, cancelled: false },
    to: { ...destination.to, planned: arrival, current: arrival, cancelled: false },
    stopovers: [],
    polyline: null,
    cancelled: false,
    realtime: false,
  };
}

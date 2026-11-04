// Refreshing a saved journey's live values: the whole itinerary first, then
// leg by leg. Shared by the `refresh` action (a client asking) and by
// abeona-refresh-trips (the final snapshot after arrival), so both read
// Transitous the same way.

import type { Journey, Leg } from "./contract.ts";
import * as transitous from "./transitous.ts";

export const MAX_REFRESH_LEGS = 12;

/** What a refresh needs of a saved leg. */
export interface SavedLegRef {
  trip_ref: string | null;
  from_id: string | null;
  to_id: string | null;
  from_name: string;
  to_name: string;
}

/** The provider calls a refresh makes; replaceable in tests. */
export interface Refresher {
  refreshItinerary(token: string): Promise<Journey>;
  refreshLeg(saved: { trip_ref: string; from_id: string | null; to_id: string | null; from_name: string; to_name: string }): Promise<Leg | null>;
}

export const transitousRefresher: Refresher = {
  refreshItinerary: transitous.refreshItinerary,
  refreshLeg: transitous.refreshLeg,
};

function isId(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= 300;
}

/**
 * The legs as the provider sees them now, by position: a leg it could not
 * find is null. `journey` is set when the whole itinerary came back.
 */
export async function refreshJourney(
  refreshToken: unknown,
  savedLegs: unknown,
  provider: Refresher = transitousRefresher,
): Promise<{ journey: Journey | null; legs: (Leg | null)[] }> {
  const saved: SavedLegRef[] = Array.isArray(savedLegs) ? savedLegs.slice(0, MAX_REFRESH_LEGS) : [];

  // First try the whole itinerary; it is one request, and it also notices a
  // leg that has been cancelled outright.
  if (isId(refreshToken)) {
    try {
      const journey = await provider.refreshItinerary(refreshToken);
      if (saved.length === 0 || journey.legs.length === saved.length) {
        return { journey, legs: journey.legs };
      }
    } catch {
      // Fall through to refreshing leg by leg.
    }
  }

  const legs = await Promise.all(
    saved.map(async (leg) => {
      if (!isId(leg?.trip_ref) || typeof leg.from_name !== "string" || typeof leg.to_name !== "string") {
        return null;
      }
      try {
        return await provider.refreshLeg({
          trip_ref: leg.trip_ref,
          from_id: isId(leg.from_id) ? leg.from_id : null,
          to_id: isId(leg.to_id) ? leg.to_id : null,
          from_name: leg.from_name,
          to_name: leg.to_name,
        });
      } catch {
        return null;
      }
    }),
  );
  return { journey: null, legs };
}

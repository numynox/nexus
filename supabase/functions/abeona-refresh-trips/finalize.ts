// The final snapshot: refresh trips that arrived 15 to 90 minutes ago through
// Transitous, once more, and freeze what came back
// (decisions/2026-11-03-abeona-final-snapshot-job.md). Transitous forgets a
// day's realtime soon after, so this must happen on the day.
//
// Kept apart from index.ts so the test can hand it a fake database and a
// fake provider.

import type { Leg } from "../abeona-transit/contract.ts";
import { type Refresher, refreshJourney, transitousRefresher } from "../abeona-transit/refresh.ts";

/** At most this many trips per run; the job runs every 10 minutes. */
export const TRIPS_PER_RUN = 10;

/** A leg as abeona_trips_to_finalize hands it over. */
export interface DueLeg {
  idx: number;
  mode: string;
  final: boolean;
  trip_ref: string | null;
  from_id: string | null;
  to_id: string | null;
  from_name: string;
  to_name: string;
}

export interface DueTrip {
  trip_id: string;
  refresh_token: string | null;
  expected_arrival: string;
  legs: DueLeg[];
}

/** The two RPCs the job calls, as supabase-js answers them. */
export interface Database {
  rpc(fn: string, args: Record<string, unknown>): PromiseLike<{ data: unknown; error: { message: string } | null }>;
}

export interface TripResult {
  trip_id: string;
  /** Legs written and marked final in this run. */
  written: number;
  final: boolean;
  error?: string;
}

/** abeona_update_trip_live's shape, which abeona_finalize_trip reads too. */
export function liveUpdate(idx: number, leg: Leg) {
  return {
    idx,
    departure: leg.from.current,
    arrival: leg.to.current,
    departure_platform: leg.from.platform,
    arrival_platform: leg.to.platform,
    cancelled: leg.cancelled,
    realtime: leg.realtime,
    stopovers: leg.stopovers,
  };
}

/**
 * The updates for one trip: every leg not final yet that the provider
 * answered for, matched by position. A refreshed leg whose mode differs from
 * the saved one is a different journey shape and is left alone.
 */
export function updatesFor(trip: DueTrip, legs: (Leg | null)[]) {
  const updates = [];
  for (const saved of trip.legs) {
    if (saved.final) continue;
    const leg = legs[saved.idx];
    if (!leg || leg.mode !== saved.mode) continue;
    updates.push(liveUpdate(saved.idx, leg));
  }
  return updates;
}

export async function finalizeDueTrips(
  db: Database,
  provider: Refresher = transitousRefresher,
  limit = TRIPS_PER_RUN,
): Promise<TripResult[]> {
  const { data, error } = await db.rpc("abeona_trips_to_finalize", { p_limit: limit });
  if (error) throw new Error(`abeona_trips_to_finalize: ${error.message}`);
  const due = (Array.isArray(data) ? data : []) as DueTrip[];

  const results: TripResult[] = [];
  // One trip after the other: a handful of requests at a time is what
  // Transitous asks of its users.
  for (const trip of due) {
    try {
      const legs = Array.isArray(trip.legs) ? trip.legs : [];
      const refreshed = await refreshJourney(trip.refresh_token, legs, provider);
      const updates = updatesFor({ ...trip, legs }, refreshed.legs);
      const written = await db.rpc("abeona_finalize_trip", { p_trip_id: trip.trip_id, p_legs: updates });
      if (written.error) throw new Error(written.error.message);
      results.push({ trip_id: trip.trip_id, written: updates.length, final: written.data === true });
    } catch (e) {
      results.push({ trip_id: trip.trip_id, written: 0, final: false, error: e instanceof Error ? e.message : String(e) });
    }
  }
  return results;
}

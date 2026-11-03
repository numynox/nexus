// Live values for a shared trip: the same refresh as the clients'
// (abeona-transit/refresh.ts, through the response cache), only around the
// trip and only for what is not final. Nothing is written back; the share
// link reads, it never changes the trip.

import type { Leg } from "../abeona-transit/contract.ts";
import { type Refresher, refreshJourney, type SavedLegRef } from "../abeona-transit/refresh.ts";
import type { SharedLeg, SharedTrip } from "./render.ts";

export interface RefreshRefs {
  refresh_token: string | null;
  source: string;
  legs: SavedLegRef[];
}

/** Two hours before departure until an hour after the expected arrival. */
export function isLiveWindow(shared: SharedTrip, now: Date): boolean {
  if (shared.trip.final || shared.trip.cancelled) return false;
  const rides = shared.legs.filter((l) => l.mode !== "walk");
  const last = rides[rides.length - 1];
  const arrival = Date.parse(last?.to.current ?? last?.to.planned ?? shared.trip.planned_arrival);
  const departure = Date.parse(shared.trip.planned_departure);
  const t = now.getTime();
  return t >= departure - 2 * 3600_000 && t <= arrival + 3600_000;
}

/** The legs with what the provider says now, matched by position and mode; final legs stay. */
export function mergeLive(legs: SharedLeg[], refreshed: (Leg | null)[]): SharedLeg[] {
  return legs.map((leg, idx) => {
    const live = refreshed[idx];
    if (!live || leg.final || live.mode !== leg.mode) return leg;
    return {
      ...leg,
      from: { ...leg.from, current: live.from.current ?? leg.from.current, platform: live.from.platform ?? leg.from.platform },
      to: { ...leg.to, current: live.to.current ?? leg.to.current, platform: live.to.platform ?? leg.to.platform },
      cancelled: live.cancelled,
      realtime: live.realtime || leg.realtime,
    };
  });
}

/** The shared trip with live values where it makes sense; the stored values when anything fails. */
export async function withLive(shared: SharedTrip, refs: RefreshRefs | null, now: Date, provider?: Refresher): Promise<SharedTrip> {
  if (!refs || refs.source === "import" || !isLiveWindow(shared, now)) return shared;
  try {
    const result = await refreshJourney(refs.refresh_token, refs.legs, provider);
    return { ...shared, legs: mergeLive(shared.legs, result.legs) };
  } catch {
    return shared;
  }
}

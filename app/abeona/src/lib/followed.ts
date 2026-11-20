/**
 * The journey as it is actually travelled once a connection from the
 * alternatives was taken: the rides before the replaced one, then the trains
 * chosen instead (abeona_trips.missed_leg_idx and replacement_legs). When the
 * replacement starts at an intermediate stop of a ride, that ride is cut off
 * there: the traveller gets off early. Pure, mirrored in Kotlin by
 * android/abeona/.../domain/Followed.kt; the cases both suites run are in
 * followed.fixtures.json.
 */
import { sameTrain } from "./intake/match.ts";
import type { Leg, StopTime } from "./transit";
import type { TravelDay } from "./travelday";

function sameStop(a: { id: string | null; name: string }, b: { id: string | null; name: string }): boolean {
  if (a.id && b.id) return a.id === b.id;
  return a.name.trim().toLowerCase() === b.name.trim().toLowerCase();
}

/** [leg] ending at its stopover [k], with the line cut at the point nearest to it. */
function cutAt(leg: Leg, k: number): Leg {
  const stop = leg.stopovers[k];
  const to: StopTime = {
    id: stop.id,
    name: stop.name,
    lat: stop.lat,
    lon: stop.lon,
    planned: stop.planned_arrival,
    current: stop.arrival,
    planned_platform: null,
    platform: null,
    cancelled: stop.cancelled,
  };
  let polyline = leg.polyline;
  if (polyline && polyline.length > 1 && typeof stop.lat === "number" && typeof stop.lon === "number") {
    let best = 0;
    let bestDistance = Infinity;
    polyline.forEach(([lon, lat], i) => {
      const d = (lat - (stop.lat as number)) ** 2 + (lon - (stop.lon as number)) ** 2;
      if (d < bestDistance) {
        bestDistance = d;
        best = i;
      }
    });
    polyline = polyline.slice(0, Math.max(best, 1) + 1);
  }
  return { ...leg, to, stopovers: leg.stopovers.slice(0, k), polyline };
}

/** The stopover of [leg] where the replacement starts, or -1. */
function stopoverAt(leg: Leg | undefined, start: { id: string | null; name: string }): number {
  if (!leg || leg.mode === "walk") return -1;
  return leg.stopovers.findIndex((s) => sameStop(s, start));
}

function sameMinute(a: string | null, b: string | null): boolean {
  return !!a && !!b && Math.floor(Date.parse(a) / 60_000) === Math.floor(Date.parse(b) / 60_000);
}

/** The platform a booked ride has at [at]: the same train, the same stop, the same planned minute. */
function bookedPlatform(booked: Leg[], line: string | null, at: StopTime, end: "from" | "to"): StopTime | null {
  const ride = booked.find(
    (b) => b.mode !== "walk" && sameTrain(b.line_name, line) && sameStop(b[end], at) && sameMinute(b[end].planned, at.planned),
  );
  const stop = ride?.[end];
  return stop && (stop.platform || stop.planned_platform) ? stop : null;
}

/**
 * A replacement ride that is a booked train (a timetable change re-routed
 * the trip around another ride) keeps the booked ride's platforms where the
 * timetable has none: the ticket's platform is better than nothing, and a
 * live one from the timetable still wins once it comes.
 */
export function withBookedPlatforms(booked: Leg[], replacement: Leg[]): Leg[] {
  return replacement.map((ride) => {
    if (ride.mode === "walk") return ride;
    const fill = (stop: StopTime, end: "from" | "to"): StopTime => {
      if (stop.platform || stop.planned_platform) return stop;
      const known = bookedPlatform(booked, ride.line_name, stop, end);
      return known ? { ...stop, planned_platform: known.planned_platform ?? known.platform, platform: known.platform ?? known.planned_platform } : stop;
    };
    const from = fill(ride.from, "from");
    const to = fill(ride.to, "to");
    return from === ride.from && to === ride.to ? ride : { ...ride, from, to };
  });
}

/**
 * The legs as travelled: [legs] unchanged without a replacement, else the
 * rides before [missedIdx] and the [replacement], with the ride the
 * replacement leaves from cut at that stop. The replacement's rides that are
 * booked trains keep the booked platforms (withBookedPlatforms).
 */
export function followedLegs(legs: Leg[], missedIdx: number | null, replacement: Leg[] | null): Leg[] {
  if (missedIdx === null || !replacement || replacement.length === 0 || missedIdx < 0 || missedIdx > legs.length) return legs;
  replacement = withBookedPlatforms(legs, replacement);
  const start = replacement[0].from;
  const prefix = legs.slice(0, missedIdx);
  // On the replaced ride itself (on board when the alternative was taken).
  const onReplaced = stopoverAt(legs[missedIdx], start);
  if (onReplaced >= 0) return [...prefix, cutAt(legs[missedIdx], onReplaced), ...replacement];
  // On the ride before it (getting off before the change).
  const last = prefix[prefix.length - 1];
  const onLast = stopoverAt(last, start);
  if (onLast >= 0) return [...prefix.slice(0, -1), cutAt(last, onLast), ...replacement];
  return [...prefix, ...replacement];
}

/**
 * The ride a connection taken now replaces: on board, the next ride (the
 * change that will not work), else the current one if there is none; before
 * a ride, the one to catch. Null when nothing is left.
 */
export function replacedRide(legs: Pick<Leg, "mode">[], day: TravelDay): number | null {
  if (day.kind === "arrived" || day.legIndex === null) return null;
  if (day.kind !== "on_board") return day.legIndex;
  const next = legs.findIndex((l, i) => i > (day.legIndex as number) && l.mode !== "walk");
  return next >= 0 ? next : day.legIndex;
}

/**
 * Taking [chosen] now, with [day] worked out on the legs as travelled: what
 * to store (missed_leg_idx, replacement_legs). With nothing taken before, it
 * replaces the ride replacedRide names, and followedLegs cuts the ride left
 * early. With a connection already taken, the new one goes into that
 * replacement at the same place, so the plan's own rides stay as they were.
 * Null when nothing is left to replace.
 */
export function takeConnection(
  legs: Leg[],
  missedIdx: number | null,
  replacement: Leg[] | null,
  day: TravelDay,
  chosen: Leg[],
): { missedIdx: number; replacement: Leg[] } | null {
  if (missedIdx === null || !replacement || replacement.length === 0) {
    const k = replacedRide(legs, day);
    return k === null ? null : { missedIdx: k, replacement: chosen };
  }
  const followed = followedLegs(legs, missedIdx, replacement);
  const k = replacedRide(followed, day);
  if (k === null) return null;
  if (k < missedIdx) return { missedIdx: k, replacement: chosen };
  return { missedIdx, replacement: followedLegs(followed.slice(missedIdx), k - missedIdx, chosen) };
}

/** A trip's legs as travelled (its replacement, if one was taken). */
export function tripFollowedLegs(trip: { legs: Leg[]; missed_leg_idx?: number | null; replacement_legs?: Leg[] | null }): Leg[] {
  return followedLegs(trip.legs, trip.missed_leg_idx ?? null, trip.replacement_legs ?? null);
}

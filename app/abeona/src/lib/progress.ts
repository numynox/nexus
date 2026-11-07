/**
 * Where a ride is right now, stop by stop: the stop it stands at or the next
 * one ahead, how many are left, how far along it is, and where on its line
 * the timetable puts it. Pure, from the clock and the live values, like the
 * travel day (travelday.ts) — and mirrored in Kotlin by
 * android/abeona/.../domain/RideProgress.kt. Change both together; the cases
 * both suites run are in progress.fixtures.json.
 */
import { distanceMetres } from "./travelday.ts";
import type { Leg, Stopover } from "./transit";

/** The part of a leg the progress reads. */
export type ProgressLeg = {
  from: Pick<Leg["from"], "id" | "name" | "lat" | "lon" | "planned" | "current">;
  to: Pick<Leg["to"], "id" | "name" | "lat" | "lon" | "planned" | "current">;
  stopovers?: Pick<Stopover, "id" | "name" | "lat" | "lon" | "planned_arrival" | "arrival" | "planned_departure" | "departure" | "cancelled">[];
  polyline?: [number, number][] | null;
};

export interface ProgressStop {
  id: string | null;
  name: string;
  lat: number | null;
  lon: number | null;
  /** Expected arrival; the ride's first stop has its departure here. */
  arrival: number;
  /** Expected departure; the ride's last stop has its arrival here. */
  departure: number;
  /** 0 at the first stop, 1 at the last, by time. */
  fraction: number;
  /** The train has left it (or, for the last stop, arrived there). */
  passed: boolean;
}

export interface RideProgress {
  /** Where the ride stops: its first stop, the stopovers it still serves, its last stop. */
  stops: ProgressStop[];
  /** The stop the train stands at now (before it leaves, at a stopover, or once arrived); null while it is moving. */
  current: number | null;
  /** The next stop the train arrives at; null once it has arrived at the last one. */
  next: number | null;
  /** Stops still ahead, the last one included: 0 once arrived. */
  stopsLeft: number;
  /** Whole minutes until the train is at [next], rounded up; null once arrived. */
  minutesToNext: number | null;
  /** 0 before it leaves, 1 once it has arrived: by time. */
  fraction: number;
  /** Where the timetable puts the train now, on its line; null without coordinates. */
  estimate: { lat: number; lon: number } | null;
}

/** Where the traveller is on the map, and what said so: the device's position, or the timetable's estimate. */
export interface MapMarker {
  lat: number;
  lon: number;
  kind: "location" | "timetable";
}

const MINUTE = 60_000;

function ms(value: string | null | undefined): number | null {
  if (!value) return null;
  const t = Date.parse(value);
  return Number.isNaN(t) ? null : t;
}

function clamp01(x: number): number {
  return Math.max(0, Math.min(1, x));
}

function hasPoint(stop: { lat: number | null; lon: number | null }): stop is { lat: number; lon: number } {
  return typeof stop.lat === "number" && typeof stop.lon === "number";
}

/** The index of the line's point nearest to (lat, lon), at or after [from]. */
function nearestIndex(line: [number, number][], lat: number, lon: number, from: number): number {
  let best = from;
  let bestDistance = Infinity;
  for (let i = from; i < line.length; i++) {
    const d = distanceMetres(lat, lon, line[i][1], line[i][0]);
    if (d < bestDistance) {
      bestDistance = d;
      best = i;
    }
  }
  return best;
}

/**
 * The point [f] of the way from a to b: along the leg's line between the
 * points nearest to each when it has one, else straight.
 */
function along(
  a: { lat: number; lon: number },
  b: { lat: number; lon: number },
  f: number,
  line: [number, number][] | null | undefined,
): { lat: number; lon: number } {
  if (line && line.length >= 2) {
    const i = nearestIndex(line, a.lat, a.lon, 0);
    const j = nearestIndex(line, b.lat, b.lon, i);
    if (j > i) {
      const part = line.slice(i, j + 1);
      const lengths = part.slice(1).map((p, k) => distanceMetres(part[k][1], part[k][0], p[1], p[0]));
      const total = lengths.reduce((s, x) => s + x, 0);
      if (total > 0) {
        let left = f * total;
        for (let k = 0; k < lengths.length; k++) {
          if (left <= lengths[k] || k === lengths.length - 1) {
            const g = lengths[k] === 0 ? 0 : clamp01(left / lengths[k]);
            return {
              lat: part[k][1] + (part[k + 1][1] - part[k][1]) * g,
              lon: part[k][0] + (part[k + 1][0] - part[k][0]) * g,
            };
          }
          left -= lengths[k];
        }
      }
    }
  }
  return { lat: a.lat + (b.lat - a.lat) * f, lon: a.lon + (b.lon - a.lon) * f };
}

/**
 * The progress of [leg] at [now]. Cancelled stopovers and stopovers without
 * any time are left out: the train does not stop there, or nobody knows when.
 * Null when the ride's own ends have no time.
 */
export function rideProgress(leg: ProgressLeg, now: number): RideProgress | null {
  const start = ms(leg.from.current) ?? ms(leg.from.planned);
  const end = ms(leg.to.current) ?? ms(leg.to.planned);
  if (start === null || end === null) return null;
  const span = Math.max(end - start, 1);

  const raw: Omit<ProgressStop, "fraction" | "passed">[] = [
    { id: leg.from.id, name: leg.from.name, lat: leg.from.lat, lon: leg.from.lon, arrival: start, departure: start },
  ];
  for (const s of leg.stopovers ?? []) {
    if (s.cancelled) continue;
    const arrival = ms(s.arrival) ?? ms(s.planned_arrival);
    const departure = ms(s.departure) ?? ms(s.planned_departure);
    const a = arrival ?? departure;
    if (a === null) continue;
    raw.push({ id: s.id, name: s.name, lat: s.lat, lon: s.lon, arrival: a, departure: Math.max(departure ?? a, a) });
  }
  raw.push({ id: leg.to.id, name: leg.to.name, lat: leg.to.lat, lon: leg.to.lon, arrival: end, departure: end });

  const last = raw.length - 1;
  const stops: ProgressStop[] = raw.map((s, i) => ({
    ...s,
    fraction: clamp01((s.arrival - start) / span),
    passed: i === last ? now >= s.arrival : now >= s.departure,
  }));

  // The first stop not yet left is where the train is, or what it heads for.
  let current: number | null = null;
  let next: number | null = null;
  if (now < start) {
    current = 0;
    next = last > 0 ? 1 : null;
  } else if (now >= end) {
    current = last;
  } else {
    const k = stops.findIndex((s, i) => i > 0 && now < (i === last ? s.arrival : s.departure));
    const i = k === -1 ? last : k;
    if (i < last && now >= stops[i].arrival) {
      current = i;
      next = i + 1;
    } else {
      next = i;
    }
  }

  let estimate: { lat: number; lon: number } | null = null;
  if (current !== null) {
    const s = stops[current];
    estimate = hasPoint(s) ? { lat: s.lat, lon: s.lon } : null;
  } else if (next !== null) {
    const a = stops[next - 1];
    const b = stops[next];
    if (hasPoint(a) && hasPoint(b)) {
      const f = clamp01((now - a.departure) / Math.max(b.arrival - a.departure, 1));
      estimate = along(a, b, f, leg.polyline);
    }
  }

  return {
    stops,
    current,
    next,
    stopsLeft: next === null ? 0 : stops.length - next,
    minutesToNext: next === null ? null : Math.max(0, Math.ceil((stops[next].arrival - now) / MINUTE)),
    fraction: clamp01((now - start) / span),
    estimate,
  };
}

/**
 * The travel day: where someone is on their trip right now — before leaving,
 * at the station, on board a ride, changing, or arrived — from the clock and
 * the live values, and on Android also from the phone's position and the
 * train's on-board Wi-Fi. Each state has one hero card on both clients.
 *
 * Written twice on purpose, like transfer.ts: here and in android/abeona
 * (domain/TravelDay.kt). travelday.fixtures.json holds the cases both
 * clients' tests run; change a rule in both files and its case there once
 * (.agents/brain/decisions/2026-11-03-abeona-fare-rules-and-device-signals.md).
 *
 * Kept free of runtime imports beyond transfer.ts so `node --test` runs it.
 */
import { effectiveTime, transfers } from "./transfer.ts";
import type { Leg, Stopover } from "./transit";

export type TravelDayKind = "before_leaving" | "at_station" | "on_board" | "changing" | "arrived";

/** What decided the state: the clock and live values, the phone's position, or the train's Wi-Fi. */
export type TravelDaySource = "time" | "location" | "onboard";

export interface Position {
  lat: number;
  lon: number;
  /** Ground speed; null when the fix has none. */
  speedKmh: number | null;
}

/** Optional device signals; the web has none of them. */
export interface TravelSignals {
  /** The ride the on-board Wi-Fi (ICE Portal, ÖBB Railnet) says this phone is on. */
  onboardLegIndex?: number | null;
  /** Where the phone is, during a trip, with the live companion running. */
  position?: Position | null;
}

export interface TravelDay {
  kind: TravelDayKind;
  /**
   * The ride the state is about: the one to catch (before leaving, at the
   * station, changing) or the one on board. Null once arrived.
   */
  legIndex: number | null;
  /** Changing: the ride just left. */
  fromLegIndex: number | null;
  /** Before leaving and at the station: the first ride's departure minus the time to the station. */
  leaveAt: string | null;
  /** Before leaving: whole minutes until leaveAt, rounded up. */
  minutesToLeave: number | null;
  /** Until the departure of the ride to catch, rounded up; negative once it has left. */
  minutesToDeparture: number | null;
  /** On board: until the ride's arrival, rounded up. */
  minutesToArrival: number | null;
  /** The ride the state is about is cancelled. */
  cancelled: boolean;
  /** A device signal says the traveller is not on the ride the clock says they should be. */
  missedChange: boolean;
  source: TravelDaySource;
}

/** The part of a leg the travel day reads; saved legs and search results both fit. */
export type DayLeg = Pick<Leg, "mode" | "cancelled"> & {
  from: Pick<Leg["from"], "id" | "name" | "lat" | "lon" | "planned" | "current" | "planned_platform" | "platform">;
  to: Pick<Leg["to"], "id" | "name" | "lat" | "lon" | "planned" | "current" | "planned_platform" | "platform">;
  stopovers?: Pick<Stopover, "id" | "name" | "lat" | "lon" | "planned_arrival" | "arrival">[];
  polyline?: [number, number][] | null;
};

/** Within this many metres of a stop counts as being there. */
export const NEAR_STOP_METRES = 300;
/** Within this many metres of a leg's line, and faster than [MOVING_KMH], counts as riding it. */
export const ON_LINE_METRES = 1000;
export const MOVING_KMH = 30;
/** How long after a departure a phone still standing at the stop counts as having missed it. */
export const MISSED_AFTER_MINUTES = 2;
/** The time to the station when none is set. */
export const DEFAULT_STATION_MINUTES = 15;

const MINUTE = 60_000;

function ms(value: string | null | undefined): number | null {
  if (!value) return null;
  const t = Date.parse(value);
  return Number.isNaN(t) ? null : t;
}

/** Whole minutes from now to then, rounded up (JavaScript's Math.ceil). */
function minutesUntil(now: number, then: number): number {
  return Math.ceil((then - now) / MINUTE);
}

/** Great-circle distance in metres. */
export function distanceMetres(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const r = 6_371_000;
  const rad = Math.PI / 180;
  const dLat = (bLat - aLat) * rad;
  const dLon = (bLon - aLon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/**
 * Metres from a point to a line of [lon, lat] points, on a flat projection
 * around the point — plenty for "is the phone on this railway".
 */
export function distanceToLineMetres(lat: number, lon: number, line: [number, number][]): number | null {
  if (line.length === 0) return null;
  const rad = Math.PI / 180;
  const kx = 6_371_000 * rad * Math.cos(lat * rad);
  const ky = 6_371_000 * rad;
  const xy = line.map(([plon, plat]) => [(plon - lon) * kx, (plat - lat) * ky]);
  if (xy.length === 1) return Math.hypot(xy[0][0], xy[0][1]);
  let best = Infinity;
  for (let i = 0; i + 1 < xy.length; i++) {
    const [ax, ay] = xy[i];
    const [bx, by] = xy[i + 1];
    const dx = bx - ax;
    const dy = by - ay;
    const len = dx * dx + dy * dy;
    const t = len === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len));
    best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
  }
  return best;
}

function rideIndices(legs: DayLeg[]): number[] {
  return legs.flatMap((leg, i) => (leg.mode === "walk" ? [] : [i]));
}

function state(kind: TravelDayKind, legs: DayLeg[], now: number, fields: Partial<TravelDay> = {}): TravelDay {
  const legIndex = fields.legIndex ?? null;
  const leg = legIndex === null ? null : legs[legIndex];
  const departure = leg ? ms(effectiveTime(leg.from)) : null;
  const arrival = leg ? ms(effectiveTime(leg.to)) : null;
  return {
    kind,
    legIndex,
    fromLegIndex: null,
    leaveAt: null,
    minutesToLeave: null,
    minutesToDeparture: kind !== "on_board" && kind !== "arrived" && departure !== null ? minutesUntil(now, departure) : null,
    minutesToArrival: kind === "on_board" && arrival !== null ? minutesUntil(now, arrival) : null,
    cancelled: leg?.cancelled ?? false,
    missedChange: false,
    source: "time",
    ...fields,
  };
}

/** The state from the clock and the live values alone. */
function byTime(legs: DayLeg[], now: number, stationMinutes: number): TravelDay {
  const rides = rideIndices(legs);
  if (rides.length === 0) return state("arrived", legs, now);

  const last = ms(effectiveTime(legs[rides[rides.length - 1]].to));
  if (last !== null && now >= last) return state("arrived", legs, now);

  const first = rides[0];
  const departure = ms(effectiveTime(legs[first].from));
  if (departure === null) return state("before_leaving", legs, now, { legIndex: first });

  const leaveAt = departure - Math.max(0, stationMinutes) * MINUTE;
  const leave = { legIndex: first, leaveAt: new Date(leaveAt).toISOString() };
  if (now < leaveAt) return state("before_leaving", legs, now, { ...leave, minutesToLeave: minutesUntil(now, leaveAt) });
  if (now < departure) return state("at_station", legs, now, leave);

  for (let k = 0; k < rides.length; k++) {
    const i = rides[k];
    const arrival = ms(effectiveTime(legs[i].to));
    if (arrival === null || now < arrival) return state("on_board", legs, now, { legIndex: i });
    const j = rides[k + 1];
    if (j === undefined) break;
    const next = ms(effectiveTime(legs[j].from));
    if (next === null || now < next) return state("changing", legs, now, { legIndex: j, fromLegIndex: i });
  }
  return state("arrived", legs, now);
}

function near(position: Position, stop: { lat: number | null; lon: number | null }): boolean {
  if (stop.lat === null || stop.lon === null) return false;
  return distanceMetres(position.lat, position.lon, stop.lat, stop.lon) <= NEAR_STOP_METRES;
}

function moving(position: Position): boolean {
  return position.speedKmh !== null && position.speedKmh > MOVING_KMH;
}

function onLine(position: Position, leg: DayLeg): boolean {
  if (!moving(position)) return false;
  const line: [number, number][] = leg.polyline && leg.polyline.length > 0
    ? leg.polyline
    : leg.from.lat !== null && leg.from.lon !== null && leg.to.lat !== null && leg.to.lon !== null
      ? [[leg.from.lon, leg.from.lat], [leg.to.lon, leg.to.lat]]
      : [];
  const d = distanceToLineMetres(position.lat, position.lon, line);
  return d !== null && d <= ON_LINE_METRES;
}

/**
 * Where the traveller is on their trip at `now`.
 *
 * By the clock (current times, else planned):
 * - no rides, or the last ride has arrived → arrived
 * - before the first ride's departure minus `stationMinutes` → before_leaving
 * - before that departure → at_station
 * - before a ride's arrival → on_board that ride
 * - between a ride's arrival and the next ride's departure → changing
 *
 * Then the device signals, strongest first:
 * - on-board Wi-Fi matched ride k → on_board k; missedChange when the ride
 *   after k has already left.
 * - position, only to confirm or correct the clock:
 *   before leaving or at the station, near the first stop → at_station;
 *   on board, moving along that ride's line → confirmed;
 *   on board, standing at the ride's first stop at least
 *   MISSED_AFTER_MINUTES after it left → missedChange (changing, or at the
 *   station for the first ride);
 *   changing, moving along the previous ride's line → still on board it;
 *   changing, near the next ride's stop → confirmed.
 */
export function travelDay(
  legs: DayLeg[],
  now: number,
  stationMinutes: number = DEFAULT_STATION_MINUTES,
  signals: TravelSignals = {},
): TravelDay {
  const day = byTime(legs, now, stationMinutes);
  const rides = rideIndices(legs);

  const k = signals.onboardLegIndex;
  if (k !== null && k !== undefined && rides.includes(k)) {
    const after = rides.find((i) => i > k);
    const leftAt = after === undefined ? null : ms(effectiveTime(legs[after].from));
    return state("on_board", legs, now, {
      legIndex: k,
      missedChange: leftAt !== null && now > leftAt,
      source: "onboard",
    });
  }

  const position = signals.position;
  if (!position || day.kind === "arrived" || day.legIndex === null) return day;
  const leg = legs[day.legIndex];

  switch (day.kind) {
    case "before_leaving":
    case "at_station":
      if (near(position, leg.from)) {
        return { ...state("at_station", legs, now, { legIndex: day.legIndex, leaveAt: day.leaveAt }), source: "location" };
      }
      return day;
    case "on_board": {
      if (onLine(position, leg)) return { ...day, source: "location" };
      const departure = ms(effectiveTime(leg.from));
      if (
        !moving(position) && near(position, leg.from) && departure !== null &&
        now >= departure + MISSED_AFTER_MINUTES * MINUTE
      ) {
        const previous = [...rides].reverse().find((i) => i < day.legIndex!);
        return previous === undefined
          ? state("at_station", legs, now, { legIndex: day.legIndex, missedChange: true, source: "location" })
          : state("changing", legs, now, { legIndex: day.legIndex, fromLegIndex: previous, missedChange: true, source: "location" });
      }
      return day;
    }
    case "changing":
      if (day.fromLegIndex !== null && onLine(position, legs[day.fromLegIndex])) {
        return state("on_board", legs, now, { legIndex: day.fromLegIndex, source: "location" });
      }
      if (near(position, leg.from)) return { ...day, source: "location" };
      return day;
  }
}

/** Where an alternatives search starts: a stop, and when the traveller can be there. */
export interface AlternativesOrigin {
  id: string | null;
  name: string;
  lat: number | null;
  lon: number | null;
  /** ISO instant: now, or the arrival at the train's next stop. */
  time: string;
}

/**
 * Before leaving, at the station or changing: the stop of the ride to catch,
 * from now. On board: the train's next stop (the first stopover still ahead,
 * else where the ride ends), from its arrival there. Arrived: none.
 */
export function alternativesOrigin(legs: DayLeg[], day: TravelDay, now: number): AlternativesOrigin | null {
  if (day.kind === "arrived" || day.legIndex === null) return null;
  const leg = legs[day.legIndex];
  const nowIso = new Date(now).toISOString();
  if (day.kind !== "on_board") {
    return { id: leg.from.id, name: leg.from.name, lat: leg.from.lat, lon: leg.from.lon, time: nowIso };
  }
  for (const stop of leg.stopovers ?? []) {
    const at = ms(stop.arrival ?? stop.planned_arrival);
    if (at !== null && at > now) {
      return { id: stop.id, name: stop.name, lat: stop.lat, lon: stop.lon, time: new Date(at).toISOString() };
    }
  }
  const end = ms(effectiveTime(leg.to));
  return {
    id: leg.to.id,
    name: leg.to.name,
    lat: leg.to.lat,
    lon: leg.to.lon,
    time: new Date(end !== null && end > now ? end : now).toISOString(),
  };
}

export type AlternativesReason = "missed" | "cancelled" | "at_risk";

/**
 * Why the trip page should offer alternatives by itself, or null: a missed
 * change first, then a cancelled ride still ahead (the current one counts),
 * then a change still ahead that is at risk. Arrived trips never.
 */
export function alternativesReason(legs: DayLeg[], day: TravelDay): AlternativesReason | null {
  if (day.kind === "arrived" || day.legIndex === null) return null;
  if (day.missedChange) return "missed";
  const from = day.legIndex;
  if (legs.some((leg, i) => i >= from && leg.mode !== "walk" && leg.cancelled)) return "cancelled";
  const ahead = transfers(legs).filter((t) => (day.kind === "on_board" ? t.fromIndex >= from : t.toIndex >= from));
  if (ahead.some((t) => t.confidence === "at_risk")) return "at_risk";
  return null;
}

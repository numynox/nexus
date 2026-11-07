/**
 * Pure helpers over a list of legs — the same for a search result and a saved
 * trip, because both are in the transit contract's shape.
 */
import { delayMinutes, effectiveTime, finalArrivalDelay, overallConfidence, transfers } from "./transfer";
import type { Leg, StopTime } from "./transit";

export interface JourneySummary {
  departure: StopTime;
  arrival: StopTime;
  /** Legs that are rides, not walks. */
  rides: Leg[];
  changes: number;
  /** Planned door-to-door minutes. */
  plannedMinutes: number;
  departureDelay: number;
  arrivalDelay: number;
  cancelled: boolean;
  confidence: ReturnType<typeof overallConfidence>;
  hasRealtime: boolean;
}

export function summarise(legs: Leg[]): JourneySummary {
  const rides = legs.filter((l) => l.mode !== "walk");
  const first = legs[0];
  const last = legs[legs.length - 1];
  const firstRide = rides[0] ?? first;
  const planned =
    first?.from.planned && last?.to.planned
      ? Math.round((Date.parse(last.to.planned) - Date.parse(first.from.planned)) / 60_000)
      : 0;
  return {
    departure: first.from,
    arrival: last.to,
    rides,
    changes: Math.max(0, rides.length - 1),
    plannedMinutes: planned,
    departureDelay: delayMinutes(firstRide.from.planned, firstRide.from.current),
    arrivalDelay: finalArrivalDelay(legs),
    cancelled: rides.some((l) => l.cancelled),
    confidence: overallConfidence(transfers(legs)),
    hasRealtime: legs.some((l) => l.realtime),
  };
}

/** When the trip will really be over: the last leg's arrival, live if known. */
export function effectiveArrival(legs: Leg[]): number {
  const last = legs[legs.length - 1];
  const t = last ? effectiveTime(last.to) : null;
  return t ? Date.parse(t) : 0;
}

const HOUR = 3600_000;

/**
 * How often a page refreshes the trips in their live window while it is
 * visible: as often as abeona-transit's cache lets a refresh be new (60 s).
 */
export const LIVE_REFRESH_MS = 60_000;

/**
 * A trip is "live" — refreshed on open and every minute — from 24 hours
 * before departure until it has arrived (and at most 12 hours after
 * departure, so a forgotten trip stops polling).
 */
export function isLiveWindow(legs: Leg[], now = Date.now()): boolean {
  if (legs.length === 0) return false;
  const departure = Date.parse(legs[0].from.planned ?? "");
  if (Number.isNaN(departure)) return false;
  const arrival = effectiveArrival(legs);
  return departure - now <= 24 * HOUR && now - departure <= 12 * HOUR && now <= arrival + 15 * 60_000;
}

export function hasArrived(legs: Leg[], now = Date.now()): boolean {
  const arrival = effectiveArrival(legs);
  return arrival > 0 && arrival < now;
}

/** bahn.de, which DB Navigator opens when installed: for proofs with no connection, like a BahnCard. */
export const DB_NAVIGATOR_HOME = "https://www.bahn.de/";

/**
 * The connection in DB's own search, which DB Navigator opens when it is
 * installed (bahn.de links are app links on Android); some ticket proofs only
 * count in DB's app. Station names and the departure time are all it needs.
 */
export function dbNavigatorUrl(legs: Leg[], travelClass: 1 | 2 = 2): string {
  const first = legs[0];
  const last = legs[legs.length - 1];
  if (!first || !last) return DB_NAVIGATOR_HOME;
  const when = first.from.planned ? new Date(first.from.planned) : new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const local = `${when.getFullYear()}-${pad(when.getMonth() + 1)}-${pad(when.getDate())}T${pad(when.getHours())}:${pad(when.getMinutes())}:00`;
  const params = [
    "sts=true",
    `so=${encodeURIComponent(first.from.name)}`,
    `zo=${encodeURIComponent(last.to.name)}`,
    `kl=${travelClass}`,
    `hd=${encodeURIComponent(local)}`,
    "hza=D",
  ];
  return `https://www.bahn.de/buchung/fahrplan/suche#${params.join("&")}`;
}

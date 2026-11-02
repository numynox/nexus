// Transitous (https://transitous.org), a community-run MOTIS instance over
// open GTFS and GTFS-RT feeds for most of Europe. Free for non-commercial use
// with attribution, best effort, no key. For Germany it carries DB's realtime
// (delays, platforms, cancellations) for the current day.
//
// bahn.de's own API refuses requests from data-centre addresses
// ("OPS_BLOCKED"), so it cannot be the server-side source
// (decisions/2026-11-02-abeona-transit-providers.md).

import type { Journey, Leg, LegMode, Station, Stopover, StopTime } from "./contract.ts";
import { decodePolyline, simplify } from "./polyline.ts";

const BASE = "https://api.transitous.org";
const USER_AGENT = "Nexus-Abeona/1.0 (https://github.com/numynox/nexus)";
const TIMEOUT_MS = 10000;

const RAIL_MODES = new Set([
  "RAIL",
  "HIGHSPEED_RAIL",
  "LONG_DISTANCE",
  "NIGHT_RAIL",
  "REGIONAL_FAST_RAIL",
  "REGIONAL_RAIL",
  "SUBURBAN",
]);
const BUS_MODES = new Set(["BUS", "COACH"]);

// deno-lint-ignore no-explicit-any
type Json = any;

export class ProviderError extends Error {
  constructor(message: string, readonly status = 502) {
    super(message);
  }
}

async function get(path: string, params: Record<string, string | undefined>): Promise<Json> {
  const url = new URL(path, BASE);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) url.searchParams.set(key, value);
  }
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
      signal: controller.signal,
    });
    if (response.status === 404) throw new ProviderError("Not found", 404);
    if (response.status === 429) throw new ProviderError("Transitous is rate limiting, try again shortly", 429);
    if (!response.ok) throw new ProviderError(`Transitous answered HTTP ${response.status}`);
    return await response.json();
  } catch (error) {
    if (error instanceof ProviderError) throw error;
    if (error instanceof Error && error.name === "AbortError") {
      throw new ProviderError("Transitous did not respond in time", 504);
    }
    throw new ProviderError(`Could not reach Transitous: ${error instanceof Error ? error.message : error}`);
  } finally {
    clearTimeout(timeout);
  }
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value.trim() : null;
}

function num(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function legMode(mode: string): LegMode {
  if (mode === "WALK") return "walk";
  if (RAIL_MODES.has(mode)) return "train";
  if (BUS_MODES.has(mode)) return "bus";
  return "other";
}

/** "ICE 932" → "ICE", "S 8" → "S", "RE4" → "RE". */
export function productOf(displayName: string | null): string | null {
  if (!displayName) return null;
  const match = /^([A-Za-zÄÖÜäöü]{1,5})(?=\s|\d|$)/.exec(displayName.trim());
  if (!match) return null;
  const product = match[1].toUpperCase();
  return product === "BUS" ? "Bus" : product;
}

function hasRealtime(reason: unknown): boolean {
  return typeof reason === "string" && reason !== "NO_RT_DATA" && reason !== "SCHEDULED";
}

function stopTime(place: Json, kind: "departure" | "arrival"): StopTime {
  const planned = kind === "departure"
    ? str(place?.scheduledDeparture) ?? str(place?.departure)
    : str(place?.scheduledArrival) ?? str(place?.arrival);
  const reason = kind === "departure" ? place?.departureTimestampReason : place?.arrivalTimestampReason;
  return {
    id: str(place?.parentId) ?? str(place?.stopId),
    name: str(place?.name) ?? "Unknown stop",
    lat: num(place?.lat),
    lon: num(place?.lon),
    planned,
    current: hasRealtime(reason) ? str(place?.[kind]) : null,
    planned_platform: str(place?.scheduledTrack),
    platform: str(place?.track) ?? str(place?.scheduledTrack),
    cancelled: place?.cancelled === true,
  };
}

function stopover(place: Json): Stopover {
  return {
    id: str(place?.parentId) ?? str(place?.stopId),
    name: str(place?.name) ?? "Unknown stop",
    lat: num(place?.lat),
    lon: num(place?.lon),
    planned_arrival: str(place?.scheduledArrival),
    arrival: hasRealtime(place?.arrivalTimestampReason) ? str(place?.arrival) : null,
    planned_departure: str(place?.scheduledDeparture),
    departure: hasRealtime(place?.departureTimestampReason) ? str(place?.departure) : null,
    cancelled: place?.cancelled === true,
  };
}

function polylineOf(leg: Json): [number, number][] | null {
  const geometry = leg?.legGeometry;
  if (!geometry || typeof geometry.points !== "string" || geometry.points === "") return null;
  const points = decodePolyline(geometry.points, typeof geometry.precision === "number" ? geometry.precision : 6);
  return points.length >= 2 ? simplify(points) : null;
}

function normaliseLeg(leg: Json): Leg {
  const mode = legMode(String(leg?.mode ?? ""));
  const lineName = mode === "walk" ? null : str(leg?.displayName) ?? str(leg?.tripShortName) ?? str(leg?.routeShortName);
  return {
    mode,
    product: mode === "walk" ? null : productOf(lineName),
    line_name: lineName,
    trip_ref: mode === "walk" ? null : str(leg?.tripId),
    headsign: str(leg?.headsign),
    operator: str(leg?.agencyName),
    from: stopTime(leg?.from, "departure"),
    to: stopTime(leg?.to, "arrival"),
    stopovers: Array.isArray(leg?.intermediateStops) ? leg.intermediateStops.map(stopover) : [],
    polyline: mode === "walk" ? null : polylineOf(leg),
    cancelled: leg?.cancelled === true,
    realtime: leg?.realTime === true,
  };
}

function normaliseItinerary(itinerary: Json): Journey {
  const legs: Leg[] = (Array.isArray(itinerary?.legs) ? itinerary.legs : []).map(normaliseLeg);
  // A walk to or from the very first or last stop is noise for a train trip.
  while (legs.length > 1 && legs[0].mode === "walk") legs.shift();
  while (legs.length > 1 && legs[legs.length - 1].mode === "walk") legs.pop();
  return {
    provider: "transitous",
    refresh_token: str(itinerary?.id),
    legs,
  };
}

export async function locations(query: string): Promise<Station[]> {
  const results: Json[] = await get("/api/v1/geocode", { text: query, type: "STOP" });
  const seen = new Set<string>();
  const stations: Station[] = [];
  for (const r of Array.isArray(results) ? results : []) {
    const modes: string[] = Array.isArray(r?.modes) ? r.modes : [];
    if (!modes.some((m) => RAIL_MODES.has(m))) continue;
    const name = str(r?.name);
    const id = str(r?.id);
    if (!name || !id) continue;
    const key = `${name}|${Math.round((r.lat ?? 0) * 100)}|${Math.round((r.lon ?? 0) * 100)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    stations.push({ id, name, lat: r.lat, lon: r.lon, country: str(r?.country) });
    if (stations.length >= 8) break;
  }
  return stations;
}

export async function journeys(input: {
  from: string;
  to: string;
  time: string;
  arriveBy: boolean;
  products: "all" | "regional";
  cursor?: string;
}): Promise<{ journeys: Journey[]; next_cursor: string | null; previous_cursor: string | null }> {
  const modes = input.products === "regional" ? "REGIONAL_RAIL,REGIONAL_FAST_RAIL,SUBURBAN" : "RAIL";
  const payload = await get("/api/v6/plan", {
    fromPlace: input.from,
    toPlace: input.to,
    time: input.time,
    arriveBy: String(input.arriveBy),
    transitModes: modes,
    numItineraries: "6",
    detailedTransfers: "false",
    pageCursor: input.cursor,
  });
  const itineraries: Json[] = Array.isArray(payload?.itineraries) ? payload.itineraries : [];
  return {
    journeys: itineraries.map(normaliseItinerary).filter((j) => j.legs.some((l) => l.mode !== "walk")),
    next_cursor: str(payload?.nextPageCursor),
    previous_cursor: str(payload?.previousPageCursor),
  };
}

/** The whole journey again, live. Experimental upstream, so it may fail. */
export async function refreshItinerary(token: string): Promise<Journey> {
  const payload = await get("/api/v6/refresh-itinerary", {
    itineraryId: token,
    detailedTransfers: "false",
    detailedLegs: "false",
  });
  return normaliseItinerary(payload);
}

function sameStop(place: Json, id: string | null, name: string): boolean {
  if (id && (place?.stopId === id || place?.parentId === id)) return true;
  return str(place?.name)?.toLowerCase() === name.toLowerCase();
}

/**
 * One leg again, live: fetch its whole trip and cut out the part between the
 * saved boarding and alighting stops.
 */
export async function refreshLeg(saved: {
  trip_ref: string;
  from_id: string | null;
  to_id: string | null;
  from_name: string;
  to_name: string;
}): Promise<Leg | null> {
  const itinerary = await get("/api/v6/trip", { tripId: saved.trip_ref, detailedLegs: "false" });
  const leg = Array.isArray(itinerary?.legs) ? itinerary.legs[0] : null;
  if (!leg) return null;
  const stops: Json[] = [leg.from, ...(Array.isArray(leg.intermediateStops) ? leg.intermediateStops : []), leg.to];
  const start = stops.findIndex((s) => sameStop(s, saved.from_id, saved.from_name));
  if (start === -1) return null;
  const endOffset = stops.slice(start + 1).findIndex((s) => sameStop(s, saved.to_id, saved.to_name));
  if (endOffset === -1) return null;
  const end = start + 1 + endOffset;
  const normalised = normaliseLeg({
    ...leg,
    from: stops[start],
    to: stops[end],
    intermediateStops: stops.slice(start + 1, end),
    legGeometry: null,
  });
  return normalised;
}

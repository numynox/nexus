// Träwelling (https://traewelling.de), the check-in diary for trains: its
// statuses become Abeona legs. Each status is one ride with real departure
// and arrival times (Träwelling records them from its own realtime source,
// or the user set them by hand), so an imported trip needs no refresh.
//
// API: https://traewelling.de/api/documentation (OpenAPI "1.0.0 - alpha",
// checked 2026-11-03). A personal access token from Settings → Applications
// is sent as "Authorization: Bearer <token>". GET /v1/auth/user names the
// user; GET /v1/user/{username}/statuses?page=n lists their statuses, newest
// first, 15 per page. Every field is read defensively: the API is marked
// alpha and its resources carry deprecated fields.

import type { Leg, LegMode, StopTime } from "../abeona-transit/contract.ts";
import { productOf } from "../abeona-transit/transitous.ts";

export const API = "https://traewelling.de/api";
const USER_AGENT = "Nexus-Abeona/1.0 (https://github.com/numynox/nexus)";
const TIMEOUT_MS = 10000;
/** Pages of 15; 40 pages is 600 check-ins, plenty for a year of commuting. */
export const MAX_PAGES = 40;
/** Rides at most this far apart, where one ends and the next starts, are one trip. */
export const CHANGE_MINUTES = 90;

// deno-lint-ignore no-explicit-any
type Json = any;

export class ImportError extends Error {
  constructor(message: string, readonly status = 502) {
    super(message);
  }
}

/** One trip to save, its legs in the transit contract's shape. */
export interface ImportedTrip {
  /** "traewelling:<status ids>", for the client to tell trips apart. */
  external_id: string;
  origin: string;
  destination: string;
  planned_departure: string;
  planned_arrival: string;
  /** Minutes late at the destination, from the real arrival; null without one. */
  arrival_delay_minutes: number | null;
  legs: (Leg & { distance_km?: number | null })[];
  /** A trip with the same origin and planned departure is saved already. */
  duplicate: boolean;
}

export interface ImportResult {
  trips: ImportedTrip[];
  /** Check-ins read in the date range. */
  statuses: number;
  /** Check-ins left out: planes, taxis, or unreadable. */
  skipped: number;
  /** The range had more check-ins than one import reads. */
  truncated: boolean;
}

export type Fetcher = (url: string, init: RequestInit) => Promise<Response>;

async function getJson(fetcher: Fetcher, path: string, token: string): Promise<Json> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetcher(`${API}${path}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json", "User-Agent": USER_AGENT },
      signal: controller.signal,
    });
    if (response.status === 401 || response.status === 403) {
      throw new ImportError("Träwelling did not accept the token. Create a new one under Settings → Applications.", 401);
    }
    if (response.status === 429) throw new ImportError("Träwelling is rate limiting, try again in a minute.", 429);
    if (!response.ok) throw new ImportError(`Träwelling answered HTTP ${response.status}.`);
    return await response.json();
  } catch (error) {
    if (error instanceof ImportError) throw error;
    if (error instanceof Error && error.name === "AbortError") throw new ImportError("Träwelling did not respond in time.", 504);
    throw new ImportError("Could not reach Träwelling.");
  } finally {
    clearTimeout(timeout);
  }
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value.trim() : null;
}

function num(value: unknown): number | null {
  const n = typeof value === "string" ? Number(value) : value;
  return typeof n === "number" && Number.isFinite(n) ? n : null;
}

/** An ISO instant in UTC, or null. */
function instant(value: unknown): string | null {
  const s = str(value);
  if (!s) return null;
  const t = Date.parse(s);
  return Number.isNaN(t) ? null : new Date(t).toISOString();
}

const TRAIN = new Set(["nationalExpress", "national", "regionalExp", "regional", "suburban"]);
const SKIP = new Set(["plane", "taxi", "freightTrain"]);

function modeOf(category: string | null): LegMode | null {
  if (!category) return "other";
  if (SKIP.has(category)) return null;
  if (TRAIN.has(category)) return "train";
  if (category === "bus") return "bus";
  return "other";
}

function stop(place: Json, kind: "departure" | "arrival", manual: unknown): StopTime {
  const station = place?.station ?? {};
  const planned = instant(kind === "departure" ? place?.departurePlanned : place?.arrivalPlanned);
  const real = instant(manual) ?? instant(kind === "departure" ? place?.departureReal : place?.arrivalReal);
  const plannedPlatform = str(kind === "departure" ? place?.departurePlatformPlanned : place?.arrivalPlatformPlanned);
  return {
    id: null,
    name: str(station?.name) ?? str(place?.name) ?? "Unknown stop",
    lat: num(station?.latitude),
    lon: num(station?.longitude),
    planned,
    current: real,
    planned_platform: plannedPlatform,
    platform: str(kind === "departure" ? place?.departurePlatformReal : place?.arrivalPlatformReal) ?? plannedPlatform,
    cancelled: place?.cancelled === true,
    country: null,
  };
}

/** One status as a leg; null for what Abeona does not record (planes, taxis) or cannot read. */
export function legOf(status: Json): (Leg & { distance_km: number | null; status_id: string }) | null {
  const checkin = status?.checkin;
  if (!checkin) return null;
  const mode = modeOf(str(checkin.category));
  if (!mode) return null;
  const lineName = str(checkin.manualJourneyNumber) ?? str(checkin.lineName) ?? str(checkin.number);
  const from = stop(checkin.origin, "departure", checkin.manualDeparture);
  const to = stop(checkin.destination, "arrival", checkin.manualArrival);
  if (!from.planned || !to.planned) return null;
  const meters = num(checkin.distance);
  return {
    mode,
    product: mode === "bus" ? "Bus" : productOf(lineName),
    line_name: lineName,
    trip_ref: null,
    headsign: null,
    operator: str(checkin.operator?.name),
    from,
    to,
    stopovers: [],
    polyline: null,
    cancelled: from.cancelled || to.cancelled,
    realtime: from.current !== null || to.current !== null,
    distance_km: meters === null ? null : Math.round(meters / 100) / 10,
    status_id: String(status?.id ?? ""),
  };
}

const minutes = (a: string, b: string) => (Date.parse(b) - Date.parse(a)) / 60000;
const expected = (s: StopTime) => s.current ?? s.planned!;

/**
 * Legs, oldest first, grouped into trips: the next ride starts where the
 * last one ended (same station name) within CHANGE_MINUTES.
 */
export function groupTrips(legs: (Leg & { distance_km: number | null; status_id: string })[]): ImportedTrip[] {
  const sorted = [...legs].sort((a, b) => Date.parse(a.from.planned!) - Date.parse(b.from.planned!));
  const groups: (typeof legs)[] = [];
  for (const leg of sorted) {
    const current = groups[groups.length - 1];
    const last = current?.[current.length - 1];
    if (
      last &&
      last.to.name.toLowerCase() === leg.from.name.toLowerCase() &&
      minutes(expected(last.to), expected(leg.from)) >= -5 &&
      minutes(expected(last.to), expected(leg.from)) <= CHANGE_MINUTES
    ) {
      current.push(leg);
    } else {
      groups.push([leg]);
    }
  }
  return groups.map((group) => {
    const first = group[0];
    const last = group[group.length - 1];
    const delay = last.to.current ? Math.max(0, Math.round(minutes(last.to.planned!, last.to.current))) : null;
    return {
      external_id: `traewelling:${group.map((l) => l.status_id).join(",")}`,
      origin: first.from.name,
      destination: last.to.name,
      planned_departure: first.from.planned!,
      planned_arrival: last.to.planned!,
      arrival_delay_minutes: delay,
      legs: group.map(({ status_id: _, ...leg }) => leg),
      duplicate: false,
    };
  });
}

/**
 * The user's check-ins whose planned departure lies in [from, to] (dates,
 * inclusive, in UTC), as trips. Reads newest first and stops at the first
 * page that goes past `from`.
 */
export async function importStatuses(
  token: string,
  from: string,
  to: string,
  fetcher: Fetcher = fetch,
): Promise<ImportResult> {
  const me = await getJson(fetcher, "/v1/auth/user", token);
  const username = str(me?.data?.username);
  if (!username) throw new ImportError("Träwelling did not say whose token this is.");

  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T23:59:59Z`);
  const legs: (Leg & { distance_km: number | null; status_id: string })[] = [];
  let statuses = 0;
  let skipped = 0;
  let truncated = false;

  for (let page = 1; ; page++) {
    if (page > MAX_PAGES) {
      truncated = true;
      break;
    }
    const body = await getJson(fetcher, `/v1/user/${encodeURIComponent(username)}/statuses?page=${page}`, token);
    const data: Json[] = Array.isArray(body?.data) ? body.data : [];
    let older = false;
    for (const status of data) {
      const departure = Date.parse(status?.checkin?.origin?.departurePlanned ?? status?.createdAt ?? "");
      if (Number.isNaN(departure)) {
        skipped++;
        continue;
      }
      if (departure > end) continue;
      if (departure < start) {
        older = true;
        continue;
      }
      statuses++;
      const leg = legOf(status);
      if (leg) legs.push(leg);
      else skipped++;
    }
    if (older || data.length === 0 || !str(body?.links?.next)) break;
  }

  return { trips: groupTrips(legs), statuses, skipped, truncated };
}

/**
 * Parsed rides → the connection in the timetable, through the existing
 * `abeona-transit` actions (`locations`, `journeys`). Mirrored in Kotlin
 * (domain/intake/Match.kt); the cases are in intake.fixtures.json
 * ("matching").
 *
 * 1. Each station name is looked up once; the first answer is taken.
 * 2. One search from the first station to the last at the first departure.
 *    A journey whose rides are the parsed ones — same line, same departure
 *    and arrival minute — is taken whole, with its refresh token.
 * 3. Otherwise each ride on its own: a ride of step 2 that matches, else a
 *    search between that ride's stations at its departure. Transitous often
 *    routes the same trains with another change (Frankfurt Flughafen instead
 *    of Hbf), which is why the trains count, not the journey.
 * 4. A ride nothing matches is kept as parsed: no route line, no trip id,
 *    no live values. Saving still works (`source` 'share' or 'pdf'). Its
 *    stops still get a place, so the map draws it: the neighbouring ride's
 *    stop at the same change where that ride was found (the printed names
 *    differ: "Frankfurt(Main)Hbf" and "Frankfurt (Main) Hauptbahnhof"),
 *    else the station looked up. A train the timetable does not have yet
 *    (a date after the next timetable change) is the usual reason, so the
 *    saved ride is looked up again later (findSavedLeg) and, once found,
 *    attached through abeona_attach_leg.
 *
 * Times printed on DB's tickets and shares are local; they are read in
 * Europe/Berlin, which is also right for the Benelux, France, Austria,
 * Switzerland and Italy, but not for the UK or Portugal.
 */
import { lineKey } from "./common.ts";
import { overallConfidence, transfers } from "../transfer.ts";
import type { ParsedLeg } from "./parsed.ts";
import type { Journey, Leg, Station, StopTime } from "../transit.ts";

export const INTAKE_ZONE = "Europe/Berlin";

/** What matching needs from the timetable; data.ts and the tests each provide one. */
export interface TransitPort {
  stations(query: string): Promise<Station[]>;
  /** Journeys from `from` to `to` departing at or after `time` (an ISO instant). */
  journeys(from: string, to: string, time: string): Promise<Journey[]>;
}

export type MatchStatus = "full" | "partial" | "none";

export interface MatchResult {
  /** The journey to save: the timetable's where found, the parsed rides elsewhere. */
  journey: Journey;
  status: MatchStatus;
  /** How many parsed rides were found in the timetable. */
  matched: number;
  total: number;
  /** Why nothing matched, when a lookup failed (e.g. outside the timetable window). */
  problem: string | null;
}

/** Wall-clock minutes of `zone` at an instant. */
function offsetMinutes(instant: number, zone: string): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: zone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).formatToParts(new Date(instant));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const wall = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"));
  return Math.round((wall - Math.floor(instant / 60_000) * 60_000) / 60_000);
}

/** "2025-11-14" + "06:29" in Europe/Berlin → "2025-11-14T05:29:00.000Z". */
export function localToInstant(date: string, time: string, zone = INTAKE_ZONE): string {
  const [y, mo, d] = date.split("-").map(Number);
  const [h, mi] = time.split(":").map(Number);
  const wall = Date.UTC(y, mo - 1, d, h, mi);
  let guess = wall - offsetMinutes(wall, zone) * 60_000;
  // Once more, for an offset that differs on the other side of a DST change.
  guess = wall - offsetMinutes(guess, zone) * 60_000;
  return new Date(guess).toISOString();
}

function sameMinute(iso: string | null, expected: string): boolean {
  if (!iso) return false;
  return Math.abs(Date.parse(iso) - Date.parse(expected)) < 60_000;
}

/** Whether two line names are the same train: "RE19" = "RE 19", or the bracketed number. */
export function sameLine(parsed: Pick<ParsedLeg, "line" | "product" | "number">, lineName: string | null): boolean {
  const key = lineKey(lineName);
  if (key === "") return false;
  if (key === lineKey(parsed.line)) return true;
  // "RE19 (4901)" may be "RE 4901" in a feed that names trains by number.
  return !!parsed.number && !!parsed.product && key === lineKey(`${parsed.product}${parsed.number}`);
}

/** The timetable's ride is the parsed one: same line, departing and arriving the same minute. */
export function legMatches(parsed: ParsedLeg, leg: Leg): boolean {
  if (leg.mode === "walk") return false;
  return (
    sameLine(parsed, leg.line_name) &&
    sameMinute(leg.from.planned, localToInstant(parsed.date, parsed.departure)) &&
    sameMinute(leg.to.planned, localToInstant(parsed.arrival_date, parsed.arrival))
  );
}

function stop(name: string, planned: string, platform: string | null): StopTime {
  return {
    id: null,
    name,
    lat: null,
    lon: null,
    planned,
    current: null,
    planned_platform: platform,
    platform,
    cancelled: false,
  };
}

/** A parsed ride as a leg of the contract: no route line, no trip id, nothing live. */
export function parsedToLeg(parsed: ParsedLeg): Leg {
  const product = parsed.product;
  return {
    mode: product === "BUS" ? "bus" : "train",
    product: product === "BUS" ? "Bus" : product,
    line_name: parsed.line || null,
    trip_ref: null,
    headsign: parsed.headsign,
    operator: null,
    from: stop(parsed.from, localToInstant(parsed.date, parsed.departure), parsed.from_platform),
    to: stop(parsed.to, localToInstant(parsed.arrival_date, parsed.arrival), parsed.to_platform),
    stopovers: [],
    polyline: null,
    cancelled: false,
    realtime: false,
  };
}

/** The timetable's leg, with the printed platforms where the timetable has none (future days often do not). */
function withPrinted(leg: Leg, parsed: ParsedLeg): Leg {
  const fill = (s: StopTime, platform: string | null): StopTime =>
    s.planned_platform || !platform ? s : { ...s, planned_platform: platform, platform: s.platform ?? platform };
  return { ...leg, from: fill(leg.from, parsed.from_platform), to: fill(leg.to, parsed.to_platform) };
}

/** A real position; the lookups' (0, 0) means none. */
function located(p: { lat: number | null; lon: number | null } | null | undefined): p is { lat: number; lon: number } {
  return !!p && typeof p.lat === "number" && typeof p.lon === "number" && !(p.lat === 0 && p.lon === 0);
}

/**
 * A stop of a ride nothing matched, placed: where the neighbouring ride
 * found in the timetable is at the same change, else the station looked up
 * for its printed name. The printed name and times stay.
 */
function place(stop: StopTime, neighbour: StopTime | null | undefined, station: Station | null | undefined): StopTime {
  if (located(neighbour)) return { ...stop, id: neighbour.id, lat: neighbour.lat, lon: neighbour.lon };
  if (located(station)) return { ...stop, id: station.id, lat: station.lat, lon: station.lon };
  return stop;
}

/** The parsed rides alone, for saving when nothing could be looked up. */
export function parsedJourney(legs: ParsedLeg[]): Journey {
  return { provider: "transitous", refresh_token: null, legs: legs.map(parsedToLeg) };
}

function rides(journey: Journey): Leg[] {
  return journey.legs.filter((l) => l.mode !== "walk");
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export async function matchConnection(legs: ParsedLeg[], port: TransitPort): Promise<MatchResult> {
  const total = legs.length;
  if (total === 0) throw new Error("Nothing to match: no rides were found.");
  let problem: string | null = null;

  // 1. Stations, each name once.
  const names = [...new Set(legs.flatMap((l) => [l.from, l.to]))];
  const stations = new Map<string, Station | null>();
  await Promise.all(
    names.map(async (name) => {
      try {
        stations.set(name, (await port.stations(name))[0] ?? null);
      } catch (e) {
        problem ??= describe(e);
        stations.set(name, null);
      }
    }),
  );
  const ids = new Map([...stations].map(([name, station]) => [name, station?.id ?? null]));

  // 2. The whole journey.
  const first = legs[0];
  const last = legs[total - 1];
  const originId = ids.get(first.from) ?? null;
  const destinationId = ids.get(last.to) ?? null;
  let candidates: Journey[] = [];
  if (originId && destinationId) {
    try {
      candidates = await port.journeys(originId, destinationId, localToInstant(first.date, first.departure));
    } catch (e) {
      problem ??= describe(e);
    }
  }
  for (const journey of candidates) {
    const r = rides(journey);
    if (r.length === total && r.every((leg, i) => legMatches(legs[i], leg))) {
      let k = 0;
      const filled = journey.legs.map((leg) => (leg.mode === "walk" ? leg : withPrinted(leg, legs[k++])));
      return { journey: { ...journey, legs: filled }, status: "full", matched: total, total, problem: null };
    }
  }

  // 3. Ride by ride.
  const pool = candidates.flatMap(rides);
  const found = await Promise.all(
    legs.map(async (parsed): Promise<Leg | null> => {
      const known = pool.find((leg) => legMatches(parsed, leg));
      if (known) return known;
      const from = ids.get(parsed.from);
      const to = ids.get(parsed.to);
      if (!from || !to) return null;
      try {
        const answer = await port.journeys(from, to, localToInstant(parsed.date, parsed.departure));
        for (const journey of answer) {
          const hit = rides(journey).find((leg) => legMatches(parsed, leg));
          if (hit) return hit;
        }
      } catch (e) {
        problem ??= describe(e);
      }
      return null;
    }),
  );

  const matched = found.filter(Boolean).length;
  const assembled = legs.map((parsed, i) => {
    if (found[i]) return withPrinted(found[i]!, parsed);
    const leg = parsedToLeg(parsed);
    return {
      ...leg,
      from: place(leg.from, found[i - 1]?.to, stations.get(parsed.from)),
      to: place(leg.to, found[i + 1]?.from, stations.get(parsed.to)),
    };
  });
  return {
    journey: { provider: "transitous", refresh_token: null, legs: assembled },
    status: matched === total ? "full" : matched === 0 ? "none" : "partial",
    matched,
    total,
    problem: matched === total ? null : problem,
  };
}

/** A saved ride the timetable did not have when it was saved: a ride, with no trip ref. */
export function isUnfound(leg: Leg): boolean {
  return leg.mode !== "walk" && !leg.trip_ref && !!lineKey(leg.line_name);
}

/** "ICE 725" → "ICE": the kind of train, to tell whether the timetable has that kind on a day. */
function lineFamily(line: string | null | undefined): string {
  return lineKey(line).replace(/[^A-Z].*$/, "");
}

/** How far after the ride's departure another train of its kind still proves the timetable covers its day. */
const COVERED_WITHIN_MS = 36 * 3600_000;

export interface SavedLegCheck {
  /** The timetable's ride, once it has it. */
  found: Leg | null;
  /**
   * Not found, although the timetable covers its day: trains of the same kind
   * run between its stops after it. The ride no longer runs as booked (a
   * timetable change, usually engineering works). False while the timetable
   * simply does not reach that far yet.
   */
  dropped: boolean;
}

/**
 * A saved ride that was not in the timetable, looked up again: a search
 * between its stops at its planned departure, and the ride with its line
 * departing and arriving the same minutes. Not there, while later trains of
 * the same kind are: dropped. Neither when the ride's stops have no id to
 * search from.
 */
export async function checkSavedLeg(leg: Leg, port: TransitPort): Promise<SavedLegCheck> {
  const none = { found: null, dropped: false };
  if (!isUnfound(leg) || !leg.from.id || !leg.to.id || !leg.from.planned || !leg.to.planned) return none;
  const departure = new Date(leg.from.planned).toISOString();
  const arrival = new Date(leg.to.planned).toISOString();
  const key = lineKey(leg.line_name);
  const family = lineFamily(leg.line_name);
  const answer = await port.journeys(leg.from.id, leg.to.id, departure);
  let later = false;
  for (const journey of answer) {
    for (const ride of rides(journey)) {
      if (!!ride.trip_ref && lineKey(ride.line_name) === key && sameMinute(ride.from.planned, departure) && sameMinute(ride.to.planned, arrival)) {
        return { found: ride, dropped: false };
      }
      const at = ride.from.planned ? Date.parse(ride.from.planned) : NaN;
      if (family && lineFamily(ride.line_name) === family && at > Date.parse(departure) && at - Date.parse(departure) <= COVERED_WITHIN_MS) {
        later = true;
      }
    }
  }
  return { found: null, dropped: later };
}

/** The rides' trains, in order: ["ICE17", "ICE725"]. */
function trainsOf(legs: Leg[]): string[] {
  return legs.filter((l) => l.mode !== "walk").map((l) => lineKey(l.line_name));
}

/**
 * When a ride was dropped, the connection that is clearly the same trip:
 * the same trains, in the same order, from the ride before the dropped one
 * to where the dropped one ends (the change may have moved, as when ICE 725
 * left from Frankfurt Flughafen instead of the Hbf, so the ride before is
 * left there), then the booked rides after it as they are. Every change
 * must still be workable (safe or tight, transfer.ts), the booked ones after
 * it included. Searching only to the dropped ride's end matters: to the
 * destination, a router prefers a quicker last train to the booked one.
 * What to store as the replacement (missed_leg_idx, replacement_legs); null
 * when there is no such connection, and the traveller has to choose.
 */
export async function clearReplacement(
  legs: Leg[],
  droppedIdx: number,
  port: TransitPort,
): Promise<{ missedIdx: number; replacement: Leg[] } | null> {
  const rideIdx = legs.map((l, i) => (l.mode === "walk" ? -1 : i)).filter((i) => i >= 0);
  const pos = rideIdx.indexOf(droppedIdx);
  if (pos < 0) return null;
  const startIdx = rideIdx[Math.max(0, pos - 1)];
  const start = legs[startIdx];
  const dropped = legs[droppedIdx];
  if (!start.from.id || !dropped.to.id || !start.from.planned) return null;
  const wanted = trainsOf(legs.slice(startIdx, droppedIdx + 1));
  const after = legs.slice(droppedIdx + 1);
  // A later ride that is not in the timetable either cannot be relied on.
  if (after.some((l) => l.mode !== "walk" && (l.cancelled || !l.trip_ref))) return null;
  const answer = await port.journeys(start.from.id, dropped.to.id, new Date(start.from.planned).toISOString());
  for (const journey of answer) {
    if (trainsOf(journey.legs).join(" ") !== wanted.join(" ")) continue;
    if (journey.legs.some((l) => l.mode !== "walk" && (l.cancelled || !l.trip_ref))) continue;
    const replacement = [...journey.legs, ...after];
    const confidence = overallConfidence(transfers(replacement));
    if (confidence === "impossible" || confidence === "at_risk") continue;
    return { missedIdx: startIdx, replacement };
  }
  return null;
}

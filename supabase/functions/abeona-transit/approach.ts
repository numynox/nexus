// Where the train to catch is now, on its way to the boarding stop: the
// last stop it is at or has left by its live times. Facts only: a stop
// counts by a realtime time the provider reports, never by the timetable.

import type { Stopover } from "./contract.ts";

export interface Approach {
  /** The stop the train is at or last left. */
  stop: string;
  state: "at" | "left";
  /** Stops it still calls at up to and including the boarding stop. */
  stops_away: number;
}

function at(value: string | null): number {
  return value ? Date.parse(value) : NaN;
}

/**
 * `stops` is the train's whole run in order, `board` the boarding stop's
 * index in it. Null when the run starts at the boarding stop, when the train
 * has reached it, or when no stop before it has a live time in the past.
 */
export function approachOf(stops: Stopover[], board: number, now: number): Approach | null {
  if (board <= 0 || board >= stops.length) return null;
  if (at(stops[board].arrival) <= now) return null;
  let found: { i: number; state: Approach["state"] } | null = null;
  for (let i = 0; i < board; i++) {
    const s = stops[i];
    if (s.cancelled) continue;
    const departure = at(s.departure);
    const arrival = at(s.arrival);
    if (departure <= now) {
      found = { i, state: "left" };
    } else if (i > 0 && arrival <= now && at(s.departure ?? s.planned_departure) > now) {
      found = { i, state: "at" };
    }
  }
  if (!found) return null;
  const away = stops.slice(found.i + 1, board + 1).filter((s) => !s.cancelled).length;
  return { stop: stops[found.i].name, state: found.state, stops_away: away };
}

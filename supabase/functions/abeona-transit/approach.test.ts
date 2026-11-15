// deno test supabase/functions/abeona-transit/approach.test.ts
import { assertEquals } from "jsr:@std/assert@1";
import { approachOf } from "./approach.ts";
import type { Stopover } from "./contract.ts";

function stop(name: string, arrival: string | null, departure: string | null, live = true, cancelled = false): Stopover {
  const iso = (t: string | null) => (t ? `2026-11-15T${t}:00Z` : null);
  return {
    id: name,
    name,
    lat: null,
    lon: null,
    planned_arrival: iso(arrival),
    arrival: live ? iso(arrival) : null,
    planned_departure: iso(departure),
    departure: live ? iso(departure) : null,
    cancelled,
  };
}

const now = Date.parse("2026-11-15T12:10:00Z");

Deno.test("left the last stop it has a live departure for", () => {
  const run = [stop("A", null, "12:00"), stop("B", "12:05", "12:06"), stop("C", "12:15", "12:16"), stop("Board", "12:25", "12:27")];
  assertEquals(approachOf(run, 3, now), { stop: "B", state: "left", stops_away: 2 });
});

Deno.test("at a stop it has arrived at and not yet left", () => {
  const run = [stop("A", null, "12:00"), stop("B", "12:08", "12:12"), stop("Board", "12:25", "12:27")];
  assertEquals(approachOf(run, 2, now), { stop: "B", state: "at", stops_away: 1 });
});

Deno.test("nothing without live times", () => {
  const run = [stop("A", null, "12:00", false), stop("B", "12:05", "12:06", false), stop("Board", "12:25", "12:27", false)];
  assertEquals(approachOf(run, 2, now), null);
});

Deno.test("nothing before it starts, at the start, or once it has reached the boarding stop", () => {
  assertEquals(approachOf([stop("A", null, "12:20"), stop("Board", "12:25", "12:27")], 1, now), null);
  assertEquals(approachOf([stop("Board", null, "12:20"), stop("B", "12:25", null)], 0, now), null);
  assertEquals(approachOf([stop("A", null, "12:00"), stop("Board", "12:09", "12:11")], 1, now), null);
});

Deno.test("cancelled stops are neither where it is nor counted", () => {
  const run = [stop("A", null, "12:00"), stop("B", "12:05", "12:06", true, true), stop("C", "12:15", "12:16", true, true), stop("Board", "12:25", "12:27")];
  assertEquals(approachOf(run, 3, now), { stop: "A", state: "left", stops_away: 1 });
});

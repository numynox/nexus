// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in followed.fixtures.json, which the Android tests run too
// (FollowedFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { followedLegs, replacedRide, takeConnection } from "./followed.ts";
import type { Leg, StopTime } from "./transit.ts";
import type { TravelDay } from "./travelday.ts";

type Raw = Record<string, unknown>;
const fixtures = JSON.parse(readFileSync(new URL("./followed.fixtures.json", import.meta.url), "utf8"));

function stop(s: Raw): StopTime {
  return {
    id: (s.id as string) ?? null,
    name: s.name as string,
    lat: (s.lat as number) ?? null,
    lon: (s.lon as number) ?? null,
    planned: (s.planned as string) ?? null,
    current: (s.current as string) ?? null,
    planned_platform: null,
    platform: null,
    cancelled: false,
  };
}

function leg(l: Raw): Leg {
  return {
    mode: (l.mode as Leg["mode"]) ?? "train",
    product: null,
    line_name: (l.line_name as string) ?? null,
    trip_ref: null,
    headsign: null,
    operator: null,
    from: stop(l.from as Raw),
    to: stop(l.to as Raw),
    stopovers: ((l.stopovers as Raw[]) ?? []).map((s) => ({
      id: (s.id as string) ?? null,
      name: s.name as string,
      lat: (s.lat as number) ?? null,
      lon: (s.lon as number) ?? null,
      planned_arrival: (s.planned_arrival as string) ?? null,
      arrival: (s.arrival as string) ?? null,
      planned_departure: null,
      departure: null,
      cancelled: false,
    })),
    polyline: (l.polyline as [number, number][]) ?? null,
    cancelled: false,
    realtime: false,
  };
}

const legs: Leg[] = fixtures.legs.map(leg);

for (const c of fixtures.followedLegs) {
  test(`followedLegs: ${c.name}`, () => {
    const result = followedLegs(legs, c.missedIdx, c.replacement ? c.replacement.map(leg) : null);
    assert.deepEqual(
      result.map((l) => ({ leg: `${l.from.id ?? "?"}>${l.to.id ?? "?"}`, stopovers: l.stopovers.length })),
      c.expect.map((e: Raw) => ({ leg: e.leg, stopovers: e.stopovers })),
    );
    c.expect.forEach((e: Raw, i: number) => {
      if (e.polylineEnd) assert.deepEqual(result[i].polyline?.[result[i].polyline!.length - 1], e.polylineEnd);
      if (e.toPlanned) assert.equal(result[i].to.planned, e.toPlanned);
      if (e.toCurrent) assert.equal(result[i].to.current, e.toCurrent);
    });
  });
}

for (const c of fixtures.replacedRide) {
  test(`replacedRide: ${c.name}`, () => {
    const day = { kind: c.kind, legIndex: c.legIndex } as TravelDay;
    assert.equal(replacedRide(legs, day), c.expect);
  });
}

const id = (l: Leg) => `${l.from.id ?? "?"}>${l.to.id ?? "?"}`;

for (const c of fixtures.takeConnection) {
  test(`takeConnection: ${c.name}`, () => {
    const replacement = c.replacement ? c.replacement.map(leg) : null;
    const taken = takeConnection(legs, c.missedIdx, replacement, c.day as TravelDay, c.chosen.map(leg));
    if (c.expect === null) return assert.equal(taken, null);
    assert.ok(taken);
    assert.equal(taken.missedIdx, c.expect.missedIdx);
    assert.deepEqual(taken.replacement.map(id), c.expect.replacement);
    assert.deepEqual(followedLegs(legs, taken.missedIdx, taken.replacement).map(id), c.expect.followed);
  });
}

// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in progress.fixtures.json, which the Android tests run too
// (RideProgressFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { type ProgressLeg, rideProgress } from "./progress.ts";

interface Expect {
  stops: string[];
  current: number | null;
  next: number | null;
  stopsLeft: number;
  minutesToNext: number | null;
  fraction: number;
  estimate: { lat: number; lon: number } | null;
  passed: boolean[];
}

interface Fixtures {
  leg: {
    from: Record<string, unknown>;
    to: Record<string, unknown>;
    stopovers: Record<string, unknown>[];
    polyline: [number, number][];
  };
  cases: { name: string; now: string; expect: Expect }[];
}

const fixtures: Fixtures = JSON.parse(readFileSync(new URL("./progress.fixtures.json", import.meta.url), "utf8"));

function stop(s: Record<string, unknown>) {
  return {
    id: (s.id as string) ?? null,
    name: s.name as string,
    lat: (s.lat as number) ?? null,
    lon: (s.lon as number) ?? null,
    planned: (s.planned as string) ?? null,
    current: (s.current as string) ?? null,
  };
}

const leg: ProgressLeg = {
  from: stop(fixtures.leg.from),
  to: stop(fixtures.leg.to),
  stopovers: fixtures.leg.stopovers.map((s) => ({
    id: (s.id as string) ?? null,
    name: s.name as string,
    lat: (s.lat as number) ?? null,
    lon: (s.lon as number) ?? null,
    planned_arrival: (s.planned_arrival as string) ?? null,
    arrival: (s.arrival as string) ?? null,
    planned_departure: (s.planned_departure as string) ?? null,
    departure: (s.departure as string) ?? null,
    cancelled: (s.cancelled as boolean) ?? false,
  })),
  polyline: fixtures.leg.polyline,
};

for (const c of fixtures.cases) {
  test(`rideProgress: ${c.name}`, () => {
    const p = rideProgress(leg, Date.parse(c.now));
    assert.ok(p);
    assert.deepEqual(
      p.stops.map((s) => s.id),
      c.expect.stops,
    );
    assert.deepEqual(
      p.stops.map((s) => s.passed),
      c.expect.passed,
    );
    assert.equal(p.current, c.expect.current);
    assert.equal(p.next, c.expect.next);
    assert.equal(p.stopsLeft, c.expect.stopsLeft);
    assert.equal(p.minutesToNext, c.expect.minutesToNext);
    assert.ok(Math.abs(p.fraction - c.expect.fraction) < 1e-4, `fraction ${p.fraction}`);
    if (c.expect.estimate === null) return assert.equal(p.estimate, null);
    assert.ok(p.estimate);
    assert.ok(Math.abs(p.estimate.lat - c.expect.estimate.lat) < 1e-4, `lat ${p.estimate.lat}`);
    assert.ok(Math.abs(p.estimate.lon - c.expect.estimate.lon) < 1e-4, `lon ${p.estimate.lon}`);
  });
}

test("rideProgress: no time at the ride's ends is no progress", () => {
  assert.equal(rideProgress({ ...leg, from: { ...leg.from, planned: null, current: null } }, Date.now()), null);
});

// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in travelday.fixtures.json, which the Android tests run too
// (TravelDayFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
  alternativesOrigin,
  alternativesReason,
  missedShortConnection,
  type DayLeg,
  type TravelDay,
  travelDay,
  type TravelSignals,
} from "./travelday.ts";

interface FixtureStop {
  id?: string | null;
  name?: string;
  lat?: number | null;
  lon?: number | null;
  planned?: string | null;
  current?: string | null;
  planned_platform?: string | null;
  platform?: string | null;
}

interface FixtureLeg {
  mode?: DayLeg["mode"];
  cancelled?: boolean;
  from: FixtureStop;
  to: FixtureStop;
  stopovers?: { id?: string | null; name: string; lat?: number; lon?: number; planned_arrival?: string; arrival?: string }[];
  polyline?: [number, number][];
}

interface Case {
  name: string;
  now: string;
  stationMinutes: number;
  signals?: TravelSignals;
  legs: FixtureLeg[];
}

interface Fixtures {
  travelDay: (Case & { expect: TravelDay })[];
  alternativesOrigin: (Case & { expect: { id: string; name: string; time: string } | null })[];
  alternativesReason: (Case & { expect: string | null })[];
  missedShortConnection: (Case & { expect: boolean })[];
}

const fixtures: Fixtures = JSON.parse(readFileSync(new URL("./travelday.fixtures.json", import.meta.url), "utf8"));

function stop(s: FixtureStop): DayLeg["from"] {
  return {
    id: s.id ?? null,
    name: s.name ?? s.id ?? "?",
    lat: s.lat ?? null,
    lon: s.lon ?? null,
    planned: s.planned ?? null,
    current: s.current ?? null,
    planned_platform: s.planned_platform ?? null,
    platform: s.platform ?? null,
  };
}

function legs(list: FixtureLeg[]): DayLeg[] {
  return list.map((l) => ({
    mode: l.mode ?? "train",
    cancelled: l.cancelled ?? false,
    from: stop(l.from),
    to: stop(l.to),
    stopovers: (l.stopovers ?? []).map((s) => ({
      id: s.id ?? null,
      name: s.name,
      lat: s.lat ?? null,
      lon: s.lon ?? null,
      planned_arrival: s.planned_arrival ?? null,
      arrival: s.arrival ?? null,
    })),
    polyline: l.polyline ?? null,
  }));
}

/** Instants compare as instants: "…:00Z" and "…:00.000Z" are the same. */
function instant(value: string | null): number | null {
  return value === null ? null : Date.parse(value);
}

function run(c: Case): { list: DayLeg[]; day: TravelDay; now: number } {
  const list = legs(c.legs);
  const now = Date.parse(c.now);
  return { list, now, day: travelDay(list, now, c.stationMinutes, c.signals ?? {}) };
}

for (const c of fixtures.travelDay) {
  test(`travelDay: ${c.name}`, () => {
    const { day } = run(c);
    assert.deepEqual({ ...day, leaveAt: instant(day.leaveAt) }, { ...c.expect, leaveAt: instant(c.expect.leaveAt) });
  });
}

for (const c of fixtures.alternativesOrigin) {
  test(`alternativesOrigin: ${c.name}`, () => {
    const { list, day, now } = run(c);
    const origin = alternativesOrigin(list, day, now);
    if (c.expect === null) return assert.equal(origin, null);
    assert.ok(origin);
    assert.deepEqual(
      { id: origin.id, name: origin.name, time: instant(origin.time) },
      { id: c.expect.id, name: c.expect.name, time: instant(c.expect.time) },
    );
  });
}

for (const c of fixtures.alternativesReason) {
  test(`alternativesReason: ${c.name}`, () => {
    const { list, day } = run(c);
    assert.equal(alternativesReason(list, day), c.expect);
  });
}

for (const c of fixtures.missedShortConnection) {
  test(`missedShortConnection: ${c.name}`, () => {
    const { list, day } = run(c);
    assert.equal(missedShortConnection(list, day), c.expect);
  });
}

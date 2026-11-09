// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in transfer.fixtures.json, which the Android tests run too
// (TransferFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
  delayMinutes,
  finalArrivalDelay,
  normalisePlatform,
  overallConfidence,
  type RuleLeg,
  trainBindingLifted,
  transfers,
} from "./transfer.ts";

interface FixtureStop {
  id?: string | null;
  planned?: string | null;
  current?: string | null;
  planned_platform?: string | null;
  platform?: string | null;
}

interface FixtureLeg {
  mode?: RuleLeg["mode"];
  cancelled?: boolean;
  from: FixtureStop;
  to: FixtureStop;
}

interface Fixtures {
  delayMinutes: { name: string; planned: string | null; current: string | null; expect: number }[];
  normalisePlatform: { value: string | null; expect: string | null }[];
  transfers: {
    name: string;
    legs: FixtureLeg[];
    expect: Record<string, unknown>[];
    overall: string | null;
  }[];
  finalArrivalDelay: { name: string; legs: FixtureLeg[]; expect: number }[];
  trainBindingLifted: { fareType: string; trainBinding: boolean; arrivalDelayMinutes: number; connectionBroken?: boolean; expect: boolean }[];
}

const fixtures: Fixtures = JSON.parse(
  readFileSync(new URL("./transfer.fixtures.json", import.meta.url), "utf8"),
);

function stop(s: FixtureStop): RuleLeg["from"] {
  return {
    id: s.id ?? null,
    planned: s.planned ?? null,
    current: s.current ?? null,
    planned_platform: s.planned_platform ?? null,
    platform: s.platform ?? null,
  } as RuleLeg["from"];
}

function legs(list: FixtureLeg[]): RuleLeg[] {
  return list.map((l) => ({
    mode: l.mode ?? "train",
    cancelled: l.cancelled ?? false,
    from: stop(l.from),
    to: stop(l.to),
  }));
}

for (const c of fixtures.delayMinutes) {
  test(`delayMinutes: ${c.name}`, () => {
    assert.equal(delayMinutes(c.planned, c.current), c.expect);
  });
}

test("normalisePlatform", () => {
  for (const c of fixtures.normalisePlatform) {
    assert.equal(normalisePlatform(c.value), c.expect, JSON.stringify(c.value));
  }
});

for (const c of fixtures.transfers) {
  test(`transfers: ${c.name}`, () => {
    const list = transfers(legs(c.legs));
    assert.deepEqual(list, c.expect);
    assert.equal(overallConfidence(list), c.overall);
  });
}

for (const c of fixtures.finalArrivalDelay) {
  test(`finalArrivalDelay: ${c.name}`, () => {
    assert.equal(finalArrivalDelay(legs(c.legs)), c.expect);
  });
}

test("trainBindingLifted mirrors the SQL rule", () => {
  for (const c of fixtures.trainBindingLifted) {
    assert.equal(
      trainBindingLifted(c.fareType, c.trainBinding, c.arrivalDelayMinutes, c.connectionBroken ?? false),
      c.expect,
      `${c.fareType}, binding ${c.trainBinding}, +${c.arrivalDelayMinutes}`,
    );
  }
});

// Run with `npm run test:abeona` (node --test; Node strips the types).
// The Android copy of these rules has the same cases — keep them in step.
import assert from "node:assert/strict";
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

function stop(id: string | null, planned: string, current: string | null = null, platform: string | null = null) {
  return { id, planned, current, planned_platform: platform, platform: null };
}

function leg(mode: RuleLeg["mode"], from: RuleLeg["from"], to: RuleLeg["to"], cancelled = false): RuleLeg {
  return { mode, cancelled, from, to };
}

const T = (hhmm: string) => `2026-11-02T${hhmm}:00Z`;

test("delayMinutes rounds and is 0 without a current time", () => {
  assert.equal(delayMinutes(T("10:00"), null), 0);
  assert.equal(delayMinutes(T("10:00"), T("10:07")), 7);
  assert.equal(delayMinutes(T("10:00"), "2026-11-02T10:00:31Z"), 1);
  assert.equal(delayMinutes(T("10:00"), T("09:58")), -2);
});

test("normalisePlatform strips prefixes and case", () => {
  assert.equal(normalisePlatform(" Gleis 7a "), "7A");
  assert.equal(normalisePlatform("Platform 3"), "3");
  assert.equal(normalisePlatform("  "), null);
  assert.equal(normalisePlatform(null), null);
});

test("same platform needs 2 minutes, another platform 5", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00"), null, "Gleis 4"));
  const same = leg("train", stop("X", T("11:04"), null, "4"), stop("B", T("12:00")));
  const other = leg("train", stop("X", T("11:04"), null, "9"), stop("B", T("12:00")));
  const [t1] = transfers([a, same]);
  assert.equal(t1.requiredMinutes, 2);
  assert.equal(t1.marginMinutes, 2);
  assert.equal(t1.confidence, "tight");
  const [t2] = transfers([a, other]);
  assert.equal(t2.requiredMinutes, 5);
  assert.equal(t2.confidence, "at_risk");
});

test("unknown platforms count as different", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00")));
  const b = leg("train", stop("X", T("11:10")), stop("B", T("12:00")));
  const [t] = transfers([a, b]);
  assert.equal(t.requiredMinutes, 5);
  assert.equal(t.confidence, "safe");
});

test("a walk between legs adds its minutes plus 3", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00")));
  const walk = leg("walk", stop("X", T("11:00")), stop("Y", T("11:06")));
  const b = leg("bus", stop("Y", T("11:12")), stop("B", T("12:00")));
  const list = transfers([a, walk, b]);
  assert.equal(list.length, 1);
  assert.equal(list[0].fromIndex, 0);
  assert.equal(list[0].toIndex, 2);
  assert.equal(list[0].walkMinutes, 6);
  assert.equal(list[0].requiredMinutes, 9);
  assert.equal(list[0].availableMinutes, 12);
  assert.equal(list[0].confidence, "safe");
});

test("a different stop without a walk needs 3 minutes", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00")));
  const b = leg("train", stop("Z", T("11:05")), stop("B", T("12:00")));
  assert.equal(transfers([a, b])[0].requiredMinutes, 3);
});

test("live times win over planned ones", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00"), T("11:12")));
  const b = leg("train", stop("X", T("11:15")), stop("B", T("12:00")));
  const [t] = transfers([a, b]);
  assert.equal(t.availableMinutes, 3);
  assert.equal(t.confidence, "at_risk");
});

test("a cancelled leg puts the transfer at risk", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00")));
  const b = leg("train", stop("X", T("11:40")), stop("B", T("12:00")), true);
  assert.equal(transfers([a, b])[0].confidence, "at_risk");
  assert.equal(overallConfidence(transfers([a, b])), "at_risk");
  assert.equal(overallConfidence([]), null);
});

test("finalArrivalDelay reads the last non-walk leg", () => {
  const a = leg("train", stop("A", T("10:00")), stop("X", T("11:00"), T("11:25")));
  const walk = leg("walk", stop("X", T("11:00")), stop("Y", T("11:05"), T("11:30")));
  assert.equal(finalArrivalDelay([a, walk]), 25);
  assert.equal(finalArrivalDelay([walk]), 0);
});

test("trainBindingLifted mirrors the SQL rule", () => {
  assert.equal(trainBindingLifted("saver", true, 20), true);
  assert.equal(trainBindingLifted("saver", true, 19), false);
  assert.equal(trainBindingLifted("saver", false, 60), false);
  assert.equal(trainBindingLifted("regional", true, 60), false);
  assert.equal(trainBindingLifted("deutschlandticket", true, 60), false);
  assert.equal(trainBindingLifted("bahncard100", true, 60), false);
  assert.equal(trainBindingLifted("flex", true, 25), true);
});

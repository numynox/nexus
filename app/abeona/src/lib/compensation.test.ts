// Run with `npm run test:abeona`. The Android copy (domain/Compensation.kt)
// has the same cases in CompensationTest.
import assert from "node:assert/strict";
import { test } from "node:test";
import { claimDeadlineText, manualReplacement, verdictHeadline } from "./compensation.ts";
import type { Leg } from "./transit.ts";

const stop = (name: string, planned: string) => ({
  id: null, name, lat: null, lon: null, planned, current: null, planned_platform: null, platform: null, cancelled: false,
});
const leg = (from: string, to: string, dep: string, arr: string): Leg => ({
  mode: "train", product: "RE", line_name: "RE 10", trip_ref: "r", headsign: null, operator: null,
  from: stop(from, dep), to: stop(to, arr), stopovers: [], polyline: null, cancelled: false, realtime: true,
});

test("headlines", () => {
  assert.equal(verdictHeadline("eligible", "€12.50"), "€12.50 to claim");
  assert.equal(verdictHeadline("below_minimum", "€1.50"), "€1.50, under DB's €4 minimum");
  assert.equal(verdictHeadline("unknown_price", null), "Compensation due: add the fare");
  assert.equal(verdictHeadline("not_eligible", null), "No compensation");
});

test("deadline text", () => {
  assert.equal(claimDeadlineText("14 Jan 2027", 72), "Claim by 14 Jan 2027 (in 72 days)");
  assert.equal(claimDeadlineText("14 Jan 2027", 1), "Claim by tomorrow, 14 Jan 2027");
  assert.equal(claimDeadlineText("14 Jan 2027", -2), "The claim deadline was 14 Jan 2027");
});

test("a manual replacement runs from the missed stop to the destination", () => {
  const missed = leg("Würzburg Hbf", "Bamberg", "2026-10-14T10:48:00Z", "2026-10-14T12:00:00Z");
  const r = manualReplacement(missed, missed, " ic 2063 ", "2026-10-14T13:05:00Z");
  assert.equal(r.line_name, "ic 2063");
  assert.equal(r.product, "IC");
  assert.equal(r.from.name, "Würzburg Hbf");
  assert.equal(r.to.name, "Bamberg");
  assert.equal(r.to.current, "2026-10-14T13:05:00Z");
  assert.equal(r.trip_ref, null);
});

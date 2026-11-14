import assert from "node:assert/strict";
import { test } from "node:test";
import { isNight, sunTimes } from "./nightSky.ts";

// Frankfurt's published times (timeanddate.com), within the formula's few minutes.
const near = (actual: Date, expected: string) =>
  assert.ok(Math.abs(actual.getTime() - Date.parse(expected)) <= 3 * 60_000, `${actual.toISOString()} vs ${expected}`);

test("Frankfurt's sunrise and sunset, midsummer and midwinter", () => {
  const june = sunTimes(new Date("2026-06-21T12:00:00Z"))!;
  near(june.sunrise, "2026-06-21T03:14:00Z"); // 05:14 CEST
  near(june.sunset, "2026-06-21T19:39:00Z"); // 21:39 CEST
  const december = sunTimes(new Date("2026-12-21T12:00:00Z"))!;
  near(december.sunrise, "2026-12-21T07:23:00Z"); // 08:23 CET
  near(december.sunset, "2026-12-21T15:23:00Z"); // 16:23 CET
});

test("the owl wakes at sunset and sleeps at sunrise", () => {
  assert.equal(isNight(new Date("2026-06-21T19:00:00Z")), false); // 21:00 CEST, still light
  assert.equal(isNight(new Date("2026-06-21T20:00:00Z")), true);
  assert.equal(isNight(new Date("2026-12-21T16:00:00Z")), true); // 17:00 CET, dark
  assert.equal(isNight(new Date("2026-12-21T07:00:00Z")), true); // 08:00 CET, before sunrise
  assert.equal(isNight(new Date("2026-12-21T09:00:00Z")), false);
});

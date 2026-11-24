// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in scene.fixtures.json, which the Android tests run too
// (SceneModelFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
  barLayout,
  type BarJourney,
  hillHeight,
  type HillKind,
  journeyBar,
  lightPosition,
  mix,
  moonLitPath,
  moonPhase,
  moonPhaseName,
  railX,
  scenePalette,
  skyLight,
  sunTimes,
  trainKind,
  trainLength,
  trainNoseX,
  trainOnBar,
  travelSpeed,
} from "./scene.ts";

const fixtures = JSON.parse(readFileSync(new URL("./scene.fixtures.json", import.meta.url), "utf8"));
const near = (actual: number, expected: number, what: string) =>
  assert.ok(Math.abs(actual - expected) <= 0.002, `${what}: ${actual} is not ${expected}`);

test("the sun rises and sets to the minute, and the light follows", () => {
  for (const c of fixtures.sky) {
    const at = Date.parse(c.at);
    const times = sunTimes(at, c.lat, c.lon)!;
    assert.ok(Math.abs(times.sunrise - Date.parse(c.sunrise)) < 60_000, `${c.place} ${c.at} sunrise`);
    assert.ok(Math.abs(times.sunset - Date.parse(c.sunset)) < 60_000, `${c.place} ${c.at} sunset`);
    const sky = skyLight(at, c.lat, c.lon);
    near(sky.light, c.light, `${c.place} ${c.at} light`);
    near(sky.glow, c.glow, `${c.place} ${c.at} glow`);
    near(sky.across, c.across, `${c.place} ${c.at} across`);
    assert.equal(sky.night, c.night, `${c.place} ${c.at} night`);
  }
});

test("without a sunrise the sky is plain day", () => {
  const c = fixtures.polar;
  assert.equal(sunTimes(Date.parse(c.at), c.lat, c.lon), null);
  assert.deepEqual(skyLight(Date.parse(c.at), c.lat, c.lon), { light: c.light, glow: c.glow, across: c.across, night: c.night });
});

test("the moon's phase, its name and its lit part", () => {
  for (const c of fixtures.moon) {
    near(moonPhase(Date.parse(c.at)), c.phase, c.at);
    assert.equal(moonPhaseName(moonPhase(Date.parse(c.at))), c.name);
  }
  for (const c of fixtures.moonPath) assert.equal(moonLitPath(c.phase, c.cx, c.cy, c.r), c.path);
});

test("the land, the colours and the rail", () => {
  for (const c of fixtures.hills) near(hillHeight(c.kind as HillKind, c.x), c.height, `${c.kind} hill at ${c.x}`);
  for (const c of fixtures.mix) assert.equal(mix(c.a, c.b, c.t), c.colour);
  for (const c of fixtures.palette) {
    const p = scenePalette(c.dark, c.light);
    assert.deepEqual(
      { sky: p.sky, near: p.near, trainWindow: p.trainWindow, trainRed: p.trainRed, accent: p.accent, ok: p.ok, watch: p.watch, bad: p.bad },
      { sky: c.sky, near: c.near, trainWindow: c.trainWindow, trainRed: c.trainRed, accent: c.accent, ok: c.ok, watch: c.watch, bad: c.bad },
    );
  }
  for (const c of fixtures.light) {
    const at = lightPosition(c.across, c.width, c.height);
    near(at.x, c.x, "light x");
    near(at.up, c.up, "light up");
  }
  for (const c of fixtures.speed) near(travelSpeed(c.kmh), c.units, `speed at ${c.kmh}`);
  for (const c of fixtures.rail) near(railX(c.fraction, c.width), c.x, `rail at ${c.fraction}`);
});

test("a ride is drawn as the train it is", () => {
  for (const c of fixtures.trains) {
    assert.equal(trainKind(c.product, c.line, c.bus), c.kind, `${c.product} ${c.line}`);
    near(trainLength(c.kind), c.length, `${c.kind} length`);
  }
  for (const c of fixtures.nose) near(trainNoseX(c.kind, c.scale, c.width), c.x, `${c.kind} nose`);
});

test("the bar is the whole journey: every ride and change, one in focus", () => {
  for (const c of fixtures.journey) {
    const journey: BarJourney = {
      rides: c.rides.map((r: { departure: string; arrival: string; stops: string[] }) => ({
        departure: Date.parse(r.departure),
        arrival: Date.parse(r.arrival),
        stops: r.stops.map((s) => Date.parse(s)),
      })),
      current: c.current,
      state: c.state,
      confidence: c.confidence,
    };
    const bar = journeyBar(journey, Date.parse(c.now));
    assert.deepEqual(bar.parts, c.parts, `${c.case}: parts`);
    assert.equal(bar.focus, c.focus, `${c.case}: focus`);
    near(bar.fraction, c.fraction, `${c.case}: fraction`);
    assert.equal(bar.tone, c.tone, `${c.case}: tone`);
    assert.equal(bar.done, c.done, `${c.case}: done`);
    assert.equal(bar.walkerAtStart, c.walkerAtStart, `${c.case}: walker at the start`);
    assert.equal(bar.stops.length, c.stops.length, `${c.case}: stops`);
    bar.stops.forEach((f, i) => near(f, c.stops[i], `${c.case}: stop ${i}`));
  }
});

test("the bar's frame never moves: the part in focus takes the room, the others are stubs", () => {
  for (const c of fixtures.barLayout) {
    const at = barLayout({ parts: c.parts, focus: c.focus }, c.width);
    assert.equal(at.length, c.at.length, `${c.case}: parts`);
    at.forEach((r, i) => {
      near(r.from, c.at[i][0], `${c.case}: part ${i} from`);
      near(r.to, c.at[i][1], `${c.case}: part ${i} to`);
    });
  }
  for (const c of fixtures.trainOnBar) near(trainOnBar(c.x, c.kind, c.scale), c.nose, `${c.kind} over ${c.x}`);
});

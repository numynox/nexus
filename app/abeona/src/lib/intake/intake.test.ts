// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in intake.fixtures.json, which the Android tests run too
// (IntakeFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { lineKey, platformOf } from "./common.ts";
import { isDbTicket, parseDbTicket } from "./dbTicket.ts";
import { fareOf } from "./fare.ts";
import { localToInstant, matchConnection, type TransitPort } from "./match.ts";
import type { ParsedShare, ParsedTicket } from "./parsed.ts";
import { pdfText, type PdfJsLike } from "./pdfText.ts";
import { isShareText, parseShareText } from "./shareText.ts";
import type { Journey, Station } from "../transit.ts";
import { nextDiscount, type Discount } from "../travellers.ts";

interface Fixtures {
  share: { name: string; file: string; expect: ParsedShare }[];
  ticket: { name: string; file: string; expect: ParsedTicket }[];
  pdf: { name: string; file: string; same_as: string }[];
  fares: { name: string | null; fare_type: string; binding: boolean }[];
  platforms: { value: string | null; expect: string | null }[];
  lines: { a: string; b: string; same: boolean }[];
  zones: { date: string; time: string; expect: string }[];
  discounts: { name: string; ticket: Discount[]; given: Discount[]; held: number[]; expect: Discount }[];
  matching: {
    name: string;
    share: string;
    stations: Record<string, string>;
    journeys: Record<string, Journey[]>;
    expect: {
      status: string;
      matched: number;
      refresh_token: string | null;
      trip_refs: (string | null)[];
      from_names: string[];
      from_platforms: (string | null)[];
      first_departure: string;
      last_arrival: string;
      legs?: number;
    };
  }[];
}

const dir = new URL("./fixtures/", import.meta.url);
const read = (file: string) => readFileSync(new URL(file, dir), "utf8");
const fixtures: Fixtures = JSON.parse(readFileSync(new URL("./intake.fixtures.json", import.meta.url), "utf8"));

for (const c of fixtures.share) {
  test(`share text: ${c.name}`, () => {
    const text = read(c.file);
    assert.equal(isShareText(text), true);
    assert.equal(isDbTicket(text), false);
    assert.deepEqual(parseShareText(text), c.expect);
  });
}

for (const c of fixtures.ticket) {
  test(`ticket: ${c.name}`, () => {
    const text = read(c.file);
    assert.equal(isDbTicket(text), true);
    assert.equal(isShareText(text), false);
    assert.deepEqual(parseDbTicket(text), c.expect);
  });

  test(`ticket, every run of spaces collapsed (what PdfBox gives): ${c.name}`, () => {
    const text = read(c.file).replace(/[ \t]+/g, " ");
    assert.deepEqual(parseDbTicket(text), c.expect);
  });
}

for (const c of fixtures.pdf) {
  test(`pdf: ${c.name}`, async () => {
    const pdfjs = (await import("pdfjs-dist/legacy/build/pdf.mjs")) as unknown as PdfJsLike;
    const data = new Uint8Array(readFileSync(new URL(c.file, dir)));
    const text = await pdfText(data, pdfjs);
    const expected = fixtures.ticket.find((t) => t.file === c.same_as)!.expect;
    assert.deepEqual(parseDbTicket(text), expected);
  });
}

test("fare names", () => {
  for (const c of fixtures.fares) {
    const fare = fareOf(c.name);
    assert.equal(fare.fare_type, c.fare_type, `${c.name}`);
    assert.equal(fare.binding, c.binding, `${c.name}`);
  }
});

test("platforms drop their sectors", () => {
  for (const c of fixtures.platforms) assert.equal(platformOf(c.value), c.expect, `${c.value}`);
});

test("line names compare without spaces or case", () => {
  for (const c of fixtures.lines) assert.equal(lineKey(c.a) === lineKey(c.b), c.same, `${c.a} / ${c.b}`);
});

test("a ticket's discounts go to the travellers who hold the cards", () => {
  for (const c of fixtures.discounts) assert.equal(nextDiscount(c.ticket, c.given, c.held), c.expect, c.name);
});

test("local times are read in Europe/Berlin", () => {
  for (const c of fixtures.zones) assert.equal(localToInstant(c.date, c.time), c.expect, `${c.date} ${c.time}`);
});

for (const c of fixtures.matching) {
  test(`matching: ${c.name}`, async () => {
    const parsed = parseShareText(read(c.share))!;
    const port: TransitPort = {
      async stations(query: string): Promise<Station[]> {
        const id = c.stations[query];
        return id ? [{ id, name: query, lat: 0, lon: 0, country: null }] : [];
      },
      async journeys(from: string, to: string, time: string): Promise<Journey[]> {
        return c.journeys[`${from}>${to}@${time}`] ?? [];
      },
    };
    const result = await matchConnection(parsed.legs, port);
    const rides = result.journey.legs.filter((l) => l.mode !== "walk");
    assert.equal(result.status, c.expect.status);
    assert.equal(result.matched, c.expect.matched);
    assert.equal(result.total, parsed.legs.length);
    assert.equal(result.journey.refresh_token, c.expect.refresh_token);
    assert.deepEqual(rides.map((l) => l.trip_ref ?? null), c.expect.trip_refs);
    assert.deepEqual(rides.map((l) => l.from.name), c.expect.from_names);
    assert.deepEqual(rides.map((l) => l.from.planned_platform ?? null), c.expect.from_platforms);
    assert.equal(new Date(rides[0].from.planned!).toISOString(), c.expect.first_departure);
    assert.equal(new Date(rides[rides.length - 1].to.planned!).toISOString(), c.expect.last_arrival);
    if (c.expect.legs !== undefined) assert.equal(result.journey.legs.length, c.expect.legs);
  });
}

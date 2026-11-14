// Run with `npm run test:abeona` (node --test; Node strips the types).
// The cases live in intake.fixtures.json, which the Android tests run too
// (IntakeFixturesTest): add a case there, not here.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { lineKey, platformOf } from "./common.ts";
import { isDbTicket, parseDbTicket, parseDbTickets } from "./dbTicket.ts";
import { fareOf } from "./fare.ts";
import { parseText, parseTicketTrips } from "./intake.ts";
import { findSavedLeg, localToInstant, matchConnection, type TransitPort } from "./match.ts";
import type { ParsedShare, ParsedTicket } from "./parsed.ts";
import { pdfText, type PdfJsLike } from "./pdfText.ts";
import { isShareText, parseShareText } from "./shareText.ts";
import { isTrenitaliaTicket, parseTrenitaliaTicket } from "./trenitaliaTicket.ts";
import type { Journey, Leg, Station } from "../transit.ts";
import { cardsForTrip, ticketSummary, type CardOnFile } from "../ticketNeeds.ts";

interface Fixtures {
  share: { name: string; file: string; expect: ParsedShare }[];
  ticket: { name: string; file: string; expect: ParsedTicket }[];
  trenitalia: { name: string; file: string; expect: ParsedTicket }[];
  trips: { name: string; file: string; expect: ParsedTicket[] }[];
  pdf: { name: string; file: string; same_as: string }[];
  fares: { name: string | null; fare_type: string; binding: boolean }[];
  platforms: { value: string | null; expect: string | null }[];
  lines: { a: string; b: string; same: boolean }[];
  zones: { date: string; time: string; expect: string }[];
  ticket_summary: { name: string; travellers: number | null; discounts: (number | null)[]; expect: { traveller_count: number | null; bahncard_discounts: number[] } }[];
  ticket_cards: {
    name: string;
    cards: { id: string; kind: number; valid_from: string; valid_to: string; file: boolean }[];
    discounts: number[];
    traveller_count: number | null;
    fare_type: string;
    day: string;
    expect: string[];
  }[];
  matching: {
    name: string;
    share: string;
    stations: Record<string, string>;
    /** Where a looked-up station is; without it, (0, 0): no position. */
    station_coords?: Record<string, [number, number]>;
    journeys: Record<string, Journey[]>;
    expect: {
      status: string;
      matched: number;
      refresh_token: string | null;
      trip_refs: (string | null)[];
      from_names: string[];
      from_platforms: (string | null)[];
      /** Per ride: whether its start and its end have a position the map can draw. */
      located?: [boolean, boolean][];
      first_departure: string;
      last_arrival: string;
      legs?: number;
    };
  }[];
  /** A saved ride the timetable did not have, looked up again; the trip ref found, or null. */
  refinding: { name: string; leg: Leg; journeys: Record<string, Journey[]>; expect: string | null }[];
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
    assert.equal(isTrenitaliaTicket(text), false);
    assert.deepEqual(parseDbTicket(text), c.expect);
    assert.deepEqual(parseTicketTrips(text), [c.expect]);
  });

  test(`ticket, every run of spaces collapsed (what PdfBox gives): ${c.name}`, () => {
    const text = read(c.file).replace(/[ \t]+/g, " ");
    assert.deepEqual(parseDbTicket(text), c.expect);
  });
}

for (const c of fixtures.trenitalia) {
  test(`trenitalia ticket: ${c.name}`, () => {
    const text = read(c.file);
    assert.equal(isTrenitaliaTicket(text), true);
    assert.equal(isDbTicket(text), false);
    assert.equal(isShareText(text), false);
    assert.deepEqual(parseTrenitaliaTicket(text), c.expect);
    assert.deepEqual(parseTicketTrips(text), [c.expect]);
    assert.deepEqual(parseText(text), c.expect);
  });

  test(`trenitalia ticket, every run of spaces collapsed (what PdfBox gives): ${c.name}`, () => {
    const text = read(c.file).replace(/[ \t]+/g, " ");
    assert.deepEqual(parseTrenitaliaTicket(text), c.expect);
  });
}

for (const c of fixtures.trips) {
  test(`ticket for both directions: ${c.name}`, () => {
    const text = read(c.file);
    assert.equal(isDbTicket(text), true);
    assert.equal(isTrenitaliaTicket(text), false);
    assert.deepEqual(parseDbTickets(text), c.expect);
    assert.deepEqual(parseTicketTrips(text), c.expect);
    assert.deepEqual(parseTicketTrips(text.replace(/[ \t]+/g, " ")), c.expect);
  });
}

for (const c of fixtures.pdf) {
  test(`pdf: ${c.name}`, async () => {
    const pdfjs = (await import("pdfjs-dist/legacy/build/pdf.mjs")) as unknown as PdfJsLike;
    const data = new Uint8Array(readFileSync(new URL(c.file, dir)));
    const text = await pdfText(data, pdfjs);
    const expected = [...fixtures.ticket, ...fixtures.trenitalia].find((t) => t.file === c.same_as)!.expect;
    assert.deepEqual(parseTicketTrips(text), [expected]);
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

test("a ticket's travellers and BahnCards, from what it printed", () => {
  for (const c of fixtures.ticket_summary) assert.deepEqual(ticketSummary(c.travellers, c.discounts), c.expect, c.name);
});

test("the household's BahnCards shown with a ticket", () => {
  for (const c of fixtures.ticket_cards) {
    const cards: CardOnFile[] = c.cards.map((k) => ({ ...k, file_path: k.file ? `h/bahncards/${k.id}/card.pdf` : null }));
    assert.deepEqual(cardsForTrip(cards, c.discounts, c.traveller_count, c.fare_type, c.day).map((k) => k.id), c.expect, c.name);
  }
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
        const [lat, lon] = c.station_coords?.[query] ?? [0, 0];
        return id ? [{ id, name: query, lat, lon, country: null }] : [];
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
    const at = (s: { lat: number | null; lon: number | null }) => typeof s.lat === "number" && typeof s.lon === "number" && !(s.lat === 0 && s.lon === 0);
    if (c.expect.located) assert.deepEqual(rides.map((l) => [at(l.from), at(l.to)]), c.expect.located, "where the map can draw each ride");
    assert.deepEqual(rides.map((l) => l.from.planned_platform ?? null), c.expect.from_platforms);
    assert.equal(new Date(rides[0].from.planned!).toISOString(), c.expect.first_departure);
    assert.equal(new Date(rides[rides.length - 1].to.planned!).toISOString(), c.expect.last_arrival);
    if (c.expect.legs !== undefined) assert.equal(result.journey.legs.length, c.expect.legs);
  });
}

for (const c of fixtures.refinding) {
  test(`refinding: ${c.name}`, async () => {
    const port: TransitPort = {
      async stations(): Promise<Station[]> {
        return [];
      },
      async journeys(from: string, to: string, time: string): Promise<Journey[]> {
        return c.journeys[`${from}>${to}@${time}`] ?? [];
      },
    };
    const found = await findSavedLeg(c.leg, port);
    assert.equal(found?.trip_ref ?? null, c.expect);
  });
}

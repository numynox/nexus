// deno test --allow-read supabase/functions/abeona-import/traewelling.test.ts
//
// Träwelling check-ins to Abeona trips, from a fixture of real statuses
// (taken from the public /v1/statuses feed on 2026-11-03, user and text
// removed), and the paging against a fake Träwelling.

import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1";
import { groupTrips, ImportError, importStatuses, legOf } from "./traewelling.ts";

const fixture = JSON.parse(await Deno.readTextFile(new URL("./fixtures/traewelling-statuses.json", import.meta.url)));
const [ice, rb, bus] = fixture.data;

Deno.test("an ICE check-in is a train leg with its real arrival", () => {
  const leg = legOf(ice)!;
  assertEquals(leg.mode, "train");
  assertEquals(leg.product, "ICE");
  assertEquals(leg.line_name, "ICE 225");
  assertEquals(leg.from.name, "Mannheim Hbf");
  assertEquals(leg.to.name, "Stuttgart Hbf");
  assertEquals(leg.to.planned, "2026-11-03T12:33:00.000Z");
  assertEquals(leg.to.current, "2026-11-03T12:41:00.000Z");
  assertEquals(leg.realtime, true);
  assert(leg.distance_km! > 90 && leg.distance_km! < 140, `distance ${leg.distance_km}`);
  assert(typeof leg.from.lat === "number" && typeof leg.from.lon === "number");
});

Deno.test("a regional train and a bus", () => {
  assertEquals(legOf(rb)!.product, "RB");
  assertEquals(legOf(bus)!.mode, "bus");
  assertEquals(legOf(bus)!.product, "Bus");
});

Deno.test("manual times win, planes are left out, garbage is null", () => {
  const manual = structuredClone(ice);
  manual.checkin.manualArrival = "2026-11-03T13:50:00+01:00";
  assertEquals(legOf(manual)!.to.current, "2026-11-03T12:50:00.000Z");
  const plane = structuredClone(ice);
  plane.checkin.category = "plane";
  assertEquals(legOf(plane), null);
  assertEquals(legOf({}), null);
  assertEquals(legOf({ checkin: { category: "regional", origin: {}, destination: {} } }), null);
});

Deno.test("rides that meet at a station within 90 minutes are one trip", () => {
  const first = structuredClone(ice);
  const second = structuredClone(rb);
  second.id = 2;
  second.checkin.origin.station.name = "Stuttgart Hbf";
  second.checkin.origin.departurePlanned = "2026-11-03T13:05:00+00:00";
  second.checkin.origin.departureReal = "2026-11-03T13:05:00+00:00";
  second.checkin.destination.arrivalPlanned = "2026-11-03T13:40:00+00:00";
  second.checkin.destination.arrivalReal = "2026-11-03T13:52:00+00:00";
  const later = structuredClone(bus);
  later.checkin.origin.departurePlanned = "2026-11-04T08:00:00+00:00";
  later.checkin.destination.arrivalPlanned = "2026-11-04T08:10:00+00:00";
  const trips = groupTrips([legOf(second)!, legOf(first)!, legOf(later)!]);
  assertEquals(trips.length, 2);
  const joined = trips.find((t) => t.legs.length === 2)!;
  assertEquals(joined.origin, "Mannheim Hbf");
  assertEquals(joined.destination, "Bad Staffelstein");
  assertEquals(joined.arrival_delay_minutes, 12);
  assertEquals(joined.external_id, "traewelling:1000,2");
});

function fakeTraewelling(pages: unknown[][], status = 200) {
  const urls: string[] = [];
  const fetcher = (url: string, init: RequestInit) => {
    urls.push(url);
    if ((init.headers as Record<string, string>).Authorization !== "Bearer secret-token-of-twenty-chars") {
      return Promise.resolve(new Response("{}", { status: 401 }));
    }
    if (status !== 200) return Promise.resolve(new Response("{}", { status }));
    if (url.endsWith("/v1/auth/user")) return Promise.resolve(Response.json({ data: { username: "fixture" } }));
    const page = Number(new URL(url).searchParams.get("page"));
    return Promise.resolve(Response.json({
      data: pages[page - 1] ?? [],
      links: { next: page < pages.length ? `x?page=${page + 1}` : null },
    }));
  };
  return { urls, fetcher };
}

Deno.test("reads pages until it passes the start of the range", async () => {
  const older = structuredClone(rb);
  older.checkin.origin.departurePlanned = "2026-10-01T08:00:00+00:00";
  const { urls, fetcher } = fakeTraewelling([[ice, rb], [bus, older], [ice]]);
  const result = await importStatuses("secret-token-of-twenty-chars", "2026-11-01", "2026-11-03", fetcher);
  assertEquals(result.statuses, 3);
  assertEquals(result.trips.length, 3);
  assertEquals(urls.filter((u) => u.includes("statuses")).length, 2, "stops after the page with an older check-in");
  assertEquals(result.truncated, false);
});

Deno.test("check-ins after the range are skipped, not counted", async () => {
  const { fetcher } = fakeTraewelling([[ice, rb, bus]]);
  const result = await importStatuses("secret-token-of-twenty-chars", "2026-10-01", "2026-10-31", fetcher);
  assertEquals(result.statuses, 0);
  assertEquals(result.trips, []);
});

Deno.test("a refused token says so, without repeating it", async () => {
  const { fetcher } = fakeTraewelling([[]]);
  const error = await assertRejects(() => importStatuses("another-token-of-twenty-chars", "2026-11-01", "2026-11-03", fetcher), ImportError);
  assertEquals(error.status, 401);
  assert(!error.message.includes("another-token"));
});

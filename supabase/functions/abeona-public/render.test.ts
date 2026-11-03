// deno test supabase/functions/abeona-public/render.test.ts
//
// The calendar file and the status JSON for a shared trip, and when the live
// refresh is asked and how it is merged.

import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1";
import type { Leg } from "../abeona-transit/contract.ts";
import { isLiveWindow, mergeLive, withLive } from "./live.ts";
import { type SharedLeg, type SharedTrip, toIcs, toStatus } from "./render.ts";

function stop(name: string, planned: string, current: string | null = null, platform: string | null = null) {
  return { name, planned, current, planned_platform: platform, platform };
}

function trip(legs: SharedLeg[], extra: Partial<SharedTrip["trip"]> = {}): SharedTrip {
  return {
    kind: "status",
    expires_at: "2026-11-03T20:00:00Z",
    trip: {
      uid: "t1",
      origin: legs[0].from.name,
      destination: legs[legs.length - 1].to.name,
      planned_departure: legs[0].from.planned!,
      planned_arrival: legs[legs.length - 1].to.planned!,
      cancelled: false,
      final: false,
      refreshed_at: null,
      ...extra,
    },
    legs,
  };
}

const ice: SharedLeg = {
  uid: "l0", mode: "train", product: "ICE", line_name: "ICE 25", headsign: "München Hbf",
  from: stop("Frankfurt (Main) Hbf", "2026-11-03T10:00:00Z", "2026-11-03T10:02:00Z", "7"),
  to: stop("Würzburg Hbf", "2026-11-03T11:08:00Z", "2026-11-03T11:20:00Z", "4"),
  cancelled: false, realtime: true, final: false,
};
const walk: SharedLeg = {
  uid: "l1", mode: "walk", product: null, line_name: null, headsign: null,
  from: stop("Würzburg Hbf", "2026-11-03T11:08:00Z"), to: stop("Würzburg Hbf", "2026-11-03T11:12:00Z"),
  cancelled: false, realtime: false, final: false,
};
const re: SharedLeg = {
  uid: "l2", mode: "train", product: "RE", line_name: "RE 10", headsign: null,
  from: stop("Würzburg Hbf", "2026-11-03T11:30:00Z", null, "2"),
  to: stop("Bamberg", "2026-11-03T12:30:00Z", "2026-11-03T12:37:00Z"),
  cancelled: false, realtime: true, final: false,
};

Deno.test("the calendar has one event per ride, at the expected times, with the delay", () => {
  const ics = toIcs({ ...trip([ice, walk, re]), kind: "ics" }, new Date("2026-11-03T09:00:00Z"));
  assertEquals(ics.match(/BEGIN:VEVENT/g)?.length, 2, "walks are not events");
  assertStringIncludes(ics, "DTSTART:20261103T100200Z");
  assertStringIncludes(ics, "DTEND:20261103T112000Z");
  assertStringIncludes(ics, "SUMMARY:ICE 25 Frankfurt (Main) Hbf → Würzburg Hbf (+12)");
  assertStringIncludes(ics, "UID:l0@abeona.nexus");
  assertStringIncludes(ics, "LOCATION:Frankfurt (Main) Hbf\\, platform 7");
  assertStringIncludes(ics, "STATUS:CONFIRMED");
  assert(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
  assert(ics.endsWith("END:VCALENDAR\r\n"));
  for (const line of ics.split("\r\n")) {
    assert(new TextEncoder().encode(line).length <= 75, `folded: ${line}`);
  }
});

Deno.test("a cancelled ride is a cancelled event, and later fetches carry a higher sequence", () => {
  const cancelled = { ...re, cancelled: true };
  const a = toIcs({ ...trip([ice, cancelled]), kind: "ics" }, new Date("2026-11-03T09:00:00Z"));
  const b = toIcs({ ...trip([ice, cancelled]), kind: "ics" }, new Date("2026-11-03T09:05:00Z"));
  assertStringIncludes(a, "SUMMARY:Cancelled: RE 10");
  assertStringIncludes(a, "STATUS:CANCELLED");
  const seq = (s: string) => Number(/SEQUENCE:(\d+)/.exec(s)![1]);
  assert(seq(b) > seq(a));
});

Deno.test("status: before, on board, changing, arrived", () => {
  const t = trip([ice, walk, re]);
  assertEquals(toStatus(t, new Date("2026-11-03T09:30:00Z")).state, "upcoming");
  const onBoard = toStatus(t, new Date("2026-11-03T10:30:00Z"));
  assertEquals(onBoard.state, "on_board");
  assertEquals(onBoard.current?.line_name, "ICE 25");
  const changing = toStatus(t, new Date("2026-11-03T11:25:00Z"));
  assertEquals(changing.state, "changing");
  assertEquals(changing.current?.line_name, "RE 10");
  const arrived = toStatus(t, new Date("2026-11-03T13:00:00Z"));
  assertEquals(arrived.state, "arrived");
  assertEquals(arrived.expected_arrival, "2026-11-03T12:37:00Z");
  assertEquals(arrived.delay_minutes, 7);
  assertEquals(arrived.legs.length, 2);
  assertEquals(arrived.live, true);
});

Deno.test("status of a cancelled trip", () => {
  assertEquals(toStatus(trip([ice], { cancelled: true }), new Date("2026-11-03T10:30:00Z")).state, "cancelled");
});

Deno.test("the status says nothing about who travels", () => {
  const json = JSON.stringify(toStatus(trip([ice, re]), new Date("2026-11-03T10:30:00Z")));
  for (const word of ["title", "price", "traveller", "household", "ticket"]) assert(!json.includes(word), word);
});

Deno.test("live only around the trip, never when final or imported", async () => {
  const t = trip([ice, re]);
  assertEquals(isLiveWindow(t, new Date("2026-11-03T07:30:00Z")), false);
  assertEquals(isLiveWindow(t, new Date("2026-11-03T08:30:00Z")), true);
  assertEquals(isLiveWindow(t, new Date("2026-11-03T13:30:00Z")), true);
  assertEquals(isLiveWindow(t, new Date("2026-11-03T13:40:00Z")), false);
  assertEquals(isLiveWindow(trip([ice, re], { final: true }), new Date("2026-11-03T10:00:00Z")), false);

  let asked = 0;
  const provider = {
    refreshItinerary: () => {
      asked++;
      return Promise.reject(new Error("no"));
    },
    refreshLeg: () => {
      asked++;
      return Promise.resolve(null);
    },
  };
  const refs = { refresh_token: "tok", source: "import", legs: [] };
  await withLive(t, refs, new Date("2026-11-03T10:00:00Z"), provider);
  assertEquals(asked, 0, "an imported trip is never refreshed");
});

Deno.test("live values replace stored ones by position and mode; final legs stay", () => {
  const live = (arrival: string): Leg => ({
    mode: "train", product: "ICE", line_name: "ICE 25", trip_ref: null, headsign: null, operator: null,
    from: { id: null, name: "A", lat: null, lon: null, planned: null, current: null, planned_platform: null, platform: "9", cancelled: false },
    to: { id: null, name: "B", lat: null, lon: null, planned: null, current: arrival, planned_platform: null, platform: null, cancelled: false },
    stopovers: [], polyline: null, cancelled: false, realtime: true,
  });
  const merged = mergeLive([ice, walk, { ...re, final: true }], [live("2026-11-03T11:31:00Z"), live("x"), live("2026-11-03T13:00:00Z")]);
  assertEquals(merged[0].to.current, "2026-11-03T11:31:00Z");
  assertEquals(merged[0].from.platform, "9");
  assertEquals(merged[1], walk, "a mode mismatch is left alone");
  assertEquals(merged[2].to.current, "2026-11-03T12:37:00Z", "final stays final");
});

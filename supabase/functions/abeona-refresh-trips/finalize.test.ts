// deno test supabase/functions/abeona-refresh-trips/finalize.test.ts
//
// The job against a fake database and a fake Transitous: which legs it
// writes, that final legs are left alone, and that one failing trip does not
// stop the others. Set ABEONA_LIVE_TRIP_REF to also refresh one real trip
// through Transitous (needs the network).

import { assert, assertEquals } from "jsr:@std/assert@1";
import type { Journey, Leg } from "../abeona-transit/contract.ts";
import type { Refresher } from "../abeona-transit/refresh.ts";
import { refreshLeg } from "../abeona-transit/transitous.ts";
import { type Database, type DueTrip, finalizeDueTrips, updatesFor } from "./finalize.ts";

function leg(mode: Leg["mode"], arrival: string | null, line = "ICE 25"): Leg {
  const stop = (name: string, current: string | null) => ({
    id: null, name, lat: null, lon: null, planned: "2026-11-03T12:00:00Z", current,
    planned_platform: "4", platform: "5", cancelled: false,
  });
  return {
    mode, product: null, line_name: mode === "walk" ? null : line, trip_ref: null, headsign: null, operator: null,
    from: stop("A", null), to: stop("B", arrival), stopovers: [], polyline: null, cancelled: false, realtime: arrival !== null,
  };
}

const saved = (idx: number, mode: string, final = false) => ({
  idx, mode, final, trip_ref: mode === "walk" ? null : `ref-${idx}`, from_id: null, to_id: null, from_name: "A", to_name: "B",
});

function fakeDb(due: DueTrip[]) {
  const calls: { fn: string; args: Record<string, unknown> }[] = [];
  const db: Database = {
    rpc(fn, args) {
      calls.push({ fn, args });
      if (fn === "abeona_trips_to_finalize") return Promise.resolve({ data: due, error: null });
      if (fn === "abeona_finalize_trip") {
        const legs = args.p_legs as unknown[];
        return Promise.resolve(args.p_trip_id === "broken"
          ? { data: null, error: { message: "boom" } }
          : { data: legs.length > 0, error: null });
      }
      return Promise.resolve({ data: null, error: { message: `unexpected ${fn}` } });
    },
  };
  return { db, calls };
}

Deno.test("writes every leg the provider answered for, and skips final ones", () => {
  const trip: DueTrip = {
    trip_id: "t", refresh_token: null, expected_arrival: "",
    legs: [saved(0, "train", true), saved(1, "walk"), saved(2, "train")],
  };
  const updates = updatesFor(trip, [leg("train", "x"), leg("walk", null), leg("train", "2026-11-03T13:05:00Z")]);
  assertEquals(updates.map((u) => u.idx), [1, 2]);
  assertEquals(updates[1].arrival, "2026-11-03T13:05:00Z");
  assertEquals(updates[1].arrival_platform, "5");
});

Deno.test("a refreshed leg of another mode is not taken for the saved one", () => {
  const trip: DueTrip = { trip_id: "t", refresh_token: null, expected_arrival: "", legs: [saved(0, "train")] };
  assertEquals(updatesFor(trip, [leg("bus", "x")]).length, 0);
});

Deno.test("the itinerary first, then leg by leg; a failing trip does not stop the rest", async () => {
  const provider: Refresher = {
    refreshItinerary: (token) =>
      token === "tok"
        ? Promise.resolve({ provider: "transitous", refresh_token: token, legs: [leg("train", "2026-11-03T13:02:00Z")] } as Journey)
        : Promise.reject(new Error("gone")),
    refreshLeg: (s) => Promise.resolve(s.trip_ref === "ref-0" ? leg("train", "2026-11-03T13:30:00Z") : null),
  };
  const { db, calls } = fakeDb([
    { trip_id: "a", refresh_token: "tok", expected_arrival: "", legs: [saved(0, "train")] },
    { trip_id: "broken", refresh_token: "old", expected_arrival: "", legs: [saved(0, "train")] },
    { trip_id: "c", refresh_token: null, expected_arrival: "", legs: [saved(0, "train"), saved(1, "train")] },
  ]);
  const results = await finalizeDueTrips(db, provider, 10);

  assertEquals(calls[0], { fn: "abeona_trips_to_finalize", args: { p_limit: 10 } });
  const writes = calls.filter((c) => c.fn === "abeona_finalize_trip");
  assertEquals(writes.length, 3);
  assertEquals((writes[0].args.p_legs as { arrival: string }[])[0].arrival, "2026-11-03T13:02:00Z");
  assertEquals((writes[2].args.p_legs as { idx: number }[]).map((u) => u.idx), [0]);

  assertEquals(results.map((r) => [r.trip_id, r.final, r.error ?? null]), [
    ["a", true, null],
    ["broken", false, "boom"],
    ["c", true, null],
  ]);
});

const liveRef = Deno.env.get("ABEONA_LIVE_TRIP_REF");
Deno.test({
  name: "live: one real leg through Transitous",
  ignore: !liveRef,
  async fn() {
    const [tripRef, from, to] = liveRef!.split("|");
    const refreshed = await refreshLeg({ trip_ref: tripRef, from_id: null, to_id: null, from_name: from, to_name: to });
    assert(refreshed, "Transitous found the leg");
    const trip: DueTrip = { trip_id: "live", refresh_token: null, expected_arrival: "", legs: [saved(0, refreshed.mode)] };
    const [update] = updatesFor(trip, [refreshed]);
    console.log(JSON.stringify({ ...update, stopovers: update.stopovers.length }));
  },
});

// deno test supabase/functions/abeona-transit/cache.test.ts
//
// The response cache's key and freshness, and the country of a stop.

import { assertEquals, assertNotEquals } from "jsr:@std/assert@1";
import { cacheKey, memoryCache, TTL_SECONDS } from "./cache.ts";
import { countryOf } from "./transitous.ts";

Deno.test("the key ignores parameter order and undefined values", async () => {
  const a = await cacheKey("/api/v6/plan", { fromPlace: "a", toPlace: "b", pageCursor: undefined });
  const b = await cacheKey("/api/v6/plan", { toPlace: "b", fromPlace: "a" });
  assertEquals(a, b);
  assertEquals(a.length, 64);
  assertNotEquals(a, await cacheKey("/api/v6/plan", { fromPlace: "a", toPlace: "c" }));
  assertNotEquals(a, await cacheKey("/api/v6/trip", { fromPlace: "a", toPlace: "b" }));
});

Deno.test("an answer is fresh for its path's time to live, then a miss", async () => {
  let now = 0;
  const cache = memoryCache(() => now);
  await cache.put("k", { ok: true });
  now = (TTL_SECONDS["/api/v6/plan"] - 1) * 1000;
  assertEquals(await cache.get("k", TTL_SECONDS["/api/v6/plan"]), { ok: true });
  now = (TTL_SECONDS["/api/v6/plan"] + 1) * 1000;
  assertEquals(await cache.get("k", TTL_SECONDS["/api/v6/plan"]), undefined);
});

Deno.test("time to live: a day for stations, two minutes for journeys, one for refreshes", () => {
  assertEquals(TTL_SECONDS["/api/v1/geocode"], 86400);
  assertEquals(TTL_SECONDS["/api/v6/plan"], 120);
  assertEquals(TTL_SECONDS["/api/v6/refresh-itinerary"], 60);
  assertEquals(TTL_SECONDS["/api/v6/trip"], 60);
});

Deno.test("a stop's country comes from its time zone, else its id", () => {
  // As Transitous answered for Munich → Salzburg on 2026-11-03.
  assertEquals(countryOf({ tz: "Europe/Vienna", stopId: "de-DELFI_at:45:50002" }), "AT");
  assertEquals(countryOf({ tz: "Europe/Berlin", parentId: "de-DELFI_de:09162:100" }), "DE");
  assertEquals(countryOf({ stopId: "ch-opentransportdataswiss26_Parentch:1:sloid:10" }), "CH");
  assertEquals(countryOf({ tz: "America/Nowhere", stopId: "x" }), null);
  assertEquals(countryOf(null), null);
});

// deno test supabase/functions/abeona-transit/towns.test.ts
import { assertEquals } from "jsr:@std/assert@1";
import { mayLackTown, townOf, withTown } from "./transitous.ts";

Deno.test("a stop named by itself may lack its town", () => {
  assertEquals(mayLackTown("Hauptbahnhof"), true);
  assertEquals(mayLackTown("Pasing"), true);
  assertEquals(mayLackTown("Hbf (tief)"), true);
  assertEquals(mayLackTown("Bahnhof Süd"), true);
  assertEquals(mayLackTown("Ulm Hauptbahnhof"), false);
  assertEquals(mayLackTown("Augsburg Hbf"), false);
  assertEquals(mayLackTown("Mannheim, Hauptbahnhof"), false);
});

Deno.test("the town is the municipality, else a city that is its own district", () => {
  const stuttgart = [
    { name: "Deutschland", adminLevel: 2 }, { name: "Baden-Württemberg", adminLevel: 4 },
    { name: "Regierungsbezirk Stuttgart", adminLevel: 5 }, { name: "Stuttgart", adminLevel: 6 },
    { name: "Stuttgart-Mitte", adminLevel: 9 },
  ];
  assertEquals(townOf(stuttgart), "Stuttgart");
  const bruchsal = [{ name: "Landkreis Karlsruhe", adminLevel: 6 }, { name: "Bruchsal", adminLevel: 8 }];
  assertEquals(townOf(bruchsal), "Bruchsal");
  assertEquals(townOf([{ name: "Landkreis Karlsruhe", adminLevel: 6 }]), null);
  assertEquals(townOf(undefined), null);
});

Deno.test("a name gets its town only when it does not say it", () => {
  assertEquals(withTown("Hauptbahnhof", "Stuttgart"), "Stuttgart, Hauptbahnhof");
  assertEquals(withTown("Pasing", "München"), "München, Pasing");
  assertEquals(withTown("Leuven", "Leuven"), "Leuven");
  assertEquals(withTown("Frankfurt(Main)Hbf", "Frankfurt am Main"), "Frankfurt(Main)Hbf");
  assertEquals(withTown("Hauptbahnhof", null), "Hauptbahnhof");
});

// deno test supabase/functions/abeona-refresh-trips/orphans.test.ts

import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { removeOrphanedFiles } from "./orphans.ts";

Deno.test("removes what the database lists, in one call", async () => {
  const removed: string[][] = [];
  const n = await removeOrphanedFiles(
    { rpc: () => Promise.resolve({ data: ["h/t/a.pdf", { abeona_orphaned_files: "h/bahncards/c/b.pdf" }], error: null }) },
    { remove: (paths) => (removed.push(paths), Promise.resolve({ error: null })) },
  );
  assertEquals(n, 2);
  assertEquals(removed, [["h/t/a.pdf", "h/bahncards/c/b.pdf"]]);
});

Deno.test("nothing listed, nothing removed", async () => {
  let called = false;
  const n = await removeOrphanedFiles(
    { rpc: () => Promise.resolve({ data: [], error: null }) },
    { remove: () => ((called = true), Promise.resolve({ error: null })) },
  );
  assertEquals(n, 0);
  assertEquals(called, false);
});

Deno.test("a failing listing is reported, not swallowed", async () => {
  await assertRejects(
    () => removeOrphanedFiles(
      { rpc: () => Promise.resolve({ data: null, error: { message: "denied" } }) },
      { remove: () => Promise.resolve({ error: null }) },
    ),
    Error,
    "denied",
  );
});

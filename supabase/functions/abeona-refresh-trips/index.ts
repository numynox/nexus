// abeona-refresh-trips: the final snapshot of Abeona's trips, run by pg_cron
// every 15 minutes through public.invoke_abeona_refresh_trips(), which reads
// this function's URL and its invoke secret from Vault.
//
// Like fetch-rss it is guarded by a shared secret, not a user's JWT
// (verify_jwt = false), and works with the service role: it reads every
// household's trips that arrived 15 to 90 minutes ago, refreshes them through
// Transitous once more and freezes the values (abeona_finalize_trip).
//
// After that, as a separate step whose failure does not touch the snapshot,
// it removes a few files from abeona-tickets that no trip or BahnCard points
// at any more (orphans.ts) — Postgres cannot delete Storage objects itself.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { databaseCache } from "../abeona-transit/cache.ts";
import { useCache } from "../abeona-transit/transitous.ts";
import { finalizeDueTrips } from "./finalize.ts";
import { removeOrphanedFiles } from "./orphans.ts";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body) + "\n", {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  const invokeSecret = Deno.env.get("ABEONA_REFRESH_INVOKE_SECRET") ?? "";
  if (!invokeSecret) return json({ error: "ABEONA_REFRESH_INVOKE_SECRET is not configured" }, 500);

  const authHeader = req.headers.get("authorization") ?? "";
  const bearer = authHeader.startsWith("Bearer ") ? authHeader.slice("Bearer ".length).trim() : "";
  const token = bearer || (req.headers.get("x-invoke-secret") ?? "");
  if (!token || token !== invokeSecret) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceKey) return json({ error: "Supabase environment is not configured" }, 500);

  try {
    const supabase = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
    useCache(databaseCache(supabase));
    const trips = await finalizeDueTrips(supabase);
    let orphans: number | string;
    try {
      orphans = await removeOrphanedFiles(supabase, supabase.storage.from("abeona-tickets"));
    } catch (error) {
      orphans = error instanceof Error ? error.message : String(error);
    }
    return json({
      trips: trips.length,
      finalized: trips.filter((t) => t.final).length,
      failed: trips.filter((t) => t.error).length,
      orphaned_files_removed: orphans,
      results: trips,
    });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : String(error) }, 500);
  }
});

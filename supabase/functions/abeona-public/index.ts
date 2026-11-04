// abeona-public: what an Abeona share link opens, for anyone holding it.
//
//   GET /functions/v1/abeona-public?t=<token>
//     ics     text/calendar, one event per ride (subscribe to follow delays)
//     status  JSON for the status page (app/abeona/src/pages/status.astro)
//
// No JWT (verify_jwt = false): the token is the credential. It is looked up
// with the service role through abeona_shared_trip(), which returns nothing
// for an unknown, malformed or expired token and only a trip's route, times
// and platforms for a valid one (decisions/2026-11-04-abeona-share-links.md).
// Around the trip, the times are refreshed live through the cached
// Transitous refresh; nothing is written.
//
// HTML is not served from here: Supabase rewrites text/html from Edge
// Functions to text/plain on its own domain, so the status page is a static
// page on GitHub Pages that fetches this JSON.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { databaseCache } from "../abeona-transit/cache.ts";
import { useCache } from "../abeona-transit/transitous.ts";
import { type RefreshRefs, withLive } from "./live.ts";
import { type SharedTrip, toIcs, toStatus } from "./render.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
};

function json(body: unknown, status = 200, maxAge = 0): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
      "Cache-Control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store",
      "X-Robots-Tag": "noindex",
    },
  });
}

const TOKEN = /^[0-9a-f]{36}$/;

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const supabase = supabaseUrl && serviceKey
  ? createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } })
  : null;
if (supabase) useCache(databaseCache(supabase));

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return json({ error: "Method not allowed" }, 405);
  if (!supabase) return json({ error: "Supabase environment is not configured" }, 500);

  const url = new URL(req.url);
  // ?t=<token>, or the token as the last path segment (".ics" allowed), for
  // calendar apps that want a file name.
  const raw = url.searchParams.get("t") ?? url.pathname.split("/").pop()?.replace(/\.ics$/, "") ?? "";
  const token = raw.trim().toLowerCase();
  if (!TOKEN.test(token)) return json({ error: "This link is not valid." }, 404);

  const { data, error } = await supabase.rpc("abeona_shared_trip", { p_token: token });
  if (error) return json({ error: "Could not open this link right now." }, 500);
  if (!data) return json({ error: "This link has expired or was revoked." }, 404);

  const { refresh, ...stored } = data as SharedTrip & { refresh: RefreshRefs | null };
  const now = new Date();
  const shared = await withLive(stored, refresh, now);

  if (shared.kind === "ics") {
    return new Response(toIcs(shared, now), {
      headers: {
        ...corsHeaders,
        "Content-Type": "text/calendar; charset=utf-8",
        "Content-Disposition": 'inline; filename="abeona-trip.ics"',
        "Cache-Control": "public, max-age=60",
        "X-Robots-Tag": "noindex",
      },
    });
  }
  return json(toStatus(shared, now), 200, 30);
});

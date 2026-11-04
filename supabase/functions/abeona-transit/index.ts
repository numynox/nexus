// Abeona's only door to timetable and realtime data: station search, journey
// search, refreshing a saved journey's live values, and alternatives for the
// rest of a trip, marked against its ticket.
//
// It runs as an Edge Function, not in the browser, so the provider can change
// without a client release (an installed APK lags behind) and so every client
// gets one normalised shape (contract.ts). Nothing is written here: the
// clients save trips and live values through RPCs, under RLS, as the caller.
// The one exception is the response cache (cache.ts), service role only.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { databaseCache } from "./cache.ts";
import type { Allowance, AlternativesResponse, Request } from "./contract.ts";
import * as transitous from "./transitous.ts";
import { ProviderError } from "./transitous.ts";
import { refreshJourney } from "./refresh.ts";
import { mark, rank } from "./validity.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function isId(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= 300;
}

function isoOrNow(value: unknown): string | null {
  if (value === undefined || value === null || value === "") return new Date().toISOString();
  if (typeof value !== "string") return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  // A year either way covers past trips for the history and any bookable date.
  const yearMs = 366 * 24 * 3600 * 1000;
  if (Math.abs(date.getTime() - Date.now()) > yearMs) return null;
  return date.toISOString();
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Connections from where the traveller is to the destination, marked against
 * the ticket. The allowance comes from abeona_allowed_products, asked as the
 * caller, so RLS decides whether the trip is theirs; the timetable is one
 * Transitous plan request, the same as a journey search. A ticket that never
 * covers long-distance trains is searched for regional trains only, so its
 * few valid connections are not crowded out.
 */
async function alternatives(
  request: Extract<Request, { action: "alternatives" }>,
  // deno-lint-ignore no-explicit-any
  supabase: any,
  time: string,
): Promise<AlternativesResponse> {
  const { data, error } = await supabase.rpc("abeona_allowed_products", { p_trip_id: request.trip_id });
  const allowance: Allowance | null = error ? null : (data as Allowance | null);
  const result = await transitous.journeys({
    from: request.from,
    to: request.to,
    time,
    arriveBy: false,
    products: allowance && allowance.known && !allowance.long_distance ? "regional" : "all",
    count: 8,
  });
  return { allowance, alternatives: rank(result.journeys.map((j) => mark(j, allowance))) };
}

// Transitous answers are cached for a minute or two (a day for station
// search) in abeona_transit_cache, with the service role; the table is not
// readable through the API. Without the key the function simply asks every
// time.
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
if (serviceKey && Deno.env.get("SUPABASE_URL")) {
  transitous.useCache(databaseCache(
    createClient(Deno.env.get("SUPABASE_URL")!, serviceKey, { auth: { persistSession: false } }),
  ));
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (!supabaseUrl || !supabaseAnonKey) {
    return json({ error: "Supabase auth environment is not configured" }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);

  // Runs as the caller: only signed-in Nexus users may spend our fair share
  // of Transitous.
  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  if (authError || !user) return json({ error: "Unauthorized" }, 401);

  let request: Request;
  try {
    request = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  try {
    switch (request?.action) {
      case "locations": {
        const query = typeof request.query === "string" ? request.query.trim() : "";
        if (query.length < 2 || query.length > 100) {
          return json({ error: "A search text of 2 to 100 characters is required" }, 400);
        }
        return json({ stations: await transitous.locations(query) });
      }

      case "journeys": {
        if (!isId(request.from) || !isId(request.to)) {
          return json({ error: "Both from and to stations are required" }, 400);
        }
        const time = isoOrNow(request.time);
        if (!time) return json({ error: "time must be an ISO date within a year of today" }, 400);
        const result = await transitous.journeys({
          from: request.from,
          to: request.to,
          time,
          arriveBy: request.arrive_by === true,
          products: request.products === "regional" ? "regional" : "all",
          cursor: isId(request.cursor) ? request.cursor : undefined,
        });
        return json(result);
      }

      case "alternatives": {
        if (typeof request.trip_id !== "string" || !UUID.test(request.trip_id)) {
          return json({ error: "trip_id must be a trip's id" }, 400);
        }
        if (!isId(request.from) || !isId(request.to)) {
          return json({ error: "Both from and to are required" }, 400);
        }
        const time = isoOrNow(request.time);
        if (!time) return json({ error: "time must be an ISO date within a year of today" }, 400);
        return json(await alternatives(request, supabase, time));
      }

      case "refresh":
        return json(await refreshJourney(request.refresh_token, request.legs));

      default:
        return json({ error: "Unknown action" }, 400);
    }
  } catch (error) {
    if (error instanceof ProviderError) {
      return json({ error: error.message }, error.status === 404 ? 404 : error.status);
    }
    return json({ error: `Request failed: ${error instanceof Error ? error.message : error}` }, 500);
  }
});

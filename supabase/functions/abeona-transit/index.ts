// Abeona's only door to timetable and realtime data: station search, journey
// search, and refreshing a saved journey's live values.
//
// It runs as an Edge Function, not in the browser, so the provider can change
// without a client release (an installed APK lags behind) and so every client
// gets one normalised shape (contract.ts). Nothing is written here: the
// clients save trips and live values through RPCs, under RLS, as the caller.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import type { Journey, Leg, Request } from "./contract.ts";
import * as transitous from "./transitous.ts";
import { ProviderError } from "./transitous.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const MAX_REFRESH_LEGS = 12;

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

async function refresh(request: Extract<Request, { action: "refresh" }>) {
  const saved = Array.isArray(request.legs) ? request.legs.slice(0, MAX_REFRESH_LEGS) : [];

  // First try the whole itinerary; it is one request, and it also notices a
  // leg that has been cancelled outright.
  if (isId(request.refresh_token)) {
    try {
      const journey: Journey = await transitous.refreshItinerary(request.refresh_token);
      if (saved.length === 0 || journey.legs.length === saved.length) {
        return { journey, legs: journey.legs };
      }
    } catch {
      // Fall through to refreshing leg by leg.
    }
  }

  const legs: (Leg | null)[] = await Promise.all(
    saved.map(async (leg) => {
      if (!isId(leg?.trip_ref) || typeof leg.from_name !== "string" || typeof leg.to_name !== "string") {
        return null;
      }
      try {
        return await transitous.refreshLeg({
          trip_ref: leg.trip_ref,
          from_id: isId(leg.from_id) ? leg.from_id : null,
          to_id: isId(leg.to_id) ? leg.to_id : null,
          from_name: leg.from_name,
          to_name: leg.to_name,
        });
      } catch {
        return null;
      }
    }),
  );
  return { journey: null, legs };
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

      case "refresh":
        return json(await refresh(request));

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

// abeona-import: brings past trips in from elsewhere. One action for now,
// import_traewelling: the client sends a Träwelling personal access token
// with a date range, the function reads the user's check-ins and answers
// them as trips in the transit contract's shape. It saves nothing: the client
// shows the list and saves the chosen ones through abeona_save_trip with
// source 'import', as the caller.
//
// The token is used for this one request and then forgotten: it is not
// stored, logged or echoed, here or by the clients.
//
// Runs as the caller (verify_jwt = true, re-checked with auth.getUser()), so
// only signed-in Nexus users can make it call Träwelling, and the duplicate
// check sees only their household's trips.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { ImportError, importStatuses } from "./traewelling.ts";

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

const DATE = /^\d{4}-\d{2}-\d{2}$/;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (!supabaseUrl || !supabaseAnonKey) return json({ error: "Supabase auth environment is not configured" }, 500);

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return json({ error: "Unauthorized" }, 401);
  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: { user }, error: authError } = await supabase.auth.getUser();
  if (authError || !user) return json({ error: "Unauthorized" }, 401);

  // deno-lint-ignore no-explicit-any
  let body: any;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  if (body?.action !== "import_traewelling") return json({ error: "Unknown action" }, 400);

  const token = typeof body.token === "string" ? body.token.trim() : "";
  if (token.length < 20 || token.length > 4000 || /\s/.test(token)) {
    return json({ error: "Paste the whole Träwelling token." }, 400);
  }
  const from = typeof body.from === "string" && DATE.test(body.from) ? body.from : null;
  const to = typeof body.to === "string" && DATE.test(body.to) ? body.to : null;
  if (!from || !to || from > to) return json({ error: "Choose a date range, the first day before the last." }, 400);
  if (Date.parse(to) - Date.parse(from) > 366 * 86400_000) return json({ error: "Import at most a year at a time." }, 400);

  try {
    const result = await importStatuses(token, from, to);

    // Which are saved already: the household's trips in the range, as the
    // caller, matched by origin and planned departure.
    if (result.trips.length > 0) {
      const { data } = await supabase
        .from("abeona_trips")
        .select("origin_name, planned_departure")
        .gte("planned_departure", `${from}T00:00:00Z`)
        .lte("planned_departure", `${to}T23:59:59Z`);
      const saved = new Set(
        ((data ?? []) as { origin_name: string; planned_departure: string }[])
          .map((t) => `${t.origin_name.toLowerCase()}|${Date.parse(t.planned_departure)}`),
      );
      for (const trip of result.trips) {
        trip.duplicate = saved.has(`${trip.origin.toLowerCase()}|${Date.parse(trip.planned_departure)}`);
      }
    }
    return json(result);
  } catch (error) {
    if (error instanceof ImportError) return json({ error: error.message }, error.status);
    return json({ error: "The import failed." }, 500);
  }
});

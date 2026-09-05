// Looks a barcode up in Open Food Facts and returns the fields Annona stores.
//
// This runs as an Edge Function rather than from the browser for three reasons:
// the request needs a User-Agent identifying the application (Open Food Facts
// asks for one and rate-limits anonymous traffic), the response is large and
// gets trimmed here to the handful of fields Annona keeps, and the browser is
// spared a cross-origin request to a third party.
//
// Nothing is written to the database: the caller decides whether to create a
// product from the result, and once created the local product is what later
// scans find.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const OFF_ENDPOINT = "https://world.openfoodfacts.org/api/v2/product";
const USER_AGENT = "Nexus-Annona/1.0 (https://github.com/numynox/nexus)";
// Long enough for a slow answer, short enough that a dead one does not hold the
// scanner. The client shows what went wrong and offers a retry, so waiting
// longer buys nothing.
const REQUEST_TIMEOUT_MS = 6000;

/** Only the fields Annona stores, so the response stays small. */
const OFF_FIELDS = [
  "product_name",
  "product_name_de",
  "product_name_en",
  "brands",
  "quantity",
  "image_front_url",
  "image_url",
  "serving_size",
  "nutriscore_grade",
  "nutriments",
].join(",");

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

/** EAN-8, UPC-A, EAN-13 and ITF-14 are the codes a scanner can produce. */
function normaliseBarcode(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const digits = value.trim();
  if (!/^\d{8}$|^\d{12,14}$/.test(digits)) return null;
  return digits;
}

function firstNonEmpty(...values: unknown[]): string | null {
  for (const value of values) {
    if (typeof value === "string" && value.trim() !== "") return value.trim();
  }
  return null;
}

/** Open Food Facts returns numbers as strings often enough to matter. */
function toNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) return null;
  // Guard against the occasional absurd entry; the column is numeric(7,2).
  return parsed > 99999 ? null : Math.round(parsed * 100) / 100;
}

function toNutriscore(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const grade = value.trim().toLowerCase();
  return ["a", "b", "c", "d", "e"].includes(grade) ? grade : null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

  if (!supabaseUrl || !supabaseAnonKey) {
    return jsonResponse(
      { error: "Supabase auth environment is not configured" },
      500,
    );
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  // Runs as the caller: this is user-facing, so it re-checks the JWT itself.
  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: authHeader } },
  });

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser();

  if (authError || !user) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  let barcode: string | null = null;
  try {
    const body = await req.json();
    barcode = normaliseBarcode(body?.ean ?? body?.barcode);
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  if (!barcode) {
    return jsonResponse(
      { error: "A numeric barcode of 8, 12, 13 or 14 digits is required" },
      400,
    );
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(
      `${OFF_ENDPOINT}/${barcode}.json?fields=${OFF_FIELDS}`,
      {
        headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
        signal: controller.signal,
      },
    );

    // Open Food Facts answers 404 for codes it does not know.
    if (response.status === 404) {
      return jsonResponse({ found: false, ean: barcode });
    }

    if (!response.ok) {
      return jsonResponse(
        { error: `Open Food Facts request failed with HTTP ${response.status}` },
        502,
      );
    }

    const payload = await response.json();

    if (payload?.status !== 1 || !payload?.product) {
      return jsonResponse({ found: false, ean: barcode });
    }

    const p = payload.product;
    const n = p.nutriments ?? {};

    const name = firstNonEmpty(
      p.product_name_de,
      p.product_name,
      p.product_name_en,
    );

    // A product with no name is not usable as a suggestion.
    if (!name) {
      return jsonResponse({ found: false, ean: barcode });
    }

    return jsonResponse({
      found: true,
      ean: barcode,
      product: {
        name,
        brand: firstNonEmpty(p.brands)?.split(",")[0]?.trim() ?? null,
        quantity: firstNonEmpty(p.quantity),
        image_url: firstNonEmpty(p.image_front_url, p.image_url),
        serving_size: firstNonEmpty(p.serving_size),
        nutriscore: toNutriscore(p.nutriscore_grade),
        energy_kcal_100g: toNumber(
          n["energy-kcal_100g"] ?? n["energy-kcal"] ?? null,
        ),
        fat_100g: toNumber(n["fat_100g"]),
        saturated_fat_100g: toNumber(n["saturated-fat_100g"]),
        carbohydrates_100g: toNumber(n["carbohydrates_100g"]),
        sugars_100g: toNumber(n["sugars_100g"]),
        protein_100g: toNumber(n["proteins_100g"]),
        salt_100g: toNumber(n["salt_100g"]),
        source: "openfoodfacts",
      },
    });
  } catch (error) {
    const aborted = error instanceof Error && error.name === "AbortError";
    const detail = error instanceof Error ? error.message : String(error);

    // A DNS or connect failure here is the runtime's network, not Open Food
    // Facts — locally that usually means the Edge Runtime container cannot
    // resolve names (a VPN, or Docker restarted without usable DNS). Say so,
    // because "name resolution failed" on its own sends people looking in the
    // wrong place.
    const networkFailure = /name resolution|dns|error sending request|connect/i
      .test(detail);

    return jsonResponse(
      {
        error: aborted
          ? "Open Food Facts did not respond in time"
          : networkFailure
            ? `Could not reach Open Food Facts from the Edge Function runtime (${detail}). Check that the runtime has working DNS and outbound network access.`
            : `Lookup failed: ${detail}`,
      },
      aborted ? 504 : 502,
    );
  } finally {
    clearTimeout(timeout);
  }
});

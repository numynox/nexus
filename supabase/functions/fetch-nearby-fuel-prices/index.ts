import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const FUEL_TYPE_MAP: Record<string, string> = {
  E5: "e5",
  E10: "e10",
  Diesel: "diesel",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

function parseBoundedNumber(
  value: unknown,
  min: number,
  max: number,
): number | null {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return null;
  if (parsed < min || parsed > max) return null;
  return parsed;
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
  const fuelPriceApiKey = Deno.env.get("FUEL_PRICE_API_KEY") ?? "";

  if (!supabaseUrl || !supabaseAnonKey) {
    return jsonResponse(
      { error: "Supabase auth environment is not configured" },
      500,
    );
  }

  if (!fuelPriceApiKey) {
    return jsonResponse({ error: "FUEL_PRICE_API_KEY is not configured" }, 500);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  const supabase = createClient(supabaseUrl, supabaseAnonKey, {
    global: {
      headers: {
        Authorization: authHeader,
      },
    },
  });

  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser();

  if (authError || !user) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  try {
    const body = await req.json();
    const lat = parseBoundedNumber(body?.lat, -90, 90);
    const lng = parseBoundedNumber(body?.lng, -180, 180);
    const radiusKm = parseBoundedNumber(body?.radiusKm, 0.1, 25) ?? 3;
    const requestedFuelType =
      typeof body?.fuelType === "string" ? body.fuelType : "";
    const tankerkoenigFuelType = FUEL_TYPE_MAP[requestedFuelType];

    if (lat === null || lng === null) {
      return jsonResponse(
        { error: "Latitude and longitude are required" },
        400,
      );
    }

    if (!tankerkoenigFuelType) {
      return jsonResponse({ error: "Unsupported fuel type" }, 400);
    }

    const params = new URLSearchParams({
      lat: String(lat),
      lng: String(lng),
      rad: String(radiusKm),
      sort: "price",
      type: tankerkoenigFuelType,
      apikey: fuelPriceApiKey,
    });

    const response = await fetch(
      `https://creativecommons.tankerkoenig.de/json/list.php?${params.toString()}`,
    );

    if (!response.ok) {
      return jsonResponse(
        {
          error: `Tankerkoenig list request failed with HTTP ${response.status}`,
        },
        502,
      );
    }

    const payload = await response.json();
    if (!payload?.ok || !Array.isArray(payload?.stations)) {
      return jsonResponse(
        {
          error:
            payload?.message ||
            "Tankerkoenig list request returned invalid data",
        },
        502,
      );
    }

    const stations = payload.stations
      .filter((station: any) => station && typeof station === "object")
      .map((station: any) => ({
        id: String(station.id || ""),
        name: typeof station.name === "string" ? station.name : "",
        brand: typeof station.brand === "string" ? station.brand : "",
        street: typeof station.street === "string" ? station.street : "",
        house_number:
          typeof station.houseNumber === "string" ||
          typeof station.houseNumber === "number"
            ? String(station.houseNumber)
            : "",
        place: typeof station.place === "string" ? station.place : "",
        lat: Number(station.lat),
        lng: Number(station.lng),
        currentPrice: Number.isFinite(Number(station.price))
          ? Number(station.price)
          : undefined,
        discount: 0,
        whole_day: false,
        opening_times: undefined,
        distanceKm: Number.isFinite(Number(station.dist))
          ? Number(station.dist)
          : undefined,
        isOpen: Boolean(station.isOpen),
      }))
      .sort((a: any, b: any) => {
        const aPrice =
          typeof a.currentPrice === "number"
            ? a.currentPrice
            : Number.POSITIVE_INFINITY;
        const bPrice =
          typeof b.currentPrice === "number"
            ? b.currentPrice
            : Number.POSITIVE_INFINITY;
        if (aPrice !== bPrice) return aPrice - bPrice;
        return String(a.brand || a.name || "").localeCompare(
          String(b.brand || b.name || ""),
        );
      });

    return jsonResponse({
      ok: true,
      fuelType: requestedFuelType,
      radiusKm,
      stations,
    });
  } catch (error) {
    console.error(error);
    return jsonResponse(
      { error: error instanceof Error ? error.message : "Unknown error" },
      500,
    );
  }
});

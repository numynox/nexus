import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req) => {
  const authHeader = req.headers.get("Authorization") || "";
  const invokeSecret = req.headers.get("x-invoke-secret");
  const FUEL_PRICE_INVOKE_SECRET = Deno.env.get("FUEL_PRICE_INVOKE_SECRET");
  const FUEL_PRICE_API_KEY = Deno.env.get("FUEL_PRICE_API_KEY");

  if (!FUEL_PRICE_INVOKE_SECRET) {
    return new Response(
      JSON.stringify({ error: "FUEL_PRICE_INVOKE_SECRET is not configured" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  if (!FUEL_PRICE_API_KEY) {
    return new Response(
      JSON.stringify({ error: "FUEL_PRICE_API_KEY is not configured" }),
      {
        status: 500,
        headers: { "Content-Type": "application/json" },
      },
    );
  }

  const bearerToken = authHeader.startsWith("Bearer ")
    ? authHeader.slice("Bearer ".length).trim()
    : "";
  const hasValidInvokeSecret = invokeSecret === FUEL_PRICE_INVOKE_SECRET;
  const hasValidBearerToken = bearerToken === FUEL_PRICE_INVOKE_SECRET;

  // 1. Authentication check
  if (!hasValidInvokeSecret && !hasValidBearerToken) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL") ?? "",
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  );

  try {
    let stationDetailUpdatedId: string | null = null;

    // 2. Check for station detail refresh (limit 1 per call, at most once per week)
    const oneWeekAgo = new Date(
      Date.now() - 7 * 24 * 60 * 60 * 1000,
    ).toISOString();
    const { data: stationsToUpdate, error: detailFetchError } = await supabase
      .from("fuel_stations")
      .select("*")
      .or(`checked_at.lt.${oneWeekAgo},checked_at.is.null`)
      .limit(1);

    if (detailFetchError) throw detailFetchError;

    if (stationsToUpdate && stationsToUpdate.length > 0) {
      const station = stationsToUpdate[0];
      const checkedAt = new Date().toISOString();

      const { error: stationCheckMarkError } = await supabase
        .from("fuel_stations")
        .update({ checked_at: checkedAt })
        .eq("id", station.id);

      if (stationCheckMarkError) throw stationCheckMarkError;

      const detailRes = await fetch(
        `https://creativecommons.tankerkoenig.de/json/detail.php?id=${station.id}&apikey=${FUEL_PRICE_API_KEY}`,
      );
      if (!detailRes.ok) {
        throw new Error(
          `Tankerkonig detail request failed for station ${station.id} with HTTP ${detailRes.status}`,
        );
      }

      const detailData = await detailRes.json();
      if (!detailData.ok || !detailData.station) {
        throw new Error(
          `Tankerkonig detail API returned no station data for ${station.id}: ${detailData?.message ?? "unknown error"}`,
        );
      }

      const s = detailData.station;
      const { error: stationUpdateError } = await supabase
        .from("fuel_stations")
        .update({
          name: s.name,
          brand: s.brand,
          street: s.street,
          place: s.place,
          lat: s.lat,
          lng: s.lng,
          house_number: s.houseNumber,
          post_code: s.postCode?.toString(),
          opening_times: s.openingTimes,
          overrides: s.overrides,
          whole_day: s.wholeDay,
          checked_at: checkedAt,
          updated_at: new Date().toISOString(),
        })
        .eq("id", station.id);

      if (stationUpdateError) throw stationUpdateError;
      stationDetailUpdatedId = station.id;
    }

    // 3. Price Refresh logic (Batching by 10)
    const { data: allStations, error: stationsError } = await supabase
      .from("fuel_stations")
      .select("id");

    if (stationsError) throw stationsError;
    if (!allStations || allStations.length === 0) {
      return new Response(
        JSON.stringify({ message: "No stations configured", prices: {} }),
        {
          headers: { "Content-Type": "application/json" },
        },
      );
    }

    const stationIds = allStations.map((s) => s.id);
    const batches = [];
    for (let i = 0; i < stationIds.length; i += 10) {
      batches.push(stationIds.slice(i, i + 10));
    }

    const currentPrices: Record<string, any> = {};
    const priceRecords: any[] = [];
    const priceBatchErrors: string[] = [];

    for (let i = 0; i < batches.length; i++) {
      const batch = batches[i];
      const idsParam = batch.join(",");
      const priceRes = await fetch(
        `https://creativecommons.tankerkoenig.de/json/prices.php?ids=${idsParam}&apikey=${FUEL_PRICE_API_KEY}`,
      );

      if (!priceRes.ok) {
        priceBatchErrors.push(
          `batch ${i + 1}/${batches.length} failed with HTTP ${priceRes.status}`,
        );
        continue;
      }

      const priceData = await priceRes.json();

      if (!priceData.ok || !priceData.prices) {
        priceBatchErrors.push(
          `batch ${i + 1}/${batches.length} returned API error: ${priceData?.message ?? "unknown error"}`,
        );
        continue;
      }

      for (const [id, data] of Object.entries(priceData.prices)) {
        if ((data as any).status === "open") {
          currentPrices[id] = data;
          const p = data as any;
          if (p.e5 !== false)
            priceRecords.push({
              station_id: id,
              fuel_type: "E5",
              price: p.e5,
            });
          if (p.e10 !== false)
            priceRecords.push({
              station_id: id,
              fuel_type: "E10",
              price: p.e10,
            });
          if (p.diesel !== false)
            priceRecords.push({
              station_id: id,
              fuel_type: "Diesel",
              price: p.diesel,
            });
        }
      }
    }

    if (priceBatchErrors.length > 0) {
      return new Response(
        JSON.stringify({
          error: "Price refresh failed for one or more batches",
          details: priceBatchErrors,
          stationCount: allStations.length,
          batchCount: batches.length,
        }),
        {
          status: 502,
          headers: { "Content-Type": "application/json" },
        },
      );
    }

    // 4. Store prices in DB
    if (priceRecords.length > 0) {
      const { error: insertError } = await supabase
        .from("fuel_prices")
        .insert(priceRecords);

      if (insertError) {
        const isFuelPricesPkConflict =
          typeof insertError.message === "string" &&
          insertError.message.includes("fuel_prices_pkey");

        if (isFuelPricesPkConflict) {
          throw new Error(
            "fuel_prices id sequence is out of sync. Reset it with: select setval(pg_get_serial_sequence('public.fuel_prices','id'), coalesce((select max(id) from public.fuel_prices), 0) + 1, false);",
          );
        }

        throw insertError;
      }
    }

    return new Response(
      JSON.stringify({
        ok: true,
        prices: currentPrices,
        diagnostics: {
          stationCount: allStations.length,
          batchCount: batches.length,
          openStationCount: Object.keys(currentPrices).length,
          insertedPriceRowCount: priceRecords.length,
          stationDetailUpdatedId,
        },
      }),
      {
        headers: { "Content-Type": "application/json" },
      },
    );
  } catch (err) {
    console.error(err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});

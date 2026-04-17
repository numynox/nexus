#!/usr/bin/env node
/**
 * Seed script for local fuel_prices development data.
 *
 * Deletes all existing fuel_prices and re-generates historical entries for every
 * station in fuel_stations, going from now back in time.
 *
 * Usage:
 *   node --env-file=.env scripts/seed-fuel-prices.mjs [options]
 *   npm run db:seed:fuel-prices -- [options]
 *
 * Options:
 *   --avg <price>      Base average price in EUR (default: 1.80)
 *   --days <n>         How many days back to generate (default: 3)
 *   --interval <min>   Interval between data points in minutes (default: 10)
 */

import { createClient } from "@supabase/supabase-js";
import { parseArgs } from "node:util";

// ---------------------------------------------------------------------------
// CLI arguments
// ---------------------------------------------------------------------------
const { values } = parseArgs({
  args: process.argv.slice(2),
  options: {
    avg: { type: "string", default: "1.80" },
    days: { type: "string", default: "30" },
    interval: { type: "string", default: "10" },
  },
  strict: true,
});

const avgPrice = parseFloat(values.avg);
const days = parseInt(values.days, 10);
const intervalMin = parseInt(values.interval, 10);

if (isNaN(avgPrice) || isNaN(days) || isNaN(intervalMin)) {
  console.error("Error: --avg, --days and --interval must be valid numbers.");
  process.exit(1);
}

// ---------------------------------------------------------------------------
// Supabase client (service role bypasses RLS)
// ---------------------------------------------------------------------------
const supabaseUrl = process.env.PUBLIC_SUPABASE_URL ?? "http://127.0.0.1:54321";
const serviceKey = process.env.SUPABASE_SERVICE_KEY;

if (!serviceKey) {
  console.error(
    "Error: SUPABASE_SERVICE_KEY is not set.\n" +
      'Add it to your .env file. You can get it from `npx supabase status` (the "Secret" key).',
  );
  process.exit(1);
}

const supabase = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false },
});

// ---------------------------------------------------------------------------
// Price generation helpers
// ---------------------------------------------------------------------------

// Typical offsets between fuel types (relative to E5 base price)
const FUEL_OFFSETS = {
  E5: 0.0,
  E10: -0.02,
  Diesel: -0.18,
};
const FUEL_TYPES = Object.keys(FUEL_OFFSETS);

/** Pseudo-gaussian via sum of uniforms (Box-Muller-lite, good enough for test data) */
function randNormal(mean, stddev) {
  let u = 0;
  for (let i = 0; i < 6; i++) u += Math.random();
  return mean + (u / 3 - 1) * stddev;
}

function roundPrice(p) {
  return Math.round(p * 1000) / 1000;
}

/**
 * Pre-compute a price curve for one station+fuel_type combo.
 *
 * Prices change on average every 60 minutes, but the actual change point is
 * randomised per station (uniform jitter of ±30 min around each hour mark).
 * Between change points the price is constant. At each change point the price
 * does a small random step (±1.5% of the running value).
 *
 * Returns a function: (stepIndex) => price
 */
function buildPriceCurve(basePrice, totalSteps, intervalMin) {
  const totalMinutes = totalSteps * intervalMin;
  const avgChangePeriodMin = 60;

  // Build sorted list of change-point minutes (random offset ±30 min per hour)
  const changeMinutes = [];
  for (let t = avgChangePeriodMin; t < totalMinutes; t += avgChangePeriodMin) {
    changeMinutes.push(t + (Math.random() - 0.5) * avgChangePeriodMin);
  }
  changeMinutes.sort((a, b) => a - b);

  // Translate to step indices
  const changeSteps = new Set(
    changeMinutes.map((m) => Math.round(m / intervalMin)),
  );

  // Walk forward in time building price array (time flows oldest→newest)
  const prices = new Array(totalSteps);
  let current = basePrice;
  for (let step = 0; step < totalSteps; step++) {
    if (changeSteps.has(step)) {
      current = roundPrice(Math.max(0.5, randNormal(current, current * 0.015)));
    }
    prices[step] = current;
  }
  return (step) => prices[step];
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------
async function main() {
  const totalMinutes = days * 24 * 60;
  const stepCount = Math.ceil(totalMinutes / intervalMin);

  console.log(
    `Settings: avg=${avgPrice} EUR, days=${days}, interval=${intervalMin} min`,
  );
  console.log(`→ ${stepCount} time steps to generate`);

  // 1. Fetch all stations
  const { data: stations, error: stationsErr } = await supabase
    .from("fuel_stations")
    .select("id");

  if (stationsErr) {
    console.error("Failed to fetch fuel_stations:", stationsErr.message);
    process.exit(1);
  }
  if (!stations?.length) {
    console.error(
      "No fuel stations found. Seed the fuel_stations table first.",
    );
    process.exit(1);
  }
  console.log(`Found ${stations.length} station(s).`);

  // 2. Delete all existing fuel_prices
  const { error: deleteErr } = await supabase
    .from("fuel_prices")
    .delete()
    .not("id", "is", null);

  if (deleteErr) {
    console.error("Failed to delete fuel_prices:", deleteErr.message);
    process.exit(1);
  }
  console.log("Cleared existing fuel_prices.");

  // 3. Generate rows
  //    Each station gets a fixed per-station price offset (±4%) so different
  //    stations have believably different price levels across the entire period.
  //    Within each station+fuel combination a price curve is pre-computed:
  //    prices stay flat and change on average every hour at a random moment.
  const stationOffset = Object.fromEntries(
    stations.map((s) => [s.id, randNormal(0, avgPrice * 0.04)]),
  );

  // Pre-build price curves keyed by "stationId:fuelType"
  const curves = {};
  for (const station of stations) {
    for (const fuelType of FUEL_TYPES) {
      const base =
        avgPrice + FUEL_OFFSETS[fuelType] + stationOffset[station.id];
      curves[`${station.id}:${fuelType}`] = buildPriceCurve(
        base,
        stepCount,
        intervalMin,
      );
    }
  }

  const now = new Date();
  const rows = [];

  // step 0 = now, step N = furthest in the past
  for (let step = 0; step < stepCount; step++) {
    const checkedAt = new Date(now.getTime() - step * intervalMin * 60_000);

    for (const station of stations) {
      for (const fuelType of FUEL_TYPES) {
        rows.push({
          station_id: station.id,
          fuel_type: fuelType,
          price: curves[`${station.id}:${fuelType}`](step),
          checked_at: checkedAt.toISOString(),
        });
      }
    }
  }

  const total = rows.length;
  console.log(
    `Inserting ${total} rows ` +
      `(${stepCount} steps × ${stations.length} stations × ${FUEL_TYPES.length} types)...`,
  );

  // 4. Insert in batches to stay well within request-size limits
  const BATCH = 500;
  for (let i = 0; i < total; i += BATCH) {
    const { error: insertErr } = await supabase
      .from("fuel_prices")
      .insert(rows.slice(i, i + BATCH));

    if (insertErr) {
      console.error(
        `\nFailed to insert batch at offset ${i}:`,
        insertErr.message,
      );
      process.exit(1);
    }

    process.stdout.write(`\r  ${Math.min(i + BATCH, total)} / ${total}`);
  }

  console.log(`\nDone. Seeded ${total} fuel price entries.`);
}

main();

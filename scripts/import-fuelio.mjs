#!/usr/bin/env node

import { createClient } from "@supabase/supabase-js";
import { readFile } from "node:fs/promises";
import { basename } from "node:path";
import { parseArgs } from "node:util";

function parseCsvLine(line) {
  const out = [];
  let current = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];

    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
      continue;
    }

    if (ch === "," && !inQuotes) {
      out.push(current);
      current = "";
      continue;
    }

    current += ch;
  }

  out.push(current);
  return out.map((value) => value.trim());
}

function normalizePlate(value) {
  return String(value || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
}

function normalizeText(value) {
  return String(value || "")
    .trim()
    .toLowerCase();
}

function parseNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const normalized = String(value).replace(",", ".").trim();
  if (!normalized) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function parseIntOrNull(value) {
  const parsed = parseNumber(value);
  if (parsed === null) return null;
  return Math.round(parsed);
}

function toIsoDateTime(value) {
  const text = String(value || "").trim();
  if (!text) return null;

  const normalized = text.includes(" ")
    ? text.replace(" ", "T")
    : `${text}T00:00`;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return null;
  return date.toISOString();
}

function expenseDedupKey(isoDateTime) {
  if (!isoDateTime) return null;
  const ms = Date.parse(String(isoDateTime));
  if (Number.isNaN(ms)) return null;
  // Fuelio costs are minute-level, so minute precision is enough for dedup.
  return Math.floor(ms / 60000);
}

function parseFuelioSections(content) {
  const lines = content.split(/\r?\n/);
  const sections = new Map();

  let currentSection = null;
  let headers = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;

    const cells = parseCsvLine(line);
    if (cells.length === 1 && cells[0].startsWith("##")) {
      currentSection = cells[0].replace(/^##\s*/, "");
      sections.set(currentSection, []);
      headers = null;
      continue;
    }

    if (!currentSection) continue;

    if (!headers) {
      headers = cells;
      continue;
    }

    const row = {};
    for (let i = 0; i < headers.length; i += 1) {
      row[headers[i]] = cells[i] ?? "";
    }
    sections.get(currentSection).push(row);
  }

  return sections;
}

async function main() {
  const { positionals, values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      "user-email": { type: "string" },
      "dry-run": { type: "boolean", default: false },
      "confirm-prod": { type: "boolean", default: false },
    },
    strict: true,
    allowPositionals: true,
  });

  const csvPath = positionals[0];
  if (!csvPath) {
    console.error(
      "Usage: npm run db:import:fuelio -- <path/to/file.csv> --user-email <email> [--dry-run] [--confirm-prod]",
    );
    process.exit(1);
  }

  const userEmail = values["user-email"]
    ? String(values["user-email"]).trim().toLowerCase()
    : "";
  if (!userEmail) {
    console.error(
      "Missing user target. Pass --user-email <email> via command line.",
    );
    process.exit(1);
  }

  const supabaseUrl =
    process.env.PUBLIC_SUPABASE_URL ?? "http://127.0.0.1:54321";
  const isLocalSupabase = /localhost|127\.0\.0\.1/i.test(supabaseUrl);
  const isDryRun = Boolean(values["dry-run"]);
  const hasProdConfirmation = Boolean(values["confirm-prod"]);
  if (!isLocalSupabase && !hasProdConfirmation) {
    console.error(
      "Refusing to run against non-local Supabase without --confirm-prod.",
    );
    process.exit(1);
  }

  const serviceKey = process.env.SUPABASE_SERVICE_KEY;
  if (!serviceKey) {
    console.error("Missing SUPABASE_SERVICE_KEY in environment.");
    process.exit(1);
  }

  const supabase = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false },
  });

  let page = 1;
  let userId = null;
  while (!userId) {
    const { data, error } = await supabase.auth.admin.listUsers({
      page,
      perPage: 200,
    });
    if (error) throw error;

    const users = data?.users || [];
    const match = users.find(
      (candidate) =>
        String(candidate.email || "")
          .trim()
          .toLowerCase() === userEmail,
    );
    if (match) {
      userId = match.id;
      break;
    }

    if (!users.length || users.length < 200) {
      break;
    }
    page += 1;
  }

  if (!userId) {
    console.error(`No auth user found for email: ${userEmail}`);
    process.exit(1);
  }

  console.log(`Resolved user email ${userEmail} to user id ${userId}`);

  const content = await readFile(csvPath, "utf8");
  const sections = parseFuelioSections(content);

  const vehicleRows = sections.get("Vehicle") || [];
  const logRows = sections.get("Log") || [];
  const costCategoryRows = sections.get("CostCategories") || [];
  const costRows = sections.get("Costs") || [];

  if (!vehicleRows.length) {
    console.error(`No Vehicle section found in ${basename(csvPath)}.`);
    process.exit(1);
  }

  const categoryMap = new Map();
  for (const row of costCategoryRows) {
    categoryMap.set(String(row.CostTypeID), row.Name || "Other");
  }

  const vehicle = vehicleRows[0];
  const vin = String(vehicle.VIN || "").trim();
  const plate = String(vehicle.Plate || "").trim();
  const name = String(vehicle.Name || "").trim();
  const make = String(vehicle.Make || "").trim() || null;
  const model = String(vehicle.Model || "").trim() || null;
  const year = parseIntOrNull(vehicle.Year);
  const tankCapacity = parseNumber(vehicle.Tank1Capacity) ?? 50;

  const { data: ownerCars, error: ownerCarsError } = await supabase
    .from("cars")
    .select("*")
    .eq("owner_id", userId);
  if (ownerCarsError) throw ownerCarsError;

  let matchedBy = "none";
  let car = null;

  if (vin) {
    car =
      (ownerCars || []).find(
        (candidate) => normalizeText(candidate.vin) === normalizeText(vin),
      ) || null;
    if (car) matchedBy = "vin";
  }

  if (!car && plate) {
    const targetPlate = normalizePlate(plate);
    car =
      (ownerCars || []).find(
        (candidate) => normalizePlate(candidate.plate) === targetPlate,
      ) || null;
    if (car) matchedBy = "plate";
  }

  if (!car && name) {
    car =
      (ownerCars || []).find(
        (candidate) => normalizeText(candidate.name) === normalizeText(name),
      ) || null;
    if (car) matchedBy = "name";
  }

  const stats = {
    carMatched: 0,
    carCreated: 0,
    refuelImported: 0,
    refuelSkippedDuplicateMileage: 0,
    refuelSkippedInvalid: 0,
    expenseImported: 0,
    expenseSkippedDuplicateDate: 0,
    expenseSkippedInvalid: 0,
  };

  if (!car) {
    if (isDryRun) {
      car = {
        id: "DRY_RUN_CAR_ID",
        name: name || "Imported Car",
        tank_capacity: tankCapacity,
      };
    } else {
      const { data: createdCar, error: createCarError } = await supabase
        .from("cars")
        .insert([
          {
            owner_id: userId,
            name: name || "Imported Car",
            vin: vin || null,
            plate: plate || null,
            make,
            model,
            year,
            tank_capacity: tankCapacity,
          },
        ])
        .select()
        .single();

      if (createCarError) throw createCarError;
      car = createdCar;
    }
    stats.carCreated += 1;
  } else {
    stats.carMatched += 1;
    console.log(`Matched existing car by ${matchedBy}: ${car.name}`);
  }

  const shouldLoadExistingEvents = !(isDryRun && car.id === "DRY_RUN_CAR_ID");
  let existingRefuels = [];
  let existingExpenses = [];

  if (shouldLoadExistingEvents) {
    const { data: existingRefuelsData, error: existingRefuelsError } =
      await supabase
        .from("refuel_events")
        .select("mileage")
        .eq("car_id", car.id);
    if (existingRefuelsError) throw existingRefuelsError;
    existingRefuels = existingRefuelsData || [];

    const { data: existingExpensesData, error: existingExpensesError } =
      await supabase
        .from("car_expenses")
        .select("expensed_at")
        .eq("car_id", car.id);
    if (existingExpensesError) throw existingExpensesError;
    existingExpenses = existingExpensesData || [];
  }

  const refuelMileageSet = new Set(
    existingRefuels.map((row) => Number(row.mileage)),
  );
  const expenseDateSet = new Set(
    existingExpenses
      .map((row) => expenseDedupKey(row.expensed_at))
      .filter((value) => value !== null),
  );

  for (const row of logRows) {
    const mileage = parseIntOrNull(row["Odo (km)"]);
    const liters = parseNumber(row["Fuel (litres)"]);
    const totalPriceRaw = parseNumber(row["Price (optional)"]);
    const pricePerLiterRaw = parseNumber(row.VolumePrice);
    const fueledAt = toIsoDateTime(row.Data);

    if (mileage === null || liters === null || liters <= 0 || !fueledAt) {
      stats.refuelSkippedInvalid += 1;
      continue;
    }

    if (refuelMileageSet.has(mileage)) {
      stats.refuelSkippedDuplicateMileage += 1;
      continue;
    }

    const totalPrice =
      totalPriceRaw !== null
        ? totalPriceRaw
        : pricePerLiterRaw !== null
          ? pricePerLiterRaw * liters
          : null;
    if (totalPrice === null) {
      stats.refuelSkippedInvalid += 1;
      continue;
    }

    const isFullRefuel = String(row.Full || "") === "1";
    const missedPreviousRefuel = String(row.Missed || "") === "1";
    const tankCalc = parseNumber(row.TankCalc);
    const fuelLevelAfter = isFullRefuel
      ? Number(car.tank_capacity || tankCapacity)
      : tankCalc !== null
        ? Math.max(
            0,
            Math.min(Number(car.tank_capacity || tankCapacity), tankCalc),
          )
        : Math.min(Number(car.tank_capacity || tankCapacity), liters);

    const pricePerLiter =
      pricePerLiterRaw !== null ? pricePerLiterRaw : totalPrice / liters;

    if (!isDryRun) {
      const { error } = await supabase.from("refuel_events").insert([
        {
          car_id: car.id,
          user_id: userId,
          mileage,
          liters,
          total_price: Number(totalPrice.toFixed(2)),
          fuel_level_after: Number(fuelLevelAfter.toFixed(2)),
          is_full_refuel: isFullRefuel,
          missed_previous_refuel: missedPreviousRefuel,
          price_per_liter_calculated: Number(pricePerLiter.toFixed(3)),
          fueled_at: fueledAt,
          fuel_station_id: null,
        },
      ]);

      if (error) throw error;
    }
    refuelMileageSet.add(mileage);
    stats.refuelImported += 1;
  }

  for (const row of costRows) {
    const mileage = parseIntOrNull(row.Odo);
    const expensedAt = toIsoDateTime(row.Date);
    const title = String(row.CostTitle || "").trim();
    const amount = parseNumber(row.Cost);

    if (
      mileage === null ||
      !expensedAt ||
      !title ||
      amount === null ||
      amount <= 0
    ) {
      stats.expenseSkippedInvalid += 1;
      continue;
    }

    const expenseKey = expenseDedupKey(expensedAt);
    if (expenseKey === null) {
      stats.expenseSkippedInvalid += 1;
      continue;
    }

    if (expenseDateSet.has(expenseKey)) {
      stats.expenseSkippedDuplicateDate += 1;
      continue;
    }

    const category = categoryMap.get(String(row.CostTypeID || "")) || "Other";

    if (!isDryRun) {
      const { error } = await supabase.from("car_expenses").insert([
        {
          car_id: car.id,
          user_id: userId,
          expensed_at: expensedAt,
          title,
          amount: Number(amount.toFixed(2)),
          mileage,
          category,
          notes: String(row.Notes || "").trim() || null,
        },
      ]);

      if (error) throw error;
    }
    expenseDateSet.add(expenseKey);
    stats.expenseImported += 1;
  }

  console.log("\nFuelio import summary");
  console.log("---------------------");
  console.log(`mode: ${isDryRun ? "DRY RUN" : "WRITE"}`);
  console.log(`supabase url: ${supabaseUrl}`);
  console.log(`target user id: ${userId}`);
  console.log(`CSV: ${csvPath}`);
  console.log(`Car: ${car.name} (${car.id})`);
  console.log(`car matched: ${stats.carMatched}`);
  console.log(`car created: ${stats.carCreated}`);
  console.log(`refuels imported: ${stats.refuelImported}`);
  console.log(
    `refuels skipped (duplicate mileage): ${stats.refuelSkippedDuplicateMileage}`,
  );
  console.log(`refuels skipped (invalid): ${stats.refuelSkippedInvalid}`);
  console.log(`expenses imported: ${stats.expenseImported}`);
  console.log(
    `expenses skipped (duplicate date): ${stats.expenseSkippedDuplicateDate}`,
  );
  console.log(`expenses skipped (invalid): ${stats.expenseSkippedInvalid}`);
}

main().catch((error) => {
  console.error("Import failed:", error?.message || error);
  process.exit(1);
});

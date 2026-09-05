#!/usr/bin/env node
/**
 * Fill existing Annona products from Open Food Facts.
 *
 * The scanner has looked barcodes up since #19, but products added before that
 * carry only what was typed by hand. This walks the catalogue and fills the
 * gaps — nutrition above all, plus an image where there is none.
 *
 * Two rules shape the whole script:
 *
 *   1. What you typed wins. name, brand and quantity are never overwritten
 *      silently: they are filled when empty, and in --interactive mode a
 *      difference is put to you rather than decided for you. Open Food Facts is
 *      crowd-sourced and your own catalogue is often the better record.
 *
 *   2. Every product is stamped, found or not. off_checked_at makes the run
 *      resumable: stop it whenever, run it again, and it picks up where it left
 *      off instead of re-querying barcodes the database has never heard of.
 *
 * Open Food Facts allows 15 product requests per minute per IP, so the default
 * pace is one every 5 seconds. Run it from your own machine: the limit is per
 * IP, and Supabase's is shared.
 *
 * Usage:
 *   npm run db:backfill:off -- --dry-run            # local, shows the diff
 *   npm run db:backfill:off -- --limit 25 --interactive
 *   npm run db:backfill:off:prod -- --limit 25      # production, needs --confirm-prod
 *
 * Options:
 *   --limit <n>      products per run           (default 25)
 *   --delay <ms>     pause between requests     (default 5000)
 *   --dry-run        report, write nothing
 *   --interactive    ask when a value differs from yours
 *   --refresh        re-check products already stamped
 *   --confirm-prod   required when the target is not localhost
 */

import { createClient } from "@supabase/supabase-js";
import { createInterface } from "node:readline/promises";
import { parseArgs } from "node:util";
import { stdin, stdout } from "node:process";

const OFF_ENDPOINT = "https://world.openfoodfacts.org/api/v2/product";

/**
 * Open Food Facts asks for AppName/Version (ContactEmail). Set OFF_CONTACT in
 * your env to be reachable; the repository stands in until you do, rather than
 * committing anyone's address.
 */
const OFF_CONTACT = process.env.OFF_CONTACT || "https://github.com/numynox/nexus";
const USER_AGENT = `Nexus-Annona/1.0 (${OFF_CONTACT})`;

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

/** Fields where a difference is a question, not an answer. */
const CURATED_FIELDS = ["name", "brand", "quantity"];

/** Filled when empty, never questioned. */
const FILL_ONLY_FIELDS = ["image_url", "serving_size", "nutriscore"];

const NUTRITION_FIELDS = [
  "energy_kcal_100g",
  "fat_100g",
  "saturated_fat_100g",
  "carbohydrates_100g",
  "sugars_100g",
  "protein_100g",
  "salt_100g",
];

const isEmpty = (value) =>
  value === null || value === undefined || String(value).trim() === "";

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function firstNonEmpty(...values) {
  for (const value of values) {
    if (typeof value === "string" && value.trim() !== "") return value.trim();
  }
  return null;
}

function toNumber(value) {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 99999) return null;
  return Math.round(parsed * 100) / 100;
}

function toNutriscore(value) {
  if (typeof value !== "string") return null;
  const grade = value.trim().toLowerCase();
  return ["a", "b", "c", "d", "e"].includes(grade) ? grade : null;
}

/** The Open Food Facts payload, reduced to the columns Annona stores. */
export function mapRemoteProduct(payload) {
  if (payload?.status !== 1 || !payload?.product) return null;

  const p = payload.product;
  const n = p.nutriments ?? {};

  const name = firstNonEmpty(
    p.product_name_de,
    p.product_name,
    p.product_name_en,
  );
  if (!name) return null;

  return {
    name,
    brand: firstNonEmpty(p.brands)?.split(",")[0]?.trim() ?? null,
    quantity: firstNonEmpty(p.quantity),
    image_url: firstNonEmpty(p.image_front_url, p.image_url),
    serving_size: firstNonEmpty(p.serving_size),
    nutriscore: toNutriscore(p.nutriscore_grade),
    energy_kcal_100g: toNumber(n["energy-kcal_100g"] ?? n["energy-kcal"]),
    fat_100g: toNumber(n["fat_100g"]),
    saturated_fat_100g: toNumber(n["saturated-fat_100g"]),
    carbohydrates_100g: toNumber(n["carbohydrates_100g"]),
    sugars_100g: toNumber(n["sugars_100g"]),
    protein_100g: toNumber(n["protein_100g"] ?? n["proteins_100g"]),
    salt_100g: toNumber(n["salt_100g"]),
  };
}

/**
 * Decide what to write, given what is stored and what came back.
 *
 * Pure on purpose: the interesting rules live here and can be exercised without
 * a network or a database.
 *
 * @returns {{ updates: Record<string, unknown>, conflicts: Array<{field: string, mine: unknown, theirs: unknown}> }}
 */
export function planUpdate(existing, remote) {
  const updates = {};
  const conflicts = [];

  for (const field of CURATED_FIELDS) {
    const theirs = remote[field];
    if (isEmpty(theirs)) continue;

    if (isEmpty(existing[field])) {
      updates[field] = theirs;
    } else if (String(existing[field]).trim() !== String(theirs).trim()) {
      conflicts.push({ field, mine: existing[field], theirs });
    }
  }

  for (const field of FILL_ONLY_FIELDS) {
    if (!isEmpty(remote[field]) && isEmpty(existing[field])) {
      updates[field] = remote[field];
    }
  }

  const nutrition = NUTRITION_FIELDS.filter(
    (field) => remote[field] !== null && isEmpty(existing[field]),
  );

  for (const field of nutrition) updates[field] = remote[field];

  if (nutrition.length > 0) {
    updates.nutrition_source = "openfoodfacts";
    updates.nutrition_updated_at = new Date().toISOString();
  }

  return { updates, conflicts };
}

async function fetchProduct(barcode) {
  const response = await fetch(`${OFF_ENDPOINT}/${barcode}.json?fields=${OFF_FIELDS}`, {
    headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
  });

  if (response.status === 404) return { status: "not-found" };

  if (response.status === 429) {
    const retryAfter = Number(response.headers.get("retry-after")) || 60;
    return { status: "rate-limited", retryAfter };
  }

  if (!response.ok) {
    return { status: "error", reason: `HTTP ${response.status}` };
  }

  const mapped = mapRemoteProduct(await response.json());
  return mapped ? { status: "found", product: mapped } : { status: "not-found" };
}

async function main() {
  const { values } = parseArgs({
    options: {
      limit: { type: "string", default: "25" },
      delay: { type: "string", default: "5000" },
      "dry-run": { type: "boolean", default: false },
      interactive: { type: "boolean", default: false },
      refresh: { type: "boolean", default: false },
      "confirm-prod": { type: "boolean", default: false },
    },
    strict: true,
  });

  const limit = Math.max(1, Number(values.limit) || 25);
  const delay = Math.max(0, Number(values.delay) || 0);
  const dryRun = values["dry-run"];
  const interactive = values.interactive;

  const supabaseUrl = process.env.PUBLIC_SUPABASE_URL ?? "";
  const serviceKey = process.env.SUPABASE_SERVICE_KEY;

  if (!supabaseUrl || !serviceKey) {
    console.error(
      "PUBLIC_SUPABASE_URL and SUPABASE_SERVICE_KEY must be set (see .env.example).",
    );
    process.exit(1);
  }

  const isLocal = /localhost|127\.0\.0\.1/i.test(supabaseUrl);
  if (!isLocal && !values["confirm-prod"]) {
    console.error(
      `Refusing to write to ${supabaseUrl} without --confirm-prod.\n` +
        "Use npm run db:backfill:off:prod, which passes it.",
    );
    process.exit(1);
  }

  console.log(`Target      : ${supabaseUrl}${isLocal ? " (local)" : "  ** PRODUCTION **"}`);
  console.log(`Pace        : one request every ${delay}ms (Open Food Facts allows 15/min)`);
  console.log(`Mode        : ${dryRun ? "dry run — nothing is written" : "writing"}${interactive ? ", interactive" : ""}`);

  const supabase = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false },
  });

  let query = supabase
    .from("annona_products")
    .select("*")
    .not("ean", "is", null)
    .order("id", { ascending: true })
    .limit(limit);

  if (!values.refresh) query = query.is("off_checked_at", null);

  const { data: products, error } = await query;

  if (error) {
    console.error(`Could not read products: ${error.message}`);
    process.exit(1);
  }

  if (!products || products.length === 0) {
    console.log("\nNothing left to look up.");
    return;
  }

  const { count: remaining } = await supabase
    .from("annona_products")
    .select("id", { count: "exact", head: true })
    .not("ean", "is", null)
    .is("off_checked_at", null);

  console.log(`Products    : ${products.length} this run, ${remaining ?? "?"} unchecked in total\n`);

  const rl = interactive ? createInterface({ input: stdin, output: stdout }) : null;
  /** Remembered "always" answers, per field. */
  const standingChoice = {};

  const summary = { updated: 0, unchanged: 0, notFound: 0, errors: 0, fields: 0 };

  for (const product of products) {
    const label = `${product.name}${product.brand ? ` (${product.brand})` : ""} · ${product.ean}`;

    let result;
    try {
      result = await fetchProduct(product.ean);
    } catch (cause) {
      console.log(`  ?  ${label}\n     network error: ${cause.message} — will retry next run`);
      summary.errors += 1;
      await sleep(delay);
      continue;
    }

    if (result.status === "rate-limited") {
      console.log(`\nRate limited by Open Food Facts. Waiting ${result.retryAfter}s once…`);
      await sleep(result.retryAfter * 1000);
      try {
        result = await fetchProduct(product.ean);
      } catch (cause) {
        console.log(`  ?  ${label}\n     network error after backoff: ${cause.message}`);
        summary.errors += 1;
        break;
      }
      if (result.status === "rate-limited") {
        console.log("Still rate limited — stopping. Re-run later; progress is saved.");
        break;
      }
    }

    if (result.status === "error") {
      console.log(`  ?  ${label}\n     ${result.reason} — will retry next run`);
      summary.errors += 1;
      await sleep(delay);
      continue;
    }

    if (result.status === "not-found") {
      console.log(`  –  ${label}\n     not in Open Food Facts`);
      summary.notFound += 1;
      if (!dryRun) {
        await supabase
          .from("annona_products")
          .update({ off_checked_at: new Date().toISOString() })
          .eq("id", product.id);
      }
      await sleep(delay);
      continue;
    }

    const { updates, conflicts } = planUpdate(product, result.product);

    for (const conflict of conflicts) {
      const standing = standingChoice[conflict.field];
      if (standing === "keep") continue;
      if (standing === "theirs") {
        updates[conflict.field] = conflict.theirs;
        continue;
      }

      if (!interactive) continue; // default: what you typed wins

      console.log(`\n  ${label}`);
      console.log(`  ${conflict.field}`);
      console.log(`    [k] keep yours  : ${conflict.mine}`);
      console.log(`    [o] use theirs  : ${conflict.theirs}`);
      console.log(`    [e] type a value`);
      console.log(`    [K] keep yours for every remaining conflict`);
      console.log(`    [O] use theirs for every remaining conflict`);

      const answer = (await rl.question("  choice [k]: ")).trim() || "k";

      if (answer === "K") standingChoice[conflict.field] = "keep";
      else if (answer === "O") {
        standingChoice[conflict.field] = "theirs";
        updates[conflict.field] = conflict.theirs;
      } else if (answer === "o") updates[conflict.field] = conflict.theirs;
      else if (answer === "e") {
        const typed = (await rl.question("  value: ")).trim();
        if (typed !== "") updates[conflict.field] = typed;
      }
    }

    const changedFields = Object.keys(updates).filter(
      (field) => field !== "nutrition_source" && field !== "nutrition_updated_at",
    );

    if (changedFields.length === 0) {
      console.log(`  =  ${label}\n     nothing to add`);
      summary.unchanged += 1;
    } else {
      console.log(`  +  ${label}\n     ${changedFields.join(", ")}`);
      summary.updated += 1;
      summary.fields += changedFields.length;
    }

    if (!dryRun) {
      const { error: updateError } = await supabase
        .from("annona_products")
        .update({ ...updates, off_checked_at: new Date().toISOString() })
        .eq("id", product.id);

      if (updateError) {
        console.log(`     write failed: ${updateError.message}`);
        summary.errors += 1;
      }
    }

    await sleep(delay);
  }

  rl?.close();

  console.log(
    `\n${dryRun ? "Would update" : "Updated"} ${summary.updated} product(s) ` +
      `(${summary.fields} field(s)), ${summary.unchanged} already complete, ` +
      `${summary.notFound} unknown to Open Food Facts, ${summary.errors} error(s).`,
  );

  if (!dryRun) console.log("Run again to continue; checked products are skipped.");
}

// Importable for testing without running the walk.
if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}

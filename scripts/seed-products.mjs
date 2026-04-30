#!/usr/bin/env node
/**
 * Seed script for Annona products.
 *
 * Inserts predefined products into annona_products (shared, no user association).
 * Skips products whose EAN already exists.
 *
 * Usage:
 *   node --env-file=.env scripts/seed-products.mjs
 *   npm run db:seed:products
 */

import { createClient } from "@supabase/supabase-js";

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
// Ensure categories exist
// ---------------------------------------------------------------------------
const CATEGORY_NAMES = [
  "Bakery",
  "Beverages",
  "Condiments & Dressings",
  "Cooking & Baking",
  "Dairy & Cheese",
  "Frozen Foods",
  "Meat",
  "Pasta",
  "Seafood",
  "Snacks",
  "Soups & Canned Goods",
  "Spices",
  "Other",
];

// Fetch existing categories
let { data: existingCats } = await supabase
  .from("annona_categories")
  .select("id, name");

if (!existingCats || existingCats.length === 0) {
  // Seed default categories
  const rows = CATEGORY_NAMES.map((name, i) => ({
    name,
    sort_order: i,
  }));
  const { error } = await supabase.from("annona_categories").insert(rows);
  if (error) {
    console.error("Error seeding categories:", error.message);
    process.exit(1);
  }
  const { data } = await supabase.from("annona_categories").select("id, name");
  existingCats = data;
}

const categoryMap = Object.fromEntries(existingCats.map((c) => [c.name, c.id]));

// ---------------------------------------------------------------------------
// Product data
// ---------------------------------------------------------------------------
const PRODUCTS = [
  {
    ean: "4260028480048",
    brand: null,
    name: "Chilly",
    quantity: "40g",
    category: "Condiments & Dressings",
  },
  {
    ean: "8076809581127",
    brand: "Barilla (al bronzo)",
    name: "Spaghetti",
    quantity: "400g",
    category: "Pasta",
  },
  {
    ean: "8076809519977",
    brand: "Barilla",
    name: "Orecchiette",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8076802085738",
    brand: "Barilla",
    name: "Penne No 73",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8076808150072",
    brand: "Barilla",
    name: "Spaghettoni N° 7",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8015936002617",
    brand: "Belmonte",
    name: "Pomodri secchi",
    quantity: "500g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8032998911886",
    brand: "Capricci di Franciacorta",
    name: "Peperoncino con tonno picante",
    quantity: "180g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8012666043262",
    brand: "Carrefour",
    name: "Minestrone",
    quantity: "500g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8012666043477",
    brand: "Carrefour",
    name: "Zuppa all'isolana",
    quantity: "500g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8012666043521",
    brand: "Carrefour",
    name: "Zuppa di legumi",
    quantity: "500g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8000965155816",
    brand: "Consilia",
    name: "Taralli",
    quantity: "250g",
    category: "Snacks",
  },
  {
    ean: "92063945",
    brand: "Cudduruni",
    name: "Brusiata Trapanese Lunga",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "92053014",
    brand: "Cudduruni",
    name: "Sugo alla norma",
    quantity: "300g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8001250120342",
    brand: "De Cecco",
    name: "Fusilli N° 34",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8001250120076",
    brand: "De Cecco",
    name: "Linguine N° 7",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8001250120410",
    brand: "De Cecco",
    name: "Penne Rigate N° 41",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8001250120120",
    brand: "De Cecco",
    name: "Spaghetti N° 12",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "80333531",
    brand: "Esselunga",
    name: "Olive verdi denocciolate",
    quantity: "350g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "80442684",
    brand: "Fabbri",
    name: "Amarena",
    quantity: "120g",
    category: "Cooking & Baking",
  },
  {
    ean: "8000139925214",
    brand: "Garofalo",
    name: "Farina W170",
    quantity: "1kg",
    category: "Cooking & Baking",
  },
  {
    ean: "6954769011095",
    brand: "Hein",
    name: "Sichuan Pepper",
    quantity: "30g",
    category: "Spices",
  },
  {
    ean: null,
    brand: "Kimbo",
    name: "Café macinato",
    quantity: "250g",
    category: "Beverages",
  },
  {
    ean: "8032774485839",
    brand: "La Cerignola",
    name: "Peperoncino ripieno con tonno",
    quantity: "550g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8004690052716",
    brand: "La Molisana",
    name: "Gnocchetti",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "80688839",
    brand: "La Trafilata",
    name: "Spaghetti Tricolori",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "7312080004025",
    brand: "Leksands Knäcke",
    name: "Brungräddat",
    quantity: "200g",
    category: "Bakery",
  },
  {
    ean: "7310350133918",
    brand: "Malaco",
    name: "Godt & Blandat",
    quantity: "180g",
    category: "Snacks",
  },
  {
    ean: "7310350106844",
    brand: "Malaco",
    name: "Gott & Blandat",
    quantity: "210g",
    category: "Snacks",
  },
  {
    ean: "8004009005259",
    brand: "Marzadro",
    name: "Pere piccole in liquore",
    quantity: "650g",
    category: "Snacks",
  },
  {
    ean: "8033373572197",
    brand: "Merano",
    name: "Olive Riviera Denocciolate",
    quantity: "500g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8027349600113",
    brand: "Merano",
    name: "Olive Taggiasche",
    quantity: "280g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "805571586002",
    brand: "Molino Signetti",
    name: "Farina per per pizza napoletana",
    quantity: "1000g",
    category: "Cooking & Baking",
  },
  {
    ean: "8076809580700",
    brand: "Mulino biancho",
    name: "Batticuori",
    quantity: "350g",
    category: "Bakery",
  },
  {
    ean: "8076809580670",
    brand: "Mulino Biancho",
    name: "Cuor di Mela",
    quantity: "300g",
    category: "Bakery",
  },
  {
    ean: "8005110001598",
    brand: "Mutti",
    name: "Pesto arrancione",
    quantity: "180g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "80042556",
    brand: "Mutti",
    name: "Polpa",
    quantity: "400g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "40081236",
    brand: "Oro",
    name: "Tomaten",
    quantity: "400g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "4008100168244",
    brand: "Oro",
    name: "Tomaten Basilikum",
    quantity: "400g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "7311070006209",
    brand: "Pågen",
    name: "Gifflar",
    quantity: "300g",
    category: "Bakery",
  },
  {
    ean: "8013355998702",
    brand: "Pavesi",
    name: "Gocciole",
    quantity: "400g",
    category: "Snacks",
  },
  {
    ean: "8000038122011",
    brand: "Praline",
    name: "Cri-Cri Gianduja",
    quantity: "150g",
    category: "Snacks",
  },
  {
    ean: "8008343200486",
    brand: "Rummo",
    name: "Fusilli N° 48",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8008343235792",
    brand: "Rummo",
    name: "Penne Rigate N° 66",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8008343235730",
    brand: "Rummo",
    name: "Spaghetti Grossi N° 5",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8000895005984",
    brand: "Sapori",
    name: "Cantuccini Toscani IGP",
    quantity: "300g",
    category: "Bakery",
  },
  {
    ean: "8013399162831",
    brand: "Sperlari",
    name: "Torroncini",
    quantity: "117g",
    category: "Snacks",
  },
  {
    ean: "8004190351326",
    brand: "Sperlari",
    name: "Torrone tenero alla mandorla",
    quantity: "250g",
    category: "Snacks",
  },
  {
    ean: "8012666504435",
    brand: "Terre d'Italia",
    name: "Cavatelli di Puglia",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8012666505586",
    brand: "Terre d'Italia",
    name: "Crema piccante di 'Nduja",
    quantity: "180g",
    category: "Meat",
  },
  {
    ean: "8012666506002",
    brand: "Terre d'Italia",
    name: "Gnocchetti",
    quantity: "500g",
    category: "Pasta",
  },
  {
    ean: "8012666508167",
    brand: "Terre d'Italia",
    name: "Pesto",
    quantity: "130g",
    category: "Soups & Canned Goods",
  },
  {
    ean: "8012666507214",
    brand: "Terre d'Italia",
    name: "Taralli tondi ai semi di finocchio",
    quantity: "400g",
    category: "Snacks",
  },
  {
    ean: "8012666507191",
    brand: "Terre d'Italia",
    name: "Taralli tondi al peperoncino",
    quantity: "400g",
    category: "Snacks",
  },
];

// ---------------------------------------------------------------------------
// Insert products (skip duplicates by EAN)
// ---------------------------------------------------------------------------
let inserted = 0;
let skipped = 0;

for (const p of PRODUCTS) {
  // Check if product already exists (by EAN if available, else by name+brand)
  if (p.ean) {
    const { data: existing } = await supabase
      .from("annona_products")
      .select("id")
      .eq("ean", p.ean)
      .limit(1)
      .maybeSingle();

    if (existing) {
      skipped++;
      continue;
    }
  } else {
    const query = supabase
      .from("annona_products")
      .select("id")
      .eq("name", p.name);
    if (p.brand) query.eq("brand", p.brand);
    const { data: existing } = await query.limit(1).maybeSingle();

    if (existing) {
      skipped++;
      continue;
    }
  }

  const categoryId = categoryMap[p.category] ?? null;

  const { error } = await supabase.from("annona_products").insert({
    name: p.name,
    brand: p.brand,
    ean: p.ean,
    quantity: p.quantity,
    category_id: categoryId,
    image_url: null,
  });

  if (error) {
    console.error(`  ✗ ${p.brand ?? ""} ${p.name}: ${error.message}`);
  } else {
    inserted++;
  }
}

console.log(
  `\nDone. Inserted: ${inserted}, Skipped (already exist): ${skipped}`,
);

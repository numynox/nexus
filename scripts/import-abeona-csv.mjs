#!/usr/bin/env node
// Imports past train trips into Abeona from a CSV file.
//
//   npm run db:import:abeona -- <file.csv> --user-email <email> [--write] [--geocode]
//   npm run db:import:abeona:prod -- <file.csv> --user-email <email> [--write]
//
// Columns (a header row, any order, comma or semicolon separated):
//   date            yyyy-mm-dd (or dd.mm.yyyy)
//   from, to        station names
//   departure       HH:MM, planned, Europe/Berlin
//   arrival         HH:MM, planned; before departure means the next day
//   actual_arrival  HH:MM, optional: the real arrival, for the delay
//   train           optional: "ICE 25", "RE 10" …
//   km              optional: the distance, when there are no coordinates
//
// One row is one trip with one ride. Nothing is written without --write; a
// non-local Supabase also needs --confirm-prod. The trips are saved as the
// user (an admin magic link is exchanged for a session; no mail is sent)
// through abeona_save_trip with source 'import', so they land in the user's
// household, final, never refreshed — exactly as the clients save them.
// A row whose origin and departure the household already has is skipped.
// --geocode looks each station up once at Transitous for coordinates and
// country, so the trip appears on the map and in the countries.

import { createClient } from "@supabase/supabase-js";
import { readFile } from "node:fs/promises";
import { parseArgs } from "node:util";

const ZONE = "Europe/Berlin";
const LONG_DISTANCE = new Set(["ICE", "ICD", "IC", "EC", "ECE", "RJ", "RJX", "NJ", "EN", "TGV", "EST", "FR", "FLX", "IR", "D", "WB"]);

function parseCsvLine(line, sep) {
  const out = [];
  let current = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
        i += 1;
      } else inQuotes = !inQuotes;
      continue;
    }
    if (ch === sep && !inQuotes) {
      out.push(current);
      current = "";
      continue;
    }
    current += ch;
  }
  out.push(current);
  return out.map((v) => v.trim());
}

export function parseCsv(text) {
  const lines = text.replace(/^﻿/, "").split(/\r?\n/).filter((l) => l.trim() !== "");
  if (lines.length === 0) return [];
  const sep = (lines[0].match(/;/g) ?? []).length > (lines[0].match(/,/g) ?? []).length ? ";" : ",";
  const header = parseCsvLine(lines[0], sep).map((h) => h.toLowerCase().replace(/\s+/g, "_"));
  return lines.slice(1).map((line, i) => {
    const cells = parseCsvLine(line, sep);
    const row = { line: i + 2 };
    header.forEach((h, j) => (row[h] = cells[j] ?? ""));
    return row;
  });
}

function normaliseDate(value) {
  const s = String(value ?? "").trim();
  let m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  let iso = null;
  if (m) iso = `${m[1]}-${m[2]}-${m[3]}`;
  m = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/.exec(s);
  if (m) iso = `${m[3]}-${m[2].padStart(2, "0")}-${m[1].padStart(2, "0")}`;
  // A real calendar day: 2026-02-30 and 2026-13-01 are not.
  const day = iso ? new Date(`${iso}T12:00:00Z`) : null;
  if (!day || Number.isNaN(day.getTime()) || day.toISOString().slice(0, 10) !== iso) return null;
  return iso;
}

function normaliseTime(value) {
  const m = /^(\d{1,2})[:.](\d{2})$/.exec(String(value ?? "").trim());
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) return null;
  return `${m[1].padStart(2, "0")}:${m[2]}`;
}

/** A Berlin wall-clock date and time as an ISO instant. */
export function berlinInstant(date, time, addDays = 0) {
  const [y, mo, d] = date.split("-").map(Number);
  const [h, mi] = time.split(":").map(Number);
  const guess = Date.UTC(y, mo - 1, d + addDays, h, mi);
  // The zone's offset at that moment, read back through Intl.
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: ZONE, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
    }).formatToParts(new Date(guess)).map((p) => [p.type, p.value]),
  );
  const asLocal = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute);
  return new Date(guess - (asLocal - guess)).toISOString();
}

function productOf(train) {
  const m = /^([A-Za-zÄÖÜäöü]{1,5})(?=\s|\d|$)/.exec(String(train ?? "").trim());
  return m ? m[1].toUpperCase() : null;
}

/** One CSV row as a trip for abeona_save_trip, or an error message. */
export function tripOf(row) {
  const date = normaliseDate(row.date);
  const dep = normaliseTime(row.departure);
  const arr = normaliseTime(row.arrival);
  const from = String(row.from ?? "").trim();
  const to = String(row.to ?? "").trim();
  if (!date) return { error: `line ${row.line}: date "${row.date}" is not yyyy-mm-dd or dd.mm.yyyy` };
  if (!from || !to) return { error: `line ${row.line}: from and to are required` };
  if (!dep || !arr) return { error: `line ${row.line}: departure and arrival must be HH:MM` };
  const departure = berlinInstant(date, dep);
  let arrival = berlinInstant(date, arr);
  if (arrival < departure) arrival = berlinInstant(date, arr, 1);
  let actual = null;
  if (String(row.actual_arrival ?? "").trim() !== "") {
    const t = normaliseTime(row.actual_arrival);
    if (!t) return { error: `line ${row.line}: actual_arrival must be HH:MM` };
    actual = berlinInstant(date, t);
    // Past midnight, or simply late enough to land on the next day.
    while (actual < arrival && Date.parse(arrival) - Date.parse(actual) > 12 * 3600_000) {
      actual = new Date(Date.parse(actual) + 86400_000).toISOString();
    }
  }
  const km = String(row.km ?? "").trim() === "" ? null : Number(String(row.km).replace(",", "."));
  if (km !== null && (!Number.isFinite(km) || km < 0)) return { error: `line ${row.line}: km must be a number` };
  const train = String(row.train ?? "").trim() || null;
  const delay = actual ? Math.max(0, Math.round((Date.parse(actual) - Date.parse(arrival)) / 60000)) : null;
  return {
    trip: {
      p_trip: {
        source: "import",
        provider: "csv",
        fare_type: "other",
        ...(delay !== null ? { arrival_delay_minutes: delay } : {}),
      },
      p_legs: [{
        mode: "train",
        product: productOf(train),
        line_name: train,
        from: { id: null, name: from, lat: null, lon: null, planned: departure, current: null },
        to: { id: null, name: to, lat: null, lon: null, planned: arrival, current: actual },
        cancelled: false,
        realtime: actual !== null,
        stopovers: [],
        polyline: null,
        distance_km: km,
      }],
    },
    summary: `${date} ${dep} ${from} → ${to}${train ? ` (${train})` : ""}${delay ? ` +${delay}` : ""}${km !== null ? `, ${km} km` : ""}`,
    long: LONG_DISTANCE.has(productOf(train) ?? ""),
  };
}

const ZONE_COUNTRY = {
  "Europe/Berlin": "DE", "Europe/Vienna": "AT", "Europe/Zurich": "CH", "Europe/Rome": "IT", "Europe/Copenhagen": "DK",
  "Europe/Paris": "FR", "Europe/Amsterdam": "NL", "Europe/Brussels": "BE", "Europe/Luxembourg": "LU", "Europe/Prague": "CZ",
  "Europe/Warsaw": "PL", "Europe/Budapest": "HU",
};

async function geocode(name, cache) {
  if (cache.has(name)) return cache.get(name);
  let place = null;
  try {
    const url = new URL("https://api.transitous.org/api/v1/geocode");
    url.searchParams.set("text", name);
    url.searchParams.set("type", "STOP");
    const res = await fetch(url, { headers: { "User-Agent": "Nexus-Abeona-import/1.0 (https://github.com/numynox/nexus)" } });
    const hits = res.ok ? await res.json() : [];
    const hit = Array.isArray(hits) ? hits[0] : null;
    if (hit && typeof hit.lat === "number") {
      place = { lat: hit.lat, lon: hit.lon, country: hit.country ?? ZONE_COUNTRY[hit.tz] ?? null };
    }
  } catch {
    // No coordinates: the trip still counts, just not on the map.
  }
  cache.set(name, place);
  await new Promise((r) => setTimeout(r, 250));
  return place;
}

async function findUserId(admin, email) {
  for (let page = 1; ; page += 1) {
    const { data, error } = await admin.auth.admin.listUsers({ page, perPage: 200 });
    if (error) throw error;
    const users = data?.users ?? [];
    const match = users.find((u) => String(u.email ?? "").trim().toLowerCase() === email);
    if (match) return match.id;
    if (users.length < 200) return null;
  }
}

/** A client signed in as the user: an admin magic link, exchanged on the spot. Nothing is mailed. */
async function userClient(url, anonKey, admin, email) {
  const { data, error } = await admin.auth.admin.generateLink({ type: "magiclink", email });
  if (error) throw error;
  const client = createClient(url, anonKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { error: verifyError } = await client.auth.verifyOtp({ token_hash: data.properties.hashed_token, type: "magiclink" });
  if (verifyError) throw verifyError;
  return client;
}

async function main() {
  const { positionals, values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      "user-email": { type: "string" },
      write: { type: "boolean", default: false },
      geocode: { type: "boolean", default: false },
      "confirm-prod": { type: "boolean", default: false },
    },
    strict: true,
    allowPositionals: true,
  });

  const csvPath = positionals[0];
  const email = String(values["user-email"] ?? "").trim().toLowerCase();
  if (!csvPath || !email) {
    console.error("Usage: npm run db:import:abeona -- <file.csv> --user-email <email> [--write] [--geocode] [--confirm-prod]");
    process.exit(1);
  }

  const url = process.env.PUBLIC_SUPABASE_URL ?? "http://127.0.0.1:54321";
  if (!/localhost|127\.0\.0\.1/i.test(url) && !values["confirm-prod"]) {
    console.error("Refusing to run against non-local Supabase without --confirm-prod.");
    process.exit(1);
  }
  const serviceKey = process.env.SUPABASE_SERVICE_KEY;
  const anonKey = process.env.PUBLIC_SUPABASE_ANON_KEY;
  if (!serviceKey || !anonKey) {
    console.error("Missing SUPABASE_SERVICE_KEY or PUBLIC_SUPABASE_ANON_KEY in the environment.");
    process.exit(1);
  }

  const rows = parseCsv(await readFile(csvPath, "utf8"));
  const parsed = rows.map(tripOf);
  const errors = parsed.filter((p) => p.error).map((p) => p.error);
  const trips = parsed.filter((p) => p.trip);

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const userId = await findUserId(admin, email);
  if (!userId) {
    console.error(`No user with the email ${email}.`);
    process.exit(1);
  }

  if (values.geocode) {
    const cache = new Map();
    for (const t of trips) {
      for (const stop of [t.trip.p_legs[0].from, t.trip.p_legs[0].to]) {
        const place = await geocode(stop.name, cache);
        if (place) Object.assign(stop, place);
      }
    }
  }

  const user = await userClient(url, anonKey, admin, email);
  const { data: household, error: hhError } = await user.rpc("abeona_ensure_household");
  if (hhError) throw hhError;

  // What the household has already, by origin and departure.
  const { data: existing, error: exError } = await user
    .from("abeona_trips")
    .select("origin_name, planned_departure")
    .eq("household_id", household);
  if (exError) throw exError;
  const known = new Set((existing ?? []).map((t) => `${t.origin_name.toLowerCase()}|${Date.parse(t.planned_departure)}`));

  console.log(`mode: ${values.write ? "WRITE" : "DRY RUN (pass --write to save)"}`);
  console.log(`target: ${url} · ${email} · household ${household}`);
  let saved = 0;
  let skipped = 0;
  for (const t of trips) {
    const leg = t.trip.p_legs[0];
    const key = `${leg.from.name.toLowerCase()}|${Date.parse(leg.from.planned)}`;
    if (known.has(key)) {
      skipped += 1;
      console.log(`  skip (saved already) ${t.summary}`);
      continue;
    }
    if (values.write) {
      const { error } = await user.rpc("abeona_save_trip", t.trip);
      if (error) {
        errors.push(`${t.summary}: ${error.message}`);
        continue;
      }
      known.add(key);
    }
    saved += 1;
    console.log(`  ${values.write ? "saved" : "would save"} ${t.summary}`);
  }
  for (const e of errors) console.error(`  error: ${e}`);
  console.log(`${rows.length} rows · ${saved} ${values.write ? "saved" : "to save"} · ${skipped} already there · ${errors.length} errors`);
  await user.auth.signOut().catch(() => {});
  if (errors.length > 0) process.exitCode = 2;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => {
    console.error(e instanceof Error ? e.message : e);
    process.exit(1);
  });
}

# Glossary

The nouns of the three domains, as they appear in code and in the UI. All UI is
English only (`<html lang="en">`); the German text you will find in
`supabase/seed.sql` and in Tankerkoenig responses is *data*, not a translation
layer. Do not add a second language without an ADR.

Read this before naming a table, a column, or a label. Several of these words
mean something narrower than they look.

---

## Cross-app

| Code | UI | Notes |
| :--- | :--- | :--- |
| `profiles` | — | one row per `auth.users` row, shared by all three apps; holds `full_name` and `email` only |
| `session` | — | the Supabase session; the Svelte store every app gates its UI on |
| `base_url` / `getBaseUrl()` | — | the app's GitHub Pages prefix (`/nexus/<app>`); every internal link is built from it |

## Noctua (RSS reader)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `feeds` | Feed | an RSS source. Global: the catalogue is shared by all users. `filter` holds per-feed keyword exclusions applied at fetch time |
| `articles` | Article | one entry from a feed, deduplicated on `(feed_id, url)`. `summary` is the feed's own description — **not** a generated summary. Visible only while one of *your* sections contains its feed, unlike the feed itself |
| `sections` | Section | a user's named, ordered grouping of feeds — the personal arrangement of the shared catalogue. `icon` is a key from `SECTION_ICONS` (not an emoji), `color` a key from `SECTION_COLORS` — the same scheme as Annona's categories |
| `sections_feeds` | — | join table, carries the feed's `sort_order` within a section |
| `article_reads` | — | **read**: durable, per-user, in the database; drives statistics |
| `noctua_seen_articles` (localStorage) | — | **seen**: device-local, disposable; drives "hide what I've already looked at" |
| `article_stars` | Starred | **intent**, not history: what you meant to come back to. `/read` is where you have been, `/starred` is what you saved |

"Read" and "seen" are two different things tracked in two different places.
Clearing seen history does not touch statistics.

## Vibilia (fuel and vehicle costs)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `cars` | Vehicle | the sidebar and routes say "Vehicle"; the tables, columns and RPCs all say `car`. Keep the split: `car_*` in SQL, "Vehicle" in labels |
| `car_access` | Members / Sharing | grants a non-owner full access to a car; owner plus these rows are what `list_car_members` returns |
| `refuel_events` | Refuel | one fill-up: mileage, litres, total, price per litre, station, plus `is_full_refuel` and `missed_previous_refuel`. `missed_previous_refuel` means a fill went unrecorded before this one, so the statistics RPCs return `null` consumption for that interval instead of a wrong number |
| `car_expenses` | Expense | non-fuel cost against a vehicle |
| `fuel_stations` | Station | a Tankerkoenig station. Its `id` **is** the Tankerkoenig id, not ours |
| `fuel_prices` | Price | one price reading for one fuel type at one station at one `checked_at`. Only the last 21 days are kept |
| `fuel_prices_daily` | — | min/max/avg per station, fuel type and **Europe/Berlin day**; the long-term history behind the "weekly market low" line once raw readings are pruned |
| `fuel_type` | E5 / E10 / Diesel | stored capitalised; the Tankerkoenig API wants lowercase — mapped inside the Edge Function |
| `opening_times`, `overrides`, `whole_day` | Open now | Tankerkoenig's opening-hours shape, stored raw as JSONB and interpreted in SQL |
| fuel level estimate | Fuel Level | derived, never stored: last refuel plus `tank_capacity` and average consumption |

## Annona (food expiration)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `annona_products` | Product | the *kind* of thing — "Barilla Farfalle 200g". Identified by `ean` where known |
| `annona_items` | Item | one physical package of a product, with its own `expiration_date` and location. Products are catalogue; items are stock |
| `is_consumed` | Consume | items are never deleted when used up, they are flagged — so the log survives |
| `annona_storage_locations` | Location | Fridge, Pantry, Freezer… |
| `annona_categories` | Category | shared list, seeded from `DEFAULT_CATEGORIES` on first login; each has a `color` and `icon` key resolved through `lib/categoryMeta.ts` |
| `annona_item_log` | Activity / Comments | append-only trail per item (`created`, `consumed`, `expiration_changed`, `location_changed`, `comment`, …) with the acting `user_id` — the only record of *who* in a shared database |
| `ean` | Barcode | scanned with the camera; the scan path is Product-first: find by EAN, else look it up in Open Food Facts, then create the product and add an item |
| `energy_kcal_100g` … `salt_100g` | Nutrition | the EU nutrition declaration per 100 g/ml, as printed on the packet |
| `nutrition_source` | — | `openfoodfacts` when the values came from a lookup; NULL when typed by hand |

Products, categories and locations in Annona are **shared by every user**, not
owned. There is no "my products".

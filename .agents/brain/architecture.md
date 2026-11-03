# Architecture

Four small internal web apps, one Supabase project, one static GitHub Pages
site. Everything here is either shared deliberately or duplicated deliberately —
knowing which is which is the point of this file.

- **Noctua** — RSS reader (`app/noctua`)
- **Vibilia** — fuel prices, refuel log, vehicle statistics, expenses (`app/vibilia`)
- **Annona** — grocery/food expiration tracker (`app/annona`)
- **Abeona** — train ride companion: trips, live delays, tickets, history (`app/abeona`)

`README.md` covers running it, `DEPLOYMENT.md` covers shipping it. This file
covers how it is put together.

---

## The shape of the whole thing

```
                     ┌─────────────── GitHub Pages (static) ───────────────┐
  browser  ────────► │ /nexus/noctua   /nexus/vibilia   /nexus/annona      │
                     └────────────────────────┬───────────────────────────-┘
                              anon key + user JWT (from the browser only)
                                              │
                     ┌────────────────────────▼───────────────────────────┐
                     │            one Supabase project ("nexus")          │
                     │  auth.users · public schema (all four apps)        │
                     │  RLS on every table · SECURITY DEFINER RPCs        │
                     │  pg_cron + pg_net + Vault ──► Edge Functions       │
                     └────────────────────────┬───────────────────────────┘
                                              │
                            Tankerkoenig API · RSS feeds (outbound)
```

There is **no application server**. The four apps are static HTML + JS — plus
a native Android app each — and the only backend is Supabase. See
`decisions/2026-02-15-static-astro-svelte-on-github-pages.md` for why, and what
that costs.

---

## Frontend

### Build topology

npm workspaces (`app/noctua`, `app/vibilia`, `app/annona`, `app/abeona`) with a single
lockfile at the root. Each app is an independent Astro project that builds
**static** output into a shared directory:

| App | `base` | `outDir` |
| :--- | :--- | :--- |
| Noctua | `/nexus/noctua` | `output/pages/noctua` |
| Vibilia | `/nexus/vibilia` | `output/pages/vibilia` |
| Annona | `/nexus/annona` | `output/pages/annona` |
| Abeona | `/nexus/abeona` | `output/pages/abeona` |

All four take `base` and `outDir` from `config.yaml` through
`packages/config`, and all four are on the same versions — Astro 7,
`@astrojs/svelte` 9, Svelte 5, Tailwind 4 + daisyUI 5. Keep them in step: they
share one hoisted copy of each, and a split forces npm to nest duplicates.

`output/pages` is what gets published, so the apps are siblings under one
Pages site and the `base` paths are load-bearing — every internal link is built
from `getBaseUrl()`, never hardcoded. All four set `vite.envDir: "../.."`, so
they read the **root** `.env`; there are no per-app env files.

### Layering inside an app

```
src/pages/<route>.astro        static shell: <Layout> + one route component
  └─ src/components/routes/<X>Route.svelte   client:only entry point (Vibilia, Annona)
       └─ AppShell.svelte      session gate + Sidebar + <slot>
            └─ src/components/<feature>/<X>Panel.svelte    the actual UI
                 └─ src/lib/data.ts     the only module that talks to Supabase
                      └─ src/lib/supabase.ts   cached client singleton
```

Two workspace packages hold what is genuinely shared: `packages/config` (the
`config.yaml` reader) and `packages/ui` (`@nexus/ui` — the session gate, the
sign-in panel, the theme picker and all theme handling; see
`decisions/2026-09-05-shared-ui-package.md`). Each app keeps a thin
`AppShell.svelte` that passes in its own sidebar and auth functions.

Supporting modules, same names in every app:

- `lib/data.ts` — **the entire data access layer**: auth helpers, every query,
  every RPC call, and the TypeScript interfaces for the rows it returns
  (hand-written; see
  `decisions/2026-09-04-permissive-supabase-client-generics.md`).
  Components never call `supabase` directly. If you need a new query, it goes
  here. This is the strongest convention in the codebase; 600–830 lines per app.
- `lib/supabase.ts` — `getSupabaseClient()`, memoised, reads
  `PUBLIC_SUPABASE_URL` / `PUBLIC_SUPABASE_ANON_KEY`.
- `lib/storage.ts` — all `localStorage` access, keys prefixed per app
  (`noctua_*`, `vibilia_*`, `annona_*`), each value guarded and normalised on
  read. Device-local preferences live here, never in the database
  (`decisions/2026-04-06-device-local-preferences-in-localstorage.md`).
- `lib/stores.ts` — tiny Svelte stores; `session` in all of them.
- `lib/config.ts` — title, description, base URL and the app's build-time
  settings, all read from the injected `__NEXUS_CONFIG__` (below).

Svelte 5 runes (`$state`, `$props`, `$derived`) throughout. Astro is used purely
as a static site generator and router: there is no SSR, no Astro endpoint, no
middleware.

### Hydration: Noctua differs from the other two

- **Noctua** renders `Sidebar.svelte` from `Layout.astro` at build time and
  hydrates feature components with `client:load`. Auth is not gated centrally:
  components discover the missing session themselves and link to
  `/nexus/noctua/login`, which is its own page.
- **Vibilia and Annona** render nothing meaningful at build time. Each page
  mounts one `*Route.svelte` with `client:only="svelte"`; that wraps
  `AppShell.svelte`, which resolves the session, shows a spinner, then either
  `LoginPanel` or the sidebar plus content. There is no `/login` route — the
  login form replaces the app in place.

The newer pattern (AppShell) is the one to follow for new work; Noctua predates
it.

### Navigation

Plain `<a href>` full page loads everywhere. No client-side router, no view
transitions. Each Astro page passes its own `activeId` to the sidebar. Sidebar
state (which section is expanded, which car is selected) is rebuilt from
`localStorage` on every load — that is why `storage.ts` matters more than it
looks.

### Styling and UI kit

Tailwind 4 through `@tailwindcss/vite` (no `tailwind.config.js`), plus daisyUI 5
with a **curated set of 11 themes**. Theme handling lives in `@nexus/ui` and
works the same everywhere:

1. `Layout.astro` hard-codes `data-theme="dark"` on `<html>`.
2. An `is:inline` script in `<head>`, generated by
   `themeBootstrapScript(storageKey)`, reads `<app>_theme` from `localStorage`
   (values are JSON-encoded, so the literal string `"auto"` includes quotes) and
   overwrites `data-theme` before first paint, avoiding a flash.
3. `storage.ts:setTheme()` delegates to the package, which writes and re-applies
   at runtime, adding a `theme-transition` class for a few hundred milliseconds
   so the change eases in rather than snapping.

Three places define the theme set and must move together: `THEMES` in
`packages/ui/src/theme.js`, the `@plugin "daisyui"` block in each app's
stylesheet, and the `@source "../../../../packages/ui/src"` line beside it —
Tailwind does not scan `node_modules`, so without it the shared components ship
unstyled.

An app may add its **own** themes on top of the shared set: Abeona defines
`abeona` and `abeona-dark` in its stylesheet and passes a `ThemeSet`
(`{themes, autoLight, autoDark}`, from its `lib/themes.ts`) to the theme
functions and `ThemePicker`, so "auto" resolves to its own pair. Without a set
an app behaves as before. An app's own themes live only in its stylesheet and
its `themes.ts`. Abeona's illustrations are inline SVGs (`?raw`) that read
`--il-*` colours its stylesheet sets per theme, so they recolour with it.

Icons are `lucide-svelte`. Charts are `chart.js` (Vibilia wraps it in
`svelte-chartjs`; Noctua and Annona draw with their own components). Vibilia's
map is Leaflet, loaded from the bundle. Annona's barcode scanner is
`html5-qrcode`, **dynamically imported** at the moment the camera opens so it
never lands in the initial bundle — do the same for anything else that heavy.

### PWA

Each app ships `public/sw.js` (a deliberately minimal cache-and-clean service
worker), a generated `manifest.webmanifest.ts`, and its own icon set. The
service worker is registered by an inline script in the layout, skipped on
`file:` URLs. They are installable, not offline-capable: no data is cached.

### Build-time configuration (`config.yaml`)

One file, one reader, four apps. `packages/config` (a workspace package, the
only thing that depends on `js-yaml`) parses `config.yaml` in Node at build
time. Nothing else reads YAML, and no app touches the filesystem for config.

Each `astro.config.mjs` calls `defineNexusConfig(app)` and gets three things
back:

- `base` — the app's Pages path
- `outDir` — `<output_base>/pages/<app>`, absolute
- `define` — a Vite `define` entry that inlines the app's config into the
  bundle as `__NEXUS_CONFIG__`

The app then reads those values through its own `src/lib/config.ts`, which is
just typed accessors over `__NEXUS_CONFIG__`. Because the values are inlined at
build time, that module is safe to import from `.astro` frontmatter *and* from
client-side Svelte — which matters, since `getBaseUrl()` is called from both.

| Key | Consumed as |
| :--- | :--- |
| `<app>.title` / `.description` / `.base_url` | every app: titles, `base`, manifest, internal links |
| `noctua.article_fetch_limit` | Noctua layout → `data-article-fetch-limit` on `<html>` |
| `noctua.statistics_weeks` | Noctua layout → `data-statistics-weeks` |
| `vibilia.price_bucket_minutes` | props into `FuelPriceRoute` |
| `vibilia.search_radius` | same, nearby-station radius in km |
| `output_base` | build output root |

Unknown app names throw; missing or invalid values fall back to the defaults in
`packages/config/index.js`. `base_url` has any trailing slash stripped, so
callers can append `/...` without doubling it.

Noctua still hands its two numeric settings to the browser as `data-*`
attributes on `<html>`, and Vibilia still passes its two as props; that is a
component-level detail, not a config one.

---

## Backend (Supabase)

One project, one `public` schema, shared by all four apps
(`decisions/2026-02-10-single-supabase-project-shared-schema.md`).

### Table naming

Noctua and Vibilia own unprefixed table names because they were there first;
Annona namespaces everything with `annona_`. **New tables get a prefix.**

| Owner | Tables |
| :--- | :--- |
| shared | `profiles` |
| Noctua | `feeds`, `articles`, `sections`, `sections_feeds`, `article_reads`, `article_stars` |
| Vibilia | `cars`, `car_access`, `refuel_events`, `car_expenses`, `fuel_stations`, `fuel_prices`, `fuel_prices_daily`, `vibilia_car_bluetooth_devices`, `vibilia_drive_intervals`, `vibilia_commutes` |
| Annona | `annona_categories`, `annona_storage_locations`, `annona_products`, `annona_items`, `annona_item_log` |
| Abeona | `abeona_households`, `abeona_household_members`, `abeona_travellers`, `abeona_trips`, `abeona_trip_travellers`, `abeona_legs`, `abeona_bahncards`, `abeona_fare_rules` and `abeona_compensation_rules` (reference data), `abeona_claims`; Storage bucket `abeona-tickets` (tickets and BahnCard files) |

`profiles` is the one genuinely shared table: `id` matches `auth.users.id`, and
`on_auth_user_created → public.handle_new_user_profile()` fills it on signup.
An earlier Vibilia-specific `user_profiles` table was folded into it. Every
authenticated user can read every profile — Annona's activity log and Vibilia's
share-by-email need to resolve names.

A `rls_auto_enable()` event trigger turns RLS on for every new table in `public`
automatically, so forgetting `ENABLE ROW LEVEL SECURITY` fails closed (no
policies) rather than open.

### Authorization: four different models, on purpose

This is the single most important thing to understand before touching data
access. All four apps share one user pool, but they scope data differently:

| App | Model | Mechanism |
| :--- | :--- | :--- |
| Noctua | per-user | `sections` and `article_reads` are filtered by `auth.uid()`. `feeds` is readable by any authenticated user — the catalogue is common property — but `articles` is **not**: a row is visible only while some section *you* own contains its feed. Unsubscribe from a feed and its articles disappear from your queries |
| Vibilia | per-car ACL | `cars.owner_id` plus rows in `car_access`, both funnelled through `public.user_can_access_car(car_id, user_id)`; every policy on refuel events and expenses defers to that helper |
| Annona | shared household | any authenticated user may read and write everything; `annona_item_log.user_id` records who did what (`decisions/2026-04-30-annona-shared-household-data.md`) |
| Abeona | bounded household | `abeona_households` with members; every trip, traveller and ticket object carries a household, and every policy defers to `public.abeona_user_in_household(household_id, user_id)`. BahnCards carry none: they belong to a traveller and follow its household. Storage objects follow the row their path names — a ticket its trip, a card file its card (`abeona_user_in_household_path`, `decisions/2026-11-03-abeona-bahncard-files-follow-the-card.md`). Unlike Annona, strangers see nothing (`decisions/2026-11-02-abeona-train-companion.md`, `decisions/2026-11-02-abeona-ticket-storage.md`). The exceptions are `abeona_fare_rules` and `abeona_compensation_rules`, reference data every signed-in user reads and nobody writes through the API. Claims carry no household either: they follow their trip |

Because there is no server, **RLS is the whole authorization story.** The anon
key is public by design; a policy gap is a data leak. Treat every new table's
policies as the security review.

### RPCs

Anything aggregating, joining or crossing a permission boundary is a
`SECURITY DEFINER` function with `search_path` pinned, called via
`supabase.rpc(...)` from `lib/data.ts`
(`decisions/2026-04-06-aggregate-in-postgres-rpcs.md`).

- Noctua: `get_similar_article_groups` (clusters same-story titles by trigram
  similarity within a time window — see
  `decisions/2026-09-05-duplicate-story-collapsing.md`) and
  `noctua_reading_statistics` (the statistics page's counts, so nothing stops
  at PostgREST's 1,000 rows — `decisions/2026-10-21-noctua-statistics-in-postgres.md`).
  Everything else queries tables directly. `get_user_home_feeds` exists in the database but nothing
  calls it any more.
- Vibilia: `get_fuel_stations_current_prices`, `get_fuel_price_plot_history`,
  `get_fuel_price_weekly_minima`, `get_car_refuel_statistics`,
  `get_car_refuel_plot_events`, `get_car_refuel_year_bounds`,
  `get_car_km_per_month` (kept for old clients), `list_car_members`,
  `share_car_with_email`, `user_can_access_car`, and drive tracking's
  `get_car_drive_periods`, `get_car_refuel_legs`, `get_car_drive_model`,
  `get_car_daily_distance`, `get_car_distance_per_month`,
  `get_car_fuel_level_estimate` (`decisions/2026-10-19-bluetooth-drive-tracking.md`),
  and `get_vibilia_commute_expectation`
  (`decisions/2026-11-02-commute-arrival-announcement.md`).
  The drive-tracking functions run as the caller (not `SECURITY DEFINER`) and
  check `user_can_access_car` themselves.
- Annona: `annona_dashboard_summary`
- Abeona: `abeona_ensure_household` (called once after sign-in), `abeona_invite_to_household`,
  `abeona_leave_household`, `abeona_list_household_members`, `abeona_current_household`,
  `abeona_user_in_household`, `abeona_save_trip` and `abeona_update_trip_live` (run as the
  caller, so RLS decides), `abeona_set_trip_travellers`, the BahnCard RPCs
  `abeona_bahncard_deadlines` and `abeona_trip_warnings` (as the caller; the warnings' English
  messages are written in SQL so both clients say the same), `abeona_statistics` (history card and year in review),
  `abeona_train_binding_lifted` (also mirrored in both clients), `abeona_allowed_products`
  (what a trip's ticket covers now, from the rows of `abeona_fare_rules`; as the caller),
  the distance helpers `abeona_haversine_km` / `abeona_polyline_km`, and after the ride
  `abeona_trip_arrival` (the real arrival: replacement, final, live or planned),
  `abeona_compensation` (the verdict from `abeona_compensation_rules`, with the German
  form text) and `abeona_claims_overview` (all as the caller), plus the snapshot job's
  `abeona_trips_to_finalize` / `abeona_finalize_trip` (service role only;
  `decisions/2026-11-03-abeona-final-snapshot-job.md`,
  `decisions/2026-11-03-abeona-compensation-rules-as-data.md`).
- Operational: `reassign_profile_user`, `invoke_fetch_rss`,
  `invoke_refresh_fuel_prices`, `invoke_abeona_refresh_trips`, `nexus_time_zone`,
  `rollup_fuel_prices_daily`, `prune_fuel_prices_raw`,
  `prune_articles_unread`, `prune_net_http_response`,
  `prune_cron_job_run_details`, `run_nexus_maintenance`

A `SECURITY DEFINER` function bypasses RLS, so each one re-checks access itself
(`user_can_access_car`, or an explicit `auth.uid()` filter). Several have been
replaced in place across migrations — when changing a signature, `DROP` the old
overload explicitly or both will exist.

### Edge Functions

Deno, in `supabase/functions/`, deployed manually. Two authentication styles:

| Function | Trigger | Auth | Does |
| :--- | :--- | :--- | :--- |
| `fetch-rss` | `pg_cron`, hourly | shared secret in `Authorization: Bearer` | fetches every enabled feed, maps entries, applies per-feed keyword filters, upserts `articles` on `(feed_id, url)` |
| `refresh-fuel-prices` | `pg_cron`, 6×/hour | shared secret (bearer or `x-invoke-secret`) | Tankerkoenig prices for tracked stations; refreshes one station's details per call, at most weekly per station |
| `fetch-nearby-fuel-prices` | called from Vibilia's UI | the end user's JWT, verified with `auth.getUser()` | radius search against Tankerkoenig for the nearby view |
| `lookup-food-product` | called from Annona's scanner | the end user's JWT, verified with `auth.getUser()` | asks Open Food Facts about a barcode Annona has no product for (`decisions/2026-09-05-open-food-facts-lookup.md`) |
| `abeona-transit` | called from both Abeona clients | the end user's JWT, verified with `auth.getUser()` | station search, journey search, live refresh and alternatives (marked against `abeona_allowed_products`, asked as the caller; `validity.ts`) against Transitous, normalised to `contract.ts`, which both clients mirror by hand; writes nothing (`decisions/2026-11-02-abeona-transit-providers.md`, `decisions/2026-11-03-abeona-fare-rules-and-device-signals.md`) |
| `abeona-refresh-trips` | `pg_cron`, every 15 min | shared secret (bearer or `x-invoke-secret`) | refreshes trips that arrived 15–90 min ago through `abeona-transit/refresh.ts` (imported by relative path) and freezes their legs (`abeona_finalize_trip`); at most 10 trips a run (`decisions/2026-11-03-abeona-final-snapshot-job.md`) |

The scheduled ones (`fetch-rss`, `refresh-fuel-prices`, `abeona-refresh-trips`) use the service-role key and must never be reachable without the
secret. The last two run as the caller and validate their own inputs (lat/lng
bounds, radius 0.1–25 km, fuel type allow-list; barcode digits and length)
because the requests come from a browser. `supabase/config.toml` declares them all: `verify_jwt = false` for the three
guarded by a shared secret, `true` for those called from a client.

### Scheduling

No external cron. `pg_cron` runs `public.invoke_<function>()`, which reads the
invoke secret and target URL from **Supabase Vault** and fires `net.http_post`
via `pg_net`
(`decisions/2026-03-27-schedule-edge-functions-with-pg-cron-and-vault.md`).

- `fetch-rss-hourly` — `0 * * * *`
- `refresh-fuel-prices-every-10min` — `7,17,27,37,47,57 * * * *`
- `abeona-refresh-trips-every-15min` — `4,19,34,49 * * * *`, Abeona's final
  snapshot; must run on the day, as Transitous forgets realtime soon after
- `nexus-maintenance-daily` — `20 3 * * *`, calling
  `public.run_nexus_maintenance()` (see *Retention* below). Unlike the other
  two it stays inside Postgres; no Edge Function, no Vault secret.

Both scheduling migrations first unschedule any earlier job with the same name
or command, so re-running them is safe. Vault secrets are **not** in migrations
and must exist in each environment or the job raises.

### Retention

Nothing here grows without a bound, because the free tier is 500 MB for all
the apps and two writers would otherwise eat it: the fuel price cron (~3,000
rows a day) and `pg_net`, which logs the HTTP response of every scheduled call
even though both invoke wrappers are fire-and-forget.

`run_nexus_maintenance()` runs nightly and does five things, each isolated so
one failure cannot stop the rest:

| Step | Keeps |
| :--- | :--- |
| `rollup_fuel_prices_daily()` | aggregates raw prices into `fuel_prices_daily` through yesterday, in Europe/Berlin days |
| `prune_fuel_prices_raw()` | 21 days of raw readings, and only deletes rows whose daily aggregate already exists |
| `prune_articles_unread()` | every article ever read, plus 90 days of everything else |
| `prune_net_http_response()` | 24 hours of pg_net responses |
| `prune_cron_job_run_details()` | 7 days of pg_cron history |

Two traps are encoded in those rules. `article_reads.article_id` cascades on
delete, so pruning read articles would silently delete the read history and
rewrite Noctua's statistics — hence "unread only". And the vehicle statistics
page charts weekly price minima over *years*, so raw prices could not simply be
deleted; the rollup preserves that chart exactly, because the minimum of daily
minima is the weekly minimum. Reasoning and the measured effect:
`decisions/2026-09-05-fuel-price-rollup-and-retention.md`.

Deletes alone do not shrink the database — reclaiming space needs `VACUUM FULL`,
which is a manual step in `DEPLOYMENT.md`.

### Migrations

`supabase/migrations/`, timestamp-named, applied with `npm run db:push`. They
are the only description of the schema — there is no ORM and no generated
types (`data.ts` hand-writes its interfaces and casts RPC calls through `any`).
`supabase/db-info.json` documents only Noctua's original five tables and is
stale for everything else; read the migrations instead.

`supabase/seed.sql` seeds the RSS feeds and the tracked fuel stations for local
work.

`supabase/config.toml` is deliberately minimal: it declares only the Edge
Function blocks and leaves everything else to CLI defaults. There is no
`project_id`, so the CLI derives the local container names from the checkout
directory (`supabase_db_nexus`) — renaming the directory starts a *different*
local stack, with an empty database.

---

## Android

Native apps live in `android/`, a Gradle build beside — not inside — the npm
workspace (`decisions/2026-10-19-native-android-apps.md`). Kotlin, Jetpack
Compose, Material 3 Expressive (pinned past the Compose BOM to 1.5 for the
Expressive components), supabase-kt on Ktor/OkHttp.

```
android/
  build-logic/  convention plugins: nexus.android.application (SDKs, version,
                signing, Supabase BuildConfig, config.yaml access) and
                nexus.android.library
  core/data     NexusCore (Supabase client with Auth, Postgrest, Functions and
                Storage; session, AuthRepository, UpdateChecker),
                error wording, NexusConfig, date serializers
  core/ui       NexusTheme (palette per app, wallpaper colours optional), NexusApp
                (theme + session gate + sign-in), Loadable, formatting, components,
                NexusChart, shared settings cards
  core/testing  FakeSupabase and ScreenshotRobot — test-only
  noctua/ vibilia/ annona/ abeona/   one module per app: data/ (rows,
                repository, preferences), ui/ (one package per screen), plus
                what is the app's own (Vibilia's tracking/, Annona's scan/ and
                reminders/, Abeona's domain/, live/, reminders/ and widget/)
```

The split mirrors the websites: `core/` is `packages/`, an app module is an
`app/<name>`. What every app module has in common — SDK levels, the version,
signing, the Supabase fields, test setup — is not in its build file but in the
convention plugin (`decisions/2026-10-19-shared-android-build-and-per-app-ci.md`);
an app's `build.gradle.kts` holds only its dependencies and its own
`config.yaml` values.

The layering inside an app mirrors the web apps. `data/*Repository.kt` is the
Android `lib/data.ts`: every query and RPC, and the only code that talks to
Supabase. Row classes are hand-written there, like the web's interfaces — the
same row shape exists twice, in TypeScript and Kotlin. Preferences are
device-local in DataStore, validated on read, like `lib/storage.ts`. Each app
has an `AppContainer` around one `NexusCore`, wired by hand; there is no DI
framework. Each app's scoping model is the web app's — RLS does not change for
a native client.

Build-time settings come from the sources the websites use: the root `.env` or
CI variables for `PUBLIC_*`, and the app's block of `config.yaml` (read with a
line scan in `build-logic`, `NexusBuildSettings`) — Vibilia's price bucket and
radius, Noctua's article limit and statistics window.

Navigation is the same everywhere: Navigation 3, the back stack a list of
`NavKey`s owned by the app's `MainScaffold`; a tab switch resets it to the home
tab plus that tab. View models are scoped to back-stack entries
(`rememberViewModelStoreNavEntryDecorator`). Forms are bottom sheets.

Things that differ from the web on purpose:

- **Vibilia** — one navigation for the whole app (Prices, Logbook, Statistics,
  Settings); the refuel and expense forms are one sheet above every screen.
  Location from `LocationManager`, not Google Play services. MapLibre with the
  web's tile choice. **Bluetooth drive tracking** (`tracking/`): a manifest
  receiver for `ACL_CONNECTED`/`ACL_DISCONNECTED` feeds `DriveTracker`, a
  state machine persisted in DataStore; WorkManager uploads finished drives and
  keeps a 15-minute heartbeat while one is open. The same connect can start
  the **commute announcement** (`commute/`): a location foreground service
  listens for a minute and speaks the arrival time through the car.
- **Noctua** — Feed, Library (the web's /starred and /read as two shelves),
  Statistics, Settings. Sections are chips above the feed, one shown at a
  time as on the web — no built-in "All", a mix is a section the reader makes; a
  section's feeds are a filter in the top bar. Swipe right stars, swipe left
  recommends to everyone (`decisions/2026-10-31-noctua-recommendations.md`), a
  long press also marks read and shares; articles open in a Custom Tab. Seen is
  marked as items scroll up past the top of the list and kept in DataStore,
  capped at 5,000 ids. The progress bar and the "N new" count follow the web:
  articles (every member of a cluster) shown and unseen, against the count
  taken at load. Refresh is a pull up past the end of the feed, not down at
  the top (`decisions/2026-09-06-refresh-at-the-end-of-the-feed.md`): a
  refresh retakes the seen snapshot, and scrolling back up must not trigger
  it. Statistics are computed on the phone from the same queries as the web
  dashboard (`ReadingStats`).
- **Annona** — Pantry (the dashboard, then stock grouped by how soon it
  expires), Products, Places, Settings (reminders, places, categories). Adding
  and using up start from a FAB menu on every tab, or from launcher shortcuts.
  Barcodes are read on the device by CameraX + zxing-cpp; an unknown barcode
  goes to the `lookup-food-product` Edge Function as on the web. A daily
  **expiry reminder** (WorkManager, device-local and off by default) lists
  what expires within the chosen window.
- **Abeona** — Trips (a BahnCard deadline banner, next trip hero, upcoming,
  a history card), Search, Review (the year in review as a pager), Settings
  (live companion, time to the station, time-to-leave reminder, location,
  alerts, household, travellers, their BahnCards, compensation claims and
  their reminder). After a trip, its screen shows Compensation and What
  happened; the claims list opens from a bar under the year in review. A BahnCard opens
  like a ticket (`ui/bahncards/`), with "Open in DB Navigator" beside it; the
  cards and their files are kept on the phone like the trip pack
  (`files/offline/bahncards.json`, `files/bahncards/<card_id>/`). The
  **BahnCard reminder** (`reminders/`, WorkManager, device-local and off by
  default, like Annona's) speaks up 14, 7, 3 and 1 days before a cancellation
  deadline and on the day. Trip detail has the timeline, MapLibre map, ticket wallet
  (`PdfRenderer`, full brightness, keep-screen-on) and "Open DB Navigator"
  (a launch intent for `de.hafas.android.db`, falling back to its store page;
  declared in `<queries>`). The **offline trip pack** is a JSON file
  (`files/offline/trips.json`) plus ticket copies in `files/tickets/<trip_id>/`;
  screens fall back to it when the network fails and say "Offline · updated
  HH:mm". The **live companion** (`live/`) is a `dataSync` foreground service
  that refreshes the trip every two minutes and keeps an ongoing notification,
  with alerts only for changes it sees after its first refresh. An optional,
  device-local auto-start (off by default) schedules WorkManager jobs for 30
  minutes before departure. Android 12+ may refuse a background start, so it
  then posts a "tap to follow it live" notification instead; Android 15's
  six-hour daily dataSync limit makes it stop itself. The **Next trip** Glance
  widget keeps the last good trip without its route and computes delays and
  confidence when it draws. Tickets go through supabase-kt's Storage plugin,
  installed on core's shared client (with an in-memory resumable-upload cache,
  since the default one needs App Startup). The rules shared with the web
  (`domain/Transfer.kt`, `app/abeona/src/lib/transfer.ts`) round minutes half
  up like `Math.round`, leave out a change whose time is unknown, and must
  change together; `transfer.fixtures.json` beside the web copy holds the
  cases both test suites run. The **travel day** (`domain/TravelDay.kt`,
  `travelday.ts`, `travelday.fixtures.json`) is built the same way; on the
  phone it also takes signals that stay on the device (`live/Signals.kt`
  holds the latest): the train's Wi-Fi (`live/Onboard.kt`, asked over the
  Wi-Fi network itself with short timeouts) and, with its setting, the
  position (`TripLocation`, `LocationManager`, only while the companion runs,
  which then adds the `location` service type when Android allows it). The
  companion looks at both every 30 s and refreshes every two minutes; its
  alerts follow device-local settings, and a WorkManager **time to leave**
  reminder (`reminders/LeaveReminder.kt`) moves itself if the train is late.
  The trip screen offers **alternatives** in a sheet, by itself once per
  refresh when a change is at risk or missed or a train cancelled
  (`decisions/2026-11-03-abeona-fare-rules-and-device-signals.md`). The web
  has the same hero and alternatives, by the clock only, while open.

The web shows what the phone records where it can, and hides what it cannot
do: the drive-based fuel estimate and day-accurate monthly chart come from
Postgres for both clients; reminders and shortcuts are Android-only.

Checks: `./gradlew :<app>:recordRoborazziDebug` runs the app's JVM unit tests
and its `AppScreenshots` — the whole app in Robolectric against a fake
Supabase (`FakeBackend` routing over `core/testing`'s `FakeSupabase`, a Ktor
`MockEngine`), walked screen by screen and photographed. That is how the UI is
reviewed without a device; CI publishes the images as `<app>-screenshots`. The
seams that exist for it: every `AppContainer` takes an HTTP engine,
`VibiliaApplication.mapsAvailable` swaps MapLibre for a schematic map, and the
screenshot applications install a fake Coil image engine.

The Supabase session is stored in each app's own `SharedPreferences`, handed to
supabase-kt explicitly — its default finds a context through App Startup,
which is invisible and does not run in tests. Each app signs in separately;
the account is the same.

---

## Repo-level tooling

- `scripts/db-backup.sh` / `db-restore-local.sh` / `db-restore-remote.sh` —
  manual backup and restore (`decisions/2026-04-17-manual-supabase-backups.md`).
- `scripts/seed-fuel-prices.mjs`, `scripts/seed-products.mjs` — service-key
  seeding, bypassing RLS.
- `scripts/import-fuelio.mjs` — one-off import of Fuelio refuel history; the
  production variant demands `--confirm-prod`.
- `.bruno/supabase/` — Bruno collection for invoking the secret-guarded Edge
  Functions by hand.
- `.vscode/tasks.json` — build, per-app dev servers, local Supabase, function serve.

Two checks exist, and both must be run by hand or by CI — nothing is automatic
on save:

- **`npm run build:websites`** — each app's build runs `astro check` first, so a
  type error fails the build. CI runs this. **`astro check` covers `.astro` and
  `.ts` files only** — it does *not* type-check the script block of a `.svelte`
  component, where most of the code lives. Verified by giving a typed object a
  property that does not exist: the check passes. Real coverage there needs
  `svelte-check`, which nothing runs yet.
- **`npm run test:db`** — pgTAP tests in `supabase/tests/`, run against the
  local Supabase stack with `supabase test db`. Each file is one transaction
  that is rolled back, so tests leave no rows behind and can run against a
  database that already holds data.
  - `vehicle_statistics.test.sql` pins Vibilia's consumption arithmetic
    against hand-computed numbers: distance from mileage gaps, fuel counted
    only across measurable intervals, `missed_previous_refuel` contributing
    distance but no fuel, a negative tank-level correction discarded, and the
    RPC returning nothing for a caller without access to the car.
  - `rls.test.sql` asserts each app's scoping model by impersonating two users
    (`set local role authenticated` plus a `request.jwt.claims` `sub`),
    including Annona's deliberate sharing — so that "everyone can see it"
    stays a decision rather than an accident.
  - `vibilia_commutes.test.sql` pins the commute median (own drives, weekday,
    window, 180 days) and that a commute is visible to its owner only.
  - `noctua_recommendations.test.sql` does the same for Noctua's one shared
    thing: everyone reads every recommendation and gets the article through
    the RPC, nobody writes or withdraws in someone else's name.

Both suites were checked by mutation: loosening the sections policy to
`using (true)`, and making the statistics RPC ignore missed refuels, each make
them fail.

Abeona's shared rules also have a web test (`npm run test:abeona`, plain
`node --test` on `.ts` files through Node 22's type stripping, so
`transfer.ts` may only have `import type` imports and `travelday.ts` imports
it with its `.ts` extension), run from
`app/abeona/src/lib/transfer.fixtures.json` and `travelday.fixtures.json`; the
Android unit tests run the same files (`TransferFixturesTest`,
`TravelDayFixturesTest`; `android/abeona/build.gradle.kts` copies every
`*.fixtures.json` onto the test classpath). Add a case there once, not to
either test. Then
`abeona.test.sql`
(household scoping, the merge on invite, distances, statistics, the binding
rule, ticket storage paths), `abeona_bahncards.test.sql` (card RLS across
households, deadline boundaries, trip warnings, card file paths after a merge)
and `abeona_fare_rules.test.sql` (the rules are read-only, the binding held,
lifted by delay or cancellation, never IC/ICE on a Deutschlandticket).

Still absent: any linter or formatter, any type-checking inside `.svelte`
components, any test of the components or the Edge Functions, and `test:db` in
CI (it needs Docker in the runner).

## History worth knowing

The project began as a Python RSS pipeline with AI summarisation
(`workflow/`, removed 2026-01). Nothing of it remains except `articles.summary`
— which now holds the feed's own description, not a generated one — and the
stale `description` field in the root `package.json`. Do not reintroduce an "AI
summary" concept without deciding it fresh.

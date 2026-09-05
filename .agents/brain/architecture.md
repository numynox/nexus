# Architecture

Three small internal web apps, one Supabase project, one static GitHub Pages
site. Everything here is either shared deliberately or duplicated deliberately —
knowing which is which is the point of this file.

- **Noctua** — RSS reader (`app/noctua`)
- **Vibilia** — fuel prices, refuel log, vehicle statistics, expenses (`app/vibilia`)
- **Annona** — grocery/food expiration tracker (`app/annona`)

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
                     │  auth.users · public schema (all three apps)       │
                     │  RLS on every table · SECURITY DEFINER RPCs        │
                     │  pg_cron + pg_net + Vault ──► Edge Functions       │
                     └────────────────────────┬───────────────────────────┘
                                              │
                            Tankerkoenig API · RSS feeds (outbound)
```

There is **no application server**. The three apps are static HTML + JS; the
only backend is Supabase. See
`decisions/2026-02-15-static-astro-svelte-on-github-pages.md` for why, and what
that costs.

---

## Frontend

### Build topology

npm workspaces (`app/noctua`, `app/vibilia`, `app/annona`) with a single
lockfile at the root. Each app is an independent Astro project that builds
**static** output into a shared directory:

| App | `base` | `outDir` |
| :--- | :--- | :--- |
| Noctua | `/nexus/noctua` | `output/pages/noctua` |
| Vibilia | `/nexus/vibilia` | `output/pages/vibilia` |
| Annona | `/nexus/annona` | `output/pages/annona` |

All three take `base` and `outDir` from `config.yaml` through
`packages/config`, and all three are on the same versions — Astro 7,
`@astrojs/svelte` 9, Svelte 5, Tailwind 4 + daisyUI 5. Keep them in step: they
share one hoisted copy of each, and a split forces npm to nest duplicates.

`output/pages` is what gets published, so the three apps are siblings under one
Pages site and the `base` paths are load-bearing — every internal link is built
from `getBaseUrl()`, never hardcoded. All three set `vite.envDir: "../.."`, so
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

Supporting modules, same names in all three apps:

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
- `lib/stores.ts` — tiny Svelte stores; `session` in all three.
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

One file, one reader, three apps. `packages/config` (a workspace package, the
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
| `<app>.title` / `.description` / `.base_url` | all three apps: titles, `base`, manifest, internal links |
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

One project, one `public` schema, shared by all three apps
(`decisions/2026-02-10-single-supabase-project-shared-schema.md`).

### Table naming

Noctua and Vibilia own unprefixed table names because they were there first;
Annona namespaces everything with `annona_`. **New tables get a prefix.**

| Owner | Tables |
| :--- | :--- |
| shared | `profiles` |
| Noctua | `feeds`, `articles`, `sections`, `sections_feeds`, `article_reads`, `article_stars` |
| Vibilia | `cars`, `car_access`, `refuel_events`, `car_expenses`, `fuel_stations`, `fuel_prices`, `fuel_prices_daily` |
| Annona | `annona_categories`, `annona_storage_locations`, `annona_products`, `annona_items`, `annona_item_log` |

`profiles` is the one genuinely shared table: `id` matches `auth.users.id`, and
`on_auth_user_created → public.handle_new_user_profile()` fills it on signup.
An earlier Vibilia-specific `user_profiles` table was folded into it. Every
authenticated user can read every profile — Annona's activity log and Vibilia's
share-by-email need to resolve names.

A `rls_auto_enable()` event trigger turns RLS on for every new table in `public`
automatically, so forgetting `ENABLE ROW LEVEL SECURITY` fails closed (no
policies) rather than open.

### Authorization: three different models, on purpose

This is the single most important thing to understand before touching data
access. All three apps share one user pool, but they scope data differently:

| App | Model | Mechanism |
| :--- | :--- | :--- |
| Noctua | per-user | `sections` and `article_reads` are filtered by `auth.uid()`. `feeds` is readable by any authenticated user — the catalogue is common property — but `articles` is **not**: a row is visible only while some section *you* own contains its feed. Unsubscribe from a feed and its articles disappear from your queries |
| Vibilia | per-car ACL | `cars.owner_id` plus rows in `car_access`, both funnelled through `public.user_can_access_car(car_id, user_id)`; every policy on refuel events and expenses defers to that helper |
| Annona | shared household | any authenticated user may read and write everything; `annona_item_log.user_id` records who did what (`decisions/2026-04-30-annona-shared-household-data.md`) |

Because there is no server, **RLS is the whole authorization story.** The anon
key is public by design; a policy gap is a data leak. Treat every new table's
policies as the security review.

### RPCs

Anything aggregating, joining or crossing a permission boundary is a
`SECURITY DEFINER` function with `search_path` pinned, called via
`supabase.rpc(...)` from `lib/data.ts`
(`decisions/2026-04-06-aggregate-in-postgres-rpcs.md`).

- Noctua: none — it queries tables directly. `get_user_home_feeds` exists in
  the database but nothing calls it any more.
- Vibilia: `get_fuel_stations_current_prices`, `get_fuel_price_plot_history`,
  `get_fuel_price_weekly_minima`, `get_car_refuel_statistics`,
  `get_car_refuel_plot_events`, `get_car_refuel_year_bounds`,
  `get_car_km_per_month`, `list_car_members`, `share_car_with_email`,
  `user_can_access_car`
- Annona: `annona_dashboard_summary`
- Operational: `reassign_profile_user`, `invoke_fetch_rss`,
  `invoke_refresh_fuel_prices`, `nexus_time_zone`,
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

The first two use the service-role key and must never be reachable without the
secret. The last two run as the caller and validate their own inputs (lat/lng
bounds, radius 0.1–25 km, fuel type allow-list; barcode digits and length)
because the requests come from a browser. `supabase/config.toml` declares all three: `verify_jwt = false` for the two
guarded by a shared secret, `true` for the one called from a browser.

### Scheduling

No external cron. `pg_cron` runs `public.invoke_<function>()`, which reads the
invoke secret and target URL from **Supabase Vault** and fires `net.http_post`
via `pg_net`
(`decisions/2026-03-27-schedule-edge-functions-with-pg-cron-and-vault.md`).

- `fetch-rss-hourly` — `0 * * * *`
- `refresh-fuel-prices-every-10min` — `7,17,27,37,47,57 * * * *`
- `nexus-maintenance-daily` — `20 3 * * *`, calling
  `public.run_nexus_maintenance()` (see *Retention* below). Unlike the other
  two it stays inside Postgres; no Edge Function, no Vault secret.

Both scheduling migrations first unschedule any earlier job with the same name
or command, so re-running them is safe. Vault secrets are **not** in migrations
and must exist in each environment or the job raises.

### Retention

Nothing here grows without a bound, because the free tier is 500 MB for all
three apps and two writers would otherwise eat it: the fuel price cron (~3,000
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

Both suites were checked by mutation: loosening the sections policy to
`using (true)`, and making the statistics RPC ignore missed refuels, each make
them fail.

Still absent: any linter or formatter, any type-checking inside `.svelte`
components, any test of the components or the Edge Functions, and `test:db` in
CI (it needs Docker in the runner).

## History worth knowing

The project began as a Python RSS pipeline with AI summarisation
(`workflow/`, removed 2026-01). Nothing of it remains except `articles.summary`
— which now holds the feed's own description, not a generated one — and the
stale `description` field in the root `package.json`. Do not reintroduce an "AI
summary" concept without deciding it fresh.

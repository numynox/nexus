# Nexus

Monorepo for three small internal web apps that share one Supabase backend and
one GitHub Pages site:

| App | What it does | Path |
| :--- | :--- | :--- |
| **Noctua** | RSS reader | `app/noctua` |
| **Vibilia** | Fuel prices, refuel log, vehicle statistics and expenses | `app/vibilia` |
| **Annona** | Grocery and food expiration tracker | `app/annona` |

One Supabase account signs you in to all three. There is no sign-up screen —
accounts are created in the Supabase dashboard.

Vibilia also has a native Android app (`android/`), on the same backend and
installed from GitHub Releases; Noctua and Annona will follow.

- **How it is put together:** `.agents/brain/architecture.md`
- **How it is hosted and released:** `DEPLOYMENT.md`
- **Conventions for changing it:** `CLAUDE.md`

## Repository layout

```text
nexus/
├── app/
│   ├── noctua/          # Astro + Svelte, static
│   ├── vibilia/
│   └── annona/
├── supabase/
│   ├── migrations/      # the only description of the schema
│   ├── functions/       # Deno Edge Functions
│   ├── config.toml
│   └── seed.sql
├── android/             # native apps: Gradle build, Kotlin + Jetpack Compose
│   ├── core/data/       # Supabase client, session, update check — shared
│   ├── core/ui/         # theme, components, charts, sign-in — shared
│   └── vibilia/         # the Vibilia app
├── packages/
│   └── config/          # the one reader for config.yaml, used by all three apps
├── scripts/             # backup, restore, seed, import
├── config.yaml          # build-time settings (see architecture.md)
├── .bruno/              # HTTP collection for invoking Edge Functions by hand
└── package.json         # npm workspaces root
```

## Prerequisites

- Node 22.12 or newer, as required by Astro 7 (`nvm install node`)
- Docker, for the local Supabase stack
- A Supabase login, and a [Tankerkoenig](https://creativecommons.tankerkoenig.de/)
  API key if you are working on Vibilia's fuel prices

## Setup

### 1. Install

One install at the root covers all three apps (npm workspaces):

```bash
npm install
```

### 2. Start local Supabase

```bash
npm run db:start
```

This prints the local API URL, anon key and service key. `npm run db:status`
prints them again later; `npm run db:stop` shuts it down.

### 3. Configure the environment

Copy `.env.example` to `.env` and fill in the values from the previous step. All
three apps read this one file at the repository root (`vite.envDir` points at
it) — there are no per-app env files.

```bash
PUBLIC_SUPABASE_URL=http://127.0.0.1:54321
PUBLIC_SUPABASE_ANON_KEY=<local anon key>
SUPABASE_SERVICE_KEY=<local service key>   # used by the scripts/ tools only
```

Optional. Vibilia's nearby map uses CARTO basemaps, which have required an API
key since August 2026. Without one the map falls back to OpenStreetMap tiles,
which look fine — set it only if you want the CARTO style locally:

```bash
PUBLIC_CARTO_API_KEY=<carto-basemap-key>
```

Needed only if you are working on the Edge Functions:

```bash
FETCH_RSS_INVOKE_SECRET=<any long random string>
FUEL_PRICE_INVOKE_SECRET=<any long random string>
FUEL_PRICE_API_KEY=<tankerkoenig-api-key>
```

Only `PUBLIC_`-prefixed variables reach the browser; the rest are for the CLI
and the scripts.

### 4. Apply the schema

```bash
npm run db:reset     # recreate the local database from supabase/migrations + seed.sql
```

`db:reset` wipes local data. To apply new migrations without wiping, use
`npm run db:migrations:up`.

### 5. Create a user

The apps only sign in. Create a user in the local Supabase Studio
(Authentication → Users → Add user, with **Auto Confirm User**).

Backups hold the `public` schema only, so a restored database has your data
but no logins. Create a local user with the same email, then attach the
restored profile to it in Studio's SQL Editor:

```sql
select public.reassign_profile_user(
  '<restored profile id>'::uuid,   -- select id, email from profiles;
  (select id from auth.users where email = '<email>'),
  true
);
```

## Development

Run the app you are working on:

```bash
npm run dev:noctua
npm run dev:vibilia
npm run dev:annona
```

Ports are pinned so all three can run side by side:

| App | Dev URL |
| :--- | :--- |
| Noctua | `http://localhost:4321/nexus/noctua/` |
| Vibilia | `http://localhost:4322/nexus/vibilia/` |
| Annona | `http://localhost:4323/nexus/annona/` |

The base path is part of the app, so the bare port root will 404.

Edge Functions, served from the local stack with the root `.env`:

```bash
npm run db:functions:serve
```

Then invoke them with the Bruno collection in `.bruno/supabase/`; the two
scheduled functions require their invoke secret and will return 401 without it.

**Anything that calls an Edge Function needs this running** — Annona's barcode
lookup most obviously. `npm run db:start` does not serve functions; only
`db:functions:serve` does.

With it stopped, the request still reaches the API gateway, which then fails to
resolve the missing runtime container and answers **"name resolution failed"**.
That message names DNS and means nothing of the sort: start the functions and it
goes away.

A genuine DNS fault inside the container looks the same from the outside but
survives starting them, because these functions call external APIs — Open Food
Facts, Tankerkoenig, RSS feeds. Test that case with:

```bash
docker run --rm alpine nslookup world.openfoodfacts.org
```

If *that* fails, give the Docker daemon explicit resolvers in
`/etc/docker/daemon.json` (`{"dns": ["1.1.1.1", "8.8.8.8"]}`), restart Docker,
then `npm run db:stop && npm run db:start`.

Optional test data:

```bash
npm run db:seed:fuel-prices   # -- --days 7 --interval 10
npm run db:seed:products
```

`.vscode/tasks.json` wires the same commands up as VS Code tasks.

## Working with the database

Migrations in `supabase/migrations/` are the single source of truth for the
schema — there is no ORM and no generated types. Create one with
`npx supabase migration new <name>`, then:

```bash
npm run db:migrations:list    # local vs remote state
npm run db:push:dry-run       # what would be applied to the linked project
npm run db:push               # apply to the linked project
```

Pushing to the remote project is a production change — see `DEPLOYMENT.md`
before doing it, and take a backup (`npm run db:backup`) first.

## Tests

```bash
npm run test:db               # pgTAP tests against the local Supabase stack
```

`supabase/tests/` covers Vibilia's refuel statistics arithmetic, its drive
tracking (deduplication, attribution to days, the model, the fuel estimate)
and the RLS policies of all three apps. Each file runs in a transaction that is rolled back,
so it is safe against a database with data in it, and it needs the local stack
running (`npm run db:start`). It is not part of CI — run it when you touch a
migration, a policy or an RPC.

## Build

```bash
npm run build:websites        # all three, into output/pages/<app>, plus the landing page
npm run build:noctua
npm run build:vibilia
npm run build:annona
npm run build:landing         # output/pages/index.html, generated from config.yaml
```

Each build runs `astro check` before `astro build`, so all three apps are
type-checked — though `astro check` only covers `.astro` and `.ts` files, not
the script block of a `.svelte` component. `output/` is generated and git-ignored. CI runs `build:websites`
and publishes `output/pages`; this is the only automated check that exists, so
build all three before pushing.

## Android app

Needs JDK 21 and the Android SDK (Android Studio brings both; otherwise set
`sdk.dir` in `android/local.properties`). The build reads the same
`PUBLIC_SUPABASE_URL`, `PUBLIC_SUPABASE_ANON_KEY` and `PUBLIC_CARTO_API_KEY`
from the root `.env` (or the environment) and Vibilia's bucket and radius from
`config.yaml`, so nothing is configured twice. To run a phone against the
local stack, keep `http://127.0.0.1:54321` and forward the port with
`adb reverse tcp:54321 tcp:54321` (again after each reconnect); the emulator
uses `10.0.2.2`. Debug builds allow plain HTTP to exactly those hosts
(`src/debug/res/xml/network_security_config.xml`) — a LAN address would be
refused.

```bash
cd android
./gradlew :vibilia:installDebug           # onto a connected phone or emulator
./gradlew :vibilia:testDebugUnitTest      # tracker, arithmetic, row decoding
./gradlew :vibilia:recordRoborazziDebug   # unit tests + screenshots of every screen
./gradlew :vibilia:assembleRelease        # what CI publishes
```

### Screenshots

`recordRoborazziDebug` runs `AppScreenshots`: the real app — `MainActivity`,
its view models and repositories — in Robolectric, against `FakeBackend`, a
fake Supabase serving plausible data. It signs in, taps through every screen
like a person would (prices, nearby, the refuel form, logbook, statistics,
settings, a vehicle, dark mode, sign-out) and saves each as a PNG in
`android/vibilia/build/outputs/roborazzi/app/`. No device, emulator or
network needed; about a minute.

CI does the same on every pull request and push and attaches the PNGs as the
**`vibilia-screenshots`** artifact of the run (Actions → the run → Artifacts),
so a UI change can be reviewed from the PR. The map is drawn schematically in
screenshots, because MapLibre's native library cannot load on the JVM. A
screen that needs new data needs it in `FakeBackend` too.

Debug builds install beside a release build (`.debug` suffix). Drive tracking
needs a real phone paired with a car; everything else works in the emulator.

## Scripts reference

`npm run` targets, grouped:

| Area | Scripts |
| :--- | :--- |
| Tests | `test:db` |
| Build / dev | `build:websites`, `build:{noctua,vibilia,annona}`, `build:landing`, `dev:{noctua,vibilia,annona}` |
| Local Supabase | `db:start`, `db:stop`, `db:status`, `db:reset`, `db:functions:serve` |
| Link / migrate | `db:login`, `db:link`, `db:projects:list`, `db:migrations:list`, `db:migrations:up`, `db:push`, `db:push:dry-run`, `db:pull` |
| Functions | `db:functions:deploy`, `db:functions:list` |
| Backup / restore | `db:backup`, `db:restore:local`, `db:restore:remote` |
| Seed / import | `db:seed:fuel-prices`, `db:seed:products`, `db:import:fuelio`, `db:import:fuelio:prod`, `db:backfill:off`, `db:backfill:off:prod` |
| Dumps | `db:dump:schema`, `db:dump:data` |

See `package.json` for the exact commands.

## License

AGPL-3.0-only.

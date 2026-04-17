# Nexus

Monorepo for two Astro + Svelte apps sharing one Supabase backend:

- `app/noctua`: RSS reader
- `app/vibilia`: Fuel tracking and expense prototype

Both apps authenticate against the same Supabase project (`nexus`), so one Supabase account can be used across both UIs.

## Repository layout

```text
nexus/
├── app/
│   ├── noctua/
│   └── vibilia/
├── supabase/
│   ├── migrations/
│   ├── functions/
│   │   ├── fetch-rss/
│   │   └── refresh-fuel-prices/
│   └── config.toml
├── config.yaml
└── package.json
```

## Local setup

### 0) Pre-requisites

Install [nvm](https://github.com/nvm-sh/nvm), npm and node.

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.4/install.sh | bash

nvm install node
```

You need to have a valid login for Supabase and Tankerkoenig.

### 1) Install dependencies

```bash
npm install
```

OR

```bash
npm run install:all
```

To spin up docker containers for a local Supabase, run

```bash
npm run db:start
```

### 2) Configure environment

Copy `.env.example` to `.env` and set values based on terminal output of the step above.

Required for both apps:

```bash
PUBLIC_SUPABASE_URL=https://<your-project-ref>.supabase.co
PUBLIC_SUPABASE_ANON_KEY=<your-anon-key>
SUPABASE_SERVICE_KEY=<your-secret>
```

Required for Edge Functions:

```bash
FETCH_RSS_INVOKE_SECRET=<long-random-secret>
FUEL_PRICE_INVOKE_SECRET=<long-random-secret>
FUEL_PRICE_API_KEY=<tankerkoenig-api-key>
```

### 3) Link local Supabase CLI to your empty `nexus` project

```bash
npm run supabase:login
npm run supabase:projects:list
npm run supabase:link -- --project-ref <your-project-ref>
```

### 4) Apply schema to Supabase

If `nexus` is empty, run:

```bash
npm run supabase:db:push
```

This applies all migrations for both apps.

### 5) Deploy functions to `nexus`

```bash
npm run supabase:functions:deploy:fetch-rss
npm run supabase:functions:deploy:refresh-fuel-prices
```

### 6) Configure Vault secrets for hourly `fetch-rss` cron

`fetch-rss` is invoked every hour via `pg_cron` calling `public.invoke_fetch_rss()`.

The function reads these secrets from `vault.decrypted_secrets`:

- `FETCH_RSS_INVOKE_SECRET`
- `FETCH_RSS_FUNCTION_URL` (for example `https://<your-project-ref>.supabase.co/functions/v1/fetch-rss`)

Set them in Supabase Dashboard → Project Settings → Vault, or via SQL:

```sql
select vault.create_secret('your-fetch-rss-invoke-secret', 'FETCH_RSS_INVOKE_SECRET');
select vault.create_secret('https://<your-project-ref>.supabase.co/functions/v1/fetch-rss', 'FETCH_RSS_FUNCTION_URL');
```

### 7) Configure Vault + Function secrets for hourly `refresh-fuel-prices`

`refresh-fuel-prices` is invoked every hour via `pg_cron` calling `public.invoke_refresh_fuel_prices()`.

Vault secrets required by SQL scheduler:

- `FUEL_PRICE_INVOKE_SECRET`
- `REFRESH_FUEL_PRICES_FUNCTION_URL` (for example `https://<your-project-ref>.supabase.co/functions/v1/refresh-fuel-prices`)
- `FUEL_PRICE_API_KEY`

Create/update these in Supabase Dashboard → Project Settings → Vault, or via SQL:

```sql
select vault.create_secret('your-refresh-invoke-secret', 'FUEL_PRICE_INVOKE_SECRET');
select vault.create_secret('https://<your-project-ref>.supabase.co/functions/v1/refresh-fuel-prices', 'REFRESH_FUEL_PRICES_FUNCTION_URL');
select vault.create_secret('your-tankerkoenig-api-key', 'FUEL_PRICE_API_KEY');
```

Edge Function runtime secrets required by `refresh-fuel-prices`:

- `FUEL_PRICE_INVOKE_SECRET`
- `FUEL_PRICE_API_KEY`

Set them in Supabase Dashboard → Project Settings → Edge Functions → Secrets,
or via CLI:

```bash
npx supabase secrets set FUEL_PRICE_INVOKE_SECRET=your-refresh-invoke-secret --project-ref <your-project-ref>
npx supabase secrets set FUEL_PRICE_API_KEY=your-tankerkoenig-api-key --project-ref <your-project-ref>
```

## Development

To login into supabase from your local dev environment, run:

```bash
npm run db:login
npm run db:link
```

Run each app separately:

```bash
npm run dev:noctua   # http://localhost:4321/nexus/noctua
npm run dev:vibilia  # http://localhost:4322/nexus/vibilia
```

Run local Supabase stack and functions:

```bash
npm run db:start
npm run db:functions:serve
```

## Build & deploy (GitHub Pages)

Both apps are built into one static artifact:

- `output/pages/noctua`
- `output/pages/vibilia`

Build locally:

```bash
npm run build:websites
```

GitHub Pages workflow (`.github/workflows/deploy-pages.yml`) publishes `output/pages`.

Production URLs:

- `https://numynox.github.io/nexus/noctua`
- `https://numynox.github.io/nexus/vibilia`

## GitHub repository variables

Set repository variables used by the Pages workflow:

- `PUBLIC_SUPABASE_URL`
- `PUBLIC_SUPABASE_ANON_KEY`

## Manual Supabase backups (free-tier workaround)

This repo now includes simple, manual scripts to back up your linked remote Supabase database
and restore it locally (for rollback testing) or remotely (destructive).

### What is backed up

- SQL schema dump for selected schemas (default: `public`)
- SQL data dump for selected schemas
- Cluster roles dump (`roles.sql`)

Backups are written to `backups/supabase/<timestamp>/` and ignored by git.

### 1) Create a backup

```bash
npm run db:backup
```

Optional: include additional schemas (for example `auth,storage,public`):

```bash
SUPABASE_BACKUP_SCHEMAS=auth,storage,public npm run db:backup
```

### 2) Test restore safely in local Supabase

```bash
npm run db:restore:local -- backups/supabase/<timestamp>
```

This starts local Supabase if needed and restores the chosen backup into your local DB,
so you can verify rollback before touching production.
The restore script also re-creates the `auth.users -> public.profiles` signup trigger.
It also reapplies the `reassign_profile_user` helper and related FK migrations (Noctua + Vibilia), so older backups still support profile reassignment.

### Reassign restored profiles to new auth users

If user IDs differ after restore (for example local development with newly created users),
use:

```sql
select public.reassign_profile_user(
	'old-profile-user-id'::uuid,
	'new-auth-user-id'::uuid,
	true
);
```

Behavior:

- Reassigns ownership in `sections`.
- Reassigns and deduplicates `article_reads`.
- Reassigns Vibilia relations (`cars.owner_id`, `car_access.user_id`, `refuel_events.user_id`, `car_expenses.user_id`).
- Merges basic profile fields into an existing target profile (when `true`) and removes the source profile.
- Uses FK `ON UPDATE CASCADE` for direct ID reassignment when no target profile exists.

### 3) Optional remote restore (destructive)

Only run this if you really need to roll back remote data.

```bash
SUPABASE_DB_URL='postgresql://postgres.<project-ref>:<password>@aws-0-<region>.pooler.supabase.com:6543/postgres' \
	npm run db:restore:remote -- backups/supabase/<timestamp> --confirm-remote-restore
```

The script requires an explicit confirmation prompt and drops/recreates `public` before restore.
Always create a fresh backup before running remote restore.

## Useful scripts

- `npm run build:websites`
- `npm run build:noctua`
- `npm run build:vibilia`
- `npm run dev:noctua`
- `npm run dev:vibilia`
- `npm run db:push`
- `npm run db:backup`
- `npm run db:restore:local -- backups/supabase/<timestamp>`
- `npm run db:functions:serve`

See also `package.json`.

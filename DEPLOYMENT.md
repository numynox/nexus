# Deployment

How Nexus is hosted, released and recovered. `README.md` covers local
development; the reasoning behind these choices is in `.agents/brain/decisions/`.

There are **two independent deployment tracks**, and only the first is
automated:

| What | How | Automated? |
| :--- | :--- | :--- |
| The three websites | GitHub Actions → GitHub Pages | yes |
| Database migrations | `npm run db:push` from a laptop | no |
| Edge Functions | `npm run db:functions:deploy` from a laptop | no |
| Vault / function secrets | Supabase dashboard or CLI | no |

A frontend change ships on its own. A change that also touches the schema or a
function needs the manual half pushed **first**, or the newly published site
runs against a database that does not have it yet.

---

## Hosting

- **Frontend:** GitHub Pages, from the `numynox/nexus` repository.
- **Backend:** one Supabase project (`nexus`) — Postgres, auth, Edge Functions,
  `pg_cron`, Vault.
- **External API:** [Tankerkoenig](https://creativecommons.tankerkoenig.de/) for
  fuel prices.

All three apps are published into one Pages site as siblings:

| App | URL |
| :--- | :--- |
| Noctua | `https://numynox.github.io/nexus/noctua` |
| Vibilia | `https://numynox.github.io/nexus/vibilia` |
| Annona | `https://numynox.github.io/nexus/annona` |

There is no landing page at `/nexus` — the app URLs are the entry points. The
`/nexus/<app>` prefixes are compiled into every internal link at build time, so
the apps only work under those paths.

---

## Website deploy (automated)

`.github/workflows/deploy-pages.yml`:

1. Triggers on **push to `main`**, or manually via `workflow_dispatch`.
2. Node 22, `npm ci` at the repository root (one workspace install for all three
   apps). Astro 7 requires Node ≥ 22.12.
3. `npm run build:websites` — builds Noctua, Vibilia and Annona in sequence into
   `output/pages/<app>`.
4. Uploads `output/pages` as the Pages artifact and deploys it.

> **A merge to `main` is a release.** There are no release tags; `main` is
> always what is live. Merge only work that is ready to be published — see
> `.agents/brain/decisions/2026-09-04-main-is-the-release-branch.md`.

### Required GitHub repository variables

Set as **variables** (not secrets) — they are baked into the published bundle,
which is expected: the anon key is a public key and all authorization is
enforced by RLS.

- `PUBLIC_SUPABASE_URL`
- `PUBLIC_SUPABASE_ANON_KEY`

Changing either requires a rebuild; there is no runtime configuration.

### Failure modes

- Missing variables do not fail the build. The apps compile, then every request
  from the browser fails with "Missing PUBLIC_SUPABASE_URL…".
- All three builds run `astro check` first, so a type error fails the deploy
  rather than shipping.

---

## Database deploy (manual)

Requires the Supabase CLI logged in and the project linked:

```bash
npm run db:login
npm run db:link -- --project-ref <your-project-ref>
```

Then, from the repository root:

```bash
npm run db:migrations:list   # what the remote has
npm run db:push:dry-run      # what would be applied
npm run db:push              # apply
```

Migrations in `supabase/migrations/` are shared by all three apps — there is no
per-app database release. Take a backup before pushing anything destructive.

## Edge Function deploy (manual)

```bash
npm run db:functions:deploy                      # all functions
npx supabase functions deploy fetch-rss          # one function
npm run db:functions:list                        # what is deployed
```

| Function | Invoked by | Authenticates with |
| :--- | :--- | :--- |
| `fetch-rss` | `pg_cron`, hourly | `FETCH_RSS_INVOKE_SECRET` as a bearer token |
| `refresh-fuel-prices` | `pg_cron`, every 10 minutes | `FUEL_PRICE_INVOKE_SECRET`, bearer or `x-invoke-secret` |
| `fetch-nearby-fuel-prices` | Vibilia's browser UI | the signed-in user's JWT |

`supabase/config.toml` sets `verify_jwt = false` for the first two — they are
guarded by the shared secret instead, and must never be deployed without it
configured.

### Edge Function secrets

Dashboard → Project Settings → Edge Functions → Secrets, or:

```bash
npx supabase secrets set FETCH_RSS_INVOKE_SECRET=<secret> --project-ref <ref>
npx supabase secrets set FUEL_PRICE_INVOKE_SECRET=<secret> --project-ref <ref>
npx supabase secrets set FUEL_PRICE_API_KEY=<tankerkoenig-key> --project-ref <ref>
```

`SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY` are injected
by the platform — do not set them.

`FUEL_PRICE_API_KEY` is used by both `refresh-fuel-prices` and
`fetch-nearby-fuel-prices`.

---

## Scheduling and Vault secrets

The cron jobs live in migrations, so `db:push` installs them. They call SQL
wrappers that read their secrets from **Supabase Vault** and POST to the Edge
Function through `pg_net`. The Vault entries are *not* in migrations and must be
created per environment:

| Vault secret | Example value |
| :--- | :--- |
| `FETCH_RSS_INVOKE_SECRET` | the same secret as the function's |
| `FETCH_RSS_FUNCTION_URL` | `https://<ref>.supabase.co/functions/v1/fetch-rss` |
| `FUEL_PRICE_INVOKE_SECRET` | the same secret as the function's |
| `REFRESH_FUEL_PRICES_FUNCTION_URL` | `https://<ref>.supabase.co/functions/v1/refresh-fuel-prices` |

Dashboard → Project Settings → Vault, or:

```sql
select vault.create_secret('<secret>', 'FETCH_RSS_INVOKE_SECRET');
select vault.create_secret('https://<ref>.supabase.co/functions/v1/fetch-rss', 'FETCH_RSS_FUNCTION_URL');
select vault.create_secret('<secret>', 'FUEL_PRICE_INVOKE_SECRET');
select vault.create_secret('https://<ref>.supabase.co/functions/v1/refresh-fuel-prices', 'REFRESH_FUEL_PRICES_FUNCTION_URL');
```

The Tankerkoenig key is deliberately **not** in Vault: only the Edge Functions
use it, and they read it from their own environment.

Rotating an invoke secret means changing it in **both** places — Vault and the
function's environment — or the job starts getting 401s.

### Current schedule

| Job | Cron | Calls |
| :--- | :--- | :--- |
| `fetch-rss-hourly` | `0 * * * *` | `public.invoke_fetch_rss()` |
| `refresh-fuel-prices-every-10min` | `7,17,27,37,47,57 * * * *` | `public.invoke_refresh_fuel_prices()` |
| `nexus-maintenance-daily` | `20 3 * * *` | `public.run_nexus_maintenance()` |

`nexus-maintenance-daily` never leaves the database — no Edge Function, no Vault
secret — so unlike the other two it reports its own outcome. It returns a JSON
summary of rows touched per step, which `cron.job_run_details` records:

```sql
select start_time, return_message
from cron.job_run_details
where jobid = (select jobid from cron.job where jobname = 'nexus-maintenance-daily')
order by start_time desc limit 7;
```

### Verifying and debugging a job

```sql
select jobid, jobname, schedule, active from cron.job;
select * from cron.job_run_details order by start_time desc limit 20;
```

`pg_net` calls are fire-and-forget: a cron run is recorded as successful even
when the Edge Function fails. To see what the function did, read its logs in the
dashboard. To invoke either function by hand, use the Bruno collection in
`.bruno/supabase/`.

Nothing alerts on failure. If prices or articles look stale, check
`cron.job_run_details` first, then the function logs.

---

## Backups and recovery

The free tier has no point-in-time recovery, so backups are manual scripts
(`.agents/brain/decisions/2026-04-17-manual-supabase-backups.md`). They run
against the **linked remote** project and write to `backups/supabase/<timestamp>/`
(git-ignored).

```bash
npm run db:backup                                          # public schema
SUPABASE_BACKUP_SCHEMAS=auth,storage,public npm run db:backup
```

Rehearse a restore locally before trusting it:

```bash
npm run db:restore:local -- backups/supabase/<timestamp>
```

The local restore rebuilds the schema from `supabase/migrations/` and then loads
only the backup's **data**, so a restored database is always at the current
schema version — old backups work without any per-migration fix-ups.

Restoring a backup taken before the fuel price rollup existed leaves
`fuel_prices_daily` empty. Nothing breaks — the weekly minima RPC falls back to
raw readings and the prune refuses to delete anything — but rebuild it with
`select public.rollup_fuel_prices_daily();` to get the aggregated history back.

Because the default backup covers `public` only, a local restore brings data but
not users; ids will not match. Repoint ownership with:

```sql
select public.reassign_profile_user(
  'old-profile-user-id'::uuid,
  'new-auth-user-id'::uuid,
  true   -- merge into an existing target profile and drop the source
);
```

It reassigns Noctua's `sections` and `article_reads` (deduplicating) and
Vibilia's `cars.owner_id`, `car_access`, `refuel_events` and `car_expenses`.

A remote restore is the mirror image: there the schema comes from the backup
snapshot, because the point is to put the database back exactly as it was. If
that snapshot predates the latest migrations, follow it with `npm run db:push`.
The script prints this reminder.

### Remote restore (destructive)

Drops and recreates `public` on the remote project. Take a fresh backup first,
and only do this deliberately:

```bash
SUPABASE_DB_URL='postgresql://postgres.<ref>:<password>@aws-0-<region>.pooler.supabase.com:6543/postgres' \
  npm run db:restore:remote -- backups/supabase/<timestamp> --confirm-remote-restore
```

---

## Reclaiming database space

Retention runs nightly (see the schedule above), but Postgres does not return
freed space to the operating system on its own: a `DELETE` only marks the rows
reusable. After the first maintenance run — or any other large cleanup — the
dashboard number moves only once the tables are rewritten.

Run it once by hand after deploying the retention migrations:

```sql
-- 1. Aggregate, then prune. Safe to repeat; reports what it touched.
select public.run_nexus_maintenance();

-- 2. Check the charts still look right in Vibilia before reclaiming.

-- 3. Rewrite the tables. Brief exclusive lock; seconds at this size.
vacuum full public.fuel_prices;
vacuum full public.articles;
reindex table public.fuel_prices;
reindex table public.articles;

-- 4. pg_net's log is a log: truncating returns the space immediately.
truncate net._http_response;
```

Measured against a restored production backup (2026-09-05): `fuel_prices` fell
from 71 MB to 7.9 MB, `articles` from 19 MB to 10 MB, and `fuel_prices_daily`
cost 560 kB. `VACUUM FULL` cannot run inside a transaction, so it can never be
part of a migration.

To see where the space actually is:

```sql
select relname, pg_size_pretty(pg_total_relation_size(c.oid)) as total
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where c.relkind = 'r' and n.nspname in ('public', 'net', 'cron')
order by pg_total_relation_size(c.oid) desc limit 10;
```

## Standing up a new environment

Order matters:

1. Create the Supabase project; note the ref, URL, anon key and service key.
2. `npm run db:login` and `npm run db:link -- --project-ref <ref>`.
3. `npm run db:push` — schema, RPCs, RLS and the cron jobs. Expect the jobs to
   raise on every tick until step 5.
4. Deploy the functions and set their secrets (above).
5. Create the Vault secrets (above).
6. Create user accounts in the dashboard. **The apps have no sign-up screen** —
   sign-in only, by design.
7. Set `PUBLIC_SUPABASE_URL` and `PUBLIC_SUPABASE_ANON_KEY` as GitHub repository
   variables, then run the Pages workflow.
8. Optionally seed: `npm run db:seed:fuel-prices`, `npm run db:seed:products`,
   or `supabase/seed.sql` for feeds and stations.

Note that granting anyone an account gives them full read/write access to
Annona's shared inventory —
`.agents/brain/decisions/2026-04-30-annona-shared-household-data.md`.

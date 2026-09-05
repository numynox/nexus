# ADR: Schedule Edge Functions from Postgres (pg_cron + pg_net + Vault)

**Date:** 2026-03-27
**Status:** Accepted
*(Recorded retroactively on 2026-09-04.)*

## Context
Two jobs must run unattended: fetching RSS feeds and refreshing fuel prices.
The candidates were a GitHub Actions schedule, an external cron/uptime pinger,
or Postgres itself. Both functions need a secret to invoke, and GitHub Pages
gives us no runtime to hold one. GitHub Actions cron would have worked, but it
puts the schedule in a place unrelated to the data, drifts by many minutes under
load, and stops firing on inactive repositories.

## Decision
`pg_cron` runs a SQL wrapper — `public.invoke_fetch_rss()` /
`public.invoke_refresh_fuel_prices()` — which reads the invoke secret and the
target function URL from **Supabase Vault** and POSTs to the Edge Function with
`pg_net`. The Edge Functions authenticate by comparing that shared secret and
refuse everything else. Schedules live in migrations
(`fetch-rss-hourly` at `0 * * * *`, `refresh-fuel-prices-every-10min` at
`7,17,27,37,47,57 * * * *`).

## Decision detail worth keeping
Each scheduling migration first unschedules any job with the same name *or* the
same command, then reschedules. Re-running them is therefore idempotent, and the
10-minute fuel schedule cleanly replaced the earlier hourly one.

## Consequences
- The schedule is versioned with the schema and applied by `db:push`.
- **Vault secrets are not in migrations.** A fresh environment applies the
  migrations successfully and then the jobs raise on every tick until
  `FETCH_RSS_INVOKE_SECRET`, `FETCH_RSS_FUNCTION_URL`,
  `FUEL_PRICE_INVOKE_SECRET` and `REFRESH_FUEL_PRICES_FUNCTION_URL` exist. See
  `DEPLOYMENT.md`.
- The same secret exists twice per function: in Vault (for the caller) and in
  the Edge Function's environment (for the check). Rotating means changing both.
- Failures are invisible unless you go looking — `cron.job_run_details` and the
  function logs in the dashboard. There is no alerting.
- `pg_net` calls are fire-and-forget: the cron job succeeds even when the Edge
  Function returns 500.

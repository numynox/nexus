# ADR: Aggregate in Postgres RPCs, not in the browser

**Date:** 2026-04-06
**Status:** Accepted
*(Recorded retroactively on 2026-09-04.)*

## Context
Vibilia's dashboards and statistics need current-price-per-station, price
history bucketed by time, weekly minima, consumption between full refuels, and
cost per month. Annona needs dashboard counts across products, items, categories
and locations. Done in the client, each of these means pulling a large slice of
a table over the wire — the fuel price table grows by six readings per station
per hour — and reimplementing window functions in TypeScript.

## Decision
Every aggregation is a `SECURITY DEFINER` Postgres function with
`SET search_path` pinned, called through `supabase.rpc()` from the app's
`lib/data.ts`. The browser receives finished rows.

## Consequences
- The interesting logic lives in SQL, in migrations — which is where the
  correctness fixes happened too (double-counting, negative consumption,
  opening-times handling), each as its own migration.
- `SECURITY DEFINER` bypasses RLS, so **every function must re-check access
  itself**: Vibilia's call `public.user_can_access_car()` or raise; Annona's
  dashboard is deliberately global because Annona's data is shared. Forgetting
  this check in a new RPC hands out other people's data.
- Changing a signature needs an explicit `DROP FUNCTION` of the old overload,
  or Postgres keeps both and callers bind to whichever matches. This has already
  been necessary once (`annona_dashboard_summary(uuid)` → no-arg).
- There are no generated types. `data.ts` casts the client to `any` for RPC
  calls and hand-writes the result interfaces, so a changed RPC shape fails at
  runtime, not at build.
- Postgres timezone handling is now part of the contract: bucketing and monthly
  grouping take an explicit time zone argument from the client.

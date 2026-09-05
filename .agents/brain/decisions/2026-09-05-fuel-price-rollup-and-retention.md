# ADR: Keep 21 days of raw fuel prices, aggregate the rest daily

**Date:** 2026-09-05
**Status:** Accepted

## Context
After six months in production the database was 170 MB of the free tier's
500 MB, and two thirds of it was data nobody reads:

| | |
| :--- | ---: |
| `fuel_prices` + its three indexes | ~107 MB |
| `net._http_response` (pg_net's log of our own cron calls) | 36 MB |
| `articles` | 21 MB |

`fuel_prices` grows by 7 stations × 3 fuel types × 6 readings/hour ≈ 3,000 rows
a day, unconditionally, whether or not a price moved. Left alone it reaches the
free tier's ceiling within about two years.

Simply deleting old rows was not available. Three things read the table, and one
of them reaches back years: the vehicle statistics page draws a "weekly market
low" line over ranges of 1, 3 or 5 years, or all of history. Deleting raw rows
would have silently emptied that chart — the failure would have looked like a
rendering bug months later.

## Decision
Aggregate rather than discard.

- `fuel_prices_daily` holds min, max, avg and a reading count per station, fuel
  type and **Europe/Berlin day** (`public.nexus_time_zone()`, the one place that
  timezone is defined).
- `rollup_fuel_prices_daily()` fills it through yesterday, starting from the
  last day already stored — so it recomputes a partial day and catches up any
  number of missed runs by itself.
- `get_fuel_price_weekly_minima` reads the rollup, unioned with raw readings for
  days the rollup has not covered yet.
- `prune_fuel_prices_raw()` keeps 21 days (the price history chart never asks
  for more than 7) and deletes a raw row **only when its aggregate exists** for
  the same station, fuel type and day.
- `run_nexus_maintenance()` runs the rollup and every prune —  raw prices,
  unread articles, `net._http_response`, `cron.job_run_details` — as one daily
  `pg_cron` job at 03:20 UTC, comfortably after the Berlin day it aggregates has
  ended. Each step is isolated, so one failure cannot stop the others.

The aggregation is exact rather than approximate, which is the reason this works
at all: **the minimum of daily minima is the weekly minimum.** Verified against
a restored production backup — 352,932 raw rows became 3,207 daily rows, and
the weekly minima for all 69 weeks × 3 fuel types were identical before and
after, including after the raw rows were deleted.

Deliberately not done: writing a row only when the price changes. It would cut
writes by an order of magnitude, but it makes `avg_price` an unweighted average
of change points rather than of time, and the storage problem is already solved.

## Consequences
- `fuel_prices` becomes a fixed-size working set (~8 MB) instead of a growing
  archive; `fuel_prices_daily` costs about 1.3 MB per year.
- **Per-station history older than 21 days is gone.** Daily min/max/avg per
  station survives, but you can no longer ask what a specific station charged at
  14:00 on a Tuesday in March. Nothing in the apps asks that today.
- `get_fuel_price_weekly_minima` keeps its `p_time_zone` parameter but no longer
  honours it: a Berlin day cannot be re-cut into another timezone after
  aggregation. The parameter stays so that an already-deployed frontend keeps
  working — the websites deploy separately from the database — and the caller
  passes Europe/Berlin in practice.
- The prune cannot outrun the rollup, so a failed maintenance run costs disk,
  never data. Restoring a backup taken before this migration leaves the rollup
  empty, in which case the RPC falls back to raw for the whole range and the
  prune refuses to delete: it degrades to the old behaviour rather than losing
  anything.
- Deletes alone do not shrink the database. Reclaiming the space needs
  `VACUUM FULL` and `REINDEX`, which take a brief exclusive lock — see the
  runbook in `DEPLOYMENT.md`.
- Rolling this back means dropping the table and functions, restoring the old
  RPC — and accepting that the raw history it discarded is only in backups.

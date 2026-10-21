# ADR: Count Noctua's statistics in Postgres

**Date:** 2026-10-21
**Status:** Accepted — amends the statistics line of
`2026-10-19-noctua-and-annona-on-android.md`

## Context
Both clients fetched every read and every article of the statistics window as
rows and counted them in the browser or on the phone. PostgREST answers at most
1,000 rows per request. With 1,797 reads and about 8,000 articles in the
eight-week window, every figure built on those rows was quietly cut short:
total reads, streaks, published per week, per-feed shares, both heatmaps.
Android's published-articles query had no order, so it counted an arbitrary
1,000. Two smaller distortions sat on top: "active days" divided by the whole
current week, days that had not happened yet included, and a feed's share
divided reads of older articles by only the window's articles, so it could
pass 100%.

## Decision
- `noctua_reading_statistics(p_weeks)` returns the aggregates as one JSON
  document: totals, every day with a read (for streaks), per-feed reads and
  publications, and [weekday, hour, count] cells for both heatmaps.
- It runs as the caller, so RLS decides which articles count, as before.
- Days, weeks (Monday first) and hours are in `nexus_time_zone()`, like every
  other report, not the device's zone.
- The clients only shape the result (streaks from the day list, weekday
  averages and busiest hour from the cells). Shares compare the same articles:
  reads of articles published in the window ÷ articles published in it.
- Pinned by `supabase/tests/noctua_reading_statistics.test.sql`.

## Consequences
- The migration must be pushed before the web and the APK that call it; an
  older APK keeps its old (capped) queries and keeps working.
- Anything new on the statistics page is a change to the function first.
- Paginating in the clients was the alternative: no migration, but about ten
  requests a load, growing with history.

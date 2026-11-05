# ADR: Scheduled jobs share minutes; writers skip unchanged rows

**Date:** 2026-11-05
**Status:** Accepted

## Context
In November 2026 Supabase warned that the project was depleting its Disk IO
budget. On the free tier (Nano compute) disk throughput falls back to a 5 MB/s
baseline once the burst budget is spent, and the project must stay on that tier.

The database itself writes little: about 10 GB of WAL since February, reads
served almost entirely from cache (hit ratio 99.998 %). The IO comes from WAL
archiving. Supabase sets `archive_timeout = 120 s`, so **every 2-minute window
in which anything is written ends in a forced switch to a fresh 16 MB WAL
segment**, written out and read back by the archiver, however few bytes the
window held. `pg_stat_archiver` counted 72,006 archived segments since
February — about 1.15 TB of segment IO for 10 GB of real WAL. On 2026-11-05
it was 18 segments an hour (~7 GB written and ~7 GB read a day, as 16 MB
spikes).

The jobs ran on 11 different minutes an hour — fuel on `7,17,…`, Abeona on
`4,19,34,49` (added on 2026-11-03, deliberately "off the minutes the other
jobs use"), RSS on `0` — and every run writes at least its own
`cron.job_run_details` row (logged, and its logging cannot be switched off
here). Each was a segment of its own.

The second writer was `fetch-rss`: it upserted every entry of every feed every
hour with `ignoreDuplicates: false`, so ~280 identical rows an hour were
rewritten while ~7 actually changed — 1.5 million updates of a 14,000-row
table, 5.6 GB of WAL (over half of all of it), 343 autovacuums, and page
pruning that made Noctua's *reads* write WAL too.

## Decision
- **All jobs run on the same minutes**, `7,17,27,37,47,57`:
  `refresh-fuel-prices-every-10min` unchanged, `abeona-refresh-trips` every
  10 instead of 15 minutes (renamed `…-every-10min`), `fetch-rss-hourly` at
  `:07`, `nexus-maintenance-daily` at 03:17 UTC. A new job goes on these
  minutes too, never beside them.
- **`fetch-rss` writes only new or changed articles.** It reads the stored
  version of the feed's entries first (a cached read) and upserts only the
  difference. The table ends up exactly as before.

Not done here, because they change what users see (proposed separately):
fetching fuel prices less often, pausing jobs at night, writing fuel prices
only when they change.

## Consequences
- Scheduled segments drop from ~11 to ~6 an hour; together with the quieter
  `articles` table, a little under half of the archive IO goes away. User
  activity still adds its own windows.
- Abeona's final snapshot now gets ~7 attempts in its 15–90 minute window
  instead of ~5, and Transitous sees up to 60 instead of 40 runs an hour. A
  run with no trip in the window makes no Transitous call at all.
- Jobs on one minute start together; `pg_net` sends their requests in
  parallel. Nothing depends on their order.
- Watch `pg_stat_archiver.archived_count` (per day) to see the effect. The
  segment count, not bytes written, is the number that matters on this tier.

# ADR: Abeona freezes a trip's actuals with a scheduled snapshot

**Date:** 2026-11-03
**Status:** Accepted

## Context
#38 phase 5 ("after the ride") needs what actually happened: the statistics
and, now, the compensation read a trip's arrival. Until now the live values
were whatever the last client refresh wrote — a trip nobody opened after
arriving kept stale or planned times. Transitous carries DB's realtime for
the current day only; checked on 2026-11-03 it still answered four hours
after arrival, but not reliably the next day. So the snapshot cannot wait
for a client, and it must happen on the day.

Options: refresh from the phone's background work (only phones with the app
and the setting, and never for web-only households), a GitHub Actions
schedule (drifts, unrelated to the data), or pg_cron like Noctua's and
Vibilia's jobs (`2026-03-27-schedule-edge-functions-with-pg-cron-and-vault.md`).

## Decision
- **One scheduled Edge Function, `abeona-refresh-trips`**, guarded by a
  shared secret (`ABEONA_REFRESH_INVOKE_SECRET`, `verify_jwt = false`) and
  working with the service role, invoked every 15 minutes
  (`4,19,34,49 * * * *`) by `public.invoke_abeona_refresh_trips()`, which reads
  the URL and secret from Vault — the existing wrapper pattern exactly.
- **The window is 15–90 minutes after the expected arrival** (the last ride's
  live arrival, else planned), so a late trip waits for its real arrival and
  every trip gets about five attempts. At most 10 trips per run, one after the
  other, to stay a light Transitous user.
- **The refresh is the clients' refresh**, moved into
  `abeona-transit/refresh.ts` and imported by relative path: the whole
  itinerary first, then leg by leg. One code path for live and final values.
- **Final is per leg** (`abeona_legs.final`); the trip gets `finalized_at`
  once every ride is final. `abeona_finalize_trip` keeps the last live time
  where the answer has none, so losing realtime never erases a known delay.
- **Final values stay final**: a trigger on `abeona_legs` keeps a final leg's
  live and planned values whatever an update says, and
  `abeona_update_trip_live` skips final legs. Older APKs can keep calling it.
- **The SQL half runs as the caller**: `abeona_trips_to_finalize` and
  `abeona_finalize_trip` are security invoker, executable by `service_role`
  only (which bypasses RLS) — no SECURITY DEFINER to re-check.
- **One real arrival**: `abeona_trip_arrival(trip_id)` says when a trip
  arrived — a recorded replacement first, then final, live, planned — and
  `abeona_statistics` and `abeona_compensation` both read it.

## Consequences
- A trip the job never reached (Transitous down for the window, job not
  configured) keeps its last live values and no `finalized_at`; nothing
  retries later, by design: the data would not be there.
- `abeona_save_trip` with an existing id still replaces the legs and with
  them the final flags. No client does that today.
- Server-side work for every household, even ones that never opened a trip.
  At most 40 runs × 10 trips an hour; Transitous's fair-use note still applies.
- Failures are as invisible as the other jobs': the function's logs and
  `cron.job_run_details`. Local `supabase functions serve` could not run in
  the development sandbox (TLS proxy); the job was tested with a fake client,
  live against Transitous, and end to end against the local REST API.

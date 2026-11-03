# ADR: Abeona's history — countries from the stop, CO₂ as figures in SQL, imports saved final

**Date:** 2026-11-04
**Status:** Accepted

## Context
#38 phases 6–7 widen the history: CO₂ saved, countries visited, the longest
trip, a map of everything travelled, and trips from before Abeona (past
dates in the search, Träwelling, a CSV file). Four questions needed an answer.

## Decision
- **Countries without a country column on stations.** The least invasive
  source: a nullable `from_country` / `to_country` on `abeona_legs`, filled by
  `abeona_save_trip` from the transit contract's optional `StopTime.country`,
  which `abeona-transit` derives from the stop's time zone (MOTIS answers it as
  `tz`), else from the country code inside the stop id. Legs saved before, and
  older APKs (which drop the field), fall back in SQL to the id
  (`abeona_stop_country`). Träwelling stations carry neither, so those rides
  have no country unless saved with one; the CSV script can geocode.
- **CO₂ as named constants in SQL** (`abeona_co2_factors`), approximate, from
  the Umweltbundesamt's TREMOD comparison (reference year 2022): car 166,
  long-distance rail 46, regional rail 55, bus 96 g CO₂e per passenger-km.
  "Saved" is what the same kilometres would have cost by car, for one
  traveller. Both clients print the source with the number.
- **Past trips are saved final.** `abeona_save_trip` with `source = 'import'`
  marks the legs final and the trip finalized, so neither the snapshot job nor
  a client refresh ever overwrites what the user entered; an optional
  `arrival_delay_minutes` sets the real arrival. Transitous plans about a
  month back (its loaded timetable window, checked 2026-11-03), so the search
  covers recent trips and says so for older dates.
- **Träwelling through a function, the token never kept.** `abeona-import`
  (user JWT) takes a personal access token with each request, reads
  `/v1/auth/user` and `/v1/user/{username}/statuses` (API "1.0.0 - alpha",
  checked 2026-11-03), groups check-ins that meet at a station within 90
  minutes into trips, and marks the ones the household already has. The client
  saves the chosen ones through `abeona_save_trip`. Neither side stores the
  token.
- **The map** is `abeona_travel_map(from, to)`: one line per pair of stations,
  the most recent route thinned to 48 points, with a count — a year fits in a
  few hundred points.
- **Orphaned Storage files are swept server-side.** Postgres may not delete
  `storage.objects` (Supabase's `protect_delete`), so `abeona_orphaned_files()`
  lists objects older than a day whose trip or card no longer points at them,
  and `abeona-refresh-trips` removes up to 20 per run through the Storage API.
  Clients keep deleting files best effort; the sweep catches the rest
  (a deleted traveller's cards, failed removals, replaced files).

## Consequences
- Countries are approximate at borders: a time zone or an id names the
  stop's country, which is right for stations but says nothing of countries
  crossed without stopping.
- The CO₂ figures move every year; correcting them is a migration.
- An imported trip cannot be refreshed or corrected by the job; editing it
  means deleting and importing again.
- The Träwelling API is marked alpha. The mapping reads every field
  defensively and is pinned by a fixture of real statuses
  (`abeona-import/fixtures/`); it has not run against a real account yet.

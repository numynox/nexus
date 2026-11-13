# ADR: Abeona's BahnCard discount is per traveller

**Date:** 2026-11-04
**Status:** Superseded by `2026-11-13-abeona-no-travellers.md`

## Context
A trip had one `bahncard_discount`. A DB ticket for several people can mix
them: "3 Personen … mit 1 BC25, 1 BC50" is one fare where each share is
discounted by its own card, and one person may have none. The trip warnings
then asked everybody for the same card, which was wrong for the others.

## Decision
- `abeona_trip_travellers.bahncard_discount` holds each traveller's discount,
  and `abeona_trip_warnings` checks each traveller against their own. A
  BahnCard 100 fare still needs one for everybody.
- `abeona_trips.bahncard_discount` stays for older APKs, which read and write
  it. It holds the discount everybody shares, else null. Writing it the old
  way (a direct update, `abeona_save_trip` without per-traveller values,
  `abeona_set_trip_travellers` adding someone) gives every traveller that
  discount, through a trigger and the two RPCs.
- New clients write per traveller only: `abeona_save_trip` with
  `p_trip.travellers`, and `abeona_save_trip_travellers` from the trip page.
  That RPC sets the trip's shared value first, so the trigger runs before the
  per-traveller values are written and never overwrites them.
- A ticket prints persons, not names. The parser reads one discount per
  person; the save sheet gives each traveller picked the one matching a card
  they hold, else the next printed (`lib/travellers.ts`,
  `domain/Travellers.kt`, cases in `intake.fixtures.json`). From a search, a
  traveller starts with the card they hold.

## Consequences
- An older APK that changes the trip's discount flattens a mixed ticket to one
  value for everybody. Acceptable: the household updates its APKs.
- A new client must never update `abeona_trips.bahncard_discount` directly.
- The "1 BC25, 1 BC50" wording is inferred from the "2 BC25" sample; the
  parser takes every BahnCard group on the "Reisende" line, so a different
  separator still parses, but a real mixed ticket should become a fixture.

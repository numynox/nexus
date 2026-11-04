# ADR: Transitous as Abeona's timetable source, behind one Edge Function

**Date:** 2026-11-02
**Status:** Accepted

## Context
Abeona needs station search, journey search, and live delays, platforms and
cancellations, mainly for Germany and ideally for AT, CH, IT, DK and FR, at
no cost. The candidates in November 2026:

- **bahn.de's own API ("vendo"/"movas")**, which `db-vendo-client` speaks.
  It has the best data, including prices and resolving a shared DB Navigator
  link, but it is unofficial. DB switched off its predecessor (HAFAS) in
  January 2025. Tested from a data-centre address on 2026-11-02, it answers
  `{"status":"ERROR","code":"OPS_BLOCKED"}`; Supabase Edge Functions run in
  data centres too.
- **DB API Marketplace, Timetables (IRIS):** official, a free key, about
  60 requests a minute. Station departure boards only, Germany only, no
  routing.
- **Transitous** (api.transitous.org): a volunteer-run MOTIS instance over
  open GTFS and GTFS-RT feeds for most of Europe. Free for non-commercial use,
  with attribution, best effort. Tested on 2026-11-02, it carries DB's realtime
  for the current day (ICE delays, platforms) and route geometry.

The user's first preference was bahn.de with a fallback. The block made that
impossible on the server.

## Decision
- **Transitous is the provider.** One Edge Function, `abeona-transit`, with the
  actions `locations`, `journeys` and `refresh`, normalises its answers to one
  contract (`supabase/functions/abeona-transit/contract.ts`). Both clients
  mirror that contract by hand, as with row types.
- Refresh tries Transitous's `refresh-itinerary` (experimental upstream)
  first. It falls back to fetching each leg's trip and cutting out the saved
  boarding and alighting stops.
- Route lines are decoded and thinned (Douglas–Peucker, about 40 m) in the
  function, so a Frankfurt–Munich ICE is ~300 points, not tens of thousands.
- The function runs as the caller (`verify_jwt = true`, re-checked with
  `auth.getUser()`), so only signed-in Nexus users spend our share.
- Attribution ("Transitous, © OpenStreetMap contributors, the operators") is
  shown in Settings on both clients.
- The provider sits behind a module boundary (`transitous.ts`). Adding DB IRIS
  as a second realtime check, ÖBB or SBB, or bahn.de **called from the phone**
  (residential addresses are not blocked) is a new module, not a new contract.

## Consequences
- No ticket prices, no resolving of DB Navigator share links on the server,
  and no coach sequence. Share-link intake parses the text and searches the
  matching connection instead.
- Realtime coverage outside Germany is whatever feeds Transitous has. The
  clients must say "timetable only" when a leg has no realtime data
  (`realtime = false`).
- Transitous asks heavy users to get in touch and may stop serving anyone.
  Two-minute polling of a handful of trips is light, but a widening user base
  is a reason to talk to them, or to run a MOTIS instance. Nothing in the
  contract would change.
- ~~The responses are not cached server-side yet.~~ Since 2026-11-04 they are:
  `abeona_transit_cache` keeps each Transitous answer under the sha-256 of its
  request, written and read with the service role by `abeona-transit`,
  `abeona-refresh-trips` and `abeona-public` (`cache.ts`): station search for
  a day, journeys for two minutes, refreshes for one. The nightly maintenance
  prunes rows older than two days. A failing cache is a miss, never an error.

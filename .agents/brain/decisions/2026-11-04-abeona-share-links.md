# ADR: Abeona share links are unguessable, revocable, expiring and read-only

**Date:** 2026-11-04
**Status:** Accepted

## Context
#38 phase 7 asks to share a trip with people outside the household: a
calendar file that follows delays, and a live status for whoever picks the
traveller up. Neither of them has a Nexus account, so RLS — the whole
authorization story elsewhere — cannot apply to them. A calendar app cannot
sign in at all; it only fetches a URL, again and again.

Options: make trips public by id (ids leak through logs and screenshots, and
can never be withdrawn), signed URLs from Storage (no live data, and nothing
to revoke), or a token per link, looked up server-side.

## Decision
- **A share link is a row** in `abeona_share_links (token, trip_id, kind, expires_at, created_by)`,
  `kind` being `ics` or `status`, at most one per trip and kind.
- **The token is the credential**: 144 random bits (`gen_random_bytes(18)`,
  hex), made by a trigger whatever the insert says, so a client cannot pick a
  guessable one. Its expiry is the database's too: a status link six hours
  after the trip's expected arrival, a calendar link 30 days after it.
- **Members create, see and revoke** links under the household's RLS (via the
  trip); there is no update. Revoking deletes the row. Nobody else reads the
  table through the API.
- **One Edge Function opens tokens**: `abeona-public`, `verify_jwt = false`,
  looks the token up with the service role through `abeona_shared_trip()`
  (service role only), which returns nothing for an unknown, malformed or
  expired token. Around the trip it refreshes the times through the cached
  Transitous refresh; it never writes.
- **What a token exposes**: origin and destination, each ride's line, stops,
  planned and live times, platforms, cancellations, whether the trip is final.
  **Not** the title (it often says where and why), travellers, the ticket, the
  price, the household or any account. The calendar's UIDs are hashes of the
  trip id, not the id.
- The calendar is served by the function (`text/calendar`). The status page is
  a static page on GitHub Pages (`/nexus/abeona/status?t=…`, `noindex`) that
  fetches JSON from the function, because Supabase rewrites `text/html` from
  Edge Functions to `text/plain` on its own domain.

## Consequences
- Anyone holding a link sees the route and times until it expires or is
  revoked; forwarding it forwards that. The UI says so next to the buttons.
- A calendar subscribed to an expired link keeps its last copy; it just stops
  updating.
- An open endpoint can be hammered. The cache keeps it from reaching
  Transitous more than once a minute per trip, and an invalid token costs one
  indexed lookup; there is no rate limit beyond Supabase's own.
- Expired rows are removed by the nightly maintenance after a week
  (`prune_abeona_share_links`).

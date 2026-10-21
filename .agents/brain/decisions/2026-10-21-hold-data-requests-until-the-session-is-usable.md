# ADR: Hold data requests until the session is usable, and do not sign out on a failed refresh

**Date:** 2026-10-21
**Status:** Accepted

## Context
After an hour or more away the access token has expired. On a cold start, or
on return from the background, supabase-kt refreshes it — and if the network
is not up yet the refresh fails, the status becomes `RefreshFailure`, and the
session is dropped from memory while it retries. Two things went wrong:

- `AuthRepository` found no current user and reported `SignedOut`, so the
  sign-in screen flashed for someone who was signed in all along.
- Requests built meanwhile carried only the anon key. RLS does not refuse
  those, it answers with nothing — Noctua showed "No sections", Vibilia could
  show "No vehicles yet".

## Decision
- `RefreshFailure` keeps the last signed-in user (or the stored session's)
  as `SignedIn`; only `NotAuthenticated` signs out.
- A Ktor plugin on the shared client (`core/data`, `sessionGate`) holds every
  PostgREST and Edge Function request until the status is settled — up to 15
  seconds — and then sends it with the token current at that moment. If the
  refresh has still not got through it fails as a network error, which the
  screens show with a retry, instead of an empty answer that looks true.
- Screens show placeholders with the shape of what is coming, not empty
  states, until their data is there. Noctua caches its sections (per user,
  cleared on sign-out) so its header is complete at once.

## Consequences
- The plugin uses supabase-kt's `httpConfig`, marked `@SupabaseInternal`. If
  that changes, the build breaks, not a phone.
- Sign-in, token refresh and the update check are not held.
- The web's shared `AppShell` had the same flash: `getSession()` rejects when
  the refresh cannot get through, and it showed the sign-in form. It now shows
  "Could not reach the server" with a retry, and lets the client's own retry
  bring the app in. Noctua's web pages already reported an error there.

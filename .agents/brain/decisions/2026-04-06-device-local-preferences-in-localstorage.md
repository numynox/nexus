# ADR: User preferences live in `localStorage`, not in the database

**Date:** 2026-04-06
**Status:** Accepted

## Context
Vibilia briefly stored a `preferred_fuel_type` on `public.profiles` (added when
the Noctua and Vibilia profile tables were unified). Reading it meant a
round-trip before the first useful render on every page load — and every
navigation is a full page load, because the apps are static. The value is a UI
default, not data anyone else needs.

## Decision
`preferred_fuel_type` was dropped from `profiles`, and all preferences moved
into each app's `lib/storage.ts`: theme, Noctua's seen-article set and filters,
Vibilia's fuel type, chart window, last selected car and nearby-search cache,
Annona's theme. Keys are namespaced per app (`noctua_*`, `vibilia_*`,
`annona_*`) so three apps sharing one origin do not collide. Values are
JSON-encoded, and every read normalises, clamps or falls back.

Only the theme breaks the "read it from Svelte" rule: an `is:inline` script in
each `Layout.astro` reads the key in `<head>` to set `data-theme` before first
paint.

## Consequences
- Preferences do not follow the user to another device and do not survive
  clearing site data. Acceptable here; not acceptable for anything a user would
  be annoyed to lose.
- Reads are synchronous at mount, with no loading state — which is what makes
  the first paint clean.
- Every stored value must be validated on read: the store is user-writable and
  old shapes linger. Noctua's `storage.ts` still runs a migration routine at
  module load to clean up keys from earlier versions.
- Anything genuinely shared (an inventory, a vehicle, a section) belongs in
  Postgres. The dividing line is "would another device or another person need
  this?".

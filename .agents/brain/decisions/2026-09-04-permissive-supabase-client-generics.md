# ADR: The Supabase client is typed permissively, and rows stay hand-written

**Date:** 2026-09-04
**Status:** Accepted

## Context
Turning on `astro check` for Vibilia and Annona surfaced 26 and 22 type errors
respectively — every one of them the same thing. `createClient(url, key)` with
no `Database` generic leaves the schema type as `any`, and supabase-js then
resolves `.from(...).insert()` / `.update()` argument types and row types to
`never`. So the entire data layer failed to type-check, without a single real
defect among the errors.

Three ways out:

1. Generate types from the schema (`supabase gen types typescript`) and pass
   them to `createClient`.
2. Cast at every call site — the existing `(supabase as any).rpc(...)` idiom,
   repeated a few dozen more times.
3. Say once, at the client, that the schema is untyped.

## Decision
Option 3: `createClient<any, "public", any>(...)` in each app's
`lib/supabase.ts`, with a comment explaining why. Row and insert shapes stay
hand-written in `lib/data.ts`, as they already were.

Generated types were rejected for now: they would have to be regenerated after
every migration, by hand, with nothing in CI to notice when they drift — and
stale generated types are more misleading than none. This is worth revisiting
if a type-generation step ever becomes part of `db:push`.

## Consequences
- `astro check` now passes for all three apps and runs in every build, so real
  type errors in components, props and page code are caught.
- The database boundary itself is unchecked: a column renamed in a migration
  will not fail the build, only at runtime. The interfaces in `data.ts` are the
  only description of a row's shape, and they are only as correct as their last
  edit.
- The existing `(supabase as any)` casts around RPC calls are now redundant.
  Harmless, so they were left alone; drop them if you are already editing the
  line.

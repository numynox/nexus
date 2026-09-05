# ADR: One Supabase project and one `public` schema for all apps

**Date:** 2026-02-10
**Status:** Accepted
*(Recorded retroactively on 2026-09-04 from the migration history.)*

## Context
Nexus hosts several unrelated internal apps for the same one or two people.
Each could have had its own Supabase project, or its own Postgres schema inside
one project. Supabase's free tier allows only two active projects, and every
extra project means another set of keys, another auth user pool, another CI
variable, and a second login for the same person.

## Decision
One project (`nexus`), one `public` schema, shared `auth.users` and one shared
`public.profiles` table. Apps are separated by **table naming**, not by schema:
Noctua and Vibilia hold historic unprefixed names, Annona and everything added
since use an `annona_`-style app prefix.

## Consequences
- One login works across all three UIs. This is the main benefit and the reason
  not to unpick it.
- The `public` schema is a shared namespace. New tables must be prefixed or the
  next app will collide with them.
- The apps' authorization models diverge inside one schema (per-user in Noctua,
  per-car in Vibilia, fully shared in Annona), which means there is no single
  "how does access work here" answer — see `architecture.md`.
- A migration is global. `npm run db:push` applies every app's migrations at
  once; there is no per-app release of the database.
- A destructive mistake takes all three apps down together, which is what makes
  `decisions/2026-04-17-manual-supabase-backups.md` matter.
- Supabase's client library is scoped to one schema per client, so moving an app
  into its own schema later would touch every call in its `data.ts`.

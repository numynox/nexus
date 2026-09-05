# ADR: Manual scripted backups instead of managed point-in-time recovery

**Date:** 2026-04-17
**Status:** Accepted

## Context
The Supabase free tier has no point-in-time recovery, and its automated backups
are not something we can rely on for a rollback. One project holds the data of
all three apps (`2026-02-10-single-supabase-project-shared-schema.md`), and
migrations are pushed by hand from a laptop, so a bad `db:push` is a plausible
way to lose everything.

## Decision
Three scripts in `scripts/`, driven by the Supabase CLI:

- `db-backup.sh` — schema, data (`--use-copy`) and roles from the linked remote
  into `backups/supabase/<timestamp>/`, with a manifest and restore notes.
  Schemas default to `public`, overridable with `SUPABASE_BACKUP_SCHEMAS`.
- `db-restore-local.sh` — restore a backup into the local Docker Supabase to
  rehearse a rollback safely.
- `db-restore-remote.sh` — destructive remote restore, gated behind
  `--confirm-remote-restore`.

`backups/` is git-ignored.

## Consequences
- Backups happen only when someone remembers. There is no schedule.
- Restoring into a different environment produces new `auth.users` ids, so
  `public.reassign_profile_user(old, new, merge)` exists to repoint ownership
  across `sections`, `article_reads`, `cars.owner_id`, `car_access`,
  `refuel_events` and `car_expenses`, relying on `ON UPDATE CASCADE` foreign
  keys added for that purpose.
- The two restore scripts take deliberately different approaches, and the
  difference matters: **local** rebuilds the schema from
  `supabase/migrations/` and loads only the backup's data, so any backup
  restores at the current schema version; **remote** restores the backup's own
  schema snapshot, because a rollback should reproduce what was there. An
  earlier version of the local script instead replayed a hand-maintained list
  of migrations, which went stale with every new one.
- Default `public`-only backups exclude `auth`, so a restore does not bring
  users back with it.

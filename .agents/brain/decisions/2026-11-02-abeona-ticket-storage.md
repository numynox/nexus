# ADR: Supabase Storage for tickets, under the household's RLS

**Date:** 2026-11-02
**Status:** Accepted — the open end about files outside a trip is resolved by `2026-11-03-abeona-bahncard-files-follow-the-card.md`

## Context
Abeona's ticket wallet shows the ticket PDF full-screen during a check, and
BahnCards will come with a PDF or barcode image. These are files, and the
first files Nexus stores: everything until now has been rows. The free tier
gives 1 GB of Storage.

## Decision
- One private bucket, `abeona-tickets` (10 MB per file; PDF, PNG, JPEG),
  created in the migration.
- Object paths are `<household_id>/<trip_id>/<file>`. The policies on
  `storage.objects` (select, insert, update, delete) go through
  `abeona_user_in_household_path`. When the second segment is a trip, access
  follows **that trip's** household; otherwise it follows the household in
  the first segment (for files that belong to no trip, like BahnCards later).
  A segment that is not a uuid is simply not accessible, rather than an error
  in the policy.
- The trip row stores the path (`abeona_trips.ticket_path`), not a URL.
  Clients download with their session; no signed URL outlives a session.
- Android copies the file into app-private storage for the offline trip pack.
  The web fetches it when shown.

## Consequences
- RLS on `storage.objects` is now part of the security review, like table
  policies. A future bucket follows the same path-prefix rule.
- Storage cannot rename objects cheaply, so the merge on invite leaves files
  where they are. That is why access follows the trip: a merged trip's ticket
  keeps its old household prefix and stays readable by its new household.
  Files outside a trip (BahnCards, later) will need moving, or a per-file
  owner row, before the merge can carry them.
- Backups (`scripts/db-backup.sh`) cover the database, not Storage.
  Tickets are copies of documents the user also has elsewhere, so that is
  acceptable for now.

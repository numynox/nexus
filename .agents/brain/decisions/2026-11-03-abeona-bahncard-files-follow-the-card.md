# ADR: BahnCards belong to travellers, and their files follow the card

**Date:** 2026-11-03
**Status:** Accepted

## Context
Phase 3 of #37 adds BahnCards: a card per traveller, with the PDF or
screenshot of the digital card in Storage. The ticket ADR
(`2026-11-02-abeona-ticket-storage.md`) left an open end: Storage cannot
rename objects cheaply, so a merge on invite leaves files under their old
household prefix. Tickets survive that because access follows the trip in
the path. Files that belong to no trip would have become unreadable for the
merged household.

Three ways out were weighed: move the objects during the merge (Storage's
move API is a client call per object, not something `abeona_invite_to_household`
can do in its transaction), a per-file owner table, or letting the path name
the row that owns the file, as tickets already do.

## Decision
- `abeona_bahncards` carries **no household of its own**. It hangs off
  `abeona_travellers` (`traveller_id`, cascade), and its policy asks whether
  the caller is in the traveller's household. The merge moves travellers, so
  their cards follow without touching the merge function.
- Card files live in the same bucket, `abeona-tickets`, under
  `<household_id>/bahncards/<card_id>/<file>`. `abeona_user_in_household_path`
  gains a second rule: when the second segment is `bahncards` and the third a
  card's uuid, access follows **that card's traveller's household**. Otherwise
  (the card row does not exist yet) it falls back to the first segment, as
  before. A card id under a foreign prefix still resolves to the card's own
  household, so a prefix cannot be used to reach someone else's card.
- Clients create or update the card row first and upload afterwards, so the
  path rule finds the card.

## Consequences
- The rule "a file's path names the row it belongs to, and access follows
  that row" now covers every Abeona file. A future kind of file outside trips
  and cards picks a new second segment and a branch in the same function.
- Deleting a traveller deletes their cards; the files are removed by the
  clients on a best-effort basis, so an orphaned object is possible and
  harmless (nobody can reach it once its row is gone and the prefix is not
  theirs).
- `abeona_trip_warnings` and `abeona_bahncard_deadlines` run as the caller
  (`security invoker`), so RLS scopes them; they need no re-check of their own.

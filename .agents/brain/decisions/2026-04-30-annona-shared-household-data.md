# ADR: Annona's data is shared by all users, not owned per user

**Date:** 2026-04-30
**Status:** Accepted
*(Supersedes the per-user model in `20260430120000_annona_init_schema.sql`,
which lasted a day.)*

## Context
Annona was built like Noctua: every category, location, product and item
carried a `user_id` and RLS scoped rows to `auth.uid()`. That is wrong for the
domain. A household's fridge is one fridge. Two people each scanning the same
yoghurt produced two products and two private inventories, and neither could see
what the other had used up. Vibilia's answer to sharing — an explicit ACL per
car — would have meant an invitation flow to see the kitchen you both live in.

## Decision
Drop `user_id` from `annona_categories`, `annona_storage_locations`,
`annona_products` and `annona_items`. RLS becomes "any authenticated user may
read and write". Names are now globally unique for categories and locations.
Attribution moves to a new append-only `annona_item_log` (`created`,
`consumed`, `unconsumed`, `expiration_changed`, `location_changed`, `deleted`,
`comment`), which keeps `user_id` and is insert-only for the acting user. The
per-item `comment` column was replaced by log entries of type `comment`, and
`annona_dashboard_summary(uuid)` was replaced by a no-argument version.

The migration deduplicates existing rows before dropping the columns: it keeps
the lowest id per name (and per `name, brand, ean` for products) and repoints
foreign keys to the survivor.

## Consequences
- **Every authenticated Nexus user is a member of the household.** Anyone who
  can log in to any app can read and change all of Annona's data. Access
  control is now "who has an account", which is acceptable only because
  accounts are created by hand in the Supabase dashboard. Revisit this before
  ever opening signup.
- `profiles` had to become readable by all authenticated users so the log can
  show who did what.
- Deleting a user cascades their log entries away (`user_id` references
  `auth.users` with `ON DELETE CASCADE`), so the trail is not permanent.
- Unique names mean a second "Fridge" is an error, not a new location — the UI
  must handle the conflict rather than assume per-user namespaces.
- Nothing in Annona can become private again without reintroducing ownership
  and a sharing model; a future "personal" feature needs a new ADR, not a
  column.

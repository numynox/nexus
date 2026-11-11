# ADR: CI applies migrations on main; Edge Functions stay manual

**Date:** 2026-11-11
**Status:** Accepted

## Context
Migrations and Edge Functions were pushed by hand (`npm run db:push`, or the
one-statement `http_get` paste in `DEPLOYMENT.md`). "Push the database half
before the frontend half" depended on memory, and cloud sessions and phones
cannot run the CLI. The pgTAP suite in `supabase/tests/` was run by nobody
routinely: when CI first ran it, three Abeona assertions had been failing since
2026-11-09 (a test bug, fixed with this change).

The options are in issue #54. Constraints: the free plan (no Branching, so not
Supabase's own GitHub deploy; no point-in-time recovery), a public repository,
and `main` being the release branch.

## Decision
- **Migrations (#54 M1):** a `migrate` job in `deploy-pages.yml`, only on
  `refs/heads/main`, runs `supabase db push --db-url` with the session pooler
  connection string (`SUPABASE_DB_URL` secret). The website jobs `need` it, so
  the database always goes first and a failed migration publishes nothing.
  It runs on every push to `main`: with nothing to apply it changes nothing.
- **No approval gate** (the owner's choice): merging is the approval.
- **Pull requests (#54 M3, tests only):** `database-tests.yml` runs
  `supabase test db` against a local stack in the runner. No dry run against
  production: that would put the database password into pull-request
  workflows, for little more than the PR's file list shows.
- **Edge Functions (#54 F3):** stay manual. Deploying them from CI needs a
  personal access token that controls the whole Supabase account.
- The Supabase CLI is pinned (2.116.0) in both workflows.

## Consequences
- A merge to `main` changes the live database at once, unreviewed by anyone
  but the merger. Backups are still manual
  (`2026-04-17-manual-supabase-backups.md`): take one before merging anything
  destructive.
- A schema change and the frontend using it can merge in one PR. A change that
  needs an Edge Function still needs that function deployed first, by hand.
- Workflow runs are no longer cancelled midway; a burst of pushes queues.
- Older APKs still lag; the no-drop/no-rename rule matters more now, because
  nothing stands between the merge and the live schema.
- Revisit Edge Functions (F1/F2 in #54) if they start changing often.

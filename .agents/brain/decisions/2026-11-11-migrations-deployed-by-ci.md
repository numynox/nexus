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
  connection string (`SUPABASE_DB_URL`, a secret of the GitHub environment
  `main`, which only this job names: a repository secret would be readable by
  any workflow on any branch of the repository, pull requests included). The
  website jobs `need` it, so
  the database always goes first and a failed migration publishes nothing.
  It runs on every push to `main`: with nothing to apply it changes nothing.
  The steps, the secret and the failure modes are in `DEPLOYMENT.md`,
  "Database deploy (automated)".
- **Session pooler, not the direct connection:** on the free plan the direct
  connection is IPv6-only, and GitHub-hosted runners have no IPv6. Do not
  "fix" the URL to the direct host.
- **The manual methods stay valid** (`npm run db:push`, the one-statement
  paste): they write the same history (`supabase_migrations.schema_migrations`),
  so CI skips what was applied by hand, e.g. a migration pushed before its
  merge.
- **No approval gate** (the owner's choice): merging is the approval.
- **Pull requests (#54 M3, tests only):** `database-tests.yml` runs
  `supabase test db` against a local stack in the runner. No dry run against
  production: that would put the database password into pull-request
  workflows, for little more than the PR's file list shows.
- **Edge Functions (#54 F3):** stay manual. Deploying them from CI needs a
  personal access token that controls the whole Supabase account. From a
  cloud session, the Supabase connector deploys them without the CLI
  (`DEPLOYMENT.md`, "From a cloud session").
- The Supabase CLI is pinned (2.116.0) in both workflows.

## Outcome
Merged on 2026-11-12 (PR #55). The first migration CI applied was
`20261112090000_ci_migration_smoke_test`, a deliberate no-op (`select 1`)
that proved the job can reach the live database and record a version; the
next push reported "Remote database is up to date". Keep that file: it is in
the live history.

The connector route for Edge Functions was tested the same way on
2026-11-12: `refresh-fuel-prices` was redeployed unchanged from a cloud
session (`deploy_edge_function`, files named `functions/refresh-fuel-prices/…`,
its `deno.json` as import map, `verify_jwt = false`), becoming version 6. Its
next cron call at 11:07 UTC and all 144 calls of the following day answered
200. It works; the steps are in `DEPLOYMENT.md`, "From a cloud session". Still
untested that way: a function with imports from another function's folder
(the Abeona functions).

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

# ADR: `main` is the release branch — no release tags

**Date:** 2026-09-04
**Status:** Accepted

## Context
`CLAUDE.md` had claimed that production was cut by pushing a `v*` tag, leaving
`main` free to hold finished-but-unreleased work. The only workflow,
`.github/workflows/deploy-pages.yml`, has always triggered on `push` to `main`;
nothing ever reacted to tags. The written process and the actual one disagreed,
which is worse than either.

Nexus is used by one or two people, ships several times a week, and has no
staging environment or release notes to hang a version number on. A tag step
would add a ceremony whose only product is a delay.

## Decision
Trunk publishes. Pushing to `main` deploys the websites; there are no release
tags and no version numbers. Direct pushes to `main` are still off limits —
changes arrive by PR — but merging that PR *is* the release.

## Consequences
- **Merge only what is ready to be live.** There is no window between "merged"
  and "released" in which to catch something.
- Rollback is a revert commit plus a rebuild, not a re-tag. There is no
  published artifact to roll back to.
- The database half does not move with it: migrations and Edge Functions are
  deployed by hand, so a PR needing both must have its database half pushed
  **before** the merge, or the freshly published site runs against a schema that
  lacks it (`DEPLOYMENT.md`).
- `main` is the only description of what is in production. Nothing records which
  commit is live except the workflow's own history.
- `workflow_dispatch` remains, so a rebuild can be forced without a commit —
  useful after changing the `PUBLIC_SUPABASE_*` repository variables, which are
  baked in at build time.

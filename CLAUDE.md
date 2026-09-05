# Nexus

Internal web applications based on Supabase and Github Pages. Three Astro +
Svelte apps in one npm workspace, all served as static files, all talking to one
Supabase project:
- Annona: Track food expiration (`app/annona`)
- Noctua: Read RSS feeds (`app/noctua`)
- Vibilia: Track fuel prices and car expenses (`app/vibilia`)

## Project knowledge

@.agents/brain/overview.md

Read the brain sections relevant to the task before structural or architectural
work, and record decisions there as they are made. The overview explains what
belongs in it.

`README.md` covers local development and `DEPLOYMENT.md` covers hosting and
CI/CD. Link to them rather than restating them — duplicated documentation drifts.

Start with `.agents/brain/architecture.md` for anything structural and
`.agents/brain/glossary.md` before naming anything user-facing.

## Environment

- Supabase for database, authentication and edge functions
- Github Pages deployed via CI for static pages

## Working agreements

- **Never push to `main` directly.** Work on a branch and open a PR.
- **Merging to `main` releases.** CI publishes the websites on every push to
  `main` — there are no release tags. Only merge work that is ready to be live,
  and never merge on someone else's behalf
  (`.agents/brain/decisions/2026-09-04-main-is-the-release-branch.md`).
- Migrations and Edge Functions are **not** deployed by CI. If a change needs
  both, push the database half before the frontend half (`DEPLOYMENT.md`).
- Small, focused commits — one concern each.
- Trailer for AI-assisted commits:
  `Co-Authored-By: Claude <model> <noreply@anthropic.com>`
- Stay inside the project folder. Ask before reading or writing outside it.

## Working from issues

Issues and instructions given in chat are equal task sources.

Planned feature work, anything spanning more than one session, and anything
carrying stakeholder requirements belongs in an issue. Exploration, diagnosis
and small mechanical changes do not — an issue for them is ceremony.

When working from one:

- Read the whole issue **and its comments** before starting. Requirements are
  often refined in the discussion rather than the description.
- If it is underspecified, do not guess. Ask in a comment on the issue rather
  than only in chat, so the answer lands where the next reader will look.
- Reference it as `Closes #42` or `Refs #17`, in the pull request body for work
  that resolves it.
- If the outcome differs from what the issue described, say so when closing.
  The discrepancy is usually the most useful part of the record.

## Architecture first

Before implementing, understand what already exists. Look for the code that covers the case, and extend it rather
than introducing a parallel solution beside it.

The conventions that carry the most weight here — reasoning in
`.agents/brain/`:

- **`src/lib/data.ts` is the only module that talks to Supabase.** Queries,
  RPC calls and row interfaces go there; components import functions from it.
- **RLS is the entire authorization story.** There is no server, and the anon
  key ships in the bundle. A new table's policies *are* its security review, and
  a `SECURITY DEFINER` RPC must re-check access itself.
- **Each app scopes data differently** — per-user in Noctua, per-car ACL in
  Vibilia, fully shared in Annona. Never assume one app's model applies to
  another.
- **`supabase/migrations/` is the source of truth for the schema.** No ORM, no
  generated types. Never edit an applied migration; add a new one.
- **Prefix new tables with the app name** (`annona_*`). All three apps share one
  `public` schema.
- **Device-local preference → `lib/storage.ts`; anything shared → Postgres.**
- **Build all three apps before pushing** (`npm run build:websites`). There are
  no tests, and only Noctua type-checks.
- Internal links come from `getBaseUrl()` — the apps live under `/nexus/<app>`,
  never at the root.

## When something is unclear

Do not guess. If requirements or architectural implications are ambiguous, stop
and ask. Where several valid approaches exist, give the trade-offs and recommend
one.
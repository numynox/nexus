# ADR: Static Astro + client-side Svelte on GitHub Pages, no server

**Date:** 2026-02-15
**Status:** Accepted
*(Recorded retroactively on 2026-09-04 from the workflow and app configs.)*

## Context
The apps are personal/internal, must cost nothing to host, and should keep
working with no maintenance for months at a time. An earlier incarnation ran a
Python pipeline on a schedule; keeping any always-on process alive turned out to
be the most expensive part of the project in attention, not money.

## Decision
Every app is an Astro project with `output: "static"`, published to GitHub Pages
by CI. All data-driven UI is Svelte, hydrated in the browser
(`client:load` in Noctua, `client:only="svelte"` in Vibilia and Annona), talking
directly to Supabase with the anon key. No SSR, no Astro endpoints, no
middleware, no server of our own.

## Consequences
- **All authorization is RLS.** The anon key ships in the bundle; anything the
  policies allow is public. A missing policy is not a bug, it is a leak.
- Anything needing a secret has to be an Edge Function. That is why three exist.
- Configuration splits in two: build-time values (`config.yaml`, read by Node
  during the build) and runtime values (`localStorage`). There is nowhere to put
  per-user server state that is not the database.
- Rebuilding is the only way to change build-time config — including the
  Supabase URL and anon key, which are baked in from GitHub repository
  variables.
- Every page is a full load; no shared client-side router. The session is
  re-resolved on each navigation, which is why the AppShell spinner exists.
- The apps live under `/nexus/<app>`, so nothing may assume it is at the domain
  root. Hardcoded absolute paths break silently only in production.

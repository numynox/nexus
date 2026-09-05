# ADR: A shared UI package for the shell, and a curated theme set

**Date:** 2026-09-05
**Status:** Accepted

## Context
Three apps had grown three copies of the same shell. The two `AppShell.svelte`
files differed by four lines, the three `LoginPanel.svelte` files by the app's
name and logo, and the theme logic — read the preference, resolve "auto",
set `data-theme`, animate the transition, and an inline `<head>` script to do it
again before first paint — existed three times in three `storage.ts` files and
four layouts. Any design change had to be made three times and, in practice,
was not: the panels had already drifted apart.

Separately, daisyUI was loaded with `themes: all`. The theme picker was a wall
of 35 near-identical buttons behind an "Auto" toggle that disabled them, and
every app shipped CSS variables for themes nobody would ever pick.

`packages/config` had already established that a workspace package is how
something shared lives here.

## Decision
`packages/ui` (`@nexus/ui`) owns the shell:

- `AppShell.svelte` — the session gate: spinner, then sign-in, then sidebar and
  content. Auth functions are **passed in as props** rather than imported, so
  each app keeps its own Supabase client and `src/lib/data.ts` stays the only
  module that talks to Supabase.
- `LoginPanel.svelte` — parameterised by title, logo and a `signIn` function.
- `ThemePicker.svelte` — swatches rendered in each theme's own colours.
- `theme.js` — the curated list, `getTheme`/`setTheme`/`applyTheme`, and
  `themeBootstrapScript(storageKey)`, which returns the inline script as source
  text because an Astro `is:inline` script cannot import anything.

Each app keeps a thin `AppShell.svelte` that supplies its own sidebar, auth
functions and session store, and each `storage.ts` keeps its `getTheme`/
`setTheme` exports delegating to the package with its own key prefix. Component
call sites did not change.

The theme set is 11: Dim, Night, Dark, Nord, Business, Forest, Coffee,
Halloween, Sunset, Emerald, Lofi. "Auto" resolves to Dark or Lofi by
`prefers-color-scheme`.

## Consequences
- Design work lands once. The picker, the sign-in screen and the loading state
  are now the same in all three apps by construction.
- **Three lists must stay in step**: `THEMES` in `packages/ui/src/theme.js`, the
  `@plugin "daisyui"` block in each app's stylesheet, and — because Tailwind
  does not scan `node_modules` — the `@source` line pointing at
  `packages/ui/src`. Miss the last one and the shared components render
  unstyled.
- Themes outside the curated list are ignored on read, so a stored `"cupcake"`
  from before falls back to Auto rather than a missing theme.
- Noctua still signs in on its own page rather than in place, so it wraps the
  shared panel to handle the redirect. It does not use `AppShell`; unifying that
  would mean restructuring its layout, which is a separate change.
- The package ships `.svelte` and plain `.js` with hand-written `.d.ts`, like
  `packages/config`. No build step, and `astro check` still type-checks the call
  sites.

# ADR: An app may bring its own themes on top of the shared set

**Date:** 2026-11-02
**Status:** Accepted

## Context
`@nexus/ui` owns theme handling for every website: a curated set of 11 daisyUI
themes, defined in three places that move together (`THEMES` in
`packages/ui/src/theme.js`, each app's `@plugin "daisyui"` block, and its
`@source` line). Abeona's look is SJ-inspired: a light green palette whose
illustrations recolour with the theme. None of the 11 themes is that, and
adding a brand theme to the shared list would offer "Abeona green" in Noctua
too.

## Decision
- The theme functions and `ThemePicker` take an optional `ThemeSet`:
  `{ themes, autoLight, autoDark }`. Without one, nothing changes; Noctua,
  Vibilia and Annona pass none.
- Abeona passes its set from `app/abeona/src/lib/themes.ts`: the shared 11
  plus `abeona` and `abeona-dark`, which are defined only in its stylesheet.
  "Auto" resolves to that pair.
- The shared list stays the shared list. "Three places move together" still
  holds for it; an app's own themes live in its stylesheet and `themes.ts`
  only.

## Consequences
- A stored theme name that only one app knows is harmless elsewhere: each app
  has its own `<app>_theme` key in `localStorage`.
- Abeona's illustrations read `--il-*` colour variables: a light default,
  and a dark set for `abeona-dark` and for `.dark`. On the shared themes they
  keep the brand colours rather than following that theme's own palette.
- Android is unaffected: each app already has its own palette in
  `NexusTheme`.

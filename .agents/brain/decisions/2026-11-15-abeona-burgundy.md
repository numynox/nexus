# ADR: Abeona's brand colour is burgundy, not orange

**Date:** 2026-11-15
**Status:** Accepted

## Context
`2026-11-15-abeona-design-system.md` made a warm orange Abeona's brand. Once
every screen carried it, the owner asked for burgundy as the key colour
instead. The rest of that decision stays: the brand is never a status, and
status is the green, gold and red triad with its icons.

## Decision
- **Burgundy `#8C1D40`** is the brand: primary button, rail progress, state
  disc, active tab, eyebrow and countdown. In the dark theme a rose
  `#F08CA8` with deep burgundy `#3D0A1A` on it.
- **One colour for fill and text.** The orange needed two values because
  `#F2761B` reached only 2.8 : 1 against white. Burgundy has 8.9 : 1 with
  white on it and 8.3 : 1 as text on paper, so `brand` and the fill are the
  same value; the rose has 7.8 : 1 on the dark paper.
- `brand-soft` is `#F7E1E7` (dark `#452033`). The warm paper, ink and status
  colours are unchanged.
- The icons are burgundy with a deeper burgundy `#5A1028` band where the
  orange icon had ink.

## Consequences
- Burgundy and the `bad` red (`#C4372B`) are both reds. They stay apart by hue
  (burgundy leans purple, `bad` towards orange), by use (`bad` is a chip, a
  banner or the one broken thing, always with its icon) and by the rule that
  the brand never marks a problem. Watch for a screen where a burgundy action
  sits next to a `bad` banner: the icon carries the difference there.
- The trains keep DB's red livery, which now sits next to the burgundy rail
  in the strip; the livery is drawn, not a token, and means nothing.
- In code the fill is `burgundy` (Android) and `--color-primary` (web); the
  orange names are gone.

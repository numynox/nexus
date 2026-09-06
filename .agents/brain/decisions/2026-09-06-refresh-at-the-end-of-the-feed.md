# ADR: Refresh by overscrolling the end of the feed, not the top

**Date:** 2026-09-06
**Status:** Accepted

## Context

Noctua shipped pull-to-refresh at the top of the article list in #20. It never
worked on a phone: at scroll position zero the downward drag belongs to the
browser, which reloads the whole document. That is worse than doing nothing —
the app restarts, the read/seen snapshot is retaken, and the in-page refresh
never runs.

The gesture is also asking for the wrong thing at the top of the list. The
articles up there are the newest ones already loaded; the moment you actually
want more is when you have run out.

## Decision

The gesture moves to the "you're all caught up" panel at the end of the feed:
drag upward past the last article and the feed re-fetches in place. Nothing
else claims that drag, so no browser behaviour has to be fought or
`preventDefault`-ed.

The panel is sized to `--noctua-feed-height` (defined in `app/noctua/src/styles/app.css`)
— the viewport minus the sticky header and main's own padding — so it occupies
the same rectangle whether it is scrolled to at the end of a full feed or shown
on its own when there is nothing left to read. That is also what makes the
empty page exactly one viewport tall, which is why no scroll lock is needed to
keep it still.

## Consequences

- Refreshing is now two gestures away when the list is long: scroll to the end,
  then pull. `r` on a keyboard and a reload from the panel remain.
- The panel's height depends on layout values it does not own (main's padding,
  the header's margin). They are named and explained in one place, but a change
  to `Layout.astro`'s padding needs a matching change to `--noctua-feed-height`.
- Do not reintroduce a top-of-page pull gesture. It is not a bug that it is
  missing; the browser has that gesture and will win.

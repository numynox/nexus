# ADR: The owl at the end of the feed

**Date:** 2026-10-27
**Status:** Accepted

## Context
The end of Noctua's feed — the panel that holds the pull-to-refresh
(`2026-09-06-refresh-at-the-end-of-the-feed.md`) — was a check mark and a
button. It is the screen a reader sees most when there is nothing left, and
the wish was to make it playful without making it loud. A prototype was
agreed first (night sky, owl, flight on refresh, today's reading as a
constellation, a handful of easter eggs). One part of it was dropped on
review: new articles showed in a sheet before the list. The reader wants to
keep reading straight away, and both apps already start a refreshed list at
its top, so the owl must never stand between the reader and the articles.

## Decision
- **The panel is a window onto the night**: an owl on a branch, a moon in its
  real phase, and a star for each article read today, joined in reading
  order. Tapping a star shows the article and opens it. The owl is awake from
  19:00 to 07:00 local time and naps otherwise.
- **The pull is the owl's**: eyes widen and look up, the branch bends, ear
  tufts pop up (with a haptic tick) at the threshold. Letting go sends it up
  out of the sky; a small owl crosses the moon while the refresh runs. If the
  refresh finds articles, the list starts at its top as before and the owl is
  simply out of sight. If it finds none, the owl comes back and shakes its
  head. Nothing waits for the animation.
- **Easter eggs stay small**: a head-bob on tap, a grumpy puff after many
  taps, two hoots on a long press (sound only with the ringer on, Android),
  the moon's phase on tapping it, a catchable shooting star at night, a full
  head turn after twenty idle seconds, a one-eyed peek when poked asleep.
- **Android is the reference.** It is drawn on a Compose `Canvas`
  (`ui/feed/owl/`), with the motion in `OwlMotion` as critically damped
  springs. The web (`NightOwl.svelte`) draws the same SVG paths in the same
  360 × 470 units, and mirrors the sky helpers in `lib/nightSky.ts`.
- **Today's reading is read from `article_reads`**, not from the loaded
  feed, so articles read in another section count too. It is display only; no
  RPC, because there is nothing to aggregate.
- **Animations off means no motion**: Android's animator scale at 0 and the
  web's `prefers-reduced-motion` skip the flight and idle life.

## Consequences
- The geometry lives twice, in Kotlin and in Svelte. A change to the owl's
  shape is a change to both; the path strings are identical on purpose so
  they can be copied across.
- The web gets less: its refresh replaces the list with a spinner (which is
  what retakes the seen snapshot), so the flight is cut short and the owl
  hops back in with the new list instead of shaking its head. Changing that
  means keeping the list mounted through a refresh, a larger change than the
  owl.
- The idle clock runs every frame while the panel is on screen. It is an
  infinite animation to Compose, so the test policy holds it still and the
  screenshots are stable; the screenshot app's clock is pinned to 22:30 so the
  owl is always awake there.
- The moon is an average lunation, within about a day of the real one.

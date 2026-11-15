# ADR: Abeona gets a design system: orange, a status triad, a living landscape

**Date:** 2026-11-15
**Status:** Accepted; its brand colour, orange, is superseded by `2026-11-15-abeona-burgundy.md`. Everything else holds.

## Context
Abeona's travel day was already ordered by urgency
(`2026-11-13-abeona-one-live-card.md`, `2026-11-15-abeona-what-where-when.md`),
but nothing was written down for the rest: each screen picked its own sizes,
labels and colours, words stood where an icon would do ("Departure",
"Arrives", "Pl.", "· 2 changes"), settings explained themselves in
paragraphs, and a card sat inside a card on Trips. The static train picture
took 132 dp above the next trip even on the travel day. The owner asked for a
system with clear user stories, a warm orange instead of the green, higher
density than the SJ app whose look they like, and something like Noctua's owl
that gives its room away when information matters. Asked for priorities, they
chose: every screen re-laid; orange for the brand with green, yellow and red
for status; a living landscape rather than a character; and a slim strip of
it on the travel day too.

## Decision
- **One written system**, `abeona-design.md`: the moments and what each shows
  first, the tokens, the type scale, the row grid, the icon vocabulary, the
  components and every screen. A rule is changed there, not worked around on a
  screen.
- **Orange is the brand; status is separate.** Amber meant "attention" before,
  and an orange brand beside it would have made every button look like a
  warning. So orange is only identity, the current step and the primary
  action, and status is a triad that always comes with an icon: `ok` green,
  `watch` a clearly yellower gold, `bad` red. A delay is `watch`, never red.
  Line chips and platform boxes lose their colour (ink, outline, well), so
  colour on a screen always means something.
- **Two oranges.** `#F2761B` reads as warm but has 2.8 : 1 against white, so
  it is a fill with ink on it (buttons, the rail, the train's stripe).
  Orange text on a light surface is `#B4470A` (5.1 : 1). On Android that
  darker one is Material's `primary`; the web's `--color-primary` is the fill
  and `text-brand` the text.
- **The scene is a landscape, drawn in code on both clients**, in the same
  units from the same model (`scene.ts`, `SceneModel.kt`, one fixtures file),
  like the owl. Its sky is the real one where the trip is. Its lightness
  follows the theme, for the reason the owl lost its sky card: a bright band
  in a dark theme is the loudest thing on the screen.
- **The strip is the ride's rail.** The owner wanted the landscape on the
  travel day; the live card must not grow. The strip's track carries the stop
  dots and the train stands where the ride is, so it takes the place of the
  stop rail the card had rather than room of its own.
- **Shape and place before words**: a boxed number is a platform, time is
  left, platform right, and each concept has one icon on both clients. Words
  stay for names, rare instructions and anything with legal weight.

## Consequences
- The four static illustrations are gone from both clients; so are the
  `--il-*` colours and the "mint", "sky" and "amber" tokens. The green lives
  on only as `ok`.
- The sun-time and moon-phase formulas now exist in Noctua (owl) and Abeona
  (scene), on both clients. Sharing them would put them in `android/core` and
  `packages/ui`, and a change there rebuilds every app; for forty lines of
  NOAA arithmetic that are tested on both sides the copy is the cheaper
  mistake. Fold them together if a third user appears.
- The scene's geometry lives twice, in Kotlin and in Svelte, like the owl's.
  A change to its shape is a change to both and to `scene.fixtures.json`.
- On the web's shared daisyUI themes the tokens derive from that theme's own
  colours, so the status triad is only as distinct as the theme makes it.
  Abeona's own two themes are the designed ones and what "auto" resolves to.
- `2026-11-02-per-app-theme-sets.md` still holds for how an app brings its own
  themes; its "light green" and `--il-*` are history. The live card's content
  rules in the two ADRs above are unchanged; only its rail and row layout are
  superseded by `abeona-design.md` §8.
- Installed APKs are unaffected: nothing in the database or the Edge
  Functions changed.

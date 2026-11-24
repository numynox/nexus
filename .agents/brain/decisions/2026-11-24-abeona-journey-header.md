# ADR: The live card's scene is the journey header; a ride's stops on request

**Date:** 2026-11-24
**Status:** Accepted

## Context
Feedback from the owner after the 2026-11-22 release, on a real ride (ICE 510
München → Mannheim, S3 to Heidelberg):

- **Stop names without the town.** The passed stop read "Hauptbahnhof". DELFI
  names some stops by themselves in a train's list of stops: Stuttgart Hbf is
  "Hauptbahnhof", München-Pasing is "Pasing".
- **No way to see all stops.** While a trip is live the timeline is hidden,
  so the ride's intermediate stops with their times and delays could no longer
  be opened.
- **The Live Update's bar looked messed up.** Its segments were proportional
  to minutes, so a 3-hour ICE, an 8-minute change and a 16-minute S-Bahn came
  out as one long line, a square stuck to the train and a sliver. The subtext
  carrying the next stop and the speed is not shown on the device at all.
- **The passing-stops line looked wrong.** It put "✓ last stop" on the left
  and "Next …" on the right, and the stops had different dots that meant
  nothing to the owner.
- **The scene should lead the card.** The owner asked for the train animation
  at the top of the card, representing the whole journey, with smooth motion
  and a representation of a change.

## Decision
- **Stop names:** `abeona-transit` gives a refreshed ride's passed stops their
  town when the name is one word or a station word alone. The town comes from
  Transitous's reverse geocoder: the municipality, else a city that is its own
  district. It is cached for a month. The result reads "Stuttgart,
  Hauptbahnhof" and "München, Pasing". A ride's own ends and the search stay
  untouched.
- **The journey header:** the scene strip is the live card's header, flush
  with the card. Its bar is the whole journey, in a frame that never moves
  (the first version added a walk lead and ride dots at the ends, so the bar
  shifted and its margins differed between states, and its trackers had no
  contrast). Every ride and change is always a part; the part in focus takes
  the room. Each mark has one meaning: dots are the stops of the train in
  focus; another train is a dot-less capsule; a change is the gap between two
  capsules, or, in focus, a dashed line in its confidence colour with a walker
  badge; the destination is always the right end. Figures on a bar are badges
  (disc, ring, glyph), on the Live Update too. This replaces the scene inside
  the band (`2026-11-20-abeona-live-journey-line.md`).
- **Smooth:** one frame clock moves the train, the bar, the walker and the
  rail marker from the expected times.
  - Live times glide in over 1 s.
  - State changes morph over 600 ms.
  - All of it stands still under reduced motion.

  The shared model has fixtures on both clients (`journeyBar`, `barLayout` in
  `scene.fixtures.json`).
- **A ride's stops on request:**
  - Under the band there is one left-aligned line: the next stop with its time
    and delay, then "· n stops ⌄".
  - The next train and the later rides have the same toggle.
  - Opened, each stop gets the same dot. On board the rail is full burgundy
    down to a marker where the train is and faded after it, and stops already
    passed are muted.
- **The Live Update:**
  - One equal segment per ride, the changes as points in their colour.
  - The tracker is a badge: the train on a ride, the walker before leaving
    and on a change.
  - Two text lines: the change, compact ("Würzburg Hbf → RE 10 · Pl. 5 · 6 min
    · tight"), then the next stop and the speed.

  This supersedes the bar and the texts of `2026-11-22-abeona-lock-screen-journey-line.md`.

## Consequences
- A trip saved before this gets the towns on its next refresh. The search
  results keep the bare names, because looking up towns would slow every
  search.
- The bar no longer shows how long each ride is, only where in the journey
  the traveller is; the card's times say how long.
- The change line in the Live Update drops "Change at" and the departure time
  to fit one line. The arrow and the minutes carry them.

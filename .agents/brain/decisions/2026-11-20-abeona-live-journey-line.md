# ADR: Abeona's live card is the journey drawn as one line

**Date:** 2026-11-20
**Status:** Accepted

## Context
On a real ride the live card had everything but was hard to read: the
headline switched between a station, a platform and a train; "⇄ 3" and a
bare "5 min" needed explaining; the train sat in a small caps eyebrow; a
timetable notice that no longer needed action took a third of the card;
and the trip's timeline below repeated every time and platform. The owner
asked for a redesign of every situation with a clear visual language, each
fact once and always in the same place, the train drawings kept, and text
cut to the minimum needed. Drawn first on the design canvas "Abeona live
companion redesign" and approved with "remove all unnecessary text".

## Decision
- The card is the journey as a vertical line in the row grid
  (`abeona-design.md` §8): done rides fold into one row, the current ride is
  the band (scene, train, speed, passing stops) on its stretch of line, the
  next thing to do is the one large row with a verb, and the rest runs down
  to the destination, which is always last.
- One fact, one row: the train's name on the band (or on its row when it is
  ahead), the platform on its step, the delay under its time, one countdown.
- While the card is live, the trip's own timeline is not shown.
- Words only where nothing shorter works: verbs on the active step, and the
  state of a broken step. No explanatory sentences.
- Alternatives stay in their sheet; the broken step opens it.

## Consequences
- Supersedes the card layout of `2026-11-13-abeona-one-live-card.md` and the
  headline rules of `2026-11-15-abeona-what-where-when.md` (what each
  situation must say still holds; where it says it changed).
- The design canvas drew the alternatives inline under a missed step; the
  build keeps the existing sheet, which both clients already open by
  themselves when a connection breaks.

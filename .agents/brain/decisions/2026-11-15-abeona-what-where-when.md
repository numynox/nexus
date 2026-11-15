# ADR: Abeona — what, where and when, the same on both cards

**Date:** 2026-11-15
**Status:** Accepted

## Context
The Trips screen's next-trip card and the trip screen's live card were built
separately and drifted. Each showed some facts twice and dropped others: before
leaving, the live card had no platform for the train to catch (Pl. 103) while
it showed the next train's platform, and no card said where the first ride
ends. The situations and what each needs were mapped and approved as a
prototype first (Design canvas "Abeona travel day prototype").

## Decision
- **Every situation answers what, where and when for the step I am on:**
  - before leaving: "Leave at 12:55", the countdown, "20 min to <station>",
    then the train to catch;
  - at the station: the platform as headline, under it the train;
  - on board: where I get off as headline, under it the arrival time and
    platform;
  - changing: the next platform as headline, with the margin and "Pl. 8 → 1";
  - last ride: the destination;
  - arrived: where and when, with the delay.
- **A train is always shown with all four facts:** time (with delay), line,
  "to" + the train's own destination as the departure board shows it (even
  when I get off before it), and platform. Arrows only for a route or a
  platform change.
- **The steps still ahead** follow, each with a time and platform: where this
  ride ends (unless it is the headline), the change and its margin, the next
  train, the destination.
- **One card, two forms:** the next-trip card on the travel day is the live
  card's compact form (`LiveCard(summary = true)`, `LiveDashboard summary`).
  It has the same state line, headline and train line, then one line to the
  destination with the change count and how the changes look. It has no
  source, ride rail, steps ahead, notices or status. Off the travel day the
  next-trip card stays the departure overview.
- **Alternatives:** amber "See alternatives" only when something broke
  (missed, cancelled, not in the timetable, impossible), on every card. A
  change only at risk gets a quiet outlined "Alternatives" on the live card and
  nothing on the Trips card. Otherwise they stay in the ⋮ menu.
- A broken step is the headline, struck through with its train, plus "Train
  binding lifted." when it is. The rest of the plan returns once a connection
  is chosen.

## Consequences
- `TravelDayHero` (both clients) is gone. A change to the travel day's wording
  now goes into the live card only, and the Trips card follows.
- On the web the Trips card is one link, so its "See alternatives" opens the
  trip, whose live card has the button.

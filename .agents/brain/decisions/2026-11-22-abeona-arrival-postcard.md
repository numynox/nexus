# ADR: Abeona ends a trip with a postcard

**Date:** 2026-11-22
**Status:** Accepted

## Context
After arrival Abeona showed the arrival, the delay and compensation, and then
nothing. The calm moment after a trip is where travel apps put their delight.
Flighty's lifetime stats ("Passport"), Transit's "Year in Transit", Railwise's
statistics and Träwelling's points are what users of those apps share and
talk about.

Research also sets limits. Delight that lasts rests on usability. A playful
screen during a disruption hurts (NN/g, Mailchimp's tone guide). Retrospectives
should be opt-in to share, not pushed. Points and leaderboards expose
movement patterns (the criticism of Träwelling's public check-ins).

The trains' Wi-Fi reports the speed. The ICE Portal and ÖBB Railnet are read
by the Android companion. Until now the speed was shown and then forgotten.

## Decision
- **`abeona_legs.top_speed_kmh`.** The Android companion records the highest
  speed the train's Wi-Fi reports during a ride. It writes the value directly
  under RLS, at most once a minute, and only when the value rises by at least
  3 km/h. A re-save keeps it.
- **`abeona_trip_summary(trip_id)`.** A Postgres function, so both clients
  share the logic. It returns:
  - the rides and the km;
  - the minutes on board (live times where known);
  - the delay at the destination (`abeona_trip_arrival`);
  - the top speed;
  - the CO₂ figures of the year in review.
- **The postcard.** It is shown on an arrived trip below compensation, never
  while something is broken. It shows the route and date and the figures
  (travelled, on board, CO₂ saved, top speed, at the end), with a Share button
  that makes a 1080×1350 image: the arrival scene, the route and the figures.
  Sharing is the traveller's choice; nothing is posted anywhere by itself.
- **`abeona_statistics`** gains `top_speed_kmh` and `top_speed_trip`. The year
  in review gets a "Top speed" card.
- No points, no leaderboards, no public check-ins.

## Consequences
- The top speed exists only for rides taken with Android and the train's
  Wi-Fi. The web shows it when Android recorded it, and leaves it out
  otherwise.
- A connection followed instead of the plan keeps the plan's distance in the
  summary. That is close enough for a postcard, though not for an invoice.

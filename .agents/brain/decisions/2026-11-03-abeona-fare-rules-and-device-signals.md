# ADR: Abeona's travel day — fare rules as data, signals on the phone

**Date:** 2026-11-03
**Status:** Accepted

## Context
Phase 4 of #37 is the live travel day: where someone is on their trip right
now, what to do next, and what to take instead when a change goes wrong.
Three questions needed a home.

1. **Which alternatives does the ticket cover?** A Deutschlandticket never
   covers an IC or ICE, a regional ticket neither, a Sparpreis only the booked
   trains until DB lifts the binding, a Flexpreis anything but FlixTrain. DB
   rewords these terms from time to time, and installed APKs lag behind
   releases by weeks.
2. **Where is the traveller?** The clock and the live delays say where they
   *should* be. The phone can know more: its position, and — on an ICE or a
   Railjet — the train's own Wi-Fi portal, which names the train. The web has
   neither: the browser cannot reach the portals (CORS, and they are only
   reachable from the train's network) and keeps no position in the
   background.
3. **Where does the "travel day" logic live?** It must say the same on the
   dashboard, the trip page, the trip screen and the live notification.

## Decision
- **Fare rules are rows.** `abeona_fare_rules` holds, per fare type: whether
  long-distance trains are covered, the products never covered, whether the
  ticket is train-bound and whether the binding can be lifted, and an English
  hint that ends in "check the terms". `abeona_allowed_products(trip_id)`
  applies the row to a trip with its last saved live values (as the caller,
  so RLS is the access check). The binding is lifted by the existing rule
  (`abeona_train_binding_lifted`, ≥ 20 min at the destination) **or a
  cancelled ride**, which DB also treats as lifting it. `other` has no row on
  purpose: every alternative is then "check the terms". Corrections are
  migrations, not releases.
- **Alternatives are one Edge Function call per refresh.** The
  `alternatives` action of `abeona-transit` reads the allowance as the caller,
  runs one Transitous plan request (the `journeys` code; regional modes only
  for a ticket that never covers long-distance trains, so its few valid
  connections are not crowded out), marks each connection `valid`, `check` or
  `invalid` with an English note (`validity.ts`) and ranks them by arrival,
  then changes. Both clients show the marks as they come.
- **The travel day is a shared pure function, written twice** like the
  transfer rules: `app/abeona/src/lib/travelday.ts` and
  `android/abeona/.../domain/TravelDay.kt`, pinned by
  `travelday.fixtures.json`. States: before leaving → at the station → on
  board ride n → changing → arrived, from the clock and the current times
  with a per-device **time to the station**. Device signals are optional
  inputs, so the web runs the same function without them.
- **Signals stay on the phone** and are never stored or sent. On-board Wi-Fi
  (ICE Portal `api1/rs/status` and `tripInfo/trip`, ÖBB Railnet
  `fis/combined.json` and `api/speed`) is the strongest: a matched train
  number puts the traveller on that ride. The position only confirms or
  corrects the clock (within 300 m of a stop; within 1 km of a ride's line
  above 30 km/h; standing at a ride's first stop two minutes after it left is
  "missed"), and the copy says "Looks like …".
- **Location is foreground-only.** `LocationManager` (no Play Services, like
  Vibilia), every 30 s or 50 m, only while the live companion runs, only with
  the setting on. The companion's foreground service gains the `location`
  type when it may; started from the background (the auto-start) Android
  refuses that type, and it follows the trip without the position. No
  background-location permission is asked for.
- **Alerts and reminders are device-local** (DataStore): delay threshold
  (default 5 min, again for each further step), connection at risk or missed,
  platform change, cancellation; the time-to-leave reminder is a WorkManager
  one-off job that refreshes the trip when it runs and moves itself if the
  train now leaves later.

## Consequences
- The portals are not public APIs. Every field is optional, a request has a
  2.5 s timeout on the Wi-Fi network itself, and anything unexpected reads as
  "not on board". A changed portal degrades to the clock, not to an error.
  None of this has run on a real train yet.
- The web shows the same states and alternatives, but only by the clock and
  only while open: no position, no portal, no time-to-leave notification.
  Server push (#37, deferred) would be needed for that.
- The fare rules are a summary, not the conditions of carriage; the hint
  always says so. A product the rules do not list (a new brand) counts as
  covered unless the ticket excludes long-distance trains, in which case a
  train of unknown product is "check".
- Asking the portals every 30 s while a live trip is on screen or the
  companion runs costs nothing on mobile data (the request is only made over
  a Wi-Fi network) and one quick failed request on any other Wi-Fi.

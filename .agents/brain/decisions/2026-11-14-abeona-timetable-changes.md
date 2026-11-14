# ADR: Abeona — rides the timetable no longer has, and following the same trains

**Date:** 2026-11-14
**Status:** Accepted

## Context
A ride saved without the timetable (intake keeps it as printed) showed as
"Timetable only" whatever the reason. Usually the reason is a date after the
next timetable change, and since #67 such a ride is looked up again and
attached once found. ICE 725 on 22 November 2026 was different: the
timetable covered that day, and the train was not in it, because of
engineering works. DB Navigator asked for another connection and lifted
the train binding, while Abeona said nothing. Often, though, the change is
small. The same trains still run, with another change station (Frankfurt
Flughafen instead of Hbf) or minutes earlier, and choosing them by hand
would be busywork.

## Decision
- **Dropped:** looking an unfound ride up again (`checkSavedLeg`, match.ts and
  Match.kt, fixtures `refinding`) marks it dropped when it is missing while
  later trains of the same kind (line letters: ICE, RJ…) run between its
  stops within 36 hours of it. That shows the timetable covers its day.
  Trains of another kind are not enough, nor are ones days later.
- **Stored as a cancellation:** `abeona_mark_leg_dropped` sets `cancelled` and
  the new `dropped_at`, once (it returns whether this call marked it). So
  `abeona_allowed_products` lifts the binding (`cancelled`), compensation
  waits for the trains taken instead, and older APKs see a cancellation.
  `dropped_at` lets the clients say "Not in the timetable".
  `abeona_attach_leg` clears it if the ride comes back.
- **The clear case is followed without asking** (`clearReplacement`, fixtures
  `replacing`). It needs the same trains in the same order, from the ride
  before the dropped one to where the dropped one ends, then the booked rides
  after it as they are. None may be cancelled or unfound, and every change
  must be safe or tight (transfer.ts). On 22 Nov 2026, ICE 725 left from
  Frankfurt Flughafen 17:42 instead of the Hbf 17:57: get off ICE 17 there,
  take ICE 725, then the booked ICE 700. The search only goes to the dropped
  ride's end on purpose. Searched to the destination, the router preferred an
  S-Bahn arriving minutes before the booked ICE 700, and the same trains were
  never offered. The result is stored like a taken alternative
  (`missed_leg_idx` = the ride before, `replacement_legs`) and shown as
  "Adjusted for a timetable change", with no "Back to the plan", since the
  plan does not run. Nothing is taken over a connection someone already
  chose.
- **Otherwise the traveller chooses:** "X is no longer in the timetable" with
  the alternatives from the dropped ride's station, from the arrival of the
  ride before it. Taking one replaces the dropped ride and the rest.
- **Told once:** Android's half-hourly job looks every upcoming trip up
  (throttled to 15 minutes per trip) and notifies on the trip alerts
  channel when a ride was newly dropped or a connection was adjusted. The
  web checks the upcoming trips when the trip list loads and shows "Timetable
  changed" / "Adjusted" on them.

## Consequences
- A gap in Transitous' data on one route could mark a ride dropped
  wrongly. The kind-of-train rule keeps this rare, and it undoes itself
  once the ride is found again (its cancellation becomes the timetable's).
  A replacement taken meanwhile stays until "Back to the plan" is used
  (offered once the ride is no longer dropped).
- One more journeys search per trip with a dropped ride and no replacement,
  at most every 15 minutes.

## Addendum: every ride, and the neighbouring days (later on 2026-11-14)
- **Every ride still to come is checked**, not only the ones intake could not
  find. A train found when the trip was saved (from Abeona's own search,
  say) can be gone after a timetable change just the same, and the live
  refresh by trip ref would only stop finding it on the day. The rides a
  connection taken instead replaces are left out. Each check costs one search
  per ride, so a trip whose rides are all found is checked every 3 hours, and
  every 15 minutes while one is unfound or dropped. Renamed `checkTimetable`
  (data.ts, `AbeonaRepository`).
- `abeona_mark_leg_dropped` takes found rides too (they keep their trip ref).
  `abeona_attach_leg` also takes the timetable's ride when the saved one is
  dropped (back again) or its trip ref changed (a feed renumbering its trips
  would otherwise leave the live refresh with nothing to find).
- **The day's coverage has a second proof.** When no later train of the same
  kind runs within 36 hours, the same train number between the same stops the
  next day, or a week on (same weekday), shows the timetable reaches the date,
  since feeds are contiguous. This catches rare trains: night trains, an IC
  once a day. The day before proves nothing, as it may be the last day
  published. Up to two more searches, only for a ride not found.

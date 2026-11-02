# ADR: Announce the commute's arrival time through the car

**Date:** 2026-11-02
**Status:** Accepted

## Context
Drive tracking (`2026-10-19-bluetooth-drive-tracking.md`) already knows when a
phone connects to the car and how long each drive lasts. On a commute that is
enough to predict the arrival time, and the phone can say it through the
car's speakers the moment the trip starts.

The request also asked for a notification on every other phone the car is
shared with. Nexus has no push channel; the options were FCM through an Edge
Function (the one Firebase piece `2026-10-19-native-android-apps.md` allows),
polling every 15 minutes, or ntfy. The owner chose to **leave that out** for
now, so the per-direction "notify others" switches were not built either.

## Decision
- **A commute is per person and per car** (`vibilia_commutes`, primary key
  `(user_id, car_id)`): weekdays, a window for the way to work and one for the
  way home (local time in `nexus_time_zone()`, not crossing midnight), and home
  and work as a point with a radius. Home and work are private: RLS shows a
  row only to its owner, and only while they may use the car.
- **Expected travel time is a median in SQL**
  (`get_vibilia_commute_expectation`): the caller's own drive intervals in the
  car that started on a commute weekday inside the window, the 20 most recent
  within 180 days. Own intervals, not merged periods, so a passenger's or a
  co-commuter's drives do not count. The median keeps an errand in the window
  from moving it. Both clients show it.
- **Announcing is Android-only and per phone** (a device-local switch, off by
  default). On a Bluetooth connect to a linked car inside a window, a location
  foreground service listens for a minute, whatever happens; if any position
  falls inside the geofence the trip starts from (home for the way to work,
  work for the way home), it speaks "Die Ankunftszeit auf der Arbeit ist 7:45
  Uhr. Gute Fahrt." or "… daheim …" as navigation guidance, which the car plays.
  Arrival is connect time plus the median. Once per car, direction and day.
- Commutes and their medians are cached on the phone, so a connection in a
  dead spot still works; the service asks the server again during the minute.

## Consequences
- Needs location "all the time": the service starts from a Bluetooth broadcast
  (an allowed background start), but reading the position from there needs
  background location. Without it the switch stays off.
- The median only knows drives this person's phone recorded, so drive
  tracking has to be on for a while before there is anything to say; until
  then the phone stays silent.
- Drives are not tied to places, so any drive starting in the window counts,
  including one that does not go to work. The median absorbs the odd one.
- Adding notifications to other members later needs a push transport — that
  is the decision deferred above, not a schema change: the commute already
  says who is going where.

# ADR: A refuel reminder on Android, like Annona's expiry reminder

**Date:** 2026-10-21
**Status:** Accepted

## Context
The fuel estimate already says when a tank runs low, but only to someone who
opens the app. A phone can tell its owner unprompted; a browser tab cannot do
that reliably.

## Decision
- A daily WorkManager job (`reminders/RefuelReminders.kt`) at a chosen hour
  asks `get_car_fuel_level_estimate` for every car the user can see and
  notifies when one is under a threshold (10–40 %, default 20 %) or runs dry
  within a day. It says what is left, the range, and what filling up costs at
  the cheapest of our stations.
- Per phone and off by default, as in Annona
  (`2026-10-19-noctua-and-annona-on-android.md`): a car is shared, nagging is
  personal. The web has no counterpart and hides nothing, because it never had
  the setting.
- No schema change: it reads what the fuel card reads.

## Consequences
- Once a day, at most: a tank that drops below the threshold mid-afternoon is
  reported the next morning.
- Driving this phone has not uploaded yet is not counted; the server's
  estimate is.
- A signed-out phone, or one without the notification permission, stays
  silent.

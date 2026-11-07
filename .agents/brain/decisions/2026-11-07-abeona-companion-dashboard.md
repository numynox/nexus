# ADR: Abeona's companion starts from an exact alarm, refreshes every minute, and shows a dashboard

**Date:** 2026-11-07
**Status:** Accepted

## Context
The live companion (`2026-11-02-abeona-train-companion.md`) did not switch on
by itself, and when it ran it was not useful enough:

- The auto-start setting was off by default, and its start was a WorkManager
  job. Android 12+ refuses to start a foreground service from a background
  job, so the job could only post "tap to follow it live" — the companion
  practically never started on its own. A delayed job may also run late in
  Doze.
- It refreshed every two minutes and redrew every 30 s, while the Edge
  Function caches a refresh for 60 s. Countdowns lagged by up to a minute.
- The hero card said what to do next but not where the traveller is: no next
  stop, no stops left, no position on the map, little about the change, and
  nothing about why Abeona thought the traveller was on a given train.

## Decision
- **The auto-start is an exact alarm** (`AlarmManager.setExactAndAllowWhileIdle`,
  `USE_EXACT_ALARM`; `SCHEDULE_EXACT_ALARM` on Android 12) at the time to the
  station plus 15 minutes before departure, never less than 30. Starting a
  foreground service from an exact alarm is one of Android's exemptions.
  `USE_EXACT_ALARM` is restricted on Google Play to alarm and calendar apps;
  the apps are sideloaded (`2026-10-19-native-android-apps.md`), so that does
  not apply. The half-hourly job only re-arms the alarm (for "now" when a trip
  is already due), it never starts the service itself. Without the exact
  permission the alarm is inexact and the old reminder remains, now with a
  "Follow live" action (a notification tap may start a foreground service).
- **The setting is on by default.** Following the ride is the point of the
  app, and the notification has a Stop button. Opening the app or the trip
  during a ride starts the companion too (the app is in front, so Android
  allows it, with the position if that setting is on — a start from the
  alarm cannot have the `location` type, so opening the app upgrades it).
  Stopped by hand, it stays off for that trip until the app restarts.
- **Every minute, every 15 seconds.** The companion and an open trip screen
  refresh every 60 s (the cache's lifetime), look at the signals and redraw
  every 15 s, and the notification's chronometer counts down to the second in
  between. An open trip screen takes the companion's refreshes from
  `TripStore.refreshed` instead of asking a second time. The web refreshes
  every minute too.
- **Ride progress is a shared pure function**, written twice like the travel
  day: `progress.ts` and `domain/RideProgress.kt`, pinned by
  `progress.fixtures.json`. It gives the next stop, the stops left and where
  the timetable puts the train, interpolated along the leg's polyline between
  the stops either side. The map shows the strongest position there is:
  the train's Wi-Fi (its own coordinates), the phone's or browser's fix, else
  that estimate as a hollow dot.
- **The source is a pill; the evidence is one tap behind it.** The first
  card carries a small pill naming what placed the traveller (Train Wi-Fi,
  Location, Timetable) and the notification's subtext names it too. Tapping
  the pill opens "How Abeona knows": each signal says what it sees, the one
  that placed the traveller is marked "Decides", and the location can be
  turned on there. A first version had this as a card at the end of the
  dashboard; it repeated the pill every time for something read only when
  Abeona seems wrong, so it moved behind the pill before it shipped.
- **The web gets the same dashboard**, with the browser's position while the
  page is open and the traveller asked for it (device-local, off by default).
  The train's Wi-Fi stays Android-only.

## Consequences
- Twice the Transitous calls during a ride (one a minute instead of one every
  two), for one household; the 60 s cache keeps several devices on one trip
  from multiplying it.
- An exact alarm is exact in Doze but costs a wake-up; there is one per trip.
- The estimate is only as good as the stopovers' live times and the
  polyline's shape; between two stops it moves at constant speed. The copy
  never calls it a position: it is "where the timetable puts your train".
- Installed apps upgrade into the new default: a phone whose owner never
  touched the setting now starts following trips on its own.
- None of this has run on a real train yet; the alarm exemption is documented
  by Android, but OEM battery savers can still delay or block it.

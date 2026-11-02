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
polling every 15 minutes, or ntfy. The owner chose to **leave app
notifications out** and, instead, to have the phone **text one contact by
SMS**, switchable per direction (usually only the way home).

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
- **Texting** reuses the same minute and the same geofence check: the commute
  holds one contact (`sms_name`, `sms_number`, stored as digits with an
  optional leading plus) and `sms_to_work` / `sms_to_home`. The phone sends
  "Bin losgefahren. Ankunft daheim ca. 17:45 Uhr." from its own SIM through
  `SmsManager`. Only a phone that was granted `SEND_SMS` texts, so it is per
  phone like the spoken announcement; either one runs without the other.
  The web edits the contact and switches but cannot send.
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
- SMS costs whatever the user's plan charges, needs a SIM and signal, and
  has no retry. The text comes from the user's own number, hence the first
  person. Android may make a sideloaded app ask for "restricted settings"
  before it can be granted `SEND_SMS`; Play's SMS policy does not apply.
- A phone number is stored next to home and work, readable only by the
  commute's owner.
- Push notifications to other members' apps remain possible later with a
  push transport; the commute already says who is going where.

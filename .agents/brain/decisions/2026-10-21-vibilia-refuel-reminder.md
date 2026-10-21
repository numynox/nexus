# ADR: A refuel reminder on Android, like Annona's expiry reminder

**Date:** 2026-10-21
**Status:** Accepted

## Context
The fuel estimate already says when a tank runs low, but only to someone who
opens the app. A phone can tell its owner unprompted; a browser tab cannot do
that reliably.

## Decision
- An hourly WorkManager job (`reminders/RefuelReminders.kt`), just after the
  price refresh, asks `get_car_fuel_level_estimate` for every car the user can
  see. It does nothing unless one is under a threshold (10–40 %, default 20 %)
  or runs dry within a day.
- **It picks the moment.** German prices repeat a daily shape — a jump at
  noon, a slide through the afternoon, the low from evening to late morning.
  `PriceOutlook` learns it on the phone from the last seven days of
  `get_fuel_price_plot_history` (hourly lowest price of our stations, as a
  premium over each day's low, median per hour). The reminder speaks up when
  the current hour is at the low ("Fill up for 1.939 €/L at JET Tennenlohe"),
  when the low is due within three hours ("from 19:00", with the expected price),
  or, whatever the price, when the tank runs dry within twelve hours.
- At most once a day, only between 07:00 and 21:59. Without three full days
  of history it falls back to the cheapest station now.
- Per phone and off by default, as in Annona
  (`2026-10-19-noctua-and-annona-on-android.md`): a car is shared, nagging is
  personal. The web has no counterpart: a browser cannot notify reliably.
- No schema change. The shape is computed on the phone because only the
  phone notifies; if the web ever shows "best time to refuel", move it into
  an RPC so both agree.

## Consequences
- The station is the cheapest one now; the expected price assumes the
  market-wide shape, not that station's own.
- Driving this phone has not uploaded yet is not counted; the server's
  estimate is.
- A signed-out phone, or one without the notification permission, stays
  silent.
